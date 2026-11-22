package com.robo_board.robot_board.competition;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;

import javax.json.Json;

import org.junit.jupiter.api.Test;

import robocode.BattleResults;

class LeagueJsonTests {
    @Test
    void emptyLeagueIncludesItsDefaultSettings() {
        var json = new League().toJsonObject();
        assertTrue(json.getJsonArray("entries").isEmpty());
        assertTrue(json.getJsonArray("games").isEmpty());
        var settings = json.getJsonObject("battleSettings");
        assertEquals(ScheduledBattle.Settings.DEFAULT,
                ScheduledBattle.Settings.fromJson(settings, settings.getBoolean("visible")));
    }

    @Test
    void leagueExportIncludesConfiguredRulesAndAccumulatedResults() {
        var settings = new ScheduledBattle.Settings(3, 1200, 900, 700, 0.3, 150, true, false);
        League league = League.Generate(List.of("sample.Fire", "sample.Corners"), 2, 1, settings);
        var result = new BattleResults("sample.Fire", 1, 100, 20, 30, 40, 5, 4, 1, 2, 3, 4);
        league.recordBattleResults(List.of(result));
        league.recordBattleResults(List.of(result));

        var json = league.toJsonObject();
        var exportedSettings = json.getJsonObject("battleSettings");
        assertEquals(settings, ScheduledBattle.Settings.fromJson(exportedSettings,
                exportedSettings.getBoolean("visible")));
        assertEquals(2, json.getJsonArray("entries").size());
        var entry = json.getJsonArray("entries").getJsonObject(0);
        assertEquals("sample.Fire", entry.getString("robotName"));
        assertEquals(0, entry.getInt("index"));
        assertEquals(1, entry.getInt("gamesScheduled"));
        assertEquals(Json.createObjectBuilder()
                .add("teamLeaderName", "sample.Fire").add("rank", 0)
                .add("score", 200).add("survival", 40).add("lastSurvivorBonus", 60)
                .add("bulletDamage", 80).add("bulletDamageBonus", 10)
                .add("ramDamage", 8).add("ramDamageBonus", 2)
                .add("firsts", 4).add("seconds", 6).add("thirds", 8).build(),
                entry.getJsonObject("stats"));
        assertEquals(1, json.getJsonArray("games").size());
        assertEquals(league.getScheduledBattles().getFirst(),
                ScheduledBattle.fromJson(json.getJsonArray("games").getJsonObject(0)));
        try (var reader = Json.createReader(new StringReader(league.toJson()))) {
            assertEquals(json, reader.readObject());
        }
    }

    @Test
    void participantIndicesDistinguishEntriesWithTheSameRobotName() {
        League league = League.Generate(List.of("sample.Fire", "sample.Fire"), 2, 1);
        var json = league.toJsonObject();
        var entries = json.getJsonArray("entries");
        var game = json.getJsonArray("games").getJsonObject(0);
        var indices = game.getJsonArray("participantIndices");
        assertEquals(2, indices.size());
        assertNotEquals(indices.getInt(0), indices.getInt(1));
        var participants = league.getScheduledBattles().getFirst().participants();
        for (int i = 0; i < indices.size(); i++) {
            assertEquals(participants.get(i).getIndex(), indices.getInt(i));
            assertEquals(game.getJsonArray("entries").getString(i),
                    entries.getJsonObject(indices.getInt(i)).getString("robotName"));
        }
    }

    @Test
    void entryExportEscapesRobotNamesAndIncludesInitialStats() {
        Entry entry = new Entry("robot.\"Quoted\"\\Name\n\u03bb", 7);
        entry.gamesScheduled = 3;
        try (var reader = Json.createReader(new StringReader(entry.toJson()))) {
            var json = reader.readObject();
            assertEquals(entry.toJsonObject(), json);
            assertEquals(entry.getRobotName(), json.getString("robotName"));
            assertEquals(7, json.getInt("index"));
            assertEquals(3, json.getInt("gamesScheduled"));
            assertEquals(entry.getRobotName(), json.getJsonObject("stats").getString("teamLeaderName"));
            assertEquals(0, json.getJsonObject("stats").getInt("score"));
        }
    }
}
