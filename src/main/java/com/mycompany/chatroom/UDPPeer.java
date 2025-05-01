package com.mycompany.chatroom;

import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;



public class UDPPeer {
    private static final Logger logger = Logger.getInstance();

    private int sourcePort;
    private DatagramSocket socket;
    private boolean running;
    private ExecutorService executorService;
    private Consumer<Message> messageReceiver;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private FileTransferManager fileTransferManager;

    public UDPPeer(int sourcePort, Consumer<Message> messageReceiver) {
        this.sourcePort = sourcePort;
        this.messageReceiver = messageReceiver;
        this.executorService = Executors.newSingleThreadExecutor();
    }


    public void start() {
        try {
            logger.info("Starting UDPPeer on port " + sourcePort);
            socket = new DatagramSocket(sourcePort);
            running = true;

            socket.setSendBufferSize(2097152);
            socket.setReceiveBufferSize(2097152);

            this.fileTransferManager = new FileTransferManager(this.socket, this.messageReceiver);


            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    listenForMessages();
                }
            });

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        logger.info("Stopping UDPPeer");
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        executorService.shutdown();
    }

    private void listenForMessages() {
        byte[] buffer = new byte[FileTransferManager.MAX_CHUNK_SIZE + 256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                String receivedData  = new String(data, StandardCharsets.UTF_8);
                String senderAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                String timestamp     = LocalDateTime.now().format(formatter);

                if (receivedData.startsWith("CMD:")) {
                    handleCommand(
                            receivedData.substring(4),
                            senderAddress,
                            packet.getAddress().getHostAddress(),
                            packet.getPort(),
                            data
                    );
                    continue;
                }

                if (receivedData.contains("\u001F")) {
                    String[] parts = receivedData.split("\u001F", 3);
                    if (parts.length == 3) {
                        Message msg = new Message(
                                parts[2],
                                parts[1],
                                timestamp,
                                false,
                                parts[0]
                        );
                        messageReceiver.accept(msg);
                        continue;
                    } else if (parts.length == 2) {
                        Message msg = new Message(
                                parts[1],
                                parts[0],
                                timestamp,
                                false
                        );
                        messageReceiver.accept(msg);
                        continue;
                    }
                }

                int sep = receivedData.indexOf(' ');
                String sender;
                String content;
                String messageId;
                if (sep > 0) {
                    sender    = receivedData.substring(0, sep);
                    content   = receivedData.substring(sep + 1);
                    messageId = sender + "-" + timestamp.replace(" ", "").replace(":", "");
                } else {
                    int splitIndex = receivedData.indexOf("|");
                    sender    = receivedData.substring(1, splitIndex);
                    sender    = sender.substring(0, 1).toUpperCase() + sender.substring(1);
                    content   = receivedData.substring(splitIndex);
                    messageId = senderAddress + "-" + timestamp.replace(" ", "").replace(":", "");
                }

                Message fallback = new Message(
                        content,
                        sender,
                        timestamp,
                        false,
                        messageId
                );
                messageReceiver.accept(fallback);

            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }


    private void handleCommand(String command, String sender, String senderIP, int senderPort, byte[] rawData) {
        logger.info("Handling command: " + command + " from " + sender);

        if (command.startsWith("DELETE_MESSAGE|")) {
            String[] parts = command.split("\\|");
            if (parts.length >= 2) {
                String messageId = parts[1];
                sendDeleteAcknowledgment(messageId, senderIP, senderPort);
                Message deleteMessage = new Message("DELETE:" + messageId, "SYSTEM",
                        LocalDateTime.now().format(formatter), false);
                messageReceiver.accept(deleteMessage);
            }
        } else if (command.startsWith("DELETE_ACK|")) {
            String[] parts = command.split("\\|");
            if (parts.length >= 2) {
                String messageId = parts[1];
                System.out.println("Delete acknowledged for message: " + messageId);
            }
        } else if (command.startsWith("RECOVER_MESSAGE|")) {
            String[] parts = command.split("\\|");
            if (parts.length >= 2) {
                String messageId = parts[1];
                Message recoverMessage = new Message("RECOVER:" + messageId, "SYSTEM",
                        LocalDateTime.now().format(formatter), false);
                messageReceiver.accept(recoverMessage);
            }
        }
        // Added file transfer commands
        else if (command.startsWith("FILE_INIT|") ||
                command.startsWith("FILE_CHUNK|") ||
                command.startsWith("FILE_END|") ||
                command.startsWith("FILE_ACK|")) {
            fileTransferManager.handleCommand(command, sender, senderIP, senderPort, rawData);
        }
    }

    private void sendDeleteAcknowledgment(String messageId, String destIp, int destPort) {
        try {
            String ackCommand = "CMD:DELETE_ACK|" + messageId;
            DatagramPacket packet = new DatagramPacket(
                    ackCommand.getBytes(),
                    ackCommand.getBytes().length,
                    InetAddress.getByName(destIp),
                    destPort
            );
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String content, String destinationIP, int destinationPort) {
        try {
            String timestamp = LocalDateTime.now().format(formatter);
            String messageId = "Me-" + timestamp.replace(" ", "").replace(":", "");

            // NEW format: messageId␟username␟content
            String transmissionFormat = messageId + "\u001F" + myUsername + "\u001F" + content;

            byte[] data = transmissionFormat.getBytes();
            InetAddress address = InetAddress.getByName(destinationIP);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, destinationPort);
            logger.info("Sending message to " + destinationIP + ":" + destinationPort + " – content=" + content);
            socket.send(packet);

            Message message = new Message(content, "Me", timestamp, true, messageId);
            messageReceiver.accept(message);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessageToServer(String messageContent) {
        if (tcpSocket != null && fromClient != null) {
            String timestamp = LocalDateTime.now().format(formatter);
            String messageId = myUsername + "-" + timestamp.replace(" ", "").replace(":", "");
            String formattedMessage = "MESSAGE|" + messageId + "|" + myUsername + "|" + timestamp + "|" + messageContent;
            fromClient.println(formattedMessage);

            Message sentMessage = new Message(messageContent, "Me", timestamp, true, messageId);
            messageReceiver.accept(sentMessage);
        }
    }

    public void sendCommand(String command, String destIp, int destPort) {
        try {
            String fullCommand = "CMD:" + command;
            DatagramPacket packet = new DatagramPacket(
                    fullCommand.getBytes(),
                    fullCommand.getBytes().length,
                    InetAddress.getByName(destIp),
                    destPort
            );
            logger.info("Sending command: " + command + " to " + destIp + ":" + destPort);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Message {
        private String content;
        private String sender;
        private String timestamp;
        private boolean sent;
        private String messageId;

        public Message(String content, String sender, String timestamp, boolean sent) {
            this.content = content;
            this.sender = sender;
            this.timestamp = timestamp;
            this.sent = sent;
            this.messageId = sender + "-" + timestamp.replace(" ", "").replace(":", "");
        }

        public Message(String content, String sender, String timestamp, boolean sent, String messageId) {
            this.content = content;
            this.sender = sender;
            this.timestamp = timestamp;
            this.sent = sent;
            this.messageId = messageId;
        }

        public String getContent() {
            return content;
        }

        public String getSender() {
            return sender;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public boolean isSent() {
            return sent;
        }

        public String getMessageId() {
            return messageId;
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] " + sender + ": " + content;
        }
    }

    //tcp connection fields
    private Socket tcpSocket;
    private PrintWriter fromClient;
    private BufferedReader fromServer;
    private String myIp;
    private static String myUsername = "User"; // Default username to avoid null
    private boolean connectedTCP = false;
    private String ClientStatus;

    public String getClientStatus() {
        return ClientStatus;
    }

    public Socket getTcpSocket() {
        return tcpSocket;
    }

    public String getMyUsername() {
        return myUsername;
    }

    public void setIp(String ip) {
        this.myIp = ip;
    }
    private int ClientPort;


    public void connectToTCPServer(String serverIp, int serverPort, String username, int clientPort) {
        LocalTime connectTime = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        try {
            this.tcpSocket = new Socket(serverIp, serverPort);
            this.fromServer = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            this.fromClient = new PrintWriter(tcpSocket.getOutputStream(), true);
            this.myIp = InetAddress.getLocalHost().getHostAddress();
            // Fix for null username issue
            if (username != null && !username.trim().isEmpty()) {
                myUsername = username;
            }
            ClientPort=clientPort;
            String connectMessage = "CONNECT" + "|" + myIp + "|" + clientPort + "|" + myUsername +"|" + userStatus + "|At:" + connectTime.format(formatter);
            ClientStatus = connectMessage;
            fromClient.println(connectMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public String getConnectionInfo() {
        return "CONNECT|" + myIp + ":" + tcpSocket.getLocalPort() + "|" + myUsername;
    }

    private List<String> connectedUsers = new ArrayList<>();
    private Consumer<List<String>> userListUpdater;

    public List<String> getConnectedUsers() {
        return connectedUsers;
    }

    public void setUserListUpdater(Consumer<List<String>> updater) {
        this.userListUpdater = updater;
    }

    public void startTcpListener() {
        Thread listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = fromServer.readLine()) != null) {
                   System.out.println("Received raw TCP line: " + line);

                    if (line.startsWith("USER_LIST|")) {
                        String userListData = line.substring("USER_LIST|".length());
                        String[] users = userListData.split(";");
                        synchronized (connectedUsers) {
                            connectedUsers.clear();
                            for (String u : users) {
                                if (!u.isEmpty()) {
                                    connectedUsers.add(u);
                                }
                            }
                        }
                        if (userListUpdater != null) {
                            userListUpdater.accept(connectedUsers);
                        }

                    } else if (line.startsWith("MESSAGE|")) {
                        String[] parts = line.split("\\|", 5);
                        if (parts.length >= 5) {
                            String messageId = parts[1];
                            String sender    = parts[2];
                            String timestamp = parts[3];
                            String content   = parts[4];

                             if (sender.equals(myUsername)) continue;

                            Message chatMsg = new Message(content, sender, timestamp, false, messageId);
                            messageReceiver.accept(chatMsg);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    //////////////////file sharing 

    public void setSaveLocation(String saveLocation) {
        // ensure it’s created before use
        if (fileTransferManager == null) {
            fileTransferManager = new FileTransferManager(socket, messageReceiver);
        }
        fileTransferManager.setSaveLocation(saveLocation);
    }

    public void setTransferStatusUpdater(Consumer<FileTransferManager.FileTransferProgress> callback) {
        fileTransferManager.setTransferStatusUpdater(callback);
    }

    public boolean sendFile(String filePath, String destinationIP, int destinationPort) {
        return fileTransferManager.sendFile(filePath, destinationIP, destinationPort);
    }

    //*****************************************STATUS******************
    private String userStatus;


    public void setAsActive()
    {userStatus="Active";}
    public void setAsBusy()
    {userStatus="Busy";}
    public void setAsAway()
    {userStatus="Away";}

    public String getUserStatus() {
        return userStatus;
    }

    public void sendStatusUpdate(String status) {
        if (fromClient != null) {
            this.userStatus = status;
            fromClient.println("STATUS_UPDATE|" + "|" + myIp + "|" + ClientPort + "|" + myUsername + "|" + status);
        }
    }

}