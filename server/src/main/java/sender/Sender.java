package sender;

public interface Sender {
    void send(byte[] message);

    void close();
}
