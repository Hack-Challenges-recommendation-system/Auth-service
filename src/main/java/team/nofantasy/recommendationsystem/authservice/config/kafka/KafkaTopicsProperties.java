package team.nofantasy.recommendationsystem.authservice.config.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.kafka.topics")
public record KafkaTopicsProperties(
        String usersToCreate,
        String usersToDelete
) {
}
