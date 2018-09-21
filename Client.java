/*
 * Iperfer client
 */

import java.io.*;
import java.util.*;
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
        
        byte[] data = new byte[1000];

        int dataSent = 0;

        long startTime = 0;
        long endTime = 0;

        Socket client = null;

        try {
            // create socket connection to server
            client = new Socket(host, port);

            OutputStream out = client.getOutputStream();

            startTime = System.currentTimeMillis();
            endTime = startTime + 1000*time;

            while (System.currentTimeMillis() < endTime) {
                out.write(data);
                dataSent += dataSent + 1000;
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
            System.out.println("datasent == "  + dataSent);
        }

        System.out.printf("client ended after %d seconds while sending %d bytes\n", (System.currentTimeMillis() - startTime)/1000, dataSent);

    }

}
