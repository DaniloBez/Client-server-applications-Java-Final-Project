package utils;

public class EloSystem {
    public static int calculateEloDelta(
            int playerElo,
            int opponentElo,
            int playerMatchCount,
            boolean isWinner,
            boolean isDraw,
            int playerScore,
            int opponentScore
    ) {
        int factor = determineFactor(playerElo, playerMatchCount);
        double expectedScore = 1.0 / (1.0 + Math.pow(10.0, (opponentElo - playerElo) / 400.0));

        if (isDraw)
            return (int) Math.round(factor * (0.5 - expectedScore));

        double rawDelta;
        double multiplier;
        if (isWinner) {
            rawDelta = factor * (1.0 - expectedScore);

            multiplier = switch (opponentScore) {
                case 1 -> 0.85;
                case 2 -> 0.70;
                default -> 1.0;
            };

        } else {
            rawDelta = factor * (0.0 - expectedScore);

            multiplier = switch (playerScore) {
                case 1 -> 0.85;
                case 2 -> 0.70;
                default -> 1.0;
            };

        }
        return (int) Math.round(rawDelta * multiplier);
    }

    private static int determineFactor(int playerElo, int playerMatchCount) {
        if (playerMatchCount < 30)
            return 40;

        if (playerElo >= 2000)
            return 10;

        return 20;
    }
}
