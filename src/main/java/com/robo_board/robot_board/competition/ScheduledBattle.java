package com.robo_board.robot_board.competition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;

import robocode.control.BattleSpecification;
import robocode.control.BattlefieldSpecification;
import robocode.control.RobotSetup;
import robocode.control.RobotSpecification;

/** A scheduled battle and its rules, shared by the league and its child JVMs. */
public final class ScheduledBattle {
    private final List<Entry> participants;
    private final Settings settings;
    private final List<StartingPosition> initialSetups;

    public ScheduledBattle(List<String> entries, Settings settings, List<StartingPosition> initialSetups) {
        this(createParticipants(entries), settings, initialSetups);
    }

    public ScheduledBattle(Collection<Entry> entries, Settings settings) {
        this(entries, settings, List.of());
    }

    private ScheduledBattle(Collection<Entry> entries, Settings settings, List<StartingPosition> initialSetups) {
        this.participants = List.copyOf(entries);
        this.settings = Objects.requireNonNull(settings);
        this.initialSetups = List.copyOf(initialSetups);
        if (participants.isEmpty() || participants.stream().anyMatch(entry -> entry.getRobotName().isBlank())) {
            throw new IllegalArgumentException("A scheduled battle needs non-empty robot identifiers");
        }
        if (!initialSetups.isEmpty() && initialSetups.size() != participants.size()) {
            throw new IllegalArgumentException("Starting positions must match the number of entries");
        }
    }

    private static Collection<Entry> createParticipants(List<String> entries) {
        List<Entry> participants = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            participants.add(new Entry(Objects.requireNonNull(entries.get(i)), i,
                    "Sample Author " + (i + 1)));
        }
        return participants;
    }

    List<Entry> participants() {
        return participants;
    }

    public List<String> entries() {
        return participants.stream().map(Entry::getRobotName).toList();
    }

    public Settings settings() {
        return settings;
    }

    public List<StartingPosition> initialSetups() {
        return initialSetups;
    }

    @Override
    public String toString() {
        return entries().toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScheduledBattle battle && entries().equals(battle.entries())
                && settings.equals(battle.settings) && initialSetups.equals(battle.initialSetups);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries(), settings, initialSetups);
    }

    public ScheduledBattle(List<String> entries, Settings settings) {
        this(entries, settings, List.of());
    }

    public ScheduledBattle(List<String> entries) {
        this(entries, Settings.DEFAULT);
    }

    public JsonObject toJsonObject() {
        JsonArrayBuilder robots = Json.createArrayBuilder();
        entries().forEach(robots::add);
        JsonArrayBuilder positions = Json.createArrayBuilder();
        initialSetups.forEach(position -> positions.add(position.toJsonObject()));
        return Json.createObjectBuilder()
                .add("entries", robots)
                .add("battleRules", settings.toJsonObject())
                .add("visible", settings.visible())
                .add("initialSetups", positions)
                .build();
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    public static ScheduledBattle fromJson(JsonValue value) {
        // Accept schedules written before battle settings were included.
        if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            return new ScheduledBattle(value.asJsonArray().getValuesAs(JsonString.class)
                    .stream().map(JsonString::getString).toList());
        }
        JsonObject game = value.asJsonObject();
        List<String> entries = game.getJsonArray("entries").getValuesAs(JsonString.class)
                .stream().map(JsonString::getString).toList();
        JsonObject rules = game.containsKey("battleRules")
                ? game.getJsonObject("battleRules") : Settings.DEFAULT.toJsonObject();
        Settings settings = Settings.fromJson(rules, game.getBoolean("visible", true));
        List<StartingPosition> positions = game.containsKey("initialSetups")
                ? game.getJsonArray("initialSetups").getValuesAs(JsonObject.class)
                        .stream().map(StartingPosition::fromJson).toList()
                : List.of();
        return new ScheduledBattle(entries, settings, positions);
    }

    /** Robots must be resolved in the same order as entries for initial setups to match. */
    public BattleSpecification toBattleSpecification(RobotSpecification[] robots) {
        if (robots.length != participants.size()) {
            throw new IllegalArgumentException("Resolved robots must match the scheduled entries");
        }
        RobotSetup[] positions = initialSetups.isEmpty() ? null : initialSetups.stream()
                .map(position -> new RobotSetup(position.x(), position.y(), position.heading()))
                .toArray(RobotSetup[]::new);
        return new BattleSpecification(new BattlefieldSpecification(settings.battlefieldWidth(),
                settings.battlefieldHeight()), settings.numRounds(), settings.inactivityTime(),
                settings.gunCoolingRate(), settings.sentryBorderSize(), settings.hideEnemyNames(),
                robots, positions);
    }

    public static void ShowCollisions(ArrayList<ScheduledBattle> games, ArrayList<Entry> entries) {
        for (Entry entry : entries) {
            // how many times each opponent shows up in my matches
            HashMap<Entry, Integer> opponentsCount = new HashMap<>();

            for (Entry opponent : entry.findPastOpponents(games)) {
                opponentsCount.put(opponent, 0);
                for (ScheduledBattle game : entry.findMyGames(games)) {
                    if (game.participants().contains(opponent)) {
                        opponentsCount.put(opponent, opponentsCount.get(opponent) + 1);
                    }
                }
            }

            if (opponentsCount.values().contains(2)) {
                System.out.printf("Opponent Count for entry %s:%n", entry);
                for (Entry opponent : opponentsCount.keySet()) {
                    System.out.printf("\t%s: %d%n", opponent, opponentsCount.get(opponent));
                }
            }
        }
    }

    public record Settings(int numRounds, int battlefieldWidth, int battlefieldHeight,
            long inactivityTime, double gunCoolingRate, int sentryBorderSize,
            boolean hideEnemyNames, boolean visible) {
        public static final Settings DEFAULT = new Settings(1, 1000, 1000, 450, 0.1, 200, false, true);

        public Settings {
            if (numRounds < 1 || battlefieldWidth < 400 || battlefieldWidth > 5000
                    || battlefieldHeight < 400 || battlefieldHeight > 5000 || inactivityTime < 1
                    || !Double.isFinite(gunCoolingRate) || gunCoolingRate < 0.1 || sentryBorderSize < 50) {
                throw new IllegalArgumentException("Invalid battle settings");
            }
        }

        JsonObject toJsonObject() {
            return Json.createObjectBuilder()
                    .add("numRounds", numRounds)
                    .add("battlefieldWidth", battlefieldWidth)
                    .add("battlefieldHeight", battlefieldHeight)
                    .add("inactivityTime", inactivityTime)
                    .add("gunCoolingRate", gunCoolingRate)
                    .add("sentryBorderSize", sentryBorderSize)
                    .add("hideEnemyNames", hideEnemyNames)
                    .build();
        }

        static Settings fromJson(JsonObject rules, boolean visible) {
            return new Settings(rules.getInt("numRounds"), rules.getInt("battlefieldWidth"),
                    rules.getInt("battlefieldHeight"), rules.getJsonNumber("inactivityTime").longValueExact(),
                    rules.getJsonNumber("gunCoolingRate").doubleValue(), rules.getInt("sentryBorderSize"),
                    rules.getBoolean("hideEnemyNames"), visible);
        }
    }

    /** Null coordinates or heading request Robocode's random placement. Heading is in degrees. */
    public record StartingPosition(Double x, Double y, Double heading) {
        public StartingPosition {
            if ((x != null && !Double.isFinite(x)) || (y != null && !Double.isFinite(y))
                    || (heading != null && !Double.isFinite(heading))) {
                throw new IllegalArgumentException("Starting positions must contain finite values");
            }
        }

        JsonObject toJsonObject() {
            JsonObjectBuilder position = Json.createObjectBuilder();
            addCoordinate(position, "x", x);
            addCoordinate(position, "y", y);
            addCoordinate(position, "heading", heading);
            return position.build();
        }

        private static void addCoordinate(JsonObjectBuilder builder, String name, Double value) {
            if (value == null) {
                builder.addNull(name);
            } else {
                builder.add(name, value);
            }
        }

        static StartingPosition fromJson(JsonObject position) {
            return new StartingPosition(coordinate(position, "x"), coordinate(position, "y"),
                    coordinate(position, "heading"));
        }

        private static Double coordinate(JsonObject position, String name) {
            return position.isNull(name) ? null : position.getJsonNumber(name).doubleValue();
        }
    }
}
