package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DbConnectionPool {
    private final TransferQueue<Connection> pool;

    public DbConnectionPool(int poolSize, String dbUrl, String username, String password) {
        pool = new LinkedTransferQueue<>();

        try {
            for (int i = 0; i < poolSize; i++)
                pool.add(DriverManager.getConnection(dbUrl, username, password));

            log.info("Connection pool initialized with {} connections.", poolSize);
        } catch (SQLException e) {
            log.error("Failed to initialize connection pool", e);
        }
    }

    public Connection getConnection() throws InterruptedException {
        return pool.take();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void releaseConnection(Connection connection) {
        if (connection != null)
            pool.offer(connection);
    }

    public void closeAll() {
        for (Connection conn : pool) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.warn("Connection closed", e);
            }
        }
    }
}
