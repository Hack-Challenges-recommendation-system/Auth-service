package team.nofantasy.recommendationsystem.authservice.dto.service.user;

public record RegisterRequestServiceDto(
        String nickname,
        String email,
        String password
) {}
