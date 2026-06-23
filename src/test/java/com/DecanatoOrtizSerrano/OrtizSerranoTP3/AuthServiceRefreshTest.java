package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Usuario;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.UsuarioRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para AuthService.refresh() — CHK023b/c.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService.refresh() — unit tests")
class AuthServiceRefreshTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxIntentos", 5);
        ReflectionTestUtils.setField(authService, "duracionBloqueoMinutos", 15);
    }

    @Test
    @DisplayName("T032a — refresh token válido → retorna nuevo access token")
    void t032a_refreshTokenValidoRetornaAccess() {
        when(jwtUtil.validateJwtToken("valid.refresh")).thenReturn(true);
        when(jwtUtil.getTokenType("valid.refresh")).thenReturn("refresh");
        when(jwtUtil.getUsernameFromJwtToken("valid.refresh")).thenReturn("ana@ipm.edu.ar");
        when(jwtUtil.generateTokenFromUsernameAndRole(eq("ana@ipm.edu.ar"), anyString()))
            .thenReturn("new.access.token");

        Usuario u = new Usuario("Ana", "M", "ana@ipm.edu.ar", "hash");
        u.setIdUsuario(1L);
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar")).thenReturn(Optional.of(u));

        JwtResponse resp = authService.refresh("valid.refresh");

        assertThat(resp.getToken()).isEqualTo("new.access.token");
        assertThat(resp.getRefreshToken()).isEqualTo("valid.refresh");
        assertThat(resp.getEmail()).isEqualTo("ana@ipm.edu.ar");
    }

    @Test
    @DisplayName("T032b — refresh token con firma inválida → 401")
    void t032b_firmaInvalida401() {
        when(jwtUtil.validateJwtToken("bad.token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad.token"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);
    }

    @Test
    @DisplayName("T032c — access token usado como refresh (type != refresh) → 401")
    void t032c_accessTokenNoEsRefresh401() {
        when(jwtUtil.validateJwtToken("access.token")).thenReturn(true);
        when(jwtUtil.getTokenType("access.token")).thenReturn(null); // access tokens don't have "type"

        assertThatThrownBy(() -> authService.refresh("access.token"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);
    }

    @Test
    @DisplayName("T032d — usuario inactivo → 401")
    void t032d_usuarioInactivo401() {
        when(jwtUtil.validateJwtToken("valid.refresh")).thenReturn(true);
        when(jwtUtil.getTokenType("valid.refresh")).thenReturn("refresh");
        when(jwtUtil.getUsernameFromJwtToken("valid.refresh")).thenReturn("inactive@ipm.edu.ar");

        Usuario u = new Usuario("Ina", "Ctiva", "inactive@ipm.edu.ar", "hash");
        u.setIdUsuario(2L);
        u.setActivo(false);
        when(usuarioRepository.findByEmail("inactive@ipm.edu.ar")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.refresh("valid.refresh"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);
    }

    @Test
    @DisplayName("T032e — usuario no encontrado → 401")
    void t032e_usuarioNoEncontrado401() {
        when(jwtUtil.validateJwtToken("valid.refresh")).thenReturn(true);
        when(jwtUtil.getTokenType("valid.refresh")).thenReturn("refresh");
        when(jwtUtil.getUsernameFromJwtToken("valid.refresh")).thenReturn("ghost@ipm.edu.ar");
        when(usuarioRepository.findByEmail("ghost@ipm.edu.ar")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid.refresh"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);
    }
}
