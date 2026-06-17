package dto.response;

public record PlayerMoveResponse(
        byte row,
        byte col,
        boolean isX,
        boolean isYourTurn
) {
}
