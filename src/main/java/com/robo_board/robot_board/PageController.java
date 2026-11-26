package com.robo_board.robot_board;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;

import com.robo_board.robot_board.competition.CompetitionService;
import com.robo_board.robot_board.events.CompetitionEvent;
import com.robo_board.robot_board.services.SseService;

import javax.json.JsonObject;
 

@RestController
public class PageController {
    private static final long INITIAL_UPDATE_DELAY_MILLIS = 250;

    private final SseService sseService;
    private final ApplicationEventPublisher eventService;
    private final CompetitionService competitionService;

    public PageController(SseService sseService, ApplicationEventPublisher eventService,
            CompetitionService competitionService) {
        this.sseService = sseService;
        this.eventService = eventService;
        this.competitionService = competitionService;
    }

    @GetMapping(
        value = "/events",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe() {
        SseEmitter emitter = sseService.subscribe();

        final JsonObject competitionState;
        try {
            competitionState = competitionService.getCurrentState();
        } catch (IllegalStateException e) {
            return emitter;
        }

        CompletableFuture.delayedExecutor(INITIAL_UPDATE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> eventService.publishEvent(new CompetitionEvent(
                        "update-competition", competitionState)));
        return emitter;
    }
}
