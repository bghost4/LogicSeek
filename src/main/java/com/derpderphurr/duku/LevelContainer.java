package com.derpderphurr.duku;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Objects;

public class LevelContainer extends Region {

    private final SimpleObjectProperty<Playfield> playfield = new SimpleObjectProperty<>();
    private final Label lblLevelSeed = new Label();
    private final Label lblTargetStats = new Label();
    private final Label lblMisses = new Label();
    private final Label lblScore = new Label();
    private final StackPane playfieldStackPane = new StackPane();

    public LevelContainer() {

        String cssUrl = Objects.requireNonNull(getClass().getResource("/LevelContainer.css")).toExternalForm();
        this.getStylesheets().add(cssUrl);

        lblLevelSeed.getStyleClass().add("seed-label");
        lblLevelSeed.setMaxWidth(Double.MAX_VALUE);

        lblTargetStats.getStyleClass().add("targetstats-label");
        lblTargetStats.setMaxWidth(Double.MAX_VALUE);

        lblScore.getStyleClass().add("score-label");
        lblScore.setMaxWidth(Double.MAX_VALUE);

        lblMisses.getStyleClass().add("misses-label");
        lblMisses.setMaxWidth(Double.MAX_VALUE);

        GridPane gp = new GridPane();

        GridPane.setHgrow(lblLevelSeed,Priority.ALWAYS);
        GridPane.setHgrow(lblTargetStats,Priority.ALWAYS);
        GridPane.setHgrow(lblMisses,Priority.ALWAYS);
        GridPane.setHgrow(lblScore,Priority.ALWAYS);

        gp.add(lblLevelSeed,0,0,2,1);
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

                lblTargetStats.textProperty().unbind();
                lblMisses.textProperty().unbind();
                lblScore.textProperty().unbind();

                playfieldStackPane.getChildren().add(nv);
                playfieldStackPane.getChildren().remove(ov);

                lblTargetStats.textProperty().bind(Bindings.format("Targets: %d/%d",nv.foundTargetsProperty(),nv.getSizeProperty()));
                lblMisses.textProperty().bind(Bindings.format("Misses: %d/%d",nv.missesProperty(),3));
                lblScore.textProperty().bind(Bindings.format("Score: %d",nv.scoreProperty()));
                nv.onLevelFailedProperty().set(Playfield::reset);
                nv.onLevelCompleteProperty().set(p -> this.setLevel(new Playfield(8,p.getSeed()+1)));
            }
        } );
    }


    public void setLevel(Playfield p) {
        GamePrefs.saveLastLevel(p.getSeed(), p.getSizeProperty().get());
        this.playfield.set(p);
    }
}
