import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;

/**
 * A simple class to create a standard Java Socket,
 * connect to a local port and check the default
 * SO_SNDBUF and SO_RCVBUF buffer sizes assigned
 * by the OS.
 */
public class CheckDefaultBuffers {

    private static final String HOST = "localhost";
    private static final int PORT = 12345; // Our Spring server port

    /**
     * The main method that runs the check.
     */
    public static void main(String[] args) {

        System.out.println("--- Checking Default TCP Buffers ---");
        System.out.println("   (Make sure the Spring server is running on " + HOST + ":" + PORT + ")");

        // Use try-with-resources to ensure the channel is closed.
        try (SocketChannel channel = SocketChannel.open()) {

            // Connect to the Spring server.
            // --- CORRECTED LINE! ---
            System.out.println("Connecting to " + HOST + ":" + PORT + "...");
            // --- END CORRECTED LINE ---
            channel.connect(new InetSocketAddress(HOST, PORT));

            // If we get here, we are connected.
            System.out.println("Successfully connected!");

            // Now ask the socket (via the channel) for its options.
            int sendBuffer = channel.getOption(StandardSocketOptions.SO_SNDBUF);
            int receiveBuffer = channel.getOption(StandardSocketOptions.SO_RCVBUF);

            // Print the results.
            System.out.println("---------------------------------------------");
            System.out.println("  TCP Buffer Values (Standard Socket):");
            System.out.println(
                    "  -> Send Buffer   (SO_SNDBUF): " + sendBuffer + " bytes (" + (sendBuffer / 1024) + " KB)");
            System.out.println("  -> Receive Buffer (SO_RCVBUF): " + receiveBuffer + " bytes ("
                    + (receiveBuffer / 1024) + " KB)");
            System.out.println("---------------------------------------------");

        } catch (Exception e) {
            // Handle possible errors (e.g., server not running).
            System.err.println("❌ Error checking buffers: " + e.getMessage());
        }
        System.out.println("--- End of Check ---");
    }
}