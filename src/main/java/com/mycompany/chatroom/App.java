package com.mycompany.chatroom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    private static Scene scene;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;

        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("Login.fxml"));
        Parent root = fxmlLoader.load();

        scene = new Scene(root);
        stage.setScene(scene);
        stage.setTitle("ChatterBox Login");
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        Parent root = fxmlLoader.load();

        scene.setRoot(root);
        primaryStage.setTitle("ChatterBox");

        if (fxml.equals("Chat")) {
            ChatController controller = fxmlLoader.getController();
            primaryStage.setOnCloseRequest(event -> {
                controller.shutdown();
                Platform.exit();
                System.exit(0);
            });
        }
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}
