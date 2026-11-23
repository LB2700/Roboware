package com.robo_board.robot_board.competition;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import com.robo_board.robot_board.events.CompetitionEvent;

import javax.json.JsonObject;

/**
 * Base class for competition services. Holds shared state such as the event publisher and the
 * running flag, and provides a helper to build the update callback used by drivers.
 */
public abstract class CompetitionService {

    protected final AtomicBoolean competitionRunning = new AtomicBoolean(false);
    protected final ApplicationEventPublisher eventPublisher;

    public CompetitionService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected Consumer<JsonObject> createEventPublisherCallback(String eventName) {
        return (JsonObject competitionData) -> {
            eventPublisher.publishEvent(new CompetitionEvent(eventName, competitionData));
        };
    }
}
