package com.robo_board.robot_board;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.robo_board.robot_board.competition.CompetitionService;
import com.robo_board.robot_board.events.CompetitionEvent;
import com.robo_board.robot_board.services.SseService;

class PageControllerTests {
    @Test
    void publishesCurrentCompetitionStateShortlyAfterSubscription() {
        SseService sseService = mock(SseService.class);
        ApplicationEventPublisher eventService = mock(ApplicationEventPublisher.class);
        CompetitionService competitionService = mock(CompetitionService.class);
        SseEmitter emitter = new SseEmitter();
        JsonObject state = Json.createObjectBuilder().add("status", "current").build();
        when(sseService.subscribe()).thenReturn(emitter);
        when(competitionService.getCurrentState()).thenReturn(state);
        PageController controller = new PageController(sseService, eventService, competitionService);

        SseEmitter subscribedEmitter = controller.subscribe();

        assertSame(emitter, subscribedEmitter);
        verify(sseService).subscribe();
        verify(eventService, timeout(2_000)).publishEvent(
                new CompetitionEvent("update-competition", state));
    }
}
