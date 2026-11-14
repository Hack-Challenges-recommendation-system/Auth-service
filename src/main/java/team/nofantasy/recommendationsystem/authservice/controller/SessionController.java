package team.nofantasy.recommendationsystem.authservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.nofantasy.recommendationsystem.authservice.dto.controller.session.SessionControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.controller.session.TerminateSessionRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.mapper.SessionMapper;
import team.nofantasy.recommendationsystem.authservice.service.SessionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
@Slf4j
public class SessionController {
    private final SessionService sessionService;
    private final SessionMapper sessionMapper;

    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public List<SessionControllerDto> getAllSessions(@RequestHeader("X-User-Id") UUID userId) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "GET /session")
                .addKeyValue("userId", userId)
                .log();

        return sessionMapper.sessionServiceListToControllerListDto(sessionService.getSessions(userId));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    public void terminateSession(@RequestBody UUID sessionId, @RequestHeader("X-User-Id") UUID userId) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "DELETE /session")
                .addKeyValue("userId", userId)
                .addKeyValue("sessionId", sessionId)
                .log();

        TerminateSessionRequestControllerDto controllerDto = new TerminateSessionRequestControllerDto(sessionId, userId);
        sessionService.terminateSession(sessionMapper.terminateSessionRequestControllerToServiceDto(controllerDto));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/all")
    public void terminateAllSessions(@RequestHeader("X-User-Id") UUID userId) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "DELETE /session/all")
                .addKeyValue("userId", userId)
                .log();

        sessionService.terminateAllSessions(userId);
    }
}
