package sender;

import dto.NetworkMessage;
import java.util.concurrent.BlockingQueue;
import lombok.extern.slf4j.Slf4j;
import server.session.ConnectionManager;
import utils.ServerSignals;

@Slf4j
public class SenderNode implements Runnable {
    private final BlockingQueue<NetworkMessage<byte[]>> inputQueue;
    private final ConnectionManager connectionManager;

    public SenderNode(
            BlockingQueue<NetworkMessage<byte[]>> inputQueue,
            ConnectionManager connectionManager
    ) {
        this.inputQueue = inputQueue;
        this.connectionManager = connectionManager;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                NetworkMessage<byte[]> message = inputQueue.take();

                if (message == ServerSignals.END_BYTES) {
                    inputQueue.put(ServerSignals.END_BYTES);
                    log.info("SenderNode thread {} stopped", Thread.currentThread().getName());
                    break;
                }

                Sender sender = connectionManager.getSender(message.connectionId());
                if (sender != null)
                    sender.send(message.data());
                else
                    log.info("Target connection not found: {}", message.connectionId());
            }
        } catch (InterruptedException e) {
            log.error(
                    "SenderNode thread {} interrupted: {}",
                    Thread.currentThread().getName(),
                    e.getMessage()
            );
            Thread.currentThread().interrupt();
        }
    }
}
