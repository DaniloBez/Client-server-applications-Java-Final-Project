package server;

import dto.NetworkMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import receiver.TcpReceiver;
import sender.TcpSender;
import server.session.ConnectionManager;
import utils.Constants;

@Slf4j
public class TcpServer implements Runnable {
    private final int port;
    private final ConnectionManager connectionManager;
    private final AtomicBoolean isRunning;
    private final LinkedTransferQueue<NetworkMessage<byte[]>> rawInputQueue;
    private final ExecutorService clientPool;

    private ServerSocket serverSocket;

    public TcpServer(
            int port,
            ConnectionManager connectionManager,
            AtomicBoolean isRunning,
            LinkedTransferQueue<NetworkMessage<byte[]>> rawInputQueue
    ) {
        this.port = port;
        this.connectionManager = connectionManager;
        this.isRunning = isRunning;
        this.rawInputQueue = rawInputQueue;
        this.clientPool = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port, 1000);
            log.info("Store Server TCP listening on port {}", port);

            while (isRunning.get() && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                String connectionId = Constants.TCP_HEADER + "-" + UUID.randomUUID();

                TcpSender sender = new TcpSender(clientSocket);
                connectionManager.addConnection(connectionId, sender);

                clientPool.execute(new TcpReceiver(
                        clientSocket,
                        connectionId,
                        rawInputQueue,
                        connectionManager
                ));
            }
        } catch (IOException e) {
            if (!isRunning.get())
                log.info("TCP Server socket closed gracefully.");
            else
                log.error("Store Server TCP Error", e);
        }
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing TCP server socket", e);
        }

        if (clientPool != null)
            clientPool.shutdownNow();
    }
}
