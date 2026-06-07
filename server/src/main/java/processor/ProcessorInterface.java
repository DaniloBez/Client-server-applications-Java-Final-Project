package processor;

import dto.Message;
import java.util.List;

public interface ProcessorInterface {
    List<Message> process(Message message);
}
