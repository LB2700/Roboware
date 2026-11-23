package com.robo_board.robot_board.bracket;

public sealed interface SlotSources{

    //Fixed types SlotSources can be
    record Seed(int seedNumber) implements SlotSources {}

    record WinnerOf(int matchID) implements SlotSources {}

    record LoserOf(int matchID) implements SlotSources {}
}