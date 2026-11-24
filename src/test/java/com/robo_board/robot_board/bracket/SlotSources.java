package com.robo_board.robot_board.bracket;

public sealed interface SlotSources{

    //Fixed types SlotSources can be, and it can only be one of the types below at a time.
    
    //Stores the location in the bracket a robot was seeded for the first round
    record Seed(int seedNumber) implements SlotSources {}

    //Stores the matchID of the last match, if the robot won that match.
    record WinnerOf(int matchID) implements SlotSources {}

    //Stores the matchID of the last match, if the robot lost that match.
    record LoserOf(int matchID) implements SlotSources {}
}