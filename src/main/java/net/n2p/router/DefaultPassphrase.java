package net.n2p.router;



//Singleton Pattern
public class DefaultPassphrase {
    private static DefaultPassphrase instance;
    private static char[] _passphrase = "projzero".toCharArray();

    private DefaultPassphrase() {

    }

    public static DefaultPassphrase getInstance() {
        if (instance == null) {
            instance = new DefaultPassphrase();
        }
        return instance;
    }

    public static char[] getPassphrase() {
        return _passphrase;
    }

    public static void setNewPassphrase(char[] newPassphrase) {
        _passphrase = newPassphrase;
    }
}
