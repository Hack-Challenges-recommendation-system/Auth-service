package team.nofantasy.recommendationsystem.authservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.nofantasy.recommendationsystem.authservice.dto.controller.auth.CredentialsControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.LoginResultDto;
import team.nofantasy.recommendationsystem.authservice.mapper.AuthorisationMapper;
import team.nofantasy.recommendationsystem.authservice.service.AuthenticationService;
import team.nofantasy.recommendationsystem.authservice.service.JwtService;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {
    public static final String REFRESH_TOKEN_COOKIE = "jwtRefreshToken";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationService authenticationService;
    private final JwtService jwtService;
    private final AuthorisationMapper authorisationMapper;

    /**
     * Creates new cookie which stores JWT refresh token in itself.
     *
     * @param jwtToken JWT token.
     * @param duration lifetime of the cookie; {@link Duration#ZERO} deletes it.
     */
    public static ResponseCookie createJwtRefreshTokenCookie(String jwtToken, Duration duration) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, jwtToken)
                .path("/")
                .httpOnly(true)
                .secure(false) // true in production
                .maxAge(duration)
                .sameSite("Strict")
                .build();
    }

    /**
     * Checks the credentials and sets the refresh token cookie. The access token comes from {@code POST /auth/refresh}.
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @RequestBody @Valid CredentialsControllerDto credentialsControllerDto,
            HttpServletRequest request
    ) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "POST /auth/login")
                .log();

        LoginResultDto loginResultServiceDto = authenticationService.login(
                authorisationMapper.credentialsControllerToServiceDto(
                        credentialsControllerDto,
                        request.getHeader(HttpHeaders.USER_AGENT),
                        request.getRemoteAddr()
                )
        );

        if (!loginResultServiceDto.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        createJwtRefreshTokenCookie(
                                loginResultServiceDto.refreshToken(),
                                jwtService.getRefreshTokenTtl()
                        ).toString()
                )
                .build();
    }

    /**
     * @return a new access token for the refresh token in the cookie.
     */
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/refresh")
    // TODO: add refreshToken rotation
    public String refresh(@CookieValue(name = REFRESH_TOKEN_COOKIE) String refreshToken) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "POST /auth/refresh")
                .log();

        return jwtService.refreshAccessToken(refreshToken);
    }

    /**
     * Deletes the refresh token of this device, denylists the access token and clears the cookie.
     * Always answers 204, so a client can call it even when its tokens are already gone.
     */
    @DeleteMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) @Nullable String refreshToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) @Nullable String authorization
    ) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "DELETE /auth/logout")
                .log();

        if (refreshToken != null) {
            String accessToken = authorization != null && authorization.startsWith(BEARER_PREFIX)
                    ? authorization.substring(BEARER_PREFIX.length())
                    : null;
            authenticationService.logout(refreshToken, accessToken);
        }

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, createJwtRefreshTokenCookie("", Duration.ZERO).toString())
                .build();
    }
}
