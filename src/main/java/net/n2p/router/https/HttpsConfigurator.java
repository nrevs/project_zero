package net.n2p.router.https;


import javax.net.ssl.*;

public class HttpsConfigurator {
    private SSLContext _sslContext;

    public HttpsConfigurator (SSLContext context) {
        if (context == null) {
            throw new NullPointerException ("null SSLContext");
        }
        _sslContext = context;
    }

    public SSLContext getSSLContext() {
        return _sslContext;
    }

    public void configure(HttpsParameters params) {
        params.setSSLParameters(getSSLContext().getDefaultSSLParameters());
    }

}
