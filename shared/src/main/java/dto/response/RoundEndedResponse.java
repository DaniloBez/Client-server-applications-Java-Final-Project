package dto.response;

public record RoundEndedResponse(
        Boolean isYouWinner,
        byte yourScore,
        byte opponentScore,
        boolean isYourMove
) {
}
