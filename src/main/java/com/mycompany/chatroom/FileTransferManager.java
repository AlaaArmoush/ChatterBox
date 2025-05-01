package com.mycompany.chatroom;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class FileTransferManager {
    static final int MAX_CHUNK_SIZE = 1024;
    private Map<String, FileTransferInfo> outgoingTransfers = new HashMap<>();
    private Map<String, FileTransferInfo> incomingTransfers = new HashMap<>();
    private String saveLocation = System.getProperty("user.home");
    private DatagramSocket socket;
    private Consumer<FileTransferProgress> transferStatusUpdater;
    private Consumer<UDPPeer.Message> messageReceiver;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = Logger.getInstance();

    public FileTransferManager(DatagramSocket socket, Consumer<UDPPeer.Message> messageReceiver) {
        this.socket = socket;
        this.messageReceiver = messageReceiver;
    }

    // Move all file transfer classes and methods here
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
            logger.info("Initiating file transfer: " + file.getName() + " (" + file.length() + " bytes) to " + destinationIP + ":" + destinationPort);
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

    private void sendFileChunks(FileTransferInfo  transferInfo) {
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
                logger.info("Sent chunk #" + chunkNum + " size=" + bytesRead + " for transfer " + transferInfo.transferId);
                System.out.println("Sent chunk " + chunkNum + " of size " + bytesRead);

                updateProgress(transferInfo, false);
                Thread.sleep(10);
            }

            String endMessage = "CMD:FILE_END|" + transferInfo.transferId;
            DatagramPacket packet = new DatagramPacket(
                    endMessage.getBytes(),
                    endMessage.getBytes().length,
                    InetAddress.getByName(transferInfo.senderIp),
                    transferInfo.senderPort
            );

            socket.send(packet);
            logger.info("Completed file transfer: " + transferInfo.filename + " transferId=" + transferInfo.transferId);
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

    public void handleCommand(String command, String sender, String senderIP, int senderPort, byte[] rawData) {
        if (command.startsWith("FILE_INIT|")) {
            handleFileInitiation(command, senderIP, senderPort);
        } else if (command.startsWith("FILE_CHUNK|")) {
            handleFileChunk(command, senderIP, senderPort, rawData);
        } else if (command.startsWith("FILE_END|")) {
            handleFileEnd(command, senderIP, senderPort);
        } else if (command.startsWith("FILE_ACK|")) {
            handleFileAck(command);
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

            logger.info("Received FILE_INIT for " + filename + " (" + fileSize + " bytes) from " + senderIP + ":" + senderPort);
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
                UDPPeer.Message fileMessage = new UDPPeer.Message(content, "SYSTEM",
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

                logger.info("Received chunk #" + chunkNum + " size=" + chunkSize + " for file " + transferInfo.filename);
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
                    logger.info("Received FILE_END for " + transferInfo.filename + ", assembling file");
                    System.out.println("Sent complete ACK for file: " + transferInfo.filename);

                    String content = "File received: " + transferInfo.filename + " saved to " + outputFile.getAbsolutePath();
                    UDPPeer.Message fileMessage = new UDPPeer.Message(content, "SYSTEM",
                            LocalDateTime.now().format(formatter), false);
                    messageReceiver.accept(fileMessage);

                } catch (IOException e) {
                    e.printStackTrace();
                    String content = "Error receiving file: " + transferInfo.filename;
                    UDPPeer.Message fileMessage = new UDPPeer.Message(content, "SYSTEM",
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
                logger.info("Received FILE_ACK for transferId=" + transferId + (isComplete? " COMPLETE":""));
                if (isComplete) {
                    String content = "File sent successfully: " + transferInfo.filename;
                    UDPPeer.Message fileMessage = new UDPPeer.Message(content, "SYSTEM",
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