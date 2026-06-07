import decryptor.MessageDecryptor;
import dto.Message;
import encryptor.MessageEncryptor;
import java.net.InetAddress;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        ClientTcp client = new ClientTcp(
                new MessageEncryptor(),
                new MessageDecryptor(),
                System.out::println
        );

        client.connect(InetAddress.getLoopbackAddress(), 10000);
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type ‘stop’ to safely shut down the tcp client.");

        boolean stop = false;
        while (!stop) {
            String command = scanner.nextLine();

            switch (command.trim()) {
                case "stop":
                    stop = true;
                    break;
                case "send":
                    client.sendCommand(new Message((byte) 0, 0L, 0, 0, "{}"));
                    break;
                default:
                    System.out.println("Invalid command.");
                    break;
            }

        }

        client.disconnect();

        System.out.println("The tcp client has been completed.");
    }
}
