package com.isna;

import com.isna.utility.FirebaseUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FirebaseUtil.initializeFirebase(); // Initialize Firebase connection
        Parent root = FXMLLoader.load(getClass().getResource("/com/isna/view/login.fxml"));
        primaryStage.setTitle("Note Taking App");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
