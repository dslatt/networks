/*
 * Iperfer server
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.lang.*;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Server {
    
    private int port;

    Server(int port) {
        System.out.printf("run server w/ port %d\n", port);
        this.port = port;
    }

    public void start() {

        byte[] data = new byte[Iperfer.BLOCK_SIZE]; 

        int totalBytes = 0;
        int numBytes = 0;

        ServerSocket socket = null;

        try {
            // create new socket connected to 'port'
            socket = new ServerSocket(port);

            // block until client socket obtained
            Socket client = socket.accept();

            InputStream in = client.getInputStream();

            while((numBytes = in.read(data, 0, data.length)) != -1) {
                totalBytes += numBytes;
            }
        } catch(IOException e) {
            System.out.printf("IOException occured during server receive\n");
        } finally{
            if (socket != null) {
                try{
                    socket.close();
                }catch(IOException e){
                    System.out.printf("Error closing server socket\n");
                }
            }    
        }

        System.out.printf("connection to client ended w/ %d bytes recieved\n", totalBytes);
    }
}
