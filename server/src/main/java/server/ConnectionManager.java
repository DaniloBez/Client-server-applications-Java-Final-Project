package server;

import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import sender.Sender;

@Slf4j
public class ConnectionManager {
    private final ConcurrentHashMap<String, Sender> activeConnections = new ConcurrentHashMap<>();

    public void addConnection(String connectionId, Sender sender) {
        activeConnections.put(connectionId, sender);
        log.info("Added connection with id {}", connectionId);
    }

    public void removeConnection(String connectionId) {
        activeConnections.remove(connectionId);
        log.info("Removed connection with id {}", connectionId);
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

    public Iterable<String> getActiveConnectionIds() {
        return activeConnections.keySet();
    }
}
