package com.robo_board.robot_board.competition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.json.Json;
import javax.json.JsonObject;

import robocode.BattleResults;

class Entry {
    private static final Path ROBOTS_DIRECTORY = Path.of("robocode", "robots")
            .toAbsolutePath().normalize();
    
    public int gamesScheduled = 0;
    private final String robotName;
    private final int index;
    private final String authorName;
    private BattleResults stats;

    public Entry(String robotName, int index, String authorName) {
        this.index = index;
        this.robotName = Objects.requireNonNull(robotName, "robotName");
        this.authorName = Objects.requireNonNull(authorName, "authorName");

        // Use BattleResults to aggregate an entry's stats across the league.
        stats = new BattleResults(robotName, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static Path findRobotFolder(String folderName) {
        Objects.requireNonNull(folderName, "folderName");
        if (folderName.isBlank() || Path.of(folderName).getNameCount() != 1) {
            throw new IllegalArgumentException("A robot folder name must be provided: " + folderName);
        }

        Path folder = ROBOTS_DIRECTORY.resolve(folderName).normalize();
        if (!folder.getParent().equals(ROBOTS_DIRECTORY) || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Robot folder not found: " + folderName);
        }
        return folder;
    }

    public static void compileSubmissions(Path robotFolder) throws IOException {
        Objects.requireNonNull(robotFolder, "robotFolder");
        Path normalizedFolder = robotFolder.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedFolder)) {
            throw new IllegalArgumentException("Robot submission folder not found: " + robotFolder);
        }

        List<Path> sourceFiles;
        try (var files = Files.list(normalizedFolder)) {
            sourceFiles = files
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("No Java submissions found in: " + normalizedFolder);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required to compile robot submissions; no Java compiler was found");
        }

        Path outputDirectory = normalizedFolder.getParent();
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Robot submission folder needs a parent directory: " + robotFolder);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "--release", "8",
                    "-encoding", StandardCharsets.UTF_8.name(),
                    "-d", outputDirectory.toString());
            boolean compiled = Boolean.TRUE.equals(compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits).call());
            if (!compiled) {
                throw new IllegalStateException(formatCompilationErrors(normalizedFolder, diagnostics));
            }
        }
    }

    private static String formatCompilationErrors(Path robotFolder,
            DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder message = new StringBuilder("Robot submission compilation failed in ")
                .append(robotFolder).append(':');
        diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .forEach(diagnostic -> message.append(System.lineSeparator())
                        .append(diagnostic.getSource() == null
                                ? "<unknown source>" : diagnostic.getSource().getName())
                        .append(':').append(diagnostic.getLineNumber())
                        .append(':').append(diagnostic.getColumnNumber())
                        .append(": error: ")
                        .append(diagnostic.getMessage(java.util.Locale.ROOT)));
        return message.toString();
    }

    public static List<Entry> readCsv(Path csvFile) throws IOException {
        Path normalizedCsvFile = csvFile.toAbsolutePath().normalize();
        Path robotFolder = normalizedCsvFile.getParent();
        if (robotFolder == null || robotFolder.getFileName() == null) {
            throw new IllegalArgumentException("Entry CSV must be inside a robot package folder: " + csvFile);
        }
        String packageName = robotFolder.getFileName().toString();

        List<List<String>> rows = parseCsv(Files.readString(normalizedCsvFile, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Entry CSV is empty: " + csvFile);
        }

        int robotNameColumn = rows.getFirst().indexOf("Robot Name");
        if (robotNameColumn < 0) {
            throw new IllegalArgumentException("Entry CSV must contain a 'Robot Name' column: " + csvFile);
        }
        int authorNameColumn = rows.getFirst().indexOf("Full name");
        if (authorNameColumn < 0) {
            throw new IllegalArgumentException("Entry CSV must contain a 'Full name' column: " + csvFile);
        }

        List<Entry> entries = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            if (robotNameColumn >= row.size() || row.get(robotNameColumn).isBlank()) {
                throw new IllegalArgumentException("Missing robot name on CSV row " + (rowIndex + 1));
            }
            if (authorNameColumn >= row.size() || row.get(authorNameColumn).isBlank()) {
                throw new IllegalArgumentException("Missing author name on CSV row " + (rowIndex + 1));
            }
            String qualifiedRobotName = packageName + "." + row.get(robotNameColumn).trim();
            entries.add(new Entry(qualifiedRobotName, entries.size(),
                    row.get(authorNameColumn).trim()));
        }
        return List.copyOf(entries);
    }

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++) {
            char character = csv.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((character == '\n' || character == '\r') && !quoted) {
                row.add(field.toString());
                field.setLength(0);
                rows.add(List.copyOf(row));
                row.clear();
                if (character == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("Unclosed quoted field in entry CSV");
        }
        if (!row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString());
            rows.add(List.copyOf(row));
        }
        return rows;
    }

    @Override
    public String toString() {
        return Integer.toString(index);
    }

    public JsonObject toJsonObject() {
        return Json.createObjectBuilder()
                .add("robotName", robotName)
                .add("authorName", authorName)
                .add("index", index)
                .add("gamesScheduled", gamesScheduled)
                .add("stats", statsToJsonObject())
                .build();
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    private JsonObject statsToJsonObject() {
        return Json.createObjectBuilder()
                .add("teamLeaderName", stats.getTeamLeaderName())
                .add("rank", stats.getRank())
                .add("score", stats.getScore())
                .add("survival", stats.getSurvival())
                .add("lastSurvivorBonus", stats.getLastSurvivorBonus())
                .add("bulletDamage", stats.getBulletDamage())
                .add("bulletDamageBonus", stats.getBulletDamageBonus())
                .add("ramDamage", stats.getRamDamage())
                .add("ramDamageBonus", stats.getRamDamageBonus())
                .add("firsts", stats.getFirsts())
                .add("seconds", stats.getSeconds())
                .add("thirds", stats.getThirds())
                .build();
    }

    public void showInfo() {
        System.out.printf("%s. gamesScheduled: %d%n", robotName, gamesScheduled);
    }

    public ArrayList<ScheduledBattle> findMyGames(ArrayList<ScheduledBattle> games) {
        ArrayList<ScheduledBattle> myGames = new ArrayList<>();
        for (ScheduledBattle game : games) {
            if (game.participants().contains(this)) {
                myGames.add(game);
            }
        }
        return myGames;
    }

    public HashSet<Entry> findPastOpponents(ArrayList<ScheduledBattle> games) {
        HashSet<Entry> pastOpponents = new HashSet<>();
        for (ScheduledBattle game : findMyGames(games)) {
            for (Entry opponent : game.participants()) {
                if (opponent != this) {
                    pastOpponents.add(opponent);
                }
            }
        }
        return pastOpponents;
    }

    public void showGames(ArrayList<ScheduledBattle> games) {
        System.out.printf("Games with %S%n", robotName);
        for (ScheduledBattle game : findMyGames(games)) {
            System.out.println(game);
        }
    }

    public int countCollisions(ArrayList<ScheduledBattle> games) {
        HashMap<Entry, Integer> opponentsCount = new HashMap<>();

        for (Entry opponent : findPastOpponents(games)) {
            opponentsCount.put(opponent, 0);
            for (ScheduledBattle game : findMyGames(games)) {
                if (game.participants().contains(opponent)) {
                    opponentsCount.put(opponent, opponentsCount.get(opponent) + 1);
                }
            }
        }
        System.out.printf("opponentsCount for entry %s: %n", robotName);
        for (Entry opponent : opponentsCount.keySet()) {
            System.out.printf("\t%s: %d%n", opponent, opponentsCount.get(opponent));
        }
        return 0;
    }

    public void aggregateStats(BattleResults battleResults) {
        stats = new BattleResults(
                robotName, 0,
                (double) (battleResults.getScore() + stats.getScore()),
                (double) (battleResults.getSurvival() + stats.getSurvival()),
                (double) (battleResults.getLastSurvivorBonus() + stats.getLastSurvivorBonus()),
                (double) (battleResults.getBulletDamage() + stats.getBulletDamage()),
                (double) (battleResults.getBulletDamageBonus() + stats.getBulletDamageBonus()),
                (double) (battleResults.getRamDamage() + stats.getRamDamage()),
                (double) (battleResults.getRamDamageBonus() + stats.getRamDamageBonus()),
                battleResults.getFirsts() + stats.getFirsts(),
                battleResults.getSeconds() + stats.getSeconds(),
                battleResults.getThirds() + stats.getThirds());
    }

    public void showStats() {
        System.out.printf("%d. name: %s | score: %d | firsts: %d | seconds: %d | thirds: %d | bullet damage: %d | ram damage: %d%n",
                index, robotName, stats.getScore(), stats.getFirsts(), stats.getSeconds(), stats.getThirds(),
                stats.getBulletDamage(), stats.getRamDamage());
    }

    public int getIndex() {
        return index;
    }

    public String getRobotName() {
        return robotName;
    }

    public String getAuthorName() {
        return authorName;
    }

    public BattleResults getStats() {
        return stats;
    }

}
