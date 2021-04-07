package net.n2p.router.https;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;

import net.n2p.router.App;
import net.n2p.router.DatabaseManager;

public class ClientHandler {
    private HttpsExchangeImpl _exchange;

    public ClientHandler(HttpsExchangeImpl exchange) {
        _exchange = exchange;
    }

    public ClientHandler() {}

    public void handle(HttpResponse<byte[]> bytes) {
        if(App.debug()){
            if(App.debug()) {System.out.println(bytes.toString());}
        }

        if (App.dummy()) {
            if (_exchange != null) {
                try {
                    if (App.store()) {
                        InetSocketAddress adrs = _exchange.getRemoteAddress();
                        DatabaseManager.insertAddress(adrs);
                    }
                    _exchange.sendResponseHeaders(bytes.statusCode(), 0);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
}
