package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception.CuentaBloqueadaException;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Usuario;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.UsuarioRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.UserDetailsImpl;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — US3 Bloqueo de cuenta")
class AuthServiceBloqueoTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "maxIntentos", 5);
        ReflectionTestUtils.setField(authService, "duracionBloqueoMinutos", 15);
    }

    @Test
    @DisplayName("T028 — al 5to fallo se bloquea y se registra CUENTA_BLOQUEADA")
    void t028_quintoFalloBloqueaYAudita() {
        Usuario u = usuarioBase();
        u.setIntentosFallidos(4);

        when(usuarioRepository.findByEmail("ana@ipm.edu.ar")).thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.login("ana@ipm.edu.ar", "wrong"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(401);

        verify(usuarioRepository).save(org.mockito.ArgumentMatchers.argThat(usr ->
            usr.getIntentosFallidos() == 5 && usr.getBloqueadoHasta() != null
        ));

        verify(auditoriaService).registrar(
            eq("USUARIO"), eq(99L), eq("CUENTA_BLOQUEADA"),
            org.mockito.ArgumentMatchers.contains("5 intentos fallidos"),
            eq(99L), eq("ana@ipm.edu.ar"), eq(null)
        );
    }

    @Test
    @DisplayName("T027 — cuenta bloqueada retorna CuentaBloqueadaException y no autentica")
    void t027_cuentaBloqueadaNoAutentica() {
        Usuario u = usuarioBase();
        u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authService.login("ana@ipm.edu.ar", "pass"))
            .isInstanceOf(CuentaBloqueadaException.class)
            .extracting(e -> ((CuentaBloqueadaException) e).getMinutosRestantes())
            .isInstanceOf(Long.class);

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("T029 — bloqueo expirado permite login y resetea contador")
    void t029_bloqueoExpiradoPermiteLogin() {
        Usuario u = usuarioBase();
        u.setIntentosFallidos(3);
        u.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));

        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenReturn(fakeAuth("ana@ipm.edu.ar", "ADMINISTRADOR"));
        when(jwtUtil.generateJwtTokenWithRole(any(), eq("ADMINISTRADOR"))).thenReturn("access");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh");

        JwtResponse resp = authService.login("ana@ipm.edu.ar", "pass");

        assertThat(resp.getToken()).isEqualTo("access");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh");
        verify(usuarioRepository, atLeastOnce()).save(org.mockito.ArgumentMatchers.argThat(usr ->
            usr.getIntentosFallidos() == 0 && usr.getBloqueadoHasta() == null
        ));
    }

    private Usuario usuarioBase() {
        Usuario u = new Usuario("Ana", "Martínez", "ana@ipm.edu.ar", "hash");
        u.setIdUsuario(99L);
        u.setIntentosFallidos(0);
        return u;
    }

    private UsernamePasswordAuthenticationToken fakeAuth(String email, String rol) {
        var ud = new UserDetailsImpl(99L, email, email, "hash",
            List.of(new SimpleGrantedAuthority(rol)));
        return new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
    }
}
