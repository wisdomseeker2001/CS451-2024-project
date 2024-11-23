package cs451;

import java.util.ArrayList;
import java.util.List;

public class TimeStampedPacket {
    private int packetID;
    private int senderID;
    private List<Message> messages;
    private long timestamp;

    public TimeStampedPacket(int packetID, int senderID, List<Message> messages) {
        this.packetID = packetID;
        this.senderID = senderID;
        this.messages = messages;

    }

    public int getPacketID() {
        return packetID;
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
    ///format: packetID:senderID:timestamp:message1,message2...
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(packetID).append(":")
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

    public static TimeStampedPacket fromString(String packetString) {
        String[] parts = packetString.split(":");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid packet string format");
        }
    
        int packetID = Integer.parseInt(parts[0]);
        int senderID = Integer.parseInt(parts[1]);
        long timestamp = Long.parseLong(parts[2]);
        List<Message> messages = new ArrayList<>();
    
        String messagesPart = parts[3];
        String[] messageStrings = messagesPart.split(",");
        for (String messageString : messageStrings) {
            messages.add(Message.fromString(messageString));
        }
    
        TimeStampedPacket packet = new TimeStampedPacket(packetID, senderID, messages);
        packet.setTimestamp(timestamp);
        return packet;
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