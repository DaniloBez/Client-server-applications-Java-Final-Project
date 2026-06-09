import decryptor.Decryptor;
import decryptor.MessageDecryptor;
import encryptor.MessageEncryptor;
import java.util.Scanner;
import org.flywaydb.core.Flyway;
import processor.Processor;
import server.Server;
import utils.DbConnectionPool;

public class Main {
    private static Server currentServer = null;
    private static boolean isRunning = false;

    private static final String url = System.getenv("DB_URL");
    private static final  String user = System.getenv("DB_USER");
    private static final String password = System.getenv("DB_PASSWORD");

    public static void main(String[] args) {
        migrate();
        autoStartServer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[ShutdownHook] Initiating graceful shutdown...");
            handleExit();
        }));

        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Type 'exit' to stop the server.");

            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim().toLowerCase();
                if ("exit".equals(input)) {
                    System.out.println("Exit command received. Stopping...");
                    System.exit(0);
                } else {
                    System.out.println("Unknown command. Type 'exit' to stop.");
                }
            }
        } catch (Exception e) {
            System.out.println("No interactive console detected. Running in daemon mode.");
        }

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .load();

        flyway.migrate();

        System.out.println("Flyway has been migrated.");
    }

    private static void autoStartServer() {
        System.out.println("Automatically booting up the initial server instance...");
        bootNewServerInstance();
    }

    private static void handleExit() {
        if (isRunning && currentServer != null) {
            System.out.println("Stopping the active server instance before exit...");
            shutdownActiveServer();
        }
    }

    private static void bootNewServerInstance() {
        currentServer = initServer();
        currentServer.start();
        isRunning = true;
    }

    private static void shutdownActiveServer() {
        System.out.println("Initiating graceful shutdown...");
        currentServer.stop();
        currentServer = null;
        isRunning = false;
    }

    private static Server initServer() {
        DbConnectionPool dbConnectionPool = new DbConnectionPool(
                10,
                url + "&stringtype=unspecified",
                user,
                password
        );

        Decryptor serverDecryptor = new MessageDecryptor();
        MessageEncryptor serverEncryptor = new MessageEncryptor();
        Processor serverProcessor = new Processor();

        return new Server(
                5,
                serverDecryptor,
                2,
                serverEncryptor,
                3,
                serverProcessor,
                4,
                10000
        );
    }
}