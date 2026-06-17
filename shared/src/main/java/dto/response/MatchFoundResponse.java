package dto.response;

public record MatchFoundResponse(
        String opponentName,
        int opponentElo,
        boolean isYouX,
        boolean isYourTurn
) {
}
