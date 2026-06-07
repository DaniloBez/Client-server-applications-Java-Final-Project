package utils;

import dto.Message;
import dto.NetworkMessage;

public class ServerSignals {
    public static final NetworkMessage<byte[]> POISON_PILL_BYTES = new NetworkMessage<>(
            null,
            new byte[0]
    );

    public static final NetworkMessage<Message> POISON_PILL_MSG = new NetworkMessage<>(
            null,
            new Message((byte) 0, 0L, -1, 0, "")
    );

    public static final Message DISCONNECT_PILL_MSG = new Message((byte) 0, 0L, -1, 0, "");
}
