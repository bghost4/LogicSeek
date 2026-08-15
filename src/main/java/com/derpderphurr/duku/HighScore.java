package com.derpderphurr.duku;

import javafx.scene.control.TableView;
import javafx.scene.layout.Region;

/**
 * a view to show you your scores, table should be autosortable by differnt fields,
 * should have a spot for average level time,
 */
public class HighScore extends Region {
    private final TableView<ScoreHistory.Score> scoreTableView = new TableView<>();

}
