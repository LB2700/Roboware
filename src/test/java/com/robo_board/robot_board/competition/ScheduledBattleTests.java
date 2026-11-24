package com.robo_board.robot_board.competition;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobotSpecification;

class ScheduledBattleTests {
    @TempDir
    Path directory;

    private final ScheduledBattle.Settings settings = new ScheduledBattle.Settings(
            3, 1200, 900, 700, 0.3, 150, true, false);

    @Test
    void fileRoundTripDeliversEverySettingToBattleSpecification() throws Exception {
        ScheduledBattle original = new ScheduledBattle(List.of("sample.Corners", "sample.Fire"), settings,
                List.of(new ScheduledBattle.StartingPosition(100.0, 200.0, 90.0),
                        new ScheduledBattle.StartingPosition(null, null, null)));
        Path file = directory.resolve("scheduledGames.json");
        Files.writeString(file, Json.createArrayBuilder().add(original.toJsonObject()).build().toString());

        ScheduledBattle parsed = GameDriver.parseSchedule(file).getFirst();
        assertEquals(original, parsed);
        assertFalse(parsed.settings().visible());
        BattleSpecification battle = parsed.toBattleSpecification(new RobotSpecification[2]);
        assertAll(
                () -> assertEquals(3, battle.getNumRounds()),
                () -> assertEquals(1200, battle.getBattlefield().getWidth()),
                () -> assertEquals(900, battle.getBattlefield().getHeight()),
                () -> assertEquals(700, battle.getInactivityTime()),
                () -> assertEquals(0.3, battle.getGunCoolingRate()),
                () -> assertEquals(150, battle.getSentryBorderSize()),
                () -> assertTrue(battle.getHideEnemyNames()),
                () -> assertEquals(100.0, battle.getInitialSetups()[0].getX()),
                () -> assertEquals(200.0, battle.getInitialSetups()[0].getY()),
                () -> assertEquals(90.0, battle.getInitialSetups()[0].getHeading()),
                () -> assertNull(battle.getInitialSetups()[1].getX()),
                () -> assertNull(battle.getInitialSetups()[1].getY()),
                () -> assertNull(battle.getInitialSetups()[1].getHeading()));
    }

    @Test
    void legacyScheduleUsesThePreviousBattleDefaults() throws Exception {
        Path file = directory.resolve("legacy.json");
        Files.writeString(file, "[[\"sample.Corners\",\"sample.Fire\"]]");
        ScheduledBattle parsed = GameDriver.parseSchedule(file).getFirst();
        BattleSpecification previous = new BattleSpecification(1,
                new BattlefieldSpecification(800, 600), new RobotSpecification[2]);
        BattleSpecification current = parsed.toBattleSpecification(new RobotSpecification[2]);
        assertEquals(List.of("sample.Corners", "sample.Fire"), parsed.entries());
        assertEquals(previous.getInactivityTime(), current.getInactivityTime());
        assertEquals(previous.getGunCoolingRate(), current.getGunCoolingRate());
        assertEquals(previous.getSentryBorderSize(), current.getSentryBorderSize());
        assertEquals(previous.getHideEnemyNames(), current.getHideEnemyNames());
        assertTrue(parsed.settings().visible());
        assertNull(current.getInitialSetups());
    }

    @Test
    void partitioningPreservesDifferentSettingsForEachBattle() {
        ScheduledBattle first = new ScheduledBattle(List.of("sample.Corners"), settings);
        ScheduledBattle second = new ScheduledBattle(List.of("sample.Fire"));
        List<List<ScheduledBattle>> batches = RobocodeEngineRunner.partitionBattles(List.of(first, second), 3);
        assertEquals(List.of(List.of(first), List.of(second)), batches);
        assertEquals(List.of(), RobocodeEngineRunner.partitionBattles(List.of(), 3));
    }

    @Test
    void leagueAndScheduledBattlePreserveConfiguredRules() {
        League league = League.Generate(List.of(
                new Entry("sample.Corners", 0, "Author 1"),
                new Entry("sample.Fire", 1, "Author 2")), 2, 1, settings);
        assertEquals(1, league.getScheduledBattles().size());
        assertEquals(settings, league.getScheduledBattles().getFirst().settings());
        ScheduledBattle game = new ScheduledBattle(List.of(new Entry("sample.Fire", 0, "Sample Author")), settings);
        try (var reader = Json.createReader(new java.io.StringReader(game.toJson()))) {
            assertEquals(game, ScheduledBattle.fromJson(reader.readObject()));
        }
    }

    @Test
    void invalidRulesAndMismatchedPositionsAreRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> new ScheduledBattle.Settings(
                0, 800, 600, 450, 0.1, 100, false, true));
        assertThrows(IllegalArgumentException.class, () -> new ScheduledBattle(
                List.of("sample.Fire", "sample.Corners"), settings,
                List.of(new ScheduledBattle.StartingPosition(null, null, null))));
    }

    @Test
    void matchupTrackingDistinguishesEntrantsWithTheSameRobotId() {
        Entry first = new Entry("sample.Fire", 0, "Sample Author 1");
        Entry second = new Entry("sample.Fire", 1, "Sample Author 2");
        Entry opponent = new Entry("sample.Corners", 2, "Sample Author 3");
        ScheduledBattle battle = new ScheduledBattle(List.of(first, opponent), settings);
        ArrayList<ScheduledBattle> games = new ArrayList<>(List.of(battle));

        assertSame(first, battle.participants().getFirst());
        assertEquals(List.of(battle), first.findMyGames(games));
        assertTrue(second.findMyGames(games).isEmpty());
        assertEquals(java.util.Set.of(opponent), first.findPastOpponents(games));
    }

}
