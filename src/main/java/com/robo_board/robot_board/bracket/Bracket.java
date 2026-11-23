package com.robo_board.robot_board.bracket;

import java.util.ArrayList;
import java.util.List;

public class Bracket {
    List<BracketMatch> matches;
    String[] seeds;
    

    public Bracket(String[] inSeeds, List<BracketMatch> inMatches) {
        seeds = inSeeds;
        matches = inMatches;
        checkWiring();
        fillSeeds();
        updateReadyMatches();
        createBracket();
    }

    public void createBracket() {
        // Implement the logic to create the bracket based on the seeds
        // This is a placeholder implementation and should be replaced with actual logic
        // For example, you can create matches based on the number of seeds and their positions in the bracket
    }

    private void checkWiring() {
        // Implement the logic to check the wiring of the bracket
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
            
            if (matches.get(i).getSlot1().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() > seeds.length) {
                throw new IllegalArgumentException("Seed number " + seed.seedNumber() + " for match ID " + matches.get(i).getMatchId() + " exceeds the number of seeds provided.");
            }
            if (matches.get(i).getSlot2().getSlotSource() instanceof SlotSources.Seed seed && seed.seedNumber() > seeds.length) {
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
            if (match.getSlot1().hasRobot() && match.getSlot2().hasRobot() && match.getMatchStatus() == MatchStatus.NOT_READY) {
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
            if (m.getSlot1().getSlotSource() instanceof WinnerOf winnerOf && winnerOf.getMatchId() == match.getMatchId()) {
                match.getMatchByID(winnerOf.getMatchId()).setSlot1().setRobotName(winnerRobotName);
                //break; // Exit the loop after updating the next match
            }
            else if (m.getSlot2().getSlotSource() instanceof WinnerOf winnerOf && winnerOf.getMatchId() == match.getMatchId()) {
                match.getMatchByID(winnerOf.getMatchId()).setSlot2().setRobotName(winnerRobotName);
                //break; // Exit the loop after updating the next match
            }

            if (m.getSlot1().getSlotSource() instanceof LoserOf loserOf && loserOf.getMatchId() == match.getMatchId()) {
                match.getMatchByID(loserOf.getMatchId()).setSlot1().setRobotName(match.otherRobot(winnerRobotName));
                //break; // Exit the loop after updating the next match
            }
            else if (m.getSlot2().getSlotSource() instanceof LoserOf loserOf && loserOf.getMatchId() == match.getMatchId()) {
                match.getMatchByID(loserOf.getMatchId()).setSlot2().setRobotName(match.otherRobot(winnerRobotName));
                //break; // Exit the loop after updating the next match
            }
        }

        // Update the status of matches that are ready to be played
        updateReadyMatches();

        // Checking if a second championship match is needed
        for (BracketMatch m : matches) {
            if (m.getMatchGroup() == BracketSide.FINALS && m.getMatchStatus() == MatchStatus.COMPLETED) {
                String finalsWinner = m.getWinner();
                String finalsLoser = m.getLoser();
                for (BracketMatch potentialReset : matches) {
                    if (potentialReset.getMatchGroup() == BracketSide.FINALS && potentialReset.isReset() && potentialReset.hasRobot(finalsLoser)) {
                        // A reset match is needed
                        potentialReset.setSlot1().setRobotName(finalsWinner);
                        potentialReset.setSlot2().setRobotName(finalsLoser);
                        potentialReset.setMatchStatus(MatchStatus.READY);
                        break; // Exit the loop after scheduling the reset match
                    }
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
            if (match.getMatchGroup() == BracketSide.FINALS && match.getMatchStatus() == MatchStatus.COMPLETED) {
                //Grand Final and Reset Final are both in the FINALS side of the brakcet, so we olny take the FINALS match without a reset.
                if (match.isReset()) {
                    continue; // Skip if the match was reset
                }
                return match.getWinner();
            }
        }
        return null; // No champion yet
    }

    // public JSON toJsonObject() {
    //     JSON json = new JSON();
    //     // Convert the bracket to a JSON object
    //     // Implement the logic to convert the bracket to JSON
    //     return json;
    // }

    // public JSON fromJsonObject(JSON json) {
    //     // Convert a JSON object to a bracket
    //     // Implement the logic to convert JSON to a bracket
    //     return null;
    // }

}
