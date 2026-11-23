package com.robo_board.robot_board.competition;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import robocode.BattleResults;

class TournamentTests {

    private static final Path ROBOCODE_HOME = Path.of("robocode").toAbsolutePath().normalize();

    @Test
    void tournamentSeedsTop16FromLeague() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            names.add("sample." + i);
        }
        League league = League.Generate(names, 4, 1);
        Tournament tournament = new Tournament(league, ROBOCODE_HOME);

        assertEquals(16, tournament.getSeeds().size());
        assertEquals(Tournament.Stage.ROUND_OF_16, tournament.getCurrentStage());
        assertEquals(8, tournament.getCurrentSchedule().size());
    }

    @Test
    void bracketProgressesToChampionWithFakeResults() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            names.add("sample." + i);
        }
        League league = League.Generate(names, 4, 1);
        Tournament tournament = new Tournament(league, ROBOCODE_HOME);

        // Always let entryA (the first listed human) win every game.
        while (!tournament.isComplete()) {
            List<ScheduledBattle> games = tournament.getCurrentSchedule();
            for (ScheduledBattle game : games) {
                tournament.recordGameResult(game, fakeWinForFirstHuman(game));
            }
            if (tournament.isStageComplete()) {
                tournament.advanceStage();
            }
        }

        assertEquals(Tournament.Stage.COMPLETE, tournament.getCurrentStage());
        assertFalse(tournament.getFinalRankings().isEmpty());
        assertEquals(16, tournament.getFinalRankings().size());
    }

    @Test
    void drawnGameIsReplayed() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            names.add("sample." + i);
        }
        League league = League.Generate(names, 4, 1);
        Tournament tournament = new Tournament(league, ROBOCODE_HOME);

        ScheduledBattle game = tournament.getCurrentSchedule().getFirst();
        tournament.recordGameResult(game, drawnResult(game));

        // After a draw the same match still needs games, and the schedule grew by one.
        assertFalse(tournament.isStageComplete());
        assertEquals(8, tournament.getCurrentSchedule().size());
    }

    @Test
    void rejectsTooFewEntrants() {
        League league = League.Generate(List.of("sample.SpinBot", "sample.Fire"), 2, 1);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Tournament(league, ROBOCODE_HOME));
        assertTrue(exception.getMessage().contains("16"));
    }

    private static List<BattleResults> fakeWinForFirstHuman(ScheduledBattle game) {
        List<String> entries = game.entries();
        String firstHuman = entries.get(0);
        String secondHuman = entries.get(1);
        return List.of(
                new BattleResults(firstHuman, 1, 100, 50, 0, 50, 0, 0, 0, 1, 0, 0),
                new BattleResults(secondHuman, 2, 50, 25, 0, 25, 0, 0, 0, 0, 1, 0),
                new BattleResults("sentry.SentryBot", 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }

    private static List<BattleResults> drawnResult(ScheduledBattle game) {
        List<String> entries = game.entries();
        String firstHuman = entries.get(0);
        String secondHuman = entries.get(1);
        return List.of(
                new BattleResults(firstHuman, 1, 100, 50, 0, 50, 0, 0, 0, 1, 0, 0),
                new BattleResults(secondHuman, 1, 100, 50, 0, 50, 0, 0, 0, 1, 0, 0),
                new BattleResults("sentry.SentryBot", 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }
}
