package cs451;

public class PacketIDHostTuple {
    public final int packetID;
    public final Host senderHost;

    public PacketIDHostTuple(int packetID, Host senderHost) {
        this.packetID = packetID;
        this.senderHost = senderHost;
    }
}