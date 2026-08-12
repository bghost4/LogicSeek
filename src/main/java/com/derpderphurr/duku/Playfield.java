package com.derpderphurr.duku;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class Playfield extends Region {
    private final GridPane gp = new GridPane();
    private final Tile[][] tiles;
    private final List<Tile> tileList;
    private final int size;
    private long levelTimer;
    private long lastFindTime;
    private double multiplier = 1.0;
    private int comboStreak = 0;
    private double difficultyScore;
    private double gap = 5;
    private final long seed;
    private final Random rand;

    private static final int BASE_POINTS = 100;
    private static final double PER_CELL_WINDOW_MS = 3000;
    private static final double MIN_WINDOW_FRACTION = 0.3;
    private static final double MAX_MULTIPLIER = 2.5;

    //Board size is derived from the seed rather than stored separately, so a seed alone is enough
    //to reproduce a level - only MIN_SIZE..MAX_SIZE are actually used, so the game only ever needs
    //to persist one number (see GamePrefs.getLastSeed / sizeForSeed below).
    public static final int MIN_SIZE = 6;
    public static final int MAX_SIZE = 10;

    public static int sizeForSeed(long seed) {
        return MIN_SIZE + new Random(seed).nextInt(MAX_SIZE - MIN_SIZE + 1);
    }

    // Derived once per level from size: the window shrinks each find so the last target of the
    // level always gets MIN_WINDOW_FRACTION of the first target's window, and a flawless run
    // always tops out at exactly MAX_MULTIPLIER by the final target, regardless of level size.
    private double initialWindowMs;
    private double decayRate;
    private double comboStep;

    private final SimpleIntegerProperty score = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty foundTargets = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty misses = new SimpleIntegerProperty(0);
    private final ReadOnlyIntegerWrapper sizeProp;
    
    public static final Color[] REGION_COLORS = {
            Color.web("#e6194b"), // red
            Color.web("#f58231"), // orange
            Color.web("#ffe119"), // yellow
            Color.web("#bfef45"), // lime
            Color.web("#3cb44b"), // green
            Color.web("#469990"), // teal
            Color.web("#42d4f4"), // cyan
            Color.web("#4363d8"), // blue
            Color.web("#000075"), // navy
            Color.web("#911eb4"), // purple
            Color.web("#f032e6"), // magenta
            Color.web("#9a6324"), // brown
    };

    public void reset() {
        tileList.forEach(Tile::reset);
        foundTargets.set(0);
        misses.set(0);
        score.set(0);
        multiplier = 1.0;
        comboStreak = 0;
        levelTimer = System.currentTimeMillis();
        lastFindTime = levelTimer;
    }

    //Generation/solving itself lives in PuzzleGenerator (no JavaFX dependency, so it can also run
    //headless in DifficultySimulator) - this just asks it for a board and paints the result onto
    //this level's Tiles.
    private void buildLevel() {
        int colorCount = Math.min(size, REGION_COLORS.length);
        // generateAnchored (one color deliberately confined to a line, everything else random)
        // measured consistently faster than plain generate() across sizes 6-10 - roughly 1.5-3.4x
        // fewer median reroll iterations, up to 5x fewer on the worst case, and about 2x less
        // wall-clock time - without a meaningful difficulty-score difference. See PuzzleGenerator
        // for both strategies; generate() is kept for reference/comparison in DifficultySimulator.
        PuzzleGenerator.Result result = PuzzleGenerator.generateAnchored(size, seed, colorCount);

        //which hex color represents which color id is purely cosmetic and doesn't affect
        //difficulty, so it's picked here rather than inside the JavaFX-free generator
        List<Color> palette = new ArrayList<>(Arrays.asList(REGION_COLORS));
        Collections.shuffle(palette, rand);
        List<Color> colors = palette.subList(0, colorCount);

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Tile t = tiles[col][row];
                t.setColor(colors.get(result.colorGrid()[row][col]));
                if (result.targetGrid()[row][col]) { t.setTarget(true); }
            }
        }

        difficultyScore = result.difficultyScore();
        System.out.printf("Took %d iterations to build a level solvable by elimination "
                        + "(singleton=%d confinement=%d sharedNeighbor=%d lockedSet=%d, score=%.1f)%n",
                result.iterations(), result.singletonHits(), result.confinementHits(),
                result.sharedNeighborHits(), result.lockedSetHits(), difficultyScore);
    }

    public void targetMissed() {
        this.misses.set(misses.get()+1);
        comboStreak = 0;
        multiplier = 1.0;
    }

    public void targetFound() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastFindTime;
        double window = initialWindowMs * Math.pow(decayRate, foundTargets.get());
        double speedBonus = Math.max(0, BASE_POINTS * (1 - elapsed / window));
        score.set(score.get() + (int) Math.round((BASE_POINTS + speedBonus) * multiplier));

        if (elapsed <= window) {
            comboStreak++;
            multiplier = Math.min(MAX_MULTIPLIER, 1.0 + comboStreak * comboStep);
        }

        lastFindTime = now;
        this.foundTargets.set(foundTargets.get()+1);
    }

    private final SimpleObjectProperty<Consumer<Playfield>> onLevelComplete = new SimpleObjectProperty<>(l -> {});
    private final SimpleObjectProperty<Consumer<Playfield>> onLevelFailed = new SimpleObjectProperty<>(l -> {});

    public ObjectProperty<Consumer<Playfield>> onLevelCompleteProperty() { return onLevelComplete; }
    public ObjectProperty<Consumer<Playfield>> onLevelFailedProperty() { return onLevelFailed; }

    public Playfield(long seed) {
        this(sizeForSeed(seed), seed);
    }

    public Playfield(int size, long seed) {
        this.size = size;
        this.seed = seed;
        this.rand = new Random(seed);

        this.initialWindowMs = PER_CELL_WINDOW_MS * size;
        this.decayRate = size > 1 ? Math.pow(MIN_WINDOW_FRACTION, 1.0 / (size - 1)) : 1.0;
        this.comboStep = (MAX_MULTIPLIER - 1.0) / size;

        tileList = new ArrayList<>(size*size);
        this.getChildren().add(gp);
        gp.setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);

        this.setWidth(800);
        this.setHeight(800);

        gp.prefWidthProperty().bind(this.widthProperty());
        gp.prefHeightProperty().bind(this.widthProperty());
        gp.setHgap(gap);
        gp.setVgap(gap);
        gp.setPadding(new Insets(gap,gap,gap,gap));

        this.prefHeightProperty().bind(this.widthProperty());

        tiles = new Tile[size][size];

        for(int x=0; x < size; x++ ){
            for(int y=0; y < size; y++) {
                Tile t  = new Tile(this,x,y);
                tiles[x][y] = t;
                GridPane.setVgrow(t, Priority.ALWAYS);
                GridPane.setHgrow(t,Priority.ALWAYS);
                gp.add(t,x,y);
                tileList.add(t);
            }
        }

        buildLevel();
        this.levelTimer = System.currentTimeMillis();

        //set up listeners for misses and targets
        foundTargets.addListener(il -> { if(foundTargets.get() == size) { onLevelComplete.get().accept(this); } } );
        misses.addListener( il -> { if(misses.get() > 2){ onLevelFailed.get().accept(this); } });
        sizeProp = new ReadOnlyIntegerWrapper(size);
    }

    public long getSeed() {
        return seed;
    }

    public long getElapsedMillis() {
        return System.currentTimeMillis() - levelTimer;
    }

    public ReadOnlyIntegerProperty getSizeProperty() {
        return sizeProp;
    }

    public ReadOnlyIntegerProperty missesProperty() { return misses; }
    public ReadOnlyIntegerProperty foundTargetsProperty() { return foundTargets; }

    public ReadOnlyIntegerProperty scoreProperty() {
        return score;
    }

    public double getDifficultyScore() {
        return difficultyScore;
    }
}
