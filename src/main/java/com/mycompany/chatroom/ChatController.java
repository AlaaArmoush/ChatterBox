package com.mycompany.chatroom;

import java.io.*;
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

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
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
import javafx.util.Duration;


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
        loadLastLoginInfo();
    }


    @Override
    public void initialize(URL url, ResourceBundle rb) {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            sourceIpField.setText(localIP);
        } catch (UnknownHostException e) {
            sourceIpField.setText("127.0.0.1");
        }
        startTimer();
        statusComboBox.getSelectionModel().select("Active");
        statusComboBox.getItems().addAll("Active","Away","Busy");
        statusComboBox.setOnAction(e -> handleStatusComboBox());
        saveLocationField.setText(System.getProperty("user.home"));
        updateSessionTime();
        messageListView.setItems(messages);
        archiveListView.setItems(archivedMessages);

        ChatBubbleFactory.applyChatBubbleStyle(messageListView);
        ChatBubbleFactory.applyChatBubbleStyle(archiveListView);

        setupButtonActions();
        archiveCleanupService = Executors.newSingleThreadScheduledExecutor();
        directMessaging();
        setupInactivityCheck();

        setupFileTransferUI();
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

            chatClient.setSaveLocation(saveLocationField.getText());
            chatClient.setTransferStatusUpdater(progress -> {
                Platform.runLater(() -> {
                    updateFileTransferProgress(progress);
                });
            });
            chatClient.start();
            chatClient.setAsActive();

            chatClient.setTransferStatusUpdater(progress ->
                    Platform.runLater(() -> updateFileTransferProgress(progress))
            );
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
        if ("SYSTEM".equals(message.getSender())) {
            System.out.println(">> SYSTEM command received: " + message.getContent());
            if (message.getContent().startsWith("DELETE:")) {
                deleteMessageById(message.getContent().substring(7));
                return;
            } else if (message.getContent().startsWith("RECOVER:")) {
                recoverMessageById(message.getContent().substring(8));
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
                chatClient.sendCommand(
                        "DELETE_MESSAGE|" + removedMessage.getMessageId(),
                        destIpField.getText(),
                        Integer.parseInt(destPortField.getText())
                );                messages.remove(selectedIndex);
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
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Save Location");
        File dir = chooser.showDialog(null);
        if (dir != null) {
            String path = dir.getAbsolutePath();
            saveLocationField.setText(path);
            if (chatClient != null) {
                chatClient.setSaveLocation(path);
                showAlert("Save location updated to:\n" + path);
            }
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
            saveLogoutTime();
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
        if (timer != null) {
            timer.shutdown();
        }
        saveLogoutTime();
    }

    ///////////////////////////////////////////////////////////////////////////////////* tcp related*//
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

        userListTimer = new Timer(true);
        userListTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    List<String> users = chatClient.getConnectedUsers();
                    userListView.getItems().clear();
                    for (String user : users) {
                        String formattedUser = formatForUserList(user);
                        if (formattedUser != null) {
                            userListView.getItems().add(formattedUser);
                        }
                    }
                });
            }
        }, 0, 1000);
    }



    private String formatForUserList(String connectionString) {
        String[] parts = connectionString.split("\\|");

        if (parts.length >= 6) {
            String ip = parts[1];
            String udpPort = parts[2];
            String username = parts[3];
            String status = statusComboBox.getValue();


            return username + "|" + status + "|" + ip + "|"+udpPort;

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

    /// ///// last log out, elapsed time etc...

    private ScheduledExecutorService timer;

    private void startTimer(){
        long startTime = System.currentTimeMillis();

        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(() -> {
            Platform.runLater(() -> {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - startTime;

                long seconds = (elapsedTime / 1000) % 60;
                long minutes = (elapsedTime / (1000 * 60)) % 60;
                long hours = (elapsedTime / (1000 * 60 * 60)) % 24;

                String timeString = String.format("Session: %02d:%02d:%02d", hours, minutes, seconds);
                sessionTimeLabel.setText(timeString);
            });
        }, 0, 1, TimeUnit.SECONDS);

    }

    private void saveLogoutTime() {
        try {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String exitAt = now.format(formatter);

            Map<String, String> loginInfo = new HashMap<>();
            File file = new File("last_login.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        loginInfo.put(parts[0], parts[1]);
                    }
                }
                reader.close();
            }
            loginInfo.put(username, exitAt);
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            for (Map.Entry<String, String> entry : loginInfo.entrySet()) {
                writer.write(entry.getKey() + "|" + entry.getValue() + "\n");
            }
            writer.close();

        } catch (IOException e) {
            System.err.println("Error saving logout time: " + e.getMessage());
        }
    }



    private void loadLastLoginInfo() {
        try {
            File file = new File("last_login.txt");

            if (!file.exists()) {
                lastLoginLabel.setText("First login");
                return;
            }

            Map<String, String> loginInfo = new HashMap<>();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Read line: " + line);
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    loginInfo.put(parts[0], parts[1]);
                }
            }
            reader.close();

            if (loginInfo.containsKey(username)) {
                lastLoginLabel.setText("Last Login: " + loginInfo.get(username));
            } else {
                lastLoginLabel.setText("First Login");
            }

        } catch (IOException e) {
            System.err.println("Error loading last login info: " + e.getMessage());
            lastLoginLabel.setText("Error loading login information");
        }
    }


    ////////// file sharing
    private void setupFileTransferUI() {
        sendFileButton.setOnAction(new javafx.event.EventHandler<javafx.event.ActionEvent>() {
            public void handle(javafx.event.ActionEvent e) {
                sendFile();
            }
        });

        if (chatClient != null) {
            chatClient.setTransferStatusUpdater(progress -> {
                Platform.runLater(() -> {
                    updateFileTransferProgress(progress);
                });
            });
        }
    }

    private void sendFile() {
        if (chatClient == null) {
            showAlert("Please set your port and connect first");
            return;
        }

        String filePath = filePathField.getText();
        if (filePath.isEmpty()) {
            showAlert("Please select a file first");
            return;
        }

        try {
            String destIp = destIpField.getText();
            int destPort = Integer.parseInt(destPortField.getText());

            if (destIp.isEmpty() || destPort <= 0) {
                showAlert("Please specify a valid destination IP and port");
                return;
            }

            fileTransferProgress.setProgress(0);
            fileSizeLabel.setText("Preparing to send...");

            chatClient.setSaveLocation(saveLocationField.getText());

            boolean success = chatClient.sendFile(filePath, destIp, destPort);
            if (!success) {
                showAlert("Failed to start file transfer. Please check the file path.");
            } else {
                showAlert("Starting file transfer...");
            }
        } catch (NumberFormatException e) {
            showAlert("Invalid destination port");
        }
    }

    private void updateFileTransferProgress(FileTransferManager.FileTransferProgress progress) {
        fileTransferProgress.setProgress(progress.progress / 100.0);

        if (progress.isIncoming) {
            fileSizeLabel.setText("Receiving: " + formatFileSize(progress.bytesTransferred) +
                    " / " + formatFileSize(progress.totalBytes));
        } else {
            fileSizeLabel.setText("Sending: " + formatFileSize(progress.bytesTransferred) +
                    " / " + formatFileSize(progress.totalBytes));
        }

        int packets = (int) (progress.bytesTransferred / 1024) + 1;
        int totalPackets = (int) (progress.totalBytes / 1024) + 1;
        packetsLabel.setText("Packets: " + packets + " / " + totalPackets);

        if (progress.isComplete) {
            if (progress.isIncoming) {
                fileSizeLabel.setText("Received: " + formatFileSize(progress.totalBytes));
            } else {
                fileSizeLabel.setText("Sent: " + formatFileSize(progress.totalBytes));
            }

            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> {
                                fileTransferProgress.setProgress(0);
                                fileSizeLabel.setText("");
                                packetsLabel.setText("Packets: 0 / 0");
                            });
                        }
                    },
                    3000
            );

            delayLabel.setText("Delay: " + progress.e2eDelay + " ms");
            jitterLabel.setText("Jitter: " + progress.jitter + " ms");

        }
    }


    @FXML
    private void handleStatusComboBox() {
        String status = statusComboBox.getValue();
        if (chatClient != null) {
            if (status.equals("Active")) {
                chatClient.setAsActive();
                if (chatClient.getTcpSocket() != null && chatClient.getTcpSocket().isConnected()) {
                    chatClient.sendStatusUpdate("Active");
                }
            } else if (status.equals("Away")) {
                chatClient.setAsAway();
                if (chatClient.getTcpSocket() != null && chatClient.getTcpSocket().isConnected()) {
                    chatClient.sendStatusUpdate("Away");
                }
            } else if (status.equals("Busy")) {
                chatClient.setAsBusy();
                if (chatClient.getTcpSocket() != null && chatClient.getTcpSocket().isConnected()) {
                    chatClient.sendStatusUpdate("Busy");
                }

            }
        }
    }
    //******************AFK
    private PauseTransition inactivityTimer;
    private static final int INACTIVITY_TIMEOUT = 10;

    @FXML
    private BorderPane chatRoot;


    private void setupInactivityCheck() {
        inactivityTimer = new PauseTransition(Duration.seconds(INACTIVITY_TIMEOUT));
        inactivityTimer.setOnFinished(event -> setStatusAway());

        chatRoot.addEventFilter(MouseEvent.ANY, this::resetInactivityTimer);
        chatRoot.addEventFilter(KeyEvent.ANY, this::resetInactivityTimer);
    }

    private void resetInactivityTimer(Event event) {
        inactivityTimer.playFromStart();
        setStatusActive();
    }

    private void setStatusAway() {
        if (chatClient != null) {
            chatClient.sendStatusUpdate("Away");
            statusComboBox.setValue("Away");
        }
    }

    private void setStatusActive() {
        if (chatClient != null) {
            chatClient.sendStatusUpdate("Active");
            statusComboBox.setValue("Active");

        }
    }








}
