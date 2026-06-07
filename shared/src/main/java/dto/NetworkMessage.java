package dto;

public record NetworkMessage<T>(
        String connectionId,
        T data
) {
}
