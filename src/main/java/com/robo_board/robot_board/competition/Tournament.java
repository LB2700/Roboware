package com.robo_board.robot_board.competition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import robocode.BattleResults;
import robocode.control.BattleSpecification;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

/**
 * Runs the playoff phase of the competition.
 *
 * <p>The playoffs are a 16-player, single-elimination bracket seeded by league standing.
 * Every match is a best-of-three duel between two entrants, fought on an 800x800 battlefield
 * with a sentrybot. Placement matches determine 3rd place and 5th-8th places.
 *
 * <p>By default the tournament waits for a keypress in the terminal between games. Pass
 * {@code interactive=false} to run every game automatically (useful when driven by a web
 * endpoint or another automated caller).
 */
public class Tournament {

    private static final int PLAYOFF_SIZE = 16;
    private static final int WINS_NEEDED = 2;
    private static final String SENTRY_BOT = "samplesentry.SentryBot";
    private static final ScheduledBattle.Settings PLAYOFF_SETTINGS = new ScheduledBattle.Settings(
            1, 800, 800, 450, 0.1, 175, false, true);

    /** Standard 16-player single-elimination seeding (pairing 1v16, 8v9, ...). */
    private static final int[] ROUND_OF_16_SEEDING = {
            0, 15, 7, 8, 4, 11, 3, 12, 2, 13, 5, 10, 6, 9, 1, 14
    };

    private final Path robocodeHome;
    private final League league;
    private final List<Entry> seeds;

    private final List<PlayoffMatch> roundOf16 = new ArrayList<>();
    private final List<PlayoffMatch> quarterFinals = new ArrayList<>();
    private final List<PlayoffMatch> fifthToEighthSemis = new ArrayList<>();
    private final List<PlayoffMatch> semiFinals = new ArrayList<>();
    private final List<PlayoffMatch> fifthToEighthFinals = new ArrayList<>();
    private final List<PlayoffMatch> seventhPlaceMatch = new ArrayList<>();
    private final List<PlayoffMatch> thirdPlaceMatch = new ArrayList<>();
    private final List<PlayoffMatch> finals = new ArrayList<>();

    private final Map<Entry, Integer> finalRankings = new LinkedHashMap<>();

    private Stage currentStage;
    private boolean complete;

    private Consumer<JsonObject> onBattleCompleted = (json) -> {
        System.out.printf("Tournament update: %s%n", json);
    };

    private final Scanner input;
    private final boolean interactive;
    private RobocodeEngine engine;
    private BattleResults[] lastResults;
    private String lastError;

    public Tournament(League league, Path robocodeHome) {
        this(league, robocodeHome, true);
    }

    public Tournament(League league, Path robocodeHome, boolean interactive) {
        this.league = Objects.requireNonNull(league);
        this.robocodeHome = Objects.requireNonNull(robocodeHome);
        this.interactive = interactive;
        this.input = interactive ? new Scanner(System.in) : null;

        List<Entry> top = league.getTopEntries(PLAYOFF_SIZE);
        if (top.size() < PLAYOFF_SIZE) {
            throw new IllegalArgumentException(
                    "Need at least " + PLAYOFF_SIZE + " entrants for playoffs, got " + top.size());
        }

        // Start playoffs with fresh stats so tournament JSON shows tournament-only points.
        List<Entry> copies = new ArrayList<>(top.size());
        for (Entry entry : top) {
            copies.add(new Entry(entry));
        }
        this.seeds = List.copyOf(copies);

        for (int i = 0; i < ROUND_OF_16_SEEDING.length; i += 2) {
            roundOf16.add(new PlayoffMatch(
                    seeds.get(ROUND_OF_16_SEEDING[i]),
                    seeds.get(ROUND_OF_16_SEEDING[i + 1]),
                    "Round of 16 - Match " + (i / 2 + 1)));
        }
        this.currentStage = Stage.ROUND_OF_16;
    }

    public void setOnBattleCompleted(Consumer<JsonObject> onBattleCompleted) {
        this.onBattleCompleted = Objects.requireNonNull(onBattleCompleted);
    }

    public List<Entry> getSeeds() {
        return seeds;
    }

    public Stage getCurrentStage() {
        return currentStage;
    }

    public boolean isComplete() {
        return complete;
    }

    public Map<Entry, Integer> getFinalRankings() {
        return Collections.unmodifiableMap(finalRankings);
    }

    /** Runs the full playoffs interactively, one game at a time, blocking until a champion is crowned. */
    public void start() {
        engine = new RobocodeEngine(robocodeHome.toFile());
        try {
            engine.addBattleListener(new BattleAdaptor() {
                @Override
                public void onBattleCompleted(BattleCompletedEvent event) {
                    lastResults = event.getSortedResults();
                }

                @Override
                public void onBattleError(BattleErrorEvent event) {
                    lastError = event.getError();
                }
            });

            while (!complete) {
                List<ScheduledBattle> pending = getCurrentSchedule();
                if (pending.isEmpty()) {
                    advanceStage();
                    onBattleCompleted.accept(toJsonObject());
                    continue;
                }

                ScheduledBattle game = pending.getFirst();
                if (interactive && !waitForUserInput(game)) {
                    throw new IllegalStateException("Tournament aborted by user");
                }
                runGame(game);
                recordGameResult(game, List.of(lastResults));

                if (isStageComplete()) {
                    advanceStage();
                }
                onBattleCompleted.accept(toJsonObject());
            }
        } finally {
            engine.close();
        }
    }

    private boolean waitForUserInput(ScheduledBattle game) {
        System.out.println();
        System.out.println("Next game: " + game.entries());
        System.out.println("Stage: " + currentStage.name());
        System.out.print("Press Enter to run, or type 'quit' to stop: ");
        String line = input.nextLine();
        return !"quit".equalsIgnoreCase(line);
    }

    private void runGame(ScheduledBattle game) {
        lastResults = null;
        lastError = null;

        engine.setVisible(game.settings().visible());
        RobotSpecification[] robots = resolveRobots(game);
        BattleSpecification battle = game.toBattleSpecification(robots);
        engine.runBattle(battle);
        engine.waitTillBattleOver();

        if (lastError != null || lastResults == null) {
            throw new IllegalStateException("Battle failed: "
                    + (lastError == null ? "No completion event received" : lastError));
        }
    }

    private RobotSpecification[] resolveRobots(ScheduledBattle game) {
        RobotSpecification[] robots = new RobotSpecification[game.entries().size()];
        for (int i = 0; i < robots.length; i++) {
            RobotSpecification[] matches = engine.getLocalRepository(game.entries().get(i));
            if (matches.length != 1) {
                throw new IllegalStateException("Could not resolve robot " + game.entries().get(i)
                        + " for battle");
            }
            robots[i] = matches[0];
        }
        return robots;
    }

    /** Returns every game that must be run before the current stage can advance. */
    public List<ScheduledBattle> getCurrentSchedule() {
        List<ScheduledBattle> schedule = new ArrayList<>();
        for (PlayoffMatch match : getActiveMatches()) {
            schedule.addAll(match.getPendingGames());
        }
        return schedule;
    }

    void recordGameResult(ScheduledBattle game, List<BattleResults> results) {
        List<String> entries = game.entries();
        if (entries.size() != 3 || !entries.contains(SENTRY_BOT)) {
            throw new IllegalArgumentException("Expected a 1v1 game with sentrybot: " + entries);
        }
        PlayoffMatch match = findMatchFor(game);
        if (match == null) {
            throw new IllegalStateException("No playoff match for game: " + game);
        }
        match.recordGame(results);

        for (BattleResults result : results) {
            String name = result.getTeamLeaderName();
            if (match.entryA.id.equals(name)) {
                match.entryA.aggregateStats(result);
            } else if (match.entryB.id.equals(name)) {
                match.entryB.aggregateStats(result);
            }
        }
    }

    private PlayoffMatch findMatchFor(ScheduledBattle game) {
        for (PlayoffMatch match : getAllMatches()) {
            if (match.hasGame(game)) {
                return match;
            }
        }
        return null;
    }

    private List<PlayoffMatch> getActiveMatches() {
        return switch (currentStage) {
            case ROUND_OF_16 -> roundOf16;
            case QUARTERFINALS -> quarterFinals;
            case FIFTH_TO_EIGHTH_SEMIFINALS -> fifthToEighthSemis;
            case SEMIFINALS -> semiFinals;
            case FIFTH_TO_EIGHTH_FINALS -> fifthToEighthFinals;
            case SEVENTH_PLACE -> seventhPlaceMatch;
            case THIRD_PLACE -> thirdPlaceMatch;
            case FINALS -> finals;
            case COMPLETE -> List.of();
        };
    }

    private List<PlayoffMatch> getAllMatches() {
        List<PlayoffMatch> all = new ArrayList<>();
        all.addAll(roundOf16);
        all.addAll(quarterFinals);
        all.addAll(fifthToEighthSemis);
        all.addAll(semiFinals);
        all.addAll(fifthToEighthFinals);
        all.addAll(seventhPlaceMatch);
        all.addAll(thirdPlaceMatch);
        all.addAll(finals);
        return all;
    }

    public boolean isStageComplete() {
        for (PlayoffMatch match : getActiveMatches()) {
            if (!match.isComplete()) {
                return false;
            }
        }
        return true;
    }

    void advanceStage() {
        currentStage = switch (currentStage) {
            case ROUND_OF_16 -> {
                createQuarterFinals();
                yield Stage.QUARTERFINALS;
            }
            case QUARTERFINALS -> {
                createFifthToEighthSemis();
                yield Stage.FIFTH_TO_EIGHTH_SEMIFINALS;
            }
            case FIFTH_TO_EIGHTH_SEMIFINALS -> {
                createSemiFinals();
                yield Stage.SEMIFINALS;
            }
            case SEMIFINALS -> {
                createFinalsAndThirdPlace();
                createFifthToEighthFinals();
                yield Stage.FIFTH_TO_EIGHTH_FINALS;
            }
            case FIFTH_TO_EIGHTH_FINALS -> Stage.SEVENTH_PLACE;
            case SEVENTH_PLACE -> Stage.THIRD_PLACE;
            case THIRD_PLACE -> Stage.FINALS;
            case FINALS -> {
                finishTournament();
                yield Stage.COMPLETE;
            }
            case COMPLETE -> Stage.COMPLETE;
        };
        complete = (currentStage == Stage.COMPLETE);
    }

    private void createQuarterFinals() {
        List<Entry> winners = getWinners(roundOf16);
        quarterFinals.add(new PlayoffMatch(winners.get(0), winners.get(1), "Quarterfinal 1"));
        quarterFinals.add(new PlayoffMatch(winners.get(2), winners.get(3), "Quarterfinal 2"));
        quarterFinals.add(new PlayoffMatch(winners.get(4), winners.get(5), "Quarterfinal 3"));
        quarterFinals.add(new PlayoffMatch(winners.get(6), winners.get(7), "Quarterfinal 4"));
    }

    private void createFifthToEighthSemis() {
        List<Entry> losers = getLosers(quarterFinals);
        // Cross-bracket consolation semis.
        fifthToEighthSemis.add(new PlayoffMatch(losers.get(0), losers.get(3), "5th-8th Semifinal 1"));
        fifthToEighthSemis.add(new PlayoffMatch(losers.get(1), losers.get(2), "5th-8th Semifinal 2"));
    }

    private void createSemiFinals() {
        List<Entry> winners = getWinners(quarterFinals);
        semiFinals.add(new PlayoffMatch(winners.get(0), winners.get(1), "Semifinal 1"));
        semiFinals.add(new PlayoffMatch(winners.get(2), winners.get(3), "Semifinal 2"));
    }

    private void createFifthToEighthFinals() {
        List<Entry> winners = getWinners(fifthToEighthSemis);
        List<Entry> losers = getLosers(fifthToEighthSemis);
        fifthToEighthFinals.add(new PlayoffMatch(winners.get(0), winners.get(1), "5th-6th Place Match"));
        seventhPlaceMatch.add(new PlayoffMatch(losers.get(0), losers.get(1), "7th-8th Place Match"));
    }

    private void createFinalsAndThirdPlace() {
        List<Entry> winners = getWinners(semiFinals);
        List<Entry> losers = getLosers(semiFinals);
        finals.add(new PlayoffMatch(winners.get(0), winners.get(1), "Final"));
        thirdPlaceMatch.add(new PlayoffMatch(losers.get(0), losers.get(1), "3rd-4th Place Match"));
    }

    private void finishTournament() {
        assignRanking(finals.get(0).getWinner(), 1);
        assignRanking(finals.get(0).getLoser(), 2);
        assignRanking(thirdPlaceMatch.get(0).getWinner(), 3);
        assignRanking(thirdPlaceMatch.get(0).getLoser(), 4);
        assignRanking(fifthToEighthFinals.get(0).getWinner(), 5);
        assignRanking(fifthToEighthFinals.get(0).getLoser(), 6);
        assignRanking(seventhPlaceMatch.get(0).getWinner(), 7);
        assignRanking(seventhPlaceMatch.get(0).getLoser(), 8);

        // Round-of-16 losers share 9th-16th place.
        for (PlayoffMatch match : roundOf16) {
            if (!finalRankings.containsKey(match.getLoser())) {
                assignRanking(match.getLoser(), 9);
            }
        }

        showTop10();
    }

    public void showTop10() {
        List<Entry> sorted = new ArrayList<>(finalRankings.keySet());
        sorted.sort((a, b) -> Integer.compare(b.getStats().getScore(), a.getStats().getScore()));

        System.out.println();
        System.out.println("Top 10 Tournament Finishers");
        System.out.println("=".repeat(52));
        System.out.printf("%-6s %-30s %10s%n", "Rank", "Robot", "Score");
        System.out.println("-".repeat(52));

        int position = 1;
        for (Entry entry : sorted) {
            if (position > 10) {
                break;
            }
            System.out.printf("%-6d %-30s %10d%n", position, entry.id, entry.getStats().getScore());
            position++;
        }
        System.out.println("=".repeat(52));
    }

    private void assignRanking(Entry entry, int rank) {
        finalRankings.putIfAbsent(entry, rank);
    }

    private static List<Entry> getWinners(List<PlayoffMatch> matches) {
        List<Entry> winners = new ArrayList<>();
        for (PlayoffMatch match : matches) {
            if (!match.isComplete() || match.getWinner() == null) {
                throw new IllegalStateException("Match not complete when reading winner: " + match.getLabel());
            }
            winners.add(match.getWinner());
        }
        return winners;
    }

    private static List<Entry> getLosers(List<PlayoffMatch> matches) {
        List<Entry> losers = new ArrayList<>();
        for (PlayoffMatch match : matches) {
            if (!match.isComplete() || match.getLoser() == null) {
                throw new IllegalStateException("Match not complete when reading loser: " + match.getLabel());
            }
            losers.add(match.getLoser());
        }
        return losers;
    }

    public JsonObject toJsonObject() {
        List<Entry> sortedByScore = new ArrayList<>(seeds);
        sortedByScore.sort((a, b) -> Integer.compare(b.getStats().getScore(), a.getStats().getScore()));

        JsonArrayBuilder entriesJson = Json.createArrayBuilder();
        for (Entry seed : sortedByScore) {
            entriesJson.add(seed.toJsonObject());
        }

        JsonArrayBuilder seedsJson = Json.createArrayBuilder();
        for (Entry seed : seeds) {
            seedsJson.add(seed.toJsonObject());
        }

        JsonArrayBuilder roundsJson = Json.createArrayBuilder();
        roundsJson.add(matchesToJson("Round of 16", roundOf16));
        roundsJson.add(matchesToJson("Quarterfinals", quarterFinals));
        roundsJson.add(matchesToJson("5th-8th Semifinals", fifthToEighthSemis));
        roundsJson.add(matchesToJson("Semifinals", semiFinals));
        roundsJson.add(matchesToJson("5th-6th Place", fifthToEighthFinals));
        roundsJson.add(matchesToJson("7th-8th Place", seventhPlaceMatch));
        roundsJson.add(matchesToJson("3rd-4th Place", thirdPlaceMatch));
        roundsJson.add(matchesToJson("Final", finals));

        JsonArrayBuilder rankingsJson = Json.createArrayBuilder();
        finalRankings.forEach((entry, rank) -> rankingsJson.add(Json.createObjectBuilder()
                .add("entry", entry.toJsonObject())
                .add("rank", rank)));

        return Json.createObjectBuilder()
                .add("stage", currentStage.name())
                .add("complete", complete)
                .add("entries", entriesJson)
                .add("seeds", seedsJson)
                .add("rounds", roundsJson)
                .add("rankings", rankingsJson)
                .build();
    }

    private static JsonObject matchesToJson(String name, List<PlayoffMatch> matches) {
        JsonArrayBuilder matchesJson = Json.createArrayBuilder();
        for (PlayoffMatch match : matches) {
            matchesJson.add(match.toJsonObject());
        }
        return Json.createObjectBuilder()
                .add("name", name)
                .add("matches", matchesJson)
                .build();
    }

    public enum Stage {
        ROUND_OF_16,
        QUARTERFINALS,
        FIFTH_TO_EIGHTH_SEMIFINALS,
        SEMIFINALS,
        FIFTH_TO_EIGHTH_FINALS,
        SEVENTH_PLACE,
        THIRD_PLACE,
        FINALS,
        COMPLETE
    }

    private enum GameOutcome {
        A_WINS,
        B_WINS,
        DRAW
    }

    private static class PlayoffMatch {
        private final Entry entryA;
        private final Entry entryB;
        private final String label;
        private final List<ScheduledBattle> games = new ArrayList<>();
        private final List<ScheduledBattle> pendingGames = new ArrayList<>();

        private int winsA = 0;
        private int winsB = 0;
        private Entry winner;
        private Entry loser;
        private boolean complete;

        PlayoffMatch(Entry entryA, Entry entryB, String label) {
            this.entryA = entryA;
            this.entryB = entryB;
            this.label = label;
            addGame();
        }

        private void addGame() {
            ScheduledBattle game = new ScheduledBattle(
                    List.of(entryA.id, entryB.id, SENTRY_BOT), PLAYOFF_SETTINGS);
            games.add(game);
            pendingGames.add(game);
        }

        void recordGame(List<BattleResults> results) {
            if (!pendingGames.isEmpty()) {
                pendingGames.removeFirst();
            }

            GameOutcome outcome = determineOutcome(results);
            if (outcome == GameOutcome.A_WINS) {
                winsA++;
            } else if (outcome == GameOutcome.B_WINS) {
                winsB++;
            }

            if (winsA >= WINS_NEEDED) {
                winner = entryA;
                loser = entryB;
                complete = true;
                pendingGames.clear();
            } else if (winsB >= WINS_NEEDED) {
                winner = entryB;
                loser = entryA;
                complete = true;
                pendingGames.clear();
            } else if (outcome == GameOutcome.DRAW) {
                addGame(); // replay; does not count as a win
            } else {
                addGame(); // next game in the best-of-three series
            }
        }

        private GameOutcome determineOutcome(List<BattleResults> results) {
            BattleResults resultA = null;
            BattleResults resultB = null;
            for (BattleResults result : results) {
                String name = result.getTeamLeaderName();
                if (entryA.id.equals(name)) {
                    resultA = result;
                } else if (entryB.id.equals(name)) {
                    resultB = result;
                }
            }
            if (resultA == null || resultB == null) {
                throw new IllegalStateException(
                        "Missing results for playoff entrants in " + label + ": " + results);
            }
            if (resultA.getRank() < resultB.getRank()) {
                return GameOutcome.A_WINS;
            } else if (resultB.getRank() < resultA.getRank()) {
                return GameOutcome.B_WINS;
            } else {
                return GameOutcome.DRAW;
            }
        }

        List<ScheduledBattle> getPendingGames() {
            return List.copyOf(pendingGames);
        }

        boolean hasGame(ScheduledBattle game) {
            return games.contains(game);
        }

        boolean isComplete() {
            return complete;
        }

        Entry getWinner() {
            return winner;
        }

        Entry getLoser() {
            return loser;
        }

        String getLabel() {
            return label;
        }

        JsonObject toJsonObject() {
            JsonObjectBuilder builder = Json.createObjectBuilder()
                    .add("label", label)
                    .add("entryA", entryA.toJsonObject())
                    .add("entryB", entryB.toJsonObject())
                    .add("winsA", winsA)
                    .add("winsB", winsB)
                    .add("complete", complete);
            if (winner != null) {
                builder.add("winner", winner.toJsonObject());
            }
            if (loser != null) {
                builder.add("loser", loser.toJsonObject());
            }
            return builder.build();
        }
    }
}
