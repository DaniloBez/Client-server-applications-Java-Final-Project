package decryptor;

import dto.Message;
import exception.DecryptionException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import utils.crypto.Crc16;
import utils.crypto.CryptoUtils;

public class MessageDecryptor implements Decryptor {
    private static final byte MAGIC_BYTE = 0x13;
    private static final byte HEADER_LENGTH = 14;

    public Message decrypt(byte[] data) throws DecryptionException {
        if (data == null || data.length < HEADER_LENGTH + 2 + 4 + 4 + 2)
            throw new DecryptionException("Message too short or null");

        ByteBuffer buffer = ByteBuffer.wrap(data);

        byte magic = buffer.get();
        if (magic != MAGIC_BYTE)
            throw new DecryptionException("Invalid magic byte: " + String.format("0x%02X", magic));

        byte[] headerForCrc = Arrays.copyOfRange(data, 0, HEADER_LENGTH);
        short expectedHeaderCrc = Crc16.calculateCrc(headerForCrc);

        buffer.position(HEADER_LENGTH);
        short actualHeaderCrc = buffer.getShort();

        if (actualHeaderCrc != expectedHeaderCrc)
            throw new DecryptionException("Header CRC mismatch");

        buffer.position(1);
        byte appId = buffer.get();
        long messageId = buffer.getLong();
        int payloadSize = buffer.getInt();

        int dataLength = payloadSize - 8;
        if (buffer.remaining() < payloadSize + 2)
            throw new DecryptionException("Declared data length exceeds actual buffer size");

        int payloadStart = HEADER_LENGTH + 2;
        byte[] payloadForCrc = Arrays.copyOfRange(data, payloadStart, payloadStart + payloadSize);
        short expectedPayloadCrc = Crc16.calculateCrc(payloadForCrc);

        buffer.position(payloadStart + payloadSize);
        short actualPayloadCrc = buffer.getShort();

        if (actualPayloadCrc != expectedPayloadCrc)
            throw new DecryptionException("Payload CRC mismatch");

        buffer.position(payloadStart);
        int commandId = buffer.getInt();
        int userId = buffer.getInt();

        byte[] encryptedData = new byte[dataLength];
        buffer.get(encryptedData);
        String messageData;

        try {
            messageData = new String(CryptoUtils.decrypt(encryptedData));
        } catch (Exception e) {
            throw new DecryptionException(e.getMessage());
        }

        return new Message(appId, messageId, commandId, userId, messageData);
    }
}
