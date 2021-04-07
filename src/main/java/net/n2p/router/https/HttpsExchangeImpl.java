package net.n2p.router.https;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.text.*;
import java.util.stream.Stream;


public class HttpsExchangeImpl {
    Headers _requestHeaders, _responseHeaders;
    Request _request;
    String _method;
    boolean _writeFinished;
    URI _uri;
    HttpConnection _httpConnection;
    long _requestContentLength;
    long _responseContentLength;
    InputStream _rawInputStream;
    OutputStream _rawOutputStream;
    Thread _thread;

    boolean _close;
    boolean _closed;
    boolean _http10 = false;

    /* for formatting the Date: header */
    private static final String _pattern = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final TimeZone _gmtTZ = TimeZone.getTimeZone("GMT");
    private static final ThreadLocal<DateFormat> _dateFormat =
        new ThreadLocal<DateFormat>() {
            @Override protected DateFormat initialValue() {
            DateFormat df = new SimpleDateFormat(_pattern, Locale.US);
            df.setTimeZone(_gmtTZ);
            return df;
            }
        };

    private static final String HEAD = "HEAD";

    InputStream _userInputStream;
    OutputStream _userOutputStream;
    LeftOverInputStream _userInputStreamOrig;
    PlaceholderOutputStream _userOutputStreamOrig;

    boolean _sentHeaders;
    int _responseCode = -1;
    HttpsServer _httpsServer;

    private byte[] _responseBuf = new byte[128];

    HttpsExchangeImpl(String method, URI uri, Request request, long requestContentLength, HttpConnection httpConnection) 
    throws IOException {
        _request = request;
        _requestHeaders = request.headers();
        _responseHeaders = new Headers();
        _method = method;
        _uri = uri;
        _httpConnection = httpConnection;
        _requestContentLength = requestContentLength;

        _rawOutputStream = request.outputStream();
        _rawInputStream = request.inputStream();
        _httpsServer = getHttpsServer();
        _httpsServer.startExchange();
    }

    public Headers getRequestHeaders() {
        return new UnmodifiableHeaders(_requestHeaders);
    }

    public Headers getResponseHeaders() {
        return _responseHeaders;
    }

    public URI getRequestURI() {
        return _uri;
    }

    public String getRequestMethod() {
        return _method;
    }

    public HttpContextImpl getHttpContext() {
        return _httpConnection.getHttpContext();
    }

    private boolean isHeadRequest() {
        return HEAD.equals(getRequestMethod());
    }

    public void close() {
        if (_closed) {
            return;
        }
        _closed = true;

        try {
            if (_userInputStreamOrig == null || _userOutputStreamOrig == null) {
                _httpConnection.close();
                return;
            }
            if (!_userOutputStreamOrig.isWrapped()) {
                _httpConnection.close();
                return;
            }
            if (!_userInputStreamOrig.isClosed()) {
                _userInputStreamOrig.close();
            }
            _userOutputStreamOrig.close();
        } catch (IOException ioException) {
            _httpConnection.close();
        }
    }

    public InputStream getRequestBody() {
        if (_userInputStream != null) {
            return _userInputStream;
        }
        if (_requestContentLength == -1L) {
            _userInputStreamOrig = new ChunkedInputStream(this, _rawInputStream);
            _userInputStream = _userInputStreamOrig;
        } else {
            _userInputStreamOrig = new FixedLengthInputStream(this, _rawInputStream, _requestContentLength);
            _userInputStream = _userInputStreamOrig;
        }
        return _userInputStream;
    }

    LeftOverInputStream getOriginalInputStream() {
        return _userInputStreamOrig;
    }

    public int getResponseCode() {
        return _responseCode;
    }

    public OutputStream getResponseBody() {
        if (_userOutputStream == null) {
            _userOutputStream = new PlaceholderOutputStream(null);
            _userOutputStream = _userOutputStreamOrig;
        }
        return _userOutputStream;
    }

    PlaceholderOutputStream getPlaceholderResponseBody() {
        getResponseBody();
        return _userOutputStreamOrig;
    }

    public void sendResponseHeaders(int responseCode, long contentLength) throws IOException {
        if (_sentHeaders) {
            throw new IOException ("headers already sent");
        }
        _responseCode = responseCode;
        String statusLine = "HTTP/1.1 " + _responseCode + Code.msg(_responseCode) + "\r\n";
        OutputStream tmpout = new BufferedOutputStream(_rawOutputStream);
        PlaceholderOutputStream o = getPlaceholderResponseBody();
        tmpout.write(bytes(statusLine, 0), 0, statusLine.length());
        boolean noContentToSend = false;
        boolean noContentLengthHeader = false;
        _responseHeaders.set("Date", _dateFormat.get().format(new Date()));

        if((_responseCode>=100 && _responseCode<200) || (_responseCode == 204) || (_responseCode == 304)) {
            if (contentLength != -1) {
                // logger
            }
            contentLength = -1;
            noContentLengthHeader = (_responseCode != 304);
        }

        if (isHeadRequest() || _responseCode == 304) {
            if (contentLength >= 0) {
                // logger
            }
            noContentToSend = true;
            contentLength = 0;
        } else {
            if (contentLength == 0) {
                if (_http10) {
                    o.setWrappedStream(new UndefLengthOutputStream(this, _rawOutputStream));
                    _close = true;
                } else {
                    _responseHeaders.set("Transfer-encoding", "chunked");
                    o.setWrappedStream(new ChunkedOutputStream(this, _rawOutputStream));
                }
            } else {
                if (contentLength == -1) {
                    noContentToSend = true;
                    contentLength = 0;
                }
                if (!noContentLengthHeader) {
                    _responseHeaders.set("Content-length", Long.toString(contentLength));
                }
                o.setWrappedStream(new FixedLengthOutputStream(this, _rawOutputStream, contentLength));
            }
        }

        if(!_close) {
            Stream<String> httpConnectionHeader = 
                Optional.ofNullable(_responseHeaders.get("Connection"))
                .map(List::stream).orElse(Stream.empty());
            if (httpConnectionHeader.anyMatch("close"::equalsIgnoreCase)) {
                // logger
                _close = true;
            }
        }

        write(_responseHeaders, tmpout);
        _responseContentLength = contentLength;
        tmpout.flush();
        _sentHeaders = true;
        if (noContentToSend) {
            WriteFinishedEvent wfe = new WriteFinishedEvent(this);
            _httpsServer.addEvent(wfe);
            _closed = true;
        }
        // logger
    }

    void write(Headers map, OutputStream outputStream) throws IOException {
        Set<Map.Entry<String, List<String>> > entries = map.entrySet();
        for (Map.Entry<String, List<String>> entry : entries) {
            String key = entry.getKey();
            byte[] buf;
            List<String> values = entry.getValue();
            for (String val : values) {
                int i = key.length();
                buf = bytes(key, 2);
                buf[i++] = ':';
                buf[i++] = ' ';
                outputStream.write(buf, 0, i);
                buf = bytes(val, 2);
                i = val.length();
                buf[i++] = '\r';
                buf[i++] = '\n';
                outputStream.write(buf, 0, i);
            }
        }
        outputStream.write('\r');
        outputStream.write('\n');
    }

    private byte[] bytes(String string, int extra) {
        int stringLength = string.length();
        if (stringLength + extra > _responseBuf.length) {
            int diff = stringLength + extra - _responseBuf.length;
            _responseBuf = new byte[2 * (_responseBuf.length + diff)];
        }
        char c[] = string.toCharArray();
        for (int i=0; i<c.length; i++) {
            _responseBuf[i] = (byte)c[i];
        }
        return _responseBuf;
    }    

    public InetSocketAddress getRemoteAddress() {
        Socket socket = _httpConnection.getChannel().socket();
        InetAddress inetAddress = socket.getInetAddress();
        int port = socket.getPort();
        return new InetSocketAddress(inetAddress, port);
    }

    public InetSocketAddress getLocalAddress() {
        Socket socket = _httpConnection.getChannel().socket();
        InetAddress inetAddress = socket.getLocalAddress();
        int port = socket.getLocalPort();
        return new InetSocketAddress(inetAddress, port);
    }

    public SSLSession getSSLSession() {
        SSLEngine sslEngine = _httpConnection.getSSLEngine();
        if (sslEngine == null) {
            return null;
        }
        return sslEngine.getSession();
    }

    public void setStreams(InputStream inputStream, OutputStream outputStream) {
        assert _userInputStream != null;
        if (inputStream != null) {
            _userInputStream = inputStream;
        }
        if (outputStream != null) {
            _userOutputStream = outputStream;
        }
    }

    HttpConnection getHttpConnection() {
        return _httpConnection;
    }

    HttpsServer getHttpsServer() {
        return getHttpContext().getHttpsServer();
    }
    
}

class PlaceholderOutputStream extends OutputStream {

    OutputStream wrapped;

    PlaceholderOutputStream(OutputStream outputStream) {
        wrapped = outputStream;
    }

    void setWrappedStream(OutputStream outputStream) {
        wrapped = outputStream;
    }
    
    boolean isWrapped() {
        return wrapped != null;
    }

    private void checkWrap() throws IOException {
        if (wrapped == null) {
            throw new IOException ("response headers not sent yet");
        }
    }

    public void write(int b) throws IOException {
        checkWrap();
        wrapped.write(b);
    }

    public void write(byte b[]) throws IOException {
        checkWrap();
        wrapped.write(b);
    }

    public void write(byte b[], int off, int len) throws IOException {
        checkWrap();
        wrapped.write(b, off, len);
    }

    public void flush() throws IOException {
        checkWrap();
        wrapped.flush();
    }

    public void close() throws IOException {
        checkWrap();
        wrapped.close();
    }

}