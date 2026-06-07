package encryptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dto.Message;
import java.nio.ByteBuffer;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.crypto.CryptoUtils;

public class MessageEncryptorTest {
    private final MessageEncryptor encoder = new MessageEncryptor();

    private static final int seed = 12345;
    private static Random random;

    @BeforeEach
    public void setUp() {
        random = new Random(seed);
    }

    private static Message createRandomMessage() {
        byte[] clientApplicationId = new byte[1];
        random.nextBytes(clientApplicationId);
        return new Message(
                clientApplicationId[0],
                random.nextLong(),
                random.nextInt(),
                random.nextInt(),
                generateRandomString(random.nextInt(100))
        );
    }

    private static String generateRandomString(int length) {
        byte[] array = new byte[length];
        random.nextBytes(array);
        return new String(array);
    }

    @Test
    public void shouldEncryptCorrectly() {
        Message message = new Message(
                (byte) 1,
                12345L,
                10,
                999,
                "Hello world!"
        );

        byte[] actual = encoder.encrypt(message);

        byte[] expected = {0x13, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39,
                0x00, 0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A,
                0x00, 0x00, 0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0,
                (byte) 0xE3, 0x35, (byte) 0xF0, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D,
                (byte) 0xB4, 0x5B, (byte) 0x98, 0x72, (byte) 0xC4, (byte) 0xE0};
        assertArrayEquals(expected, actual);
    }

    @Test
    public void shouldAlwaysHaveMagicByte() {
        for (int i = 0; i < 5; i++) {
            Message message = createRandomMessage();
            byte[] encoded = encoder.encrypt(message);
            assertEquals(0x13, encoded[0]);
        }
    }

    @Test
    public void shouldWorkWithEmptyData() throws Exception {
        Message message = new Message(
                (byte) 1,
                12345L,
                10,
                999,
                ""
        );

        byte[] encoded = encoder.encrypt(message);
        ByteBuffer buffer = ByteBuffer.wrap(encoded);

        assertEquals(24, buffer.getInt(10)); // Мінімум 16 байт на текст + 8 на commandId та userId

        byte[] encodedData = new byte[16];
        buffer.position(24);
        buffer.get(encodedData);
        String data = new String(CryptoUtils.decrypt(encodedData));
        assertTrue(data.isEmpty());
    }

    @Test
    public void crcShouldBeCorrect() {
        Message message = new Message(
                (byte) 1,
                12345L,
                10,
                999,
                "Hello world!"
        );

        byte[] encoded = encoder.encrypt(message);
        ByteBuffer buffer = ByteBuffer.wrap(encoded);

        buffer.position(14);
        assertEquals(-21111, buffer.getShort()); // crc header

        buffer.position(32);
        assertEquals(30026, buffer.getShort()); // crc payload
    }

    @Test
    public void shouldWorkWithBigData() {
        Message message = createRandomMessage();
        message.setData(generateRandomString(100_000));

        byte[] encoded = encoder.encrypt(message);
    }

    @Test
    public void shouldWorkWithStrangeData() throws Exception {
        Message message = createRandomMessage();
        String data = "Кирилиця_ґ_🎶";
        message.setData(data);

        byte[] encoded = encoder.encrypt(message);
        ByteBuffer buffer = ByteBuffer.wrap(encoded);

        buffer.position(10);
        int encryptedDataLength = buffer.getInt();

        buffer.position(24);
        byte[] extractedEncryptedData = new byte[encryptedDataLength - 8];
        buffer.get(extractedEncryptedData);

        byte[] decryptedBytes = CryptoUtils.decrypt(extractedEncryptedData);
        String actualData = new String(decryptedBytes);

        assertEquals(data, actualData);
    }
}
