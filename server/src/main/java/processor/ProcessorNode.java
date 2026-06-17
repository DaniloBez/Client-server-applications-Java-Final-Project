package processor;

import dto.Commands;
import dto.Message;
import dto.NetworkMessage;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import server.session.SessionRegistry;
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

                if (inputMessage == ServerSignals.END_MSG) {
                    log.info("ProcessorNode thread {} stopped", Thread.currentThread().getName());

                    if (activeProcessorsCounter.decrementAndGet() == 0) {
                        log.info("The last processor has finished its work."
                                + " Passing the poison pill to the Encryptors.");
                        outputQueue.put(ServerSignals.END_MSG);
                    } else
                        inputQueue.put(ServerSignals.END_MSG);

                    break;
                }

                if (inputMessage.data() == ServerSignals.DISCONNECT_MSG) {
                    log.info(
                            "Processor handling disconnect for dead client: {}",
                            inputMessage.connectionId()
                    );

                    Integer disconnectedUserId = SessionRegistry.getUserId(
                            inputMessage.connectionId()
                    );

                    if (disconnectedUserId != null) {
                        Message disconnectMsg = new Message(
                                (byte) 0,
                                0,
                                Commands.PLAYER_DISCONNECTED,
                                disconnectedUserId,
                                ""
                        );

                        NetworkMessage<Message> netMsg = new NetworkMessage<>(
                                inputMessage.connectionId(),
                                disconnectMsg
                        );

                        List<Message> disconnectResults = processor.process(netMsg);
                        routeMessages(disconnectResults);
                    }
                    SessionRegistry.remove(inputMessage.connectionId());
                    continue;
                }

                List<Message> resultMessages = processor.process(inputMessage);
                routeMessages(resultMessages);
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

    private void routeMessages(List<Message> messages) throws InterruptedException {
        for (Message message : messages) {
            String targetConnId = SessionRegistry.getConnectionId(message.getUserId());
            if (targetConnId != null) {
                NetworkMessage<Message> outputMessage = new NetworkMessage<>(
                        targetConnId,
                        message
                );
                outputQueue.put(outputMessage);
            }
        }
    }
}
