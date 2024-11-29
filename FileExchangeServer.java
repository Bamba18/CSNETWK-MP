import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class FileExchangeServer {
    private static final int PORT_NUMBER = 12345;
    private static final String FILE_STORAGE_DIR = "server_storage/";
    private static final String SERVER_IP = "127.0.0.1"; // Localhost for testing

    public static void main(String[] args) {
        new File(FILE_STORAGE_DIR).mkdir(); // Create directory if it doesn't exist

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(SERVER_IP, PORT_NUMBER));
            System.out.println("Server IP: " + SERVER_IP + " Port Number: " + PORT_NUMBER);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("A new client has connected.");

                new ClientHandler(clientSocket, FILE_STORAGE_DIR).start();
            }
        } catch (IOException ex) {
            System.out.println("Server encountered an error: " + ex.getMessage());
        }
    }
}

class ClientHandler extends Thread {
    private Socket clientSocket;
    private String storageDirectory;
    private String clientAlias;
    private PrintWriter output;
    private BufferedReader input;

    public ClientHandler(Socket clientSocket, String storageDirectory) {
        this.clientSocket = clientSocket;
        this.storageDirectory = storageDirectory;
    }

    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);

            output.println("Successfully connected to the File Exchange Server!");

            String clientMessage;
            while ((clientMessage = input.readLine()) != null) {
                processClientMessage(clientMessage);
            }

            clientSocket.close();
            System.out.println(clientAlias + " has disconnected.");
        } catch (IOException ex) {
            System.out.println("Client handler error: " + ex.getMessage());
        }
    }

    private void processClientMessage(String message) throws IOException {
        if (message.startsWith("/register")) {
            registerClientAlias(message);
        } else if (message.startsWith("/store")) {
            prepareRecieveFile(message);
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

    private void registerClientAlias(String message) {
        clientAlias = message.split(" ")[1];
        output.println("Welcome " + clientAlias + "!");
    }

    private void prepareRecieveFile(String message) throws IOException {
        String fileName = message.split(" ")[1];
        output.println("READY_TO_RECEIVE");
        receiveFile(fileName);
    }

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

    private void prepareSendFile(String message) throws IOException {
        String fileName = message.split(" ")[1];
        File file = new File(storageDirectory + fileName);
        if (!file.exists()) {
            output.println("This file is not found: " + fileName);
            return;
        }
        output.println("READY_TO_SEND");
        sendFile(file);
    }

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

