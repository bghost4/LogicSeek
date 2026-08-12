package com.derpderphurr.duku;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;

import java.util.Objects;
import java.util.Optional;

public class LevelContainer extends Region {

    private final SimpleObjectProperty<Level> playfield = new SimpleObjectProperty<>();
    private final Label lblLevelSeed = new Label();
    private final Label lblDifficulty = new Label();
    private final Label lblTargetStats = new Label();
    private final Label lblMisses = new Label();
    private final Label lblScore = new Label();
    private final StackPane playfieldStackPane = new StackPane();

    //Shown in playfieldStackPane while generateLevel's background Task is running, so a slow
    //(unlucky-seed) generation reads as "working" instead of a frozen board. Built once and
    //added/removed rather than recreated per generateLevel call.
    private final ProgressBar loadingProgress = new ProgressBar(ProgressIndicator.INDETERMINATE_PROGRESS);
    private final Label lblLoading = new Label("Generating level...");
    private final VBox loadingPane = new VBox(8, lblLoading, loadingProgress);

    public LevelContainer() {

        String cssUrl = Objects.requireNonNull(getClass().getResource("/LevelContainer.css")).toExternalForm();
        this.getStylesheets().add(cssUrl);

        loadingPane.setAlignment(Pos.CENTER);
        loadingProgress.setMaxWidth(200);

        lblLevelSeed.getStyleClass().add("seed-label");
        lblLevelSeed.setMaxWidth(Double.MAX_VALUE);

        lblDifficulty.getStyleClass().add("difficulty-label");
        lblDifficulty.setMaxWidth(Double.MAX_VALUE);

        lblTargetStats.getStyleClass().add("targetstats-label");
        lblTargetStats.setMaxWidth(Double.MAX_VALUE);

        lblScore.getStyleClass().add("score-label");
        lblScore.setMaxWidth(Double.MAX_VALUE);

        lblMisses.getStyleClass().add("misses-label");
        lblMisses.setMaxWidth(Double.MAX_VALUE);

        GridPane gp = new GridPane();

        GridPane.setHgrow(lblLevelSeed,Priority.ALWAYS);
        GridPane.setHgrow(lblDifficulty,Priority.ALWAYS);
        GridPane.setHgrow(lblTargetStats,Priority.ALWAYS);
        GridPane.setHgrow(lblMisses,Priority.ALWAYS);
        GridPane.setHgrow(lblScore,Priority.ALWAYS);

        gp.add(lblLevelSeed,0,0);
        gp.add(lblDifficulty,1,0);
        gp.add(lblTargetStats,0,1);
        gp.add(lblMisses,1,1);
        gp.add(lblScore,0,2,2,1);
        GridPane.setHgrow(playfieldStackPane, Priority.ALWAYS);
        GridPane.setVgrow(playfieldStackPane, Priority.ALWAYS);

        this.prefWidth(800);

        gp.prefWidthProperty().bind(this.widthProperty());
        gp.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);

        gp.add(playfieldStackPane,0,3,2,1);

        this.getChildren().add(gp);

        //Attach Code
        playfield.addListener( (ob,ov,nv) -> {
            if(nv != null) {
                lblLevelSeed.setText(String.format("LEVEL SEED: %d",nv.getSeed()));
                lblDifficulty.setText(String.format("Difficulty: %.1f",nv.getDifficultyScore()));

                lblTargetStats.textProperty().unbind();
                lblMisses.textProperty().unbind();
                lblScore.textProperty().unbind();

                playfieldStackPane.getChildren().add(nv);
                playfieldStackPane.getChildren().remove(ov);

                lblTargetStats.textProperty().bind(Bindings.format("Targets: %d/%d",nv.foundTargetsProperty(),nv.getSizeProperty()));
                lblMisses.textProperty().bind(Bindings.format("Misses: %d/%d",nv.missesProperty(),3));
                lblScore.textProperty().bind(Bindings.format("Score: %d",nv.scoreProperty()));
                nv.onLevelFailedProperty().set(Level::reset);
                nv.onLevelCompleteProperty().set(p -> {
                    ScoreHistory.recordCompletion(p.getSeed(), p.getSizeProperty().get(), p.scoreProperty().get(), p.getElapsedMillis());
                    this.generateLevel(p.getSeed() + 1);
                });
            }
        } );
    }


    public void setLevel(Level p) {
        GamePrefs.saveLastSeed(p.getSeed());
        this.playfield.set(p);
    }

    //Generation is the expensive part (can take anywhere from milliseconds to seconds depending
    //on how unlucky the seed's reroll count is - see PuzzleGenerator), so it runs on a background
    //Task instead of blocking the FX thread. Task's state-change handlers (setOnSucceeded here)
    //are dispatched back onto the FX thread automatically, so building the Level and swapping it
    //in via setLevel is still safe to do directly in the callback.
    //
    //generateAnchoredParallel gives up on a seed after MAX_ITERATIONS_PER_SEED attempts (see
    //PuzzleGenerator) rather than retrying it forever, returning empty - when that happens this
    //just moves on to the next seed and tries again, recomputing size for it via
    //Level.sizeForSeed each time (a seed and its size always have to be derived together, or the
    //"seed alone reproduces the level" guarantee breaks for whichever seed actually gets used).
    public void generateLevel(long seed) {
        playfieldStackPane.getChildren().add(loadingPane);

        Task<PuzzleGenerator.Result> task = new Task<>() {
            @Override
            protected PuzzleGenerator.Result call() {
                long trySeed = seed;
                Optional<PuzzleGenerator.Result> result;
                do {
                    int size = Level.sizeForSeed(trySeed);
                    result = PuzzleGenerator.generateAnchoredParallel(size, trySeed);
                    trySeed++;
                } while (result.isEmpty());
                return result.get();
            }
        };
        task.setOnSucceeded(e -> {
            playfieldStackPane.getChildren().remove(loadingPane);
            setLevel(new Level(task.getValue()));
        });

        Thread thread = new Thread(task, "level-generator");
        thread.setDaemon(true);
        thread.start();
    }
}
