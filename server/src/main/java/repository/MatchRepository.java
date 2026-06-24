package repository;

import dto.request.FindMatchesRequest;
import dto.request.MatchFilter;
import dto.request.Pagination;
import dto.request.Sorting;
import dto.response.PageResponse;
import entity.Match;
import entity.MatchStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import utils.DbConnectionPool;
import utils.SqlQueryBuilder;

public class MatchRepository {
    private final DbConnectionPool dbConnectionPool;

    public MatchRepository(DbConnectionPool dbConnectionPool) {
        this.dbConnectionPool = dbConnectionPool;
    }

    public long create(int userId1, int userId2) {
        String sql = "INSERT INTO matches (player1_id, player2_id) VALUES (?, ?)";
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                preparedStatement.setInt(1, userId1);
                preparedStatement.setInt(2, userId2);
                preparedStatement.executeUpdate();

                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next())
                        return generatedKeys.getLong(1);
                    else
                        throw new SQLException("Creating match failed, no ID obtained.");
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error creating match!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public void save(Match match) {
        String sql = "UPDATE matches SET "
                + "player1_id = ?, "
                + "player2_id = ?, "
                + "player1_score = ?, "
                + "player2_score = ?, "
                + "winner_id = ?, "
                + "status = ?::match_status, "
                + "ended_at = CURRENT_TIMESTAMP "
                + "WHERE id = ?";

        Connection connection = null;
        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, match.getUser1Id());
                preparedStatement.setInt(2, match.getUser2Id());
                preparedStatement.setInt(3, match.getUser1Score());
                preparedStatement.setInt(4, match.getUser2Score());
                preparedStatement.setObject(5, match.getWinnerId(), Types.BIGINT);
                preparedStatement.setString(6, match.getStatus().name());
                preparedStatement.setLong(7, match.getId());

                int rowsAffected = preparedStatement.executeUpdate();

                if (rowsAffected == 0)
                    throw new IllegalArgumentException("The match with ID "
                            + match.getId()
                            + " was not found");
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error updating match!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public PageResponse<Match> findAll(FindMatchesRequest request) {
        MatchFilter filter = request != null ? request.filter() : null;
        Sorting sorting = request != null ? request.sorting() : null;
        Pagination pagination = request != null ? request.pagination() : null;

        SqlQueryBuilder dataBuilder = new SqlQueryBuilder("SELECT * FROM matches");
        SqlQueryBuilder countBuilder = new SqlQueryBuilder("SELECT COUNT(*) FROM matches");

        if (filter != null) {
            dataBuilder.whereAnyColumnEqual(filter.userId1(), "player1_id", "player2_id")
                    .whereAnyColumnEqual(filter.userId2(), "player1_id", "player2_id")
                    .whereEqual("status", filter.status())
                    .whereGreaterOrEqual("started_at", filter.startedAtFrom())
                    .whereLessOrEqual("started_at", filter.startedAtTo())
                    .whereGreaterOrEqual("ended_at", filter.finishedAtFrom())
                    .whereLessOrEqual("ended_at", filter.finishedAtTo());

            countBuilder.whereAnyColumnEqual(filter.userId1(), "player1_id", "player2_id")
                    .whereAnyColumnEqual(filter.userId2(), "player1_id", "player2_id")
                    .whereEqual("status", filter.status())
                    .whereGreaterOrEqual("started_at", filter.startedAtFrom())
                    .whereLessOrEqual("started_at", filter.startedAtTo())
                    .whereGreaterOrEqual("ended_at", filter.finishedAtFrom())
                    .whereLessOrEqual("ended_at", filter.finishedAtTo());
        }

        if (sorting != null && sorting.column() != null) {
            if (sorting.isAscending())
                dataBuilder.orderByAsc(sorting.column());
            else
                dataBuilder.orderByDesc(sorting.column());
        } else {
            dataBuilder.orderByAsc("id");
        }

        if (pagination != null)
            dataBuilder.paginate(pagination.page(), pagination.size());

        List<Match> matches = fetchMatchesWithBuilder(
                dataBuilder.getSql(),
                dataBuilder.getParams()
        );
        int totalElements = fetchCountWithBuilder(countBuilder.getSql(), countBuilder.getParams());

        int currentPage = (pagination != null && pagination.page() != null) ? pagination.page() : 1;
        int pageSize = (pagination != null && pagination.size() != null)
                ? pagination.size()
                : Math.max(totalElements, 1);
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;

        return new PageResponse<>(matches, totalElements, totalPages, currentPage);
    }

    private List<Match> fetchMatchesWithBuilder(String sql, List<Object> params) {
        List<Match> matches = new ArrayList<>();
        Connection connection = null;
        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                setParameters(preparedStatement, params);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Timestamp endedAtTs = resultSet.getTimestamp("ended_at");
                        Instant endedAt = (endedAtTs != null)
                                ? endedAtTs.toInstant()
                                : null;

                        matches.add(new Match(
                                resultSet.getLong("id"),
                                resultSet.getInt("player1_id"),
                                resultSet.getInt("player2_id"),
                                resultSet.getInt("player1_score"),
                                resultSet.getInt("player2_score"),
                                resultSet.getObject("winner_id") != null
                                        ? resultSet.getInt("winner_id")
                                        : null,
                                MatchStatus.valueOf(resultSet.getString("status")),
                                resultSet.getTimestamp("started_at").toInstant(),
                                endedAt
                        ));
                    }
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error searching matches", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }

        return matches;
    }

    private int fetchCountWithBuilder(String sql, List<Object> params) {
        Connection connection = null;
        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                setParameters(preparedStatement, params);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) return resultSet.getInt(1);
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error counting matches", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
        return 0;
    }

    private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++)
            stmt.setObject(i + 1, params.get(i));
    }
}
