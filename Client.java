/*
 * Iperfer client
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.lang.*;

public class Client {

    // timeout value on socket connection in millis
    private static final int CLIENT_TIMEOUT = 5000;

    public volatile boolean killFlag = true;

    private boolean exitError = false;

    private int port;
    private String host;
    private long time;

    private Timer timer;

    private long startTime = 0;
    private long endTime = 0;

    private long dataSent = 0;

    Client(int port, String host, long time) {
        this.port = port;
        this.host = host;
        this.time = time;
    }

    class KillTask extends TimerTask {
        public void run() {
            killFlag = false;
            timer.cancel();
        }
    }

    public void start() {
        
        byte[] data = new byte[Iperfer.BLOCK_SIZE];

        dataSent = 0;

        Socket client = null;
	OutputStream out = null;
        InetAddress validHost = null;

        timer = new Timer();
        client = new Socket();

        try {
            validHost = InetAddress.getByName(host);
            client.connect(new InetSocketAddress(validHost,port), CLIENT_TIMEOUT);
            out = client.getOutputStream();

            // schedule TimerTask to stop client send after 'time' seconds
            timer.schedule(new KillTask(), time * 1000);
            startTime = System.currentTimeMillis();

            while (killFlag) {
                out.write(data);
                dataSent += data.length;
            }
            
            endTime = System.currentTimeMillis();
        } catch(SocketTimeoutException e) {
            System.out.printf("Error: Timeout during socket connection%n");
            exitError = true;
        } catch(UnknownHostException e) {
            System.out.printf("Error: Invalid hostname%n");
            exitError = true;
        } catch(IOException e) {
            System.out.printf("Error: IO error occured during client connection%n"); 
            exitError = true;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch(IOException e){
                    System.out.printf("Error: Failed closing client socket%n");
                    exitError = true;
                }
            }
            if (exitError)
                System.exit(1);
        }
    }

    public void printResults() {
        System.out.printf( "sent=%d KB rate=%f Mbps%n", 
                            dataSent / 1000,
                            (double)(dataSent / 1000) / (endTime - startTime));
    }

}
