package processor;

import dto.Message;
import dto.NetworkMessage;
import java.util.List;

public interface ProcessorInterface {
    List<Message> process(NetworkMessage<Message> message);
}
