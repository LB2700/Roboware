package com.robo_board.robot_board.bracket;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for the bracket model (steps B5 to B9).
 *
 * No Robocode and no Spring are needed: robots are just names, and the tests
 * report winners themselves by calling endMatch.
 *
 * Every test uses the same hand-built 4-player double-elimination bracket:
 *
 *   id  side     round  slot 1        slot 2        reset?
 *   1   WINNERS  1      Seed 1 (A)    Seed 4 (D)    no
 *   2   WINNERS  1      Seed 2 (B)    Seed 3 (C)    no
 *   3   WINNERS  2      WinnerOf 1    WinnerOf 2    no    (winners final)
 *   4   LOSERS   1      LoserOf 1     LoserOf 2     no
 *   5   LOSERS   2      WinnerOf 4    LoserOf 3     no    (losers final)
 *   6   FINALS   1      WinnerOf 3    WinnerOf 5    no    (grand final)
 *   7   FINALS   2      WinnerOf 6    LoserOf 6     yes   (reset)
 */
class BracketTests {

    private static final String[] SEEDS = {"A", "B", "C", "D"};

    // ------------------------------------------------------------------
    // Helpers: these build things for the tests. They are not tests.
    // ------------------------------------------------------------------

    /** Makes one match. Shorter to read than calling the constructor directly. */
    private static BracketMatch match(int id, BracketSide side, int round,
            SlotSources source1, SlotSources source2, boolean isReset) {
        return new BracketMatch(id, side, round, new Slot(source1), new Slot(source2), isReset);
    }

    /** The 7 matches from the table at the top of this file. */
    private static List<BracketMatch> fourPlayerMatches() {
        List<BracketMatch> matches = new ArrayList<>();
        matches.add(match(1, BracketSide.WINNERS, 1, new SlotSources.Seed(1), new SlotSources.Seed(4), false));
        matches.add(match(2, BracketSide.WINNERS, 1, new SlotSources.Seed(2), new SlotSources.Seed(3), false));
        matches.add(match(3, BracketSide.WINNERS, 2, new SlotSources.WinnerOf(1), new SlotSources.WinnerOf(2), false));
        matches.add(match(4, BracketSide.LOSERS, 1, new SlotSources.LoserOf(1), new SlotSources.LoserOf(2), false));
        matches.add(match(5, BracketSide.LOSERS, 2, new SlotSources.WinnerOf(4), new SlotSources.LoserOf(3), false));
        matches.add(match(6, BracketSide.FINALS, 1, new SlotSources.WinnerOf(3), new SlotSources.WinnerOf(5), false));
        matches.add(match(7, BracketSide.FINALS, 2, new SlotSources.WinnerOf(6), new SlotSources.LoserOf(6), true));
        return matches;
    }

    private static Bracket fourPlayerBracket() {
        return new Bracket(SEEDS, fourPlayerMatches());
    }

    private static MatchStatus status(Bracket bracket, int matchId) {
        return bracket.findMatchByID(matchId).getMatchStatus();
    }

    private static String slot1(Bracket bracket, int matchId) {
        return bracket.findMatchByID(matchId).getSlot1().getRobotName();
    }

    private static String slot2(Bracket bracket, int matchId) {
        return bracket.findMatchByID(matchId).getSlot2().getRobotName();
    }

    /**
     * Plays matches 1 to 5, so only the grand final (6) is left.
     * Results: A beats D, B beats C, A beats B, D beats C, D beats B.
     * The grand final is then A (unbeaten) against D (one loss).
     */
    private static Bracket bracketAtGrandFinal() {
        Bracket bracket = fourPlayerBracket();
        bracket.endMatch(1, "A");
        bracket.endMatch(2, "B");
        bracket.endMatch(3, "A");
        bracket.endMatch(4, "D");
        bracket.endMatch(5, "D");
        return bracket;
    }

    // ------------------------------------------------------------------
    // B5: building the bracket
    // ------------------------------------------------------------------

    @Test
    void newBracketPutsSeedsIntoFirstRound() {
        Bracket bracket = fourPlayerBracket();

        assertEquals("A", slot1(bracket, 1));
        assertEquals("D", slot2(bracket, 1));
        assertEquals("B", slot1(bracket, 2));
        assertEquals("C", slot2(bracket, 2));
    }

    @Test
    void onlyFirstRoundMatchesStartReady() {
        Bracket bracket = fourPlayerBracket();

        assertEquals(MatchStatus.READY, status(bracket, 1));
        assertEquals(MatchStatus.READY, status(bracket, 2));
        for (int id = 3; id <= 7; id++) {
            assertEquals(MatchStatus.NOT_STARTED, status(bracket, id), "match " + id);
        }
    }

    @Test
    void laterMatchesStartWithEmptySlots() {
        Bracket bracket = fourPlayerBracket();

        assertNull(slot1(bracket, 3));
        assertNull(slot2(bracket, 3));
    }

    @Test
    void rejectsReferenceToMatchThatDoesNotExist() {
        List<BracketMatch> matches = fourPlayerMatches();
        matches.add(match(8, BracketSide.LOSERS, 3, new SlotSources.WinnerOf(99), new SlotSources.LoserOf(5), false));

        assertThrows(RuntimeException.class, () -> new Bracket(SEEDS, matches));
    }

    @Test
    void rejectsTwoMatchesWithTheSameId() {
        List<BracketMatch> matches = fourPlayerMatches();
        matches.add(match(1, BracketSide.WINNERS, 1, new SlotSources.Seed(1), new SlotSources.Seed(2), false));

        assertThrows(RuntimeException.class, () -> new Bracket(SEEDS, matches));
    }

    @Test
    void rejectsSeedNumberBiggerThanSeedList() {
        List<BracketMatch> matches = fourPlayerMatches();
        matches.set(0, match(1, BracketSide.WINNERS, 1, new SlotSources.Seed(1), new SlotSources.Seed(5), false));

        assertThrows(RuntimeException.class, () -> new Bracket(SEEDS, matches));
    }

    @Test
    void rejectsSeedNumberZero() {
        List<BracketMatch> matches = fourPlayerMatches();
        matches.set(0, match(1, BracketSide.WINNERS, 1, new SlotSources.Seed(0), new SlotSources.Seed(4), false));

        assertThrows(RuntimeException.class, () -> new Bracket(SEEDS, matches));
    }

    @Test
    void rejectsSameLoserSentToTwoPlaces() {
        List<BracketMatch> matches = fourPlayerMatches();
        // Match 4 already takes LoserOf 1. A second slot asking for it is a wiring mistake.
        matches.add(match(8, BracketSide.LOSERS, 3, new SlotSources.LoserOf(1), new SlotSources.WinnerOf(5), false));

        assertThrows(RuntimeException.class, () -> new Bracket(SEEDS, matches));
    }

    // ------------------------------------------------------------------
    // B6: starting a match
    // ------------------------------------------------------------------

    @Test
    void startingReadyMatchPutsItInProgress() {
        Bracket bracket = fourPlayerBracket();

        bracket.startMatch(1);

        assertEquals(MatchStatus.IN_PROGRESS, status(bracket, 1));
    }

    @Test
    void startingMatchThatIsNotReadyThrows() {
        Bracket bracket = fourPlayerBracket();

        assertThrows(RuntimeException.class, () -> bracket.startMatch(3));
        assertEquals(MatchStatus.NOT_STARTED, status(bracket, 3));
    }

    @Test
    void startingMatchThatDoesNotExistThrows() {
        Bracket bracket = fourPlayerBracket();

        assertThrows(RuntimeException.class, () -> bracket.startMatch(99));
    }

    // ------------------------------------------------------------------
    // B7: ending a match and moving robots forward
    // ------------------------------------------------------------------

    @Test
    void endingMatchRecordsWinnerAndLoser() {
        Bracket bracket = fourPlayerBracket();

        bracket.endMatch(1, "A");

        BracketMatch finished = bracket.findMatchByID(1);
        assertEquals(MatchStatus.COMPLETED, finished.getMatchStatus());
        assertEquals("A", finished.getWinner());
        assertEquals("D", finished.getLoser());
    }

    @Test
    void endingMatchThatWasStartedAlsoWorks() {
        Bracket bracket = fourPlayerBracket();

        bracket.startMatch(1);
        bracket.endMatch(1, "A");

        assertEquals(MatchStatus.COMPLETED, status(bracket, 1));
    }

    @Test
    void winnerAndLoserMoveToTheSlotsThatAskedForThem() {
        Bracket bracket = fourPlayerBracket();

        bracket.endMatch(1, "A");

        assertEquals("A", slot1(bracket, 3), "WinnerOf 1 is slot 1 of match 3");
        assertEquals("D", slot1(bracket, 4), "LoserOf 1 is slot 1 of match 4");
        assertNull(slot2(bracket, 3), "match 2 has not finished yet");
        assertEquals(MatchStatus.NOT_STARTED, status(bracket, 3));
    }

    @Test
    void robotGoesIntoTheMatchingSlotEvenIfTheOtherSlotIsEmpty() {
        // Match 5 is: slot 1 = WinnerOf 4, slot 2 = LoserOf 3.
        // Finish match 3 before match 4, so the loser of 3 arrives first.
        // It must go into slot 2, the slot that asked for it, not the first empty one.
        Bracket bracket = fourPlayerBracket();
        bracket.endMatch(1, "A");
        bracket.endMatch(2, "B");
        bracket.endMatch(3, "A");

        assertNull(slot1(bracket, 5), "match 4 has not been played yet");
        assertEquals("B", slot2(bracket, 5));
    }

    @Test
    void matchBecomesReadyOnceBothRobotsArrive() {
        Bracket bracket = fourPlayerBracket();

        bracket.endMatch(1, "A");
        bracket.endMatch(2, "B");

        assertEquals(MatchStatus.READY, status(bracket, 3));
        assertEquals(MatchStatus.READY, status(bracket, 4));
        assertEquals("C", slot2(bracket, 4));
    }

    @Test
    void finishedMatchesStayCompleted() {
        Bracket bracket = fourPlayerBracket();

        bracket.endMatch(1, "A");
        bracket.endMatch(2, "B");

        assertEquals(MatchStatus.COMPLETED, status(bracket, 1));
        assertEquals(MatchStatus.COMPLETED, status(bracket, 2));
    }

    @Test
    void endingWithRobotNotInTheMatchThrowsAndChangesNothing() {
        Bracket bracket = fourPlayerBracket();

        assertThrows(RuntimeException.class, () -> bracket.endMatch(1, "B"));

        BracketMatch untouched = bracket.findMatchByID(1);
        assertEquals(MatchStatus.READY, untouched.getMatchStatus());
        assertNull(untouched.getWinner());
        assertNull(slot1(bracket, 3));
    }

    @Test
    void endingMatchThatIsNotReadyThrows() {
        Bracket bracket = fourPlayerBracket();

        assertThrows(RuntimeException.class, () -> bracket.endMatch(3, "A"));
    }

    @Test
    void endingMatchTwiceThrows() {
        Bracket bracket = fourPlayerBracket();
        bracket.endMatch(1, "A");

        assertThrows(RuntimeException.class, () -> bracket.endMatch(1, "A"));
    }

    // ------------------------------------------------------------------
    // B8: grand final, reset and champion
    // ------------------------------------------------------------------

    @Test
    void grandFinalHasWinnersChampionInSlot1() {
        Bracket bracket = bracketAtGrandFinal();

        assertEquals(MatchStatus.READY, status(bracket, 6));
        assertEquals("A", slot1(bracket, 6), "winners-bracket champion");
        assertEquals("D", slot2(bracket, 6), "losers-bracket champion");
        assertFalse(bracket.isFinished());
        assertNull(bracket.getChampion());
    }

    @Test
    void ifFavouriteWinsGrandFinalTheResetIsCancelled() {
        Bracket bracket = bracketAtGrandFinal();

        bracket.endMatch(6, "A");

        assertEquals(MatchStatus.CANCELLED, status(bracket, 7));
        assertTrue(bracket.isFinished());
        assertEquals("A", bracket.getChampion());
    }

    @Test
    void ifUnderdogWinsGrandFinalTheResetIsPlayed() {
        Bracket bracket = bracketAtGrandFinal();

        bracket.endMatch(6, "D");

        assertEquals(MatchStatus.READY, status(bracket, 7));
        assertTrue(bracket.findMatchByID(7).hasRobot("A"));
        assertTrue(bracket.findMatchByID(7).hasRobot("D"));
        assertFalse(bracket.isFinished());
        assertNull(bracket.getChampion(), "no champion until the reset is played");
    }

    @Test
    void resetWinnerIsChampionEvenIfTheyLostTheGrandFinal() {
        Bracket bracket = bracketAtGrandFinal();
        bracket.endMatch(6, "D");

        bracket.endMatch(7, "A");

        assertEquals(MatchStatus.COMPLETED, status(bracket, 7));
        assertTrue(bracket.isFinished());
        assertEquals("A", bracket.getChampion());
    }

    @Test
    void resetWinnerIsChampionWhenUnderdogWinsBoth() {
        Bracket bracket = bracketAtGrandFinal();
        bracket.endMatch(6, "D");

        bracket.endMatch(7, "D");

        assertTrue(bracket.isFinished());
        assertEquals("D", bracket.getChampion());
    }

    // ------------------------------------------------------------------
    // B9: play a whole tournament and check the double-elimination rules
    // ------------------------------------------------------------------

    @Test
    void wholeTournamentWhenSlot1AlwaysWins() {
        checkWholeTournament(true);
    }

    @Test
    void wholeTournamentWhenSlot2AlwaysWins() {
        checkWholeTournament(false);
    }

    /**
     * Keeps ending the first READY match until the bracket is finished, then checks
     * the definition of double elimination: everyone except the champion lost exactly twice.
     */
    private static void checkWholeTournament(boolean slot1AlwaysWins) {
        Bracket bracket = fourPlayerBracket();

        int safetyLimit = 50; // stops the test looping forever if something never finishes
        while (!bracket.isFinished()) {
            assertTrue(--safetyLimit > 0, "tournament never finished");

            BracketMatch next = firstReadyMatch(bracket);
            assertNotNull(next, "nothing is READY, but the tournament is not finished");

            String winner = slot1AlwaysWins
                    ? next.getSlot1().getRobotName()
                    : next.getSlot2().getRobotName();
            bracket.endMatch(next.getMatchId(), winner);
        }

        String champion = bracket.getChampion();
        assertNotNull(champion);

        Map<String, Integer> losses = new HashMap<>();
        for (String robot : SEEDS) {
            losses.put(robot, 0);
        }
        for (BracketMatch match : bracket.getMatches()) {
            if (match.getMatchStatus() == MatchStatus.COMPLETED) {
                losses.merge(match.getLoser(), 1, Integer::sum);
            }
        }

        for (String robot : SEEDS) {
            if (robot.equals(champion)) {
                assertTrue(losses.get(robot) <= 1, "champion " + robot + " lost " + losses.get(robot) + " times");
            } else {
                assertEquals(2, losses.get(robot), robot + " should be knocked out after exactly 2 losses");
            }
        }
    }

    private static BracketMatch firstReadyMatch(Bracket bracket) {
        for (BracketMatch match : bracket.getMatches()) {
            if (match.getMatchStatus() == MatchStatus.READY) {
                return match;
            }
        }
        return null;
    }
}
