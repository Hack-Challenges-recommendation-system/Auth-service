package team.nofantasy.recommendationsystem.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.nofantasy.recommendationsystem.authservice.configuration.properties.JwtProperties;

@RequiredArgsConstructor
@Repository
public class RedisService {
    /**
     * Key prefix of denylisted access tokens. The API gateway rejects a token whose {@code jti} is stored here.
     */
    public static final String DENYLIST_PREFIX = "jwt:denylist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    /**
     * Denylists an access token until it would expire anyway.
     *
     * @param jwtAccessTokenId the {@code jti} of the access token.
     */
    public void addJwtAccessTokenToDenylist(String jwtAccessTokenId) {
        stringRedisTemplate.opsForValue().set(
                DENYLIST_PREFIX + jwtAccessTokenId,
                "",
                jwtProperties.accessTokenTtl()
        );
    }
}
