package processor;

import dto.Message;
import dto.NetworkMessage;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import utils.ServerSignals;

@Slf4j
public class ProcessorNode implements Runnable {

    private final LinkedTransferQueue<NetworkMessage<Message>> inputQueue;
    private final LinkedTransferQueue<NetworkMessage<Message>> outputQueue;
    private final ProcessorInterface processor;
    private final AtomicInteger activeProcessorsCounter;

    public ProcessorNode(
            LinkedTransferQueue<NetworkMessage<Message>> inputQueue,
            LinkedTransferQueue<NetworkMessage<Message>> outputQueue,
            ProcessorInterface processor,
            AtomicInteger activeProcessorsCounter
    ) {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.processor = processor;
        this.activeProcessorsCounter = activeProcessorsCounter;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                NetworkMessage<Message> inputMessage = inputQueue.take();

                if (inputMessage == ServerSignals.POISON_PILL_MSG) {
                    log.info("ProcessorNode thread {} stopped", Thread.currentThread().getName());

                    if (activeProcessorsCounter.decrementAndGet() == 0) {
                        log.info("The last processor has finished its work."
                                + " Passing the poison pill to the Encryptors.");
                        outputQueue.put(ServerSignals.POISON_PILL_MSG);
                    } else
                        inputQueue.put(ServerSignals.POISON_PILL_MSG);

                    break;
                }

                if (inputMessage.data() == ServerSignals.DISCONNECT_PILL_MSG) {
                    log.info(
                            "Processor cleaned up cache for dead client: {}",
                            inputMessage.connectionId()
                    );
                    continue;
                }

                List<Message> resultMessages = processor.process(inputMessage.data());

                for (Message message : resultMessages) {
                    NetworkMessage<Message> outputMessage = new NetworkMessage<>(
                            inputMessage.connectionId(),
                            message
                    );
                    outputQueue.put(outputMessage);
                }
            }
        } catch (InterruptedException e) {
            log.info(
                    "ProcessorNode thread {} interrupted: {}",
                    Thread.currentThread().getName(),
                    e.getMessage()
            );
            Thread.currentThread().interrupt();
        }
    }
}
