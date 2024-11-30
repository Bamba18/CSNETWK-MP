import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A server program for exchanging files between clients.
 * The server accepts multiple clients, allowing them to upload, download, and list files.
 * <p>
 * Commands supported:
 * - /register <alias>: Register an alias for the client.
 * - /store <filename>: Upload a file to the server.
 * - /dir: List all files available on the server.
 * - /get <filename>: Download a specific file.
 * - /leave: Disconnect from the server.
 * - /?: Display help information.
 */
public class FileExchangeServer {
    private static final int PORT_NUMBER = 12345; // The port number the server listens on
    private static final String FILE_STORAGE_DIR = "server_storage/"; // Directory for storing files
    private static final String SERVER_IP = "127.0.0.1"; // Server IP address (localhost for testing)

    public static void main(String[] args) {
        // Ensure the storage directory exists
        File storageDir = new File(FILE_STORAGE_DIR);
        if (!storageDir.exists() && !storageDir.mkdir()) {
            System.err.println("Failed to create storage directory: " + FILE_STORAGE_DIR);
            System.exit(1); // Terminate the program if directory creation fails
        }

        try (ServerSocket serverSocket = new ServerSocket()) {
            // Bind the server socket to the specified IP and port
            serverSocket.bind(new InetSocketAddress(SERVER_IP, PORT_NUMBER));
            System.out.println("Server IP: " + SERVER_IP + " Port Number: " + PORT_NUMBER);

            // Intentionally using an infinite loop to keep accepting client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("A new client has connected.");

                // Start a new thread to handle the connected client
                new ClientHandler(clientSocket, FILE_STORAGE_DIR).start();
            }
        } catch (IOException ex) {
            System.out.println("Server encountered an error: " + ex.getMessage());
        }
    }
}

/**
 * Handles communication with a connected client.
 * Each client is managed in its own thread to support concurrent connections.
 */
class ClientHandler extends Thread {
    private final Socket clientSocket; // The client socket connection
    private final String storageDirectory; // Directory for storing and retrieving files
    private String clientAlias; // Alias of the connected client
    private PrintWriter output; // Output stream for sending messages to the client
    private BufferedReader input; // Input stream for receiving messages from the client

    /**
     * Constructor for the client handler.
     *
     * @param clientSocket      The socket connection to the client.
     * @param storageDirectory  The directory where files are stored.
     */
    public ClientHandler(Socket clientSocket, String storageDirectory) {
        this.clientSocket = clientSocket;
        this.storageDirectory = storageDirectory;
    }

    /**
     * Main logic for handling client communication.
     * Reads commands from the client and processes them accordingly.
     */
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);

            output.println("Successfully connected to the File Exchange Server!");

            String clientMessage;
            // Continuously listen for client commands until the connection is closed
            while ((clientMessage = input.readLine()) != null) {
                processClientMessage(clientMessage);
            }

            clientSocket.close(); // Close the connection when done
            System.out.println(clientAlias + " has disconnected.");
        } catch (IOException ex) {
            System.out.println("Client handler error: " + ex.getMessage());
        }
    }

    /**
     * Processes commands received from the client.
     *
     * @param message The command sent by the client.
     * @throws IOException If an error occurs while processing the command.
     */
    private void processClientMessage(String message) throws IOException {
        if (message.startsWith("/register")) {
            registerClientAlias(message);
        } else if (message.startsWith("/store")) {
            prepareReceiveFile(message);
        } else if (message.equals("/dir")) {
            sendFileList();
        } else if (message.startsWith("/get")) {
            prepareSendFile(message);
        } else if (message.equals("/leave")) {
            output.println("Connection terminated. Goodbye!");
        } else if (message.equals("/?")) {
            displayHelp();
        } else {
            output.println("Unknown command. Type /? for help.");
        }
    }

    /**
     * Registers a client alias.
     *
     * @param message The /register command containing the alias.
     */
    private void registerClientAlias(String message) {
        clientAlias = message.split(" ")[1]; // Extract the alias
        output.println("Welcome " + clientAlias + "!");
    }

    /**
     * Prepares to receive a file from the client.
     *
     * @param message The /store command containing the filename.
     * @throws IOException If an error occurs during file reception.
     */
    private void prepareReceiveFile(String message) throws IOException {
        String fileName = message.split(" ")[1]; // Extract the filename
        output.println("READY_TO_RECEIVE");
        receiveFile(fileName);
    }

    /**
     * Receives a file from the client and saves it to the storage directory.
     *
     * @param fileName The name of the file to save.
     * @throws IOException If an error occurs during file reception.
     */
    private void receiveFile(String fileName) throws IOException {
        File file = new File(storageDirectory + fileName);
        try (FileWriter fw = new FileWriter(file)) {
            String line;
            while (!(line = input.readLine()).equals("END_OF_FILE")) {
                fw.write(line + "\n");
            }
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        output.println(clientAlias + " uploaded " + fileName + " at " + timeStamp);
        System.out.println(clientAlias + " uploaded " + fileName + " at " + timeStamp);
    }

    /**
     * Sends a list of files available in the storage directory to the client.
     */
    private void sendFileList() {
        File dir = new File(storageDirectory);
        String[] files = dir.list();

        if (files != null && files.length > 0) {
            for (String file : files) {
                output.println(file);
            }
        } else {
            output.println("No files available.");
        }
        output.println("END_OF_LIST");
    }

    /**
     * Prepares to send a file to the client.
     *
     * @param message The /get command containing the filename.
     * @throws IOException If an error occurs while preparing to send the file.
     */
    private void prepareSendFile(String message) throws IOException {
        String fileName = message.split(" ")[1]; // Extract the filename
        File file = new File(storageDirectory + fileName);
        if (!file.exists()) {
            output.println("This file is not found: " + fileName);
            return;
        }
        output.println("READY_TO_SEND");
        sendFile(file);
    }

    /**
     * Sends a file to the client.
     *
     * @param file The file to send.
     * @throws IOException If an error occurs during file transmission.
     */
    private void sendFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.println(line);
            }
            output.println("END_OF_FILE");
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        output.println("File " + file.getName() + " sent to " + clientAlias + " at " + timeStamp);
        System.out.println("File " + file.getName() + " sent to " + clientAlias + " at " + timeStamp);
    }

    /**
     * Displays a list of available commands to the client.
     */
    private void displayHelp() {
        output.println("Available commands:");
        output.println("/join <server_ip> <port>");
        output.println("/leave");
        output.println("/register <handle>");
        output.println("/store <filename>");
        output.println("/dir");
        output.println("/get <filename>");
        output.println("/?");
        output.println("END_OF_HELP");
    }
}
