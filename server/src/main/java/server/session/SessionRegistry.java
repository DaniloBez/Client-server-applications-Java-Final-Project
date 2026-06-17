package server.session;

import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private static final ConcurrentHashMap<Long, String> userToConnection
            = new ConcurrentHashMap<>();

    public static void registerUser(long userId, String connectionId) {
        userToConnection.put(userId, connectionId);
    }

    public static String getConnectionId(long userId) {
        return userToConnection.get(userId);
    }

    public static void remove(String connectionId) {
        for (var entry : userToConnection.entrySet()) {
            if (entry.getValue().equals(connectionId)) {
                userToConnection.remove(entry.getKey());
                break;
            }
        }
    }

    public static Integer getUserId(String connectionId) {
        for (var entry : userToConnection.entrySet()) {
            if (entry.getValue().equals(connectionId))
                return entry.getKey().intValue();
        }

        return null;
    }
}
