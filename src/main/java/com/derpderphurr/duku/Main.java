package com.derpderphurr.duku;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    private final LevelContainer lc = new LevelContainer();

    @Override
    public void start(Stage primaryStage) throws Exception {
        VBox vb = new VBox();
        vb.getChildren().add(lc);
        primaryStage.setWidth(800);
        Playfield p = new Playfield(6,1);
        lc.setLevel(p);

        primaryStage.setScene(new Scene(vb));
        primaryStage.show();
    }
}