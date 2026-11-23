package com.robo_board.robot_board.competition;

import java.nio.file.Path;
import java.util.function.Consumer;

import javax.json.JsonObject;

/**
 * Runs the interactive playoff phase of the competition one game at a time.
 */
public class TournamentDriver extends CompetitionDriver {

    private final Tournament tournament;

    /**
     * Creates a non-interactive driver suitable for web/API use (games run back-to-back
     * without waiting for terminal input).
     */
    public TournamentDriver(League league, Path robocodeHome) {
        this(league, robocodeHome, false);
    }

    /**
     * Creates a driver with explicit control over interactive mode.
     *
     * @param interactive if true, waits for a keypress in the terminal between games.
     * Oisín: This is how we will most likely use it for the competition.
     *
     */
    public TournamentDriver(League league, Path robocodeHome, boolean interactive) {
        super(robocodeHome);
        this.tournament = new Tournament(league, robocodeHome, interactive);
    }

    @Override
    public void setOnBattleCompleted(Consumer<JsonObject> onBattleCompleted) {
        super.setOnBattleCompleted(onBattleCompleted);
        tournament.setOnBattleCompleted(onBattleCompleted);
    }

    @Override
    public void start() {
        tournament.start();
    }

    public Tournament getTournament() {
        return tournament;
    }
}
