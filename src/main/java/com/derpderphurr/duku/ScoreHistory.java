package com.derpderphurr.duku;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

//Appends completed-level stats to a CSV file in the user's home directory, as a foundation for a
//future high-scores/stats screen. System.getProperty("user.home") resolves correctly on both
//Windows and Linux with no extra dependency; CSV keeps this dependency-free (the project has no
//JSON library) and trivially appendable - each completion is one more line, never a rewrite.
public final class ScoreHistory {
    private ScoreHistory() {}

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".duku-scores.csv");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String HEADER = "dateTimeIso,seed,size,score,completionTimeMillis";

    /**
     * Stash a high score file, might be useful for comparing statistics
     */
    public static void stash() throws IOException {
        Path newTarget = Paths.get(System.getProperty("user.home"),String.format(".duku-scores.csv-%s",TIMESTAMP_FORMAT.format(LocalDateTime.now())));
        Files.move(FILE,newTarget);
    }

    public record Score (long seed, int size, int score, long completionTimeMillis, LocalDateTime date) {
        static Score fromLine(String s) {
            String[] parts = s.split(",");
            assert(parts.length == 5);

            LocalDateTime d = LocalDateTime.from(TIMESTAMP_FORMAT.parse(parts[0]));
            long seed = Long.parseLong(parts[1]);
            int size = Integer.parseInt(parts[2]);
            int score = Integer.parseInt(parts[3]);
            long compTime = Long.parseLong(parts[4]);

            return new Score(seed,size,score,compTime,d);
        }
    }

    public static Stream<Score> getAllScores() throws IOException {
        return Files.lines(FILE).map(Score::fromLine);
    }

    //Appends one completed level's stats as a CSV row, creating the file (with header) on first
    //write. Failures are logged, not thrown - a lost score write shouldn't break level transitions.
    //seed is included so a past entry's exact board can be reproduced later (Playfield.sizeForSeed
    //derives the same size from it, and Playfield's own generation is seeded from it too).
    public static void recordCompletion(long seed, int size, int score, long completionTimeMillis) throws IOException {
        String row = TIMESTAMP_FORMAT.format(LocalDateTime.now()) + "," + seed + "," + size + "," + score + "," + completionTimeMillis;
        if (Files.notExists(FILE)) {
            Files.writeString(FILE, HEADER + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
        }
        Files.writeString(FILE, row + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
