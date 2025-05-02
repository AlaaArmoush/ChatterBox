# ChatterBox 💬

An extensible JavaFX-based desktop chatroom application supporting peer-to-peer UDP messaging, optional TCP server discovery, and a rich set of chat features, built with a modular MVC architecture for maintainability and future growth.

![ReadMeScreen](https://github.com/user-attachments/assets/cc4dda62-16b5-44ce-8cdf-2026fec2f7b4)


## Overview

ChatterBox enables both registered users and guests to connect and chat in one-on-one or group settings, leveraging UDP for direct messaging and an optional TCP-based discovery server for centralized user lookup.

- **Registered Users**: Authenticate using credentials stored in `mock_login.txt`.
- **Guests**: Instant anonymous access without login.
- **Hybrid Architecture**: Direct UDP communication between peers with optional TCP server for discovery and group coordination.

## Features

- **💬 Direct & Group Messaging**  
  - UDP-based direct chat with configurable IP/port.
  - TCP discovery server for group conversations and user presence.  

- **📋 User Presence & Status**  
  - Manual statuses: Active, Away, Busy.
  - Automatic idle detection: switch to Away after inactivity.  

- **📂 File Transfer**  
  - Chunked UDP file transfer with progress bar.
  - Metrics: packet count, latency, jitter, and error handling.
  - Configurable save location for received files.

- **🗑️ Message Management**
  - Archive tab for recently deleted messages.
  - Soft-delete with timed cleanup (2 min) and recovery options.
  - Export full chat history to `.txt`.  

- **⏱️ Session Tracking**  
  - Live session timer, last-login timestamp display.
  - Persistent login history between sessions.

- **🔌 Extensible MVC Architecture**  
  - Clear separation of UI (`*.fxml`), controllers, and network models.

## Architecture & Design

- **Controllers**:  
  - `LoginController`: Handles authentication and scene switching.  
  - `ChatController`: Manages message I/O, UI updates, and user actions.
  - `TcpServerController`: Manages the server-side user registry and message routing.

- **Models**:  
  - `UDPPeer`: Encapsulates UDP socket logic for messaging.
  - `TCPServer`: Discovery and group messaging transport.
  - `FileTransferManager`: Implements chunked file send/receive with reliability metrics.

- **Views**:  
  - Defined in FXML (`Login.fxml`, `Chat.fxml`, `Server.fxml`) for clean UI layout.  
  - CSS styling for consistent look-and-feel.
  - `ChatBubbleFactory` for stylized message presentation.

## Tech Stack

- **Java 17+ & JavaFX** – Core language and UI framework.
- **Maven** – Project build, dependency management, and lifecycle.
- **FXML & CSS** – Declarative UI and theming.

## Getting Started

### Prerequisites

- Java 17 or higher installed.
- Maven 3.6+ on your PATH.

### Clone & Build

```bash
$ git clone https://github.com/AlaaArmoush/ChatterBox.git
$ cd ChatterBox
$ mvn clean install
```

### Configure Credentials

Edit `src/main/resources/com/mycompany/chatroom/mock_login.txt`:
```
Alaa 1234
Yousef 4321
```  

## Running the Application

```bash
$ mvn exec:java -Dexec.mainClass="com.mycompany.chatroom.App"
```

## Usage

1. **Login**: Enter username/password or click **Guest**.  
2. **Connect**:
   - For P2P: Input peer IP and port; press **Enter** or click **Connect**.  
   - For Group: Start or join TCP server in **Server** mode.  
3. **Chat**: Send messages, files, change status, delete/recover messages.  
4. **Export**: Save conversation history via **Export Chat** button.
 
## License

Distributed under the MIT License. 
