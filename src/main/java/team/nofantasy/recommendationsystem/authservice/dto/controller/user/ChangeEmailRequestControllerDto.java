package team.nofantasy.recommendationsystem.authservice.dto.controller.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequestControllerDto(
        @Email(message = "Invalid email format.")
        @NotBlank(message = "Email cannot be blank")
        String email
) {}
