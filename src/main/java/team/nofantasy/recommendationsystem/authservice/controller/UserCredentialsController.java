package team.nofantasy.recommendationsystem.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.nofantasy.recommendationsystem.authservice.dto.controller.user.ChangeEmailRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.controller.user.ChangePasswordRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.controller.user.RegisterRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.mapper.UserCredentialsMapper;
import team.nofantasy.recommendationsystem.authservice.service.UserCredentialsService;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserCredentialsController {
    private final UserCredentialsService userCredentialsService;
    private final UserCredentialsMapper userCredentialsMapper;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public void register(@RequestBody @Valid RegisterRequestControllerDto registerRequestControllerDto) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "POST /user")
                .log();

        userCredentialsService.register(userCredentialsMapper.registerControllerToServiceDto(registerRequestControllerDto));
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/password")
    public void changePassword(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody @Valid ChangePasswordRequestControllerDto changePasswordRequestControllerDto
    ) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "PUT /user/password")
                .addKeyValue("userId", userId)
                .log();

        userCredentialsService.changePassword(
                userCredentialsMapper.changePasswordRequestControllerToServiceDto(userId, changePasswordRequestControllerDto)
        );
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/email")
    public void changeEmail(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody @Valid ChangeEmailRequestControllerDto changeEmailRequestControllerDto
    ) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "PUT /user/email")
                .addKeyValue("userId", userId)
                .log();

        userCredentialsService.changeEmail(userId, changeEmailRequestControllerDto.email());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    public void deleteUser(@RequestHeader("X-User-Id") UUID userId) {
        log.atInfo()
                .setMessage("API endpoint is called")
                .addKeyValue("API endpoint", "DELETE /user")
                .addKeyValue("userId", userId)
                .log();

        userCredentialsService.deleteUser(userId);
    }
}
