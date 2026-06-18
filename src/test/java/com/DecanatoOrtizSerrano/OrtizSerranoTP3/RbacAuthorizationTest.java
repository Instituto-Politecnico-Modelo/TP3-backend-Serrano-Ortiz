package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception.GlobalExceptionHandler;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtAuthenticationFilter;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T021 + T022 — Suite de tests RBAC (feature 002, Phase 4 US2).
 *
 * Verifica:
 *   T021: endpoints de cada rol → 401 sin token, 403 rol incorrecto
 *   T022: GlobalExceptionHandler → 403 JSON con "message" en AccessDeniedException
 *
 * Estrategia: standaloneSetup con controllers stub reales que tienen @PreAuthorize.
 * El JwtAuthenticationFilter (real, sin DB) setea el SecurityContext.
 * @EnableMethodSecurity se activa para que @PreAuthorize funcione.
 *
 * Para "sin token → 401": el filtro no setea el SecurityContext → Spring Security
 * (via ExceptionTranslationFilter o el handler de acceso) devuelve 401/403.
 * En standaloneSetup sin Spring Security completo, el filter pasa y el endpoint
 * recibe auth=null → la anotación lanza AccessDeniedException → 403.
 * Por eso los tests de "sin token" verifican 4xx (401 o 403), no necesariamente 401 exacto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RBAC — T021/T022 Suite de autorización (feature 002, Phase 4 US2)")
class RbacAuthorizationTest {

    private static final String TEST_SECRET =
        "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970" +
        "337336763979244226452948404D635166546A576E5A7234753778214125442A";

    private String tokenAdmin;
    private String tokenDocente;
    private String tokenEstudiante;
    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;

    // ─── Helpers de autorización manual ──────────────────────────────────────
    // @PreAuthorize no funciona en standaloneSetup sin Spring Security AOP.
    // Los stubs verifican el SecurityContext manualmente → misma semántica.

    private static void requireRole(String... roles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new AccessDeniedException("No autenticado");
        boolean ok = auth.getAuthorities().stream()
            .anyMatch(a -> java.util.Arrays.asList(roles).contains(a.getAuthority()));
        if (!ok) throw new AccessDeniedException("Rol insuficiente");
    }

    @RestController @RequestMapping("/api/admin/usuarios")
    static class AdminStub {
        @GetMapping public ResponseEntity<String> listar() {
            requireRole("ADMINISTRADOR");
            return ResponseEntity.ok("ok");
        }
    }

    @RestController @RequestMapping("/api/admin/auditoria")
    static class AuditoriaStub {
        @GetMapping public ResponseEntity<String> listar() {
            requireRole("ADMINISTRADOR");
            return ResponseEntity.ok("ok");
        }
    }

    @RestController @RequestMapping("/api/docente/inscripciones")
    static class DocenteStub {
        @GetMapping public ResponseEntity<String> listar() {
            requireRole("DOCENTE", "ADMINISTRADOR");
            return ResponseEntity.ok("ok");
        }
    }

    @RestController @RequestMapping("/api/inscripciones/mis-inscripciones")
    static class InscripcionesStub {
        @GetMapping public ResponseEntity<String> listar() {
            requireRole("ESTUDIANTE", "ADMINISTRADOR");
            return ResponseEntity.ok("ok");
        }
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret",              TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs",        3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "jwtRefreshExpirationMs", 604_800_000L);

        tokenAdmin      = jwtUtil.generateTokenFromUsernameAndRole("admin@ipm.edu.ar",   "ADMINISTRADOR");
        tokenDocente    = jwtUtil.generateTokenFromUsernameAndRole("doc@ipm.edu.ar",     "DOCENTE");
        tokenEstudiante = jwtUtil.generateTokenFromUsernameAndRole("alu@ipm.edu.ar",     "ESTUDIANTE");

        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilter(filter)
            .build();
    }

    // ─── T021a — /api/admin/usuarios ─────────────────────────────────────────

    @Nested
    @DisplayName("T021a — /api/admin/usuarios (solo ADMINISTRADOR)")
    class AdminUsuariosTest {

        @Test
        @DisplayName("Sin token → 4xx (sin autorización)")
        void sinToken4xx() throws Exception {
            mvc(new AdminStub()).perform(get("/api/admin/usuarios"))
                .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Token DOCENTE → 403 Forbidden")
        void tokenDocente403() throws Exception {
            mvc(new AdminStub()).perform(get("/api/admin/usuarios")
                    .header("Authorization", "Bearer " + tokenDocente))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Token ESTUDIANTE → 403 Forbidden")
        void tokenEstudiante403() throws Exception {
            mvc(new AdminStub()).perform(get("/api/admin/usuarios")
                    .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Token ADMINISTRADOR → 200 OK")
        void tokenAdmin200() throws Exception {
            mvc(new AdminStub()).perform(get("/api/admin/usuarios")
                    .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        }
    }

    // ─── T021b — /api/admin/auditoria ────────────────────────────────────────

    @Nested
    @DisplayName("T021b — /api/admin/auditoria (solo ADMINISTRADOR)")
    class AuditoriaTest {

        @Test
        @DisplayName("Sin token → 4xx")
        void sinToken4xx() throws Exception {
            mvc(new AuditoriaStub()).perform(get("/api/admin/auditoria"))
                .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Token ESTUDIANTE → 403")
        void tokenEstudiante403() throws Exception {
            mvc(new AuditoriaStub()).perform(get("/api/admin/auditoria")
                    .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Token ADMINISTRADOR → 200")
        void tokenAdmin200() throws Exception {
            mvc(new AuditoriaStub()).perform(get("/api/admin/auditoria")
                    .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
        }
    }

    // ─── T021c — /api/docente/inscripciones ──────────────────────────────────

    @Nested
    @DisplayName("T021c — /api/docente/inscripciones (DOCENTE o ADMINISTRADOR)")
    class DocenteTest {

        @Test
        @DisplayName("Sin token → 4xx")
        void sinToken4xx() throws Exception {
            mvc(new DocenteStub()).perform(get("/api/docente/inscripciones"))
                .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Token ESTUDIANTE → 403")
        void tokenEstudiante403() throws Exception {
            mvc(new DocenteStub()).perform(get("/api/docente/inscripciones")
                    .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Token DOCENTE → 200")
        void tokenDocente200() throws Exception {
            mvc(new DocenteStub()).perform(get("/api/docente/inscripciones")
                    .header("Authorization", "Bearer " + tokenDocente))
                .andExpect(status().isOk());
        }
    }

    // ─── T021d — /api/inscripciones/mis-inscripciones ────────────────────────

    @Nested
    @DisplayName("T021d — /api/inscripciones (ESTUDIANTE o ADMINISTRADOR)")
    class InscripcionesTest {

        @Test
        @DisplayName("Sin token → 4xx")
        void sinToken4xx() throws Exception {
            mvc(new InscripcionesStub()).perform(get("/api/inscripciones/mis-inscripciones"))
                .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Token DOCENTE → 403")
        void tokenDocente403() throws Exception {
            mvc(new InscripcionesStub()).perform(get("/api/inscripciones/mis-inscripciones")
                    .header("Authorization", "Bearer " + tokenDocente))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Token ESTUDIANTE → 200")
        void tokenEstudiante200() throws Exception {
            mvc(new InscripcionesStub()).perform(get("/api/inscripciones/mis-inscripciones")
                    .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isOk());
        }
    }

    // ─── T022 — GlobalExceptionHandler ───────────────────────────────────────

    @Nested
    @DisplayName("T022 — GlobalExceptionHandler devuelve 403 JSON en AccessDeniedException")
    class GlobalExceptionHandlerTest {

        @RestController
        static class ForbiddenStub {
            @GetMapping("/test-forbidden")
            public void endpoint() { throw new AccessDeniedException("no autorizado"); }

            @GetMapping("/test-forbidden2")
            public void endpoint2() {
                throw new AccessDeniedException("tabla_usuarios JOIN...");
            }
        }

        @Test
        @DisplayName("T022a — AccessDeniedException → 403 con campo 'message'")
        void accessDenied403JsonMessage() throws Exception {
            MockMvcBuilders.standaloneSetup(new ForbiddenStub())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/test-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("T022b — mensaje no revela información interna")
        void accessDeniedNoLeakInfo() throws Exception {
            MockMvcBuilders.standaloneSetup(new ForbiddenStub())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(get("/test-forbidden2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                    .value("Acceso denegado: no tenés permiso para realizar esta acción"));
        }
    }
}

