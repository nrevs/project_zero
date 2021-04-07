package net.n2p.router.https;

import java.nio.*;
import java.io.*;
import java.nio.channels.*;

import net.n2p.router.App;

public class Request {
    
    final static int BUF_LEN = 2048;
    final static byte CR = 13;
    final static byte LF = 10;

    private String _startLine;
    private SocketChannel _socketChannel;
    private InputStream _inputStream;
    private OutputStream _outputStream;

    Request(InputStream rawInputStream, OutputStream rawOutputStream) throws IOException {
        if(App.debug()){System.out.println("Request");}
        
        _inputStream = rawInputStream;
        _outputStream = rawOutputStream;

        do {
            _startLine = readLine();
            if (_startLine == null) {
                return;
            }
        } while (_startLine == null ? false : _startLine.equals(""));
    }

    char[] buf = new char[BUF_LEN];
    int pos;
    StringBuffer lineBuf;

    public InputStream inputStream() {
        return _inputStream;
    }

    public OutputStream outputStream() {
        return _outputStream;
    }

    public String readLine() throws IOException {
        if(App.debug()){System.out.println("Request.readLine()");}
        
        boolean gotCR = false, gotLF = false;
        pos = 0;
        lineBuf = new StringBuffer();
        
        while(!gotLF) {
            int c = _inputStream.read();
            if (c == -1) {
                return null;
            }
            if (gotCR) {
                if (c == LF) {
                    gotLF = true;
                } else {
                    gotCR = false;
                    consume(CR);
                    consume(c);
                }
            } else {
                if (c == CR) {
                    gotCR = true;
                } else {
                    consume(c);
                }
            }
        }

        lineBuf.append(buf, 0, pos);
        return new String(lineBuf);
    }

    private void consume(int c) {
        if (pos == BUF_LEN) {
            lineBuf.append(buf);
            pos = 0;
        }
        buf[pos++] = (char)c;
    }

    public String requestLine() {
        return _startLine;
    }

    Headers _headers = null;
    Headers headers() throws IOException {
        if (_headers != null) {
            return _headers;
        }
        _headers = new Headers();

        char s[] = new char[10];
        int len = 0;

        int firstc = _inputStream.read();

        // check for empty headers
        if (firstc == CR || firstc == LF) {
            int c = _inputStream.read();
            if (c == CR || c == LF) {
                return _headers;
            }
            s[0] = (char)firstc;
            len = 1;
            firstc = c;
        }

        while (firstc != LF && firstc != CR && firstc >= 0) {
            int keyend = -1;
            int c;
            boolean inKey = firstc > ' ';
            s[len++] = (char) firstc;
            parseloop:{
                while((c = _inputStream.read()) >= 0) {
                    switch (c) {
                        case ':':
                            if (inKey && len>0)
                                keyend = len;
                            inKey = false;
                            break;
                        case '\t':
                            c = ' ';
                        case CR:
                        case LF:
                            firstc = _inputStream.read();
                            if (c == CR && firstc == LF) {
                                firstc = _inputStream.read();
                                if (firstc == CR)
                                    firstc = _inputStream.read();
                            }
                            if (firstc == LF || firstc == CR || firstc > ' ')
                                break parseloop;
                            c = ' ';
                            break;
                    }
                    if (len >= s.length) {
                        char ns[] = new char[s.length * 2];
                        System.arraycopy(s, 0, ns, 0, len);
                        s = ns;
                    }
                    s[len++] = (char)c;
                }
                firstc = -1;
            }
            while (len > 0 && s[len-1] <= ' ')
                len--;
            String k;
            if (keyend <= 0) {
                k = null;
                keyend = 0;
            } else {
                k = String.copyValueOf(s, 0, keyend);
                if (keyend < len && s[keyend] == ':')
                    keyend++;
                while (keyend < len && s[keyend] <= ' ')
                    keyend++;
            }
            String v;
            if (keyend >= len)
                v = new String();
            else
                v = String.copyValueOf(s, keyend, len-keyend);

            if (_headers.size() >= ServerConfig.getMaxReqHeaders()) {
                throw new IOException("Maximum number of request headers (" +
                        "httpsServer.maxReqHeaders) exceeded, " +
                        ServerConfig.getMaxReqHeaders() + ".");

                // logger
            }
            _headers.add(k,v);
            len = 0;
        }
        // while (firstc != LF && firstc != CR && firstc >= 0) 

        return _headers;
    }

    static class ReadStream extends InputStream {
        SocketChannel socketChannel;
        ByteBuffer channelBuf;
        byte[] one;
        private boolean closed = false, eof = false;
        ByteBuffer markBuf;
        boolean marked;
        boolean reset;
        int readlimit;
        static long readTimeout;
        HttpsServer httpsServer;
        final static int BUFSIZE = 8*1024;

        public ReadStream(HttpsServer httpsServer, SocketChannel socketChannel) throws IOException {
            if(App.debug()){System.out.println("ReadStream {}");}
            this.socketChannel = socketChannel;
            this.httpsServer = httpsServer;
            channelBuf = ByteBuffer.allocate(BUFSIZE);
            channelBuf.clear();
            one = new byte[1];
            closed = marked = reset = false;
        }

        public synchronized int read(byte[] b) throws IOException {
            return read(b, 0, b.length);
        }

        public synchronized int read() throws IOException {
            int result = read(one, 0, 1);
            if (result == 1) {
                return one[0] & 0xFF;
            } else {
                return -1;
            }
        }

        public synchronized int read(byte[] b, int off, int srclen) throws IOException {
            
            int canreturn, willreturn;

            if (closed)
                throw new IOException("Stream Closed");
            
            if (eof) {
                return -1;
            }

            assert socketChannel.isBlocking();

            if (off < 0 || srclen < 0 || srclen > (b.length-off)) {
                throw new IndexOutOfBoundsException();
            }

            if (reset) {
                canreturn = markBuf.remaining();
                willreturn = canreturn>srclen ? srclen : canreturn;
                markBuf.get(b, off, willreturn);
                if (canreturn == willreturn) {
                    reset = false;
                }
            } else {
                channelBuf.clear();
                if (srclen < BUFSIZE) {
                    channelBuf.limit(srclen);
                }
                do {
                    willreturn = socketChannel.read(channelBuf);
                } while (willreturn == 0);
                
                if (willreturn == -1) {
                    eof = true;
                    return -1;
                }
                channelBuf.flip();
                channelBuf.get(b, off, willreturn);

                if (marked) {
                    try {
                        markBuf.put(b, off, willreturn);
                    } catch (BufferOverflowException boe) {
                        marked = false;
                    }
                }
            }
            return willreturn;
        }
        // read(byte[] b, int off, int srclen)

        public synchronized int available() throws IOException {
            if (closed)
                throw new IOException ("Stream is closed");
            
            if (eof)
                return -1;

            if (reset)
                return markBuf.remaining();
            
            return channelBuf.remaining();
        }

        public void close() throws IOException {
            if (closed) {
                return;
            }
            socketChannel.close();
            closed = true;
        }

        public synchronized void mark(int readlimit) {
            if (closed)
                return;
            
            this.readlimit = readlimit;
            markBuf = ByteBuffer.allocate(readlimit);
            marked = true;
            reset = false;
        }

        public synchronized void reset() throws IOException {
            if (closed)
                return;

            if (!marked)
                throw new IOException("Stream not marked");
            marked = false;
            reset = true;
            markBuf.flip();
        }
    }
    // static class ReadStream

    static class WriteStream extends OutputStream {
        SocketChannel socketChannel;
        ByteBuffer buf;
        SelectionKey key;
        boolean closed;
        byte[] one;
        HttpsServer httpsServer;


        public WriteStream(HttpsServer httpsServer, SocketChannel socketChannel) throws IOException {
            this.socketChannel = socketChannel;
            this.httpsServer = httpsServer;

            assert socketChannel.isBlocking();

            closed = false;
            one = new byte[1];
            buf = ByteBuffer.allocate(4096);
        }

        public synchronized void write(int b) throws IOException {
            one[0] = (byte)b;
            write (one, 0, 1);
        }

        public synchronized void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        public synchronized void write(byte[] b, int off, int len) throws IOException {
            int l = len;
            if (closed)
                throw new IOException("stream is closed");

            int cap = buf.capacity();
            if (cap < len) {
                int diff = len - cap;
                buf = ByteBuffer.allocate(2 * (cap+diff));
            }
            buf.clear();
            buf.put(b, off, len);
            buf.flip();
            int n;
            while((n = socketChannel.write(buf)) < l) {
                l -= n;
                if (l == 0)
                    return;

            }
        }

        public void cloase() throws IOException {
            if (closed)
                return;

            socketChannel.close();
            closed = true;
        }
    }
    //static class WriteStream
}
