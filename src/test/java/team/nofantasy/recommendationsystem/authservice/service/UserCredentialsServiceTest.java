package team.nofantasy.recommendationsystem.authservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import team.nofantasy.recommendationsystem.authservice.dto.service.kafka.UserToCreate;
import team.nofantasy.recommendationsystem.authservice.dto.service.kafka.UserToDelete;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.ChangePasswordRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.RegisterRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.entity.UserCredentials;
import team.nofantasy.recommendationsystem.authservice.exception.EntityAlreadyExistsException;
import team.nofantasy.recommendationsystem.authservice.exception.IncorrectPasswordException;
import team.nofantasy.recommendationsystem.authservice.repository.UserCredentialsRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCredentialsServiceTest {
    @Mock
    private KafkaProducerService kafkaProducerService;
    @Mock
    private UserCredentialsRepository userCredentialsRepository;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserCredentialsService userCredentialsService;

    private static UserCredentials user(UUID id, String email) {
        return UserCredentials.builder().userId(id).email(email).password("hash").build();
    }

    @Test
    void registerRejectsTakenEmail() {
        when(userCredentialsRepository.findByEmail("taken@example.com"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), "taken@example.com")));

        assertThatThrownBy(() -> userCredentialsService.register(
                new RegisterRequestServiceDto("nick", "taken@example.com", "password")
        )).isInstanceOf(EntityAlreadyExistsException.class);

        verify(userCredentialsRepository, never()).save(any());
        verifyNoInteractions(kafkaProducerService);
    }

    @Test
    void registerStoresEncodedPasswordAndAsksForProfile() {
        UUID userId = UUID.randomUUID();
        when(userCredentialsRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userCredentialsRepository.save(any())).thenReturn(user(userId, "new@example.com"));

        userCredentialsService.register(new RegisterRequestServiceDto("nick", "new@example.com", "password"));

        ArgumentCaptor<UserCredentials> saved = ArgumentCaptor.forClass(UserCredentials.class);
        verify(userCredentialsRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded");
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        verify(kafkaProducerService).sendUserToCreate(new UserToCreate(userId, "nick"));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        UUID userId = UUID.randomUUID();
        when(userCredentialsRepository.findById(userId)).thenReturn(Optional.of(user(userId, "user@example.com")));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userCredentialsService.changePassword(
                new ChangePasswordRequestServiceDto(userId, "wrong", "new-password")
        )).isInstanceOf(IncorrectPasswordException.class);

        verify(userCredentialsRepository, never()).save(any());
    }

    @Test
    void changeEmailRejectsEmailOfAnotherUser() {
        UUID userId = UUID.randomUUID();
        when(userCredentialsRepository.findById(userId)).thenReturn(Optional.of(user(userId, "me@example.com")));
        when(userCredentialsRepository.findByEmail("other@example.com"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), "other@example.com")));

        assertThatThrownBy(() -> userCredentialsService.changeEmail(userId, "other@example.com"))
                .isInstanceOf(EntityAlreadyExistsException.class);

        verify(userCredentialsRepository, never()).save(any());
    }

    @Test
    void deleteUserAsksToDeleteProfile() {
        UUID userId = UUID.randomUUID();
        when(userCredentialsRepository.existsById(userId)).thenReturn(true);

        userCredentialsService.deleteUser(userId);

        verify(userCredentialsRepository).deleteById(userId);
        verify(kafkaProducerService).sendUserToDelete(new UserToDelete(userId));
    }

    @Test
    void deletingMissingUserDoesNothing() {
        UUID userId = UUID.randomUUID();
        when(userCredentialsRepository.existsById(userId)).thenReturn(false);

        userCredentialsService.deleteUser(userId);

        verify(userCredentialsRepository, never()).deleteById(any());
        verifyNoInteractions(kafkaProducerService);
    }
}
