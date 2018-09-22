/*
 * Iperfer network performance tool
 */

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.*;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Iperfer implements Runnable {

    /* constant values */
    public static final int BLOCK_SIZE = 1000;
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    @Option(names="-c", description="run Iperfer in client mode")
    private boolean clientMode = false;

    @Option(names="-s", description="run Iperfer in server mode")
    private boolean serverMode = false;

    @Option(names="-p", arity="1", description="port to use for connection (Must be in range 1024-65535)")
    private int port = 0;

    @Option(names="-h", arity="1", description="host IP address to connect to")
    private String host = "";

    @Option(names="-t", arity="1", description="time period to test")
    private long time = 0;

    public void run() {

        if (port < MIN_PORT || port > MAX_PORT) {
            System.out.printf("Error: port number must be in the range %d to %d%n", MIN_PORT, MAX_PORT);
            System.exit(1);
        }

        if(clientMode){
            Client client = new Client(port, host, time);
            client.start();
            client.printResults();
        }else if(serverMode){
            Server server = new Server(port);
            server.start();
            server.printResults();
        }else{
            System.out.printf("Error: Must specify mode (client '-c' or server -s')%n");
        }
    }

    public static void main(String[] args){
//        CommandLine.run(new Iperfer(), args);

        CommandLine cli = new CommandLine(new Iperfer())
                            .addSubcommand("-c", new Client())
                            .addSubcommand("-s", new Server());
        List<CommandLine> parsed = commandLine.parse(args);
        handleParseResult(parsed);
    }
}
