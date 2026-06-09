package service;

import java.util.Random;
import lombok.Getter;

public class GameSession {
    @Getter
    private final long matchId;

    @Getter
    private final long player1Id;
    @Getter
    private final long player2Id;

    @Getter
    private byte player1Score = 0;
    @Getter
    private byte player2Score = 0;

    @Getter
    private final int[][] map = new int[3][3];

    private boolean isFirstPlayerMove;
    private boolean isFirstPlayerStartGame;

    private byte gameCount = 0;

    public GameSession(long matchId, long player1Id, long player2Id) {
        this.matchId = matchId;
        this.player1Id = player1Id;
        this.player2Id = player2Id;

        Random rand = new Random();
        isFirstPlayerStartGame = rand.nextBoolean();
        isFirstPlayerMove = isFirstPlayerStartGame;
    }

    public synchronized MoveResult processMove(long playerId, byte row, byte col) {
        if (player1Id != playerId && player2Id != playerId)
            return MoveResult.INVALID;

        if (player1Id == playerId && !isFirstPlayerMove)
            return MoveResult.INVALID;

        if (player2Id == playerId && isFirstPlayerMove)
            return MoveResult.INVALID;

        if (row < 0 || row >= 3 || col < 0 || col >= 3)
            return MoveResult.INVALID;

        if (map[row][col] != GameConstant.EMPTY_CELL)
            return MoveResult.INVALID;

        if (
                (isFirstPlayerStartGame && playerId == player1Id)
                        || (!isFirstPlayerStartGame && playerId == player2Id)
        )
            map[row][col] = GameConstant.X_MOVE;
        else
            map[row][col] = GameConstant.O_MOVE;

        if (checkForWin()) {
            incrementScore(playerId);
            gameCount++;

            if (player1Score == 3 || player2Score == 3) {
                gameCount++;
                return MoveResult.MATCH_WIN;
            }

            if (gameCount == 5)
                return MoveResult.MATCH_END;

            return MoveResult.ROUND_WIN;
        }

        if (checkForDraw()) {
            gameCount++;

            if (gameCount == 5)
                return MoveResult.MATCH_END;

            return MoveResult.DRAW;
        }

        isFirstPlayerMove = !isFirstPlayerMove;

        return MoveResult.SUCCESS;
    }

    private boolean checkForDraw() {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                if (map[row][col] == GameConstant.EMPTY_CELL)
                    return false;

        return true;
    }

    private boolean checkForWin() {
        for (int row = 0; row < 3; row++) {
            if (map[row][0] != GameConstant.EMPTY_CELL
                    && map[row][0] == map[row][1]
                    && map[row][0] == map[row][2]
            ) {
                return true;
            }
        }

        for (int col = 0; col < 3; col++) {
            if (map[0][col] != GameConstant.EMPTY_CELL
                    && map[0][col] == map[1][col]
                    && map[0][col] == map[2][col]
            ) {
                return true;
            }
        }

        if (map[0][0] != GameConstant.EMPTY_CELL
                && map[0][0] == map[1][1]
                && map[0][0] == map[2][2]
        )
            return true;

        return map[2][0] != GameConstant.EMPTY_CELL
                && map[2][0] == map[1][1]
                && map[2][0] == map[0][2];
    }

    private void incrementScore(long winnerId) {
        if (winnerId == player1Id)
            player1Score++;
        else
            player2Score++;
    }

    public void resetBoard() {
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                map[i][j] = GameConstant.EMPTY_CELL;

        isFirstPlayerMove = !isFirstPlayerStartGame;
        isFirstPlayerStartGame = !isFirstPlayerStartGame;
    }
}
