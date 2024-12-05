package cs451;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class UrbProcess {

    public static List<Host> hosts;
    public static HashMap<SocketAddress, Integer> addressToHost = new HashMap<>();
    private final String outputFilePath;
    private final Object lockGeneral = new Object();

    public int myID;
    Host myHost;
    List<Host> listOfHosts;
    volatile boolean stop = false;

    public int numberOfMessages;
    private int MAX_MESS_PER_PACK = 8; // Maximum number of messages per packet
    private int totalPackets;
    private int numberOfHosts;
    private int majority;

    private Set<Sender_Packet_Size_Tuple> pending = ConcurrentHashMap.newKeySet();;
    private Set<Sender_Packet_Size_Tuple> urbDelivered = new HashSet<>();

    private Map<Integer, Map<Integer, Set<Integer>>> acked_perSender;
    private HashMap<Integer, PriorityQueue<Sender_Packet_Size_Tuple>> received_packets_toLog = new HashMap<>();
    private HashMap<Integer, Integer> highestLoggedID = new HashMap<>();

    private DatagramSocket mySocket;
    private AtomicInteger unAckedPackets = new AtomicInteger(0);
    public ConcurrentLinkedQueue<String> logs = new ConcurrentLinkedQueue<>();

    // TODO understand what these values should be, wait time can be function of
    // window Size/ unacked Size ?
    private final long FLUSH_INTERVAL = 1000;
    private final long WAIT_TIME_RESEND_ACK = 1000;
    private final int BUFFER_SIZE = 4096;

    private final LockCounter lockCounter = new LockCounter();
    private final ResendSignal resendSignal = new ResendSignal();

    public UrbProcess(String outputFilePath, int myId, DatagramSocket mySocket, int numberOfMessages,
            List<Host> listOfHosts, Host myHost) {
        this.outputFilePath = outputFilePath;
        this.myID = myId;
        this.numberOfMessages = numberOfMessages;
        this.listOfHosts = listOfHosts;
        this.numberOfHosts = listOfHosts.size();
        this.majority = (numberOfHosts / 2) + 1;
        this.myHost = myHost;
        this.mySocket = mySocket;
        this.totalPackets = (numberOfMessages % MAX_MESS_PER_PACK == 0)
                ? numberOfMessages / MAX_MESS_PER_PACK
                : (numberOfMessages / MAX_MESS_PER_PACK) + 1;

        for (int hostID = 1; hostID <= numberOfHosts; hostID++) {
            Map<Integer, Set<Integer>> packetMap = new HashMap<>();
            for (int packetID = 1; packetID <= totalPackets; packetID++) {
                packetMap.put(packetID, new HashSet<>());
            }
            acked_perSender.put(hostID, packetMap);
        }
        for (int hostID = 1; hostID <= numberOfHosts; hostID++) {
            PriorityQueue<Sender_Packet_Size_Tuple> queue = new PriorityQueue<>(new PacketComparator());
            received_packets_toLog.put(hostID, queue);
        }
        for (int hostID = 1; hostID <= numberOfHosts; hostID++) {
            highestLoggedID.put(hostID, 0);
        }

    }

    // TODO change MAIN
    public void stop() {
        stop = true;
    }

    public void addToPending(int senderID, int packetID, int packetSize) {
        Sender_Packet_Size_Tuple tuple = new Sender_Packet_Size_Tuple(senderID, packetID, packetSize);
        synchronized (pending) {
            if (!pending.contains(tuple)) {
                pending.add(tuple);
            }
        }
    }

    public boolean pendingContains(int senderID, int packetID, int packetSize) {
        synchronized (pending) {
            return pending.contains(new Sender_Packet_Size_Tuple(senderID, packetID, packetSize));
        }
    }

    public void urbBroadcast() {
        new Thread(new UrbBroadcast(this)).start();
        new Thread(new BebDeliver(this)).start();
        new Thread(new Logger(this)).start();
    }

    private class UrbBroadcast implements Runnable {
        private UrbProcess process;

        public UrbBroadcast(UrbProcess process) {
            this.process = process;
        }

        @Override
        public void run() {
            try {
                int packetID = 1;
                int firstMessageID = 1;
                int remainingMessages = process.numberOfMessages;

                while (remainingMessages > 0 && !process.stop) {

                    // DEFINES HOW MANY PACKETS WE SEND AT ONCE TO EACH PROCESS (WINDOW SIZE)
                    // If the window size is 0 => Congestion => Set Window Size to max(1,
                    // windowSize/2)
                    // Set Counter to New Window Size - num of Unacked
                    // Wait a small amount before notifying Resender to Resend unacked packets
                    // wait for counter to become non-zero so that can strat transmitting again

                    synchronized (lockGeneral) {
                        if (process.lockCounter.getCounterValue() == 0) {
                            int currentWindowSize = process.lockCounter.getWindowValue();
                            process.lockCounter.setNewWindowSize(Math.max(1, currentWindowSize / 2));
                            process.lockCounter.setCounter(currentWindowSize - unAckedPackets.get());
                            // TODO RESEND SIGNAL
                            process.resendSignal.signalResender();
                            process.lockCounter.waitForNonZero();
                        }
                    }
                    // 1: Iterate over all processes (except self)
                    // 2: Send to each, number of packets allowed by current window size
                    // Add (self, packetID) to pending at each interation (of last process only so
                    // no duplicates)

                    // loop to send all the packets allowed by windowsize to a single process
                    // TODO CHANGE ... must parse the packets
                    // TODO need to log before you actually send..... (but log only once u send
                    // packet to all processes?)

                    // need to send same amount to all processed (cant recheck after sent to each
                    // process)
                    int numberPacketsToSend = process.lockCounter.getCounterValue();

                    for (int receiverID = 1; receiverID <= process.numberOfHosts; receiverID++) {

                        int numberPacketsPerProcess = numberPacketsToSend;
                        int remainingMessagesPerProcess = remainingMessages;
                        int PacketIDPerProcess = packetID;
                        int firstMessageIDPerProcess = firstMessageID;
                        Host receiverHost = listOfHosts.get(receiverID - 1);
                        String receiverIP = receiverHost.getIp();
                        int receiverPort = receiverHost.getPort();
                        List<TimeStampedPacket> listOfPackets = new ArrayList<>();

                        // Emulate as if you received packet from youself
                        if (receiverID == myID) {
                            for (int pckID = 1; pckID <= numberPacketsPerProcess; pckID++) {
                                acked_perSender.get(myID).get(pckID).add(myID);
                            }
                            continue;
                        }
                        // Parse the Packets to BEB Broadast
                        for (int k = 0; k < numberPacketsPerProcess; k++) {

                            int messagesToSend = Math.min(MAX_MESS_PER_PACK, remainingMessagesPerProcess);
                            List<Message> messages = new ArrayList<>(messagesToSend);
                            for (int x = 0; x < messagesToSend; x++) {
                                messages.add(new Message(firstMessageIDPerProcess + x));
                            }
                            listOfPackets.add(new TimeStampedPacket(PacketIDPerProcess, myID, myID, messages));

                            // if sending to the last process: add to pending, add to logs, change GLOBAL
                            // variables
                            if (receiverID == process.listOfHosts.size()) {
                                synchronized (pending) {
                                    pending.add(new Sender_Packet_Size_Tuple(myID, PacketIDPerProcess, messagesToSend));
                                }
                                for (int messSeq = 0; messSeq < messagesToSend; messSeq++) {
                                    int messageID = firstMessageIDPerProcess + messSeq;
                                    logs.add("b" + " " + messageID);
                                }
                                packetID += 1;
                                firstMessageID += messagesToSend;
                                remainingMessages -= messagesToSend;
                            }
                            // Increment packetID and decrement remainingMessages (LOCAL VARIABLES)
                            PacketIDPerProcess += 1;
                            firstMessageIDPerProcess += messagesToSend;
                            remainingMessagesPerProcess -= messagesToSend;
                        } // once you exit this loop, re initialise local variables for the next process

                        // SEND PARSED PACKETS `TO RECEIVER
                        byte[] buffer = TimeStampedPacket.parsePackets(listOfPackets).getBytes();
                        InetAddress address = InetAddress.getByName(receiverIP);
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, receiverPort);
                        synchronized (lockGeneral) {
                            process.mySocket.send(packet);
                            process.lockCounter.decrementCounter(numberPacketsToSend);
                            unAckedPackets.incrementAndGet();
                        }
                    } // once you exit this loop, re initialise global variables
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class BebDeliver implements Runnable {
        private UrbProcess process;

        public BebDeliver(UrbProcess process) {
            this.process = process;
        }

        @Override // TODO decrement unacked packets when receive packets , impleement FIFO
                  // pritotisatin....
        public void run() {
            try {

                // continously receive packets
                // find out who the sender is
                // check if the message has already been logged
                // add packet to receivedPackets
                // log if havent been logged

                byte[] buffer = new byte[BUFFER_SIZE];
                while (!process.stop) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    process.mySocket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength());
                    List<TimeStampedPacket> receivedPackets = TimeStampedPacket.unparsePackets(receivedData);

                    for (TimeStampedPacket receivedPacket : receivedPackets) {

                        unAckedPackets.decrementAndGet();
                        process.lockCounter.increment(receivedPackets.size());

                        int packetID = receivedPacket.getPacketID();
                        int senderID = receivedPacket.getSenderID();
                        int forwarderID = receivedPacket.getForwarderID();
                        int packetSize = receivedPacket.getMessages().size();

                        Main.print("Process " + myID + " Received packet with ID " + packetID + " forwarded by process "
                                + forwarderID + " sent by process " + senderID);

                        // ADD to acked -> CHECK if can URB DELIVER
                        // TODO use urbDelovered for what, to avoid doing what twice?

                        acked_perSender.get(senderID).get(packetID).add(forwarderID);
                        if (!urbDelivered.contains(new Sender_Packet_Size_Tuple(senderID, packetID, packetSize))) {
                            if (acked_perSender.get(senderID).get(packetID).size() > (majority)) {// URB DELIVER
                                urbDelivered.add(new Sender_Packet_Size_Tuple(senderID, packetID, packetSize));
                                pending.remove(new Sender_Packet_Size_Tuple(senderID, packetID, packetSize));
                                received_packets_toLog.get(senderID)
                                        .add(new Sender_Packet_Size_Tuple(senderID, packetID, packetSize)); // TODO
                                                                                                            // defin
                                                                                                            // priority
                                                                                                            // in queue
                            }
                        }

                        // TODO ADD to pendinf and boracst atomic?
                        // ADD TO forwarder to PENDING UPON RECEIVING PACKET

                        // TODO window size must be modified for all packets sent & received
                        // TODO FIFO mechanism: prioritise which we should send back first

                        // TODO FIFO SYSTEM - when rebroacstsing ... for each packet, must pritotirse
                        // differnet, how to deinf how mnay to resend?

                        if (!pendingContains(senderID, packetID, packetSize)) {
                            addToPending(senderID, packetID, packetSize);

                            // tirgger beb broadcast
                            for (int receiverID = 1; receiverID <= process.listOfHosts.size(); receiverID++) {

                                if (receiverID == myID) {
                                    continue;
                                } // Skip self TODO (what to do when sending to SELF - add to ?)

                                Host receiverHost = listOfHosts.get(receiverID - 1);
                                String receiverIP = receiverHost.getIp();
                                int receiverPort = receiverHost.getPort();

                                TimeStampedPacket timeStampedPacket = new TimeStampedPacket(packetID, myID, senderID,
                                        receivedPacket.getMessages());
                                byte[] bufferb = timeStampedPacket.toString().getBytes();
                                InetAddress address = InetAddress.getByName(receiverIP);
                                DatagramPacket packetb = new DatagramPacket(bufferb, buffer.length, address,
                                        receiverPort);
                                process.mySocket.send(packetb);

                            }
                        }
                    }

                }
            } catch (

            Exception e) {
                e.printStackTrace();
            }
        }
    }

    // TODO do i need to have a mechnaism to immeditely flush the logs when SIG
    // arrives or can I wait FLUSH_INTERVAL?
    // TODO do i have to also terminate this thread?
    // TODO need to make sur logs are flushed before sending hence this way may not
    // be correct

    // for (int i = 1; i <= packetSize; i++) {
    //     logs.add("d" + " " + senderID + " " + (((packetID - 1) * MAX_MESS_PER_PACK) + i));
    //     }

    private class Logger implements Runnable {
        private UrbProcess process;

        public Logger(UrbProcess process) {
            this.process = process;
        }

        @Override
        public void run() {
            while (!process.stop) {
                try {
                    Thread.sleep(FLUSH_INTERVAL); // Sleep for the specified interval
                    // FinalLogger.writeLogs(process.outputFilePath, process.logs);
                    // process.logs.clear();
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
        FinalLogger.writeLogs(this.outputFilePath, this.logs);
        this.logs.clear();
    }

}