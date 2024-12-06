package cs451;

import java.util.Comparator;

public class LogComparator implements Comparator<Sender_Packet_Size_Tuple> {
    @Override
    public int compare(Sender_Packet_Size_Tuple o1, Sender_Packet_Size_Tuple o2) {
        // Define the comparison logic
        return Integer.compare(o1.getPacketID(), o2.getPacketID());
    }
}