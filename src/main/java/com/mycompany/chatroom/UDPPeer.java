package com.mycompany.chatroom;

import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import java.io.*;

import java.net.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running) {
            try {
                socket.receive(packet);
                String receivedData = new String(packet.getData(), 0, packet.getLength());
                String senderAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                String timestamp = LocalDateTime.now().format(formatter);

                if (receivedData.startsWith("CMD:")) {
                    handleCommand(receivedData.substring(4), senderAddress, packet.getAddress().getHostAddress(), packet.getPort());
                } else {
                    String content = receivedData;
                    String sender = "Guest";
                    String messageId = senderAddress + "-" + timestamp.replace(" ", "").replace(":", "");

                    if (receivedData.contains("\u001F")) {
                        String[] parts = receivedData.split("\u001F", 2);
                        if (parts.length == 2) {
                            sender = parts[0];
                            content = parts[1];
                        } else {
                            messageId = parts[0];
                            content = parts.length > 1 ? parts[1] : "";
                        }
                    }

                    Message message = new Message(content, sender, timestamp, false, messageId);
                    messageReceiver.accept(message);
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void handleCommand(String command, String sender, String senderIP, int senderPort) {
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

            String transmissionFormat = myUsername + "\u001F" + content;

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
            // Create a unique ID combining sender and timestamp
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
    //****************************************************************************************************//
    //tcp
    private Socket tcpSocket;
    private PrintWriter fromClient;
    private BufferedReader fromServer;
    private String myIp;

    private static String myUsername;
    private boolean connectedTCP =false;
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
            this.myUsername = username;




            //String connectMessage =  "CONNECT"  + "|" + myIp + ":" + tcpSocket.getLocalPort() + "|" + clientPort + "|" + username + "| At:" + connectTime.format(formatter)   ;
            String connectMessage =  "CONNECT"  + "|" + myIp +  "|" + clientPort + "|" + username + "| At:" + connectTime.format(formatter)   ;

            ClientStatus = connectMessage;
            //send to server
            fromClient.println(connectMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public String getConnectionInfo() {
        return  "CONNECT|" + myIp + ":" + tcpSocket.getLocalPort() + "|" + myUsername;
    }
//*******************************************************************************

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
                String message;
                while ((message = fromServer.readLine()) != null) {
                    if (message.startsWith("USER_LIST|")) {
                        String userListData = message.substring(10);
                        String[] users = userListData.split(";");

                        // Update the internal list
                        synchronized(connectedUsers) {
                            connectedUsers.clear();
                            for (String user : users) {
                                if (!user.isEmpty()) {
                                    connectedUsers.add(user);
                                }
                            }
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








}
