package com.mycompany.chatroom;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.net.URL;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class ChatController implements Initializable {

    @FXML
    private ComboBox<String> statusComboBox;
    @FXML
    private Button logoutButton;
    @FXML
    private TextField sourceIpField;
    @FXML
    private TextField sourcePortField;
    @FXML
    private TextField destIpField;
    @FXML
    private TextField destPortField;
    @FXML
    private ListView<HBox> messageListView;

    @FXML
    private TextArea messageInput;
    @FXML
    private Button sendButton;
    @FXML
    private Button sendAllButton;
    @FXML
    private Button deleteSelectedButton;
    @FXML
    private Button deleteAllButton;
    @FXML
    private Button exportChatButton;
    @FXML
    private Button sendFileButton;
    @FXML
    private TextField filePathField;
    @FXML
    private Button browseButton;
    @FXML
    private TextField saveLocationField;
    @FXML
    private Button changeSaveLocationButton;
    @FXML
    private ProgressBar fileTransferProgress;
    @FXML
    private Label fileSizeLabel;
    @FXML
    private Label packetsLabel;
    @FXML
    private Label delayLabel;
    @FXML
    private Label jitterLabel;
    @FXML
    private ListView<HBox> archiveListView;
    @FXML
    private Label lastLoginLabel;
    @FXML
    private Label sessionTimeLabel;

    @FXML
    private Button recoverSelectedButton;
    @FXML
    private Button recoverAllButton;

    @FXML
    private UDPPeer chatClient;
    private ObservableList<HBox> messages = FXCollections.observableArrayList();
    private ObservableList<HBox> archivedMessages = FXCollections.observableArrayList();
    private List<UDPPeer.Message> messageHistory = new ArrayList<>();
    private List<UDPPeer.Message> archiveHistory = new ArrayList<>();
    private ScheduledExecutorService archiveCleanupService;
    private java.util.Map<String, java.util.concurrent.ScheduledFuture<?>> cleanupTasks = new java.util.HashMap<>();



    private String username;
    public void setUsername(String username) {
        this.username = username;
    }


    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            sourceIpField.setText(localIP);
        } catch (UnknownHostException e) {
            sourceIpField.setText("127.0.0.1");
        }

        statusComboBox.setItems(FXCollections.observableArrayList("Active", "Busy", "Away"));
        statusComboBox.getSelectionModel().select("Active");
        saveLocationField.setText(System.getProperty("user.home"));
        updateSessionTime();
        messageListView.setItems(messages);
        archiveListView.setItems(archivedMessages);
      //  userListView.setItems(FXCollections.observableArrayList("User1", "User2"));
        setupButtonActions();
        archiveCleanupService = Executors.newSingleThreadScheduledExecutor();
        directMessaging();
    }

    private void setupButtonActions() {
        sendAllButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                sendMessageToServer();
            }
        });

        sendButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                sendMessage();
            }
        });
        deleteSelectedButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                deleteSelectedMessage();
            }
        });
        deleteAllButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                deleteAllMessages();
            }
        });
        exportChatButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                exportChat();
            }
        });
        browseButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                selectFile();
            }
        });
        changeSaveLocationButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                changeSaveLocation();
            }
        });
        sourcePortField.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                startUDPClient();
            }
        });

        recoverSelectedButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                recoverSelectedMessage();
            }
        });
        recoverAllButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                recoverAllMessages();
            }
        });

        logoutButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                handleLogout(e);
            }
        });

    }

    private void startUDPClient() {
        try {
            if (chatClient != null) {
                chatClient.stop();
            }

            int port = Integer.parseInt(sourcePortField.getText());

            chatClient = new UDPPeer(port, message -> {
                Platform.runLater(() -> {
                    displayMessage(message);
                });
            });
            chatClient.start();

            System.out.println("Started UDP client on port " + port);

        } catch (NumberFormatException ex) {
            showAlert("Invalid port number");
        }
    }


    private void sendMessage() {
        if (chatClient == null) {
            showAlert("Please set your port and connect first");
            return;
        }
        String message = messageInput.getText();
        if (message.isEmpty()) {
            return;
        }
        try {
            String destIp = destIpField.getText();
            int destPort = Integer.parseInt(destPortField.getText());
            chatClient.sendMessage(message, destIp, destPort);
            messageInput.clear();
        } catch (NumberFormatException e) {
            showAlert("Invalid destination port");
        }
    }

    private void displayMessage(UDPPeer.Message message) {
        if (message.getSender().equals("SYSTEM")) {
            if (message.getContent().startsWith("DELETE:")) {
                String messageIdToDelete = message.getContent().substring(7);
                deleteMessageById(messageIdToDelete);
                return;
            } else if (message.getContent().startsWith("RECOVER:")) {
                String messageIdToRecover = message.getContent().substring(8);
                recoverMessageById(messageIdToRecover);
                return;
            }
        }

        HBox messageContainer = new HBox();
        TextFlow textFlow = new TextFlow();
        Text timestampAndSender = new Text("[" + message.getTimestamp() + "] " + message.getSender() + ": ");
        timestampAndSender.setStyle("-fx-font-weight: bold");
        timestampAndSender.setFill(Color.WHITE);
        Text content = new Text(message.getContent());
        if (message.isSent()) {
            content.setFill(Color.YELLOW);
        } else {
            content.setFill(Color.RED);
        }
        textFlow.getChildren().addAll(timestampAndSender, content);
        messageContainer.getChildren().add(textFlow);

        messageContainer.getProperties().put("messageId", message.getMessageId());

        messages.add(messageContainer);
        messageHistory.add(message);
    }

    private void recoverMessageById(String messageId) {
        for (int i = 0; i < archiveHistory.size(); i++) {
            if (archiveHistory.get(i).getMessageId().equals(messageId)) {
                UDPPeer.Message messageToRecover = archiveHistory.get(i);
                archivedMessages.remove(i);
                archiveHistory.remove(i);
                restoreMessage(messageToRecover);
                break;
            }
        }
    }

    private void deleteMessageById(String messageId) {
        for (int i = 0; i < messageHistory.size(); i++) {
            if (messageHistory.get(i).getMessageId().equals(messageId)) {
                UDPPeer.Message removedMessage = messageHistory.get(i);
                messages.remove(i);
                messageHistory.remove(i);
                archiveMessage(removedMessage);
                break;
            }
        }
    }

    private void deleteSelectedMessage() {
        int selectedIndex = messageListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            UDPPeer.Message removedMessage = messageHistory.get(selectedIndex);

            try {
                String destIp = destIpField.getText();
                int destPort = Integer.parseInt(destPortField.getText());
                chatClient.sendCommand("DELETE_MESSAGE|" + removedMessage.getMessageId(), destIp, destPort);
                messages.remove(selectedIndex);
                messageHistory.remove(selectedIndex);
                archiveMessage(removedMessage);
            } catch (NumberFormatException e) {
                showAlert("Invalid destination port");
            }
        }
    }

    private void deleteAllMessages() {
        try {
            String destIp = destIpField.getText();
            int destPort = Integer.parseInt(destPortField.getText());

            for (UDPPeer.Message message : messageHistory) {
                chatClient.sendCommand("DELETE_MESSAGE|" + message.getMessageId(), destIp, destPort);
                archiveMessage(message);
            }
            messages.clear();
            messageHistory.clear();
        } catch (NumberFormatException e) {
            showAlert("Invalid destination port");
        }
    }

    private void archiveMessage(UDPPeer.Message message) {
        HBox archiveContainer = new HBox();
        TextFlow textFlow = new TextFlow();
        Text timestampAndSender = new Text("[" + message.getTimestamp() + "] " + message.getSender() + ": ");
        timestampAndSender.setStyle("-fx-font-weight: bold");
        timestampAndSender.setFill(Color.WHITE);
        Text content = new Text(message.getContent());
        content.setFill(Color.GRAY);
        textFlow.getChildren().addAll(timestampAndSender, content);
        archiveContainer.getChildren().add(textFlow);
        archivedMessages.add(archiveContainer);
        archiveHistory.add(message);

        java.util.concurrent.ScheduledFuture<?> cleanupTask = archiveCleanupService.schedule(new Runnable() {
            public void run() {
                Platform.runLater(new Runnable() {
                    public void run() {
                        archivedMessages.remove(archiveContainer);
                        archiveHistory.remove(message);
                        cleanupTasks.remove(message.getMessageId());
                    }
                });
            }
        }, 2, TimeUnit.MINUTES);

        cleanupTasks.put(message.getMessageId(), cleanupTask);
    }

    private void exportChat() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Chat History");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
            );
            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                FileWriter writer = new FileWriter(file);
                for (UDPPeer.Message message : messageHistory) {
                    writer.write(message.toString() + "\n");
                }
                writer.close();
                showAlert("Chat history exported successfully");
            }
        } catch (IOException e) {
            showAlert("Error exporting chat: " + e.getMessage());
        }
    }

    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            filePathField.setText(file.getAbsolutePath());
            try {
                long size = Files.size(Paths.get(file.getAbsolutePath()));
                fileSizeLabel.setText("Size: " + formatFileSize(size));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void changeSaveLocation() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Save Location");
        File directory = directoryChooser.showDialog(null);
        if (directory != null) {
            saveLocationField.setText(directory.getAbsolutePath());
        }
    }

    private void updateSessionTime() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        sessionTimeLabel.setText("Session: " + LocalDateTime.now().format(timeFormatter));
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void recoverSelectedMessage() {
        int selectedIndex = archiveListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            UDPPeer.Message messageToRecover = archiveHistory.get(selectedIndex);

            archivedMessages.remove(selectedIndex);
            archiveHistory.remove(selectedIndex);

            restoreMessage(messageToRecover);

            try {
                String destIp = destIpField.getText();
                int destPort = Integer.parseInt(destPortField.getText());

                chatClient.sendCommand("RECOVER_MESSAGE|" + messageToRecover.getMessageId(), destIp, destPort);
            } catch (NumberFormatException e) {
                showAlert("Invalid destination port");
            }
        }
    }

    private void recoverAllMessages() {
        List<UDPPeer.Message> messagesToRecover = new ArrayList<>(archiveHistory);

        for (UDPPeer.Message message : messagesToRecover) {
            restoreMessage(message);

            try {
                String destIp = destIpField.getText();
                int destPort = Integer.parseInt(destPortField.getText());
                chatClient.sendCommand("RECOVER_MESSAGE|" + message.getMessageId(), destIp, destPort);
            } catch (NumberFormatException e) {
                showAlert("Invalid destination port");
                break;
            }
        }

        archivedMessages.clear();
        archiveHistory.clear();
    }

    private void restoreMessage(UDPPeer.Message message) {
        HBox messageContainer = new HBox();
        TextFlow textFlow = new TextFlow();
        Text timestampAndSender = new Text("[" + message.getTimestamp() + "] " + message.getSender() + ": ");
        timestampAndSender.setStyle("-fx-font-weight: bold");
        timestampAndSender.setFill(Color.WHITE);
        Text content = new Text(message.getContent());
        if (message.isSent()) {
            content.setFill(Color.YELLOW);
        } else {
            content.setFill(Color.RED);
        }
        textFlow.getChildren().addAll(timestampAndSender, content);
        messageContainer.getChildren().add(textFlow);

        messageContainer.getProperties().put("messageId", message.getMessageId());
        java.util.concurrent.ScheduledFuture<?> cleanupTask = cleanupTasks.remove(message.getMessageId());
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        messages.add(messageContainer);
        messageHistory.add(message);
    }

    public void handleLogout(ActionEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("Login.fxml"));
            Parent root = fxmlLoader.load();
            Scene scene = new Scene(root);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("ChatterBox");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (chatClient != null) {
            chatClient.stop();
        }
        if (archiveCleanupService != null) {
            archiveCleanupService.shutdown();
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////**//
    @FXML
    private TextField serverPortField;
    @FXML
    private TextField serverIpField;

    private TcpServerController tcpServerController;
    @FXML
    private ListView<String> userListView;

    private java.util.Timer userListTimer;




  public void handleTCPConnectButton() throws IOException {
      try {
          String serverIp = serverIpField.getText();
          int serverPort = Integer.parseInt(serverPortField.getText());

          startUDPClient();
          chatClient.setIp(sourceIpField.getText());
          chatClient.connectToTCPServer(serverIp, serverPort, username,
                  Integer.parseInt(sourcePortField.getText()));

          chatClient.startTcpListener();
          startUserListRefreshTimer();
          showAlert("Successfully connected to TCP server");
      } catch (NumberFormatException e) {
          showAlert("Invalid port number");
      }
  }


    private void startUserListRefreshTimer() {
        if (userListTimer != null) {
            userListTimer.cancel();
        }

        // Create a new timer
        userListTimer = new Timer(true);
        userListTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    List<String> users = chatClient.getConnectedUsers();
                    userListView.getItems().clear();
                    for (String user : users) {
                        userListView.getItems().add(formatForUserList(user));
                    }
                });
            }
        }, 0, 1000);
    }



    private String formatForUserList(String connectionString) {
        String[] parts = connectionString.split("\\|");
        if (parts.length >= 4) {
            String ip = parts[1];    // IP
            String udpPort = parts[2]; // port
            String username = parts[3]; // Username

            return username + "|" + ip + "|" + udpPort + "|";
        }
        return connectionString;
    }

    public void directMessaging()
    {
        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                handleListItemClick(newValue);
            }
        });

    }

    private void handleListItemClick(String Value)
    {
        String[] destData = Value.split("\\|");
        if (destData.length >= 3)
        {
            String username = destData[0];
            String destIp = destData[1];
            String destPort = destData[2];

            destIpField.setText(destIp);
            destPortField.setText(destPort);
            messageInput.setPromptText("Message to " + username + ".....");

        }

        messageInput.setPromptText("Type Message...");



    }

    @FXML
    public void sendMessageToServer() {
        String messageText = messageInput.getText();
        if (messageText != null && !messageText.isEmpty()) {
            chatClient.sendMessageToServer(messageText);
            messageInput.clear();
        }
    }



}
