package cs451;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class SenderProcess {
    public static List<Host> hosts;
    public static HashMap<SocketAddress, Integer> addressToHost = new HashMap<>();

    private final String outputFilePath;

    private final Object lockGeneral = new Object();

    public int myID;
    Host myHost;

    List<Host> listOfHosts;
    volatile boolean stop = false;

    public int receiverID;
    public Host receiverHost;
    public String receiverIP;
    public int receiverPort;

    public int numberOfMessages;
    private int maxMessagesPerPacket = 8; // Maximum number of messages per packet

    private DatagramSocket mySocket;
    private ConcurrentHashMap<Integer, TimeStampedPacket> sentPackets = new ConcurrentHashMap<>();
    private AtomicInteger unAckedPackets = new AtomicInteger(0);
    public ConcurrentLinkedQueue<String> logs = new ConcurrentLinkedQueue<>(); 

    // TODO understand what these values should be, wait time can be function of window Size/ unacked Size ? 
    private final long FLUSH_INTERVAL = 1000;
    private final long WAIT_TIME_RESEND_ACK = 1000; 

    private final LockCounter lockCounter = new LockCounter();
    private final ResendSignal resendSignal = new ResendSignal();

    public SenderProcess(String outputFilePath, int myId, DatagramSocket mySocket,int receiverID, int numberOfMessages, List<Host> listOfHosts, Host myHost) {
        this.outputFilePath = outputFilePath;
        this.myID = myId;
        this.receiverID = receiverID;
        this.receiverHost = listOfHosts.get(receiverID - 1);
        this.receiverIP = receiverHost.getIp();
        this.receiverPort = receiverHost.getPort();
        this.numberOfMessages = numberOfMessages;
        this.listOfHosts = listOfHosts;
        this.myHost = myHost;
        this.mySocket = mySocket;
    }
    public void stop() {
        stop = true;
    }
    public void senderBroadcast() {
        new Thread(new Sender(this)).start();
        new Thread(new AckReceiver(this)).start();
        new Thread(new Resender(this)).start();
        new Thread(new Logger(this)).start();
    }
    private class Sender implements Runnable {
        private SenderProcess process;

        public Sender(SenderProcess process) {
            this.process = process;
        }
        @Override
        public void run() {
            try {
                int packetID = 1;
                int remainingMessages = process.numberOfMessages;

                while (remainingMessages > 0 && !process.stop) {

                    // If the window size is 0 => Congestion => Set Window Size to max(1, windowSize/2)
                    // Set Counter to NEw Window Size - num of Unacked 
                    // Wait a small amount before notifying Resender to Resend unacked packets
                    // wait for counter to become non-zero so that can strat transmitting again
                    synchronized (lockGeneral) {
                        if (process.lockCounter.getCounterValue() == 0) {
                            int currentWindowSize = process.lockCounter.getWindowValue();
                            process.lockCounter.setNewWindowSize(Math.max(1, currentWindowSize / 2));
                            process.lockCounter.setCounter(currentWindowSize - unAckedPackets.get()); 
                            wait(WAIT_TIME_RESEND_ACK);
                            process.resendSignal.signalResender();
                            process.lockCounter.waitForNonZero(); 
                        }
                    }
                    // Outer for loop for sending packets allowed by current window size 
                    for (int i = 0; i < process.lockCounter.getCounterValue(); i++) {
                        int messagesToSend = Math.min(maxMessagesPerPacket, remainingMessages);
                        List<Message> messages = new ArrayList<>(messagesToSend);

                        // Create messages to send in the packet
                        for (int j = 0; j < messagesToSend; j++) {
                            messages.add(new Message(packetID + j));
                        }
                        // Create Packet & timestamp when sending the packet
                        TimeStampedPacket timeStampedPacket = new TimeStampedPacket(packetID, myID, messages);
                        byte[] buffer = timeStampedPacket.toString().getBytes();
                        InetAddress address = InetAddress.getByName(process.receiverIP);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, process.receiverPort);

                        // TODO Ensure timestamp not used in receiver because the timsteamp isn't in the string format as timestamp is set after
                        // Send Packet, decrement counter, add packet to sentPackets, increment unacked packets, log 
                        synchronized (lockGeneral) {
                            timeStampedPacket.setTimestamp(System.currentTimeMillis());
                            process.mySocket.send(packet);
                            process.lockCounter.decrementCounter(); 
                            synchronized (process.sentPackets) {
                            process.sentPackets.put(packetID, timeStampedPacket);
                            unAckedPackets.incrementAndGet();
                            }
                            for (int k = 0; k < messagesToSend; k++) {
                                int messageID = packetID + k;
                                logs.add("b" + " " + messageID);
                            }
                        }
                        // Increment packetID and decrement remainingMessages
                        packetID += messagesToSend;
                        remainingMessages -= messagesToSend;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class AckReceiver implements Runnable {
        private SenderProcess process;

        public AckReceiver(SenderProcess process) {
            this.process = process;
        }
        @Override
        public void run() {
            try {
                //TOOD buffer size?
                byte[] buffer = new byte[1024];

                // handle acks, remove from sentPackets, decrement unacked packets, increment counter +2 & window +1
                while (!process.stop) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    process.mySocket.receive(packet);
                    process.lockCounter.increment();
                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    int packetID = Integer.parseInt(receivedData);
                    synchronized (process.sentPackets) {
                    process.sentPackets.remove(packetID); 
                    process.unAckedPackets.decrementAndGet();
                    } 
                }
                mySocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class Resender implements Runnable {
        private SenderProcess process;

        public Resender(SenderProcess process) {
            this.process = process;
        }
        @Override
        public void run() {
            try {
                //TODO think of not resending all packets in sentPakcets, instead a Queue and you only resend the first chunk 
                //TODO perhaps also think that in the for loop when sending say packet 2, by that time it could have been acked and removed from sentPackets

                // Resender waits from signal from Sender to resend packets
                // Take a snapshot of sentPackets 
                // Resend all packets in the snapshot
                while (!process.stop) {
                    process.resendSignal.waitForResender(); 
                    ConcurrentHashMap<Integer, TimeStampedPacket> snapshot;
                    synchronized (process.sentPackets) {
                        snapshot = new ConcurrentHashMap<>(process.sentPackets);
                    }
                    for (TimeStampedPacket packet : snapshot.values()) {
                        byte[] buffer = packet.toString().getBytes();
                        InetAddress address = InetAddress.getByName(process.receiverIP);
                        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length, address,
                                process.receiverPort);
                        packet.setTimestamp(System.currentTimeMillis()); // Update the timestamp when resending
                        process.mySocket.send(datagramPacket);
                }
            }
                mySocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // TODO  do i need to have a mechnaism to immeditely flush the logs when SIG arrives or can I wait FLUSH_INTERVAL?
    // TODO do i have to also terminate this thread?
    private class Logger implements Runnable {
        private SenderProcess process;

        public Logger(SenderProcess process) {
            this.process = process;
        }
        @Override
        public void run() {
            while (!process.stop) {
                try {
                    Thread.sleep(FLUSH_INTERVAL); // Sleep for the specified interval
                    flushLogs();
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