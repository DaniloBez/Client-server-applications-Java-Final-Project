package decryptor;

import dto.Message;
import dto.NetworkMessage;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import utils.ServerSignals;

@Slf4j
public class DecryptorNode implements Runnable {
    private final LinkedTransferQueue<NetworkMessage<byte[]>> inputQueue;
    private final LinkedTransferQueue<NetworkMessage<Message>> outputQueue;
    private final Decryptor decryptor;
    private final AtomicInteger activeEncryptorsCounter;

    public DecryptorNode(
            LinkedTransferQueue<NetworkMessage<byte[]>> inputQueue, 
            LinkedTransferQueue<NetworkMessage<Message>> outputQueue, 
            Decryptor decryptor, 
            AtomicInteger activeEncryptorsCounter
    ) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.decryptor = decryptor;
        this.activeEncryptorsCounter = activeEncryptorsCounter;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                NetworkMessage<byte[]> message = inputQueue.take();

                if (message == ServerSignals.END_BYTES) {
                    log.info("DecryptorNode thread {} stopped", Thread.currentThread().getName());

                    if (activeEncryptorsCounter.decrementAndGet() == 0) {
                        log.info("The last decryptor has finished its work. "
                                + "Passing the poison pill to the Processors.");
                        outputQueue.put(ServerSignals.END_MSG);
                    } else
                        inputQueue.put(ServerSignals.END_BYTES);

                    break;
                }

                Message payload;
                try {
                    payload = decryptor.decrypt(message.data());
                } catch (Exception e) {
                    log.warn("Security/Format error! Dropped invalid packet from {}: {}",
                            message.connectionId(), e.getMessage());
                    continue;
                }

                NetworkMessage<Message> decryptedMessage = new NetworkMessage<>(
                        message.connectionId(),
                        payload
                );

                outputQueue.put(decryptedMessage);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
