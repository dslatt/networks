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

    private boolean exitError = false;

    private long dataRecieved = 0;
    private long startTime = 0;
    private long endTime = 0;

    Server(int port) {
        this.port = port;
    }

    public void start() {

        byte[] data = new byte[Iperfer.BLOCK_SIZE]; 

        long numBytes = 0;

        ServerSocket socket = null;

        try {
            // create new socket connected to 'port'
            socket = new ServerSocket(port);
            
            try {
                // block until client socket obtained
                Socket client = socket.accept();

                startTime = System.currentTimeMillis();

                InputStream in = client.getInputStream();

                while((numBytes = in.read(data, 0, data.length)) != -1) {
                    dataRecieved += numBytes;
                }

                endTime = System.currentTimeMillis();
            } catch(IOException e) {
                System.out.printf("Error: Exception occured during active client connection%n");
                exitError = true;
            }
        } catch(IOException e) {
            System.out.printf("Error: Unable to bind to port %d%n", port);
            exitError = true;
        } finally{
            if (socket != null) {
                try{
                    socket.close();
                }catch(IOException e){
                    System.out.printf("Error: Failed closing server socket%n");
                    exitError = true;
                }
            }    
            if (exitError)
                System.exit(1);
        }
    }

    public void printResults() {
        System.out.printf( "received=%d KB rate=%f Mbps%n",
                            dataRecieved / 1000,
                            (double)((dataRecieved*8) / 1000) / (endTime - startTime));

    }

}
