package team.nofantasy.recommendationsystem.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import team.nofantasy.recommendationsystem.authservice.configuration.properties.JwtProperties;
import team.nofantasy.recommendationsystem.authservice.entity.JwtRefreshToken;
import team.nofantasy.recommendationsystem.authservice.entity.UserCredentials;
import team.nofantasy.recommendationsystem.authservice.repository.JwtRefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtServiceTest {
    private static final String SECRET = "integration-test-secret-".repeat(4);
    private static final Duration ACCESS_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private JwtRefreshTokenRepository repository;
    private JwtService service;
    private UserCredentials user;

    @BeforeEach
    void setUp() {
        repository = mock(JwtRefreshTokenRepository.class);
        service = new JwtService(repository, new JwtProperties(SECRET, ACCESS_TTL, REFRESH_TTL));
        user = UserCredentials.builder().userId(UUID.randomUUID()).email("user@example.com").password("hash").build();
    }

    private static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void signsAccessTokensWithUtf8SecretAndHs512() {
        String accessToken = ReflectionTestUtils.invokeMethod(service, "generateAccessToken", user);

        var jws = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(accessToken);

        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS512");
        assertThat(jws.getPayload().getSubject()).isEqualTo(user.getUserId().toString());
        assertThat(jws.getPayload().get("type", String.class)).isEqualTo("ACCESS");
        assertThat(jws.getPayload().get("role")).as("roles are gone").isNull();
        assertThat(jws.getPayload().getExpiration().toInstant())
                .isCloseTo(Instant.now().plus(ACCESS_TTL), within(Duration.ofSeconds(5)));
    }

    @Test
    void rejectsSecretShorterThanHs512Requires() {
        assertThatThrownBy(() -> new JwtService(repository, new JwtProperties("too-short", ACCESS_TTL, REFRESH_TTL)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtService(repository, new JwtProperties(null, ACCESS_TTL, REFRESH_TTL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesOnlyHashOfRefreshToken() {
        String refreshToken = service.createOrUpdateRefreshToken(user, "JUnit", "127.0.0.1");

        ArgumentCaptor<JwtRefreshToken> saved = ArgumentCaptor.forClass(JwtRefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getToken()).isEqualTo(service.hash(refreshToken)).isNotEqualTo(refreshToken);
        assertThat(saved.getValue().getJti().toString()).isEqualTo(parse(refreshToken).getId());
        assertThat(parse(refreshToken).get("type", String.class)).isEqualTo("REFRESH");
    }

    @Test
    void refreshIssuesAccessTokenForStoredRefreshToken() {
        String refreshToken = service.createOrUpdateRefreshToken(user, "JUnit", "127.0.0.1");
        ArgumentCaptor<JwtRefreshToken> saved = ArgumentCaptor.forClass(JwtRefreshToken.class);
        verify(repository).save(saved.capture());
        when(repository.findByToken(service.hash(refreshToken))).thenReturn(Optional.of(saved.getValue()));

        String accessToken = service.refreshAccessToken(refreshToken);

        assertThat(parse(accessToken).get("type", String.class)).isEqualTo("ACCESS");
        assertThat(parse(accessToken).getSubject()).isEqualTo(user.getUserId().toString());
        verify(repository, times(2)).save(any());
    }

    @Test
    void refreshRejectsAccessToken() {
        String accessToken = ReflectionTestUtils.invokeMethod(service, "generateAccessToken", user);

        assertThatThrownBy(() -> service.refreshAccessToken(accessToken)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshRejectsUnknownRefreshToken() {
        String refreshToken = service.createOrUpdateRefreshToken(user, "JUnit", "127.0.0.1");
        when(repository.findByToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshAccessToken(refreshToken)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void isValidRejectsMissingForeignAndExpiredTokens() {
        String foreign = Jwts.builder()
                .subject(user.getUserId().toString())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor("another-secret-".repeat(6).getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();
        String expired = Jwts.builder()
                .subject(user.getUserId().toString())
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();

        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("   ")).isFalse();
        assertThat(service.isValid("not-a-jwt")).isFalse();
        assertThat(service.isValid(foreign)).isFalse();
        assertThat(service.isValid(expired)).isFalse();
    }

    @Test
    void deleteRefreshTokenIgnoresInvalidToken() {
        service.deleteRefreshToken("not-a-jwt");

        verifyNoInteractions(repository);
    }
}
