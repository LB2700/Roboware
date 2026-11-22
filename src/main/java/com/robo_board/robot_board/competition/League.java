package com.robo_board.robot_board.competition;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import robocode.BattleResults;


public class League {

    private static final ArrayList<String> SAMPLE_ENTRANTS = new ArrayList<String>(List.of( 
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
        "sample.VelociRobot",
        "sample.VelociRobot"
    ));
    
    private ArrayList<Entry> entries;
    private ArrayList<ScheduledBattle> games;

    public static League Generate(List<String> EntryNames, final int GAME_SIZE, final int GAME_COUNT_PER_ENTRY) {
        return Generate(EntryNames, GAME_SIZE, GAME_COUNT_PER_ENTRY, ScheduledBattle.Settings.DEFAULT);
    }

    public static League Generate(List<String> EntryNames, final int GAME_SIZE, final int GAME_COUNT_PER_ENTRY,
            ScheduledBattle.Settings settings) {
        final int ENTRY_COUNT = EntryNames.size();

        League newLeague = new League();
        newLeague.battleSettings = java.util.Objects.requireNonNull(settings);
        Random random = new Random();

        for (int i = 0; i < ENTRY_COUNT; i++) {
            newLeague.entries.add(new Entry(EntryNames.get(i), i));
        }

        // ensure entries are matched uniformly the whole way up. Only matches entries with less than targetScheduledCount amount of games scheduled, 
        // then progressively increment targetScheduledCount until it reaches GAME_COUNT_PER_ENTRY
        int targetScheduledCount = 0;
        int collisionsAllowed = 0;

        while(getLowestGameCount(newLeague.entries) < GAME_COUNT_PER_ENTRY) {
        //for (int j = 0; j < 98; j++) {

            // keep attempting to generate games until the minimum number of games per player reaches the desired count

            ArrayList<Entry> onlyEntriesWithLowestGames = getEntriesWithLessGamesThan(targetScheduledCount, newLeague.entries);
            ArrayList<Entry> nextGame = new ArrayList<>();


            boolean abortGame = false;

            // start at index 0 (relative to some random starting position to prevent final players getting undesirable matchings)
            int i = 0;
            int randOffset = random.nextInt(ENTRY_COUNT);

            // repeat this until a valid game has been created, or restrictions are loosened and we try again
            // loop through every player and attempt to put them in this game
            while (nextGame.size() < GAME_SIZE) {
                //System.out.println(onlyEntriesWithLowestGames);

                // if we haven't iterated through this player yet
                //System.out.printf("Entry Pool: %s%n", onlyEntriesWithLowestGames);

                if (i < onlyEntriesWithLowestGames.size()) {
                    int index = (i++ + randOffset) % onlyEntriesWithLowestGames.size(); // index relative to random starting point
                
                    //System.out.printf("index %d%n", index);

                    // looping through entries. Think of this as the 'current' entry. All checks are based on this entry
                    Entry entry = onlyEntriesWithLowestGames.get(index);

                    if (nextGame.contains(entry)) continue; // skip if already in game, can't play against yourself

                    // count number of 'collisions'
                    // a collision is an instance where an entry is already put into this match has already played against the current player
                    int entryCollisions = 0;
                    for (Entry otherEntry : nextGame) {
                        if (otherEntry.findPastOpponents(newLeague.games).contains(entry)) {
                            // skip this entry from entering this game because the other entry has seen me before
                            entryCollisions++;
                        }
                    }

                    // the desired amount of collisions initially is zero, this is one of the restrictions that get loosened as the algorithm
                    // fails to make games
                    // if too many collisions occur, skip this entry they are not allowed play this game against the currently selected entries
                    if (entryCollisions > collisionsAllowed) {
                        continue;
                    }
 
                    // if the entry already has all their games scheduled. they CANNOT play anymore. This is imperitive to the league
                    if (entry.gamesScheduled >= GAME_COUNT_PER_ENTRY) {
                        continue;
                    }
                    //System.out.printf("Selected Entry %s, for game %s%n", entry, nextGame);

                    // if these checks are passed, they entry can play this game
                    entry.gamesScheduled++;
                    nextGame.add(entry);
                    collisionsAllowed = 0;
                } else {
                    // the code reaches this point when all the valid players have been ran iterated over and a full game was not 
                    // constructed. The following logic is used to progressively loosen the restrictions

                    // System.out.println("Exhausted Entry Pool ");
                    // System.out.printf("Entry Pool: %s%n", onlyEntriesWithLowestGames);
                    // System.out.printf("Lowest Game Count: %d%n", getLowestGameCount(newLeague.entries));

                    if (onlyEntriesWithLowestGames.size() < GAME_SIZE && targetScheduledCount != GAME_COUNT_PER_ENTRY) {
                        onlyEntriesWithLowestGames = getEntriesWithLessGamesThan(++targetScheduledCount, newLeague.entries);
                        collisionsAllowed = 0;
                        System.out.printf("entry pool is smaller than %d, increment targetScheduledCount, %d%n", GAME_SIZE, targetScheduledCount);
                        System.out.printf("reset collisionsAllowed %n");
                    } else if (collisionsAllowed == GAME_SIZE) { //increase collision allowed first, if reaches maxium increment targetScheduledCount
                        onlyEntriesWithLowestGames = getEntriesWithLessGamesThan(++targetScheduledCount, newLeague.entries);
                        collisionsAllowed = 0;
                        System.out.printf("collisionsAllowed is maxed out, increment targetScheduledCount, %d%n", targetScheduledCount);
                        System.out.printf("reset collisionsAllowed %n");
                        if (targetScheduledCount == GAME_COUNT_PER_ENTRY) {
                            // all entrants have no more games to play except for a set of entrants less than GAME_COUNT
                            // a game (or games) needs to be played with less than the specified game count

                            // example; 13 entrants, 3 games of 4. entrant #12 is left by themselves
                            // [0, 1, 2, 3]
                            // [7, 8, 9, 10]
                            // [4, 5, 6, 11]
                            // [12]

                            // fix, take 1 player from (GAME_COUNT - P - 1) games and match them in the final game. 
                            // Where P is the amount of players in the final game. P < GAME_COUNT
                            // result:
                            // [0, 1, 2, 3]
                            // [7, 8, 9]
                            // [4, 5, 6]
                            // [12, 10, 11]

                            if (nextGame.size() < GAME_SIZE - 1) { 
                                System.out.println(newLeague.games.size());
                                System.out.println(GAME_SIZE);
                                System.out.println(nextGame.size());

                                System.out.println(newLeague.games.size() + nextGame.size() + 1 - GAME_SIZE);
                                for (int j = newLeague.games.size() + nextGame.size() + 1 - GAME_SIZE; j < newLeague.games.size(); j++) {
                                    System.out.println(j);
                                    ScheduledBattle previousGame = newLeague.games.get(j);
                                    ArrayList<Entry> remainingEntries = new ArrayList<>(previousGame.participants());
                                    Entry entry = remainingEntries.remove(0);
                                    newLeague.games.set(j, new ScheduledBattle(remainingEntries, previousGame.settings()));
                                    nextGame.add(entry);
                                }
                            }
                            break;
                        }
                            
                    } else {
                        collisionsAllowed++;
                        System.out.printf("Increment collisionsAllowed: %d%n", collisionsAllowed);
                    }

                    i = 0;
                }

                if (nextGame.size() == GAME_SIZE) {
                    break;
                }
            }

            if (!abortGame) {
                //System.out.printf("Game Scheduled: %s%n", nextGame);
                newLeague.scheduleGame(nextGame);
            } 
        }

        return newLeague;
    }

    private static int getLowestGameCount(ArrayList<Entry> entries) {
        ArrayList<Entry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort((a, b) -> Integer.compare(a.gamesScheduled, b.gamesScheduled));

        return sortedEntries.get(0).gamesScheduled;
    }

    private static ArrayList<Entry> getEntriesWithLessGamesThan(int targetScheduledCount, ArrayList<Entry> entries) {
        ArrayList<Entry> onlyEntriesWithLowestGames = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.gamesScheduled <= targetScheduledCount) {
                onlyEntriesWithLowestGames.add(entry);
            }
        }
        return onlyEntriesWithLowestGames;
    }

    public static void main() {
       League myLeague = League.Generate(SAMPLE_ENTRANTS, 4, 2);
       myLeague.showSchedule();
    }
        
    public League() {
        entries = new ArrayList<>();
        games =  new ArrayList<>();
    }

    private ScheduledBattle.Settings battleSettings = ScheduledBattle.Settings.DEFAULT;

    public List<ScheduledBattle> getScheduledBattles() {
        return List.copyOf(games);
    }

    /** Immutable robot-name schedule for callers that only need the matchups. */
    public List<List<String>> getSchedule() {
        List<List<String>> schedule = new ArrayList<>();
        for (ScheduledBattle game : games) {
            schedule.add(game.entries());
        }
        return List.copyOf(schedule);
    }

    /** Returns an object containing the games, optionally including each game's battleRules. */
    public JsonObject getScheduleJson(boolean includeBattleRules) {
        JsonArrayBuilder schedule = Json.createArrayBuilder();
        for (ScheduledBattle game : games) {
            JsonObjectBuilder battle = Json.createObjectBuilder(game.toJsonObject());
            if (!includeBattleRules) {
                battle.remove("battleRules");
            }
            schedule.add(battle);
        }
        return Json.createObjectBuilder().add("games", schedule).build();
    }

    /** Encodes all instance state, using entry indices to identify battle participants. */
    public JsonObject toJsonObject() {
        JsonArrayBuilder entrants = Json.createArrayBuilder();
        entries.forEach(entry -> entrants.add(entry.toJsonObject()));

        JsonArrayBuilder battles = Json.createArrayBuilder();
        for (ScheduledBattle game : games) {
            JsonArrayBuilder participantIndices = Json.createArrayBuilder();
            game.participants().forEach(entry -> participantIndices.add(entry.getIndex()));
            battles.add(Json.createObjectBuilder(game.toJsonObject())
                    .add("participantIndices", participantIndices));
        }

        return Json.createObjectBuilder()
                .add("entries", entrants)
                .add("games", battles)
                .add("battleSettings", Json.createObjectBuilder(battleSettings.toJsonObject())
                        .add("visible", battleSettings.visible()))
                .build();
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    /** Called by the competition driver once for each completed battle. */
    public void recordBattleResults(List<BattleResults> results) {
        for (BattleResults result : results) {
            updateResults(result);
        }
    }
    private void updateResults(BattleResults results) {
        //System.out.println(results);

        for (Entry entry : entries) {
            if (entry.getRobotName().equals(results.getTeamLeaderName())) {
                entry.aggregateStats(results);
                //entry.showStats();
                break;
            }
        }
    }

    private void scheduleGame(List<Entry> entries) {
        games.add(new ScheduledBattle(entries, battleSettings));
        //System.out.printf("Scheduling game: %s%n", entries);
    }

    private int getGameCount() {
        return games.size();
    }

    public void showSchedule() {
       for (int i = 0; i < games.size(); i++) {
        System.out.printf("%d. %s%n", i, games.get(i));
       }
    }

    public void showStatsTable() {
        List<Entry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort((first, second) -> Integer.compare(
                second.getStats().getScore(),
                first.getStats().getScore()));

        System.out.printf("%-30s %10s %10s %18s %20s %15s %20s %15s %8s %8s %8s%n",
                "Robot Name", "Score", "Survival", "Last survivor bonus",
                "Bullet damage", "Bullet damage bonus", "Ram damage",
                "Ram damage bonus", "Firsts", "Seconds", "Thirds");
        System.out.println("=".repeat(172));

        for (Entry entry : sortedEntries) {
            BattleResults stats = entry.getStats();
            System.out.printf("%-30s %10d %10d %18d %20d %15d %20d %15d %8d %8d %8d%n",
                    stats.getTeamLeaderName(), stats.getScore(), stats.getSurvival(),
                    stats.getLastSurvivorBonus(), stats.getBulletDamage(),
                    stats.getBulletDamageBonus(), stats.getRamDamage(),
                    stats.getRamDamageBonus(), stats.getFirsts(),
                    stats.getSeconds(), stats.getThirds());
        }
    }

  
}
