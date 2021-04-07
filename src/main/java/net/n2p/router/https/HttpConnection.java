package net.n2p.router.https;

import java.io.*;
import java.nio.channels.*;
import javax.net.ssl.*;

import net.n2p.router.App;


public class HttpConnection {

    HttpContextImpl _httpContext;
    SSLEngine _sslEngine;
    SSLContext _sslContext;
    SSLStreams _sslStreams;

    InputStream _inputStream;
    InputStream _rawInputStream;

    OutputStream _outputStream;

    SocketChannel _socketChannel;
    SelectionKey _selectionKey;
    String _protocol = "https";

    long _time;
    volatile long _creationTime;
    volatile long _responseStartTime;
    int _remaining;
    boolean _closed = false;

    public enum State { IDLE, REQUEST, RESPONSE };
    volatile State _state;

    public String toString() {
        String string = null;
        if (_socketChannel != null) {
            string = _socketChannel.toString();
        }
        return string;
    }

    HttpConnection() {

    }

    void setChannel(SocketChannel socketChannel) {
        _socketChannel = socketChannel;
    }

    void setContext(HttpContextImpl httpContext) {
        if(App.debug()){System.out.println("HttpConnection.setContext():");}
        if(App.debug()){System.out.println(httpContext.getHttpsServer().toString());}
        _httpContext = httpContext;
        if(App.debug()){System.out.println(_httpContext.getHttpsServer().toString());}
    }

    State getState() {
        return _state;
    }

    void setState(State state) {
        _state = state;
    }

    void setParameters( InputStream inputStream,
                        OutputStream outputStream,
                        SocketChannel socketChannel,
                        SSLEngine sslEngine,
                        SSLStreams sslStreams,
                        SSLContext sslContext,
                        HttpContextImpl httpContext,
                        InputStream rawInputStream
                        ) {
        _httpContext = httpContext;
        _inputStream = inputStream;
        _outputStream = outputStream;
        _rawInputStream = rawInputStream;
        _sslEngine = sslEngine;
        _sslStreams = sslStreams;
        _socketChannel = socketChannel;
        _sslContext = sslContext;
    }

    SocketChannel getChannel() {
        return _socketChannel;
    }

    synchronized void close() {
        if (_closed) {
            return;
        }
        _closed = true;
        // logger
        if (!_socketChannel.isOpen()) {
            // logger
        }
        try {
            if (_rawInputStream != null) {
                _rawInputStream.close();
            }
        } catch (IOException ioException) {
            // logger
        }
        try {
            if (_outputStream != null) {
                _outputStream.close();
            }
        } catch (IOException ioException) {
            // logger
        }
        try {
            if (_sslStreams!= null) {
                _sslStreams.close();
            }
        } catch (IOException ioException) {
            // logger
        }
        try {
            _socketChannel.close();
        } catch (IOException ioException) {
            // logger
        }
    }

        /* remaining is the number of bytes left on the lowest level inputstream
     * after the exchange is finished
     */
    void setRemaining (int r) {
        _remaining = r;
    }

    int getRemaining () {
        return _remaining;
    }

    SelectionKey getSelectionKey () {
        return _selectionKey;
    }

    InputStream getInputStream () {
            return _inputStream;
    }

    OutputStream getRawOutputStream () {
            return _outputStream;
    }

    String getProtocol () {
            return _protocol;
    }

    SSLEngine getSSLEngine () {
            return _sslEngine;
    }

    SSLContext getSSLContext () {
            return _sslContext;
    }

    HttpContextImpl getHttpContext () {
            return _httpContext;
    }

}
