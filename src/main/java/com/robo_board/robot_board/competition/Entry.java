package com.robo_board.robot_board.competition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import robocode.BattleResults;

class Entry {
    
    public int gamesScheduled = 0;
    private final String robotName;
    private final int index;
    private BattleResults stats;

    public Entry(String robotName, int index) {
        this.index = index;
        this.robotName = robotName;

        // Use BattleResults to aggregate an entry's stats across the league.
        stats = new BattleResults(robotName, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static List<Entry> readCsv(Path csvFile) throws IOException {
        List<List<String>> rows = parseCsv(Files.readString(csvFile, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Entry CSV is empty: " + csvFile);
        }

        int robotNameColumn = rows.getFirst().indexOf("Robot Name");
        if (robotNameColumn < 0) {
            throw new IllegalArgumentException("Entry CSV must contain a 'Robot Name' column: " + csvFile);
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
            entries.add(new Entry(row.get(robotNameColumn).trim(), entries.size()));
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

    public BattleResults getStats() {
        return stats;
    }

}
