package net.n2p.router;
/*
 * This Java source file was generated by the Gradle 'init' task.
 */

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.List;
import java.util.Scanner;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import net.n2p.router.networkdb.RouterInfo;

public class App {
    
    // set BouncyCastleProvider
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    
    public static Path n2pDirPath = Paths.get(System.getProperty("user.dir")+"/.n2p");
    public static String n2pDirPathStr = n2pDirPath.toAbsolutePath().toString();

    static {
        File file = new File(n2pDirPathStr);
        // logger
        System.out.println("Working directory: "+System.getProperty("user.dir"));
        if (file.isDirectory()) {
            // logger
            System.out.println(".n2p found at: "+n2pDirPathStr);
        } else {
            // logger
            System.out.println("creating .n2p directory at: "+n2pDirPathStr);
            file.mkdirs();
        }
    }

    private static boolean dbg = false;
    private static int port = 443;
    private static boolean storeCS = true;
    private static String dummyHost = "google.com";
    private static boolean dummy = false;
    
    private static Router _router;


    public static void main(String[] args) {
        System.out.println(String.valueOf(args.length));
        if (args.length > 0) {
            System.out.println("Args:");
            for (String s: args) {
                System.out.println(s);
                if (s.equals("dummy")) {
                    dummy = true;
                }
            }
        }
        System.out.println("Is dummy true?");
        
        if (dummy == true){
            System.out.println("YES! RUNNING IN DUMMY MODE **********************************");
            Scanner sc = new Scanner(System.in);
            boolean cont = false;
            int cmd;
            do{
                String prompt1 = "Select mode:\n\t(0) normal\n\t(1) debug";
                System.out.print(prompt1);
                cmd = sc.nextInt();
                System.out.println(cmd);
                switch (cmd)
                {
                    case 0:
                        dbg = false;
                        cont = true;
                    case 1:
                        dbg = true;
                        cont = true;
                    default:
                }
            } while(!cont);
            
            
            do{
                String prompt2 = "Set server port (443 default, 0-65353):";
                System.out.print(prompt2);
                cmd = sc.nextInt();
                System.out.println(cmd);
                if ((0 <= cmd) && (cmd < 65354))
                {
                    port = cmd;
                    break;
                } else {
                    port = 443;
                    break;
                }
            } while(true);
            cont = false;
            do{
                String prompt3 = "Store connections in DB (0) YES, (1) NO:";
                System.out.print(prompt3);
                cmd = sc.nextInt();
                System.out.println(cmd);
                switch (cmd)
                {
                    case 0:
                        storeCS = true;
                        cont = true;
                        break;
                    case 1:
                        storeCS = false;
                        cont = true;
                        break;
                    default:
                        cont = false;
                }
            } while(!cont);

            String prompt4 = "Set client host (default: google.com, should have HTTPS/TLS/SSL):";
            System.out.print(prompt4);
            String cmdHost = sc.next();
            dummyHost = cmdHost;
        
            _router = new Router();
            _router.runRouter();
            do{
                String prompt5 = "Press 'q' to quit:";
                System.out.print(prompt5);
                String cmdStr = sc.next();
                if (cmdStr.equals("q"))
                    _router.stop();
                    break;
            } while(true);
            sc.close();
            if(store()) {
                List<RouterInfo> ris = DatabaseManager.getAllRouterInfo();
                List<InetSocketAddress> adrs = DatabaseManager.getAddresses();
                System.out.println("********** SUMMARY ******************************");
                System.out.println("ROUTER INFO:");
                for (RouterInfo ri : ris){
                    System.out.println("\t\tRouterHash: " +ri.getHash());
                    System.out.print("\t\tCert:\n\t"+ri.getCert().toString());
                }
                System.out.println("**********************************************");
                System.out.println("ADDRESSES:");
                for (InetSocketAddress isa : adrs) {
                    System.out.println("\t"+isa.getHostString()+" : "+String.valueOf(isa.getPort()));
                }
                
            }
            
        } else {
            System.out.println("NO! RUNNING IN NORMAL MODE **********************************");
            System.out.println("Try browsing to https://localhost.443");
            _router = new Router();
            _router.runRouter();
            
        }






        

        

        getGreeting();
    }

    public static String getGreeting() {
        return "Goodbye";
    }

    public static boolean debug(){
        return dbg;
    }

    public static int port(){
        return port;
    }

    public static boolean store(){
        return storeCS;
    }

    public static boolean dummy(){
        return dummy;
    }

    public static String dummyHost(){
        return dummyHost;
    }
}
