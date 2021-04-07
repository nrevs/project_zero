package net.n2p.router.networkdb;

import java.net.InetSocketAddress;

public class Address {
    private String _host;
    private int _port;
    private InetSocketAddress _isa;


    public Address(String host, int port) {
        _host = host;
        _port = port;
        _isa = new InetSocketAddress(_host, _port);
    }

    public Address(InetSocketAddress inetSocketAddress) {
        _host = inetSocketAddress.getHostName();
        _port = inetSocketAddress.getPort();
        _isa = new InetSocketAddress(_host, _port);
    }

    public String getHost() {
        return _host;
    }

    public int getPort() {
        return _port;
    }

    public InetSocketAddress getInetSocketAddress() {
        return _isa;
    }
}
