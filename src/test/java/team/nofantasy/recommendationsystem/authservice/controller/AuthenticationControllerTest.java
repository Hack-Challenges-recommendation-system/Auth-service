package team.nofantasy.recommendationsystem.authservice.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.CredentialsServiceDto;
import team.nofantasy.recommendationsystem.authservice.dto.service.auth.LoginResultDto;
import team.nofantasy.recommendationsystem.authservice.mapper.AuthorisationMapperImpl;
import team.nofantasy.recommendationsystem.authservice.service.AuthenticationService;
import team.nofantasy.recommendationsystem.authservice.service.JwtService;

import java.time.Duration;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthenticationController.class)
@Import(AuthorisationMapperImpl.class)
class AuthenticationControllerTest {
    private static final String CREDENTIALS = """
            {"email": "user@example.com", "password": "password"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private JwtService jwtService;

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    @Test
    void loginSetsHttpOnlyRefreshCookie() throws Exception {
        when(authenticationService.login(any())).thenReturn(new LoginResultDto(true, "refresh-token"));
        when(jwtService.getRefreshTokenTtl()).thenReturn(Duration.ofDays(30));

        mockMvc.perform(api(post("/api/v1/auth/login"))
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("jwtRefreshToken=refresh-token"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Max-Age=2592000")
                )));

        verify(authenticationService).login(new CredentialsServiceDto("user@example.com", "password", "JUnit", "127.0.0.1"));
    }

    @Test
    void loginWithBadCredentialsIsUnauthorized() throws Exception {
        when(authenticationService.login(any())).thenReturn(new LoginResultDto(false, null));

        mockMvc.perform(api(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginValidatesEmail() throws Exception {
        mockMvc.perform(api(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void refreshReturnsAccessToken() throws Exception {
        when(jwtService.refreshAccessToken("refresh-token")).thenReturn("access-token");

        mockMvc.perform(api(post("/api/v1/auth/refresh")).cookie(new Cookie("jwtRefreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(content().string("access-token"));
    }

    @Test
    void refreshWithoutCookieIsBadRequest() throws Exception {
        mockMvc.perform(api(post("/api/v1/auth/refresh")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutWithoutAuthorizationHeaderStillClearsCookie() throws Exception {
        mockMvc.perform(api(delete("/api/v1/auth/logout")).cookie(new Cookie("jwtRefreshToken", "refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authenticationService).logout("refresh-token", null);
    }

    @Test
    void logoutPassesBearerToken() throws Exception {
        mockMvc.perform(api(delete("/api/v1/auth/logout"))
                        .cookie(new Cookie("jwtRefreshToken", "refresh-token"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isNoContent());

        verify(authenticationService).logout("refresh-token", "access-token");
    }

    @Test
    void logoutWithoutCookieOnlyClearsCookie() throws Exception {
        mockMvc.perform(api(delete("/api/v1/auth/logout")))
                .andExpect(status().isNoContent());

        verifyNoInteractions(authenticationService);
    }
}
