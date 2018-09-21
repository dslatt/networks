/*
 * Iperfer client
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.lang.*;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Client {

    private int port;
    private String host;
    private int time;

    Client(int port, String host, int time) {
        System.out.printf("run client w/ port %d, host %s, time %d\n", port, host, time);
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

        try {
            // create socket connection to server
            client = new Socket(host, port);

            out = new DataOutputStream(client.getOutputStream());

            startTime = System.currentTimeMillis();
            endTime = startTime + TimeUnit.SECONDS.toMillis(time);

            while (System.currentTimeMillis() < endTime) {
                out.write(data, 0, data.length);
            }
        } catch(IOException e) {
            System.out.printf("IOException occured during client transmit\n");
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch(IOException e){
                    System.out.printf("Error closing client socket\n");
                }
            }
            if (out != null) dataSent = out.size();
        }

        System.out.printf("sent=%d KB rate=%f Mbps", dataSent / 1000, ((double)dataSent / (1000^2)) / (double)time);
        //System.out.printf("client ended after %d seconds while sending %d bytes\n", TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime), dataSent);

    }

}
