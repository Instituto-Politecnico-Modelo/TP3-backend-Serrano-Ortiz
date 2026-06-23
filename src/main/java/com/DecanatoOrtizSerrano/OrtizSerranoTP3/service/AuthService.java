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

    // ─── Refresh Token ────────────────────────────────────────────────────────

    /**
     * CHK023b — Renueva el access token usando un refresh token válido.
     *
     * Valida:
     * 1. Firma y expiración del refresh token
     * 2. Que el claim "type" sea "refresh" (no un access token reutilizado)
     * 3. Que el usuario exista y esté activo
     *
     * @param refreshToken el refresh token JWT
     * @return JwtResponse con nuevo access token (y el mismo refresh token)
     */
    @Transactional(readOnly = true)
    public JwtResponse refresh(String refreshToken) {
        // 1. Validar firma + expiración
        if (!jwtUtil.validateJwtToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido o expirado");
        }

        // 2. Verificar que es de tipo "refresh"
        String tokenType = jwtUtil.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El token proporcionado no es un refresh token");
        }

        // 3. Extraer usuario
        String email = jwtUtil.getUsernameFromJwtToken(refreshToken);
        Usuario usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (!usuario.isActivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cuenta desactivada");
        }

        // 4. Generar nuevo access token
        String role = "ESTUDIANTE"; // default
        // Intentar deducir el rol del usuario según su tipo
        if (usuario.getClass().getSimpleName().equals("Docente")) {
            role = "DOCENTE";
        } else if (usuario.getClass().getSimpleName().equals("Estudiante")) {
            role = "ESTUDIANTE";
        } else {
            // Para admin u otros, buscar en la lógica existente
            role = "ADMINISTRADOR";
        }

        String newAccessToken = jwtUtil.generateTokenFromUsernameAndRole(email, role);

        // 5. Construir respuesta
        JwtResponse response = new JwtResponse(
            newAccessToken,
            usuario.getIdUsuario(),
            usuario.getEmail(),
            usuario.getNombre(),
            usuario.getApellido(),
            role
        );
        response.setRefreshToken(refreshToken); // mismo refresh token (no rotar)
        return response;
    }
}
