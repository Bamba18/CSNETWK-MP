import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * FileExchangeClient is a command-line client application for interacting with a file server.
 * It supports connecting to a server, registering an alias, uploading files, downloading files,
 * viewing server file listings, and checking connection status.
 * <p>
 * Usage:
 * <ul>
 * <li>Type /? for a list of available commands.</li>
 * <li>Use /join to connect to a server.</li>
 * </ul>
 */
public class FileExchangeClient {
    private static Socket clientSocket;
    private static PrintWriter output;
    private static BufferedReader input;

    /**
     * Main method that starts the File Exchange Client.
     * Continuously listens for user input and executes corresponding commands.
     *
     * @param args Command-line arguments (not used).
     */
    public static void main(String[] args) {
        System.out.println("Welcome to the File Exchange Client!");
        System.out.println("Type /? to see a list of available commands.");

        Scanner userScanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            String userInput = userScanner.nextLine();

            try {
                if (userInput.startsWith("/join")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 3) {
                        System.out.println("Please use the correct format: /join <server_ip> <port>");
                        continue;
                    }
                    String serverIp = params[1];
                    int portNumber = Integer.parseInt(params[2]);
                    joinServer(serverIp, portNumber);
                } else if (userInput.equals("/?")) {
                    showHelpMenu();
                } else if (!isServerConnected()) {
                    System.out.println("You're not connected to a server. Use /join to start a connection.");
                } else if (userInput.equals("/leave")) {
                    leaveServer();
                    break;
                } else if (userInput.startsWith("/register")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 2) {
                        System.out.println("Please use the correct format: /register <handle>");
                        continue;
                    }
                    String clientAlias = params[1];
                    registerAlias(clientAlias);
                } else if (userInput.startsWith("/store")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 2) {
                        System.out.println("Please use the correct format: /store <filename>");
                        continue;
                    }
                    String fileName = params[1];
                    uploadFile(fileName);
                } else if (userInput.equals("/dir")) {
                    requestFileList();
                } else if (userInput.startsWith("/get")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 2) {
                        System.out.println("Please use the correct format: /get <filename>");
                        continue;
                    }
                    String fileName = params[1];
                    downloadFile(fileName);
                } else if (userInput.equals("/status")) {
                    checkConnectionStatus();
                } else {
                    System.out.println("Command not recognized. Type /? for help.");
                }
            } catch (IOException e) {
                System.out.println("An error occurred: " + e.getMessage());
                if (!isServerConnected()) {
                    System.out.println("Connection lost. Use /join to reconnect.");
                    clientSocket = null;
                }
            }
        }
        userScanner.close();
    }

    /**
     * Checks if the client is currently connected to a server.
     *
     * @return {@code true} if the client is connected; {@code false} otherwise.
     */
    private static boolean isServerConnected() {
        return clientSocket != null && !clientSocket.isClosed() && clientSocket.isConnected();
    }


    /**
     * Establishes a connection to the specified server using the given IP address and port number.
     *
     * @param serverIp The IP address of the server.
     * @param portNumber The port number of the server.
     * @throws IOException If an error occurs while connecting to the server.
     */
    private static void joinServer(String serverIp, int portNumber) throws IOException {
        System.out.println("Connecting to server at " + serverIp + ":" + portNumber + "...");
        clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(serverIp, portNumber), 10000); // 10-second timeout
        System.out.println("Connection established successfully!");
        output = new PrintWriter(clientSocket.getOutputStream(), true);
        input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String welcomeMessage = input.readLine();
        System.out.println("Server says: " + welcomeMessage);
    }

    /**
     * Disconnects the client from the server.
     * Sends a leave command to the server and closes the connection.
     */
    private static void leaveServer() {
        if (isServerConnected()) {
            try {
                output.println("/leave");
                System.out.println("Disconnecting from server...");
                System.out.println(input.readLine()); // Disconnection message from server
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error while disconnecting: " + e.getMessage());
            } finally {
                clientSocket = null;
                output = null;
                input = null;
                System.out.println("You have been disconnected.");
            }
        } else {
            System.out.println("You're already disconnected.");
        }
    }

    /**
     * Registers a client alias with the server.
     *
     * @param clientAlias The alias to register.
     */
    private static void registerAlias(String clientAlias) {
        output.println("/register " + clientAlias);
        try {
            System.out.println(input.readLine()); // Welcome message from server
        } catch (IOException e) {
            System.out.println("Failed to register alias: " + e.getMessage());
        }
    }

    /**
     * Uploads a file to the server.
     * Sends the file contents to the server after initiating the transfer.
     *
     * @param fileName The name of the file to upload.
     * @throws IOException If an error occurs during the upload process.
     */
    private static void uploadFile(String fileName) throws IOException {
        if (!isServerConnected()) {
            System.out.println("Please connect to the server before uploading a file.");
            return;
        }

        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("The file \"" + fileName + "\" was not found.");
            return;
        }

        System.out.println("Preparing to upload \"" + fileName + "\"...");
        output.println("/store " + file.getName());
        output.flush();

        String response = input.readLine();
        if (response == null) {
            System.out.println("No response from the server. Upload aborted.");
            leaveServer();
            return;
        }

        if (!response.equals("READY_TO_RECEIVE")) {
            System.out.println("Server could not initiate upload. Response: " + response);
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.println(line);
            }
            output.println("END_OF_FILE");
        }
        System.out.println("Upload of \"" + fileName + "\" completed!");

        response = input.readLine();
        System.out.println("Server confirmation: " + response);
    }

    /**
     * Requests a list of available files on the server.
     * Outputs the file list to the console.
     */
    private static void requestFileList() {
        output.println("/dir");
        try {
            System.out.println("Fetching file list from the server...");
            String response;
            while (!(response = input.readLine()).equals("END_OF_LIST")) {
                System.out.println(response);
            }
        } catch (IOException e) {
            System.out.println("Could not fetch file list: " + e.getMessage());
        }
    }

    /**
     * Downloads a specified file from the server.
     * Saves the file locally after receiving its contents.
     *
     * @param fileName The name of the file to download.
     * @throws IOException If an error occurs during the download process.
     */
    private static void downloadFile(String fileName) throws IOException {
        output.println("/get " + fileName);

        String response = input.readLine();
        if (!response.equals("READY_TO_SEND")) {
            System.out.println("Server response: " + response);
            return;
        }

        System.out.println("Downloading \"" + fileName + "\"...");
        StringBuilder fileContents = new StringBuilder();
        String line;
        while (!(line = input.readLine()).equals("END_OF_FILE")) {
            fileContents.append(line).append("\n");
        }

        try (FileWriter fw = new FileWriter(fileName)) {
            fw.write(fileContents.toString());
        }
        System.out.println("Download complete. File saved as \"" + fileName + "\".");
    }


    /**
     * Displays a list of available commands and their descriptions.
     * Provides guidance for using the application.
     */
    private static void showHelpMenu() {
        System.out.println("Available commands:");
        System.out.println("/join <server_ip> <port> - Connect to a server");
        System.out.println("/leave - Disconnect from the server");
        System.out.println("/register <handle> - Register your alias with the server");
        System.out.println("/store <filename> - Upload a file to the server");
        System.out.println("/dir - View files available on the server");
        System.out.println("/get <filename> - Download a file from the server");
        System.out.println("/? - Display this help message");
        System.out.println("/status - Check connection status");

        if (!isServerConnected()) {
            System.out.println("\nNote: You are not connected to a server.");
        }
    }

    /**
     * Checks and displays the status of the client's socket connection.
     * Includes details about whether the connection is open, closed, or shut down.
     */
    private static void checkConnectionStatus() {
        if (clientSocket != null) {
            System.out.println("Connection Status:");
            System.out.println("  Connected: " + clientSocket.isConnected());
            System.out.println("  Closed: " + clientSocket.isClosed());
        } else {
            System.out.println("You are not currently connected to a server.");
        }
    }
}
