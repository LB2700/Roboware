package com.robo_board.robot_board.competition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import javax.json.Json;

import org.junit.jupiter.api.Test;

class CompetitionDriverJsonTests {

    @Test
    void competitionJsonNestsLeagueAndKeepsEntriesAtTopLevel() {
        var league = Json.createObjectBuilder()
                .add("entries", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("robotName", "example.Robot")))
                .add("games", Json.createArrayBuilder())
                .add("battleSettings", Json.createObjectBuilder())
                .build();

        var json = CompetitionDriver.buildCompetitionJson(league, null);

        assertEquals(Set.of("league", "bracket", "entries"), json.keySet());
        assertTrue(json.get("entries").getValueType() == javax.json.JsonValue.ValueType.ARRAY);
        assertTrue(json.get("league").getValueType() == javax.json.JsonValue.ValueType.OBJECT);
        assertFalse(json.getJsonObject("league").containsKey("entries"));
        assertEquals(CompetitionDriver.LEAGUE_TARGET_TPS,
                json.getJsonObject("league").getInt("targetTps"));
        assertTrue(json.isNull("bracket"));
    }
}
