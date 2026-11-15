package team.nofantasy.recommendationsystem.authservice.dto.service.auth;

public record LoginResultDto (
        boolean success,
        String refreshToken
) {}
