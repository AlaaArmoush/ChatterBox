module com.mycompany.chatroom {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;

    opens com.mycompany.chatroom to javafx.fxml;
    exports com.mycompany.chatroom;
}
