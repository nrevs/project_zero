package net.n2p.router;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.*;

import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import net.n2p.router.crypto.CertificateManager;
import net.n2p.router.networkdb.RouterInfo;

public class CryptoManager {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 4096;

    private static final String BC_PROVIDER = "BC";

    private static KeyStore _theKeyStore;

    
    public CryptoManager() throws KeyStoreException, 
                            NoSuchAlgorithmException, 
                            OperatorCreationException, 
                            CertificateException, 
                            IOException, 
                            NoSuchProviderException, 
                            InvalidAlgorithmParameterException {

        // logger
        if(App.debug()) {System.out.println("CryptoManager() called");}
        try {
            String keystorePath = App.n2pDirPathStr+"/"+"startup.jks";
            if(App.debug()) {System.out.println("keystorePath: "+keystorePath);}
            File file = new File(keystorePath);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(App.n2pDirPathStr+"/"+"startup.jks");
                KeyStore ks = KeyStore.getInstance("PKCS12", BC_PROVIDER);
                ks.load(fis, DefaultPassphrase.getPassphrase());
                if(App.debug()) {System.out.println("startup.jks: "+ks.toString());}
                if (!( ks.containsAlias("projzero") && ks.containsAlias("issued-cert"))) {
                    makeNewKeyStore();
                } else {
                    if(App.debug()) {System.out.println("keystore startup with aliases projzero and ssl already exists");}
                    _theKeyStore = ks;
                }
            } else {
                makeNewKeyStore();
            }
            if(App.debug()) {System.out.println("....");}
        } catch (IOException ioE) {
            // logger
            ioE.printStackTrace();
        } catch (NoSuchAlgorithmException nsaE) {
            // logger
            nsaE.printStackTrace();
        } catch (CertificateException cE) {
            // logger
            cE.printStackTrace();
        }
        
    }

    public static KeyStore getTheKeyStore(){
        return _theKeyStore;
    }

    public KeyPair getKeyPair() 
        throws NoSuchAlgorithmException, 
                NoSuchProviderException, 
                InvalidAlgorithmParameterException {
            
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);//(KEY_ALGORITHM);
        //ECGenParameterSpec spec = new ECGenParameterSpec("secp256r1");
        SecureRandom random = new SecureRandom();//.getInstance("SHA1PRNG");
        kpg.initialize(KEY_SIZE, random);
        KeyPair keyPair = kpg.generateKeyPair();
        return keyPair;
    }

    public static SecureRandom getRandom(){
        return new SecureRandom();
    }

    private void makeNewKeyStore() 
        throws NoSuchAlgorithmException, 
                NoSuchProviderException, 
                InvalidAlgorithmParameterException, 
                OperatorCreationException, 
                CertificateException, 
                KeyStoreException, 
                IOException {

        if(App.debug()){System.out.println("making new keystore");}
        // we need to make projzero keystore/alias
        KeyPair rootKeys = getKeyPair();
        KeyPair intKeys = getKeyPair();
        KeyPair clKeys = getKeyPair();
        
        X509Certificate rootCert = CertificateManager.generateRootCertificate(rootKeys, SIGNATURE_ALGORITHM);
        PKCS10CertificationRequest intCSR = CertificateManager.generateCSR(intKeys, rootKeys, SIGNATURE_ALGORITHM, "inter-cert");
        X509Certificate intCert = CertificateManager.getSignedCertFromCSR(intCSR, rootCert, rootKeys);
        PKCS10CertificationRequest clCSR = CertificateManager.generateCSR(clKeys, intKeys, SIGNATURE_ALGORITHM, "client-cert");
        X509Certificate clCert = CertificateManager.getSignedCertFromCSR(clCSR, intCert, intKeys);
        
        //ORDER MATTERS for certChain!!!
        X509Certificate[] certChain = {
            clCert,
            intCert,
            rootCert
        };
        KeyStore ks = KeyStore.getInstance("PKCS12", BC_PROVIDER);
        ks.load(null, null);
        ks.setKeyEntry("projzero", clKeys.getPrivate(), DefaultPassphrase.getPassphrase(), certChain);
        ks.setCertificateEntry("issued-cert", clCert);
        
        try (FileOutputStream fos = new FileOutputStream(App.n2pDirPathStr+"/"+"startup.jks")) {
            ks.store(fos, DefaultPassphrase.getPassphrase());
            // logger
            if(App.debug()){System.out.println("saving keyfile to .n2p/");}
        }
        _theKeyStore = ks;

        if (App.store()) {
            RouterInfo ri = new RouterInfo(clCert);
            DatabaseManager.setRouterInfo(ri);
        }
    }

    public static byte[] makeHash(byte[] msgBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(msgBytes);
        byte[] digest = md.digest();
        return digest;
    }

}
