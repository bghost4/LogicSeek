package com.derpderphurr.duku;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.IOException;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    private final LevelContainer lc = new LevelContainer();

    private void showHighScore(ActionEvent e) {
        Dialog<Void> scoreDialog = new Dialog<>();

        HighScore hs = new HighScore();

        scoreDialog.getDialogPane().setContent(hs);

        scoreDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        scoreDialog.show();

    }

    private void resetHighScore(ActionEvent e) {
        try {
            ScoreHistory.stash();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        VBox vb = new VBox();

        MenuBar mb = new MenuBar();

        Menu mOptions = new Menu("Options");
            MenuItem miScore = new MenuItem("Score");
                miScore.setOnAction(this::showHighScore);

            MenuItem miAbout = new MenuItem("About");

        Menu mReset = new Menu("Reset");
        MenuItem miResetProgress = new MenuItem("Reset Game Progress");
        miResetProgress.setOnAction( e -> GamePrefs.resetLevel() );
        MenuItem miResetScore = new MenuItem("Reset High Score");
        miResetScore.setOnAction(this::resetHighScore);

        mOptions.getItems().addAll(miScore,miAbout);
        mReset.getItems().addAll(miResetProgress,miResetScore);
        mb.getMenus().addAll(mOptions,mReset);
        vb.getChildren().addAll(mb,lc);

        long seed = GamePrefs.getLastSeed(1);
        lc.generateLevel(seed);

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

        primaryStage.setTitle("Logiseek");

        primaryStage.getIcons().add(new Image(this.getClass().getResource("/icon.png").toExternalForm()));

        primaryStage.show();
    }
}