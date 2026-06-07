package utils;

import dto.Message;
import dto.NetworkMessage;

public class ServerSignals {
    public static final NetworkMessage<byte[]> END_BYTES = new NetworkMessage<>(
            null,
            new byte[0]
    );

    public static final NetworkMessage<Message> END_MSG = new NetworkMessage<>(
            null,
            new Message((byte) 0, 0L, -1, 0, "")
    );

    public static final Message DISCONNECT_MSG = new Message((byte) 0, 0L, -1, 0, "");
}
