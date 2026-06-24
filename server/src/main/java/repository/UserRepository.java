package repository;

import dto.request.FindUsersRequest;
import dto.request.Pagination;
import dto.request.Sorting;
import dto.request.UserFilter;
import dto.response.LeaderboardEntry;
import dto.response.PageResponse;
import entity.User;
import entity.UserRole;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import utils.DbConnectionPool;
import utils.SqlQueryBuilder;

public class UserRepository {
    private final DbConnectionPool dbConnectionPool;

    public UserRepository(DbConnectionPool dbConnectionPool) {
        this.dbConnectionPool = dbConnectionPool;
    }

    public Optional<User> getUser(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, id);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next())
                        return Optional.of(parse(resultSet));
                    else
                        return Optional.empty();
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error finding user!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public int create(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, passwordHash);
                preparedStatement.executeUpdate();

                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next())
                        return generatedKeys.getInt(1);
                    else
                        throw new SQLException("Creating user failed, no ID obtained.");
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error creating user!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public int createAdmin(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password_hash, role) "
                + "VALUES (?, ?, 'ADMIN'::user_role)";
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                preparedStatement.setString(1, username);
                preparedStatement.setString(2, passwordHash);
                preparedStatement.executeUpdate();

                try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                    if (generatedKeys.next())
                        return generatedKeys.getInt(1);
                    else
                        throw new SQLException("Creating admin failed, no ID obtained.");
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error creating admin!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, username);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next())
                        return Optional.of(parse(resultSet));
                    else
                        return Optional.empty();
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error finding user!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public void updateEloAndMatchCount(int userId, int eloDelta) {
        String sql = "UPDATE users SET "
                + "match_count = match_count + 1, "
                + "elo_rating = elo_rating + ? "
                + "WHERE id = ?";

        Connection connection = null;
        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, eloDelta);
                preparedStatement.setInt(2, userId);

                int rowsAffected = preparedStatement.executeUpdate();

                if (rowsAffected == 0)
                    throw new IllegalArgumentException("The user with ID "
                            + userId
                            + " was not found");
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error updating user!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    public List<LeaderboardEntry> getTopPlayers(int limit) {
        String sql = "SELECT username, elo_rating, match_count "
                + "FROM users "
                + "ORDER BY elo_rating DESC, match_count DESC LIMIT ?";
        List<LeaderboardEntry> result = new ArrayList<>();
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, limit);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    int rank = 1;
                    while (resultSet.next()) {
                        result.add(new LeaderboardEntry(
                                rank++,
                                resultSet.getString("username"),
                                resultSet.getInt("elo_rating"),
                                resultSet.getInt("match_count")
                        ));
                    }
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error getting top players!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
        return result;
    }

    public PageResponse<User> findAll(FindUsersRequest request) {
        UserFilter filter = request != null ? request.filter() : null;
        Sorting sorting = request != null ? request.sorting() : null;
        Pagination pagination = request != null ? request.pagination() : null;

        SqlQueryBuilder dataBuilder = new SqlQueryBuilder("SELECT * FROM users");
        SqlQueryBuilder countBuilder = new SqlQueryBuilder("SELECT COUNT(*) FROM users");

        if (filter != null) {
            dataBuilder.whereIlike("username", filter.nameLike())
                    .whereEqual("role", filter.userRole())
                    .whereEqual("is_banned", filter.isBanned())
                    .whereGreaterOrEqual("elo_rating", filter.minElo())
                    .whereLessOrEqual("elo_rating", filter.maxElo())
                    .whereGreaterOrEqual("match_count", filter.minMatchCount())
                    .whereLessOrEqual("match_count", filter.maxMatchCount())
                    .whereGreaterOrEqual("created_at", filter.createdAtFrom())
                    .whereLessOrEqual("created_at", filter.createdAtTo());

            countBuilder.whereIlike("username", filter.nameLike())
                    .whereEqual("role", filter.userRole())
                    .whereEqual("is_banned", filter.isBanned())
                    .whereGreaterOrEqual("elo_rating", filter.minElo())
                    .whereLessOrEqual("elo_rating", filter.maxElo())
                    .whereGreaterOrEqual("match_count", filter.minMatchCount())
                    .whereLessOrEqual("match_count", filter.maxMatchCount())
                    .whereGreaterOrEqual("created_at", filter.createdAtFrom())
                    .whereLessOrEqual("created_at", filter.createdAtTo());
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

        List<User> users = fetchUsersWithBuilder(dataBuilder.getSql(), dataBuilder.getParams());
        int totalElements = fetchCountWithBuilder(countBuilder.getSql(), countBuilder.getParams());

        int currentPage = (pagination != null && pagination.page() != null) ? pagination.page() : 1;
        int pageSize = (pagination != null && pagination.size() != null)
                ? pagination.size()
                : Math.max(totalElements, 1);
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;

        return new PageResponse<>(users, totalElements, totalPages, currentPage);
    }

    public void setBannedStatus(long userId, boolean status) {
        String sql = "UPDATE users SET is_banned = ? WHERE id = ?";
        Connection connection = null;

        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setBoolean(1, status);
                preparedStatement.setLong(2, userId);

                int rowsAffected = preparedStatement.executeUpdate();

                if (rowsAffected == 0)
                    throw new IllegalArgumentException("The user with ID "
                            + userId
                            + " was not found");
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error updating user!", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
    }

    private List<User> fetchUsersWithBuilder(String sql, List<Object> params) {
        List<User> users = new ArrayList<>();
        Connection connection = null;
        try {
            connection = dbConnectionPool.getConnection();
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                setParameters(preparedStatement, params);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        users.add(parse(resultSet));
                    }
                }
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException("Error searching users", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
        return users;
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
            throw new RuntimeException("Error counting users", e);
        } finally {
            dbConnectionPool.releaseConnection(connection);
        }
        return 0;
    }

    private User parse(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getInt("id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                UserRole.valueOf(resultSet.getString("role")),
                resultSet.getInt("match_count"),
                resultSet.getInt("elo_rating"),
                resultSet.getBoolean("is_banned"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    private void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++)
            stmt.setObject(i + 1, params.get(i));
    }
}
