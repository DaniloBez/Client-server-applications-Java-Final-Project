package repository;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import utils.DbConnectionPool;

@Testcontainers
public abstract class BaseRepositoryTest {
    @Container
    protected static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    protected DbConnectionPool dbConnectionPool;

    @BeforeAll
    static void initSchema() {
        postgres.start();

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load();
        flyway.migrate();
    }

    @BeforeEach
    void setUpPoolAndCleanDb() throws Exception {
        dbConnectionPool = new DbConnectionPool(
                5,
                postgres.getJdbcUrl() + "&stringtype=unspecified",
                postgres.getUsername(),
                postgres.getPassword()
        );

        try (Connection conn = dbConnectionPool.getConnection();
                Statement stmt = conn.createStatement()
        ) {
            stmt.execute("TRUNCATE TABLE matches, users RESTART IDENTITY CASCADE;");
        }
    }

    @AfterAll
    static void tearDown() {
        postgres.stop();
    }
}
