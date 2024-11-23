package cs451;

public class Message {
    private int payload;


    public Message(int payload) {
        this.payload = payload;
    }

    public int getPayload() {
        return payload;
    }

    @Override
    public String toString() {
    return String.valueOf(payload);
    }

    public static Message fromString(String messageString) {
        int payload = Integer.parseInt(messageString);
        return new Message(payload);
    }
}