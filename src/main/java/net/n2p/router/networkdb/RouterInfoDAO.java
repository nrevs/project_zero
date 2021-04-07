package net.n2p.router.networkdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.n2p.router.App;

public class RouterInfoDAO {
    Connection connection;

    public RouterInfoDAO(Connection connection) {
        this.connection = connection;
    }

    
    public void update(RouterInfo ri) {
        try {
            PreparedStatement pStatement0 = connection.prepareStatement(
                "delete * from routerinfo");
            pStatement0.executeUpdate();
            PreparedStatement pStatement1 = connection.prepareStatement(
                "insert into routerinfo(hash, certificate) values (?. ?)");
            pStatement1.setBytes(1, ri.getHash());
            pStatement1.setBytes(2, ri.getCertBytes());
            pStatement1.executeUpdate();
        } catch(SQLException sqE) {
            if(App.debug()) {sqE.printStackTrace();}
        }
    }

    public RouterInfo get(byte[] hash) {
        try{
            PreparedStatement pStatement = connection.prepareStatement(
                "select (hash, certbytes) where hash = ?"
            );
            pStatement.setBytes(1, hash);
            ResultSet rs = pStatement.executeQuery();
            RouterInfo ri = new RouterInfo(rs.getBytes("hash"), rs.getBytes("cert"));
            return ri;
        } catch (SQLException sqE) {
            if(App.debug()) {sqE.printStackTrace();}
            return null;
        }
    }

    public List<RouterInfo> getAll() {
        List<RouterInfo> ris = new ArrayList<RouterInfo>();
        try{
            PreparedStatement pStatement = connection.prepareStatement(
                "select * from routerinfo"
            );
            ResultSet rSet = pStatement.executeQuery();
            while (rSet.next()) {
                RouterInfo ri = new RouterInfo(rSet.getBytes("hash"), rSet.getBytes("cert"));
                ris.add(ri);
            }
        } catch (SQLException sqE) {
            if(App.debug()) {sqE.printStackTrace();}
        }
        return ris;
    }




}
