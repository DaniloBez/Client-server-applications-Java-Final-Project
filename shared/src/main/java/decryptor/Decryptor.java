package decryptor;

import dto.Message;

public interface Decryptor {
    Message decrypt(byte[] message);
}
