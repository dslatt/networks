/*
 * Iperfer network performance tool
 * server/client
 */

import java.io.*;
import java.util.*;
import java.lang.*;

public class Iperfer {

    /* constant values */
    public static final int BLOCK_SIZE = 1000;
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    private static boolean badArgs = false;
    private static boolean badArgsFormat = false;

    private static int mode = 0;

    private static int port = 0;
    private static String host = "";
    private static long time = 0;

    private static void parseSimpleArgsList(String args[]) {
        if (args[0].equals("-c")){
            mode = 0;
            if (args.length == 7) { 
                if (!args[3].equals("-p") || 
                    !args[1].equals("-h") || 
                    !args[5].equals("-t"))
                {
                    badArgs = true;
                }else{
                    try {
                        port = Integer.parseInt(args[4]);
                        time = Long.parseLong(args[6]);
                        host = args[2];
                    }catch(NumberFormatException e){
                        badArgsFormat = true;
                    }
                }
            } else{
                badArgs = true;
            }
        } else if (args[0].equals("-s")){
            mode = 1;
            if (args.length == 3) {
                if(!args[1].equals("-p")) {
                    badArgs = true;
                }else{
                    try {
                        port = Integer.parseInt(args[2]);
                    }catch(NumberFormatException e){
                        badArgsFormat = true;
                    }
                }
            }else {
                badArgs = true; 
            }
        }else{
            System.out.printf("Error: Must specify mode (client '-c' or server -s')%n");
            System.exit(1);
        }
        if (badArgs) {
            System.out.printf("Error: missing or additional arguments%n");
            System.exit(1);
        }else if(badArgsFormat){
            System.out.printf("Error: invalid argument%n");
            System.exit(1);
        }
    }

    public static void main(String args[]) {

        parseSimpleArgsList(args);

        if (port < MIN_PORT || port > MAX_PORT) {
            System.out.printf("Error: port number must be in the range %d to %d%n", MIN_PORT, MAX_PORT);
            System.exit(1);
        }

        if(mode == 0){
            Client client = new Client(port, host, time);
            client.start();
            client.printResults();
        }else if(mode == 1){
            Server server = new Server(port);
            server.start();
            server.printResults();
        }
    }
}
