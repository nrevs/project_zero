package net.n2p.router.networkdb;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class AddressDAO {
    Connection connection;



    public AddressDAO(Connection connection) {
        this.connection = connection;
    }



    public void insert(Address address) {
        try {
            PreparedStatement pStatement = connection.prepareStatement("insert into addresses(host, port) values (?, ?)", Statement.RETURN_GENERATED_KEYS);
            pStatement.setString(1, address.getHost());
            pStatement.setInt(2, address.getPort());
            pStatement.execute();
        } catch (SQLException sqE) {
            sqE.printStackTrace();
        }
    }



    public List<InetSocketAddress> getAll() {
        List<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
        try {
            PreparedStatement pStatement = connection.prepareStatement(
                "select * from addresses");
            ResultSet rSet = pStatement.executeQuery();
            while (rSet.next()) {
                Address adrs = new Address(rSet.getString("host"), rSet.getInt("port"));
                addresses.add(adrs.getInetSocketAddress());
            }
        } catch(SQLException sqE) {
            sqE.printStackTrace();
        }
        return addresses;
    }

}
