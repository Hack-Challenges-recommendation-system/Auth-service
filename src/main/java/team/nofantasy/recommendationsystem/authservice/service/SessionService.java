package team.nofantasy.recommendationsystem.authservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.nofantasy.recommendationsystem.authservice.dto.service.session.SessionServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.session.TerminateSessionRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.entity.JwtRefreshToken;
import team.nofantasy.recommendationsystem.authservice.entity.UserCredentials;
import team.nofantasy.recommendationsystem.authservice.exception.InsecureDirectObjectReferenceException;
import team.nofantasy.recommendationsystem.authservice.repository.JwtRefreshTokenRepository;
import team.nofantasy.recommendationsystem.authservice.repository.UserCredentialsRepository;

import java.util.List;
import java.util.UUID;

/**
 * A session is one logged-in device, stored as its refresh token.
 */
@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserCredentialsRepository userCredentialsRepository;
    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    private static SessionServiceDto toSessionServiceDto(JwtRefreshToken jwtRefreshToken) {
        return new SessionServiceDto(
                jwtRefreshToken.getJti(),
                jwtRefreshToken.getExpiration(),
                jwtRefreshToken.getLastUsed(),
                jwtRefreshToken.getUserAgent(),
                jwtRefreshToken.getIpAddress()
        );
    }

    /**
     * @throws EntityNotFoundException if the user does not exist.
     */
    public List<SessionServiceDto> getSessions(UUID userId) {
        UserCredentials userCredentials = userCredentialsRepository.findById(userId).orElseThrow(
                () -> new EntityNotFoundException("User is not found; id=" + userId)
        );

        return jwtRefreshTokenRepository.findAllByUserCredentials(userCredentials).stream()
                .map(SessionService::toSessionServiceDto)
                .toList();
    }

    /**
     * @throws EntityNotFoundException if the session does not exist.
     * @throws InsecureDirectObjectReferenceException if the session belongs to another user.
     */
    @Transactional
    public void terminateSession(TerminateSessionRequestServiceDto terminateSessionDto) {
        JwtRefreshToken activeSession = jwtRefreshTokenRepository.findById(terminateSessionDto.sessionId()).orElseThrow(
                () -> new EntityNotFoundException("Session not found; sessionId=" + terminateSessionDto.sessionId())
        );

        if (!activeSession.getUserCredentials().getUserId().equals(terminateSessionDto.userId())) {
            throw new InsecureDirectObjectReferenceException(
                    "IDOR occurred; jwtRefreshTokenId=" + terminateSessionDto.sessionId() +
                            ", actual userId=" + activeSession.getUserCredentials().getUserId() +
                            ", authenticated userId=" + terminateSessionDto.userId()
            );
        }

        jwtRefreshTokenRepository.delete(activeSession);
    }

    /**
     * @throws EntityNotFoundException if the user does not exist.
     */
    @Transactional
    public void terminateAllSessions(UUID userId) {
        UserCredentials userCredentials = userCredentialsRepository.findById(userId).orElseThrow(
                () -> new EntityNotFoundException("User is not found; id=" + userId)
        );

        jwtRefreshTokenRepository.deleteAll(jwtRefreshTokenRepository.findAllByUserCredentials(userCredentials));
    }
}
