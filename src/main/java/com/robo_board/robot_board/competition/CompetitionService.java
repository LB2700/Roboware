package com.robo_board.robot_board.competition;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.robo_board.robot_board.events.CompetitionEvent;

import javax.json.JsonObject;

@Service
public class CompetitionService {
    private final AtomicBoolean competitionRunning = new AtomicBoolean(false);
    private final AtomicReference<JsonObject> currentState = new AtomicReference<>(
            javax.json.Json.createObjectBuilder().build());

    private final ApplicationEventPublisher eventPublisher;
    
    public CompetitionService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void runCompetition() {

        System.out.printf("Running competition... %n");

        CompetitionDriver competition = new CompetitionDriver(CompetitionDriver.SAMPLE_ENTRANTS,
                Path.of("robocode").toAbsolutePath().normalize());
        currentState.set(competition.getCurrentState());

        competition.setOnBattleCompleted((JsonObject competitionData) -> {
            currentState.set(competitionData);
            //System.out.printf("from cop service: %s %n", competitionData);
            eventPublisher.publishEvent(
                new CompetitionEvent("update-competition", competitionData)
            );
        });

        competition.start();
    }

    public JsonObject getCurrentState() {
        if (currentState.get().isEmpty()) {
            throw new IllegalStateException("No Competition Started");
        }
        return currentState.get();
    }
}
