package com.robo_board.robot_board.competition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import robocode.BattleResults;

/**
 * Runs the league phase of the competition in parallel child Robocode JVMs.
 */
public class LeagueDriver extends CompetitionDriver {

    private final int BROADCAST_COUNT = 1;
    private final boolean PRINT_LEAGUE_SCHEDULE = true;
    private final boolean PRINT_LEAGUE_RESULTS = true;
    private final int LEAGUE_GAMES_PER_ENTRY = 4;
    private final int LEAGUE_GAME_SIZE = 4;

    private final League leaguePhase;

    public LeagueDriver(List<String> entrants, Path robocodeHome) {
        this(entrants, robocodeHome, ScheduledBattle.Settings.DEFAULT);
    }

    public LeagueDriver(List<String> entrants, Path robocodeHome, ScheduledBattle.Settings settings) {
        super(robocodeHome);
        leaguePhase = League.Generate(entrants, LEAGUE_GAME_SIZE, LEAGUE_GAMES_PER_ENTRY, settings);

        if (PRINT_LEAGUE_SCHEDULE) {
            leaguePhase.showSchedule();
        }
    }

    @Override
    public void start() {
        List<RobocodeEngineRunner> runners = new ArrayList<>();
        int runnerId = 1;

        for (List<ScheduledBattle> batch : RobocodeEngineRunner.partitionBattles(
                leaguePhase.getScheduledBattles(), BROADCAST_COUNT)) {
            runners.add(new RobocodeEngineRunner(runnerId++, robocodeHome.toFile(), batch));
        }

        RobocodeEngineRunner.runAll(runners,
                (List<BattleResults> battleResults) -> {
                    leaguePhase.recordBattleResults(battleResults);
                    onBattleCompleted.accept(leaguePhase.toJsonObject());
                });

        if (PRINT_LEAGUE_RESULTS) {
            leaguePhase.showStatsTable();
        }
    }

    public League getLeague() {
        return leaguePhase;
    }
}
