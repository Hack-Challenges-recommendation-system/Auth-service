package team.nofantasy.recommendationsystem.authservice.mapper;

import org.mapstruct.Mapper;
import team.nofantasy.recommendationsystem.authservice.dto.controller.session.SessionControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.controller.session.TerminateSessionRequestControllerDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.session.SessionServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.session.TerminateSessionRequestServiceDto;

import java.util.List;

@Mapper
public interface SessionMapper {
    SessionControllerDto sessionServiceToControllerDto(SessionServiceDto sessionServiceDto);

    List<SessionControllerDto> sessionServiceListToControllerListDto(List<SessionServiceDto> sessionServiceDtoList);

    TerminateSessionRequestServiceDto terminateSessionRequestControllerToServiceDto(
            TerminateSessionRequestControllerDto terminateSessionRequestControllerDto
    );
}
