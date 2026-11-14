package team.nofantasy.recommendationsystem.authservice.mapper;

import org.mapstruct.Mapper;
import team.nofantasy.recommendationsystem.authservice.dto.controller.user.ChangePasswordRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.controller.user.RegisterRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.ChangePasswordRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.RegisterRequestServiceDto;

import java.util.UUID;

@Mapper
public interface UserCredentialsMapper {
    RegisterRequestServiceDto registerControllerToServiceDto(
            RegisterRequestControllerDto registerRequestControllerDto
    );

    ChangePasswordRequestServiceDto changePasswordRequestControllerToServiceDto(
            UUID userId,
            ChangePasswordRequestControllerDto changePasswordRequestControllerDto
    );
}
