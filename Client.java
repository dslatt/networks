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
    private static final int CLIENT_TIMEOUT = 10000;

    public volatile boolean killFlag = true;

    private int port;
    private String host;
    private int time;

    Client(int port, String host, int time) {
        this.port = port;
        this.host = host;
        this.time = time;
    }

    public void start() {
        
        byte[] data = new byte[Iperfer.BLOCK_SIZE];

        int dataSent = 0;

        long startTime = 0;
        long endTime = 0;

        Socket client = null;

	DataOutputStream out = null;

        Timer cutoffTimer = new Timer();

        try {
            // create socket connection to server
            client = new Socket();

            client.connect(new InetSocketAddress(host,port), CLIENT_TIMEOUT);

            cutoffTimer.schedule(new TimerTask() {
                   @Override
                   public void run() {
                       killFlag = false; 
                   }
            }, time * 1000);

            out = new DataOutputStream(client.getOutputStream());

            //startTime = System.currentTimeMillis();
            //endTime = startTime + TimeUnit.SECONDS.toMillis(time);

            //while (System.currentTimeMillis() < endTime) {
            while (killFlag) {
                out.write(data, 0, data.length);
            }

        } catch(IOException e) {
            System.out.printf("Error: IOException occured during client transmit%n");
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch(IOException e){
                    System.out.printf("Error: Failed closing client socket%n");
                }
            }
            if (out != null) dataSent = out.size();
        }

        System.out.printf("sent=%d KB rate=%f Mbps%n", dataSent / 1000, (dataSent / Math.pow(1000,2)) / time);
    }

}
