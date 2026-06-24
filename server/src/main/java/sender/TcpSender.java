package sender;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpSender implements Sender {
    private final DataOutputStream out;

    public TcpSender(Socket socket) throws IOException {
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public synchronized void send(byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch (IOException e) {
            log.error("Failed to send data to TCP socket", e);
        }
    }

    @Override
    public void close() {
        try {
            out.close();
            log.info("TCP socket closed successfully");
        } catch (IOException e) {
            log.error("Error closing TCP socket", e);
        }
    }
}
