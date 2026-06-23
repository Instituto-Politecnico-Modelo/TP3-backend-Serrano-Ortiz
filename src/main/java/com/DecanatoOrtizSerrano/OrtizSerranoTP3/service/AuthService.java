package com.DecanatoOrtizSerrano.OrtizSerranoTP3.service;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception.CuentaBloqueadaException;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.JwtResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Estudiante;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Usuario;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.UsuarioRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.UserDetailsImpl;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * T016 — Servicio de autenticación (feature 002, US1).
 *
 * Encapsula la lógica de login separándola del controller:
 * - Verificación de bloqueo de cuenta (per-user, independiente del rate limiter por IP)
 * - Delegación a Spring Security (AuthenticationManager + BCrypt)
 * - Incremento de intentosFallidos y bloqueo tras maxIntentos
 * - Reseteo de contadores en login exitoso
 * - Generación de access token + refresh token
 */
@Service
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /** T032 — Auditoría de CUENTA_BLOQUEADA (inyección opcional: null-safe si no está disponible). */
    @Autowired(required = false)
    private AuditoriaService auditoriaService;

    @Value("${auth.bloqueo.max-intentos:5}")
    private int maxIntentos;

    @Value("${auth.bloqueo.duracion-minutos:15}")
    private int duracionBloqueoMinutos;

    /**
     * T016 — Intenta autenticar al usuario. Lanza ResponseStatusException con:
     * - HTTP 423 si la cuenta está bloqueada
     * - HTTP 401 si las credenciales son inválidas (después de incrementar intentosFallidos)
     *
     * @param email    email del usuario
     * @param password contraseña en texto plano
     * @return JwtResponse con access token, refresh token y datos del usuario
     */
    @Transactional
    public JwtResponse login(String email, String password) {

        // ── 1. Verificar bloqueo per-user ─────────────────────────────────────
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario != null && usuario.isBloqueado()) {
            long minutosRestantes = ChronoUnit.MINUTES.between(LocalDateTime.now(), usuario.getBloqueadoHasta());
            // T027 — el tiempo restante se incluye en el mensaje para que el controller lo exponga en X-Retry-After
            throw new CuentaBloqueadaException(minutosRestantes);
        }

        // ── 2. Autenticar con Spring Security (BCrypt) ────────────────────────
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            // Incrementar intentosFallidos si el usuario existe
            if (usuario != null) {
                int intentos = usuario.getIntentosFallidos() + 1;
                usuario.setIntentosFallidos(intentos);
                if (intentos >= maxIntentos) {
                    usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(duracionBloqueoMinutos));
                    // T032 — registrar CUENTA_BLOQUEADA en auditoría
                    if (auditoriaService != null) {
                        try {
                            auditoriaService.registrar(
                                "USUARIO", usuario.getIdUsuario(), "CUENTA_BLOQUEADA",
                                "Cuenta bloqueada tras " + intentos + " intentos fallidos",
                                usuario.getIdUsuario(), email, null
                            );
                        } catch (Exception ignored) { /* no debe romper el flujo de auth */ }
                    }
                }
                usuarioRepository.save(usuario);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        // ── 3. Login exitoso → resetear contadores ────────────────────────────
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Recargar usuario (puede haber cambiado en el paso anterior) y resetear contadores
        Usuario usuarioActualizado = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Usuario no encontrado"));
        usuarioActualizado.setIntentosFallidos(0);
        usuarioActualizado.setBloqueadoHasta(null);
        usuarioRepository.save(usuarioActualizado);

        // ── 4. Determinar rol ─────────────────────────────────────────────────
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        // ── 5. Generar tokens ─────────────────────────────────────────────────
        String accessToken  = jwtUtil.generateJwtTokenWithRole(authentication, role);
        String refreshToken = jwtUtil.generateRefreshToken(email);

        // ── 6. Construir respuesta ────────────────────────────────────────────
        JwtResponse response = new JwtResponse(
            accessToken,
            userDetails.getId(),
            userDetails.getEmail(),
            usuarioActualizado.getNombre(),
            usuarioActualizado.getApellido(),
            role
        );
        response.setRefreshToken(refreshToken);
        if (usuarioActualizado instanceof Estudiante est) {
            response.setCarrera(est.getCarrera());
        }
        return response;
    }

    // ─── Getters para tests ───────────────────────────────────────────────────

    public int getMaxIntentos() {
        return maxIntentos;
    }

    public int getDuracionBloqueoMinutos() {
        return duracionBloqueoMinutos;
    }
}
