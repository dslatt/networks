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

    private int port;
    private String host;
    private int time;

    private Timer timer;

    private long dataSent = 0;

    Client(int port, String host, int time) {
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

        long startTime = 0;
        long endTime = 0;

        Socket client = null;

	BufferedOutputStream out = null;

        timer = new Timer();

        try {
            // create socket connection to server
            client = new Socket();

            client.connect(new InetSocketAddress(host,port), CLIENT_TIMEOUT);

            out = new BufferedOutputStream(client.getOutputStream());

            timer.schedule(new KillTask(), time * 1000);
            startTime = System.currentTimeMillis();

            while (killFlag) {
                out.write(data, 0, data.length);
                dataSent += data.length;
            }
            
            endTime = System.currentTimeMillis();
        } catch(IOException e) {
            System.out.printf("Error: IOException occured during client transmit%n");
            e.printStackTrace();
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch(IOException e){
                    System.out.printf("Error: Failed closing client socket%n");
                }
            }
            /* if (out != null) dataSent = out.size(); */
        }

    }

    public void printResults() {
        System.out.printf( "sent=%d KB rate=%f Mbps%n", 
                            dataSent / 1000,
                            (dataSent / Math.pow(1000,2)) / time);
    }

}
