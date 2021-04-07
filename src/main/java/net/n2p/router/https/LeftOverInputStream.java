package net.n2p.router.https;

import java.io.*;

abstract class LeftOverInputStream extends FilterInputStream{
    final HttpsExchangeImpl _exchangeImpl;
    final HttpsServer _httpsServer;
    protected boolean closed = false;
    protected boolean eof = false;
    byte[] one = new byte [1];

    public LeftOverInputStream(HttpsExchangeImpl t, InputStream src) {
        super(src);
        _exchangeImpl = t;
        _httpsServer = t.getHttpsServer();
    }
    /**
     * if bytes are left over buffered on *the UNDERLYING* stream
     */
    public boolean isDataBuffered() throws IOException {
        assert eof;
        return super.available() > 0;
    }

    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (!eof) {
            eof = drain(ServerConfig.getDrainAmount());
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isEOF() {
        return eof;
    }

    protected abstract int readImpl(byte[]b, int off, int len) throws IOException;

    public synchronized int read() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        int c = readImpl (one, 0, 1);
        if (c == -1 || c == 0) {
            return c;
        } else {
            return one[0] & 0xFF;
        }
    }

    public synchronized int read(byte[]b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }
        return readImpl(b, off, len);
    }

    /**
     * read and discard up to l bytes or "eof" occurs,
     * (whichever is first). Then return true if the stream
     * is at eof (ie. all bytes were read) or false if not
     * (still bytes to be read)
     */
    public boolean drain(long l) throws IOException {
        int bufSize = 2048;
        byte[] db = new byte [bufSize];
        while (l > 0) {
            if (_httpsServer.isFinishing()) {
                break;
            }
            long len = readImpl(db, 0, bufSize);
            if (len == -1) {
                eof = true;
                return true;
            } else {
                l = l - len;
            }
        }
        return false;
    }
}
