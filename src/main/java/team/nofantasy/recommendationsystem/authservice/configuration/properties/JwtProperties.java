package team.nofantasy.recommendationsystem.authservice.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings.
 *
 * @param secret HS512 signing secret, at least 64 UTF-8 bytes. The API gateway verifies tokens with the same secret.
 * @param accessTokenTtl lifetime of an access token; also how long a logged-out token stays in the denylist.
 * @param refreshTokenTtl lifetime of a refresh token and of its cookie.
 */
@ConfigurationProperties("jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
