package com.DecanatoOrtizSerrano.OrtizSerranoTP3.controller;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.MessageResponse;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Inscripcion;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.InscripcionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * CHK019b — Endpoint de administración para inscripciones.
 * Solo ADMINISTRADOR puede reabrir notas cerradas.
 */
@Tag(name = "Admin – Notas", description = "Gestión administrativa de notas e inscripciones")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:3000}")
@RestController
@RequestMapping("/api/admin/inscripciones")
@PreAuthorize("hasAuthority('ADMINISTRADOR')")
public class AdminInscripcionesController {

    @Autowired
    private InscripcionService inscripcionService;

    /**
     * PATCH /api/admin/inscripciones/{id}/reabrir
     * Reabre una nota cerrada. Requiere motivo obligatorio.
     */
    @Operation(
        summary = "Reabrir nota cerrada",
        description = "Solo ADMINISTRADOR. Reabre una nota previamente cerrada. "
                    + "Body: { \"motivo\": \"razón...\" }. Se registra en auditoría."
    )
    @PatchMapping("/{id}/reabrir")
    public ResponseEntity<?> reabrirNota(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            String motivo = body != null ? body.get("motivo") : null;
            String emailAdmin = authentication != null ? authentication.getName() : null;
            String ip = obtenerIp(httpRequest);

            Inscripcion inscripcion = inscripcionService.reabrirNota(id, motivo, emailAdmin, ip);
            return ResponseEntity.ok(inscripcion);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                .body(new MessageResponse(ex.getReason()));
        }
    }

    private static String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
