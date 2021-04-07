
package net.n2p.router.https;

import java.net.*;
import java.net.http.HttpConnectTimeoutException;
import javax.net.ssl.*;


import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.nio.channels.*;

import net.n2p.router.App;
import net.n2p.router.https.HttpConnection.State;


public class HttpsServer implements TimeSource {

    private String _protocol;
    private boolean _useHttps;
    private Executor _executor;

    private HttpsConfigurator _httpsConfig;
    private SSLContext _sslContext;
    private HttpContextImpl _httpContext;
    
    private InetSocketAddress _address;
    private ServerSocketChannel _serverSocketChannel;
    private Selector _selector;
    private SelectionKey _listenerKey;
    private Set<HttpConnection> _idleHttpConnections;
    private Set<HttpConnection> _allHttpConnections;

    private Set<HttpConnection> _requestHttpConnections;
    private Set<HttpConnection> _responseHttpConnections;

    private List<Event> _events;
    private Object _lockObject = new Object();

    private volatile boolean _finished = false;
    private volatile boolean _terminating = false;
    private boolean _bound = false;
    private boolean _started = false;
    private volatile long _time;
    private volatile long _subticks = 0;
    private volatile long _ticks;

    final static int CLOCK_TICK = ServerConfig.getClockTick();
    final static long IDLE_INTERVAL = ServerConfig.getIdleInterval();
    final static int MAX_IDLE_CONNECTIONS = ServerConfig.getMaxIdleConnections();
    final static long TIMER_MILLIS = ServerConfig.getTimerMillis ();
    final static long MAX_REQ_TIME=getTimeMillis(ServerConfig.getMaxReqTime());
    final static long MAX_RSP_TIME=getTimeMillis(ServerConfig.getMaxRspTime());
    final static boolean _timer1Enabled = MAX_REQ_TIME != -1 || MAX_RSP_TIME != -1;

    private Timer _timer, _timer1;
    private Thread _dispatcherThread;
    Dispatcher _dispatcher;

    private int _exchangeCount = 0;




    // CONSTRUCTORS

    public HttpsServer() throws IOException {
        this(new InetSocketAddress(443), 0);
    }

    HttpsServer (InetSocketAddress inetSocketAddress, int backlog) throws IOException {
        _protocol = "https";
        _address = inetSocketAddress;
        _useHttps = true;
        _serverSocketChannel = ServerSocketChannel.open();
        if (_address != null) {
            ServerSocket socket = _serverSocketChannel.socket();
            if(App.debug()){System.out.println(_address.toString());}
            socket.bind(_address, backlog);
            _bound = true;
        }
        _selector = Selector.open();
        _serverSocketChannel.configureBlocking(false);
        _listenerKey = _serverSocketChannel.register(_selector, SelectionKey.OP_ACCEPT);
        _dispatcher = new Dispatcher();
        _idleHttpConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        _allHttpConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        _requestHttpConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        _responseHttpConnections = Collections.synchronizedSet(new HashSet<HttpConnection>());
        _time = System.currentTimeMillis();
        _timer = new Timer("httpsServer-timer", true);
        _timer.schedule(new ServerTimerTask(), CLOCK_TICK, CLOCK_TICK);
        if (_timer1Enabled) {
            _timer1 = new Timer("httpsServer-timer1", true);
            _timer1.schedule(new ServerTimerTask1(), TIMER_MILLIS, TIMER_MILLIS);
            // logger
        }
        _events = new LinkedList<Event>();
        // logger
    }

    // INNER CLASSES

    private static class DefaultExecutor implements Executor {
        public void execute(Runnable task) {
            task.run();
        }
    }

    class Dispatcher implements Runnable {

        final LinkedList<HttpConnection> _connectionsToRegister = new LinkedList<HttpConnection>();

        private void handleEvent(Event event) {
            HttpsExchangeImpl exchangeImpl = event._exchangeImpl;
            HttpConnection httpConnection = exchangeImpl.getHttpConnection();
            try {
                if (event instanceof WriteFinishedEvent) {
                    int exchanges = endExchange();
                    if (_terminating && exchanges == 0) {
                        _finished = true;
                    }
                    responseCompleted(httpConnection); 
                    LeftOverInputStream leftOverInputStream = exchangeImpl.getOriginalInputStream();
                    if (!leftOverInputStream.isEOF()) {
                        exchangeImpl._close = true;
                    }
                    if (exchangeImpl._close || _idleHttpConnections.size() >= MAX_IDLE_CONNECTIONS) {
                        httpConnection.close();
                        _allHttpConnections.remove(httpConnection);
                    } else {
                        if (leftOverInputStream.isDataBuffered()) {
                            requestStarted(httpConnection);
                            dispatchHandle(httpConnection.getChannel(), httpConnection);
                        } else {
                            _connectionsToRegister.add(httpConnection);
                        }
                    }
                }
            } catch (IOException e) {
                // logger
                e.printStackTrace();
                httpConnection.close();
            }
        }

        private void handleException(SelectionKey selectionKey, Exception exception) {
            HttpConnection httpConnection = (HttpConnection)selectionKey.attachment();
            if (exception != null) {
                // logger
                exception.printStackTrace();
            }
            closeHttpConnection(httpConnection);
        }

        private void dispatchHandle(SocketChannel socketChannel, HttpConnection httpConnection) {
            try {
                Exchange exchange = new Exchange(socketChannel, _protocol, httpConnection);
                _executor.execute(exchange);
            } catch(HttpError httpError) {
                // logger
            } catch(IOException ioException) {
                // logger
            } catch(Throwable exception) {
                // logger
            }
        }

        void reRegister(HttpConnection httpConnection) {
            try {
                SocketChannel socketChannel = httpConnection.getChannel();
                socketChannel.configureBlocking(false);
                SelectionKey selectionKey = socketChannel.register(_selector, SelectionKey.OP_READ);
                selectionKey.attach(httpConnection);
                httpConnection._selectionKey = selectionKey;
                httpConnection._time = getTime() + IDLE_INTERVAL;
                _idleHttpConnections.add(httpConnection);
            } catch (IOException ioException) {
                // logger
                httpConnection.close();
            }
        }

        public void run() {
            while (!_finished) {
                try {
                    List<Event> list = null;
                    synchronized (_lockObject) {
                        if(_events.size() > 0) {
                            list = _events;
                            _events = new LinkedList<Event>();
                        }
                    }

                    if (list != null) {
                        for (Event event : list) {
                            handleEvent(event);
                        }
                    }

                    for(HttpConnection httpConnection : _connectionsToRegister) {
                        reRegister(httpConnection);
                    }
                    _connectionsToRegister.clear();

                    _selector.select(1000);

                    /* process the selected list now */
                    Set<SelectionKey> selected = _selector.selectedKeys();
                    Iterator<SelectionKey> selectionKeyIterator = selected.iterator();
                    while(selectionKeyIterator.hasNext()) {
                        SelectionKey selectionKey = selectionKeyIterator.next();
                        selectionKeyIterator.remove();
                        if (selectionKey.equals(_listenerKey)) {
                            if (_terminating) {
                                continue;
                            }
                            SocketChannel socketChannel = _serverSocketChannel.accept();

                            // if there's a channel
                            if (socketChannel != null){
                                // logger
                                if(App.debug()) {System.out.println("found a channel");}
                                if (ServerConfig.noDelay()) {
                                    socketChannel.socket().setTcpNoDelay(true);
                                }
                                socketChannel.configureBlocking(false);
                                SelectionKey newSelectionKey = socketChannel.register(_selector, SelectionKey.OP_READ);
                                HttpConnection httpConnection = new HttpConnection();
                                httpConnection._selectionKey = newSelectionKey;
                                httpConnection.setChannel(socketChannel);
                                newSelectionKey.attach(httpConnection);
                                requestStarted(httpConnection);
                                _allHttpConnections.add(httpConnection);
                            }
                        } else {
                            try {
                                if (selectionKey.isReadable()) {
                                    SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
                                    HttpConnection httpConnection = (HttpConnection)selectionKey.attachment();

                                    selectionKey.cancel();
                                    socketChannel.configureBlocking(true);
                                    if (_idleHttpConnections.remove(httpConnection)) {
                                        requestStarted(httpConnection);
                                    }
                                    dispatchHandle(socketChannel, httpConnection);
                                } else {
                                    assert false : "Unexpected non-readable key:" + selectionKey;
                                }
                            } catch (CancelledKeyException cancelledKeyException) {
                                handleException(selectionKey, null);

                            } catch (IOException ioException) {
                                handleException(selectionKey, ioException);
                            }
                        }
                    }
                    _selector.selectNow();
                } catch (IOException ioE) {
                    // logger
                    ioE.printStackTrace();
                } catch (Exception e) {
                    // looger
                    e.printStackTrace();
                }
            }
            try {
                _selector.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        } // run()

    } // class Dispatcher

    class Exchange implements Runnable {
        SocketChannel socketChannel;
        HttpConnection httpConnection;
        HttpContextImpl httpContext;
        InputStream rawInputStream;
        OutputStream rawOutputStream;
        String protocol;
        HttpsExchangeImpl exchangeImpl;
        boolean rejected = false;

        Exchange (SocketChannel socketChannel, String protocol, HttpConnection httpConnection) throws IOException {
            this.socketChannel = socketChannel;
            this.httpConnection = httpConnection;
            this.protocol = protocol;
        }

        void reject(int code, String requestString, String message) {
            rejected = true;
            // logger
            sendReply(code, false, "<h1>"+code+Code.msg(code)+"</h1>"+message);
            closeHttpConnection(httpConnection);
        }

        void sendReply(int code, boolean closeNow, String text) {
            try {
                StringBuilder builder = new StringBuilder(512);
                builder.append("HTTP/1.1")
                    .append(code)
                    .append(Code.msg(code))
                    .append("\r\n");

                if (text != null && text.length() != 0) {
                    builder.append("Content-Length: ")
                        .append(text.length()).append("\r\n")
                        .append("Content-Type: text/html\r\n");
                } else {
                    builder.append("Content-Length: 0\r\n");
                    text = "";
                }
                if (closeNow) {
                    builder.append("Connection: close\r\n");
                }
                builder.append("\r\n").append(text);
                String string = builder.toString();
                byte[] bytes = string.getBytes("ISO889_1");
                rawOutputStream.write(bytes);
                rawOutputStream.flush();
                if(closeNow) {
                    closeHttpConnection(httpConnection);
                }
            } catch (IOException ioE) {
                // looger
                ioE.printStackTrace();
            }
        }

        public void run() {
            httpContext = httpConnection.getHttpContext();
            boolean newConnection;
            SSLEngine sslEngine = null;
            String requestLine = null;
            SSLStreams sslStreams = null;
            
            try {
                if (httpContext != null) {
                    this.rawInputStream = httpConnection.getInputStream();
                    this.rawOutputStream = httpConnection.getRawOutputStream();
                    newConnection = false;
                } else {
                    /* figure out what kind of connection it is */
                    newConnection = true;
                    if(_useHttps) {
                        if (_sslContext == null) {
                            // logger
                            throw new HttpError("No SSL context extablished");
                        }
                        sslStreams = new SSLStreams(HttpsServer.this, _sslContext, socketChannel);
                        rawInputStream = sslStreams.getInputStream();
                        rawOutputStream = sslStreams.getOutputStream();
                        sslEngine = sslStreams.getSSLEngine();
                        httpConnection._sslStreams = sslStreams;
                    } else {
                        rawInputStream = new BufferedInputStream(
                                        new Request.ReadStream(HttpsServer.this, socketChannel));
                        rawOutputStream = new Request.WriteStream(HttpsServer.this, socketChannel);

                    }
                    httpConnection._inputStream = rawInputStream;
                    httpConnection._outputStream = rawOutputStream;
                }

                Request request = new Request(rawInputStream, rawOutputStream);
                requestLine = request.requestLine();

                if(App.debug()) {System.out.println(requestLine);}
                if(requestLine == null) {
                    // logger
                    closeHttpConnection(httpConnection);
                    return;
                }
                // logger

                int space = requestLine.indexOf(' ');
                if (space == -1) {
                    reject(Code.HTTP_BAD_REQUEST, requestLine, "Bad request line");
                    return;
                }
                String method = requestLine.substring(0, space);
                int start = space+1;
                space = requestLine.indexOf(' ', start);
                if(space==-1){
                    reject(Code.HTTP_BAD_REQUEST, requestLine, "Bad request line");
                    return;
                }
                String uriString = requestLine.substring(start, space);
                URI uri = new URI (uriString);
                start = space+1;
                String version = requestLine.substring(start);
                Headers headers = request.headers();
                String string = headers.getFirst("Transfer-encoding");
                long clen = 0L;
                if(string != null && string.equalsIgnoreCase("chunked")) {
                    clen = -1L;
                } else {
                    string = headers.getFirst("Content-Length");
                    if (string!=null) {
                        clen = Long.parseLong(string);
                    }
                    if (clen == 0) {
                        requestCompleted(httpConnection);
                    }
                }
                httpContext = _httpContext;
                if (httpContext == null) {
                    reject (Code.HTTP_NOT_FOUND,
                            requestLine, "No context found for request");
                    return;
                }
                httpConnection.setContext(httpContext);
                if (httpContext.getHttpHandler() == null) {
                    reject(Code.HTTP_INTERNAL_ERROR, requestLine, "No handler");
                }
                exchangeImpl = new HttpsExchangeImpl(method, uri, request, clen, httpConnection);
                String chdr = headers.getFirst("Connection");
                Headers responseHeaders = exchangeImpl.getResponseHeaders();

                if (chdr != null && chdr.equalsIgnoreCase("close")) {
                    exchangeImpl._close = true;
                }
                if (version.equalsIgnoreCase("http/1.0")) {
                    exchangeImpl._http10 = true;
                    if (chdr == null) {
                        exchangeImpl._close = true;
                        responseHeaders.set("Connection", "close");
                    } else if (chdr.equalsIgnoreCase ("keep-alive")) {
                        responseHeaders.set("Connection", "keep-alive");
                        int idle=(int)(ServerConfig.getIdleInterval()/1000);
                        int max=ServerConfig.getMaxIdleConnections();
                        String val = "timeout="+idle+", max="+max;
                        responseHeaders.set("Keep-Alive", val);
                    }
                }

                if (newConnection) {
                    httpConnection.setParameters(rawInputStream, 
                                                rawOutputStream, 
                                                socketChannel, 
                                                sslEngine, 
                                                sslStreams, 
                                                _sslContext,
                                                httpContext,
                                                rawInputStream);
                }

                String expect = headers.getFirst("Expect");
                if (expect != null && expect.equalsIgnoreCase("100-continue")) {
                    sendReply(Code.HTTP_CONTINUE, false, null);
                }
                exchangeImpl.getRequestBody();
                exchangeImpl.getResponseBody();

                HttpHandler handler = httpContext.getHttpHandler();
                handler.handle(exchangeImpl);

            } catch(IOException ioE) {
                // logger
                ioE.printStackTrace();
                closeHttpConnection(httpConnection);
            } catch(NumberFormatException nfE) {
                // logger
                nfE.printStackTrace();
                reject (Code.HTTP_BAD_REQUEST, requestLine, "NumberFormatException thrown");
            } catch (URISyntaxException usE) {
                // logger
                usE.printStackTrace();
                reject (Code.HTTP_BAD_REQUEST, requestLine, "URISyntaxException thrown");
            } catch (Exception e) {
                // logger
                e.printStackTrace();
                closeHttpConnection(httpConnection);
            }
        } // run()

    }       // class Exchange

    class ServerTimerTask extends TimerTask {
        public void run() {
            LinkedList<HttpConnection> toClose = new LinkedList<HttpConnection>();
            _time = System.currentTimeMillis();
            _ticks++;
            synchronized (_idleHttpConnections) {
                for (HttpConnection httpConnection : _idleHttpConnections) {
                    if (httpConnection._time <= _time) {
                        toClose.add(httpConnection);
                    }
                }
                for (HttpConnection httpConnection : toClose) {
                    _idleHttpConnections.remove(httpConnection);
                    _allHttpConnections.remove(httpConnection);
                    httpConnection.close();
                }
            }
        }
    }

    class ServerTimerTask1 extends TimerTask {
        public void run() {
            LinkedList<HttpConnection> toClose = new LinkedList<HttpConnection>();
            _time = System.currentTimeMillis();
            synchronized (_requestHttpConnections) {
                if (MAX_REQ_TIME != -1) {
                    for (HttpConnection httpConnection : _requestHttpConnections) {
                        if (httpConnection._creationTime + TIMER_MILLIS + MAX_REQ_TIME <= _time) {
                            toClose.add(httpConnection);
                        }
                    }
                    for (HttpConnection httpConnection : toClose) {
                        // logger
                        _requestHttpConnections.remove(httpConnection);
                        _allHttpConnections.remove(httpConnection);
                        httpConnection.close();
                    }
                }
            }
            toClose = new LinkedList<HttpConnection>();
            synchronized (_responseHttpConnections) {
                if (MAX_RSP_TIME != -1) {
                    for(HttpConnection httpConnection : _responseHttpConnections) {
                        if (httpConnection._responseStartTime + TIMER_MILLIS + MAX_REQ_TIME <= _time) {
                            toClose.add(httpConnection);
                        }
                    }
                    for (HttpConnection httpConnection : toClose) {
                        // logger
                        _responseHttpConnections.remove(httpConnection);
                        _allHttpConnections.remove(httpConnection);
                        httpConnection.close();
                    }
                }
            }
        }
    }


    // METHODS - INNER CLASSES

    void addEvent(Event event) {
        synchronized (_lockObject) {
            _events.add(event);
            _selector.wakeup();
        }
    }

    synchronized void startExchange() {
        // logger
        if(App.debug()) {System.out.println("startExchange");}
        _exchangeCount++;
    }

    synchronized int endExchange() {
        _exchangeCount--;
        assert _exchangeCount >= 0;
        return _exchangeCount;
    }


    // METHODS

    public void bind(InetSocketAddress inetSocketAddress, int backlog) throws IOException {
        if (_bound) {
            throw new BindException("HttpServer already bound");
        }
        if (inetSocketAddress == null) {
            throw new NullPointerException("null address");
        }
        ServerSocket socket = _serverSocketChannel.socket();
        socket.bind(inetSocketAddress, backlog);
        _bound = true;
    }

    public void start() {
        if (!_bound || _started || _finished) {
            throw new IllegalStateException("Server in wrong state");
        }
        if (_executor == null) {
            _executor = new DefaultExecutor();
        }
        _dispatcherThread = new Thread(null, _dispatcher, "HTTP-Dispatcher", 0, false);
        _started = true;
        _dispatcherThread.start();
    }

    public void setHttpsConfigurator(HttpsConfigurator config) {
        if (config == null) {
            throw new NullPointerException("null HttpsConfigurator");
        }
        if (_started) {
            throw new IllegalStateException ("server already started");
        }
        _httpsConfig = config;
        _sslContext = config.getSSLContext();
    }

    public HttpsConfigurator getHttpsConfigurator() {
        return _httpsConfig;
    }

    public final boolean isFinishing() {
        return _finished;
    }

    public void stop(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("negative delay parameter");
        }
        _terminating = true;
        try {
            _serverSocketChannel.close();
        } catch(IOException ioException) {

        }
        _selector.wakeup();
        long latest = System.currentTimeMillis() + delay * 10000;
        while(System.currentTimeMillis() < latest) {
            delay();
            if (_finished) {
                break;
            }
        }
        _finished = true;
        _selector.wakeup();
        synchronized(_allHttpConnections) {
            for (HttpConnection httpConnection : _allHttpConnections) {
                httpConnection.close();
            }
        }
        _allHttpConnections.clear();
        _idleHttpConnections.clear();
        _timer.cancel();
        if (_timer1Enabled) {
            _timer1.cancel();
        }
        if (_dispatcherThread != null && _dispatcherThread != Thread.currentThread()) {
            try {
                _dispatcherThread.join();
            } catch(InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                // logger
            }
        }
    }

    public synchronized HttpContextImpl createHttpContext(HttpHandler handler) {
        if (handler == null) {
            throw new NullPointerException("null handler");
        }
        HttpContextImpl context = new HttpContextImpl(handler, this);
        if(App.debug()) {System.out.println("HttpContextImpl context created:");}
        if(App.debug()) {System.out.println(context.getHttpsServer().toString());}
        _httpContext = context;
        if(App.debug()) {System.out.println("_httpContext created: "+_httpContext.toString());}
        return _httpContext;
    }

    public InetSocketAddress getAddress() {
        return (InetSocketAddress)_serverSocketChannel.socket()
                                .getLocalSocketAddress();
    }

    Selector getSelector() {
        return _selector;
    }

    private void closeHttpConnection(HttpConnection httpConnection) {
        httpConnection.close();
        _allHttpConnections.remove(httpConnection);
        switch(httpConnection.getState()) {
            case REQUEST:
                _requestHttpConnections.remove(httpConnection);
                break;
            case RESPONSE:
                _responseHttpConnections.remove(httpConnection);
                break;
            case IDLE:
                _idleHttpConnections.remove(httpConnection);
                break;
        }
        assert !_requestHttpConnections.remove(httpConnection);
        assert !_responseHttpConnections.remove(httpConnection);
        assert !_idleHttpConnections.remove(httpConnection);
    }

    void requestStarted(HttpConnection httpConnection) {
        httpConnection._creationTime = getTime();
        httpConnection.setState(State.REQUEST);
        _requestHttpConnections.add(httpConnection);
    }

    void requestCompleted(HttpConnection httpConnection) {
        State state = httpConnection.getState();
        assert state == State.REQUEST : "State is not REQUEST (" + state + ")";
        _requestHttpConnections.remove(httpConnection);
        httpConnection._responseStartTime = getTime();
        _responseHttpConnections.add(httpConnection);
        httpConnection.setState(State.RESPONSE);
    }

    void responseCompleted(HttpConnection httpConnection) {
        State state = httpConnection.getState();
        assert state == State.RESPONSE : "Stateis not RESPONSE (" + state + ")";
        _responseHttpConnections.remove(httpConnection);
        httpConnection.setState(State.IDLE);
    }

    static long getTimeMillis(long seconds) {
        if (seconds == -1) {
            return -1;
        } else {
            return seconds * 10000;
        }
    }

    void delay() {
        Thread.yield();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {

        }
    }

    public long getTime() {
        return _time;
    }

}
