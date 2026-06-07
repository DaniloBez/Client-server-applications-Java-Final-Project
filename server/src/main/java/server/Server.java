package server;

import decryptor.Decryptor;
import decryptor.DecryptorNode;
import dto.Message;
import dto.NetworkMessage;
import encryptor.Encryptor;
import encryptor.EncryptorNode;
import encryptor.MessageEncryptor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import processor.Processor;
import processor.ProcessorNode;
import sender.SenderNode;
import utils.ServerSignals;

@Slf4j
public class Server {
    private final LinkedTransferQueue<NetworkMessage<byte[]>> rawInputQueue
            = new LinkedTransferQueue<>();
    private final LinkedTransferQueue<NetworkMessage<Message>> decodedQueue
            = new LinkedTransferQueue<>();
    private final LinkedTransferQueue<NetworkMessage<Message>> responseQueue
            = new LinkedTransferQueue<>();
    private final LinkedTransferQueue<NetworkMessage<byte[]>> rawOutputQueue
            = new LinkedTransferQueue<>();

    private final ExecutorService executorService;
    private final ConnectionManager connectionManager = new ConnectionManager();

    private final int port;

    private AtomicBoolean isTcpServerRun = new AtomicBoolean(false);
    private TcpServer tcpServer;

    private final Processor processor;
    private final Decryptor decryptor;
    private final Encryptor encryptor;

    private final int senderCount;
    private final int decryptorCount;
    private final int encryptorCount;
    private final int processorCount;

    public Server(
            int senderCount,
            Decryptor decryptor,
            int decryptorCount,
            MessageEncryptor encryptor,
            int encryptorCount,
            Processor processor,
            int processorCount,
            int port
    ) {
        validate(senderCount, decryptorCount, encryptorCount, processorCount, port);

        this.senderCount = senderCount;
        this.decryptor = decryptor;
        this.decryptorCount = decryptorCount;
        this.encryptor = encryptor;
        this.encryptorCount = encryptorCount;
        this.processor = processor;
        this.processorCount = processorCount;

        this.port = port;

        this.executorService = Executors.newFixedThreadPool(
                2
                        + senderCount
                        + decryptorCount
                        + encryptorCount
                        + processorCount
        );
    }

    private void validate(
            int senderCount,
            int decryptorCount,
            int encryptorCount,
            int processorCount,
            int port
    ) throws IllegalArgumentException {
        if (senderCount <= 0)
            throw new IllegalArgumentException("Sender count must be greater than 0");
        if (decryptorCount <= 0)
            throw new IllegalArgumentException("Decryptor count must be greater than 0");
        if (processorCount <= 0)
            throw new IllegalArgumentException("Processor count must be greater than 0");
        if (encryptorCount <= 0)
            throw new IllegalArgumentException("TCP port must be greater than 0");

        if (port <= 1000)
            throw new IllegalArgumentException("TCP port must be greater than 1000");
    }

    public void start() {
        log.info("Starting TCP Server");
        this.isTcpServerRun = new AtomicBoolean(true);
        this.tcpServer = new TcpServer(port, connectionManager, isTcpServerRun, rawInputQueue);
        executorService.execute(tcpServer);

        log.info("Launching threads (Scale up)...");

        AtomicInteger activeDescriptors = new AtomicInteger(decryptorCount);
        for (int i = 0; i < decryptorCount; i++)
            executorService.submit(
                    new DecryptorNode(rawInputQueue, decodedQueue, decryptor, activeDescriptors)
            );

        AtomicInteger activeProcessors = new AtomicInteger(processorCount);
        for (int i = 0; i < processorCount; i++)
            executorService.submit(
                    new ProcessorNode(decodedQueue, responseQueue, processor, activeProcessors)
            );

        AtomicInteger activeEncryptors = new AtomicInteger(encryptorCount);
        for (int i = 0; i < encryptorCount; i++)
            executorService.submit(
                    new EncryptorNode(responseQueue, rawOutputQueue, encryptor, activeEncryptors)
            );

        for (int i = 0; i < senderCount; i++)
            executorService.submit(new SenderNode(rawOutputQueue, connectionManager));

        log.info("The server has started successfully and is ready to go!");
    }

    public void stop() {
        log.info("A shutdown signal has been received. Initiating a graceful shutdown...");

        log.info("Shutting down TCP Server");
        isTcpServerRun.set(false);
        if (tcpServer != null)
            tcpServer.stop();

        log.info("Shutting down connections");
        connectionManager.closeAllConnections();

        rawInputQueue.put(ServerSignals.POISON_PILL_BYTES);

        executorService.shutdown();

        log.info("The server is shutting down. "
                + "We are waiting for the remaining items in the queues to be processed...");

        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                log.error("Some threads didn't have time to stop! "
                        + "We're performing a forced shutdown.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error(
                    "Unexpected interrupted while waiting for threads to shut down: {}",
                    e.getMessage()
            );
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
