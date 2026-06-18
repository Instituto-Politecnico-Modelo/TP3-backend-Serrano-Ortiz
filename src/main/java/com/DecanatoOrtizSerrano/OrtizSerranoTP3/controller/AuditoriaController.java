package com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.AuditoriaIntegridadResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.HashChainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * T027-T031 — Endpoints paginados de consulta y verificación del log de auditoría.
 *
 * Todos los endpoints requieren rol ADMINISTRADOR.
 * SC-001: GET /api/admin/auditoria?page=0&size=50 con 10.000 registros ≤ 500ms p95.
 * El parámetro size tiene un máximo de 100; si se excede → HTTP 400.
 */
@Tag(name = "Auditoría", description = "Consulta y verificación de integridad del log de auditoría encadenada")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
@RestController
@RequestMapping("/api/admin/auditoria")
@PreAuthorize("hasAuthority('ADMINISTRADOR')")
public class AuditoriaController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    @Autowired
    private AuditoriaService auditoriaService;

    // ── helpers ─────────────────────────────────────────────────────────────────

    private Pageable buildPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by("idRegistro").ascending());
    }

    private ResponseEntity<?> sizeError(int size) {
        if (size < 1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "El parámetro size debe ser al menos 1. Valor recibido: " + size,
                    "minSize", 1,
                    "maxSize", MAX_PAGE_SIZE));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "El parámetro size no puede superar " + MAX_PAGE_SIZE
                        + ". Valor recibido: " + size,
                "maxSize", MAX_PAGE_SIZE));
    }

    // ── T027 ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/auditoria?page=0&size=50
     * Lista paginada de todos los registros.
     * SC-001: ≤500ms p95 con 10.000 registros (índice idx_audit_timestamp cubre el ORDER BY).
     */
    @Operation(summary = "Listar registros paginados",
               description = "Devuelve el log de auditoría paginado. page=0-based; size máx 100 (default 50).")
    @GetMapping
    public ResponseEntity<?> listarTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) return sizeError(size);
        Page<RegistroAuditoria> result = auditoriaService.listarTodosPaginado(buildPageable(page, size));
        return ResponseEntity.ok(result);
    }

    // ── T028 ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/auditoria/entidad/{entidad}?page=0&size=50
     * Filtra paginado por tipo de entidad (ej: "Inscripcion", "Materia", "Usuario").
     */
    @Operation(summary = "Filtrar paginado por entidad",
               description = "Devuelve registros de una entidad específica paginados.")
    @GetMapping("/entidad/{entidad}")
    public ResponseEntity<?> porEntidad(
            @PathVariable String entidad,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) return sizeError(size);
        return ResponseEntity.ok(auditoriaService.porEntidadPaginado(entidad, buildPageable(page, size)));
    }

    // ── T029 ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/auditoria/entidad/{entidad}/{id}?page=0&size=50
     * Historial paginado de un objeto específico (entidad + id).
     */
    @Operation(summary = "Historial paginado de un objeto",
               description = "Devuelve todos los registros de auditoría de un objeto específico.")
    @GetMapping("/entidad/{entidad}/{id}")
    public ResponseEntity<?> porEntidadYId(
            @PathVariable String entidad,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) return sizeError(size);
        return ResponseEntity.ok(
                auditoriaService.porEntidadYIdPaginado(entidad, id, buildPageable(page, size)));
    }

    // ── T030 ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/auditoria/usuario/{idUsuario}?page=0&size=50
     * Acciones paginadas de un usuario específico.
     */
    @Operation(summary = "Acciones paginadas de un usuario",
               description = "Devuelve todos los registros generados por un usuario específico.")
    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<?> porUsuario(
            @PathVariable Long idUsuario,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) return sizeError(size);
        return ResponseEntity.ok(auditoriaService.porUsuarioPaginado(idUsuario, buildPageable(page, size)));
    }

    // ── T031 ─────────────────────────────────────────────────────────────────────

    /**
     * GET /api/admin/auditoria/accion/{accion}?page=0&size=50
     * Registros paginados de un tipo de acción (INSCRIPCION_CONFIRMADA, NOTA_CERRADA, etc.).
     */
    @Operation(summary = "Filtrar paginado por acción",
               description = "Devuelve registros de un tipo de acción paginados.")
    @GetMapping("/accion/{accion}")
    public ResponseEntity<?> porAccion(
            @PathVariable String accion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) return sizeError(size);
        return ResponseEntity.ok(auditoriaService.porAccionPaginado(accion, buildPageable(page, size)));
    }

    // ── US3 — Verificación de integridad ─────────────────────────────────────────

    /**
     * GET /api/admin/auditoria/verificar
     * Verifica la integridad completa de la cadena SHA-256.
     * Recorre todos los registros en lotes y recomputa cada hash.
     * Si todos coinciden → cadena íntegra. Si alguno falla → datos manipulados.
     *
     * NOTA: Usa verificarIntegridadCompleta() que retorna totalRegistros
     * directamente del recorrido, sin un COUNT(*) extra sobre la tabla.
     */
    @Operation(summary = "Verificar integridad de la cadena",
               description = "Recorre todos los registros y recomputa cada hash SHA-256. "
                       + "Vacío = íntegro. Puede tardar unos segundos con grandes volúmenes.")
    @GetMapping("/verificar")
    public ResponseEntity<AuditoriaIntegridadResponse> verificarIntegridad() {
        HashChainService.IntegridadResult result = auditoriaService.verificarIntegridadCompleta();
        boolean integra = result.getErrores().isEmpty();
        return ResponseEntity.ok(
                new AuditoriaIntegridadResponse(integra, result.getTotalRegistros(), result.getErrores()));
    }
}
