package net.n2p.router.https;

import java.net.*;
import java.nio.*;
import java.io.*;
import java.nio.channels.*;
import java.util.concurrent.locks.*;
import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;

import net.n2p.router.App;


public class SSLStreams {

    SSLContext _sslContext;
    SocketChannel _socketChannel;
    TimeSource _time;
    HttpsServer _httpsServer;
    SSLEngine _sslEngine;
    EngineWrapper _wrapper;
    OutputStream _os;
    InputStream _is;

    Lock handshaking = new ReentrantLock();

    SSLStreams (HttpsServer httpsServer, SSLContext sslContext, SocketChannel socketChannel) throws IOException {
        _httpsServer = httpsServer;
        _time = (TimeSource)httpsServer;
        _sslContext = sslContext;
        _socketChannel = socketChannel;
        InetSocketAddress inetSocketAddress = 
            (InetSocketAddress)socketChannel.socket().getRemoteSocketAddress();
        _sslEngine = sslContext.createSSLEngine(inetSocketAddress.getHostName(), inetSocketAddress.getPort());
        _sslEngine.setUseClientMode(false);
        HttpsConfigurator cfg = httpsServer.getHttpsConfigurator();
        configureEngine(cfg, inetSocketAddress);
        _wrapper = new EngineWrapper(socketChannel, _sslEngine);
    }

    private void configureEngine(HttpsConfigurator cfg, InetSocketAddress inetSocketAddress) {
        if (cfg != null) {
            Parameters params = new Parameters(cfg, inetSocketAddress);

            cfg.configure(params);
            SSLParameters sslParams = params.getSSLParameters();
            if (sslParams != null) {
                _sslEngine.setSSLParameters(sslParams);
            } else {
                if (params.getCipherSuites() != null) {
                    try {
                        _sslEngine.setEnabledCipherSuites(params.getCipherSuites());
                    } catch (IllegalArgumentException iaE) {
                        iaE.printStackTrace();
                        // logger
                    }
                }
                _sslEngine.setNeedClientAuth(params.getNeedClientAuth());
                _sslEngine.setWantClientAuth(params.getWantClientAuth());
                if (params.getProtocols() != null) {
                    try {
                        _sslEngine.setEnabledProtocols(params.getProtocols());
                    } catch(IllegalArgumentException iaE) {
                        iaE.printStackTrace();
                        // logger
                    }
                }
            }
        }
    }

    class Parameters extends HttpsParameters {
        InetSocketAddress inetSocketAddress;
        HttpsConfigurator cfg;
        SSLParameters params;
            
        Parameters(HttpsConfigurator cfg, InetSocketAddress inetSocketAddress) {
            this.inetSocketAddress = inetSocketAddress;
            this.cfg = cfg;
        }

        public InetSocketAddress getClientAddress() {
            return inetSocketAddress;
        }

        public HttpsConfigurator getHttpsConfigurator() {
            return cfg;
        }

        public void setSSLParameters(SSLParameters p) {
            params = p;
        }
        SSLParameters getSSLParameters() {
            return params;
        }
    }

    void close() throws IOException {
        _wrapper.close();
    }

    InputStream getInputStream() throws IOException {
        if (_is == null) {
            _is = new InputStream();
        }
        return _is;
    }

    OutputStream getOutputStream() throws IOException {
        if (_os == null) {
            _os = new OutputStream();
        }
        return _os;
    }

    public SSLEngine getSSLEngine() {
        return _sslEngine;
    }

    void beginHandshake() throws SSLException {
        _sslEngine.beginHandshake();
    }

    class WrapperResult {
        SSLEngineResult result;

        ByteBuffer buf;
    }

    int app_buf_size;
    int packet_buf_size;

    enum BufType {
        PACKET, APPLICATION
    };

    private ByteBuffer allocate (BufType type) {
        return allocate(type, -1);
    }

    private ByteBuffer allocate (BufType type, int len) {
        assert _sslEngine != null;
        synchronized(this) {
            int size;
            if (type == BufType.PACKET) {
                if (packet_buf_size == 0) {
                    SSLSession sess = _sslEngine.getSession();
                    packet_buf_size = sess.getPacketBufferSize();
                }
                if (len > packet_buf_size) {
                    packet_buf_size = len;
                }
                size = packet_buf_size;
            } else {
                if (app_buf_size == 0) {
                    SSLSession sess = _sslEngine.getSession();
                    app_buf_size = sess.getApplicationBufferSize();
                }
                if (len > app_buf_size) {
                    app_buf_size = len;
                }
                size = app_buf_size;
            }
            return ByteBuffer.allocate (size);
        }
    }

    private ByteBuffer realloc(ByteBuffer b, boolean flip, BufType type) {
        synchronized(this) {
            int nsize = 2 * b.capacity();
            ByteBuffer n = allocate (type, nsize);
            if (flip) {
                b.flip();
            }
            n.put(b);
            b = n;
        }
        return b;
    }

    class EngineWrapper{
        SocketChannel chan;
        SSLEngine engine;
        Object wrapLock, unwrapLock;
        ByteBuffer unwrap_src, wrap_dst;
        boolean closed = false;
        int u_remaining; // the number of bytes left in unwrap_src after an unwrap()

        EngineWrapper (SocketChannel chan, SSLEngine engine) throws IOException {
            this.chan = chan;
            this.engine = engine;
            wrapLock = new Object();
            unwrapLock = new Object();
            unwrap_src = allocate(BufType.PACKET);
            wrap_dst = allocate(BufType.PACKET);
        }

        void close () throws IOException {
        }

        /* try to wrap and send the data in src. Handles OVERFLOW.
         * Might block if there is an outbound blockage or if another
         * thread is calling wrap(). Also, might not send any data
         * if an unwrap is needed.
         */
        WrapperResult wrapAndSend(ByteBuffer src) throws IOException {
            return wrapAndSendX(src, false);
        }

        WrapperResult wrapAndSendX(ByteBuffer src, boolean ignoreClose) throws IOException {
            if(App.debug()) {System.out.println("wrapAndSendX");}
            if (closed && !ignoreClose) {
                throw new IOException ("Engine is closed");
            }
            Status status;
            WrapperResult r = new WrapperResult();
            synchronized (wrapLock) {
                wrap_dst.clear();
                do {
                    r.result = engine.wrap(src, wrap_dst);
                    status = r.result.getStatus();
                    if (status == Status.BUFFER_OVERFLOW) {
                        wrap_dst = realloc(wrap_dst, true, BufType.PACKET);
                    }
                } while (status == Status.BUFFER_OVERFLOW);
                if (status == Status.CLOSED && !ignoreClose) {
                    closed = true;
                    return r;
                }
                if (r.result.bytesProduced() > 0) {
                    wrap_dst.flip();
                    int l = wrap_dst.remaining();
                    assert l == r.result.bytesProduced();
                    while (l>0) {
                        l -= chan.write (wrap_dst);
                    }
                }
            }
            return r;
        }

        /* block until a complete message is available and return it
         * in dst, together with the Result. dst may have been re-allocated
         * so caller should check the returned value in Result
         * If handshaking is in progress then, possibly no data is returned
         */
        WrapperResult recvAndUnwrap(ByteBuffer dst) throws IOException {
            if(App.debug()) {System.out.println("recvAndUnwrap begin: "+new String(dst.array(), "UTF-8"));}
            Status status = Status.OK;
            WrapperResult r = new WrapperResult();
            r.buf = dst;
            if (closed) {
                throw new IOException ("Engine is closed");
            }
            boolean needData;
            if (u_remaining > 0) {
                unwrap_src.compact();
                unwrap_src.flip();
                needData = false;
            } else {
                unwrap_src.clear();
                needData = true;
            }
            synchronized (unwrapLock) {
                int x;
                do {
                    if (needData) {
                        do {
                        x = chan.read (unwrap_src);
                        } while (x == 0);
                        if (x == -1) {
                            throw new IOException ("connection closed for reading");
                        }
                        unwrap_src.flip();
                    }
                    r.result = engine.unwrap(unwrap_src, r.buf);
                    status = r.result.getStatus();
                    if (status == Status.BUFFER_UNDERFLOW) {
                        if (unwrap_src.limit() == unwrap_src.capacity()) {
                            /* buffer not big enough */
                            unwrap_src = realloc (
                                unwrap_src, false, BufType.PACKET
                            );
                        } else {
                            /* Buffer not full, just need to read more
                             * data off the channel. Reset pointers
                             * for reading off SocketChannel
                             */
                            unwrap_src.position (unwrap_src.limit());
                            unwrap_src.limit (unwrap_src.capacity());
                        }
                        needData = true;
                    } else if (status == Status.BUFFER_OVERFLOW) {
                        r.buf = realloc (r.buf, true, BufType.APPLICATION);
                        needData = false;
                    } else if (status == Status.CLOSED) {
                        closed = true;
                        r.buf.flip();
                        return r;
                    }
                } while (status != Status.OK);
            }
            u_remaining = unwrap_src.remaining();
            if(App.debug()) {System.out.println("recvAndUnwrap end: "+new String(r.buf.array(), "UTF-8"));}
            return r;
        }
    }

    public WrapperResult sendData(ByteBuffer src) throws IOException {
        WrapperResult r=null;
        while (src.remaining() > 0) {
            if(App.debug()) {System.out.println("sendData() - src.remaining(): "+String.valueOf(src.remaining()));}
            r = _wrapper.wrapAndSend(src);
            Status status = r.result.getStatus();
            if(App.debug()) {System.out.println("sendData() - WR status == Status.CLOSED");}
            if (status == Status.CLOSED) {
                doClosure(1);
                return r;
            }
            HandshakeStatus hs_status = r.result.getHandshakeStatus();
            if (hs_status != HandshakeStatus.FINISHED &&
                hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                doHandshake(hs_status);
            }
        }
        return r;
    }

    public WrapperResult recvData(ByteBuffer dst) throws IOException {
        // logger
        if(App.debug()) {System.out.println("Data:");}
        if(App.debug()) {System.out.println(new String(dst.array(), "UTF-8"));}
        /* we wait until some user data arrives */
        WrapperResult r = null;
        assert dst.position() == 0;
        while (dst.position() == 0) {
            r = _wrapper.recvAndUnwrap(dst);
            dst = (r.buf != dst) ? r.buf: dst;
            Status status = r.result.getStatus();
            if (status == Status.CLOSED) {
                if(App.debug()) {System.out.println("recvData() - WR status == Status.CLOSED, so doClossure()");}
                doClosure (0);
                return r;
            }

            HandshakeStatus hs_status = r.result.getHandshakeStatus();
            if(App.debug()) {System.out.println("HandshakeStatus: "+hs_status.toString());}
            if (hs_status != HandshakeStatus.FINISHED &&
                hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                doHandshake (hs_status);
            }
        }
        dst.flip();
        return r;
    }

    /* we've received a close notify. Need to call wrap to send
     * the response
     */
    void doClosure(int who) throws IOException {
        try {
            handshaking.lock();
            ByteBuffer tmp = allocate(BufType.APPLICATION);
            WrapperResult r;
            int counter = 0;
            do {
                counter++;
                tmp.clear();
                tmp.flip ();
                if(App.debug()){System.out.print(String.valueOf(who));}
                if(App.debug()){System.out.println("doClosure() - handshake status!=Status.CLOSED");}
                r = _wrapper.wrapAndSendX(tmp, true);
                if(App.debug()){System.out.println("WR status: "+r.result.getStatus());}
                if (counter >= 10){
                    throw new IOException("stink");
                }
            } while (r.result.getStatus() != Status.CLOSED);
        } finally {
            handshaking.unlock();
        }
    }

    @SuppressWarnings("fallthrough")
    void doHandshake (HandshakeStatus hs_status) throws IOException {
        if(App.debug()){System.out.println("doHandshake - HandshakeStatus: "+hs_status.toString());}
        try {
            handshaking.lock();
            ByteBuffer tmp = allocate(BufType.APPLICATION);
            while (hs_status != HandshakeStatus.FINISHED &&
                   hs_status != HandshakeStatus.NOT_HANDSHAKING)
            {
                WrapperResult r = null;
                switch (hs_status) {
                    case NEED_TASK:
                        Runnable task;
                        while ((task = _sslEngine.getDelegatedTask()) != null) {
                            /* run in current thread, because we are already
                             * running an external Executor
                             */
                            task.run();
                        }
                        /* fall thru - call wrap again */
                    case NEED_WRAP:
                        tmp.clear();
                        tmp.flip();
                        if(App.debug()){System.out.println("doHandshake() - handshake status: "+hs_status.toString());}
                        r = _wrapper.wrapAndSend(tmp);
                        break;

                    case NEED_UNWRAP:
                        tmp.clear();
                        r = _wrapper.recvAndUnwrap (tmp);
                        if (r.buf != tmp) {
                            tmp = r.buf;
                        }
                        assert tmp.position() == 0;
                        break;
                }
                hs_status = r.result.getHandshakeStatus();
                if(App.debug()){System.out.println("new handshake status: "+hs_status.toString());}
            }
        } finally {
            handshaking.unlock();
        }
    }

    class InputStream extends java.io.InputStream {

        ByteBuffer bbuf;
        boolean closed = false;

        /* this stream eof */
        boolean eof = false;

        boolean needData = true;

        InputStream() {
            bbuf = allocate(BufType.APPLICATION);
        }

        public int read(byte[] buf, int off, int len) throws IOException {
            if (closed) {
                throw new IOException ("SSL stream is closed");
            }
            if (eof) {
                return 0;
            }
            int available=0;
            if (!needData) {
                available = bbuf.remaining();
                needData = (available==0);
            }
            if (needData) {
                bbuf.clear();
                WrapperResult r = recvData(bbuf);
                bbuf = r.buf== bbuf? bbuf: r.buf;
                if ((available=bbuf.remaining()) == 0) {
                    eof = true;
                    return 0;
                } else {
                    needData = false;
                }
            }
            /* copy as much as possible from buf into users buf */
            if (len > available) {
                len = available;
            }
            bbuf.get (buf, off, len);
            return len;
        }

        public int available() throws IOException {
            return bbuf.remaining();
        }

        public boolean markSupported() {
            return false; /* not possible with SSLEngine */
        }

        public void reset() throws IOException {
            throw new IOException ("mark/reset not supported");
        }

        public long skip(long s) throws IOException {
            int n = (int)s;
            if (closed) {
                throw new IOException ("SSL stream is closed");
            }
            if (eof) {
                return 0;
            }
            int ret = n;
            while (n > 0) {
                if (bbuf.remaining() >= n) {
                    bbuf.position (bbuf.position()+n);
                    return ret;
                } else {
                    n -= bbuf.remaining();
                    bbuf.clear();
                    WrapperResult r = recvData(bbuf);
                    bbuf = r.buf==bbuf? bbuf: r.buf;
                }
            }
            return ret; /* not reached */
        }

        public void close() throws IOException {
            eof = true;
            _sslEngine.closeInbound ();
        }

        public int read(byte[] buf) throws IOException {
            return read(buf, 0, buf.length);
        }

        byte single[] = new byte [1];

        public int read() throws IOException {
            int n = read (single, 0, 1);
            if (n == 0) {
                return -1;
            } else {
                return single[0] & 0xFF;
            }
        }
    }

    class OutputStream extends java.io.OutputStream {
        ByteBuffer buf;
        boolean closed = false;
        byte single[] = new byte[1];

        OutputStream() {
            buf = allocate(BufType.APPLICATION);
        }

        public void write(int b) throws IOException {
            single[0] = (byte)b;
            write (single, 0, 1);
        }

        public void write(byte b[]) throws IOException {
            write (b, 0, b.length);
        }
        public void write(byte b[], int off, int len) throws IOException {
            if (closed) {
                throw new IOException ("output stream is closed");
            }
            while (len > 0) {
                int l = len > buf.capacity() ? buf.capacity() : len;
                buf.clear();
                buf.put (b, off, l);
                len -= l;
                off += l;
                buf.flip();
                WrapperResult r = sendData (buf);
                if (r.result.getStatus() == Status.CLOSED) {
                    closed = true;
                    if (len > 0) {
                        throw new IOException ("output stream is closed");
                    }
                }
            }
        }

        public void flush() throws IOException {
            /* no-op */
        }

        public void close() throws IOException {
            WrapperResult r=null;
            _sslEngine.closeOutbound();
            closed = true;
            HandshakeStatus stat = HandshakeStatus.NEED_WRAP;
            buf.clear();
            while (stat == HandshakeStatus.NEED_WRAP) {
                if(App.debug()){System.out.println("close() - Handshake status"+stat.toString());}
                r = _wrapper.wrapAndSend (buf);
                stat = r.result.getHandshakeStatus();
            }
            assert r.result.getStatus() == Status.CLOSED;
        }
    }

}
// class SSLStreams




