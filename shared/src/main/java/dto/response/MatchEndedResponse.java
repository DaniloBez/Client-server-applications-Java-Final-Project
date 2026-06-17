package dto.response;

public record MatchEndedResponse(
        Boolean isYouWinner,
        byte yourFinalScore,
        byte opponentFinalScore,
        int eloDelta
) {
}
