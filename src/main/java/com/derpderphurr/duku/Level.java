package com.derpderphurr.duku;

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
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Level extends Region {
    private final GridPane gp = new GridPane();
    private final Tile[][] tiles;
    private final List<Tile> tileList;
    private final int size;
    private double gap = 5;
    private final long seed;
    private final Random rand;

    public static final Color[] REGION_COLORS = {
            Color.hsb(0,   0.45, 0.95), // pastel red
            Color.hsb(36,  0.45, 0.95), // pastel orange
            Color.hsb(72,  0.45, 0.95), // pastel yellow-green
            Color.hsb(108, 0.45, 0.95), // pastel green
            Color.hsb(144, 0.45, 0.95), // pastel spring green/teal
            Color.hsb(180, 0.45, 0.95), // pastel cyan
            Color.hsb(216, 0.45, 0.95), // pastel blue
            Color.hsb(252, 0.45, 0.95), // pastel indigo/violet
            Color.hsb(288, 0.45, 0.95), // pastel magenta
            Color.hsb(324, 0.45, 0.95), // pastel rose/pink
    };

    private void buildTargets() {
        //choose targets & verify level design
        clearTargets();
        List<Integer> unusedColumns = new ArrayList<>(IntStream.range(0, size).boxed().toList());
        List<Integer> unusedRows = new ArrayList<>(IntStream.range(0, size).boxed().toList());

        Collections.shuffle(unusedColumns,rand);
        Collections.shuffle(unusedRows,rand);

        for(int i=0; i < size; i++ ) {
            tiles[unusedColumns.get(i)][unusedRows.get(i)].setTarget(true);
        }
    }

    private void clearTargets() {
        tileList.forEach(t -> t.setTarget(false));
    }

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

    private void generateFill(List<Color> colors) {
        //fill in the colors
        //same colors must border on the top left bottom or right (a color may be a single tile)

        //seed each color at one of the (already-verified) targets, so every region is
        //guaranteed exactly one target without a separate per-color check afterward
        List<Tile> seeds = new ArrayList<>(tileList.stream().filter(Tile::isTarget).toList());
        Collections.shuffle(seeds, rand);

        //grow every region outward in lockstep so no two same-colored blobs can end up disconnected
        List<Deque<Tile>> frontiers = new ArrayList<>();
        for (int i = 0; i < colors.size() && i < seeds.size(); i++) {
            Tile seed = seeds.get(i);
            seed.setColor(colors.get(i));
            List<Tile> neighbors = getOrthogonalNeighbors(seed);
            Collections.shuffle(neighbors, rand);
            frontiers.add(new ArrayDeque<>(neighbors));
        }

        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < frontiers.size(); i++) {
                Deque<Tile> frontier = frontiers.get(i);
                Color color = colors.get(i);

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
                    List<Tile> neighbors = getOrthogonalNeighbors(next);
                    Collections.shuffle(neighbors, rand);
                    frontier.addAll(neighbors);
                    grew = true;
                }
            }
        }
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

    //Simulates the deductions a player is allowed to make (no guessing/backtracking) to check
    //that every target can be found by process of elimination alone
    private boolean isSolvableByElimination() {
        Set<Tile> candidates = new HashSet<>(tileList);
        Set<Tile> solved = new HashSet<>();

        boolean progress = true;
        while (progress) {
            progress = false;

            for (int i = 0; i < size; i++) {
                final int fi = i;
                progress |= trySingleton(candidates.stream().filter(t -> t.getRow() == fi).toList(), candidates, solved);
                progress |= trySingleton(candidates.stream().filter(t -> t.getCol() == fi).toList(), candidates, solved);
            }

            Map<Color, List<Tile>> byColor = candidates.stream().collect(Collectors.groupingBy(Tile::getColor));
            for (List<Tile> group : byColor.values()) {
                progress |= trySingleton(group, candidates, solved);
            }
            for (List<Tile> group : byColor.values()) {
                progress |= tryConfinement(group, candidates, Tile::getRow);
                progress |= tryConfinement(group, candidates, Tile::getCol);
            }
        }

        return solved.size() == size;
    }

    private void buildLevel() {

        buildTargets();
        int iterations = 1;
        while(!verifyTargets()) {
            buildTargets();
            iterations++;
        }
        System.out.printf("Took %d iterations to build level%n",iterations);

        //choose colors, then fill regions outward from the now-verified targets
        List<Color> palette = new ArrayList<>(Arrays.asList(REGION_COLORS));
        Collections.shuffle(palette, rand);
        List<Color> colors = palette.subList(0, Math.min(size, palette.size()));

        generateFill(colors);
        int fillIterations = 1;
        while (!isSolvableByElimination()) {
            clearFill();
            generateFill(colors);
            fillIterations++;
        }
        System.out.printf("Took %d iterations to build a fill solvable by elimination%n", fillIterations);
    }

    public Level(int size,long seed) {
        this.size = size;
        this.seed = seed;
        this.rand = new Random(seed);

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
                Tile t  = new Tile(x,y);
                tiles[x][y] = t;
                GridPane.setVgrow(t, Priority.ALWAYS);
                GridPane.setHgrow(t,Priority.ALWAYS);
                gp.add(t,x,y);
                tileList.add(t);
            }
        }

        buildLevel();

    }

    private Stream<Tile> getNeighbors(Tile root) {
        return tileList.stream().filter(root::isNeighbor);
    }

    private boolean verifyTargets() {
        //verify all rows / columns have a target
        for(int i=0; i < size; i++) {
            final int fi = i;
            boolean rowPass = tileList.stream().filter(t -> (t.getRow() == fi && t.isTarget())).count() == 1;
            boolean colPass = tileList.stream().filter(t -> (t.getCol() == fi && t.isTarget())).count() == 1;
            if (!rowPass || !colPass) { return false; }
        }

        //Check Neighbors of targets
        List<Tile> targets = tileList.stream().filter(Tile::isTarget).toList();
        if(targets.stream().anyMatch(t -> getNeighbors(t).filter(Tile::isTarget).count() > 1)) { return false; }

        //One target per color is now guaranteed by generateFill seeding each region at a target

        return true;
    }

}
