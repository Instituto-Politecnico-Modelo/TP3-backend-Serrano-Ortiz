package com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.AuditoriaIntegridadResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Endpoints de consulta y verificación de la auditoría encadenada.
 * Solo accesible por ADMINISTRADOR.
 * Todos los endpoints de listado devuelven respuestas paginadas (Spring Page).
 */
@Tag(name = "Auditoría", description = "Consulta y verificación de integridad del log de auditoría encadenada")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
@RestController
@RequestMapping("/api/admin/auditoria")
@PreAuthorize("hasAuthority('ADMINISTRADOR')")
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    /**
     * GET /api/admin/auditoria?page=0&size=50
     * Lista todos los registros de auditoría con paginación obligatoria.
     */
    @Operation(summary = "Listar todos los registros (paginado)",
               description = "Devuelve el log de auditoría paginado. Default: page=0, size=50, max size=100.")
    @GetMapping
    public ResponseEntity<Page<RegistroAuditoria>> listarTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditoriaService.listarTodosPaginado(page, size));
    }

    /**
     * GET /api/admin/auditoria/entidad/{entidad}?page=0&size=50
     * Filtra registros por tipo de entidad (ej: "INSCRIPCION", "NOTA").
     */
    @Operation(summary = "Filtrar por entidad (paginado)",
               description = "Devuelve registros de una entidad específica con paginación.")
    @GetMapping("/entidad/{entidad}")
    public ResponseEntity<Page<RegistroAuditoria>> porEntidad(
            @PathVariable String entidad,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditoriaService.porEntidadPaginado(entidad, page, size));
    }

    /**
     * GET /api/admin/auditoria/entidad/{entidad}/{id}?page=0&size=50
     * Filtra por entidad e ID del objeto (historial de un objeto específico).
     */
    @Operation(summary = "Historial de un objeto (paginado)",
               description = "Devuelve todos los registros de auditoría de un objeto específico.")
    @GetMapping("/entidad/{entidad}/{id}")
    public ResponseEntity<Page<RegistroAuditoria>> porEntidadYId(
            @PathVariable String entidad, @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditoriaService.porEntidadYIdPaginado(entidad, id, page, size));
    }

    /**
     * GET /api/admin/auditoria/usuario/{idUsuario}?page=0&size=50
     * Filtra por ID de usuario — todas las acciones que realizó.
     */
    @Operation(summary = "Acciones de un usuario (paginado)",
               description = "Devuelve todos los registros generados por un usuario específico.")
    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<Page<RegistroAuditoria>> porUsuario(
            @PathVariable Long idUsuario,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditoriaService.porUsuarioPaginado(idUsuario, page, size));
    }

    /**
     * GET /api/admin/auditoria/accion/{accion}?page=0&size=50
     * Filtra por tipo de acción (INSERT, UPDATE, LOGIN_FALLIDO, etc.).
     */
    @Operation(summary = "Filtrar por acción (paginado)",
               description = "Devuelve registros de un tipo de acción.")
    @GetMapping("/accion/{accion}")
    public ResponseEntity<Page<RegistroAuditoria>> porAccion(
            @PathVariable String accion,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditoriaService.porAccionPaginado(accion, page, size));
    }

    /**
     * GET /api/admin/auditoria/rango?desde=...&hasta=...&page=0&size=50
     * Filtra por rango de fechas (formato ISO: 2026-04-20T00:00:00).
     */
    @Operation(summary = "Filtrar por rango de fechas (paginado)",
               description = "Devuelve registros entre dos timestamps. Formato: 2026-04-20T00:00:00")
    @GetMapping("/rango")
    public ResponseEntity<Page<RegistroAuditoria>> porRango(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditoriaService.porRangoFechaPaginado(desde, hasta, page, size));
    }

    /**
     * GET /api/admin/auditoria/verificar
     * Verifica la integridad completa de la cadena recomputando cada hash.
     */
    @Operation(summary = "Verificar integridad de la cadena",
               description = "Recorre todos los registros y recomputa cada hash SHA-256. "
                           + "Devuelve lista de errores (vacía = cadena íntegra).")
    @GetMapping("/verificar")
    public ResponseEntity<AuditoriaIntegridadResponse> verificarIntegridad() {
        List<String> errores = auditoriaService.verificarIntegridad();
        int total = (int) auditoriaService.listarTodosPaginado(0, 1).getTotalElements();
        boolean integra = errores.isEmpty();
        return ResponseEntity.ok(new AuditoriaIntegridadResponse(integra, total, errores));
    }
}
