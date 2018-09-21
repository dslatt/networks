/*
 * Iperfer server
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.lang.*;

public class Server {
    
    private int port;

    Server(int port) {
        this.port = port;
    }

    public void start() {

        byte[] data = new byte[Iperfer.BLOCK_SIZE]; 

        int totalBytes = 0;
        int numBytes = 0;
        long totalTime = 0;

        ServerSocket socket = null;

        try {
            // create new socket connected to 'port'
            socket = new ServerSocket(port);

            // block until client socket obtained
            Socket client = socket.accept();

            InputStream in = client.getInputStream();

            while((numBytes = in.read(data, 0, data.length)) != -1) {
                totalBytes += numBytes;
                totalTime = System.currentTimeMillis();
            }

        } catch(IOException e) {
            System.out.printf("Error: IOException occured during server receive%n");
        } finally{
            if (socket != null) {
                try{
                    socket.close();
                }catch(IOException e){
                    System.out.printf("Error: Failed closing server socket%n");
                }
            }    
        }

        System.out.printf("received=%d KB rate=%f Mbps%n", totalBytes / 1000, (totalBytes / Math.pow(1000,2)) / (totalTime/1000));
    }
}
