package com.robo_board.robot_board;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.robo_board.robot_board.competition.LeagueService;
import com.robo_board.robot_board.competition.TournamentService;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class LeaderboardController {

    private final LeagueService leagueService;
    private final TournamentService tournamentService;

    public LeaderboardController(LeagueService leagueService,
            TournamentService tournamentService) {
        this.leagueService = leagueService;
        this.tournamentService = tournamentService;
    }

    @PostMapping("/league/start")
    public String startLeague() {
        leagueService.runCompetition();
        return "League finished.";
    }

    @PostMapping("/brackets/start")
    public String startBrackets() {
        if (leagueService.getLastLeague() == null) {
            return "No league has been run yet. Start a league first via /api/league/start.";
        }
        tournamentService.runTournament(leagueService.getLastLeague());
        return "Tournament brackets finished.";
    }
}
