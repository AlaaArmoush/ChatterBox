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

    private int sourcePort;
    private DatagramSocket socket;
    private boolean running;
    private ExecutorService executorService;
    private Consumer<Message> messageReceiver;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public UDPPeer(int sourcePort, Consumer<Message> messageReceiver) {
        this.sourcePort = sourcePort;
        this.messageReceiver = messageReceiver;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void start() {
        try {
            socket = new DatagramSocket(sourcePort);
            running = true;

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
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        executorService.shutdown();
    }

    private void listenForMessages() {
        byte[] buffer = new byte[MAX_CHUNK_SIZE + 256];
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
                    }
                }

                int sep = receivedData.indexOf(' ');
                String sender, content, messageId;
                if (sep > 0) {
                    sender    = receivedData.substring(0, sep);
                    content   = receivedData.substring(sep + 1);
                    messageId = sender + "-" + timestamp.replace(" ", "").replace(":", "");
                } else {
                    sender    = "Guest";
                    content   = receivedData;
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
        else if (command.startsWith("FILE_INIT|")) {
            handleFileInitiation(command, senderIP, senderPort);
        } else if (command.startsWith("FILE_CHUNK|")) {
            handleFileChunk(command, senderIP, senderPort, rawData);
        } else if (command.startsWith("FILE_END|")) {
            handleFileEnd(command, senderIP, senderPort);
        } else if (command.startsWith("FILE_ACK|")) {
            handleFileAck(command);
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

            String connectMessage = "CONNECT" + "|" + myIp + "|" + clientPort + "|" + myUsername + "| At:" + connectTime.format(formatter);
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

                            // REMOVE this part!
                            // if (sender.equals(myUsername)) continue;

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
    private static final int MAX_CHUNK_SIZE = 1024;
    private Map<String, FileTransferInfo> outgoingTransfers = new HashMap<>();
    private Map<String, FileTransferInfo> incomingTransfers = new HashMap<>();
    private String saveLocation = System.getProperty("user.home");

    public static class FileTransferProgress {
        public String transferId;
        public String filename;
        public int progress;
        public long bytesTransferred;
        public long totalBytes;
        public boolean isComplete;
        public boolean isIncoming;
        public String status;
        public long e2eDelay;
        public long jitter;
    }

    private Consumer<FileTransferProgress> transferStatusUpdater;

    public static class FileTransferInfo {
        File file;
        FileOutputStream outputStream;
        FileInputStream inputStream;
        int totalChunks;
        int currentChunk;
        String transferId;
        String filename;
        long fileSize;
        long startTime;
        String senderIp;
        int senderPort;
        boolean complete;
        byte[][] receivedChunks;
        boolean[] receivedFlags;

        public long endTime;
        public long prevPacket = -1;
        public long minArrival = Long.MAX_VALUE;
        public long maxArrival = Long.MIN_VALUE;
        public long getE2EDelay() { return endTime - startTime; }
        public long getJitter() { return (maxArrival - minArrival)/2; }

        public FileTransferInfo(File file, String transferId, String destIp, int destPort) {
            this.file = file;
            this.transferId = transferId;
            this.filename = file.getName();
            this.fileSize = file.length();
            this.totalChunks = (int) Math.ceil((double) fileSize / MAX_CHUNK_SIZE);
            this.currentChunk = 0;
            this.senderIp = destIp;
            this.senderPort = destPort;
            this.startTime = System.currentTimeMillis();
            this.complete = false;
        }

        public FileTransferInfo(String transferId, String filename, long fileSize, String senderIp, int senderPort) {
            this.transferId = transferId;
            this.filename = filename;
            this.fileSize = fileSize;
            this.totalChunks = (int) Math.ceil((double) fileSize / MAX_CHUNK_SIZE);
            this.currentChunk = 0;
            this.senderIp = senderIp;
            this.senderPort = senderPort;
            this.startTime = System.currentTimeMillis();
            this.complete = false;
            this.receivedChunks = new byte[totalChunks][];
            this.receivedFlags = new boolean[totalChunks];
        }
    }

    public void setSaveLocation(String saveLocation) {
        this.saveLocation = saveLocation;
    }

    public void setTransferStatusUpdater(Consumer<FileTransferProgress> callback) {
        this.transferStatusUpdater = callback;
    }

    public boolean sendFile(String filePath, String destinationIP, int destinationPort) {
        try {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                return false;
            }

            String transferId = UUID.randomUUID().toString();
            System.out.println("Starting file transfer: " + file.getName() + " to " + destinationIP + ":" + destinationPort);

            String initMessage = "CMD:FILE_INIT|" + transferId + "|" + file.getName() + "|" + file.length();
            DatagramPacket packet = new DatagramPacket(
                    initMessage.getBytes(),
                    initMessage.getBytes().length,
                    InetAddress.getByName(destinationIP),
                    destinationPort
            );
            socket.send(packet);

            FileTransferInfo transferInfo = new FileTransferInfo(file, transferId, destinationIP, destinationPort);
            outgoingTransfers.put(transferId, transferInfo);
            transferInfo.startTime = System.currentTimeMillis();

            new Thread(() -> sendFileChunks(transferInfo)).start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendFileChunks(FileTransferInfo transferInfo) {
        try {
            System.out.println("Starting to send file chunks for: " + transferInfo.filename);
            transferInfo.inputStream = new FileInputStream(transferInfo.file);
            byte[] buffer = new byte[MAX_CHUNK_SIZE];
            int bytesRead;

            while ((bytesRead = transferInfo.inputStream.read(buffer)) > 0) {
                int chunkNum = transferInfo.currentChunk++;
                String header = "CMD:FILE_CHUNK|" + transferInfo.transferId + "|" + chunkNum + "|" + bytesRead + "|";
                byte[] headerBytes = header.getBytes();

                byte[] packetData = new byte[headerBytes.length + bytesRead];
                System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
                System.arraycopy(buffer, 0, packetData, headerBytes.length, bytesRead);

                DatagramPacket packet = new DatagramPacket(
                        packetData,
                        packetData.length,
                        InetAddress.getByName(transferInfo.senderIp),
                        transferInfo.senderPort
                );
                socket.send(packet);
                System.out.println("Sent chunk " + chunkNum + " of size " + bytesRead);

                updateProgress(transferInfo, false);
                Thread.sleep(800);
            }

            String endMessage = "CMD:FILE_END|" + transferInfo.transferId;
            DatagramPacket packet = new DatagramPacket(
                    endMessage.getBytes(),
                    endMessage.getBytes().length,
                    InetAddress.getByName(transferInfo.senderIp),
                    transferInfo.senderPort
            );
            socket.send(packet);
            System.out.println("Sent file end message for: " + transferInfo.filename);

            transferInfo.inputStream.close();
            transferInfo.complete = true;
            transferInfo.endTime = System.currentTimeMillis();
            updateProgress(transferInfo, false);

            if (transferStatusUpdater != null) {
                FileTransferProgress progress = new FileTransferProgress();
                progress.transferId = transferInfo.transferId;
                progress.filename = transferInfo.filename;
                progress.bytesTransferred = transferInfo.fileSize;
                progress.totalBytes = transferInfo.fileSize;
                progress.progress = 100;
                progress.isComplete = true;
                progress.isIncoming = false;
                progress.status = "complete";
                progress.e2eDelay = transferInfo.getE2EDelay();
                progress.jitter = 0;
                transferStatusUpdater.accept(progress);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            if (transferStatusUpdater != null) {
                FileTransferProgress progress = new FileTransferProgress();
                progress.transferId = transferInfo.transferId;
                progress.filename = transferInfo.filename;
                progress.status = "error";
                transferStatusUpdater.accept(progress);
            }
        }
    }

    private void updateProgress(FileTransferInfo transferInfo, boolean isIncoming) {
        if (transferStatusUpdater != null) {
            FileTransferProgress progress = new FileTransferProgress();
            progress.transferId = transferInfo.transferId;
            progress.filename = transferInfo.filename;
            progress.bytesTransferred = (long) transferInfo.currentChunk * MAX_CHUNK_SIZE;
            if (progress.bytesTransferred > transferInfo.fileSize) {
                progress.bytesTransferred = transferInfo.fileSize;
            }
            progress.totalBytes = transferInfo.fileSize;
            if(transferInfo.fileSize == 0)
                return;
            progress.progress = (int) ((progress.bytesTransferred * 100) / transferInfo.fileSize);
            progress.isComplete = transferInfo.complete;
            progress.isIncoming = isIncoming;
            progress.status = transferInfo.complete ? "complete" : "in progress";

            if(transferInfo.complete){
                progress.e2eDelay = transferInfo.getE2EDelay();
                progress.jitter = transferInfo.getJitter();
            }

            transferStatusUpdater.accept(progress);
        }
    }

    private void handleFileInitiation(String command, String senderIP, int senderPort) {
        String[] parts = command.split("\\|");
        if (parts.length >= 4) {
            String transferId = parts[1];
            String filename = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            System.out.println("Received file initiation: " + filename + " (" + formatFileSize(fileSize) + ")");

            FileTransferInfo transferInfo = new FileTransferInfo(transferId, filename, fileSize, senderIP, senderPort);
            incomingTransfers.put(transferId, transferInfo);
            transferInfo.startTime = System.currentTimeMillis();

            try {
                String ackMessage = "CMD:FILE_ACK|" + transferId;
                DatagramPacket packet = new DatagramPacket(
                        ackMessage.getBytes(),
                        ackMessage.getBytes().length,
                        InetAddress.getByName(senderIP),
                        senderPort
                );
                socket.send(packet);
                System.out.println("Sent file ACK for: " + transferId);

                if (transferStatusUpdater != null) {
                    FileTransferProgress progress = new FileTransferProgress();
                    progress.transferId = transferId;
                    progress.filename = filename;
                    progress.totalBytes = fileSize;
                    progress.bytesTransferred = 0;
                    progress.progress = 0;
                    progress.isComplete = false;
                    progress.isIncoming = true;
                    progress.status = "in progress";

                    transferStatusUpdater.accept(progress);
                }

                String content = "Receiving file: " + filename + " (" + formatFileSize(fileSize) + ")";
                Message fileMessage = new Message(content, "SYSTEM",
                        LocalDateTime.now().format(formatter), false);
                messageReceiver.accept(fileMessage);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleFileChunk(String command, String senderIP, int senderPort, byte[] rawData) {
        try {
            String header = "CMD:FILE_CHUNK|";
            String headerStr = new String(rawData, 0, Math.min(100, rawData.length));

            if (!headerStr.startsWith(header)) return;

            int[] pipePositions = new int[4];
            int currentPos = 0;
            for(int i = 0; i < pipePositions.length; i++){
                currentPos = headerStr.indexOf('|', currentPos + 1);
                if(currentPos < 0)
                    return;
                pipePositions[i] = currentPos;
            }
            String transferId = headerStr.substring(pipePositions[0] + 1, pipePositions[1]);
            int chunkNum = Integer.parseInt(headerStr.substring(pipePositions[1] + 1, pipePositions[2]));
            int chunkSize = Integer.parseInt(headerStr.substring(pipePositions[2] + 1, pipePositions[3]));

            FileTransferInfo transferInfo = incomingTransfers.get(transferId);
            if (transferInfo != null && (chunkNum < transferInfo.receivedFlags.length) && !transferInfo.receivedFlags[chunkNum]) {
                int dataStart = pipePositions[3] + 1;

                byte[] chunkData = new byte[chunkSize];
                System.arraycopy(rawData, dataStart, chunkData, 0, chunkSize);

                transferInfo.receivedChunks[chunkNum] = chunkData;
                transferInfo.receivedFlags[chunkNum] = true;
                transferInfo.currentChunk++;

                System.out.println("Received chunk " + chunkNum + " of size " + chunkSize + " for file: " + transferInfo.filename);

                updateProgress(transferInfo, true);

                if(transferInfo.prevPacket >= 0){
                    long arrivalTime = System.currentTimeMillis() - transferInfo.prevPacket;
                    transferInfo.minArrival = Math.min(transferInfo.minArrival, arrivalTime);
                    transferInfo.maxArrival = Math.max(transferInfo.maxArrival, arrivalTime);
                }
                //for the next packet calc
                transferInfo.prevPacket = System.currentTimeMillis();
            }
        } catch (Exception e) {
            System.err.println("Error processing file chunk: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleFileEnd(String command, String senderIP, int senderPort) {
        String[] parts = command.split("\\|");
        if (parts.length >= 2) {
            String transferId = parts[1];
            FileTransferInfo transferInfo = incomingTransfers.get(transferId);

            if (transferInfo != null) {
                try {
                    System.out.println("Received file end for: " + transferInfo.filename);

                    File outputFile = new File(saveLocation, transferInfo.filename);
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        for (int i = 0; i < transferInfo.totalChunks; i++) {
                            if (transferInfo.receivedFlags[i] && transferInfo.receivedChunks[i] != null) {
                                fos.write(transferInfo.receivedChunks[i]);
                            }
                        }
                    }

                    transferInfo.complete = true;
                    transferInfo.endTime = System.currentTimeMillis();
                    updateProgress(transferInfo, true);

                    String ackMessage = "CMD:FILE_ACK|" + transferId + "|COMPLETE";
                    DatagramPacket packet = new DatagramPacket(
                            ackMessage.getBytes(),
                            ackMessage.getBytes().length,
                            InetAddress.getByName(senderIP),
                            senderPort
                    );
                    socket.send(packet);
                    System.out.println("Sent complete ACK for file: " + transferInfo.filename);

                    String content = "File received: " + transferInfo.filename + " saved to " + outputFile.getAbsolutePath();
                    Message fileMessage = new Message(content, "SYSTEM",
                            LocalDateTime.now().format(formatter), false);
                    messageReceiver.accept(fileMessage);

                } catch (IOException e) {
                    e.printStackTrace();
                    String content = "Error receiving file: " + transferInfo.filename;
                    Message fileMessage = new Message(content, "SYSTEM",
                            LocalDateTime.now().format(formatter), false);
                    messageReceiver.accept(fileMessage);
                }
            }
        }
    }

    private void handleFileAck(String command) {
        String[] parts = command.split("\\|");
        if (parts.length >= 2) {
            String transferId = parts[1];
            boolean isComplete = parts.length >= 3 && "COMPLETE".equals(parts[2]);

            FileTransferInfo transferInfo = outgoingTransfers.get(transferId);
            if (transferInfo != null) {
                System.out.println("Received file ACK for: " + transferInfo.filename +
                        (isComplete ? " (COMPLETE)" : ""));

                if (isComplete) {
                    String content = "File sent successfully: " + transferInfo.filename;
                    Message fileMessage = new Message(content, "SYSTEM",
                            LocalDateTime.now().format(formatter), false);
                    messageReceiver.accept(fileMessage);
                }
            }
        }
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
}