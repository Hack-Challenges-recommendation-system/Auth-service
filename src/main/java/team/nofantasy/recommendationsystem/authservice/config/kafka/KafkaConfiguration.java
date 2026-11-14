package team.nofantasy.recommendationsystem.authservice.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaConfiguration {
    @Bean
    public NewTopic usersToCreateTopic(KafkaTopicsProperties topics) {
        return TopicBuilder
                .name(topics.usersToCreate())
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic usersToDeleteTopic(KafkaTopicsProperties topics) {
        return TopicBuilder
                .name(topics.usersToDelete())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
