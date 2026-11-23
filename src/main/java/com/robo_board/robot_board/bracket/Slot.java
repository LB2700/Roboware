package com.robo_board.robot_board.bracket;

public class Slot{
    private final SlotSources slotSource;
    private String robotName;  

    public Slot(SlotSources source) {
        this.slotSource = source;
        this.robotName = null;
    }

    public SlotSources getSlotSource() {
        return slotSource;
    }

    public String getRobotName() {
        return robotName;
    }

    public void setRobotName(String robotName) {
        this.robotName = robotName;
    }

    public boolean hasRobot() {
        return robotName != null;
    }

    // public void toJSONObject() {

    // }

    // public void fromJSONObject() {

    // }
}