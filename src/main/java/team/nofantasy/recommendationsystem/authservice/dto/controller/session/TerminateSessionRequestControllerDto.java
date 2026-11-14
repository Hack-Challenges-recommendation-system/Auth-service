package team.nofantasy.recommendationsystem.authservice.dto.controller.session;

import java.util.UUID;

public record TerminateSessionRequestControllerDto(
        UUID sessionId,
        UUID userId
) {
}
