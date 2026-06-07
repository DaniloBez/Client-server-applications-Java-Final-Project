package encryptor;

import dto.Message;

public interface Encryptor {
    byte[] encrypt(Message message);
}
