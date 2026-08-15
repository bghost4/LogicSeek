package com.derpderphurr.duku;

import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * a view to show you your scores, table should be autosortable by differnt fields,
 * should have a spot for average level time,
 */
public class HighScore extends Region {
    private final TableView<ScoreHistory.Score> scoreTableView = new TableView<>();
    private final TableColumn<ScoreHistory.Score,Long> tcSeed = new TableColumn<>("Seed");
    private final TableColumn<ScoreHistory.Score, LocalDateTime> tcDate= new TableColumn<>("Date");
    private final TableColumn<ScoreHistory.Score,Long> tcTime = new TableColumn<>("Time");
    private final TableColumn<ScoreHistory.Score,Double> tcDifficulty = new TableColumn<>("Hardness");
    private final TableColumn<ScoreHistory.Score,Integer> tcScore = new TableColumn<>("Score");
    private final TableColumn<ScoreHistory.Score,Integer> tcSize = new TableColumn<>("Size");

    private final Label lblAvgTime = new Label();

    public HighScore() {

        this.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);
        this.setPrefSize(800,600);

        //set up table column values
        tcDate.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().date()) );
        tcDifficulty.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().difficulty()));
        tcSeed.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().seed()));
        tcTime.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().completionTimeMillis()));
        tcScore.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().score()));
        tcSize.setCellValueFactory(cdf -> new ReadOnlyObjectWrapper<>(cdf.getValue().size()));

        VBox vb = new VBox();
        scoreTableView.getColumns().addAll(tcDate,tcScore,tcTime,tcSize,tcSeed,tcDifficulty);
        try {
            List<ScoreHistory.Score> slist = ScoreHistory.getAllScores().toList();
            scoreTableView.getItems().setAll(slist);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        vb.getChildren().add(scoreTableView);

        vb.prefWidthProperty().bind(this.widthProperty());
        vb.prefHeightProperty().bind(this.heightProperty());

        this.getChildren().add(vb);

    }

}
