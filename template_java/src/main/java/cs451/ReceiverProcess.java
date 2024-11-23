package cs451;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ReceiverProcess {

    private final String outputFilePath;

    Host myHost;
    public int myID;

    volatile boolean stop = false;

    private final Object lockGeneral = new Object();

    List<Host> listOfHosts;
    public int numberOfMessages;

    // TODO to adjust ?
    private final long FLUSH_INTERVAL = 1000; 

    private DatagramSocket mySocket;
    private LinkedBlockingQueue<PacketIDHostTuple> ackQueue = new LinkedBlockingQueue<>();

    public static List<Host> hosts;
    public static HashMap<SocketAddress, Integer> addressToHost = new HashMap<>();
    public static HashSet<PacketIDHostTuple> receivedPackets = new HashSet<>();
    public ConcurrentLinkedQueue<String> logs = new ConcurrentLinkedQueue<>(); // ConcurrentLinkedQueue to store log entries                                                     

    public ReceiverProcess(String outputFilePath, int myId, DatagramSocket mySocket, int numberOfMessages,
            List<Host> listOfHosts, Host myHost) {
        this.outputFilePath = outputFilePath;
        this.myID = myId;
        this.numberOfMessages = numberOfMessages;
        this.listOfHosts = listOfHosts;
        this.myHost = myHost;
        this.mySocket = mySocket;
    }
    public void stop() {
        stop = true;
    }
    public void receiverBroadcast() {
        new Thread(new Receiver(this)).start();
        new Thread(new AckSender(this)).start();
        new Thread(new Logger(this)).start();
    }

    private class Receiver implements Runnable {
        private ReceiverProcess process;

        public Receiver(ReceiverProcess process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024]; 

                // continously receive packets 
                // find out who the sender is 
                // check if the message has already been logged
                // add packet to receivedPackets
                // log if havent been logged
                while (!process.stop) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    process.mySocket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    TimeStampedPacket receivedPacket = TimeStampedPacket.fromString(receivedData);

                    int packetID = receivedPacket.getPacketID();
                    int senderID = receivedPacket.getSenderID();
                    Host senderHost = process.listOfHosts.get(senderID - 1); //
                    process.ackQueue.put(new PacketIDHostTuple(packetID, senderHost));

                    if (!receivedPackets.contains(new PacketIDHostTuple(packetID, senderHost))) {
                        synchronized (lockGeneral) {
                            receivedPackets.add(new PacketIDHostTuple(packetID, senderHost));
                            List<Message> messages = receivedPacket.getMessages();
                            for (int i = 0; i < messages.size(); i++) {
                                int messageID = packetID + i;
                                logs.add("d" + " " + senderID + " " + messageID);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class AckSender implements Runnable {
        private ReceiverProcess process;

        public AckSender(ReceiverProcess process) {
            this.process = process;
        }
        @Override
        public void run() {
            try {
                // Take Packet from ackQueue, Send Ack to the Sender 
                while (!process.stop) {
                    PacketIDHostTuple ackTuple = process.ackQueue.take(); 
                    String ackMessage = String.valueOf(ackTuple.packetID);
                    byte[] ackBuffer = ackMessage.getBytes();
                    Host senderHost = ackTuple.senderHost;
                    InetAddress address = InetAddress.getByName(senderHost.getIp());
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, address, senderHost.getPort());
                    process.mySocket.send(ackPacket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class Logger implements Runnable {
        private ReceiverProcess process;

        public Logger(ReceiverProcess process) {
            this.process = process;
        }
        @Override
        public void run() {
            while (!process.stop) {
                try {
                    Thread.sleep(FLUSH_INTERVAL); // Sleep for the specified interval
                    FinalLogger.writeLogs(process.outputFilePath, process.logs);
                    process.logs.clear();
                    // flushLogs();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                    break; // Exit the loop if the thread is interrupted
                }
            }
            flushLogs();
        }
    }
    public void flushLogs() { 
        FinalLogger.writeLogs(outputFilePath, logs);
        logs.clear();
    }
}
