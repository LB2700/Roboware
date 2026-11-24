package com.robo_board.robot_board.competition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import robocode.BattleResults;

import javax.json.JsonObject;



public class CompetitionDriver {

    private final int BROADCAST_COUNT = 1; // how many robocode instances to run in parrallel that 'broadcast' the competition to to the audience

    private final boolean PRINT_LEAGUE_SCHEDULE = true; 
    private final boolean PRINT_LEAGUE_RESULTS = true; 
    private final int LEAGUE_GAMES_PER_ENTRY = 4; // how many games should each entry play in the league. The higher this is the more chance of a player to play an opponent more than once 
    private final int LEAGUE_GAME_SIZE = 4; // specifies 1v1v1v1 or 4-person-ffa. Make this 2 and the league runs on 1v1s

    private static final String SUBMISSION_FOLDER_NAME = "test_submissions";

    private final List<Entry> entries;
    private final Path robocodeHome;
    private final League leaguePhase;

    private Consumer<JsonObject> onBattleCompleted = (JsonObject competitionData) -> {
        System.out.printf("from initial callback: %s %n", competitionData);
    };

    public CompetitionDriver(Path robocodeHome) {
        this(robocodeHome, ScheduledBattle.Settings.DEFAULT);
    }

    public CompetitionDriver(Path robocodeHome, ScheduledBattle.Settings settings) {
        
        this.robocodeHome = robocodeHome; 

        this.entries = loadEntries();

        leaguePhase = League.Generate(entries, LEAGUE_GAME_SIZE, LEAGUE_GAMES_PER_ENTRY, settings);

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

        // create ArrayList of 'Runners' each one a robocode instance with a determined schedule of games
        List<RobocodeEngineRunner> runners = new ArrayList<>();
        int runnerId = 1;

        // partition the league schedule and instantiate each runner 
        for (List<ScheduledBattle> batch : RobocodeEngineRunner.partitionBattles(
                leaguePhase.getScheduledBattles(), BROADCAST_COUNT)) {
            runners.add(new RobocodeEngineRunner(runnerId++, robocodeHome.toFile(), batch));
        }

        // run each runner in a seperate java process, pass function to respond to games completing
        RobocodeEngineRunner.runAll(runners, 
            (List<BattleResults> battleResults) -> {
                leaguePhase.recordBattleResults(battleResults);
                onBattleCompleted.accept(leaguePhase.toJsonObject());
                //System.out.println(leaguePhase.toJsonObject());
            }
        );

        if (PRINT_LEAGUE_RESULTS) leaguePhase.showStatsTable();
    }

    public void setOnBattleCompleted(Consumer<JsonObject> onBattleCompleted) {
        //System.out.println("This function does get clled");
        this.onBattleCompleted = onBattleCompleted;
    }
}
