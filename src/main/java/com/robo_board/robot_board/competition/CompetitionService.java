package com.robo_board.robot_board.competition;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.robo_board.robot_board.events.CompetitionEvent;

import javax.json.JsonObject;


@Service
public class CompetitionService {
    private final AtomicBoolean competitionRunning = new AtomicBoolean(false);

    private final ApplicationEventPublisher eventPublisher;
    
    public CompetitionService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void runCompetition() {

        System.out.printf("Running competition... %n");

        CompetitionDriver competition = new CompetitionDriver(
                Path.of("robocode").toAbsolutePath().normalize());

        competition.setOnBattleCompleted((JsonObject competitionData) -> {
            //System.out.printf("from cop service: %s %n", competitionData);
            eventPublisher.publishEvent(
                new CompetitionEvent("update-competition", competitionData)
            );
        });

        competition.start();
    }
}
