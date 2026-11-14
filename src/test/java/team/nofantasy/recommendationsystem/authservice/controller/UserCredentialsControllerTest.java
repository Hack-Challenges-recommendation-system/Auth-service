package team.nofantasy.recommendationsystem.authservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import team.nofantasy.recommendationsystem.authservice.dto.service.user.RegisterRequestServiceDto;
import team.nofantasy.recommendationsystem.authservice.exception.EntityAlreadyExistsException;
import team.nofantasy.recommendationsystem.authservice.mapper.UserCredentialsMapperImpl;
import team.nofantasy.recommendationsystem.authservice.service.UserCredentialsService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserCredentialsController.class)
@Import(UserCredentialsMapperImpl.class)
class UserCredentialsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCredentialsService userCredentialsService;

    private static MockHttpServletRequestBuilder api(MockHttpServletRequestBuilder builder) {
        return builder.servletPath("/api/v1");
    }

    @Test
    void registerCreatesUser() throws Exception {
        mockMvc.perform(api(post("/api/v1/user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "founder", "email": "founder@example.com", "password": "password"}
                                """))
                .andExpect(status().isCreated());

        verify(userCredentialsService).register(new RegisterRequestServiceDto("founder", "founder@example.com", "password"));
    }

    @Test
    void registerValidatesEveryField() throws Exception {
        mockMvc.perform(api(post("/api/v1/user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "ab", "email": "not-an-email", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nickname").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").exists());

        verifyNoInteractions(userCredentialsService);
    }

    @Test
    void registerWithTakenEmailConflicts() throws Exception {
        doThrow(new EntityAlreadyExistsException("taken")).when(userCredentialsService).register(any());

        mockMvc.perform(api(post("/api/v1/user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "founder", "email": "founder@example.com", "password": "password"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void endpointsForSignedInUsersRequireUserIdHeader() throws Exception {
        mockMvc.perform(api(delete("/api/v1/user")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userCredentialsService);
    }

    @Test
    void deleteUsesUserIdFromGateway() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(api(delete("/api/v1/user")).header("X-User-Id", userId))
                .andExpect(status().isNoContent());

        verify(userCredentialsService).deleteUser(userId);
    }

    @Test
    void changeEmailValidatesEmail() throws Exception {
        mockMvc.perform(api(put("/api/v1/user/email"))
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nope"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userCredentialsService);
    }
}
