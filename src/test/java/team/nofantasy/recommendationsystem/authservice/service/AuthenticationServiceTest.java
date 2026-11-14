package team.nofantasy.recommendationsystem.authservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.CredentialsServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.LoginResultDto;
import team.nofantasy.recommendationsystem.authservice.entity.UserCredentials;
import team.nofantasy.recommendationsystem.authservice.repository.UserCredentialsRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    private static final String EMAIL = "user@example.com";

    @Mock
    private JwtService jwtService;
    @Mock
    private RedisService redisService;
    @Mock
    private UserCredentialsRepository userCredentialsRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthenticationService authenticationService;

    private final UserCredentials user = UserCredentials.builder()
            .userId(UUID.randomUUID())
            .email(EMAIL)
            .password("hash")
            .build();

    @Test
    void loginFailsForUnknownEmail() {
        when(userCredentialsRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        LoginResultDto result = authenticationService.login(new CredentialsServiceDto(EMAIL, "password", "JUnit", "127.0.0.1"));

        assertThat(result.success()).isFalse();
        assertThat(result.refreshToken()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginFailsForWrongPassword() {
        when(userCredentialsRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        LoginResultDto result = authenticationService.login(new CredentialsServiceDto(EMAIL, "wrong", "JUnit", "127.0.0.1"));

        assertThat(result.success()).isFalse();
        verifyNoInteractions(jwtService);
    }

    @Test
    void loginCreatesRefreshTokenForTheDevice() {
        when(userCredentialsRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(jwtService.createOrUpdateRefreshToken(user, "JUnit", "127.0.0.1")).thenReturn("refresh-token");

        LoginResultDto result = authenticationService.login(new CredentialsServiceDto(EMAIL, "password", "JUnit", "127.0.0.1"));

        assertThat(result.success()).isTrue();
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginWithoutUserAgentStoresUnknownDevice() {
        when(userCredentialsRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(jwtService.createOrUpdateRefreshToken(any(), any(), any())).thenReturn("refresh-token");

        authenticationService.login(new CredentialsServiceDto(EMAIL, "password", null, "127.0.0.1"));

        verify(jwtService).createOrUpdateRefreshToken(user, AuthenticationService.UNKNOWN_USER_AGENT, "127.0.0.1");
    }

    @Test
    void logoutDeletesRefreshTokenAndDenylistsAccessToken() {
        when(jwtService.isValid("access-token")).thenReturn(true);
        when(jwtService.extractId("access-token")).thenReturn("access-jti");

        authenticationService.logout("refresh-token", "access-token");

        verify(jwtService).deleteRefreshToken("refresh-token");
        verify(redisService).addJwtAccessTokenToDenylist("access-jti");
    }

    @Test
    void logoutWithoutValidAccessTokenOnlyDeletesRefreshToken() {
        when(jwtService.isValid("expired")).thenReturn(false);

        authenticationService.logout("refresh-token", null);
        authenticationService.logout("refresh-token", "expired");

        verify(jwtService, times(2)).deleteRefreshToken("refresh-token");
        verifyNoInteractions(redisService);
    }
}
