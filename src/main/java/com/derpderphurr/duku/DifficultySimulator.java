package com.derpderphurr.duku;

import java.util.Arrays;
import java.util.stream.IntStream;

//Headless characterization of PuzzleGenerator's difficultyScore and generation cost: runs both
//fill strategies (generate = random seeding, generateAnchored = one guaranteed foothold) many
//times per board size and reports their score/iteration-count distributions side by side. No
//JavaFX, no test framework - this is exploratory, not a pass/fail check, so it's a plain main()
//you run directly, the same way Main.java is run from the IDE.
public class DifficultySimulator {

    @FunctionalInterface
    private interface Strategy {
        PuzzleGenerator.Result generate(int size, long seed, int colorCount);
    }

    // generateConstructive is deliberately excluded here - see its javadoc in PuzzleGenerator for
    // why (it failed 1.2M+ reroll attempts at size 4 and would hang this sweep).
    private static final Strategy[] STRATEGIES = { PuzzleGenerator::generate, PuzzleGenerator::generateAnchored };
    private static final String[] STRATEGY_LABELS = { "random", "anchored" };

    private static final int MIN_SIZE = 4;
    // The game only ever generates sizes 6-10 in practice; sizes above that are excluded from the
    // sweep because the random strategy alone already takes ~10 minutes at size 10 with just 20
    // trials - no need to force every run through 11-12 as well. Re-add if that range matters later.
    private static final int MAX_TEST_SIZE = 10;
    // Matches Playfield.REGION_COLORS.length (12) - duplicated rather than referenced so this
    // tool never has to resolve any JavaFX class just to read a scalar constant.
    private static final int MAX_COLOR_COUNT = 12;

    public static void main(String[] args) {
        for (int size = MIN_SIZE; size <= MAX_TEST_SIZE; size++) {
            int colorCount = Math.min(size, MAX_COLOR_COUNT);
            // Reroll iteration counts grow steeply with size (observed roughly x4-5 per size step
            // in the size 4-7 range), and each solve() call itself gets more expensive too since
            // tryLockedSets searches more color combinations as colorCount grows - so trial count
            // is scaled down for larger sizes to keep total wall time bounded, at the cost of a
            // noisier tail estimate up there. Both strategies use the same trial count per size so
            // the comparison is fair.
            int trials = Math.max(20, 400 / Math.max(1, (size - MIN_SIZE) * (size - MIN_SIZE)));

            for (int s = 0; s < STRATEGIES.length; s++) {
                runTrials(STRATEGY_LABELS[s], STRATEGIES[s], size, colorCount, trials);
            }
        }
    }

    private static void runTrials(String label, Strategy strategy, int size, int colorCount, int trials) {
        long start = System.currentTimeMillis();
        // Each trial is fully independent - its own Random, grid, and Solver, nothing shared or
        // mutated across calls - and is keyed by an explicit seed (0..trials-1) rather than
        // execution order, so running them in parallel changes nothing about the results, only
        // how fast they arrive.
        PuzzleGenerator.Result[] results = IntStream.range(0, trials)
                .parallel()
                .mapToObj(i -> strategy.generate(size, i, colorCount))
                .toArray(PuzzleGenerator.Result[]::new);
        long elapsedMs = System.currentTimeMillis() - start;

        double[] scores = Arrays.stream(results).mapToDouble(PuzzleGenerator.Result::difficultyScore).sorted().toArray();
        int[] iterations = Arrays.stream(results).mapToInt(PuzzleGenerator.Result::iterations).sorted().toArray();

        System.out.printf(
                "size=%2d [%-12s] trials=%3d (%6dms)  score[min=%.1f p25=%.1f median=%.1f p75=%.1f max=%.1f]  "
                        + "iterations[min=%d median=%d max=%d]%n",
                size, label, trials, elapsedMs,
                scores[0], percentile(scores, 25), percentile(scores, 50), percentile(scores, 75), scores[scores.length - 1],
                iterations[0], iterations[iterations.length / 2], iterations[iterations.length - 1]);
    }

    //Nearest-rank percentile over an already-sorted array.
    private static double percentile(double[] sorted, int p) {
        int index = Math.min(sorted.length - 1, (p * sorted.length) / 100);
        return sorted[index];
    }
}
