package com.derpderphurr.duku;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

//Derives which cells of a generated board must be targets, using only the deductions a player is
//allowed to make (no guessing/backtracking) - see solve() for the technique list and PuzzleGenerator
//for how a board gets built in the first place. Holds hit counters for one solve() call; callers
//build a fresh Solver per candidate board rather than reusing one across attempts.
final class Solver {
    private final int size;
    private final List<Cell> cells;
    int singletonHits;
    int confinementHits;
    int sharedNeighborHits;
    int lockedSetHits;

    Solver(int size, List<Cell> cells) {
        this.size = size;
        this.cells = cells;
    }

    private boolean sharesConstraint(Cell a, Cell b) {
        return a.row == b.row
                || a.col == b.col
                || a.colorGroup == b.colorGroup
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

        int colorGroup = group.get(0).colorGroup;
        List<Cell> eliminated = candidates.stream()
                .filter(t -> lineOf.applyAsInt(t) == line && t.colorGroup != colorGroup)
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
                        .filter(t -> lines.contains(lineOf.applyAsInt(t)) && !subsetColors.contains(t.colorGroup))
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

            Map<Integer, List<Cell>> byColor = candidates.stream().collect(Collectors.groupingBy(c -> c.colorGroup));
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

        for (Cell c : solved) {
            c.target = true;
        }
        return solved;
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