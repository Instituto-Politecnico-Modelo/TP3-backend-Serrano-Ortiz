package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller.AuthController;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception.GlobalExceptionHandler;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtAuthenticationFilter;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuthService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.RateLimiterService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.TokenBlocklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHK023d/e/f — Tests de logout con revocación via blocklist + filtro JWT rechaza token revocado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Logout & TokenBlocklist — CHK023d/e/f")
class LogoutBlocklistTest {

    // ─── Controller test ──────────────────────────────────────────────────────

    @Mock private AuthService authService;
    @Mock private RateLimiterService rateLimiterService;
    @Mock private TokenBlocklistService tokenBlocklistService;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("CHK023d — POST /api/auth/logout con Bearer token → 204 + token revocado en blocklist")
    void logoutRevocaToken204() throws Exception {
        when(jwtUtil.getRemainingTtl("my.jwt.token")).thenReturn(1800000L); // 30 min

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer my.jwt.token"))
            .andExpect(status().isNoContent());

        verify(tokenBlocklistService).revocar(eq("my.jwt.token"), eq(1800000L));
    }

    @Test
    @DisplayName("CHK023d — POST /api/auth/logout sin Authorization → 204 sin revocar")
    void logoutSinHeader204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent());

        verify(tokenBlocklistService, never()).revocar(any(), anyLong());
    }

    // ─── Filter test (token revocado) ─────────────────────────────────────────

    @Test
    @DisplayName("CHK023f — Token revocado no establece autenticación en SecurityContext")
    void tokenRevocadoNoAutentica() throws Exception {
        // Setup filter with mocks
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "tokenBlocklistService", tokenBlocklistService);

        when(jwtUtil.validateJwtToken("revoked.token")).thenReturn(true);
        when(tokenBlocklistService.estaRevocado("revoked.token")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        filter.doFilter(request, response, chain);

        // El SecurityContext NO debe tener autenticación
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("CHK023f — Token válido y no revocado establece autenticación")
    void tokenValidoNoRevocadoAutentica() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        org.springframework.test.util.ReflectionTestUtils.setField(filter, "tokenBlocklistService", tokenBlocklistService);

        when(jwtUtil.validateJwtToken("valid.token")).thenReturn(true);
        when(tokenBlocklistService.estaRevocado("valid.token")).thenReturn(false);
        when(jwtUtil.getUsernameFromJwtToken("valid.token")).thenReturn("ana@ipm.edu.ar");
        when(jwtUtil.getRolFromToken("valid.token")).thenReturn("ADMINISTRADOR");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.clearContext();
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ana@ipm.edu.ar");
        verify(chain).doFilter(request, response);
    }
}
