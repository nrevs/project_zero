package net.n2p.router.https;


import java.net.InetSocketAddress;
import javax.net.ssl.SSLParameters;

public abstract class HttpsParameters {
    private String[] cipherSuites;
    private String[] protocols;
    private boolean wantClientAuth;
    private boolean needClientAuth;

    protected HttpsParameters() {}
    /**
     * Returns the HttpsConfigurator for this HttpsParameters.
     */
    public abstract HttpsConfigurator getHttpsConfigurator();

    /**
     * Returns the address of the remote client initiating the
     * connection.
     */
    public abstract InetSocketAddress getClientAddress();

//BEGIN_TIGER_EXCLUDE
    /**
     * Sets the SSLParameters to use for this HttpsParameters.
     * The parameters must be supported by the SSLContext contained
     * by the HttpsConfigurator associated with this HttpsParameters.
     * If no parameters are set, then the default behavior is to use
     * the default parameters from the associated SSLContext.
     * @param params the SSLParameters to set. If <code>null</code>
     * then the existing parameters (if any) remain unchanged.
     * @throws IllegalArgumentException if any of the parameters are
     *   invalid or unsupported.
     */
    public abstract void setSSLParameters (SSLParameters params);
//END_TIGER_EXCLUDE

    /**
     * Returns a copy of the array of ciphersuites or null if none
     * have been set.
     *
     * @return a copy of the array of ciphersuites or null if none
     * have been set.
     */
    public String[] getCipherSuites() {
        return cipherSuites != null ? cipherSuites.clone() : null;
    }

    /**
     * Sets the array of ciphersuites.
     *
     * @param cipherSuites the array of ciphersuites (or null)
     */
    public void setCipherSuites(String[] cipherSuites) {
        this.cipherSuites = cipherSuites != null ? cipherSuites.clone() : null;
    }

    /**
     * Returns a copy of the array of protocols or null if none
     * have been set.
     *
     * @return a copy of the array of protocols or null if none
     * have been set.
     */
    public String[] getProtocols() {
        return protocols != null ? protocols.clone() : null;
    }

    /**
     * Sets the array of protocols.
     *
     * @param protocols the array of protocols (or null)
     */
    public void setProtocols(String[] protocols) {
        this.protocols = protocols != null ? protocols.clone() : null;
    }

    /**
     * Returns whether client authentication should be requested.
     *
     * @return whether client authentication should be requested.
     */
    public boolean getWantClientAuth() {
        return wantClientAuth;
    }

    /**
     * Sets whether client authentication should be requested. Calling
     * this method clears the <code>needClientAuth</code> flag.
     *
     * @param wantClientAuth whether client authentication should be requested
     */
    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

    /**
     * Returns whether client authentication should be required.
     *
     * @return whether client authentication should be required.
     */
    public boolean getNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * Sets whether client authentication should be required. Calling
     * this method clears the <code>wantClientAuth</code> flag.
     *
     * @param needClientAuth whether client authentication should be required
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }
}
