package com.mycompany.chatroom;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;

public class ChatBubbleFactory {

    public static void applyChatBubbleStyle(ListView<HBox> listView) {
        listView.setCellFactory(new Callback<ListView<HBox>, ListCell<HBox>>() {
            @Override
            public ListCell<HBox> call(ListView<HBox> param) {
                return new ChatBubbleCell();
            }
        });
    }

    private static class ChatBubbleCell extends ListCell<HBox> {
        @Override
        protected void updateItem(HBox item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (item.getChildren().isEmpty() || !(item.getChildren().get(0) instanceof TextFlow)) {
                setGraphic(item);
                return;
            }
            TextFlow textFlow = (TextFlow) item.getChildren().get(0);
            if (textFlow.getChildren().size() < 2) {
                setGraphic(item);
                return;
            }
            Text timestampAndSender = (Text) textFlow.getChildren().get(0);
            Text content = (Text) textFlow.getChildren().get(1);
            String timeStampText = timestampAndSender.getText();
            String messageText = content.getText();
            boolean isSent = content.getFill().equals(Color.YELLOW);

            HBox container = new HBox();
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            VBox bubble = new VBox(3);

            container.prefWidthProperty().bind(
                    getListView().widthProperty().multiply(0.9)
            );
            bubble.maxWidthProperty().bind(
                    getListView().widthProperty().multiply(0.8)
            );

            Label header = new Label(timeStampText);
            header.setFont(Font.font("System", FontWeight.BOLD, 11));
            header.setTextFill(Color.WHITE);
            header.setWrapText(true);

            Label message = new Label(messageText);
            message.setFont(Font.font("System", 14));
            message.setWrapText(true);
            message.maxWidthProperty().bind(
                    getListView().widthProperty().multiply(0.8)
            );

            String time = extractTime(timeStampText);
            Label timeLabel = new Label(time);
            timeLabel.setFont(Font.font("System", 9));
            timeLabel.setTextFill(Color.LIGHTGRAY);

            HBox messageFooter = new HBox(timeLabel);
            messageFooter.setAlignment(Pos.BOTTOM_RIGHT);

            bubble.getChildren().addAll(header, message, messageFooter);

            if (isSent) {
                container.getChildren().setAll(spacer, bubble);
                bubble.setBackground(new Background(new BackgroundFill(
                        Color.web("#075E54"), new CornerRadii(15, 0, 15, 15, false), Insets.EMPTY
                )));
                message.setTextFill(Color.YELLOW);
            } else {
                container.getChildren().setAll(bubble, spacer);
                bubble.setBackground(new Background(new BackgroundFill(
                        Color.web("#2A3942"), new CornerRadii(0, 15, 15, 15, false), Insets.EMPTY
                )));
                message.setTextFill(Color.LIGHTCORAL);
            }

            container.setPadding(new Insets(2, 5, 2, 5));
            bubble.setPadding(new Insets(4, 12, 4, 12));

            Object messageId = item.getProperties().get("messageId");
            if (messageId != null) {
                container.getProperties().put("messageId", messageId);
            }

            setGraphic(container);
        }

        private String extractTime(String timeStampText) {
            if (timeStampText.contains("]")) {
                String timestamp = timeStampText.substring(1, timeStampText.indexOf("]"));
                if (timestamp.contains(" ")) {
                    return timestamp.split(" ")[1];
                }
                return timestamp;
            }
            return "";
        }
    }
}

