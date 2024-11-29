import java.io.*;
import java.net.*;
import java.util.Scanner;

public class FileExchangeClient {
    private static Socket clientSocket;
    private static PrintWriter outStream;
    private static BufferedReader inStream;
    private static Scanner userScanner;

    public static void main(String[] args) {
        userScanner = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            String userInput = userScanner.nextLine();

            try {
                if (userInput.startsWith("/join")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 3) {
                        System.out.println("/join <server_ip> <port>");
                        continue;
                    }
                    String serverIp = params[1];
                    int serverPort = Integer.parseInt(params[2]);
                    connectToServer(serverIp, serverPort);
                } else if (userInput.equals("/?")) {
                    displayLocalHelp();
                } else if (!isConnected() && !userInput.equals("/?")) {
                    System.out.println("Not connected to a server. Use /join to connect.");
                    continue;
                } else if (userInput.equals("/leave")) {
                    disconnectFromServer();
                    break;
                } else if (userInput.startsWith("/register")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 2) {
                        System.out.println("/register <handle>");
                        continue;
                    }
                    String aliasName = params[1];
                    sendAlias(aliasName);
                } else if (userInput.startsWith("/store")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 2) {
                        System.out.println("/store <filename>");
                        continue;
                    }
                    String filePath = params[1];
                    uploadFile(filePath);
                } else if (userInput.equals("/dir")) {
                    requestFileList();
                } else if (userInput.startsWith("/get")) {
                    String[] params = userInput.split(" ");
                    if (params.length != 2) {
                        System.out.println("/get <filename>");
                        continue;
                    }
                    String fileName = params[1];
                    downloadFile(fileName);
                } else if (userInput.equals("/status")) {
                    checkSocketStatus();
                } else {
                    System.out.println("Unknown command. Type /? for help.");
                }
            } catch (IOException e) {
                System.out.println("Client error: " + e.getMessage());
                if (!isConnected()) {
                    System.out.println("Disconnected from server. Use /join to reconnect.");
                    clientSocket = null;
                }
            }
        }
        userScanner.close();
    }

    private static boolean isConnected() {
        return clientSocket != null && !clientSocket.isClosed() && clientSocket.isConnected();
    }

    private static void connectToServer(String serverIp, int serverPort) throws IOException {
        System.out.println("Attempting to connect to " + serverIp + ":" + serverPort);
        clientSocket = new Socket();
        clientSocket.connect(new InetSocketAddress(serverIp, serverPort), 10000); // 10 second timeout
        System.out.println("Socket connected");
        outStream = new PrintWriter(clientSocket.getOutputStream(), true);
        inStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        System.out.println("Streams established");
        String welcomeMessage = inStream.readLine();
        System.out.println("Server welcome message: " + welcomeMessage);
    }

    private static void disconnectFromServer() {
        if (isConnected()) {
            try {
                outStream.println("/leave");
                System.out.println(inStream.readLine()); // Disconnection message from server
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Error while disconnecting: " + e.getMessage());
            } finally {
                clientSocket = null;
                outStream = null;
                inStream = null;
            }
        } else {
            System.out.println("Already disconnected.");
        }
    }

    private static void sendAlias(String aliasName) {
        outStream.println("/register " + aliasName);
        try {
            System.out.println(inStream.readLine()); // Welcome message from server
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private static void uploadFile(String filePath) throws IOException {
        if (!isConnected()) {
            System.out.println("Not connected to server. Please connect first.");
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }
        
        System.out.println("Sending store command to server...");
        outStream.println("/store " + file.getName());
        outStream.flush();

        System.out.println("Waiting for server response...");
        String response = inStream.readLine();
        if (response == null) {
            System.out.println("Connection lost. Server did not respond.");
            disconnectFromServer();
            return;
        }

        System.out.println("Server response: " + response);
        if (!response.equals("READY_TO_RECEIVE")) {
            System.out.println("File transfer initiation failed. Server response: " + response);
            return;
        }

        System.out.println("Starting file transfer...");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                outStream.println(line);
            }
            outStream.println("END_OF_FILE");
            outStream.flush();
        }
        System.out.println("File transfer complete.");

        System.out.println("Waiting for upload confirmation...");
        response = inStream.readLine();
        if (response == null) {
            System.out.println("Connection lost. Server did not confirm upload.");
            disconnectFromServer();
        } else {
            System.out.println("Server confirmation: " + response);
        }
    }

    private static void requestFileList() {
        outStream.println("/dir");
        try {
            String response;
            while (!(response = inStream.readLine()).equals("END_OF_LIST")) {
                System.out.println(response);
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        }
    }

    private static void downloadFile(String fileName) throws IOException {
        outStream.println("/get " + fileName);

        String response = inStream.readLine();
        if (!response.equals("READY_TO_SEND")) {
            System.out.println(response);
            return;
        }

        System.out.println("Receiving file contents:");
        System.out.println("------------------------");

        StringBuilder fileContents = new StringBuilder();
        String line;
        while (!(line = inStream.readLine()).equals("END_OF_FILE")) {
            System.out.println(line);
            fileContents.append(line).append("\n");
        }

        System.out.println("------------------------");

        try (FileWriter fw = new FileWriter(new File(fileName))) {
            fw.write(fileContents.toString());
        }

        System.out.println("File download complete. Contents saved to " + fileName);
        System.out.println(inStream.readLine()); 
    }

    private static void displayLocalHelp() {
        System.out.println("Available commands:");
        System.out.println("/join <server_ip> <port> - Connect to a server");
        System.out.println("/leave - Disconnect from the server");
        System.out.println("/register <username> - Set your username on the server");
        System.out.println("/store <filename> - Upload a file to the server");
        System.out.println("/dir - List files on the server");
        System.out.println("/get <filename> - Download a file from the server");
        System.out.println("/? - Display this help message");
        System.out.println("/status - Check connection status");
        
        if (!isConnected()) {
            System.out.println("\nNot connected to a server. Use /join to connect.");
        }
    }

    private static void checkSocketStatus() {
        if (clientSocket != null) {
            System.out.println("Socket status:");
            System.out.println("  Is connected: " + clientSocket.isConnected());
            System.out.println("  Is closed: " + clientSocket.isClosed());
            System.out.println("  Input shutdown: " + clientSocket.isInputShutdown());
            System.out.println("  Output shutdown: " + clientSocket.isOutputShutdown());
        } else {
            System.out.println("Socket is null");
        }
    }
}
