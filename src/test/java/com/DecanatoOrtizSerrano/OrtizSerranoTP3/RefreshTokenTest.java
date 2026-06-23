package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller.AuthController;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception.GlobalExceptionHandler;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Usuario;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.UsuarioRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuthService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CHK023b/c — Tests de POST /api/auth/refresh (feature 002, Phase 6 US4).
 *
 * T030: refresh token válido → nuevo access token
 * T031: refresh token expirado/inválido → 401
 * T032: access token (tipo incorrecto) → 401
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — POST /api/auth/refresh")
class RefreshTokenTest {

    @Mock private AuthService authService;
    @Mock private RateLimiterService rateLimiterService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("T030a — refresh token válido → HTTP 200 con nuevo access token")
    void t030a_refreshValido200() throws Exception {
        JwtResponse resp = new JwtResponse("new.access.token", 1L, "ana@ipm.edu.ar", "Ana", "M", "ADMINISTRADOR");
        resp.setRefreshToken("original.refresh.token");
        when(authService.refresh("valid.refresh.token")).thenReturn(resp);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("refreshToken", "valid.refresh.token"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("new.access.token"))
            .andExpect(jsonPath("$.refreshToken").value("original.refresh.token"))
            .andExpect(jsonPath("$.role").value("ADMINISTRADOR"));
    }

    @Test
    @DisplayName("T031a — refresh token expirado → HTTP 401")
    void t031a_refreshExpirado401() throws Exception {
        when(authService.refresh(anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado"));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("refreshToken", "expired.token"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Refresh token inválido o expirado"));
    }

    @Test
    @DisplayName("T031b — access token usado como refresh → HTTP 401")
    void t031b_accessTokenComoRefresh401() throws Exception {
        when(authService.refresh(anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token proporcionado no es un refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("refreshToken", "access.token.here"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("El token proporcionado no es un refresh token"));
    }

    @Test
    @DisplayName("T031c — body sin refreshToken → HTTP 400")
    void t031c_sinRefreshToken400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of())))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("T031d — refreshToken vacío → HTTP 400")
    void t031d_refreshTokenVacio400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("refreshToken", ""))))
            .andExpect(status().isBadRequest());
    }
}
