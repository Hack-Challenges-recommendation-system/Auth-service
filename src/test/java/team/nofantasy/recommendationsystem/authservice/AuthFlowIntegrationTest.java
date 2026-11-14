package team.nofantasy.recommendationsystem.authservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import team.nofantasy.recommendationsystem.authservice.controller.AuthenticationController;
import team.nofantasy.recommendationsystem.authservice.repository.JwtRefreshTokenRepository;
import team.nofantasy.recommendationsystem.authservice.repository.UserCredentialsRepository;
import team.nofantasy.recommendationsystem.authservice.service.RedisService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The whole auth flow against real Postgres, Redis and Kafka, including Flyway migrations and Hibernate validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTest {
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private UserCredentialsRepository userCredentialsRepository;
    @Autowired
    private JwtRefreshTokenRepository jwtRefreshTokenRepository;
    @Autowired
    private KafkaContainer kafkaContainer;

    @Value("${jwt.secret}")
    private String secret;

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private UUID register(String nickname, String email) throws Exception {
        mockMvc.perform(api(post("/api/v1/user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "%s", "email": "%s", "password": "%s"}
                                """.formatted(nickname, email, PASSWORD)))
                .andExpect(status().isCreated());

        return userCredentialsRepository.findByEmail(email).orElseThrow().getUserId();
    }

    private Cookie login(String email, String password) throws Exception {
        Cookie cookie = mockMvc.perform(api(post("/api/v1/auth/login"))
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn()
                .getResponse()
                .getCookie(AuthenticationController.REFRESH_TOKEN_COOKIE);

        assertThat(cookie).isNotNull();
        return cookie;
    }

    private String refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(api(post("/api/v1/auth/refresh")).cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Reads the topic from the beginning and returns the value of the first record with this key.
     */
    private Optional<String> awaitRecord(String topic, UUID key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "auth-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;

            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.toString().equals(record.key())) {
                        return Optional.of(record.value());
                    }
                }
            }
        }

        return Optional.empty();
    }

    @Test
    void registerLoginRefreshAndLogout() throws Exception {
        String email = uniqueEmail();
        UUID userId = register("founder", email);

        assertThat(awaitRecord("users.to.create", userId))
                .hasValueSatisfying(json -> assertThat(json)
                        .contains("\"userId\":\"" + userId + "\"")
                        .contains("\"nickname\":\"founder\""));

        Cookie refreshCookie = login(email, PASSWORD);
        String accessToken = refresh(refreshCookie);
        Claims claims = parse(accessToken);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("type", String.class)).isEqualTo("ACCESS");

        mockMvc.perform(api(get("/api/v1/session")).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userAgent").value("JUnit"));

        mockMvc.perform(api(delete("/api/v1/auth/logout"))
                        .cookie(refreshCookie)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(redisTemplate.hasKey(RedisService.DENYLIST_PREFIX + claims.getId())).isTrue();
        mockMvc.perform(api(post("/api/v1/auth/refresh")).cookie(refreshCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void registeringTakenEmailConflicts() throws Exception {
        String email = uniqueEmail();
        register("founder", email);

        mockMvc.perform(api(post("/api/v1/user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "second", "email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        String email = uniqueEmail();
        register("founder", email);

        mockMvc.perform(api(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changedPasswordIsUsedForNextLogin() throws Exception {
        String email = uniqueEmail();
        UUID userId = register("founder", email);

        mockMvc.perform(api(put("/api/v1/user/password"))
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword": "wrong-password", "newPassword": "new-password-1"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(api(put("/api/v1/user/password"))
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword": "%s", "newPassword": "new-password-1"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());

        login(email, "new-password-1");
    }

    @Test
    void deletingUserRemovesSessionsAndAsksToDeleteProfile() throws Exception {
        String email = uniqueEmail();
        UUID userId = register("founder", email);
        login(email, PASSWORD);

        mockMvc.perform(api(delete("/api/v1/user")).header("X-User-Id", userId))
                .andExpect(status().isNoContent());

        assertThat(userCredentialsRepository.existsById(userId)).isFalse();
        assertThat(jwtRefreshTokenRepository.findAll())
                .noneMatch(token -> token.getUserCredentials().getUserId().equals(userId));
        assertThat(awaitRecord("users.to.delete", userId))
                .hasValueSatisfying(json -> assertThat(json).contains(userId.toString()));
    }
}
