package com.mycompany.chatroom;
import java.io.*;

import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TCPServer
{


    private boolean running = false;

    private ServerSocket tcpSocket;

    private InetAddress tcpIp;
    private int TCPPort;

    private ExecutorService executorService = Executors.newCachedThreadPool();

    // client/info
    private Map<Socket, String> clientSocketMap = new HashMap<>();


    private DatagramSocket forwardingSocket;

    public TCPServer(InetAddress tcpIp, int inPort) throws IOException {
        this.tcpIp = tcpIp;
        this.TCPPort = inPort;
        tcpSocket = new ServerSocket(inPort);
        forwardingSocket = new DatagramSocket();
        System.out.println("TCP Server initialized on port: " + inPort);
    }



    public int getTCPPort() {
        return TCPPort;
    }

    public InetAddress getTcpIp() {
        return tcpIp;
    }

    public void start() {
        running = true;
        executorService.submit(() -> {
            try {
                System.out.println("TCP Server is running and waiting for connections...");
                while (running) {
                    Socket socket = tcpSocket.accept();
                    handleClient(socket);
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                } else {
                    System.out.println("TCP Server stopped.");
                }
            }
        });
    }

    public boolean isRunning() {
        return running;
    }

    private List<String> clientsInfo = new ArrayList<>();

    private ArrayList<UDPPeer> allConnectedClients = new ArrayList<>();




    public List<String> getClientsInfo() {
        return clientsInfo;
    }

    private int i = 0;

    private void handleClient(Socket clientSocket) {
        executorService.submit(() -> {
            try {
                BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String receivedMessage;
                while ((receivedMessage = clientReader.readLine()) != null) {
                    System.out.println("Client: " + receivedMessage);

                    if (receivedMessage.startsWith("CONNECT")) {
                        clientSocketMap.put(clientSocket, receivedMessage);
                        backToClient();
                    } else if (receivedMessage.startsWith("MESSAGE|")) {
                        String[] messageParts = receivedMessage.split("\\|");
                        String senderUsername = "";
                        if (messageParts.length >= 3) {
                            senderUsername = messageParts[2]; // Extract sender username
                        }

                        forwardMessageToClients(receivedMessage, senderUsername);
                    }

                    clientsInfo.add(i, receivedMessage);
                    i++;
                }
            } catch (IOException exception) {
                handleClientDisconnect(clientSocket);
                System.out.println("Client disconnected: " + exception.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        });
    }

    private void forwardMessageToClients(String message, String senderUsername) {
        String[] parts = message.split("\\|");
        String actualMessage = parts.length >= 5 ? parts[4] : message;

        for (String clientInfo : clientsInfo) {
            if (clientInfo.startsWith("CONNECT")) {
                String[] clientParts = clientInfo.split("\\|");
                if (clientParts.length >= 4) {
                    String clientIp = clientParts[1];
                    try {
                        int udpPort = Integer.parseInt(clientParts[2]);
                        String clientUsername = clientParts[3].split(" ")[0];

                        if (clientUsername.equals(senderUsername) ||
                                clientUsername.startsWith(senderUsername)) {
                            continue;
                        }

                        String messageWithSender = senderUsername + "\u001F" + actualMessage;

                        byte[] messageData = messageWithSender.getBytes();
                        InetAddress destAddress = InetAddress.getByName(clientIp);
                        DatagramPacket packet = new DatagramPacket(
                                messageData,
                                messageData.length,
                                destAddress,
                                udpPort
                        );
                        forwardingSocket.send(packet);
                    } catch (NumberFormatException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void handleClientDisconnect(Socket socket) {
        String clientInfo = clientSocketMap.get(socket);
        if (clientInfo != null) {
            clientSocketMap.remove(socket);

            clientsInfo.remove(clientInfo);

            clientsInfo.add("DISCONNECT|" + clientInfo);

            System.out.println("Removed disconnected client: " + clientInfo);
        }
    }

    public void stopServer() throws IOException {
        running = false;
        tcpSocket.close();
        if (forwardingSocket != null) {
            forwardingSocket.close();
        }
        executorService.shutdown();
    }


    public void backToClient() {
        String userList = "USER_LIST|";
        for (String clientInfo : clientsInfo) {
            if (clientInfo.startsWith("CONNECT")) {
                userList += clientInfo + ";";
            }
        }
        for (Socket socket : clientSocketMap.keySet()) {
            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(userList);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



}