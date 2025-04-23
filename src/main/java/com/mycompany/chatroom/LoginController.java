package com.mycompany.chatroom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Button guestButton;

    @FXML
    private Label statusLabel;

    private Map<String, String> credentials = new HashMap<>();

    @FXML
    public void initialize() {
        loadCredentials();

        loginButton.setOnAction(this::handleLogin);
        guestButton.setOnAction(this::handleGuestLogin);
    }

    private void loadCredentials() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getClass().getResourceAsStream("/com/mycompany/chatroom/mock_login.txt")))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    credentials.put(parts[0].toLowerCase(), parts[1].toLowerCase());
                }
            }
        } catch (IOException e) {
            statusLabel.setText("Error loading credentials");
            e.printStackTrace();
        }
    }

    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().toLowerCase().strip();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password");
            return;
        }

        if (credentials.containsKey(username) && credentials.get(username).equals(password)) {
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("Chat.fxml"));
                Parent root = fxmlLoader.load();

                ChatController controller = fxmlLoader.getController();
                controller.setUsername(username);

                Scene scene = new Scene(root);
                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.setScene(scene);
                stage.setTitle("ChatterBox");
                stage.show();

            } catch (IOException e) {
                statusLabel.setText("Error loading chat screen");
                e.printStackTrace();
            }
        } else {
            statusLabel.setText("Invalid username or password");
        }
    }
    private void handleGuestLogin(ActionEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("Chat.fxml"));
            Parent root = fxmlLoader.load();

            ChatController controller = fxmlLoader.getController();
            controller.setUsername("Guest");

            Scene scene = new Scene(root);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("ChatterBox");
            stage.show();
        } catch (IOException e) {
            statusLabel.setText("Error loading chat screen");
            e.printStackTrace();
        }

    }
}
