package com.robo_board.robot_board.competition;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Path;

@Service
public class TournamentService extends CompetitionService {

    public TournamentService(ApplicationEventPublisher eventPublisher) {
        super(eventPublisher);
    }

    public void runTournament(League league) {
        if (!competitionRunning.compareAndSet(false, true)) {
            System.out.println("Tournament is already running.");
            return;
        }

        System.out.printf("Running tournament... %n");

        try {
            TournamentDriver tournament = new TournamentDriver(league,
                    Path.of("robocode").toAbsolutePath().normalize());

            tournament.setOnBattleCompleted(createEventPublisherCallback("update-tournament"));

            tournament.start();
        } finally {
            competitionRunning.set(false);
        }
    }
}
