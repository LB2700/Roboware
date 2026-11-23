package com.robo_board.robot_board.competition;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;

@Service
public class LeagueService extends CompetitionService {

    private League lastLeague;

    public LeagueService(ApplicationEventPublisher eventPublisher) {
        super(eventPublisher);
    }

    public void runCompetition() {
        if (!competitionRunning.compareAndSet(false, true)) {
            System.out.println("League is already running.");
            return;
        }

        System.out.printf("Running league... %n");

        try {
            LeagueDriver league = new LeagueDriver(League.SAMPLE_ENTRANTS,
                    Path.of("robocode").toAbsolutePath().normalize());

            league.setOnBattleCompleted(createEventPublisherCallback("update-competition"));

            league.start();
            lastLeague = league.getLeague();
        } finally {
            competitionRunning.set(false);
        }
    }

    public League getLastLeague() {
        return lastLeague;
    }
}
