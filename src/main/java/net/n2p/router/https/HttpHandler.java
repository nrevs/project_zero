package net.n2p.router.https;

import java.io.*;

public interface HttpHandler {
    
    public abstract void handle(HttpsExchangeImpl exchange) throws IOException;
    
}
