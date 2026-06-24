package server;

import decryptor.Decryptor;
import decryptor.DecryptorNode;
import dto.Message;
import dto.NetworkMessage;
import encryptor.Encryptor;
import encryptor.EncryptorNode;
import encryptor.MessageEncryptor;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import processor.Processor;
import processor.ProcessorNode;
import repository.MatchRepository;
import repository.UserRepository;
import sender.SenderNode;
import server.session.ConnectionManager;
import server.session.SessionRegistry;
import service.UserService;
import service.LobbyService;
import service.game.GameManager;
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
    private final HttpServer httpAuthServer;

    private final GameManager gameManager;
    private final LobbyService lobbyService;
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
            int processorCount,
            int tcpPort,
            int httpPort,
            String jwtSecret,
            UserRepository userRepository,
            MatchRepository matchRepository
    ) {
        validate(senderCount, decryptorCount, encryptorCount, processorCount, tcpPort, httpPort);

        this.senderCount = senderCount;
        this.decryptor = decryptor;
        this.decryptorCount = decryptorCount;
        this.encryptor = encryptor;
        this.encryptorCount = encryptorCount;

        Consumer<Message> asyncDispatcher = message -> {
            String connId = SessionRegistry.getConnectionId(message.getUserId());
            if (connId != null)
                responseQueue.offer(new NetworkMessage<>(connId, message));
        };

        this.gameManager = new GameManager(userRepository, matchRepository);
        this.gameManager.setMessageDispatcher(asyncDispatcher);

        this.lobbyService = new LobbyService(this.gameManager);
        this.lobbyService.setMessageDispatcher(asyncDispatcher);

        UserService authService = new UserService(userRepository, jwtSecret);
        this.httpAuthServer = new HttpServer(
                httpPort,
                authService,
                this.connectionManager
        );

        this.processor = new Processor(this.lobbyService, this.gameManager, authService);
        this.processorCount = processorCount;

        this.port = tcpPort;

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
            int tcpPort,
            int httpPort
    ) throws IllegalArgumentException {
        if (senderCount <= 0)
            throw new IllegalArgumentException("Sender count must be greater than 0");
        if (decryptorCount <= 0)
            throw new IllegalArgumentException("Decryptor count must be greater than 0");
        if (processorCount <= 0)
            throw new IllegalArgumentException("Processor count must be greater than 0");
        if (encryptorCount <= 0)
            throw new IllegalArgumentException("Encryptor count must be greater than 0");

        if (tcpPort <= 1000 || httpPort <= 1000)
            throw new IllegalArgumentException("Ports must be greater than 1000");
    }

    public void start() {
        log.info("Starting TCP Server");
        this.isTcpServerRun = new AtomicBoolean(true);
        connectionManager.setMessageDispatcher(message -> {
            String connId = SessionRegistry.getConnectionId(message.getUserId());
            decodedQueue.offer(new NetworkMessage<>(
                    Objects.requireNonNullElse(connId, ""),
                    message)
            );
        });
        connectionManager.setOutboundDispatcher(message -> {
            String connId = SessionRegistry.getConnectionId(message.getUserId());
            if (connId != null)
                responseQueue.offer(new NetworkMessage<>(connId, message));
        });
        this.tcpServer = new TcpServer(port, connectionManager, isTcpServerRun, rawInputQueue);
        executorService.execute(tcpServer);

        this.httpAuthServer.start();

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
            
        if (httpAuthServer != null)
            httpAuthServer.stop();

        log.info("Shutting down game services");
        gameManager.stop();
        lobbyService.stop();

        log.info("Shutting down connections");
        connectionManager.closeAllConnections();

        rawInputQueue.put(ServerSignals.END_BYTES);

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
