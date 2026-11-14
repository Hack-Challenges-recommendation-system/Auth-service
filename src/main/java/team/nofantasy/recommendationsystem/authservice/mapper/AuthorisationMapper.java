package team.nofantasy.recommendationsystem.authservice.mapper;

import org.mapstruct.Mapper;
import team.nofantasy.recommendationsystem.authservice.dto.controller.auth.CredentialsControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.CredentialsServiceDto;

@Mapper
public interface AuthorisationMapper {
    CredentialsServiceDto credentialsControllerToServiceDto(
            CredentialsControllerDto credentialsControllerDto,
            String userAgent,
            String userIpAddress
    );

}
