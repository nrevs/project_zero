package net.n2p.router.https;

import java.io.*;
import java.net.InetSocketAddress;

import net.n2p.router.App;
import net.n2p.router.DatabaseManager;

public class ServerHandler implements HttpHandler {
    

    private HttpsManager _httpsManager;

    public ServerHandler(HttpsManager httpsManager) {
    }
    
    public void handle(HttpsExchangeImpl exchange) throws IOException {
        System.out.println("ServerHandler is handling it!");


        if(App.dummy()) {
            if(App.store()) {
                InetSocketAddress adrs = exchange.getRemoteAddress();
                DatabaseManager.insertAddress(adrs);
            }
            _httpsManager.send(new InetSocketAddress(App.dummyHost(),443),exchange.getRequestBody(),new ClientHandler(exchange));
        }
    }
}
