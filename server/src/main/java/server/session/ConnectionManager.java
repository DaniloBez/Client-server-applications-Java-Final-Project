package server.session;

import dto.Commands;
import dto.Message;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import sender.Sender;

@Slf4j
public class ConnectionManager {
    private final ConcurrentHashMap<String, Sender> activeConnections = new ConcurrentHashMap<>();

    @Setter
    private Consumer<Message> messageDispatcher;

    public void addConnection(String connectionId, Sender sender) {
        activeConnections.put(connectionId, sender);
        log.info("Added connection with id {}", connectionId);
    }

    public void removeConnection(String connectionId) {
        activeConnections.remove(connectionId);
        log.info("Removed connection with id {}", connectionId);

        Integer userId = SessionRegistry.getUserId(connectionId);
        if (userId != null && messageDispatcher != null) {
            Message disconnectMessage = new Message(
                    (byte) 0,
                    0,
                    Commands.PLAYER_DISCONNECTED,
                    userId,
                    ""
            );
            messageDispatcher.accept(disconnectMessage);
        }
        SessionRegistry.remove(connectionId);
    }

    public Sender getSender(String connectionId) {
        return activeConnections.get(connectionId);
    }

    public void closeAllConnections() {
        log.info("Closing all active connections...");

        for (Sender sender : activeConnections.values())
            if (sender != null)
                sender.close();

        activeConnections.clear();
        log.info("All connections closed.");
    }
}
