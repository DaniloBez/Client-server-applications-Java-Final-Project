package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EloSystemTest {

    @Test
    public void testCalculateEloDeltaNewPlayerWin() {
        // Player: 1000 ELO, 0 matches (Factor = 40)
        // Opponent: 1000 ELO
        // Result: Win, 3-0
        // Expected Score = 0.5
        // Delta = 40 * (1 - 0.5) * 1.0 = 20
        int delta = EloSystem.calculateEloDelta(
                1000,
                1000,
                0,
                true,
                false,
                3,
                0
        );
        assertEquals(20, delta);
    }

    @Test
    public void testCalculateEloDeltaNewPlayerLoss() {
        // Player: 1000 ELO, 0 matches (Factor = 40)
        // Opponent: 1000 ELO
        // Result: Loss, 0-3
        // Expected Score = 0.5
        // Delta = 40 * (0 - 0.5) * 1.0 = -20
        int delta = EloSystem.calculateEloDelta(
                1000,
                1000,
                0,
                false,
                false,
                0,
                3
        );
        assertEquals(-20, delta);
    }

    @Test
    public void testCalculateEloDeltaNormalPlayerWin() {
        // Player: 1500 ELO, 50 matches (Factor = 20)
        // Opponent: 1500 ELO
        // Result: Win, 3-0
        // Expected Score = 0.5
        // Delta = 20 * (1 - 0.5) * 1.0 = 10
        int delta = EloSystem.calculateEloDelta(
                1500,
                1500,
                50,
                true,
                false,
                3,
                0
        );
        assertEquals(10, delta);
    }

    @Test
    public void testCalculateEloDeltaAdvancedPlayerWin() {
        // Player: 2000 ELO, 100 matches (Factor = 10)
        // Opponent: 2000 ELO
        // Result: Win, 3-0
        // Expected Score = 0.5
        // Delta = 10 * (1 - 0.5) * 1.0 = 5
        int delta = EloSystem.calculateEloDelta(
                2000,
                2000,
                100,
                true,
                false,
                3,
                0
        );
        assertEquals(5, delta);
    }

    @Test
    public void testCalculateEloDeltaWinWithDroppedRounds() {
        // Player: 1500 ELO, 50 matches (Factor = 20)
        // Opponent: 1500 ELO
        // Result: Win, 3-1 (multiplier 0.85)
        // Raw Delta = 10. 10 * 0.85 = 8.5 -> round to 9
        int delta31 = EloSystem.calculateEloDelta(
                1500,
                1500,
                50,
                true,
                false,
                3,
                1
        );
        assertEquals(9, delta31);

        // Result: Win, 3-2 (multiplier 0.70)
        // Raw Delta = 10. 10 * 0.70 = 7
        int delta32 = EloSystem.calculateEloDelta(
                1500,
                1500,
                50,
                true,
                false,
                3,
                2
        );
        assertEquals(7, delta32);
    }

    @Test
    public void testCalculateEloDeltaLossWithWonRounds() {
        // Player: 1500 ELO, 50 matches (Factor = 20)
        // Opponent: 1500 ELO
        // Result: Loss, 1-3 (multiplier 0.85)
        // Raw Delta = -10. -10 * 0.85 = -8.5 -> round to -8
        int delta13 = EloSystem.calculateEloDelta(
                1500,
                1500,
                50,
                false,
                false,
                1,
                3
        );
        assertEquals(-8, delta13);

        // Result: Loss, 2-3 (multiplier 0.70)
        // Raw Delta = -10. -10 * 0.70 = -7
        int delta23 = EloSystem.calculateEloDelta(
                1500,
                1500,
                50,
                false,
                false,
                2,
                3
        );
        assertEquals(-7, delta23);
    }

    @Test
    public void testCalculateEloDeltaDraw() {
        // Player: 1500 ELO, 50 matches (Factor = 20)
        // Opponent: 1500 ELO
        // Expected Score = 0.5
        // Delta = 20 * (0.5 - 0.5) = 0
        int delta = EloSystem.calculateEloDelta(
                1500,
                1500,
                50,
                false,
                true,
                0,
                0
        );
        assertEquals(0, delta);

        // Player: 1400 ELO, Opponent: 1600 ELO (Expected < 0.5, Draw gives positive delta)
        // Expected Score for 1400 vs 1600 = 1 / (1 + 10^(200/400)) 
        // = 1 / (1 + 10^0.5) = 1 / 4.162 = 0.240
        // Delta = 20 * (0.5 - 0.24) = 20 * 0.26 = 5.2 -> 5
        int deltaUnderdog = EloSystem.calculateEloDelta(
                1400,
                1600,
                50,
                false,
                true,
                0,
                0
        );
        assertEquals(5, deltaUnderdog);
        
        // Overdog draws
        // Expected Score = ~0.76
        // Delta = 20 * (0.5 - 0.76) = 20 * -0.26 = -5.2 -> -5
        int deltaOverdog = EloSystem.calculateEloDelta(
                1600,
                1400,
                50,
                false,
                true,
                0,
                0
        );
        assertEquals(-5, deltaOverdog);
    }

    @Test
    public void testCalculateEloDeltaUnderdogWin() {
        // Player: 1400 ELO, Opponent: 1600 ELO (Expected Score ~0.240)
        // Delta = 20 * (1 - 0.240) = 15.2 -> 15
        int delta = EloSystem.calculateEloDelta(
                1400,
                1600,
                50,
                true,
                false,
                3,
                0
        );
        assertEquals(15, delta);
    }

    @Test
    public void testCalculateEloDeltaOverdogWin() {
        // Player: 1600 ELO, Opponent: 1400 ELO (Expected Score ~0.760)
        // Delta = 20 * (1 - 0.760) = 4.8 -> 5
        int delta = EloSystem.calculateEloDelta(
                1600,
                1400,
                50,
                true,
                false,
                3,
                0
        );
        assertEquals(5, delta);
    }

    @Test
    public void testCalculateEloDeltaUnderdogLoss() {
        // Player: 1400 ELO, Opponent: 1600 ELO (Expected Score ~0.240)
        // Delta = 20 * (0 - 0.240) = -4.8 -> -5
        int delta = EloSystem.calculateEloDelta(
                1400,
                1600,
                50,
                false,
                false,
                0,
                3
        );
        assertEquals(-5, delta);
    }

    @Test
    public void testCalculateEloDeltaOverdogLoss() {
        // Player: 1600 ELO, Opponent: 1400 ELO (Expected Score ~0.760)
        // Delta = 20 * (0 - 0.760) = -15.2 -> -15
        int delta = EloSystem.calculateEloDelta(
                1600,
                1400,
                50,
                false,
                false,
                0,
                3
        );
        assertEquals(-15, delta);
    }
}
