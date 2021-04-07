package net.n2p.router.networkdb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import net.n2p.router.App;
import net.n2p.router.CryptoManager;

public class RouterInfo {

    private byte[] _hash;
    private X509Certificate _cert;

    public RouterInfo(X509Certificate certificate) throws NoSuchAlgorithmException {
        _cert = certificate;
        PublicKey pk = certificate.getPublicKey();
        _hash = CryptoManager.makeHash(pk.getEncoded());
    }

    public RouterInfo(byte[] hash, byte[] cert) {
        try {
            makeCertFromBytes(cert);
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            if(App.debug()) {e.printStackTrace();}
        }
        _hash = hash;
    }

    public byte[] getCertBytes() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;
        byte[] certBytes = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(_cert);
            out.flush();
            certBytes = bos.toByteArray();

        } catch (IOException ioE) {
            if(App.debug()) {ioE.printStackTrace();}
        }finally {
            try {
                bos.close();
            } catch (IOException ioE) {
                if(App.debug()) {ioE.printStackTrace();}
            }
        }
        return certBytes;
    }

    //public certFromBytes(
    private void makeCertFromBytes(byte[] bytes) throws ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInput in = null;
        try {
            in = new ObjectInputStream(bis);
            X509Certificate _cert = (X509Certificate) in.readObject();
        } catch(IOException ioE) {
            if(App.debug()) {ioE.printStackTrace();}
        } finally {
            try {
                if(in != null) {
                    in.close();
                }
            } catch (IOException ioE) {
                if(App.debug()) {ioE.printStackTrace();}
            }
        } 
    }

    public X509Certificate getCert() {
        return _cert;
    }

    public byte[] getHash() {
        return _hash;
    }


}