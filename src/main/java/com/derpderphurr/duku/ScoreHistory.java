package com.derpderphurr.duku;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

//Appends completed-level stats to a CSV file in the user's home directory, as a foundation for a
//future high-scores/stats screen. System.getProperty("user.home") resolves correctly on both
//Windows and Linux with no extra dependency; CSV keeps this dependency-free (the project has no
//JSON library) and trivially appendable - each completion is one more line, never a rewrite.
public final class ScoreHistory {
    private ScoreHistory() {}

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".duku-scores.csv");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String HEADER = "dateTimeIso,seed,size,score,completionTimeMillis";

    //Appends one completed level's stats as a CSV row, creating the file (with header) on first
    //write. Failures are logged, not thrown - a lost score write shouldn't break level transitions.
    //seed is included so a past entry's exact board can be reproduced later (Playfield.sizeForSeed
    //derives the same size from it, and Playfield's own generation is seeded from it too).
    public static void recordCompletion(long seed, int size, int score, long completionTimeMillis) {
        String row = TIMESTAMP_FORMAT.format(LocalDateTime.now()) + "," + seed + "," + size + "," + score + "," + completionTimeMillis;
        try {
            if (Files.notExists(FILE)) {
                Files.writeString(FILE, HEADER + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            }
            Files.writeString(FILE, row + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to record score history: " + e.getMessage());
        }
    }
}
