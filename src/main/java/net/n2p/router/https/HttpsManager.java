package net.n2p.router.https;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpClient.*;
import java.net.http.HttpRequest.*;
import java.net.http.HttpResponse.*;
import java.security.KeyStore;
import java.time.Duration;

import javax.net.ssl.*;

import net.n2p.router.App;
import net.n2p.router.CryptoManager;
import net.n2p.router.DefaultPassphrase;

public class HttpsManager {

    private ServerHandler _handler;

    private SSLContext _sslContext;
    private KeyManagerFactory _keyManagerFactory;
    private KeyStore _keyStore;
    //private final char[] _passphrase = "projzero".toCharArray(); // "passphrase"
    private HttpsServer _server;
    private HttpContextImpl _context;

    private HttpClient _httpClient;


    public HttpsManager() {
        try {
            if(App.debug()){System.out.println("entered HttpsManager()");}
            _handler = new ServerHandler(this);
            _keyManagerFactory = KeyManagerFactory.getInstance("SUNX509");
            _keyStore = KeyStore.getInstance("JKS");

            _keyStore = CryptoManager.getTheKeyStore();
            //_keyStore.load(getClass().getClassLoader().getResourceAsStream("keystore.jks"), DefaultPassphrase.getPassphrase()); //testkeys
            

            _keyManagerFactory.init(_keyStore, DefaultPassphrase.getPassphrase());
            _sslContext = SSLContext.getInstance("TLSv1.3");
            _sslContext.init(_keyManagerFactory.getKeyManagers(), null, CryptoManager.getRandom());
            

            _server = new HttpsServer();
            _server.setHttpsConfigurator(new HttpsConfigurator(_sslContext));
            _context = _server.createHttpContext(_handler);

            _httpClient = HttpClient.newBuilder()
                            .version(Version.HTTP_2)
                            .followRedirects(Redirect.NORMAL)
                            .connectTimeout(Duration.ofSeconds(20))
                            .build();

        } catch(Exception e) {
            e.printStackTrace();
        }

    }



    public void start() {
        this._server.start();
    }


    public void send(InetSocketAddress address, InputStream is, ClientHandler handler) throws IOException {

        HttpRequest request = HttpRequest.newBuilder()
                                            .uri(URI.create("https://"+address.toString()))
                                            .header("Content-Type","text/html")
                                            .POST(BodyPublishers.ofByteArray(is.readAllBytes()))
                                            .build();
        ClientRunner clientRunner =  new ClientRunner(_httpClient, request, handler);
        Thread t = new Thread(clientRunner);
        t.start();
    }

    private class ClientRunner implements Runnable {
        volatile HttpClient httpClient;
        volatile HttpRequest request;
        volatile ClientHandler handler;

        ClientRunner(HttpClient client, HttpRequest request, ClientHandler handler) {
            this.httpClient = client;
            this.request = request;
            this.handler = handler;
        }

        public void run() {
            try {
                HttpResponse<byte[]> response = httpClient.send(request, BodyHandlers.ofByteArray());
                handler.handle(response);
            } catch (InterruptedException ie) {
                // logger
            } catch (IOException ioe) {
                // logger
            }
        }
    }

}
