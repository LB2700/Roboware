package com.robo_board.robot_board.competition;

import java.nio.file.Path;
import java.util.function.Consumer;

import javax.json.JsonObject;

/**
 * Base class for competition drivers. Holds shared state such as the Robocode home path and
 * the callback used to publish state updates after each battle.
 */
public abstract class CompetitionDriver {

    protected final Path robocodeHome;
    protected Consumer<JsonObject> onBattleCompleted = (JsonObject competitionData) -> {
        System.out.printf("from initial callback: %s %n", competitionData);
    };

    public CompetitionDriver(Path robocodeHome) {
        this.robocodeHome = robocodeHome;
    }

    public void setOnBattleCompleted(Consumer<JsonObject> onBattleCompleted) {
        this.onBattleCompleted = onBattleCompleted;
    }

    public abstract void start();
}
