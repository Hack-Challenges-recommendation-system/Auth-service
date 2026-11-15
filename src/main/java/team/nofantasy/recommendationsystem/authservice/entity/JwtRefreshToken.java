package team.nofantasy.recommendationsystem.authservice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code JwtRefreshToken} contains refresh token data of one logged-in device.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JwtRefreshToken {
    @Id
    private UUID jti;

    @NotBlank
    private String token;

    @NotNull
    private Instant expiration;

    @NotNull
    private Instant lastUsed;

    @NotBlank
    private String userAgent;

    @NotBlank
    private String ipAddress;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private UserCredentials userCredentials;
}
