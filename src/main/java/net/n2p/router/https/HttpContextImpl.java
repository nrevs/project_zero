package net.n2p.router.https;


public class HttpContextImpl {
    private String _protocol = "https";
    private HttpHandler _httpHandler;
    private HttpsServer _httpsServer;

    HttpContextImpl(HttpHandler httpHandler, HttpsServer httpsServer) {
        _httpHandler = httpHandler;
        _httpsServer = httpsServer;
    }

    public HttpHandler getHttpHandler() {
        return _httpHandler;
    }

    public void setHttpHandler(HttpHandler httpHandler) {
        if(httpHandler == null) {
            throw new NullPointerException ("Null handler parameter");
        }
        if (_httpHandler != null) {
            throw new IllegalArgumentException ("handler already set");
        }
        _httpHandler = httpHandler;
    }
    

    public HttpsServer getHttpsServer () {
        return _httpsServer;
    }

}
