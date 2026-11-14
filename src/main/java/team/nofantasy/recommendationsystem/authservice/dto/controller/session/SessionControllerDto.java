package team.nofantasy.recommendationsystem.authservice.dto.controller.session;

import java.time.Instant;
import java.util.UUID;

public record SessionControllerDto(
        UUID sessionId,
        Instant expiration,
        Instant lastUsed,
        String userAgent,
        String ipAddress
) {
}
