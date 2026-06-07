import decryptor.Decryptor;
import dto.Message;
import encryptor.Encryptor;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientTcp implements Client {

    private InetAddress serverAddress;
    private int serverPort;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private volatile ClientState state = ClientState.DISCONNECTED;
    private final Object stateLock = new Object();

    private final ConcurrentLinkedQueue<Message> offlineQueue = new ConcurrentLinkedQueue<>();

    private final Encryptor encryptor;
    private final Decryptor decryptor;

    private final Consumer<Message> onMessageReceived;

    public ClientTcp(
            Encryptor encryptor,
            Decryptor decryptor,
            Consumer<Message> onMessageReceived
    ) {
        this.encryptor = encryptor;
        this.decryptor = decryptor;
        this.onMessageReceived = onMessageReceived;
    }

    @Override
    public void connect(InetAddress address, int port) {
        this.serverAddress = address;
        this.serverPort = port;
        this.state = ClientState.CONNECTING;

        Thread reconnectThread = new Thread(this::connectionManagerLoop, "TCP-Reconnect-Loop");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    @Override
    public void disconnect() {
        synchronized (stateLock) {
            this.state = ClientState.DISCONNECTED;
            stateLock.notifyAll();
        }

        try {
            if (socket != null && !socket.isClosed())
                socket.close();

            log.info("TCP Client socket closed");
        } catch (IOException e) {
            log.error("Error while closing client socket", e);
        }
    }

    @Override
    public void sendCommand(Message message) {
        if (state != ClientState.CONNECTED) {
            log.info("Offline. Adding message ID {} to local queue.", message.getMessageId());
            offlineQueue.add(message);
            return;
        }

        try {
            byte[] encryptedMessage = encryptor.encrypt(message);

            synchronized (this) {
                out.write(encryptedMessage);
                out.flush();
            }
        } catch (IOException e) {
            log.warn(
                    "Connection lost while sending! Buffering message ID {}",
                    message.getMessageId()
            );
            offlineQueue.add(message);
            synchronized (stateLock) {
                state = ClientState.CONNECTING;
                stateLock.notifyAll();
            }
        }
    }

    @SuppressWarnings("BusyWait")
    private void connectionManagerLoop() {
        while (state != ClientState.DISCONNECTED) {
            if (state == ClientState.CONNECTING) {
                try {
                    socket = new Socket(serverAddress, serverPort);
                    out = new DataOutputStream(socket.getOutputStream());
                    in = new DataInputStream(socket.getInputStream());

                    state = ClientState.CONNECTED;
                    log.info("Successfully connected to {}!", serverAddress.getHostAddress());

                    Thread listenerThread = new Thread(
                            this::listenForServerMessages,
                            "TCP-Listener-" + socket.getLocalPort()
                    );
                    listenerThread.setDaemon(true);
                    listenerThread.start();

                    flushOfflineQueue();
                } catch (IOException e) {
                    log.debug("Connection failed. Retrying in 3 seconds...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            } else {
                synchronized (stateLock) {
                    try {
                        if (state == ClientState.CONNECTED)
                            stateLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void listenForServerMessages() {
        try {
            while (state == ClientState.CONNECTED && !Thread.currentThread().isInterrupted()) {
                byte[] header = new byte[14];
                in.readFully(header);

                ByteBuffer headerBuffer = ByteBuffer.wrap(header);
                headerBuffer.position(10);
                int payloadSize = headerBuffer.getInt();

                int remainingSize = 2 + payloadSize + 2;
                byte[] remainingData = new byte[remainingSize];
                in.readFully(remainingData);

                byte[] fullData = new byte[14 + remainingSize];
                System.arraycopy(header, 0, fullData, 0, 14);
                System.arraycopy(remainingData, 0, fullData, 14, remainingSize);

                Message message = decryptor.decrypt(fullData);
                if (onMessageReceived != null)
                    onMessageReceived.accept(message);

                log.info(
                        "Received message from TCP Server {}:{}: {}",
                        socket.getInetAddress().getHostName(),
                        socket.getPort(),
                        message
                );
            }
        } catch (IOException e) {
            synchronized (stateLock) {
                if (state != ClientState.DISCONNECTED) {
                    log.warn("Lost connection to server! Switching to reconnect mode...");
                    state = ClientState.CONNECTING;
                    stateLock.notifyAll();
                }
            }
        }
    }

    private synchronized void flushOfflineQueue() {
        if (offlineQueue.isEmpty()) return;

        log.info("Flushing {} offline messages to the server...", offlineQueue.size());
        Message msg;

        while ((msg = offlineQueue.peek()) != null) {
            try {
                byte[] encryptedMessage = encryptor.encrypt(msg);
                out.write(encryptedMessage);
                out.flush();

                offlineQueue.poll();
            } catch (IOException e) {
                log.error("Failed to flush queue, connection dropped again!");
                state = ClientState.CONNECTING;
                break;
            }
        }
    }
}
