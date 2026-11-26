package com.robo_board.robot_board.bracket;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class Bracket {

    List<BracketMatch> matches;
    String[] seeds;

    Status status = Status.EMPTY;

    /**
     * Creates a complete 16-player double-elimination bracket.
     *
     * @param inSeeds player names ordered from seed 1 through seed 16
     */
    public Bracket(String[] inSeeds) {
        this(validateAndCopySeeds(inSeeds), createBracket());
    }

    public Bracket(String[] inSeeds, List<BracketMatch> inMatches) {
        seeds = Objects.requireNonNull(inSeeds, "inSeeds").clone();
        matches = List.copyOf(Objects.requireNonNull(inMatches, "inMatches"));
        checkWiring();
        fillSeeds();
        updateReadyMatches();
    }

    /**
     * Generates the match graph for a 16-player double-elimination tournament.
     * Match IDs 1-15 are the winners bracket, 16-29 are the losers bracket,
     * 30 is the grand final, and 31 is the conditional bracket-reset final.
     */
    public static List<BracketMatch> createBracket() {
        return List.of(
                // Winners bracket - round 1
                match(1, BracketSide.WINNERS, 1, seed(1), seed(16), false),
                match(2, BracketSide.WINNERS, 1, seed(8), seed(9), false),
                match(3, BracketSide.WINNERS, 1, seed(5), seed(12), false),
                match(4, BracketSide.WINNERS, 1, seed(4), seed(13), false),
                match(5, BracketSide.WINNERS, 1, seed(6), seed(11), false),
                match(6, BracketSide.WINNERS, 1, seed(3), seed(14), false),
                match(7, BracketSide.WINNERS, 1, seed(7), seed(10), false),
                match(8, BracketSide.WINNERS, 1, seed(2), seed(15), false),

                // Winners bracket - rounds 2 through 4
                match(9, BracketSide.WINNERS, 2, winner(1), winner(2), false),
                match(10, BracketSide.WINNERS, 2, winner(3), winner(4), false),
                match(11, BracketSide.WINNERS, 2, winner(5), winner(6), false),
                match(12, BracketSide.WINNERS, 2, winner(7), winner(8), false),
                match(13, BracketSide.WINNERS, 3, winner(9), winner(10), false),
                match(14, BracketSide.WINNERS, 3, winner(11), winner(12), false),
                match(15, BracketSide.WINNERS, 4, winner(13), winner(14), false),

                // Losers bracket - rounds 1 and 2
                match(16, BracketSide.LOSERS, 1, loser(1), loser(2), false),
                match(17, BracketSide.LOSERS, 1, loser(3), loser(4), false),
                match(18, BracketSide.LOSERS, 1, loser(5), loser(6), false),
                match(19, BracketSide.LOSERS, 1, loser(7), loser(8), false),
                match(20, BracketSide.LOSERS, 2, winner(16), loser(10), false),
                match(21, BracketSide.LOSERS, 2, winner(17), loser(9), false),
                match(22, BracketSide.LOSERS, 2, winner(18), loser(12), false),
                match(23, BracketSide.LOSERS, 2, winner(19), loser(11), false),

                // Losers bracket - rounds 3 through 6
                match(24, BracketSide.LOSERS, 3, winner(20), winner(21), false),
                match(25, BracketSide.LOSERS, 3, winner(22), winner(23), false),
                match(26, BracketSide.LOSERS, 4, winner(24), loser(14), false),
                match(27, BracketSide.LOSERS, 4, winner(25), loser(13), false),
                match(28, BracketSide.LOSERS, 5, winner(26), winner(27), false),
                match(29, BracketSide.LOSERS, 6, winner(28), loser(15), false),

                // Global rounds 7 and 8: grand final and conditional reset
                match(30, BracketSide.FINALS, 7, winner(15), winner(29), false),
                match(31, BracketSide.FINALS, 8, winner(30), loser(30), true));
    }

    private static BracketMatch match(int id, BracketSide side, int round,
            SlotSources slot1, SlotSources slot2, boolean reset) {
        return new BracketMatch(id, side, round, new Slot(slot1), new Slot(slot2), reset);
    }

    private static SlotSources seed(int seedNumber) {
        return new SlotSources.Seed(seedNumber);
    }

    private static SlotSources winner(int matchId) {
        return new SlotSources.WinnerOf(matchId);
    }

    private static SlotSources loser(int matchId) {
        return new SlotSources.LoserOf(matchId);
    }

    private static String[] validateAndCopySeeds(String[] inSeeds) {
        Objects.requireNonNull(inSeeds, "inSeeds");
        if (inSeeds.length != 16) {
            throw new IllegalArgumentException(
                    "A generated double-elimination bracket requires exactly 16 seeds; received "
                            + inSeeds.length);
        }

        String[] copy = inSeeds.clone();
        Set<String> uniqueSeeds = new HashSet<>();
        for (int i = 0; i < copy.length; i++) {
            String seed = copy[i];
            if (seed == null || seed.isBlank()) {
                throw new IllegalArgumentException("Seed " + (i + 1) + " must have a player name");
            }
            if (!uniqueSeeds.add(seed)) {
                throw new IllegalArgumentException("Duplicate player name in seeds: " + seed);
            }
        }
        return copy;
    }

    private void checkWiring() {

        Set<SlotSources> usedSources = new HashSet<>();
        for (BracketMatch match : matches) {
            for (Slot slot : new Slot[]{match.getSlot1(), match.getSlot2()}) {
                if(!usedSources.add(slot.getSlotSource())) {
                    throw new IllegalArgumentException("Duplicate slot source found: " + slot.getSlotSource());
                }
            }
        }

        // Logic to check the the bracket is created correctly and no overlap is present in matches.
        for (int i = 0; i < matches.size(); i++) {
            for (int j = i + 1; j < matches.size(); j++) {
                if (matches.get(i).getMatchId() == matches.get(j).getMatchId()) {
                    throw new IllegalArgumentException("Duplicate match ID found: " + matches.get(i).getMatchId());
                }
            }
            if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.WinnerOf winnerOf) {
                int sourceMatchId = winnerOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            } else if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.LoserOf loserOf) {
                int sourceMatchId = loserOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            }
            if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.WinnerOf winnerOf) {
                int sourceMatchId = winnerOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            } else if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.LoserOf loserOf) {
                int sourceMatchId = loserOf.matchID();
                BracketMatch sourceMatch = findMatchByID(sourceMatchId);
                if (sourceMatch == null) {
                    throw new IllegalArgumentException("Source match ID " + sourceMatchId + " for match ID " + matches.get(i).getMatchId() + " does not exist.");
                }
            }
            
            if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.Seed seed && (seed.seedNumber() < 1 || seed.seedNumber() > seeds.length)) {
                throw new IllegalArgumentException("Seed number " + seed.seedNumber() + " for match ID " + matches.get(i).getMatchId() + " exceeds the number of seeds provided.");
            }
            if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.Seed seed && (seed.seedNumber() < 1 || seed.seedNumber() > seeds.length)) {
                throw new IllegalArgumentException("Seed number " + seed.seedNumber() + " for match ID " + matches.get(i).getMatchId() + " exceeds the number of seeds provided.");
            }
        }
        
    }

    private void fillSeeds() {
        // Implement the logic to fill the seeds into the bracket
        for (int i = 1; i <= seeds.length; i++) {
            for (BracketMatch match : matches) {
                if (match.getSlot1().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() == i) {
                    match.getSlot1().setRobotName(seeds[i-1]);
                }
                if (match.getSlot2().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() == i) {
                    match.getSlot2().setRobotName(seeds[i-1]);
                }
            }
        }
    }

    private void updateReadyMatches() {
        // Implement the logic to update the status of matches that are ready to be played
        for (BracketMatch match : matches) {
            if (match.getSlot1().hasRobot() && match.getSlot2().hasRobot() && match.getMatchStatus() == MatchStatus.NOT_STARTED) {
                match.setMatchStatus(MatchStatus.READY);
            }
        }
    }




    public List<BracketMatch> getMatches() {
        return matches;
    }

    public BracketMatch findMatchByID(int matchId) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).getMatchId() == matchId) {
                return matches.get(i);
            }
        }
        return null; // Match not found
    }

    public void startMatch(int matchId) {
        //Start match and update the bracket
        BracketMatch match = findMatchByID(matchId);
        if (match != null && match.getMatchStatus() == MatchStatus.READY) {
            match.setMatchStatus(MatchStatus.IN_PROGRESS);
        }
        else {
            throw new IllegalArgumentException("Match with ID " + matchId + " is not ready to start.");
        }
    }

    public void endMatch(int matchId, String winnerRobotName) {
        // End the match and update the bracket
        BracketMatch match = findMatchByID(matchId);
        if (match == null) {
            throw new IllegalArgumentException("Match with ID " + matchId + " not found.");
        }
        if (match.getMatchStatus() != MatchStatus.IN_PROGRESS && match.getMatchStatus() != MatchStatus.READY ) {
            throw new IllegalArgumentException("Match with ID " + match.getMatchId() + " is not in progress.");
        }

        if (match.hasRobot(winnerRobotName) == false) {
            throw new IllegalArgumentException("Winner robot " + winnerRobotName + " is not part of match with ID " + match.getMatchId() + ".");
        }
       
        
        match.setWinner(winnerRobotName); // Mark the winner
        match.setLoser(match.otherRobot(winnerRobotName)); // Mark the loser
        match.setMatchStatus(MatchStatus.COMPLETED);

        // Update the next match in the bracket with the winner
        for (BracketMatch m : matches) {
            if (m.getSlot1().getSlotSource() instanceof SlotSources.WinnerOf winnerOf && winnerOf.matchID() == match.getMatchId()) {
                m.getSlot1().setRobotName(winnerRobotName);
                //break; // Exit the loop after updating the next match
            }
            else if (m.getSlot2().getSlotSource() instanceof SlotSources.WinnerOf winnerOf && winnerOf.matchID() == match.getMatchId()) {
                m.getSlot2().setRobotName(winnerRobotName);
                //break; // Exit the loop after updating the next match
            }

            if (m.getSlot1().getSlotSource() instanceof SlotSources.LoserOf loserOf && loserOf.matchID() == match.getMatchId()) {
                m.getSlot1().setRobotName(match.otherRobot(winnerRobotName));
                //break; // Exit the loop after updating the next match
            }
            else if (m.getSlot2().getSlotSource() instanceof SlotSources.LoserOf loserOf && loserOf.matchID() == match.getMatchId()) {
                m.getSlot2().setRobotName(match.otherRobot(winnerRobotName));
                //break; // Exit the loop after updating the next match
            }
        }

        // Update the status of matches that are ready to be played
        updateReadyMatches();

        // Checking if a second championship match is needed
        for (BracketMatch m : matches) {
            // Check if the match is a grand final and if the winner is the winner of the Winners bracket
            if (m.isReset() && m.getSlot1().getSlotSource() instanceof SlotSources.WinnerOf winnersFinal && winnersFinal.matchID() == match.getMatchId()) {
                    // Slot 1 is always the winners-bracket champion, who has never lost.
                    String unbeatenRobot = match.getSlot1().getRobotName();
                    if (winnerRobotName.equals(unbeatenRobot)) {
                        m.setMatchStatus(MatchStatus.CANCELLED); // No need for a second match, the loser already got their second chance
                    }
            }
        }
        

    }

    public boolean isFinished() {
        // Check if all matches in the bracket are completed
        for (BracketMatch match : matches) {
            if (match.getMatchStatus() != MatchStatus.COMPLETED && match.getMatchStatus() != MatchStatus.CANCELLED) {
                return false;
            }
        }
        return true;
    }

    public String getChampion() {
        // Return the name of the champion robot
        for (BracketMatch match : matches) {
            if(match.isReset()) {
                if(match.getMatchStatus() == MatchStatus.COMPLETED) {
                    return match.getWinner();
                }
            }
            if (match.getMatchStatus() == MatchStatus.CANCELLED && match.getSlot1().getSlotSource() instanceof SlotSources.WinnerOf winnersFinal) {
                return findMatchByID(winnersFinal.matchID()).getWinner();
            }

        }
        return null; // No champion yet
    }

    private enum Status {
        EMPTY,
        READY,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    public JsonObject toJsonObject() {
        JsonArrayBuilder seedArray = Json.createArrayBuilder();
        for (String seed : seeds) {
            seedArray.add(seed);
        }

        JsonArrayBuilder matchArray = Json.createArrayBuilder();
        for (BracketMatch match : matches) {
            JsonObjectBuilder matchJson = Json.createObjectBuilder()
                    .add("matchId", match.getMatchId())
                    .add("matchGroup", match.getMatchGroup().name())
                    .add("round", match.getRound())
                    .add("matchStatus", match.getMatchStatus().name())
                    .add("slot1Source", slotSourceToJsonObject(match.getSlot1().getSlotSource()))
                    .add("slot2Source", slotSourceToJsonObject(match.getSlot2().getSlotSource()))
                    .add("isReset", match.isReset());
            addNullableString(matchJson, "winner", match.getWinner());
            addNullableString(matchJson, "loser", match.getLoser());
            addNullableString(matchJson, "slot1", match.getSlot1().getRobotName());
            addNullableString(matchJson, "slot2", match.getSlot2().getRobotName());
            matchArray.add(matchJson);
        }

        JsonObjectBuilder bracketJson = Json.createObjectBuilder()
                .add("seeds", seedArray)
                .add("matches", matchArray)
                .add("finished", isFinished());
        addNullableString(bracketJson, "champion", getChampion());
        return bracketJson.build();
    }

    public String toJson() {
        return toJsonObject().toString();
    }

    private static JsonObject slotSourceToJsonObject(SlotSources source) {
        if (source instanceof SlotSources.Seed seed) {
            return Json.createObjectBuilder()
                    .add("type", "SEED")
                    .add("seedNumber", seed.seedNumber())
                    .build();
        }
        if (source instanceof SlotSources.WinnerOf winnerOf) {
            return Json.createObjectBuilder()
                    .add("type", "WINNER_OF")
                    .add("matchId", winnerOf.matchID())
                    .build();
        }
        if (source instanceof SlotSources.LoserOf loserOf) {
            return Json.createObjectBuilder()
                    .add("type", "LOSER_OF")
                    .add("matchId", loserOf.matchID())
                    .build();
        }
        throw new IllegalArgumentException("Unsupported slot source: " + source);
    }

    private static void addNullableString(JsonObjectBuilder builder, String key, String value) {
        if (value == null) {
            builder.addNull(key);
        } else {
            builder.add(key, value);
        }
    }

    // public JSON fromJsonObject(JSON json) {
    //     // Convert a JSON object to a bracket
    //     // Implement the logic to convert JSON to a bracket
    //     return null;
    // }

}
