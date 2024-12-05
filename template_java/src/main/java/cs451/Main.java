package cs451;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.logging.Logger;

public class Main {
    private static int myID;
    private static int receiverID;
    private static boolean IAmreceiver;
    private static DatagramSocket mySocket;
    private static ReceiverProcess receiverProcess;
    private static SenderProcess senderProcess;
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    private static void handleSignal() {
        // immediately stop network packet processing
         print("Immediately stopping network packet processing.");
        if (mySocket != null && !mySocket.isClosed()) {
            mySocket.close();
        }
        if (IAmreceiver) {
            if (receiverProcess != null) {
            receiverProcess.stop();
            receiverProcess.flushLogs(); // Flush logs immediately
            }
        } else {
            if (senderProcess != null) {
            senderProcess.stop();
            senderProcess.flushLogs(); // Flush logs immediately
            }

        }
    }

    public static void print(String message) {
        System.out.println(message);
    }

    private static void initSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                handleSignal();
            }
        });
    }

    public static int retrieve(String configFilePath, int position) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                String[] parts = firstLine.split(" ");
                if (position == 0 && parts.length > 0) {
                    return Integer.parseInt(parts[0]);
                } else if (position == 1 && parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }
        return -1; // Return a default value or handle the error as needed
    }

    private static DatagramSocket createDatagramSocket(String IP, int port) {
        logger.info("Creating Datagram Socket");

        try {
            InetAddress address = InetAddress.getByName(IP);
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(address, port));
            return socket;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws InterruptedException {

        Parser parser = new Parser(args);
        parser.parse();

        initSignalHandlers();

        String configFilePath = parser.config();
        String outputFilePath = parser.output();

        myID = parser.myId();

        // IAmreceiver = (myID == receiverID); no longer needed 

        List<Host> listOfHosts = parser.hosts();
        Host myHost = listOfHosts.get(myID - 1);
        int myPort = myHost.getPort();
        String myIP = myHost.getIp();

        int numberofMessages = retrieve(configFilePath, 0);

        mySocket = createDatagramSocket(myIP, myPort);
        logger.info("Created Datagram Socket");
        // System.out.println("Created Datagram Socket");

     
            UrbProcess urbProcess = new UrbProcess(outputFilePath, myID, mySocket, numberofMessages, listOfHosts, myHost);

                    urbProcess.urbBroadcast();
       
        while (true) {
            // Sleep for 1 hour
            Thread.sleep(60 * 60 * 1000);
        }
    }
}
