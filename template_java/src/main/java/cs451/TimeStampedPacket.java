package cs451;

import java.util.ArrayList;
import java.util.List;

public class TimeStampedPacket {
    private int packetID;
    private int forwarderID;
    private int senderID;
    private List<Message> messages;
    private int timestamp;

    public TimeStampedPacket(int packetID, int forwarderID, int senderID, List<Message> messages) {
        this.packetID = packetID;
        this.forwarderID = forwarderID;
        this.senderID = senderID;
        this.messages = messages;

    }

    public int getPacketID() {
        return packetID;
    }

    public int getForwarderID() {
        return forwarderID;
    }
    
    public int getSenderID() {
        return senderID;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }



    @Override
    ///format: packetID:forwaderID:senderID:timestamp:message1,message2...
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(packetID).append(":")
        .append(forwarderID).append(":")
        .append(senderID).append(":")
        .append(timestamp).append(":");
        for (int i = 0; i < messages.size(); i++) {
            sb.append(messages.get(i).toString());
            if (i < messages.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    //packet1-packet2-packet3 e.g. 1:1:2:2:1,2,3,4- 
    public static String parsePackets(List<TimeStampedPacket> listOfPackets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listOfPackets.size(); i++) {
            sb.append(listOfPackets.get(i).toString());
            if(i < listOfPackets.size() - 1) {
            sb.append("-");
            }
        }
        return sb.toString();
    }

    public static TimeStampedPacket fromString(String packetString) {
        String[] parts = packetString.split(":");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid packet string format");
        }
    
        int packetID = Integer.parseInt(parts[0]);
        int forwarderID = Integer.parseInt(parts[1]);
        int senderID = Integer.parseInt(parts[2]);
        long timestamp = Long.parseLong(parts[3]);
        List<Message> messages = new ArrayList<>();
    
        String messagesPart = parts[3];
        String[] messageStrings = messagesPart.split(",");
        for (String messageString : messageStrings) {
            messages.add(Message.fromString(messageString));
        }
    
        TimeStampedPacket packet = new TimeStampedPacket(packetID, forwarderID, senderID, messages);
        packet.setTimestamp(timestamp);
        return packet;
    }

    public static List<TimeStampedPacket> unparsePackets(String packetsString) {
        String[] packetStrings = packetsString.split("-");
        List<TimeStampedPacket> packets = new ArrayList<>();
        for (String packetString : packetStrings) {
            packets.add(TimeStampedPacket.fromString(packetString));
        }
        return packets;
    }

    // public static List<Message> extractMessages(String packetString) {
    //     String[] parts = packetString.split(":");
    //     List<Message> messages = new ArrayList<>();
    //     if (parts.length > 3) { // Ensure there are messages to process
    //         String messagesPart = parts[3]; // The part containing all messages
    //         String[] messageStrings = messagesPart.split(",");
    //         for (String messageString : messageStrings) {
    //             messages.add(Message.fromString(messageString));
    //         }
    //     }
    //     return messages;
    // }
}