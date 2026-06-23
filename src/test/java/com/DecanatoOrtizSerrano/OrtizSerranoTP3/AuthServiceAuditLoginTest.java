package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CHK028/029/030 — Auditoría de eventos de seguridad en login.
 *
 * CHK028: Cada intento fallido genera LOGIN_FALLIDO en auditoría con ipOrigen.
 * CHK029: Login exitoso NO genera registro de auditoría.
 * CHK030: El registro de LOGIN_FALLIDO incluye hashActual encadenado (delegado a AuditoriaService).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — CHK028/029 Auditoría de login")
class AuthServiceAuditLoginTest {

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
    @DisplayName("CHK028 — login fallido registra LOGIN_FALLIDO con email e IP")
    void chk028_loginFallidoRegistraEnAuditoria() {
        Usuario u = usuarioBase();
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar")).thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad"));

        try {
            authService.login("ana@ipm.edu.ar", "wrong", "192.168.1.100");
        } catch (Exception ignored) {}

        verify(auditoriaService).registrar(
            eq("USUARIO"), eq(99L), eq("LOGIN_FALLIDO"),
            contains("ana@ipm.edu.ar"),
            eq(99L), eq("ana@ipm.edu.ar"), eq("192.168.1.100")
        );
    }

    @Test
    @DisplayName("CHK028 — login fallido de usuario inexistente registra LOGIN_FALLIDO con idEntidad=null")
    void chk028_loginFallidoUsuarioInexistente() {
        when(usuarioRepository.findByEmail("ghost@ipm.edu.ar")).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("not found"));

        try {
            authService.login("ghost@ipm.edu.ar", "pass", "10.0.0.1");
        } catch (Exception ignored) {}

        verify(auditoriaService).registrar(
            eq("USUARIO"), isNull(), eq("LOGIN_FALLIDO"),
            contains("ghost@ipm.edu.ar"),
            isNull(), eq("ghost@ipm.edu.ar"), eq("10.0.0.1")
        );
    }

    @Test
    @DisplayName("CHK029 — login exitoso NO registra en auditoría")
    void chk029_loginExitosoNoRegistra() {
        Usuario u = usuarioBase();
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar"))
            .thenReturn(Optional.of(u))
            .thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenReturn(fakeAuth("ana@ipm.edu.ar", "ADMINISTRADOR"));
        when(jwtUtil.generateJwtTokenWithRole(any(), eq("ADMINISTRADOR"))).thenReturn("t");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("r");

        authService.login("ana@ipm.edu.ar", "pass", "192.168.1.1");

        // No debe llamar a registrar() en un login exitoso
        verify(auditoriaService, never()).registrar(
            anyString(), any(), anyString(), anyString(), any(), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("CHK028 — 5to intento registra LOGIN_FALLIDO Y CUENTA_BLOQUEADA (2 registros)")
    void chk028_quintoIntentoRegistraAmboEventos() {
        Usuario u = usuarioBase();
        u.setIntentosFallidos(4); // 5to intento
        when(usuarioRepository.findByEmail("ana@ipm.edu.ar")).thenReturn(Optional.of(u));
        when(authenticationManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("bad"));

        try {
            authService.login("ana@ipm.edu.ar", "wrong", "10.0.0.5");
        } catch (Exception ignored) {}

        // Primer call: LOGIN_FALLIDO
        verify(auditoriaService).registrar(
            eq("USUARIO"), eq(99L), eq("LOGIN_FALLIDO"),
            anyString(), eq(99L), eq("ana@ipm.edu.ar"), eq("10.0.0.5")
        );
        // Segundo call: CUENTA_BLOQUEADA
        verify(auditoriaService).registrar(
            eq("USUARIO"), eq(99L), eq("CUENTA_BLOQUEADA"),
            anyString(), eq(99L), eq("ana@ipm.edu.ar"), eq("10.0.0.5")
        );
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

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
