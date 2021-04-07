package net.n2p.router;


import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import org.bouncycastle.operator.OperatorCreationException;

import net.n2p.router.https.*;

public class Router {
    
    private RouterContext _routerContext;
    private HttpsManager _httpsManager;
    private ServerHandler _serverHandler;
    private CryptoManager _cryptoManager;
    private DatabaseManager _databaseManager;

    public Router() {
        _databaseManager = new DatabaseManager();

        try {
            _cryptoManager = new CryptoManager();
        } catch (KeyStoreException | NoSuchAlgorithmException | OperatorCreationException | CertificateException
                | IOException e) {
            // TODO Auto-generated catch block
            if(App.debug()) {e.printStackTrace();}
        } catch (NoSuchProviderException e) {
            // TODO Auto-generated catch block
            if(App.debug()) {e.printStackTrace();}
        } catch (InvalidAlgorithmParameterException e) {
            // TODO Auto-generated catch block
            if(App.debug()) {e.printStackTrace();}
        }

        
        this._routerContext = new RouterContext(this);
        this._httpsManager = new HttpsManager();

        
    }

    public void runRouter() {
        this._httpsManager.start();
    }

}