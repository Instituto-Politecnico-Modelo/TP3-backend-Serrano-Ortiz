package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtAuthenticationFilter;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * T012 — Tests unitarios de JwtAuthenticationFilter (feature 002, Phase 2 Foundational).
 *
 * Verifica (SIN tocar MySQL — puramente stateless):
 *   - Token válido → SecurityContext seteado con username y rol
 *   - Token inválido → SecurityContext vacío (sin excepción)
 *   - Sin header Authorization → SecurityContext vacío
 *   - El filtro NUNCA llama a ningún UserDetailsService o repositorio
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter — T012 Tests unitarios (sin DB)")
class JwtFilterTest {

    private static final String TEST_SECRET =
        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970" +
        "337336763979244226452948404D635166546A576E5A7234753778214125442A";

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret",              TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs",        3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "jwtRefreshExpirationMs", 604_800_000L);

        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
    }

    @Test
    @DisplayName("T012a — token válido → SecurityContext contiene username y autoridad")
    void t012a_validTokenSetsSecurityContext() throws Exception {
        String token = jwtUtil.generateJwtTokenWithRole(fakeAuth("docente@ipm.edu.ar"), "DOCENTE");
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(req, new MockHttpServletResponse(), filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("docente@ipm.edu.ar");
        assertThat(auth.getAuthorities())
            .extracting(a -> a.getAuthority())
            .containsExactly("DOCENTE");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("T012b — token con firma manipulada → SecurityContext vacío")
    void t012b_invalidTokenDoesNotSetContext() throws Exception {
        String token = jwtUtil.generateTokenFromUsername("hacker@ipm.edu.ar");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".firmaInvalidaXXXXXX";
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + tampered);

        filter.doFilter(req, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("T012c — sin header Authorization → SecurityContext vacío")
    void t012c_noHeaderDoesNotSetContext() throws Exception {
        var req = new MockHttpServletRequest();

        filter.doFilter(req, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("T012d — header Authorization sin 'Bearer ' → SecurityContext vacío")
    void t012d_malformedHeaderDoesNotSetContext() throws Exception {
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(req, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("T012e — sin DB: UserDetailsService NO es invocado durante validación")
    void t012e_noDatabaseCallDuringValidation() throws Exception {
        // El filtro no tiene UserDetailsService inyectado.
        // Si hubiera un DB call, el test fallaría con NullPointerException.
        String token = jwtUtil.generateJwtTokenWithRole(fakeAuth("admin@ipm.edu.ar"), "ADMINISTRADOR");
        var req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(req, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private org.springframework.security.core.Authentication fakeAuth(String email) {
        var ud = org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("irrelevant")
                .roles("ADMIN")
                .build();
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                ud, null, ud.getAuthorities());
    }
}
