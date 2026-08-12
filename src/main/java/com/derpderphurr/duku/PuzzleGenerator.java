package com.derpderphurr.duku;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

//JavaFX-free twin of Playfield's puzzle generation/solving logic - a plain function of
//(size, seed, colorCount) to a Result. Exists so the difficulty model can be run in a tight loop
//(see DifficultySimulator) without booting the UI, and so Playfield and any simulation always
//agree, since both call the exact same code.
public final class PuzzleGenerator {

    private PuzzleGenerator() {}

    public record Result(int size, long seed, int colorCount,
                          int[][] colorGrid, boolean[][] targetGrid,
                          int iterations, int singletonHits, int confinementHits,
                          int sharedNeighborHits, int lockedSetHits, double difficultyScore) {}

    private static final int UNCLAIMED = -1;

    //Plain data twin of Tile: a fixed board position plus a mutable color id assigned during
    //generation. No JavaFX, no rendering, no target/found/crossed UI state.
    private static final class Cell {
        final int row, col;
        int colorId = UNCLAIMED;

        Cell(int row, int col) {
            this.row = row;
            this.col = col;
        }

        boolean isNeighbor(Cell other) {
            return Math.abs(row - other.row) < 2 && Math.abs(col - other.col) < 2;
        }
    }

    //Difficulty rating: each deduction is weighted by roughly how hard it is for a player to spot
    //by inspection. Singletons are close to trivial (count down to one). Confinement and shared-
    //neighbor both take real work - one color/line at a time, or eyeballing a shared neighbor -
    //so they're weighted the same. Locked sets are the hardest, since they require tracking
    //several colors and lines simultaneously. These are flat per-hit weights, not scaled by how
    //many colors/lines a given locked-set hit spanned - a reasonable starting point, tune from here.
    private static final double SINGLETON_WEIGHT = 1.0;
    private static final double CONFINEMENT_WEIGHT = 3.0;
    private static final double SHARED_NEIGHBOR_WEIGHT = 3.0;
    private static final double LOCKED_SET_WEIGHT = 6.0;

    //Any seed-selection strategy that can be dropped into generateWith - it just needs to leave
    //`cells` partitioned by colorId (UNCLAIMED is not allowed to survive) when it returns.
    @FunctionalInterface
    private interface FillStrategy {
        void apply(List<Cell> cells, Cell[][] grid, int colorCount, Random rand);
    }

    //Random-seed rejection sampling: pick random cells as seeds, grow, and hope the resulting
    //partition happens to be solvable by elimination (checked/rerolled by generateWith).
    public static Result generate(int size, long seed, int colorCount) {
        return generateWith(size, seed, colorCount, PuzzleGenerator::generateFill);
    }

    //Constructive alternative: pick a valid target placement first, then grow each region from
    //its own target (see growFromTargets) so the reroll loop never has to fight a partition with
    //no valid solution at all - it may still reroll if elimination alone can't derive that
    //solution, same as the random strategy.
    //
    //MEASURED AND REJECTED: tested at size 4, this failed over 1.2 million reroll attempts in 30
    //seconds (generate()'s worst case at that size was 10). Anchoring every color to an evenly-
    //spread target permutation produces regions that are MORE symmetric than random seeding, and
    //every deduction technique in Solver depends on finding lopsided structure to exploit -
    //removing all the randomness removed exactly the asymmetry elimination needs, so boards came
    //out almost universally ambiguous instead of easier to solve. Kept here as a documented
    //negative result; excluded from DifficultySimulator's sweep since it would hang it. See
    //generateAnchored below for the strategy that grew out of this lesson.
    public static Result generateConstructive(int size, long seed, int colorCount) {
        return generateWith(size, seed, colorCount, PuzzleGenerator::growFromTargets);
    }

    //Guarantees exactly ONE deliberate foothold instead of constructing the whole board: one
    //color's entire region is forced onto a single line (see growWithAnchor), so tryConfinement
    //fires on it unconditionally as solve()'s very first move. Everything else - every other
    //color's seed and growth - stays exactly as random as generate()/generateFill. The idea is
    //that solve()'s existing cascade (a solved target clears its row/col/color/neighbors via
    //sharesConstraint, which can trigger further deductions) can carry the rest from that one
    //guaranteed start, without needing the entire partition to be favorable by luck the way
    //generate() does, and without removing the asymmetry generateConstructive's approach did.
    public static Result generateAnchored(int size, long seed, int colorCount) {
        return generateWith(size, seed, colorCount, PuzzleGenerator::growWithAnchor);
    }

    //Shared driver: build the grid, keep asking the given fill strategy for a fresh partition
    //until one is fully derivable by elimination alone, then package up the result. The two
    //public generate*() methods differ only in which FillStrategy they pass in here.
    private static Result generateWith(int size, long seed, int colorCount, FillStrategy fill) {
        Random rand = new Random(seed);
        List<Cell> cells = new ArrayList<>(size * size);
        Cell[][] grid = new Cell[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Cell c = new Cell(row, col);
                grid[row][col] = c;
                cells.add(c);
            }
        }

        Solver solver = new Solver(size, cells);
        Set<Cell> targets;
        int iterations = 1;
        fill.apply(cells, grid, colorCount, rand);
        targets = solver.solve();
        while (targets.size() != size) {
            clearFill(cells);
            fill.apply(cells, grid, colorCount, rand);
            targets = solver.solve();
            iterations++;
        }

        int[][] colorGrid = new int[size][size];
        boolean[][] targetGrid = new boolean[size][size];
        for (Cell c : cells) {
            colorGrid[c.row][c.col] = c.colorId;
        }
        for (Cell t : targets) {
            targetGrid[t.row][t.col] = true;
        }

        double difficultyScore = solver.singletonHits * SINGLETON_WEIGHT
                + solver.confinementHits * CONFINEMENT_WEIGHT
                + solver.sharedNeighborHits * SHARED_NEIGHBOR_WEIGHT
                + solver.lockedSetHits * LOCKED_SET_WEIGHT;

        return new Result(size, seed, colorCount, colorGrid, targetGrid, iterations,
                solver.singletonHits, solver.confinementHits, solver.sharedNeighborHits,
                solver.lockedSetHits, difficultyScore);
    }

    private static List<Cell> getOrthogonalNeighbors(Cell[][] grid, int size, Cell root) {
        List<Cell> neighbors = new ArrayList<>(4);
        int row = root.row, col = root.col;
        if (col > 0) neighbors.add(grid[row][col - 1]);
        if (col < size - 1) neighbors.add(grid[row][col + 1]);
        if (row > 0) neighbors.add(grid[row - 1][col]);
        if (row < size - 1) neighbors.add(grid[row + 1][col]);
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
    //
    // Seed placement and growth are deliberately split: this method only picks WHICH cells the
    // colors start from (random cells here); growLockstep does the actual spreading and is shared
    // with growFromTargets below, whose seeds are chosen very differently. Keeping growth
    // identical between strategies is what makes comparing them meaningful.
    private static void generateFill(List<Cell> cells, Cell[][] grid, int colorCount, Random rand) {
        List<Cell> seeds = new ArrayList<>(cells);
        Collections.shuffle(seeds, rand);
        seeds = seeds.subList(0, Math.min(colorCount, seeds.size()));
        for (int i = 0; i < seeds.size(); i++) {
            seeds.get(i).colorId = i;
        }
        growLockstep(seeds, grid, rand);
    }

    //Picks a uniformly-random permutation of columns and rejects it until it satisfies the
    //no-touching constraint between consecutive rows (no |col[i+1]-col[i]| == 1, since row
    //distance is always exactly 1 between consecutive entries and same-row/same-column touching
    //can't happen in a permutation). This is exactly Hertzsprung's problem (OEIS A002464) - about
    //13% of permutations qualify at the sizes this game uses, so a handful of shuffles reliably
    //finds one; this part of generation was never the expensive one.
    private static List<Integer> randomValidPermutation(int size, Random rand) {
        List<Integer> base = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            base.add(i);
        }
        List<Integer> candidate;
        do {
            candidate = new ArrayList<>(base);
            Collections.shuffle(candidate, rand);
        } while (!isNonAdjacent(candidate));
        return candidate;
    }

    private static boolean isNonAdjacent(List<Integer> perm) {
        for (int i = 0; i + 1 < perm.size(); i++) {
            if (Math.abs(perm.get(i + 1) - perm.get(i)) == 1) {
                return false;
            }
        }
        return true;
    }

    //Constructive alternative to generateFill: instead of seeding from random cells and hoping
    //the resulting partition happens to have a valid solution, pick a valid target placement
    //first (randomValidPermutation) and seed each region at its own target cell. This guarantees
    //the partition has at least one valid solution - the one just picked - before elimination is
    //even attempted, which random seeding can't promise. It does NOT guarantee solve() can derive
    //that solution by elimination alone; growLockstep and the reroll loop in generateWith are
    //otherwise identical to the random strategy.
    private static void growFromTargets(List<Cell> cells, Cell[][] grid, int colorCount, Random rand) {
        List<Integer> targetCols = randomValidPermutation(grid.length, rand);
        List<Integer> colorOrder = new ArrayList<>(colorCount);
        for (int i = 0; i < colorCount; i++) {
            colorOrder.add(i);
        }
        Collections.shuffle(colorOrder, rand); // which color grows from which target row

        List<Cell> seeds = new ArrayList<>(colorCount);
        for (int row = 0; row < colorCount; row++) {
            Cell seed = grid[row][targetCols.get(row)];
            seed.colorId = colorOrder.get(row);
            seeds.add(seed);
        }
        growLockstep(seeds, grid, rand);
    }

    //Claims a short, straight run of cells (1-2, capped so at least one other color keeps a cell
    //on the line) for one color, entirely within one randomly chosen row or column, and never
    //adds that color to growLockstep's seed list - so it can never grow beyond that run. The
    //result: that color's ENTIRE region is, by construction, confined to one line, so
    //tryConfinement fires on it unconditionally as solve()'s very first move. Every other color
    //still seeds at a random cell and grows completely unrestricted via the same growLockstep
    //generateFill uses - only one guaranteed foothold is added, nothing else about the random
    //process changes.
    private static void growWithAnchor(List<Cell> cells, Cell[][] grid, int colorCount, Random rand) {
        boolean anchorIsRow = rand.nextBoolean();
        int line = rand.nextInt(grid.length);
        int runLength = Math.max(1, Math.min(2, grid.length - 1));
        int start = rand.nextInt(grid.length - runLength + 1);
        int anchorColor = rand.nextInt(colorCount);

        List<Cell> anchorCells = new ArrayList<>(runLength);
        for (int i = 0; i < runLength; i++) {
            Cell c = anchorIsRow ? grid[line][start + i] : grid[start + i][line];
            c.colorId = anchorColor;
            anchorCells.add(c);
        }

        List<Cell> pool = new ArrayList<>(cells);
        pool.removeAll(anchorCells);
        Collections.shuffle(pool, rand);

        List<Cell> seeds = new ArrayList<>(colorCount - 1);
        int nextColor = 0;
        for (Cell seed : pool) {
            if (nextColor == anchorColor) { nextColor++; }
            if (nextColor >= colorCount || seeds.size() == colorCount - 1) { break; }
            seed.colorId = nextColor;
            seeds.add(seed);
            nextColor++;
        }

        growLockstep(seeds, grid, rand);
    }

    //Grows every region outward from its already-colored seed cell, one cell per region per
    //round, so regions expand at roughly the same pace instead of one color racing ahead and
    //swallowing the board. Shared by every seed-selection strategy.
    private static void growLockstep(List<Cell> seeds, Cell[][] grid, Random rand) {
        int size = grid.length;
        List<Deque<Cell>> frontiers = new ArrayList<>(seeds.size());
        List<Integer> colorIds = new ArrayList<>(seeds.size());
        for (Cell seed : seeds) {
            colorIds.add(seed.colorId);
            List<Cell> neighbors = getOrthogonalNeighbors(grid, size, seed);
            Collections.shuffle(neighbors, rand);
            frontiers.add(new ArrayDeque<>(neighbors));
        }

        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < frontiers.size(); i++) {
                Deque<Cell> frontier = frontiers.get(i);
                int colorId = colorIds.get(i);

                //pop candidates until we find one still unclaimed - a cell can sit in more than
                //one region's frontier if it borders two colors, so whichever region gets there
                //first wins and every other region just discards it when its turn comes around
                Cell next = null;
                while (!frontier.isEmpty()) {
                    Cell candidate = frontier.remove();
                    if (candidate.colorId == UNCLAIMED) {
                        next = candidate;
                        break;
                    }
                }

                if (next != null) {
                    next.colorId = colorId;
                    List<Cell> neighbors = getOrthogonalNeighbors(grid, size, next);
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

    private static void clearFill(List<Cell> cells) {
        cells.forEach(c -> c.colorId = UNCLAIMED);
    }

    //Holds solver state (counters) for one generate() call's worth of solve() attempts.
    private static final class Solver {
        private final int size;
        private final List<Cell> cells;
        private int singletonHits;
        private int confinementHits;
        private int sharedNeighborHits;
        private int lockedSetHits;

        Solver(int size, List<Cell> cells) {
            this.size = size;
            this.cells = cells;
        }

        private boolean sharesConstraint(Cell a, Cell b) {
            return a.row == b.row
                    || a.col == b.col
                    || a.colorId == b.colorId
                    || a.isNeighbor(b);
        }

        //If a row/column/region is down to one remaining candidate, that cell must be a target
        private boolean trySingleton(List<Cell> group, Set<Cell> candidates, Set<Cell> solved) {
            if (group.size() != 1) { return false; }
            Cell target = group.get(0);
            if (!candidates.contains(target)) { return false; }

            solved.add(target);
            candidates.remove(target);
            candidates.removeIf(other -> sharesConstraint(target, other));
            return true;
        }

        //If a region's remaining candidates are all on the same row/column, that row/column's
        //target has to come from this region, so every other region's candidate on that line can
        //be eliminated. This is the size-1 case of tryLockedSets below (one color confined to one
        //line); kept as its own method because it's the common case and doesn't need the
        //combination search.
        private boolean tryConfinement(List<Cell> group, Set<Cell> candidates, ToIntFunction<Cell> lineOf) {
            if (group.isEmpty()) { return false; }
            int line = lineOf.applyAsInt(group.get(0));
            boolean confinedToLine = group.stream().mapToInt(lineOf).allMatch(l -> l == line);
            if (!confinedToLine) { return false; }

            int colorId = group.get(0).colorId;
            List<Cell> eliminated = candidates.stream()
                    .filter(t -> lineOf.applyAsInt(t) == line && t.colorId != colorId)
                    .toList();
            if (eliminated.isEmpty()) { return false; }

            candidates.removeAll(eliminated);
            return true;
        }

        //Generalizes tryConfinement from "1 color confined to 1 line" to "N colors confined to N
        //lines": if some subset of colors' combined remaining candidates only touch as many lines
        //as there are colors in the subset, those lines are fully spoken for by that subset - none
        //of their targets can belong to any other color, so every other color's candidate on those
        //lines can be eliminated. Spotting this takes real work since it means holding several
        //colors' and lines' candidates in mind at once, unlike confinement which is just one color
        //at a time. Sizes 2..colors.size()-1 only: size 1 is tryConfinement, and the full color set
        //"confining" to every line on the board eliminates nothing.
        private boolean tryLockedSets(Map<Integer, List<Cell>> byColor, Set<Cell> candidates, ToIntFunction<Cell> lineOf) {
            List<Integer> colors = new ArrayList<>(byColor.keySet());
            for (int k = 2; k < colors.size(); k++) {
                for (List<Integer> subset : combinations(colors, k)) {
                    List<Cell> combined = subset.stream()
                            .flatMap(c -> byColor.getOrDefault(c, List.of()).stream())
                            .toList();
                    if (combined.isEmpty()) { continue; }

                    Set<Integer> lines = combined.stream().map(lineOf::applyAsInt).collect(Collectors.toSet());
                    if (lines.size() != k) { continue; }

                    Set<Integer> subsetColors = new HashSet<>(subset);
                    List<Cell> eliminated = candidates.stream()
                            .filter(t -> lines.contains(lineOf.applyAsInt(t)) && !subsetColors.contains(t.colorId))
                            .toList();
                    if (eliminated.isEmpty()) { continue; }

                    candidates.removeAll(eliminated);
                    return true;
                }
            }
            return false;
        }

        //If every remaining candidate of a not-yet-solved unit (a row, column, or color) touches
        //the same cell, that cell can never be a target: whichever candidate the unit eventually
        //resolves to, the shared cell would end up an immediate neighbor of it, breaking the
        //no-touching rule. This is the proactive form of the neighbor-clearing tryLockedSets/
        //trySingleton already do once a target is confirmed - here it fires before anything in the
        //unit is confirmed at all.
        private boolean trySharedNeighbor(List<Cell> group, Set<Cell> candidates) {
            if (group.size() < 2) { return false; }
            List<Cell> commonNeighbors = candidates.stream()
                    .filter(c -> group.stream().allMatch(g -> !g.equals(c) && g.isNeighbor(c)))
                    .toList();
            if (commonNeighbors.isEmpty()) { return false; }

            candidates.removeAll(commonNeighbors);
            return true;
        }

        //Derives which cells must be targets using only the deductions a player is allowed to make
        //(no guessing/backtracking). The puzzle has three constraint types - row, column, and
        //color - each of which must contain exactly one target, so every technique below is
        //applied once per constraint type. In rough order of how easy each is to spot:
        //  - singleton: a row/column/color down to one candidate is forced.
        //  - confinement / locked sets: a color (or N colors together) confined to a line (or N
        //    lines) rules out every other color's candidates on those lines.
        //  - shared neighbor: a cell touching every remaining candidate of a unit can never be a
        //    target itself, regardless of which candidate the unit resolves to.
        //Runs purely off the color structure, so it works whether or not any targets are set yet.
        //Also tallies how many times each deduction rule fired, for use as a difficulty rating.
        Set<Cell> solve() {
            Set<Cell> candidates = new HashSet<>(cells);
            Set<Cell> solved = new HashSet<>();
            singletonHits = 0;
            confinementHits = 0;
            sharedNeighborHits = 0;
            lockedSetHits = 0;

            boolean progress = true;
            while (progress) {
                progress = false;

                for (int i = 0; i < size; i++) {
                    final int fi = i;
                    if (trySingleton(candidates.stream().filter(t -> t.row == fi).toList(), candidates, solved)) { singletonHits++; progress = true; }
                    if (trySingleton(candidates.stream().filter(t -> t.col == fi).toList(), candidates, solved)) { singletonHits++; progress = true; }
                    if (trySharedNeighbor(candidates.stream().filter(t -> t.row == fi).toList(), candidates)) { sharedNeighborHits++; progress = true; }
                    if (trySharedNeighbor(candidates.stream().filter(t -> t.col == fi).toList(), candidates)) { sharedNeighborHits++; progress = true; }
                }

                Map<Integer, List<Cell>> byColor = candidates.stream().collect(Collectors.groupingBy(c -> c.colorId));
                for (List<Cell> group : byColor.values()) {
                    if (trySingleton(group, candidates, solved)) { singletonHits++; progress = true; }
                    if (trySharedNeighbor(group, candidates)) { sharedNeighborHits++; progress = true; }
                }
                for (List<Cell> group : byColor.values()) {
                    if (tryConfinement(group, candidates, c -> c.row)) { confinementHits++; progress = true; }
                    if (tryConfinement(group, candidates, c -> c.col)) { confinementHits++; progress = true; }
                }
                if (tryLockedSets(byColor, candidates, c -> c.row)) { lockedSetHits++; progress = true; }
                if (tryLockedSets(byColor, candidates, c -> c.col)) { lockedSetHits++; progress = true; }
            }

            return solved;
        }
    }

    //Plain subset generator (all k-element subsets of items, order-independent) used to search
    //color combinations in tryLockedSets. Color counts are capped at REGION_COLORS.length (12),
    //so this is cheap even though it's combinatorial.
    private static List<List<Integer>> combinations(List<Integer> items, int k) {
        List<List<Integer>> result = new ArrayList<>();
        combinationsInto(items, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combinationsInto(List<Integer> items, int k, int start, List<Integer> current, List<List<Integer>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            combinationsInto(items, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
