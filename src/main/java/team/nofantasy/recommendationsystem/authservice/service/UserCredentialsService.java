package team.nofantasy.recommendationsystem.authservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import team.nofantasy.recommendationsystem.authservice.dto.service.kafka.UserToCreate;
import team.nofantasy.recommendationsystem.authservice.dto.service.kafka.UserToDelete;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.ChangePasswordRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.RegisterRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.entity.UserCredentials;
import team.nofantasy.recommendationsystem.authservice.exception.EntityAlreadyExistsException;
import team.nofantasy.recommendationsystem.authservice.exception.IncorrectPasswordException;
import team.nofantasy.recommendationsystem.authservice.repository.UserCredentialsRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserCredentialsService {
    private final KafkaProducerService kafkaProducerService;
    private final UserCredentialsRepository userCredentialsRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Registers new user in the system and asks profile-service to create the profile.
     *
     * @param dto credentials to register.
     * @throws EntityAlreadyExistsException if specified email already exists in DB.
     */
    public void register(RegisterRequestServiceDto dto) {
        String email = dto.email();

        if (userCredentialsRepository.findByEmail(email).isPresent()) {
            throw new EntityAlreadyExistsException("User with this email already exists; email=" + email);
        }

        UserCredentials userCredentialsRegistered = userCredentialsRepository.save(
                UserCredentials.builder()
                        .email(email)
                        .password(passwordEncoder.encode(dto.password()))
                        .build()
        );

        kafkaProducerService.sendUserToCreate(
                new UserToCreate(userCredentialsRegistered.getUserId(), dto.nickname())
        );
    }

    /**
     * @throws EntityNotFoundException if the user does not exist.
     * @throws IncorrectPasswordException if the old password does not match.
     */
    public void changePassword(ChangePasswordRequestServiceDto dto) {
        UserCredentials userCredentialsToChangePassword = userCredentialsRepository.findById(dto.userId()).orElseThrow(
                () -> new EntityNotFoundException("User not found; id=" + dto.userId())
        );

        if (!passwordEncoder.matches(dto.oldPassword(), userCredentialsToChangePassword.getPassword())) {
            throw new IncorrectPasswordException("Passwords do not match");
        }

        userCredentialsToChangePassword.setPassword(passwordEncoder.encode(dto.newPassword()));
        userCredentialsRepository.save(userCredentialsToChangePassword);
    }

    /**
     * @throws EntityNotFoundException if the user does not exist.
     * @throws EntityAlreadyExistsException if another user already has {@code newEmail}.
     */
    public void changeEmail(UUID userId, String newEmail) {
        UserCredentials userCredentialsToChangeEmail = userCredentialsRepository.findById(userId).orElseThrow(
                () -> new EntityNotFoundException("User not found; id=" + userId)
        );

        userCredentialsRepository.findByEmail(newEmail)
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw new EntityAlreadyExistsException("User with this email already exists; email=" + newEmail);
                });

        userCredentialsToChangeEmail.setEmail(newEmail);
        userCredentialsRepository.save(userCredentialsToChangeEmail);
    }

    /**
     * Deletes the user together with all their sessions and asks profile-service to delete the profile.
     * Deleting a user that does not exist does nothing.
     */
    public void deleteUser(UUID userId) {
        if (!userCredentialsRepository.existsById(userId)) {
            return;
        }

        userCredentialsRepository.deleteById(userId);
        kafkaProducerService.sendUserToDelete(new UserToDelete(userId));
    }
}
