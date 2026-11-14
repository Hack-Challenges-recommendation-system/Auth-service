package team.nofantasy.recommendationsystem.authservice.dto.service.session;

import java.util.UUID;

public record TerminateSessionRequestServiceDto(
        UUID sessionId,
        UUID userId
) {
}
