package com.robo_board.robot_board.bracket;


public class BracketMatch {
    private int matchId;
    private BracketSide matchGroup;
    private int round;
    private MatchStatus matchStatus;
    private String winner;
    private String loser;
    private Slot slot1;
    private Slot slot2;
    private boolean isReset;

    public BracketMatch(int inID, BracketSide inGroup, int inRound, Slot inSlot1, Slot inSlot2, boolean inIsReset) {
        this.matchId = inID;
        this.matchGroup = inGroup;
        this.round = inRound;
        this.matchStatus = MatchStatus.NOT_STARTED;
        this.winner = null;
        this.loser = null;
        this.slot1 = inSlot1;
        this.slot2 = inSlot2;
        this.isReset = inIsReset;

    }

    public String[] getRobots() {
        return new String[]{slot1.getRobotName(), slot2.getRobotName()};
    }

    public boolean slotsFilled() {
        return slot1.hasRobot() && slot2.hasRobot();
    }

    public boolean hasRobot(String inRobot) {
        if (slot1.getRobotName() != null && slot1.getRobotName().equals(inRobot)) {
            return true;
        } else if (slot2.getRobotName() != null && slot2.getRobotName().equals(inRobot)) {
            return true;
        }
        return false;
    } 

    public String otherRobot(String inRobot) {
        if (slot1.getRobotName() != null && slot1.getRobotName().equals(inRobot)) {
            return slot2.getRobotName();
        } else if (slot2.getRobotName() != null && slot2.getRobotName().equals(inRobot)) {
            return slot1.getRobotName();
        }
        return null;
    }

    public int getMatchId() {
        return matchId;
    }
    public BracketSide getMatchGroup() {
        return matchGroup;
    }

    public int getRound() {
        return round;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public String getWinner() {
        return winner;
    }

    public String getLoser() {
        return loser;
    }

    public Slot getSlot1() {
        return slot1;
    }

    public Slot getSlot2() {
        return slot2;
    }

    public void setMatchStatus(MatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public void setLoser(String loser) {
        this.loser = loser;
    }

    public boolean isReset() {
        return isReset;
    }

    // public JSON toJsonObject() {
    //     JSON json = new JSON();
    //     json.put("matchId", matchId);
    //     json.put("matchGroup", matchGroup.toString());
    //     json.put("round", round);
    //     json.put("matchStatus", matchStatus.toString());
    //     json.put("winner", winner);
    //     json.put("loser", loser);
    //     json.put("slot1", slot1.getRobotName());
    //     json.put("slot2", slot2.getRobotName());
    //     json.put("isReset", isReset);
    //     return json;
    // }

    // public static BracketMatch fromJsonObject(JSON json) {
    //     int matchId = json.getInt("matchId");
    //     BracketSide matchGroup = BracketSide.valueOf(json.getString("matchGroup"));
    //     int round = json.getInt("round");
    //     MatchStatus matchStatus = MatchStatus.valueOf(json.getString("matchStatus"));
    //     String winner = json.getString("winner");
    //     String loser = json.getString("loser");
    //     Slot slot1 = new Slot(new SlotSources.Seed(0)); // Placeholder, adjust as needed
    //     slot1.setRobotName(json.getString("slot1"));
    //     Slot slot2 = new Slot(new SlotSources.Seed(0)); // Placeholder, adjust as needed
    //     slot2.setRobotName(json.getString("slot2"));
    //     boolean isReset = json.getBoolean("isReset");

    //     BracketMatch match = new BracketMatch(matchId, matchGroup, round, slot1, slot2, isReset);
    //     match.matchStatus = matchStatus;
    //     match.winner = winner;
    //     match.loser = loser;

    //     return match;
    // }
}