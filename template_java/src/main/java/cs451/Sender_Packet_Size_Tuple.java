package cs451;

public class Sender_Packet_Size_Tuple {
    public final int senderID;
    public final int packetID;
    public final int packetSize;

    public Sender_Packet_Size_Tuple(int senderID, int packetID, int packetSize) {
        this.senderID = senderID;
        this.packetID = packetID;
        this.packetSize = packetSize;
    }

    public int getPacketID() {
        return packetID;
    }
    public int getPacketSize() {
        return packetSize;
    }
    public int getSenderID() {
        return senderID;
    }
    
}