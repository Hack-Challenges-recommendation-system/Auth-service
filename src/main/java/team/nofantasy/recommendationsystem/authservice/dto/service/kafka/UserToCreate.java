package team.nofantasy.recommendationsystem.authservice.dto.service.kafka;

import java.util.UUID;

/**
 * Published to {@code users.to.create} after registration; profile-service creates the profile from it.
 */
public record UserToCreate(
        UUID userId,
        String nickname
) {
}
