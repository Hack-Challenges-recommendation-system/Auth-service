package team.nofantasy.recommendationsystem.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import team.nofantasy.recommendationsystem.authservice.config.kafka.KafkaTopicsProperties;
import team.nofantasy.recommendationsystem.authservice.dto.service.kafka.UserToCreate;
import team.nofantasy.recommendationsystem.authservice.dto.service.kafka.UserToDelete;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<UUID, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public void sendUserToCreate(UserToCreate userToCreate) {
        kafkaTemplate.send(topics.usersToCreate(), userToCreate.userId(), userToCreate);
    }

    public void sendUserToDelete(UserToDelete userToDelete) {
        kafkaTemplate.send(topics.usersToDelete(), userToDelete.userId(), userToDelete);
    }
}
