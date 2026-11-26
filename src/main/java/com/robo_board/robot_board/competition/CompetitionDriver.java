package com.robo_board.robot_board.competition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import robocode.BattleResults;

import javax.json.JsonObject;



public class CompetitionDriver {


    public static final List<String> SAMPLE_ENTRANTS = List.of(
        "sample.Corners",
        "sample.Fire",
        "sample.SpinBot",
        "sample.SittingDuck",
        "sample.Crazy",
        "sample.MyFirstRobot",
        "sample.PaintingRobot",
        "sample.RamFire",
        "sample.Tracker",
        "sample.Target",
        "sample.TrackFire",
        "sample.VelociRobot"
        // "sample.VelociRobot"
    );

    private final int BROADCAST_COUNT = 1; // how many robocode instances to run in parrallel that 'broadcast' the competition to to the audience

    private final boolean PRINT_LEAGUE_SCHEDULE = true; 
    private final boolean PRINT_LEAGUE_RESULTS = true; 
    private final int LEAGUE_GAMES_PER_ENTRY = 4; // how many games should each entry play in the league. The higher this is the more chance of a player to play an opponent more than once 
    private final int LEAGUE_GAME_SIZE = 4; // specifies 1v1v1v1 or 4-person-ffa. Make this 2 and the league runs on 1v1s
    private final Path robocodeHome;
    private final League leaguePhase;

    private Consumer<JsonObject> onBattleCompleted = (JsonObject competitionData) -> {
        System.out.printf("from initial callback: %s %n", competitionData);
    };

    public CompetitionDriver(List<String> entrants, Path robocodeHome) {
        this(entrants, robocodeHome, ScheduledBattle.Settings.DEFAULT);
    }

    public CompetitionDriver(List<String> entrants, Path robocodeHome, ScheduledBattle.Settings settings) {
        
        this.robocodeHome = robocodeHome; 
        leaguePhase = League.Generate(entrants, LEAGUE_GAME_SIZE, LEAGUE_GAMES_PER_ENTRY, settings);

        if (PRINT_LEAGUE_SCHEDULE) leaguePhase.showSchedule();

        
    }

    public void start() {

        // create ArrayList of 'Runners' each one a robocode instance with a determined schedule of games
        List<RobocodeEngineRunner> runners = new ArrayList<>();
        int runnerId = 1;

        // partition the league schedule and instantiate each runner 
        for (List<ScheduledBattle> batch : RobocodeEngineRunner.partitionBattles(
                leaguePhase.getScheduledBattles(), BROADCAST_COUNT)) {
            runners.add(new RobocodeEngineRunner(runnerId++, robocodeHome.toFile(), batch));
        }

        onBattleCompleted.accept(leaguePhase.toJsonObject());

        // run each runner in a seperate java process, pass function to respond to games completing
        RobocodeEngineRunner.runAll(runners, 
            (List<BattleResults> battleResults) -> {
                leaguePhase.recordBattleResults(battleResults);
                onBattleCompleted.accept(leaguePhase.toJsonObject());
                //System.out.println(leaguePhase.toJsonObject());

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
        );

        if (PRINT_LEAGUE_RESULTS) leaguePhase.showStatsTable();
    }

    public void setOnBattleCompleted(Consumer<JsonObject> onBattleCompleted) {
        //System.out.println("This function does get clled");
        this.onBattleCompleted = onBattleCompleted;
    }

    public JsonObject getCurrentState() {
        return leaguePhase.toJsonObject();
    }
}
