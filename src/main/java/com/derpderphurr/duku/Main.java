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

        long seed = GamePrefs.getLastSeed(1);
        int size = GamePrefs.getLastSize(6);
        Playfield p = new Playfield(size, seed);
        lc.setLevel(p);

        primaryStage.setScene(new Scene(vb));

        double x = GamePrefs.getWindowX(Double.NaN);
        double y = GamePrefs.getWindowY(Double.NaN);
        double height = GamePrefs.getWindowHeight(Double.NaN);
        if (!Double.isNaN(x)) primaryStage.setX(x);
        if (!Double.isNaN(y)) primaryStage.setY(y);
        if (!Double.isNaN(height)) primaryStage.setHeight(height);
        primaryStage.setWidth(GamePrefs.getWindowWidth(800));

        primaryStage.setOnCloseRequest(e -> GamePrefs.saveWindowBounds(
                primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight()));

        primaryStage.show();
    }
}