package com.derpderphurr.duku;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

//JavaFX-free twin of Playfield's puzzle generation/solving logic - a plain function of
//(size, seed) to a Result. Exists so the difficulty model can be run in a tight loop
//(see DifficultySimulator) without booting the UI, and so Playfield and any simulation always
//agree, since both call the exact same code.
//
//Board size doubles as the color count: the puzzle needs exactly one target per row, one per
//column, and one per color, so a size-N board can only ever be solvable with exactly N colors -
//there's no such thing as a valid board where they differ. That's why colorCount isn't a separate
//parameter anywhere in this class; keeping it independent invites a caller to pass a mismatched
//value, which isn't just wrong, it's unsolvable - the reroll loop in generateWith/
//generateWithParallel would spin forever looking for a solution that can't exist.
public final class PuzzleGenerator {

    private PuzzleGenerator() {}

    public record Result(int size, long seed,
                          int[][] colorGrid, boolean[][] targetGrid,
                          int iterations, int singletonHits, int confinementHits,
                          int sharedNeighborHits, int lockedSetHits, double difficultyScore) {}

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
        void apply(List<Cell> cells, Cell[][] grid, Random rand);
    }

    //Random-seed rejection sampling: pick random cells as seeds, grow, and hope the resulting
    //partition happens to be solvable by elimination (checked/rerolled by generateWith).
    public static Result generate(int size, long seed) {
        return generateWith(size, seed, PuzzleGenerator::generateFill);
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
    public static Result generateConstructive(int size, long seed) {
        return generateWith(size, seed, PuzzleGenerator::growFromTargets);
    }

    //Guarantees exactly ONE deliberate foothold instead of constructing the whole board: one
    //color's entire region is forced onto a single line (see growWithAnchor), so tryConfinement
    //fires on it unconditionally as solve()'s very first move. Everything else - every other
    //color's seed and growth - stays exactly as random as generate()/generateFill. The idea is
    //that solve()'s existing cascade (a solved target clears its row/col/color/neighbors via
    //sharesConstraint, which can trigger further deductions) can carry the rest from that one
    //guaranteed start, without needing the entire partition to be favorable by luck the way
    //generate() does, and without removing the asymmetry generateConstructive's approach did.
    public static Result generateAnchored(int size, long seed) {
        return generateWith(size, seed, PuzzleGenerator::growWithAnchor);
    }

    //Parallelism used by the no-arg generateAnchoredParallel below. Deliberately a fixed constant
    //rather than Runtime.getRuntime().availableProcessors(): the batch size changes which board a
    //given seed produces (see generateWithParallel), so it has to be baked into the algorithm the
    //same way `size` is, not left to vary with whatever hardware happens to run it -
    //otherwise the same seed would generate a different level on a different core count.
    private static final int DEFAULT_PARALLELISM = 4;

    //Parallel twin of generateAnchored: races `parallelism` reroll attempts per round on a thread
    //pool instead of trying them one at a time. See generateWithParallel for how it stays
    //deterministic (same seed -> same board) despite the concurrency. `parallelism` is part of
    //that seed->board mapping, so callers who need reproducible boards must pass the same value
    //every time (the no-arg overload below always uses DEFAULT_PARALLELISM for this reason).
    public static Result generateAnchoredParallel(int size, long seed) {
        return generateWithParallel(size, seed, PuzzleGenerator::growWithAnchor, DEFAULT_PARALLELISM);
    }

    //One fill-and-solve try: partition the board with `fill` (consuming `rand`), then ask Solver
    //whether that partition is fully derivable by elimination alone. Pure function of its
    //arguments - safe to run concurrently as long as each call gets its own Random, since it
    //never touches anything outside the Cell grid it builds itself.
    private static Attempt attempt(int size, FillStrategy fill, Random rand) {
        List<Cell> cells = new ArrayList<>(size * size);
        Cell[][] grid = new Cell[size][size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Cell c = new Cell(row, col);
                grid[row][col] = c;
                cells.add(c);
            }
        }

        fill.apply(cells, grid, rand);
        Solver solver = new Solver(size, cells);
        Set<Cell> targets = solver.solve();

        int[][] colorGrid = new int[size][size];
        boolean[][] targetGrid = new boolean[size][size];
        for (Cell c : cells) {
            colorGrid[c.row][c.col] = c.colorId;
        }
        for (Cell t : targets) {
            targetGrid[t.row][t.col] = true;
        }

        return new Attempt(targets.size() == size, colorGrid, targetGrid, solver.singletonHits,
                solver.confinementHits, solver.sharedNeighborHits, solver.lockedSetHits);
    }

    //Result of one attempt(), before we know yet whether it'll be the one we keep.
    private record Attempt(boolean solved, int[][] colorGrid, boolean[][] targetGrid,
                            int singletonHits, int confinementHits, int sharedNeighborHits,
                            int lockedSetHits) {
        Result toResult(int size, long seed, int iterations) {
            double difficultyScore = singletonHits * SINGLETON_WEIGHT
                    + confinementHits * CONFINEMENT_WEIGHT
                    + sharedNeighborHits * SHARED_NEIGHBOR_WEIGHT
                    + lockedSetHits * LOCKED_SET_WEIGHT;
            return new Result(size, seed, colorGrid, targetGrid, iterations,
                    singletonHits, confinementHits, sharedNeighborHits, lockedSetHits, difficultyScore);
        }
    }

    //Shared driver: keep asking the given fill strategy for a fresh partition until one is fully
    //derivable by elimination alone, then package up the result. The two public generate*()
    //methods differ only in which FillStrategy they pass in here. Single-threaded and consumes
    //`rand` sequentially across attempts, so a given seed always retries in exactly the same
    //order - kept exactly as before so existing seeds keep producing the same board.
    private static Result generateWith(int size, long seed, FillStrategy fill) {
        Random rand = new Random(seed);
        int iterations = 1;
        Attempt a = attempt(size, fill, rand);
        while (!a.solved()) {
            a = attempt(size, fill, rand);
            iterations++;
        }
        return a.toResult(size, seed, iterations);
    }

    //Parallel driver: same reroll-until-solved idea as generateWith, but tries a whole batch of
    //`batchSize` candidates at once on a thread pool instead of one at a time. To stay a
    //deterministic function of `seed` despite running concurrently, the *choice* of which
    //candidate wins never depends on which thread finishes first: sub-seeds for the batch are
    //drawn from `master` sequentially (so their order only depends on seed, not on timing), and
    //once the whole batch comes back we always keep the lowest-index solved attempt, waiting for
    //every future in the batch even if an earlier one already succeeded. Only the batch itself
    //runs in parallel; the seed -> board mapping this produces is otherwise fixed, just different
    //from generateWith's (batchSize=1 falls back to it exactly, batchSize>1 does not match it).
    private static Result generateWithParallel(int size, long seed, FillStrategy fill,
                                                 int batchSize) {
        if (batchSize <= 1) {
            return generateWith(size, seed, fill);
        }

        Random master = new Random(seed);
        ExecutorService pool = Executors.newFixedThreadPool(batchSize);
        try {
            int iterations = 0;
            while (true) {
                List<Future<Attempt>> futures = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize; i++) {
                    long subSeed = master.nextLong();
                    futures.add(pool.submit(() -> attempt(size, fill, new Random(subSeed))));
                }

                Attempt winner = null;
                for (Future<Attempt> future : futures) {
                    Attempt a = await(future);
                    iterations++;
                    if (winner == null && a.solved()) {
                        winner = a;
                    }
                }
                if (winner != null) {
                    return winner.toResult(size, seed, iterations);
                }
            }
        } finally {
            pool.shutdown();
        }
    }

    private static Attempt await(Future<Attempt> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while generating puzzle", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Puzzle generation attempt failed", e.getCause());
        }
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
    private static void generateFill(List<Cell> cells, Cell[][] grid, Random rand) {
        List<Cell> seeds = new ArrayList<>(cells);
        int colorCount = grid[0].length; //a replacement for color count since we work with square grids
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
    private static void growFromTargets(List<Cell> cells, Cell[][] grid, Random rand) {
        int colorCount = grid[0].length;
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
    private static void growWithAnchor(List<Cell> cells, Cell[][] grid, Random rand) {
        boolean anchorIsRow = rand.nextBoolean();
        int colorCount = grid[0].length;
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
                    if (candidate.colorId == Cell.UNCLAIMED) {
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

}
