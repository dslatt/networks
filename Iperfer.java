/*
 * Iperfer network performance tool
 */

import java.io.*;
import java.util.*;
import java.lang.*;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Iperfer implements Runnable {

    @Option(names="-c", description="run Iperfer in client mode")
    private boolean clientMode = false;

    @Option(names="-s", description="run Iperfer in server mode")
    private boolean serverMode = false;

    @Option(names="-p", arity="1", description="port to use for connection (Must be in range 1024-65535)")
    private int port = 0;

    @Option(names="-h", arity="1", description="host IP address to connect to")
    private String host = "";

    @Option(names="-t", arity="1", description="time period to test")
    private int time = 0;

    public void run() {
        if(clientMode){
            Client client = new Client(port, host, time);
            client.start();
        }else if(serverMode){
            Server server = new Server(port);
            server.start();
        }else{
            System.out.printf("Error: Must specify mode (client '-c' or server -s')\n");
        }
    }

    public static void main(String[] args){
        CommandLine.run(new Iperfer(), args);
    }
}
