import dto.Message;
import java.net.InetAddress;

public interface Client {
    void connect(InetAddress address, int port);

    void disconnect();

    void sendCommand(Message message);
}
