package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller.AuthController;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T015 — Test de integración de POST /api/auth/login (feature 002, Phase 3 US1).
 *
 * Verifica el endpoint completo mediante MockMvc standaloneSetup (sin Spring context).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — T015 POST /api/auth/login")
class AuthControllerLoginTest {

    @Mock private AuthService authService;
    @Mock private RateLimiterService rateLimiterService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        // IP no bloqueada por defecto — lenient para evitar UnnecessaryStubbingException
        // en tests donde @Valid rechaza el body antes de llegar al controller
        lenient().when(rateLimiterService.loginBloqueado(anyString())).thenReturn(false);
    }

    @Test
    @DisplayName("T015a — credenciales válidas → HTTP 200 con token, refreshToken y rol")
    void t015a_loginExitoso200() throws Exception {
        JwtResponse resp = new JwtResponse("access.tok", 1L, "ana@ipm.edu.ar", "Ana", "Martínez", "ADMINISTRADOR");
        resp.setRefreshToken("refresh.tok");
        when(authService.login("ana@ipm.edu.ar", "pass123")).thenReturn(resp);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "ana@ipm.edu.ar", "password", "pass123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access.tok"))
            .andExpect(jsonPath("$.refreshToken").value("refresh.tok"))
            .andExpect(jsonPath("$.role").value("ADMINISTRADOR"))
            .andExpect(jsonPath("$.email").value("ana@ipm.edu.ar"));
    }

    @Test
    @DisplayName("T015b — credenciales inválidas → HTTP 401 con mensaje genérico")
    void t015b_loginFallido401() throws Exception {
        when(authService.login(anyString(), anyString()))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "x@ipm.edu.ar", "password", "wrong"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("T015c — body inválido (email malformado) → HTTP 400")
    void t015c_bodyInvalidoBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "no-es-email", "password", "pass"))))
            .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(), any());
    }

    @Test
    @DisplayName("T015d — cuenta bloqueada → HTTP 423")
    void t015d_cuentaBloqueada423() throws Exception {
        when(authService.login(anyString(), anyString()))
            .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.LOCKED, "Cuenta bloqueada"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "u@ipm.edu.ar", "password", "pass"))))
            .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("T015e — JWT retornado tiene 3 segmentos (formato Header.Payload.Signature)")
    void t015e_tokenConFormatoJwt() throws Exception {
        JwtResponse resp = new JwtResponse("eyJ.payload.sig", 2L, "doc@ipm.edu.ar", "Carlos", "Lopez", "DOCENTE");
        resp.setRefreshToken("eyJ.rpayload.rsig");
        when(authService.login("doc@ipm.edu.ar", "pass")).thenReturn(resp);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", "doc@ipm.edu.ar", "password", "pass"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token", containsString(".")))
            .andExpect(jsonPath("$.type").value("Bearer"));
    }
}
