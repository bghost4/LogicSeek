package com.derpderphurr.duku;

import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

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

    private List<Tile> getOrthogonalNeighbors(Tile root) {
        List<Tile> neighbors = new ArrayList<>(4);
        int col = root.getCol();
        int row = root.getRow();
        if (col > 0) neighbors.add(tiles[col - 1][row]);
        if (col < size - 1) neighbors.add(tiles[col + 1][row]);
        if (row > 0) neighbors.add(tiles[col][row - 1]);
        if (row < size - 1) neighbors.add(tiles[col][row + 1]);
        return neighbors;
    }

    // This is a multi-source flood fill: think of it as dropping one seed of paint per color
    // onto the board and letting all the puddles spread outward at the same time, one cell each
    // per round, until they've covered the whole grid and are all touching each other's edges.
    //
    // Each region gets its own "frontier" - a queue of cells it's allowed to try claiming next
    // (always cells that border a cell it already owns). Because a cell only ever enters a
    // region's frontier by bordering that region, the first time it actually gets claimed it is
    // guaranteed to be touching an existing cell of the same color - so a region can never end up
    // as two disconnected islands, only ever a single connected blob (or a lone cell, if its
    // frontier never wins a race - which is fine, a color may exist by itself).
    private void generateFill(List<Color> colors) {
        //fill in the colors
        //same colors must border on the top left bottom or right (a color may be a single tile)

        //seed each color at a random cell; the target set isn't known yet, it gets derived
        //afterward by solving whatever partition this produces
        List<Tile> seeds = new ArrayList<>(tileList);
        Collections.shuffle(seeds, rand);

        //claim one seed cell per color and stock its frontier with that seed's neighbors -
        //shuffling the neighbors means growth picks a random direction each time instead of
        //always expanding the same way, which is what makes the regions look organic/irregular
        //rather than plain squares
        List<Deque<Tile>> frontiers = new ArrayList<>();
        for (int i = 0; i < colors.size() && i < seeds.size(); i++) {
            Tile seed = seeds.get(i);
            seed.setColor(colors.get(i));
            List<Tile> neighbors = getOrthogonalNeighbors(seed);
            Collections.shuffle(neighbors, rand);
            frontiers.add(new ArrayDeque<>(neighbors));
        }

        //grow every region outward in lockstep, one cell per region per round, so regions expand
        //at roughly the same pace instead of one color racing ahead and swallowing the board
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < frontiers.size(); i++) {
                Deque<Tile> frontier = frontiers.get(i);
                Color color = colors.get(i);

                //pop candidates until we find one still unclaimed - a cell can sit in more than
                //one region's frontier if it borders two colors, so whichever region gets there
                //first wins and every other region just discards it when its turn comes around
                Tile next = null;
                while (!frontier.isEmpty()) {
                    Tile candidate = frontier.remove();
                    if (candidate.getColor().equals(Tile.DEFAULT_COLOR)) {
                        next = candidate;
                        break;
                    }
                }

                if (next != null) {
                    next.setColor(color);
                    //this cell's neighbors become new growth candidates for the SAME region only,
                    //which is exactly what keeps every color's cells connected to each other
                    List<Tile> neighbors = getOrthogonalNeighbors(next);
                    Collections.shuffle(neighbors, rand);
                    frontier.addAll(neighbors);
                    grew = true;
                }
            }
        }
        //once every frontier fails to produce an unclaimed cell in the same round, grew stays
        //false and we stop - since the grid is fully connected, that only happens once every
        //single cell has been claimed by some region
    }

    private void clearFill() {
        tileList.forEach(t -> t.setColor(Tile.DEFAULT_COLOR));
    }

    private boolean sharesConstraint(Tile a, Tile b) {
        return a.getRow() == b.getRow()
                || a.getCol() == b.getCol()
                || a.getColor().equals(b.getColor())
                || a.isNeighbor(b);
    }

    //If a row/column/region is down to one remaining candidate, that cell must be a target
    private boolean trySingleton(List<Tile> group, Set<Tile> candidates, Set<Tile> solved) {
        if (group.size() != 1) { return false; }
        Tile target = group.get(0);
        if (!candidates.contains(target)) { return false; }

        solved.add(target);
        candidates.remove(target);
        candidates.removeIf(other -> sharesConstraint(target, other));
        return true;
    }

    //If a region's remaining candidates are all on the same row/column, that row/column's target
    //has to come from this region, so every other region's candidate on that line can be eliminated
    private boolean tryConfinement(List<Tile> group, Set<Tile> candidates, ToIntFunction<Tile> lineOf) {
        if (group.isEmpty()) { return false; }
        int line = lineOf.applyAsInt(group.get(0));
        boolean confinedToLine = group.stream().mapToInt(lineOf).allMatch(l -> l == line);
        if (!confinedToLine) { return false; }

        Color color = group.get(0).getColor();
        List<Tile> eliminated = candidates.stream()
                .filter(t -> lineOf.applyAsInt(t) == line && !t.getColor().equals(color))
                .toList();
        if (eliminated.isEmpty()) { return false; }

        candidates.removeAll(eliminated);
        return true;
    }

    //Difficulty rating: singleton deductions are close to trivial for a player to spot, while
    //confinement deductions take real work, so confinement is weighted much heavier when scoring
    //how hard a level was to derive by elimination alone.
    private static final double SINGLETON_WEIGHT = 1.0;
    private static final double CONFINEMENT_WEIGHT = 3.0;

    private int singletonHits;
    private int confinementHits;

    //Derives which cells must be targets using only the deductions a player is allowed to make
    //(no guessing/backtracking): a row/column/region down to one candidate is forced, and a
    //region confined to one row/column rules out every other region's candidates on that line.
    //Runs purely off the color structure, so it works whether or not any targets are set yet.
    //Also tallies how many times each deduction rule fired, for use as a difficulty rating.
    private Set<Tile> solve() {
        Set<Tile> candidates = new HashSet<>(tileList);
        Set<Tile> solved = new HashSet<>();
        singletonHits = 0;
        confinementHits = 0;

        boolean progress = true;
        while (progress) {
            progress = false;

            for (int i = 0; i < size; i++) {
                final int fi = i;
                if (trySingleton(candidates.stream().filter(t -> t.getRow() == fi).toList(), candidates, solved)) { singletonHits++; progress = true; }
                if (trySingleton(candidates.stream().filter(t -> t.getCol() == fi).toList(), candidates, solved)) { singletonHits++; progress = true; }
            }

            Map<Color, List<Tile>> byColor = candidates.stream().collect(Collectors.groupingBy(Tile::getColor));
            for (List<Tile> group : byColor.values()) {
                if (trySingleton(group, candidates, solved)) { singletonHits++; progress = true; }
            }
            for (List<Tile> group : byColor.values()) {
                if (tryConfinement(group, candidates, Tile::getRow)) { confinementHits++; progress = true; }
                if (tryConfinement(group, candidates, Tile::getCol)) { confinementHits++; progress = true; }
            }
        }

        return solved;
    }

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

    private void buildLevel() {
        //choose colors, then keep growing fresh region partitions until one is fully
        //derivable by elimination alone - that derived set becomes the targets
        List<Color> palette = new ArrayList<>(Arrays.asList(REGION_COLORS));
        Collections.shuffle(palette, rand);
        List<Color> colors = palette.subList(0, Math.min(size, palette.size()));

        generateFill(colors);
        Set<Tile> targets = solve();
        int iterations = 1;
        while (targets.size() != size) {
            clearFill();
            generateFill(colors);
            targets = solve();
            iterations++;
        }
        targets.forEach(t -> t.setTarget(true));
        difficultyScore = singletonHits * SINGLETON_WEIGHT + confinementHits * CONFINEMENT_WEIGHT;
        System.out.printf("Took %d iterations to build a level solvable by elimination%n", iterations);
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
