package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * T011 — Tests unitarios de JwtUtil (feature 002, Phase 2 Foundational).
 *
 * Verifica:
 *   - Generación de access token con claims sub + rol
 *   - Generación de refresh token con claim type=refresh
 *   - Validación: token válido → true
 *   - Validación: token expirado → false (sin lanzar excepción)
 *   - Validación: firma manipulada → false
 *   - Extracción de username y rol
 *   - getRemainingTtl: token fresco > 0, token expirado = 0
 */
@DisplayName("JwtUtil — T011 Tests unitarios")
class JwtUtilTest {

    // Clave BASE64 de 64 bytes (512 bits) válida para HS512 — solo para tests
    private static final String TEST_SECRET =
        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970" +
        "337336763979244226452948404D635166546A576E5A7234753778214125442A";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret",            TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs",      3_600_000L); // 1h
        ReflectionTestUtils.setField(jwtUtil, "jwtRefreshExpirationMs", 604_800_000L); // 7d
    }

    // ─── Access token ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("T011a — generateJwtTokenWithRole → token válido con sub y claim rol")
    void t011a_generateTokenWithRole() {
        // Simular Authentication con UserDetails mínimo
        var auth = fakeAuth("ana@ipm.edu.ar");
        String token = jwtUtil.generateJwtTokenWithRole(auth, "ADMINISTRADOR");

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.getUsernameFromJwtToken(token)).isEqualTo("ana@ipm.edu.ar");
        assertThat(jwtUtil.getRolFromToken(token)).isEqualTo("ADMINISTRADOR");
    }

    @Test
    @DisplayName("T011b — validateJwtToken → token recién generado retorna true")
    void t011b_validateFreshToken() {
        String token = jwtUtil.generateTokenFromUsername("user@ipm.edu.ar");
        assertThat(jwtUtil.validateJwtToken(token)).isTrue();
    }

    @Test
    @DisplayName("T011c — validateJwtToken → token con firma manipulada retorna false")
    void t011c_validateTamperedToken() {
        String token = jwtUtil.generateTokenFromUsername("user@ipm.edu.ar");
        // Corromper la firma (último segmento del JWT)
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".firmaInvalidaXXXXXX";
        assertThat(jwtUtil.validateJwtToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("T011d — validateJwtToken → token expirado retorna false (no lanza excepción)")
    void t011d_validateExpiredToken() {
        // TTL = 0 ms → inmediatamente expirado
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", 0L);
        String token = jwtUtil.generateTokenFromUsername("user@ipm.edu.ar");
        assertThatCode(() -> jwtUtil.validateJwtToken(token)).doesNotThrowAnyException();
        assertThat(jwtUtil.validateJwtToken(token)).isFalse();
    }

    @Test
    @DisplayName("T011e — getUsernameFromJwtToken → extrae el subject correctamente")
    void t011e_extractUsername() {
        String token = jwtUtil.generateTokenFromUsername("docente@ipm.edu.ar");
        assertThat(jwtUtil.getUsernameFromJwtToken(token)).isEqualTo("docente@ipm.edu.ar");
    }

    // ─── Refresh token ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("T011f — generateRefreshToken → es válido y tiene subject correcto")
    void t011f_refreshTokenValid() {
        String refresh = jwtUtil.generateRefreshToken("user@ipm.edu.ar");
        assertThat(jwtUtil.validateJwtToken(refresh)).isTrue();
        assertThat(jwtUtil.getUsernameFromJwtToken(refresh)).isEqualTo("user@ipm.edu.ar");
    }

    @Test
    @DisplayName("T011g — generateRefreshToken → TTL mayor que el access token")
    void t011g_refreshTokenLongerTtl() {
        String access  = jwtUtil.generateTokenFromUsername("user@ipm.edu.ar");
        String refresh = jwtUtil.generateRefreshToken("user@ipm.edu.ar");
        assertThat(jwtUtil.getRemainingTtl(refresh))
            .isGreaterThan(jwtUtil.getRemainingTtl(access));
    }

    // ─── getRemainingTtl ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("T011h — getRemainingTtl → token fresco retorna valor > 0")
    void t011h_remainingTtlFreshToken() {
        String token = jwtUtil.generateTokenFromUsername("user@ipm.edu.ar");
        assertThat(jwtUtil.getRemainingTtl(token)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("T011i — getRemainingTtl → token expirado retorna 0")
    void t011i_remainingTtlExpiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", 0L);
        String token = jwtUtil.generateTokenFromUsername("user@ipm.edu.ar");
        assertThat(jwtUtil.getRemainingTtl(token)).isEqualTo(0L);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    /** Crea un Authentication mínimo a partir de un email. */
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
