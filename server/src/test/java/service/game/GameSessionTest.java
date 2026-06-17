package service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GameSessionTest {
    private GameSession session;
    private final int p1 = 10;
    private final int p2 = 20;

    @BeforeEach
    void setUp() {
        session = new GameSession(1L, p1, p2);
    }

    private int getActivePlayer() throws Exception {
        Field field = GameSession.class.getDeclaredField("isFirstPlayerMove");
        field.setAccessible(true);
        boolean isFirst = (boolean) field.get(session);
        return isFirst ? p1 : p2;
    }

    private int getWaitingPlayer() throws Exception {
        return getActivePlayer() == p1 ? p2 : p1;
    }

    @Test
    void shouldRejectInvalidMoves() throws Exception  {
        int active = getActivePlayer();
        int waiting = getWaitingPlayer();

        assertEquals(MoveResult.INVALID, session.processMove(999, (byte) 0, (byte) 0));

        assertEquals(MoveResult.INVALID, session.processMove(waiting, (byte) 0, (byte) 0));

        assertEquals(MoveResult.INVALID, session.processMove(active, (byte) 3, (byte) 0));
        assertEquals(MoveResult.INVALID, session.processMove(active, (byte) -1, (byte) 0));

        assertEquals(MoveResult.SUCCESS, session.processMove(active, (byte) 1, (byte) 1));
        assertEquals(MoveResult.INVALID, session.processMove(waiting, (byte) 1, (byte) 1));
    }

    @Test
    void shouldRegisterValidMoveAndSwapTurn() throws Exception  {
        int firstToMove = getActivePlayer();
        int secondToMove = getWaitingPlayer();

        MoveResult result = session.processMove(firstToMove, (byte) 0, (byte) 0);
        assertEquals(MoveResult.SUCCESS, result);

        assertEquals(GameConstant.X_MOVE, session.getMap()[0][0]);

        assertEquals(secondToMove, getActivePlayer());
    }

    @Test
    void shouldDetectRoundWin() throws Exception  {
        int px = getActivePlayer();
        int po = getWaitingPlayer();

        assertEquals(MoveResult.SUCCESS, session.processMove(px, (byte) 0, (byte) 0));
        assertEquals(MoveResult.SUCCESS, session.processMove(po, (byte) 0, (byte) 1));
        assertEquals(MoveResult.SUCCESS, session.processMove(px, (byte) 1, (byte) 0));
        assertEquals(MoveResult.SUCCESS, session.processMove(po, (byte) 1, (byte) 1));

        assertEquals(MoveResult.ROUND_WIN, session.processMove(px, (byte) 2, (byte) 0));

        if (px == p1)
            assertEquals(1, session.getPlayer1Score());
        else
            assertEquals(1, session.getPlayer2Score());
    }

    @Test
    void shouldDetectDraw() throws Exception  {
        int px = getActivePlayer();
        int po = getWaitingPlayer();

        session.processMove(px, (byte) 0, (byte) 0);
        session.processMove(po, (byte) 0, (byte) 1);
        session.processMove(px, (byte) 0, (byte) 2);
        session.processMove(po, (byte) 1, (byte) 1);
        session.processMove(px, (byte) 1, (byte) 0);
        session.processMove(po, (byte) 1, (byte) 2);
        session.processMove(px, (byte) 2, (byte) 1);
        session.processMove(po, (byte) 2, (byte) 0);

        assertEquals(MoveResult.DRAW, session.processMove(px, (byte) 2, (byte) 2));

        assertEquals(0, session.getPlayer1Score());
        assertEquals(0, session.getPlayer2Score());
    }

    @Test
    void shouldDetectMatchWinAfterThreeRounds() throws Exception  {
        int champion = getActivePlayer();

        playActivePlayerWins(champion, getWaitingPlayer());
        assertEquals(MoveResult.ROUND_WIN, session.processMove(champion, (byte) 2, (byte) 0));
        session.resetBoard();

        assertEquals(champion, getWaitingPlayer());
        playWaitingPlayerWins(getActivePlayer(), champion);
        assertEquals(MoveResult.ROUND_WIN, session.processMove(champion, (byte) 1, (byte) 2));
        session.resetBoard();

        assertEquals(champion, getActivePlayer());
        playActivePlayerWins(champion, getWaitingPlayer());

        MoveResult finalResult = session.processMove(champion, (byte) 2, (byte) 0);
        assertEquals(MoveResult.MATCH_WIN, finalResult);
    }

    @Test
    void shouldDetectMatchEndAfterFiveRounds() throws Exception  {

        for (int i = 0; i < 4; i++) {
            int active = getActivePlayer();
            int waiting = getWaitingPlayer();

            playActivePlayerWins(active, waiting);
            assertEquals(MoveResult.ROUND_WIN, session.processMove(active, (byte) 2, (byte) 0));
            session.resetBoard();
        }

        int px = getActivePlayer();
        int po = getWaitingPlayer();

        session.processMove(px, (byte) 0, (byte) 0);
        session.processMove(po, (byte) 0, (byte) 1);
        session.processMove(px, (byte) 0, (byte) 2);
        session.processMove(po, (byte) 1, (byte) 1);
        session.processMove(px, (byte) 1, (byte) 0);
        session.processMove(po, (byte) 1, (byte) 2);
        session.processMove(px, (byte) 2, (byte) 1);
        session.processMove(po, (byte) 2, (byte) 0);

        assertEquals(MoveResult.MATCH_END, session.processMove(px, (byte) 2, (byte) 2));
    }

    @Test
    void shouldResetBoardAndSwapStarter() throws Exception  {
        int initialStarter = getActivePlayer();

        session.processMove(initialStarter, (byte) 1, (byte) 1);
        assertNotEquals(GameConstant.EMPTY_CELL, session.getMap()[1][1]);

        session.resetBoard();

        assertEquals(GameConstant.EMPTY_CELL, session.getMap()[1][1]);

        int newStarter = getActivePlayer();
        assertNotEquals(initialStarter, newStarter);
    }

    private void playActivePlayerWins(int active, int waiting) {
        session.processMove(active, (byte) 0, (byte) 0);
        session.processMove(waiting, (byte) 0, (byte) 1);
        session.processMove(active, (byte) 1, (byte) 0);
        session.processMove(waiting, (byte) 1, (byte) 1);
    }

    private void playWaitingPlayerWins(int active, int waiting) {
        session.processMove(active, (byte) 0, (byte) 0);
        session.processMove(waiting, (byte) 1, (byte) 0);
        session.processMove(active, (byte) 0, (byte) 1);
        session.processMove(waiting, (byte) 1, (byte) 1);
        session.processMove(active, (byte) 2, (byte) 2);
    }
}
