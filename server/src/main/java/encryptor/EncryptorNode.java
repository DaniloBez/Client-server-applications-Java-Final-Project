package encryptor;

import dto.Message;
import dto.NetworkMessage;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import utils.ServerSignals;

@Slf4j
public class EncryptorNode implements Runnable {
    private final LinkedTransferQueue<NetworkMessage<Message>> inputQueue;
    private final LinkedTransferQueue<NetworkMessage<byte[]>> outputQueue;
    private final Encryptor encryptor;
    private final AtomicInteger activeEncryptorsCounter;

    public EncryptorNode(
            LinkedTransferQueue<NetworkMessage<Message>> inputQueue,
            LinkedTransferQueue<NetworkMessage<byte[]>> outputQueue,
            Encryptor encryptor,
            AtomicInteger activeEncryptorsCounter
    ) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.encryptor = encryptor;
        this.activeEncryptorsCounter = activeEncryptorsCounter;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                NetworkMessage<Message> message = inputQueue.take();

                if (message == ServerSignals.POISON_PILL_MSG) {
                    log.info("EncryptorNode thread {} stopped", Thread.currentThread().getName());

                    if (activeEncryptorsCounter.decrementAndGet() == 0) {
                        log.info("The last encryptor has finished its work. "
                                + "Passing the poison pill to the Senders.");
                        outputQueue.put(ServerSignals.POISON_PILL_BYTES);
                    } else
                        inputQueue.put(ServerSignals.POISON_PILL_MSG);

                    break;
                }

                byte[] encryptedBytes;
                try {
                    encryptedBytes = encryptor.encrypt(message.data());
                } catch (Exception e) {
                    log.error("Failed to encrypt message for {}. Dropping packet. Reason: {}",
                            message.connectionId(), e.getMessage());
                    continue;
                }

                NetworkMessage<byte[]> encryptedMsg = new NetworkMessage<>(
                        message.connectionId(),
                        encryptedBytes
                );
                outputQueue.put(encryptedMsg);
            }
        } catch (InterruptedException e) {
            log.error(
                    "EncryptorNode thread {} has been interrupted: {}",
                    Thread.currentThread().getName(),
                    e.getMessage()
            );
            Thread.currentThread().interrupt();
        }
    }
}
