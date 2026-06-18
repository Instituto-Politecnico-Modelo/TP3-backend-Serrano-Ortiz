package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller.AuditoriaController;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.HashChainService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T022-T026 — Tests de slice del AuditoriaController usando standaloneSetup (sin contexto Spring).
 *
 * Cubren:
 *   T022 — Respuesta paginada tiene los campos correctos de Page<>
 *   T023 — Parámetros por defecto (page=0, size=50)
 *   T024 — size > 100 → HTTP 400 con campo "error" y "maxSize"
 *   T025 — Seguridad: @Disabled — cubierto por SecurityTest.A1/A2
 *   T026 — Performance 10k registros: @Disabled (requiere DB real)
 *   T028-T031 — Sub-endpoints devuelven Page<> correctamente
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("T022-T031 — AuditoriaController: paginación, validación y sub-endpoints")
class AuditoriaControllerTest {

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AuditoriaController auditoriaController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // standaloneSetup: prueba el controller aislado, sin contexto Spring ni filtros de seguridad.
        // Los tests de seguridad (T025) están cubiertos por SecurityTest.A1 y SecurityTest.A2.
        mockMvc = MockMvcBuilders.standaloneSetup(auditoriaController).build();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private Page<RegistroAuditoria> paginaVacia(int page, int size) {
        return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
    }

    private Page<RegistroAuditoria> paginaCon(int page, int size, int total) {
        RegistroAuditoria r = new RegistroAuditoria();
        r.setEntidad("Inscripcion");
        r.setAccion("INSCRIPCION_CONFIRMADA");
        r.setDescripcion("Registro de prueba");
        return new PageImpl<>(List.of(r), PageRequest.of(page, size), total);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T022 — GET /api/admin/auditoria → Page<> con totalElements, totalPages, content, number
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(22)
    @DisplayName("T022 — ?page=0&size=50 → 200 con estructura Page<>")
    void t022_listarTodos_retornaEstructuraPaginada() throws Exception {
        when(auditoriaService.listarTodosPaginado(any(Pageable.class)))
                .thenReturn(paginaCon(0, 50, 1));

        mockMvc.perform(get("/api/admin/auditoria")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test @Order(22)
    @DisplayName("T022b — Page<> refleja totalElements y calcula totalPages correctamente")
    void t022b_paginaMultiple_totalPagesCalculado() throws Exception {
        // 250 registros totales, tamaño 50 → 5 páginas
        when(auditoriaService.listarTodosPaginado(any(Pageable.class)))
                .thenReturn(paginaCon(0, 50, 250));

        mockMvc.perform(get("/api/admin/auditoria")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(250))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test @Order(22)
    @DisplayName("T022c — content contiene campos de RegistroAuditoria")
    void t022c_content_contieneRegistroAuditoria() throws Exception {
        when(auditoriaService.listarTodosPaginado(any(Pageable.class)))
                .thenReturn(paginaCon(0, 50, 1));

        mockMvc.perform(get("/api/admin/auditoria")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entidad").value("Inscripcion"))
                .andExpect(jsonPath("$.content[0].accion").value("INSCRIPCION_CONFIRMADA"));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T023 — Sin parámetros → defaults page=0, size=50
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(23)
    @DisplayName("T023 — Sin ?page ni ?size → 200 OK con defaults page=0, size=50")
    void t023_sinParams_usaDefaults() throws Exception {
        when(auditoriaService.listarTodosPaginado(any(Pageable.class)))
                .thenReturn(paginaVacia(0, 50));

        mockMvc.perform(get("/api/admin/auditoria")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T024 — size > 100 → HTTP 400 con "error" y "maxSize"
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(24)
    @DisplayName("T024 — ?size=101 → 400 Bad Request con body error y maxSize=100")
    void t024_sizeExcede100_retorna400() throws Exception {
        mockMvc.perform(get("/api/admin/auditoria")
                        .param("page", "0")
                        .param("size", "101")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("100")))
                .andExpect(jsonPath("$.maxSize").value(100));
    }

    @Test @Order(24)
    @DisplayName("T024b — ?size=100 (límite exacto) → 200 OK (no rechazado)")
    void t024b_sizeLimiteExacto_retorna200() throws Exception {
        when(auditoriaService.listarTodosPaginado(any(Pageable.class)))
                .thenReturn(paginaVacia(0, 100));

        mockMvc.perform(get("/api/admin/auditoria")
                        .param("size", "100")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test @Order(24)
    @DisplayName("T024c — ?size=500 en /entidad/{e} → 400")
    void t024c_sizeExcedeEnSubEndpoint_retorna400() throws Exception {
        mockMvc.perform(get("/api/admin/auditoria/entidad/Inscripcion")
                        .param("size", "500")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(24)
    @DisplayName("T024d — ?size=0 → 400 (tamaño inválido)")
    void t024d_sizeCero_retorna400() throws Exception {
        // PageRequest.of() lanza excepción si size < 1 → Spring devuelve 400
        mockMvc.perform(get("/api/admin/auditoria")
                        .param("size", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T025 — Seguridad (delegado a SecurityTest — standaloneSetup no tiene filtros)
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(25)
    @Disabled("T025 — Cubierto por SecurityTest.A1 (sin token→401) y SecurityTest.A2 (DOCENTE/ESTUDIANTE→403). "
            + "standaloneSetup no aplica filtros de Spring Security.")
    @DisplayName("T025 — Sin JWT → 401, DOCENTE/ESTUDIANTE → 403 (ver SecurityTest.A1, A2)")
    void t025_seguridad_cubiertaPorSecurityTest() {
        // SecurityTest.A1: sin token → 401 en /api/admin/auditoria
        // SecurityTest.A2: estudiante → 403 en /api/admin/auditoria
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T028-T031 — Sub-endpoints paginados
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(28)
    @DisplayName("T028 — GET /entidad/{entidad} → 200 con Page<>")
    void t028_porEntidad_retornaPaginado() throws Exception {
        when(auditoriaService.porEntidadPaginado(any(), any(Pageable.class)))
                .thenReturn(paginaVacia(0, 50));

        mockMvc.perform(get("/api/admin/auditoria/entidad/Inscripcion")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test @Order(29)
    @DisplayName("T029 — GET /entidad/{entidad}/{id} → 200 con Page<>")
    void t029_porEntidadYId_retornaPaginado() throws Exception {
        when(auditoriaService.porEntidadYIdPaginado(any(), any(), any(Pageable.class)))
                .thenReturn(paginaVacia(0, 50));

        mockMvc.perform(get("/api/admin/auditoria/entidad/Inscripcion/42")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test @Order(30)
    @DisplayName("T030 — GET /usuario/{idUsuario} → 200 con Page<>")
    void t030_porUsuario_retornaPaginado() throws Exception {
        // Nota: PageImpl recalcula total cuando pageSize > total.
        // Para que totalElements=3 sea consistente usamos size=1 < total=3
        RegistroAuditoria r = new RegistroAuditoria();
        r.setEntidad("Inscripcion");
        r.setAccion("INSCRIPCION_CONFIRMADA");
        Page<RegistroAuditoria> page = new PageImpl<>(
                List.of(r), PageRequest.of(0, 1), 3L);  // size=1 < total=3 → totalElements=3

        when(auditoriaService.porUsuarioPaginado(any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/auditoria/usuario/7")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test @Order(31)
    @DisplayName("T031 — GET /accion/{accion} → 200 con Page<>")
    void t031_porAccion_retornaPaginado() throws Exception {
        when(auditoriaService.porAccionPaginado(any(), any(Pageable.class)))
                .thenReturn(paginaVacia(0, 50));

        mockMvc.perform(get("/api/admin/auditoria/accion/INSCRIPCION_CONFIRMADA")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test @Order(31)
    @DisplayName("T031b — GET /verificar → 200 con IntegridadResponse completa")
    void t031b_verificar_retornaIntegridadResponse() throws Exception {
        HashChainService.IntegridadResult mockResult =
                new HashChainService.IntegridadResult(0, List.of());
        when(auditoriaService.verificarIntegridadCompleta()).thenReturn(mockResult);

        mockMvc.perform(get("/api/admin/auditoria/verificar")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(true))
                .andExpect(jsonPath("$.totalRegistros").value(0))
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test @Order(31)
    @DisplayName("T031c — GET /verificar con errores → integra=false y lista de errores")
    void t031c_verificarConErrores_retornaFalso() throws Exception {
        List<String> errores = List.of(
                "Registro #5: hash esperado 'abc' pero era 'xyz'",
                "Registro #12: hash inválido"
        );
        HashChainService.IntegridadResult mockResult =
                new HashChainService.IntegridadResult(100, errores);
        when(auditoriaService.verificarIntegridadCompleta()).thenReturn(mockResult);

        mockMvc.perform(get("/api/admin/auditoria/verificar")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(false))
                .andExpect(jsonPath("$.totalRegistros").value(100))
                .andExpect(jsonPath("$.errores", hasSize(2)));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T026 — Performance (requiere DB real con 10k registros)
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(26)
    @Disabled("T026 — Requiere DB real con 10.000 registros. Ejecutar con @SpringBootTest en CI.")
    @DisplayName("T026 — SC-001: GET /api/admin/auditoria con 10k registros ≤ 500ms p95")
    void t026_performance_10kRegistros() {
        // Cubrir en DatabaseVolumeTest o suite de integración con @ActiveProfiles("perf")
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  T049 — DELETE /api/admin/auditoria/** → 405 Method Not Allowed
    //         Verificar retención 5 años: el log es inmutable, no hay DELETE
    // ════════════════════════════════════════════════════════════════════════════

    @Test @Order(49)
    @DisplayName("T049a — DELETE /api/admin/auditoria → 405 (no existe endpoint de borrado)")
    void t049a_delete_raiz_retorna405() throws Exception {
        mockMvc.perform(delete("/api/admin/auditoria"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test @Order(49)
    @DisplayName("T049b — DELETE /api/admin/auditoria/{id} → 404 (no hay mapping, inmutabilidad garantizada)")
    void t049b_delete_porId_noEsOk() throws Exception {
        // Spring devuelve 404 porque no existe ningún mapping DELETE para /{id}.
        // El controller solo expone GETs → borrado imposible a nivel HTTP.
        mockMvc.perform(delete("/api/admin/auditoria/1"))
                .andExpect(status().isNotFound());
    }

    @Test @Order(49)
    @DisplayName("T049c — DELETE /api/admin/auditoria/entidad/Inscripcion → 405")
    void t049c_delete_porEntidad_retorna405() throws Exception {
        mockMvc.perform(delete("/api/admin/auditoria/entidad/Inscripcion"))
                .andExpect(status().isMethodNotAllowed());
    }
}
