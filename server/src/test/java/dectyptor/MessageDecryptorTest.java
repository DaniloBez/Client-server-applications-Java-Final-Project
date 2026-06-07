package dectyptor;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import decryptor.MessageDecryptor;
import dto.Message;
import exception.DecryptionException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import utils.crypto.Crc16;

public class MessageDecryptorTest {

    MessageDecryptor decoder = new MessageDecryptor();

    @Test
    public void shouldDecodeCorrectly() {
        byte[] encodedData = {0x13, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39, 0x00,
                0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00,
                0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0, (byte) 0xE3, 0x35,
                (byte) 0xF0, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D, (byte) 0xB4, 0x5B, (byte) 0x98,
                0x72, (byte) 0xC4, (byte) 0xE0};

        Message expected = new Message(
                (byte) 1,
                12345L,
                10,
                999,
                "Hello world!"
        );

        Message actual = decoder.decrypt(encodedData);

        assertEquals(expected, actual);
    }

    @Test
    public void badDataTest() {
        assertThatThrownBy(() -> decoder.decrypt(null))
                .isInstanceOf(DecryptionException.class).hasMessage("Message too short or null");

        assertThatThrownBy(() -> decoder.decrypt(new byte[0]))
                .isInstanceOf(DecryptionException.class).hasMessage("Message too short or null");
    }

    @Test
    public void shouldThrowOnInvalidMagicByte() {
        byte[] encodedData = {0x11, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39, 0x00,
                0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00,
                0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0, (byte) 0xE3, 0x35,
                (byte) 0xF0, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D, (byte) 0xB4, 0x5B, (byte) 0x98,
                0x72, (byte) 0xC4, (byte) 0xE0};

        assertThatThrownBy(() -> decoder.decrypt(encodedData))
                .isInstanceOf(DecryptionException.class)
                .message().startsWith("Invalid magic byte: ");
    }

    @Test
    public void shouldThrowOnInvalidCrc() {
        byte[] encodedData = {0x13, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39, 0x00,
                0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00,
                0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0, (byte) 0xE3,
                0x35, (byte) 0xF0, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D, (byte) 0xB4, 0x5B,
                (byte) 0x98, 0x72, (byte) 0xC4, (byte) 0xE0};

        assertThatThrownBy(() -> decoder.decrypt(encodedData))
                .isInstanceOf(DecryptionException.class).hasMessage("Header CRC mismatch");


        byte[] encodedData2 = {0x13, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39, 0x00,
                0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00,
                0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0, (byte) 0xE3, 0x35,
                (byte) 0xF9, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D, (byte) 0xB4, 0x5B, (byte) 0x98,
                0x72, (byte) 0xC4, (byte) 0xE0};

        assertThatThrownBy(() -> decoder.decrypt(encodedData2))
                .isInstanceOf(DecryptionException.class).hasMessage("Payload CRC mismatch");
    }

    @Test
    public void shouldThrowOnTruncatedPayload() {
        byte[] encodedData = {0x13, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39, 0x00,
                0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00,
                0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0, (byte) 0xE3,
                0x35, (byte) 0xF0, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D, (byte) 0xB4, 0x5B,
                (byte) 0x98, 0x72, (byte) 0xC4, (byte) 0xE0};

        // Імітуємо втрату даних
        byte[] truncated = Arrays.copyOf(encodedData, encodedData.length - 5);

        assertThatThrownBy(() -> decoder.decrypt(truncated))
                .isInstanceOf(DecryptionException.class)
                .hasMessage("Declared data length exceeds actual buffer size");

    }

    @Test
    public void shouldThrowOnCorruptedEncryptedData() {
        byte[] encodedData = {0x13, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x39, 0x00,
                0x00, 0x00, 0x18, (byte) 0xAD, (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00,
                0x03, (byte) 0xE7, (byte) 0xCE, 0x63, (byte) 0x9C, (byte) 0xD0, (byte) 0xE3, 0x35,
                (byte) 0xF0, (byte) 0xC3, 0x75, 0x4A, 0x02, 0x2D, (byte) 0xB4, 0x5B, (byte) 0x98,
                0x72, (byte) 0xC4, (byte) 0xE0};

        int encryptedDataIndex = encodedData.length - 5;

        // Псуємо данні
        encodedData[encryptedDataIndex] = (byte) (encodedData[encryptedDataIndex] ^ 0xFF);

        int payloadStart = 16;
        int payloadSize = encodedData.length - 18;
        byte[] newPayloadForCrc = Arrays.copyOfRange(
                encodedData,
                payloadStart,
                payloadStart + payloadSize
        );
        short newCrc = Crc16.calculateCrc(newPayloadForCrc); // перераховуємо payload crc

        ByteBuffer buffer = ByteBuffer.wrap(encodedData);
        buffer.putShort(encodedData.length - 2, newCrc);

        assertThatThrownBy(() -> decoder.decrypt(encodedData))
                .isInstanceOf(DecryptionException.class)
                .hasMessage("Given final block not properly padded. "
                        + "Such issues can arise if a bad key is used during decryption.");
    }
}
