package net.n2p.router.crypto;

import java.io.IOException;

// based off of https://gist.github.com/vivekkr12/c74f7ee08593a8c606ed96f4b62a208a

import java.math.BigInteger;
import java.security.*;
import java.util.*;


import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import net.n2p.router.App;




public class CertificateManager {


    private static final String BC_PROVIDER = "BC";
    private static final String KEY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private static Calendar _calendar;
    private static Date _startDate, _endDate;


    public static X509Certificate generateRootCertificate(KeyPair kp, String signAlgo) 
        throws OperatorCreationException, 
                NoSuchAlgorithmException, 
                CertIOException, 
                CertificateException  
        {
        //Security.addProvider(new BouncyCastleProvider());

        // Initialize a new KeyPair generator
        //KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, BC_PROVIDER);
        
        // Setup start date to yesterday and end date for 1 year validity
        _calendar = Calendar.getInstance();
        _calendar.add(Calendar.DATE, -1);
        _startDate = _calendar.getTime();

        _calendar.add(Calendar.YEAR, 1);
        _endDate = _calendar.getTime();

        // First step is to create a root certificate
        // First Generate a KeyPair,
        // then a random serial number
        // then generate a certificate using the KeyPair
        BigInteger rootSerialNum = new BigInteger(Long.toString(new SecureRandom().nextLong()));
        
        // Issued By and Issued To same for root certificate
        X500Name rootCertIssuer = new X500Name("CN=root-cert");
        X500Name rootCertSubject = rootCertIssuer;
        ContentSigner rootCertContentSigner = 
            new JcaContentSignerBuilder(signAlgo).build(kp.getPrivate());//.setProvider(BC_PROVIDER).build(kp.getPrivate());
        X509v3CertificateBuilder rootCertBuilder = 
            new JcaX509v3CertificateBuilder(rootCertIssuer, 
                                            rootSerialNum, 
                                            _startDate, 
                                            _endDate, 
                                            rootCertSubject, 
                                            kp.getPublic());

        // Add Extensions
        // A BasicConstraint to mark root certificate as CA certificate
        JcaX509ExtensionUtils rootCertExtUtils = new JcaX509ExtensionUtils();
        rootCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        rootCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, rootCertExtUtils.createSubjectKeyIdentifier(kp.getPublic()));

        rootCertBuilder.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.keyEncipherment));


        // Create a cert holder and export to X509Certificate
        X509CertificateHolder rootCertHolder = rootCertBuilder.build(rootCertContentSigner);
        X509Certificate rootCert = new JcaX509CertificateConverter().getCertificate(rootCertHolder);//.setProvider(BC_PROVIDER).getCertificate(rootCertHolder);
        return rootCert;
    }

    public static PKCS10CertificationRequest generateCSR(KeyPair issKP, KeyPair signerKP, String signAlgo, String subject)
        throws OperatorCreationException 
        {
        X500Name issuedCertSubject = new X500Name("CN="+subject);
        
        PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder(issuedCertSubject, issKP.getPublic());
        JcaContentSignerBuilder csrBuilder = new JcaContentSignerBuilder(signAlgo);//.setProvider(BC_PROVIDER);
    
        // Sign the new KeyPair with the root cert Private Key
        ContentSigner csrContentSigner = csrBuilder.build(signerKP.getPrivate());
        PKCS10CertificationRequest csr = p10Builder.build(csrContentSigner);
        return csr;
    }

    public static X509Certificate getSignedCertFromCSR(PKCS10CertificationRequest csr, 
                            X509Certificate signerCert, KeyPair signerKP) 
            throws NoSuchAlgorithmException, 
                    OperatorCreationException, 
                    CertificateException, IOException 
        {

        _calendar = Calendar.getInstance();
        _calendar.add(Calendar.DATE, -1);
        _startDate = _calendar.getTime();

        _calendar.add(Calendar.YEAR, 1);
        _endDate = _calendar.getTime();

        JcaX509CertificateHolder jch = new JcaX509CertificateHolder(signerCert);
        X500Name rci = jch.getSubject();
        BigInteger icsn = jch.getSerialNumber();
        // Use the Signed KeyPair and CSR to generate an issued Certificate
        // Here serial number is randomly generated. In general, CAs use
        // a sequence to generate Serial number and avoid collisions

        X509v3CertificateBuilder issuedCertBuilder = 
            new X509v3CertificateBuilder(rci, 
                                        icsn, 
                                        _startDate, 
                                        _endDate, 
                                        csr.getSubject(), 
                                        csr.getSubjectPublicKeyInfo());

        JcaX509ExtensionUtils issuedCertExtUtils = new JcaX509ExtensionUtils();

            // Add Extensions
        // Use BasicConstraints to say that this Cert is not a CA
        issuedCertBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        // Add Issuer cert identifier as Extension
        issuedCertBuilder.addExtension(Extension.authorityKeyIdentifier, false, issuedCertExtUtils.createAuthorityKeyIdentifier(signerCert));
        issuedCertBuilder.addExtension(Extension.subjectKeyIdentifier, false, issuedCertExtUtils.createSubjectKeyIdentifier(csr.getSubjectPublicKeyInfo()));

        // Add intended key usage extension if needed
        issuedCertBuilder.addExtension(Extension.keyUsage, false, new KeyUsage(KeyUsage.keyAgreement | KeyUsage.digitalSignature).getEncoded());
        ExtendedKeyUsage exUsage = new ExtendedKeyUsage(new KeyPurposeId[] {
            KeyPurposeId.id_kp_serverAuth,
            KeyPurposeId.id_kp_clientAuth
        });
        issuedCertBuilder.addExtension(Extension.extendedKeyUsage, false, exUsage.getEncoded());
        // Add DNS name is cert is to used for SSL
        issuedCertBuilder.addExtension(Extension.subjectAlternativeName, false, new DERSequence(new ASN1Encodable[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1")
        }));

        // Sign the new KeyPair with the root cert Private Key
        if(App.debug()){System.out.println(csr.getSignatureAlgorithm().toString());}
        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");//csr.getSignatureAlgorithm().toString()); //.setProvider(BC_PROVIDER);

        ContentSigner contentSigner =  csBuilder.build(signerKP.getPrivate());    
        
        //= csrBuilder.build(rootKeyPair.getPrivate());
        //KCS10CertificationRequest csr = p10Builder.build(csrContentSigner);
        X509CertificateHolder issuedCertHolder = issuedCertBuilder.build(contentSigner);
        X509Certificate issuedCert  = new JcaX509CertificateConverter().getCertificate(issuedCertHolder); //.setProvider(BC_PROVIDER).getCertificate(issuedCertHolder);
        return issuedCert;
    }
    
    public static boolean verify(X509Certificate issCert, X509Certificate rootCert) {
        try {
            issCert.verify(rootCert.getPublicKey());
            return true;
        } catch(NoSuchAlgorithmException nsA) {
            // logger
            return false;
        } catch(InvalidKeyException ivkE) {
            // logger
            return false;
        } catch(NoSuchProviderException nspE) {
            // logger
            return false;
        } catch(SignatureException sE) {
            // logger
            return false;
        } catch(CertificateException cE) {
            // logger
            return false;
        }
    }
}







































