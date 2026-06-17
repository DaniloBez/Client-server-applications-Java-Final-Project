package repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dto.request.FindMatchesRequest;
import dto.request.MatchFilter;
import dto.request.Sorting;
import dto.response.PageResponse;
import entity.Match;
import entity.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchRepositoryTest extends BaseRepositoryTest {

    private MatchRepository matchRepository;
    private UserRepository userRepository;

    private int player1Id;
    private int player2Id;

    @BeforeEach
    void setUp() {
        matchRepository = new MatchRepository(dbConnectionPool);
        userRepository = new UserRepository(dbConnectionPool);

        player1Id = userRepository.create("player1", "pass");
        player2Id = userRepository.create("player2", "pass");
    }

    @Test
    void shouldCreateMatchAndReturnId() {
        long matchId = matchRepository.create(player1Id, player2Id);

        assertTrue(matchId > 0);
    }

    @Test
    void shouldSaveMatchResult() {
        long matchId = matchRepository.create(player1Id, player2Id);

        Match matchToUpdate = new Match(
                matchId,
                player1Id,
                player2Id,
                3,
                1,
                player1Id,
                MatchStatus.COMPLETED
        );

        matchRepository.save(matchToUpdate);

        PageResponse<Match> response = matchRepository.findAll(null);
        Match savedMatch = response.items().getFirst();

        assertEquals(3, savedMatch.getUser1Score());
        assertEquals(1, savedMatch.getUser2Score());
        assertEquals(MatchStatus.COMPLETED, savedMatch.getStatus());
        assertEquals(player1Id, savedMatch.getWinnerId());
        assertNotNull(savedMatch.getFinishedAt());
    }

    @Test
    void shouldFindAllMatchesByUserIdOrStatus() {
        int anotherPlayerId = userRepository.create("another", "pass");

        long match1Id = matchRepository.create(player1Id, player2Id);
        matchRepository.create(player1Id, anotherPlayerId);

        Match m1 = new Match(
                match1Id,
                player1Id,
                player2Id,
                0,
                0, player1Id,
                MatchStatus.TECHNICAL_WIN
        );
        matchRepository.save(m1);

        MatchFilter filterO = new MatchFilter(player2Id, null, null, null, null, null, null);
        FindMatchesRequest req1 = new FindMatchesRequest(filterO, null, null);
        PageResponse<Match> res1 = matchRepository.findAll(req1);

        assertEquals(1, res1.totalElements());
        assertEquals(match1Id, res1.items().getFirst().getId());

        MatchFilter filterX = new MatchFilter(player1Id, null, null, null, null, null, null);
        FindMatchesRequest req2 = new FindMatchesRequest(filterX, new Sorting("id", true), null);
        PageResponse<Match> res2 = matchRepository.findAll(req2);

        assertEquals(2, res2.totalElements());

        MatchFilter filterStatus = new MatchFilter(
                null,
                null,
                MatchStatus.TECHNICAL_WIN.name(),
                null,
                null,
                null,
                null
        );
        FindMatchesRequest req3 = new FindMatchesRequest(filterStatus, null, null);
        PageResponse<Match> res3 = matchRepository.findAll(req3);

        assertEquals(1, res3.totalElements());
        assertEquals(MatchStatus.TECHNICAL_WIN, res3.items().getFirst().getStatus());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentMatch() {
        Match fakeMatch = new Match(
                9999L, player1Id, player2Id, 0, 0, null, MatchStatus.COMPLETED
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> matchRepository.save(fakeMatch)
        );

        assertTrue(exception.getMessage().contains("was not found"));
    }

    @Test
    void shouldFilterMatchesByStatus() {
        long match1Id = matchRepository.create(player1Id, player2Id);
        matchRepository.create(player1Id, player2Id);

        Match m1 = new Match(
                match1Id, player1Id, player2Id, 3, 1, player1Id, MatchStatus.COMPLETED
        );
        matchRepository.save(m1);

        MatchFilter filter = new MatchFilter(
                null, null, MatchStatus.COMPLETED.name(), null, null, null, null
        );
        FindMatchesRequest request = new FindMatchesRequest(filter, null, null);
        PageResponse<Match> response = matchRepository.findAll(request);

        assertEquals(1, response.totalElements());
        assertEquals(MatchStatus.COMPLETED, response.items().getFirst().getStatus());
        assertEquals(match1Id, response.items().getFirst().getId());
    }
}