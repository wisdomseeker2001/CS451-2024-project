package cs451;

public class Sender_Packet_Size_Tuple {
    public final int HostID;
    public final int PacketID;
    public final int PacketSize;

    public Sender_Packet_Size_Tuple(int senderID, int packetID, int packetSize) {
        this.HostID = senderID;
        this.PacketID = packetID;
        this.PacketSize = packetSize;
    }
    
}