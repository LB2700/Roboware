package com.robo_board.robot_board.competition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import robocode.BattleResults;
import robocode.control.RobocodeEngine;
import robocode.control.RobotSpecification;
import robocode.control.events.BattleAdaptor;
import robocode.control.events.BattleCompletedEvent;
import robocode.control.events.BattleErrorEvent;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.robo_board.robot_board.bracket.Bracket;
import com.robo_board.robot_board.bracket.BracketMatch;
import com.robo_board.robot_board.bracket.MatchStatus;



public class CompetitionDriver {
    public static final int LEAGUE_TARGET_TPS = 250;
    public static final int BRACKET_TARGET_TPS = 150;

    private static final String TARGET_TPS_PROPERTY = "robocode.options.battle.desiredTPS";

    private final int BROADCAST_COUNT = 4; // number of parallel league runner JVMs
    private final boolean PRINT_LEAGUE_SCHEDULE = true; 
    private final boolean PRINT_LEAGUE_RESULTS = true; 
    private final int LEAGUE_GAMES_PER_ENTRY = 1; // how many games should each entry play in the league. The higher this is the more chance of a player to play an opponent more than once 
    private final int LEAGUE_GAME_SIZE = 4; // specifies 1v1v1v1 or 4-person-ffa. Make this 2 and the league runs on 1v1s

    private static final String SUBMISSION_FOLDER_NAME = "test_submissions";

    private final List<Entry> entries;
    private final Path robocodeHome;
    private final League leaguePhase;
    private final ScheduledBattle.Settings battleSettings;
    private volatile Bracket tournament;

    private Consumer<JsonObject> onBattleCompleted = (JsonObject competitionData) -> {
        System.out.printf("from initial callback: %s %n", competitionData);
    };

    public CompetitionDriver(Path robocodeHome) {
        this(robocodeHome, ScheduledBattle.Settings.DEFAULT);
    }

    public CompetitionDriver(Path robocodeHome, ScheduledBattle.Settings settings) {
        this.robocodeHome = robocodeHome; 
        this.battleSettings = settings;

        this.entries = loadEntries();

        leaguePhase = League.Generate(entries, LEAGUE_GAME_SIZE, LEAGUE_GAMES_PER_ENTRY,
                settings, LEAGUE_TARGET_TPS);

        if (PRINT_LEAGUE_SCHEDULE) leaguePhase.showSchedule();
        
    }

    private static List<Entry> loadEntries() {
        Path robotFolder = Entry.findRobotFolder(SUBMISSION_FOLDER_NAME);
        Path metadata = robotFolder.resolve("metadata.csv");
        try {
            Entry.compileSubmissions(robotFolder);
            return Entry.readCsv(metadata);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to compile or read competition entries from " + robotFolder,
                    failure);
        }
    }

    public void start() {

        onBattleCompleted.accept(leaguePhase.toJsonObject());

        // create ArrayList of 'Runners' each one a robocode instance with a determined schedule of games
        List<RobocodeEngineRunner> runners = new ArrayList<>();
        int runnerId = 1;

        for (List<ScheduledBattle> batch : RobocodeEngineRunner.partitionBattles(
                leaguePhase.getScheduledBattles(), BROADCAST_COUNT)) {
            runners.add(new RobocodeEngineRunner(runnerId++, robocodeHome.toFile(), batch));
        }

        onBattleCompleted.accept(toJsonObject());

        RobocodeEngineRunner.runAll(runners, battleResults -> {
            leaguePhase.recordBattleResults(battleResults);
            onBattleCompleted.accept(toJsonObject());
        });

        if (PRINT_LEAGUE_RESULTS) {
            leaguePhase.showStatsTable();
        }

        runTournamentInSeries();
    }

    /**
     * Seeds a double-elimination tournament from the completed league and runs
     * its matches synchronously in one JVM. A single Robocode engine is reused,
     * and each battle finishes before the next bracket battle is started.
     *
     * @return the completed tournament bracket
     */
    public Bracket runTournamentInSeries() {
        tournament = new Bracket(leaguePhase.getTopN(16).stream()
                .map(Entry::getRobotName)
                .toArray(String[]::new));

        System.setProperty(TARGET_TPS_PROPERTY, Integer.toString(BRACKET_TARGET_TPS));
        RobocodeEngine engine = new RobocodeEngine(robocodeHome.toFile());
        BattleOutcomeListener outcomeListener = new BattleOutcomeListener();
        engine.addBattleListener(outcomeListener);

        try {
            RobotSpecification[] repository = engine.getLocalRepository();

            while (!tournament.isFinished()) {


                BracketMatch match = tournament.getMatches().stream()
                        .filter(candidate -> candidate.getMatchStatus() == MatchStatus.READY)
                        .min(java.util.Comparator.comparingInt(BracketMatch::getRound)
                                .thenComparingInt(BracketMatch::getMatchId))
                        .orElseThrow(() -> new IllegalStateException(
                                "Tournament has not finished, but no bracket match is ready"));

                tournament.startMatch(match.getMatchId());

                onBattleCompleted.accept(toJsonObject());

                ScheduledBattle battle = new ScheduledBattle(
                        Arrays.asList(match.getRobots()), battleSettings, BRACKET_TARGET_TPS);
                
                List<BattleResults> results = runBattle(engine, outcomeListener, repository,
                        battle, "bracket match " + match.getMatchId());
                String winner = normalizeRobotName(results.getFirst().getTeamLeaderName());
                if (!match.hasRobot(winner)) {
                    throw new IllegalStateException("Robocode returned an unexpected winner for bracket match "
                            + match.getMatchId() + ": " + winner);
                }
                tournament.endMatch(match.getMatchId(), winner);
            }

            onBattleCompleted.accept(toJsonObject());

            return tournament;
        } finally {
            engine.close();
        }
    }

    /** Returns a competition snapshot with entries shared above both phases. */
    public JsonObject toJsonObject() {
        return buildCompetitionJson(leaguePhase.toJsonObject(), tournament);
    }

    static JsonObject buildCompetitionJson(JsonObject leagueState, Bracket currentTournament) {
        JsonObject leagueJson = Json.createObjectBuilder(leagueState)
                .remove("entries")
                .add("targetTps", LEAGUE_TARGET_TPS)
                .build();

        JsonObjectBuilder competitionJson = Json.createObjectBuilder()
                .add("league", leagueJson);
        if (currentTournament == null) {
            competitionJson.addNull("bracket");
        } else {
            competitionJson.add("bracket", Json.createObjectBuilder(currentTournament.toJsonObject())
                    .add("targetTps", BRACKET_TARGET_TPS));
        }
        competitionJson.add("entries", leagueState.getJsonArray("entries"));
        return competitionJson.build();
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    private static List<BattleResults> runBattle(RobocodeEngine engine,
            BattleOutcomeListener outcomeListener, RobotSpecification[] repository,
            ScheduledBattle battle, String description) {
        RobotSpecification[] robots = resolveRobots(repository, battle.entries(), description);
        outcomeListener.reset();
        engine.setVisible(battle.settings().visible());
        engine.runBattle(battle.toBattleSpecification(robots));
        engine.waitTillBattleOver();
        return outcomeListener.results(description, robots.length);
    }

    private static RobotSpecification[] resolveRobots(RobotSpecification[] repository,
            List<String> robotNames, String description) {
        RobotSpecification[] robots = new RobotSpecification[robotNames.size()];
        for (int i = 0; i < robotNames.size(); i++) {
            String robotName = robotNames.get(i);
            RobotSpecification[] matches = Arrays.stream(repository)
                    .filter(robot -> robotName.equals(robot.getClassName()))
                    .toArray(RobotSpecification[]::new);
            if (matches.length != 1) {
                throw new IllegalStateException("Could not resolve robot " + robotName
                        + " for " + description);
            }
            robots[i] = matches[0];
        }
        return robots;
    }

    private static String normalizeRobotName(String robotName) {
        if (robotName != null && robotName.endsWith("*")) {
            return robotName.substring(0, robotName.length() - 1);
        }
        return robotName;
    }

    private static final class BattleOutcomeListener extends BattleAdaptor {
        private volatile BattleResults[] completedResults;
        private volatile String battleError;

        @Override
        public void onBattleCompleted(BattleCompletedEvent event) {
            completedResults = event.getSortedResults().clone();
        }

        @Override
        public void onBattleError(BattleErrorEvent event) {
            battleError = event.getError();
        }

        void reset() {
            completedResults = null;
            battleError = null;
        }

        List<BattleResults> results(String description, int expectedResultCount) {
            if (battleError != null) {
                throw new IllegalStateException(description + " failed: " + battleError);
            }
            if (completedResults == null) {
                throw new IllegalStateException(description + " failed: no completion event was received");
            }
            if (completedResults.length != expectedResultCount) {
                throw new IllegalStateException(description + " returned " + completedResults.length
                        + " results; expected " + expectedResultCount);
            }
            return List.copyOf(Arrays.asList(completedResults));
        }

    }

    public void setOnBattleCompleted(Consumer<JsonObject> onBattleCompleted) {
        //System.out.println("This function does get clled");
        this.onBattleCompleted = onBattleCompleted;
    }

    public JsonObject getCurrentState() {
        return leaguePhase.toJsonObject();
    }
}
