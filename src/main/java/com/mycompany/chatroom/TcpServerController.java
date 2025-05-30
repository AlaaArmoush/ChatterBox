package com.mycompany.chatroom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class TcpServerController implements Initializable {

    @FXML
    private TextField serverIpTextField;
    @FXML
    private TextField portField;
    @FXML
    private ListView<String> connectedUsersListView;
    @FXML
    private Button startButton, listenButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label connectedCountLabel;

    public TCPServer tcpServer;
    private Timer refreshTimer;

    public TCPServer getTcpServer() {
        return tcpServer;
    }


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        try {
            String serverIp = InetAddress.getLocalHost().getHostAddress();
            serverIpTextField.setText(serverIp);
        } catch (UnknownHostException e) {
            serverIpTextField.setText("127.0.0.1");
        }

    }

    public void initializeTCPServer(String serverIp, int serverPort) {
        try {

            String ip = serverIp;
            int port = serverPort;
            InetAddress tcpIp = InetAddress.getLocalHost();

            tcpServer = new TCPServer(tcpIp, port);
            tcpServer.start();

            System.out.println("TCP Server started on " + ip + ":" + port);
            System.out.println("tcpServer reference: " + tcpServer);

            startClientListRefresher();
            refreshClientsList();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startClientListRefresher() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }

        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> refreshClientsList());
            }
        }, 0, 2000); // Check every 2 seconds
    }


    private void refreshClientsList() {
        if (tcpServer == null) return;

        List<String> activeConnections = new ArrayList<>();
        List<String> clientsInfo = tcpServer.getClientsInfo();

        connectedUsersListView.getItems().clear();

        if (clientsInfo != null) {
            for (String info : clientsInfo) {
                if (info.startsWith("DISCONNECT|")) {
                    continue;
                }

                // Add connect events
                if (info.startsWith("CONNECT")) {
                    connectedUsersListView.getItems().add(info);
                    activeConnections.add(info);
                }
            }
        }

        tcpServer.backToClient();
        updateConnectedCount();
    }

    private void updateConnectedCount() {
        int usersCount = connectedUsersListView.getItems().size();
        connectedCountLabel.setText("Connected users: " + usersCount);
    }

    ArrayList<String> connectedClientsInfo = new ArrayList<String>();
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    @FXML
    public void handleStartButton() {
        String serverIP = serverIpTextField.getText().trim();
        String portText = portField.getText().trim();

        if (serverIP.isEmpty()) {
            showAlert("Please enter the Server IP address.");
            return;
        }

        if (portText.isEmpty()) {
            showAlert("Please enter the Port number.");
            return;
        }

        int serverPort;
        try {
            serverPort = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            showAlert("Port number must be a valid integer.");
            return;
        }

        initializeTCPServer(serverIP, serverPort);
        statusLabel.setText("Server Active");
    }
    @FXML
    public void handleListenButton() {
        refreshClientsList();
    }

    // Method to clean up resources when the application is closing
    public void shutdown() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
        }

        if (tcpServer != null) {
            try {
                tcpServer.stopServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Add a new user
    @FXML
    private Button addUserButton;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField passwordField;

    @FXML
    public void addNewUser() {
        String username = usernameField.getText().trim().toLowerCase();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Username and password cannot be empty");
            return;
        }

        try {
            String filePath = "src/main/resources/com/mycompany/chatroom/mock_login.txt";

            java.util.List<String> lines = new ArrayList<>();

            java.io.File file = new java.io.File(filePath);
            if (file.exists()) {
                lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(filePath));
            } else {
                file.getParentFile().mkdirs();
            }

            for (String line : lines) {
                String existingUsername = line.split(" ")[0];
                if (existingUsername.equalsIgnoreCase(username)) {
                    showAlert("Username already exists");
                    return;
                }
            }


            try (java.io.FileWriter writer = new java.io.FileWriter(file, true)) {
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                    writer.write("\n");
                }
                writer.write(username + " " + password);
            }

            showAlert("User " + username + " added successfully");

            usernameField.clear();
            passwordField.clear();

        } catch (java.io.IOException e) {
            showAlert("Error adding user: " + e.getMessage());
            e.printStackTrace();
        }
    }

}