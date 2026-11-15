package team.nofantasy.recommendationsystem.authservice.service;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.CredentialsServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.LoginResultDto;
import team.nofantasy.recommendationsystem.authservice.entity.UserCredentials;
import team.nofantasy.recommendationsystem.authservice.repository.UserCredentialsRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    static final String UNKNOWN_USER_AGENT = "unknown";

    private final JwtService jwtService;
    private final RedisService redisService;
    private final UserCredentialsRepository userCredentialsRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Login a user into system.
     *
     * @param credentialsServiceDto credentials of the user.
     * @return {@link LoginResultDto}; {@code success} is {@code false} for an unknown email or a wrong password.
     */
    public LoginResultDto login(@NonNull CredentialsServiceDto credentialsServiceDto) {
        Optional<UserCredentials> userOptional = userCredentialsRepository.findByEmail(credentialsServiceDto.email());

        if (userOptional.isEmpty() ||
                !passwordEncoder.matches(credentialsServiceDto.password(), userOptional.get().getPassword())) {
            return new LoginResultDto(false, null);
        }

        String userAgent = credentialsServiceDto.userAgent();
        String refreshToken = jwtService.createOrUpdateRefreshToken(
                userOptional.get(),
                userAgent == null || userAgent.isBlank() ? UNKNOWN_USER_AGENT : userAgent,
                credentialsServiceDto.userIpAddress()
        );

        return new LoginResultDto(true, refreshToken);
    }

    /**
     * Logs the current device out: deletes its refresh token and denylists the access token until it expires.
     *
     * @param refreshToken refresh token from the cookie.
     * @param accessToken access token from the {@code Authorization} header; {@code null} if the request had none.
     */
    public void logout(String refreshToken, @Nullable String accessToken) {
        jwtService.deleteRefreshToken(refreshToken);

        if (accessToken != null && jwtService.isValid(accessToken)) {
            redisService.addJwtAccessTokenToDenylist(jwtService.extractId(accessToken));
        }
    }
}
