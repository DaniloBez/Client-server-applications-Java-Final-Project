package receiver;

import dto.NetworkMessage;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedTransferQueue;
import lombok.extern.slf4j.Slf4j;
import server.ConnectionManager;

@Slf4j
public class TcpReceiver implements Runnable {
    private final Socket socket;
    private final String connectionId;
    private final LinkedTransferQueue<NetworkMessage<byte[]>> rawInputQueue;
    private final ConnectionManager connectionManager;

    public TcpReceiver(
            Socket socket,
            String connectionId,
            LinkedTransferQueue<NetworkMessage<byte[]>> rawInputQueue,
            ConnectionManager connectionManager
    ) {
        this.socket = socket;
        this.connectionId = connectionId;
        this.rawInputQueue = rawInputQueue;
        this.connectionManager = connectionManager;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] header = new byte[14];
                in.readFully(header);

                ByteBuffer headerBuffer = ByteBuffer.wrap(header);
                headerBuffer.position(10);
                int payloadSize = headerBuffer.getInt();

                int remainingSize = 2 + payloadSize + 2;
                byte[] remainingData = new byte[remainingSize];

                in.readFully(remainingData);

                byte[] fullMessage = new byte[14 + remainingSize];
                System.arraycopy(header, 0, fullMessage, 0, 14);
                System.arraycopy(remainingData, 0, fullMessage, 14, remainingSize);

                NetworkMessage<byte[]> envelope = new NetworkMessage<>(connectionId, fullMessage);
                rawInputQueue.put(envelope);
            }
        } catch (IOException e) {
            String errorMessage = e.getMessage();

            if (isGracefulDisconnect(e, errorMessage))
                log.info(
                        "TCP client {} disconnected gracefully (Connection terminated)",
                        connectionId
                );
            else
                log.error("TCP client {} unexpected error: {}", connectionId, e.toString());
        } finally {
            connectionManager.removeConnection(connectionId);
            try {
                socket.close();
            } catch (IOException ex) {
                /* ignore */
            }
        }
    }

    private boolean isGracefulDisconnect(Exception e, String errorMessage) {
        if (e instanceof java.io.EOFException)
            return true;

        if (errorMessage == null)
            return false;

        return errorMessage.contains("Socket closed") || errorMessage.contains("Connection reset");
    }
}
