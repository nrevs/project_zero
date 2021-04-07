package net.n2p.router;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import net.n2p.router.networkdb.Address;
import net.n2p.router.networkdb.AddressDAO;
import net.n2p.router.networkdb.RouterInfo;
import net.n2p.router.networkdb.RouterInfoDAO;

public class DatabaseManager {

    private static AddressDAO _aDao;
    private static RouterInfoDAO _riDao;

    public DatabaseManager() {
        String url = "jdbc:postgresql://localhost:5432/projzero";
        String username = "projzero";
        String password = "projzero";

        try {
            Connection connection = DriverManager.getConnection (url, username, password);
            _aDao = new AddressDAO(connection);
            _riDao = new RouterInfoDAO(connection);

        } catch (SQLException e) {
            // logger
            e.printStackTrace();
        }
    }

    public static void setRouterInfo(RouterInfo ri) {
        _riDao.update(ri);
    }

    public static RouterInfo getRouterInfo(byte[] hash) {
        return _riDao.get(hash);
    }

    public static List<RouterInfo> getAllRouterInfo() {
        return _riDao.getAll();
    }
    
    public static void insertAddress(InetSocketAddress inetSocketAddress) {
        Address address = new Address(inetSocketAddress);
        _aDao.insert(address);
    }

    public static List<InetSocketAddress> getAddresses() {
        return _aDao.getAll();
    }
}
