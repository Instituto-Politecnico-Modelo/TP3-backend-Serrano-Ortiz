package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception.CuentaBloqueadaException;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Usuario;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.UsuarioRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.UserDetailsImpl;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T013 + T014 — Tests unitarios de AuthService.login() (feature 002, Phase 3 US1).
 *
 * T013: credenciales válidas → JwtResponse con token y refreshToken
 * T014: contraseña incorrecta → intentosFallidos++ → HTTP 401
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — T013/T014 Tests unitarios de login()")
class AuthServiceLoginTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxIntentos",          5);
        ReflectionTestUtils.setField(authService, "duracionBloqueoMinutos", 15);
    }

    // ─── T013: login exitoso ────────────────────────────────────────────────────

    @Test
    @DisplayName("T013a — credenciales válidas → JwtResponse con token no nulo")
    void t013a_loginExitosoRetornaToken() {
        Usuario u = usuarioBase();
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenReturn(fakeAuth("ana@ipm.edu.ar", "ADMINISTRADOR"));
        when(jwtUtil.generateJwtTokenWithRole(any(), eq("ADMINISTRADOR")))
            .thenReturn("access.token.fake");
        when(jwtUtil.generateRefreshToken("ana@ipm.edu.ar"))
            .thenReturn("refresh.token.fake");

        JwtResponse resp = authService.login("ana@ipm.edu.ar", "pass123");

        assertThat(resp.getToken()).isEqualTo("access.token.fake");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh.token.fake");
        assertThat(resp.getEmail()).isEqualTo("ana@ipm.edu.ar");
        assertThat(resp.getRole()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    @DisplayName("T013b — login exitoso → intentosFallidos reseteados a 0")
    void t013b_loginExitosoResetaContador() {
        Usuario u = usuarioBase();
        u.setIntentosFallidos(3); // tenía intentos previos
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenReturn(fakeAuth("ana@ipm.edu.ar", "ADMINISTRADOR"));
        when(jwtUtil.generateJwtTokenWithRole(any(), any())).thenReturn("token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        authService.login("ana@ipm.edu.ar", "pass123");

        // Verificar que se llamó save() con intentosFallidos = 0
        verify(usuarioRepository, atLeastOnce()).save(argThat(usr ->
            usr.getIntentosFallidos() == 0 && usr.getBloqueadoHasta() == null
        ));
    }

    @Test
    @DisplayName("T013c — login exitoso → JwtResponse incluye nombre y apellido")
    void t013c_loginExitosoIncluiDatosPersonales() {
        Usuario u = usuarioBase();
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenReturn(fakeAuth("ana@ipm.edu.ar", "ADMINISTRADOR"));
        when(jwtUtil.generateJwtTokenWithRole(any(), any())).thenReturn("t");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("r");

        JwtResponse resp = authService.login("ana@ipm.edu.ar", "pass");

        assertThat(resp.getNombre()).isEqualTo("Ana");
        assertThat(resp.getApellido()).isEqualTo("Martínez");
    }

    // ─── T014: credenciales inválidas ──────────────────────────────────────────

    @Test
    @DisplayName("T014a — contraseña incorrecta → intentosFallidos incrementado")
    void t014a_passwordIncorrectaIncrementaContador() {
        Usuario u = usuarioBase(); // intentosFallidos = 0
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login("ana@ipm.edu.ar", "wrongpass"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);

        verify(usuarioRepository).save(argThat(usr -> usr.getIntentosFallidos() == 1));
    }

    @Test
    @DisplayName("T014b — N intentos fallidos → cuenta bloqueada (bloqueadoHasta != null)")
    void t014b_nIntentosFallidosBloqueaCuenta() {
        Usuario u = usuarioBase();
        u.setIntentosFallidos(4); // 4 previos, el 5to bloquea
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login("ana@ipm.edu.ar", "wrongpass"))
            .isInstanceOf(ResponseStatusException.class);

        verify(usuarioRepository).save(argThat(usr ->
            usr.getIntentosFallidos() == 5 && usr.getBloqueadoHasta() != null
        ));
    }

    @Test
    @DisplayName("T014c — cuenta ya bloqueada → CuentaBloqueadaException sin intentar autenticar")
    void t014c_cuentaBloqueadaLanzaExcepcionEspecifica() {
        Usuario u = usuarioBase();
        u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login("ana@ipm.edu.ar", "pass"))
            .isInstanceOf(CuentaBloqueadaException.class);

        // No se debe intentar autenticar si ya está bloqueada
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("T014d — email inexistente → HTTP 401 (sin revelar si existe)")
    void t014d_emailInexistente401() {
        when(usuarioRepository.findByEmail("noexiste@ipm.edu.ar"))
            .thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("not found"));

        assertThatThrownBy(() -> authService.login("noexiste@ipm.edu.ar", "pass"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Usuario usuarioBase() {
        Usuario u = new Usuario("Ana", "Martínez", "ana@ipm.edu.ar", "$2a$10$hash");
        u.setIntentosFallidos(0);
        return u;
    }

    private UsernamePasswordAuthenticationToken fakeAuth(String email, String rol) {
        var ud = new UserDetailsImpl(1L, email, email, "hash",
            List.of(new SimpleGrantedAuthority(rol)));
        return new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
    }
}
