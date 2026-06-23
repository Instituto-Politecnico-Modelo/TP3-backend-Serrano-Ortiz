package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.NotaRequest;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Docente;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Estudiante;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Inscripcion;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Materia;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.InscripcionRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.InscripcionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;


import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature 003 — Gestión de Notas: tests unitarios.
 * CHK036-CHK046
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InscripcionService — Gestión de Notas (003)")
class NotaServiceTest {

    @Mock private InscripcionRepository inscripcionRepository;
    @Mock private AuditoriaService auditoriaService;

    @InjectMocks
    private InscripcionService inscripcionService;

    private Inscripcion inscripcionAbierta;
    private Inscripcion inscripcionCerrada;

    @BeforeEach
    void setUp() {
        inscripcionAbierta = crearInscripcion(false, "ACTIVA");
        inscripcionCerrada = crearInscripcion(true, "APROBADA");
    }

    // ─── CHK036: cargarNota con nota abierta → 200 ─────────────────────────

    @Test
    @DisplayName("CHK036 — cargarNota con notaCerrada=false → actualiza notas")
    void chk036_cargarNotaAbierta() {
        when(inscripcionRepository.findById(1L)).thenReturn(Optional.of(inscripcionAbierta));
        when(inscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotaRequest req = new NotaRequest();
        req.setNotaParcial1(7.5);
        req.setAsistencias(85);

        Inscripcion result = inscripcionService.cargarNota(1L, req, "doc@ipm.edu.ar", "10.0.0.1");

        assertThat(result.getNotaParcial1()).isEqualTo(7.5);
        assertThat(result.getAsistencias()).isEqualTo(85);
        verify(auditoriaService).registrar(eq("INSCRIPCION"), eq(1L), eq("NOTA_UPDATE"),
            anyString(), isNull(), eq("doc@ipm.edu.ar"), eq("10.0.0.1"));
    }

    // ─── CHK037: cargarNota con notaCerrada=true → 409 ──────────────────────

    @Test
    @DisplayName("CHK037 — cargarNota con notaCerrada=true → HTTP 409")
    void chk037_cargarNotaCerrada409() {
        when(inscripcionRepository.findById(2L)).thenReturn(Optional.of(inscripcionCerrada));

        NotaRequest req = new NotaRequest();
        req.setNotaFinal(8.0);

        assertThatThrownBy(() -> inscripcionService.cargarNota(2L, req, "doc@ipm.edu.ar", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409);
    }

    // ─── CHK039: docente ajeno → 403 ────────────────────────────────────────

    @Test
    @DisplayName("CHK039 — cargarNota de docente ajeno → HTTP 403")
    void chk039_docenteAjeno403() {
        when(inscripcionRepository.findById(1L)).thenReturn(Optional.of(inscripcionAbierta));

        NotaRequest req = new NotaRequest();
        req.setNotaFinal(5.0);

        assertThatThrownBy(() -> inscripcionService.cargarNota(1L, req, "otro@ipm.edu.ar", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(403);
    }

    // ─── CHK040: cerrarNota → notaCerrada=true + auditoría ──────────────────

    @Test
    @DisplayName("CHK040 — cerrarNota → notaCerrada=true + audit ACTA_CERRADA")
    void chk040_cerrarNotaExitoso() {
        inscripcionAbierta.setNotaFinal(7.0);
        when(inscripcionRepository.findById(1L)).thenReturn(Optional.of(inscripcionAbierta));
        when(inscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Inscripcion result = inscripcionService.cerrarNota(1L, "doc@ipm.edu.ar", "10.0.0.1");

        assertThat(result.isNotaCerrada()).isTrue();
        assertThat(result.getEstado()).isEqualTo("APROBADA");
        verify(auditoriaService).registrar(eq("INSCRIPCION"), eq(1L), eq("ACTA_CERRADA"),
            anyString(), isNull(), eq("doc@ipm.edu.ar"), eq("10.0.0.1"));
    }

    // ─── CHK016: cerrarNota con nota ya cerrada → 409 ───────────────────────

    @Test
    @DisplayName("CHK016 — cerrarNota con nota ya cerrada → HTTP 409")
    void chk016_cerrarNotaYaCerrada409() {
        when(inscripcionRepository.findById(2L)).thenReturn(Optional.of(inscripcionCerrada));

        assertThatThrownBy(() -> inscripcionService.cerrarNota(2L, "doc@ipm.edu.ar", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409);
    }

    // ─── CHK040b: reabrir → notaCerrada=false + audit NOTA_REABIERTA ────────

    @Test
    @DisplayName("CHK040b — reabrirNota con motivo → notaCerrada=false + audit NOTA_REABIERTA")
    void chk040b_reabrirExitoso() {
        when(inscripcionRepository.findById(2L)).thenReturn(Optional.of(inscripcionCerrada));
        when(inscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Inscripcion result = inscripcionService.reabrirNota(2L, "Error de carga", "admin@ipm.edu.ar", "192.168.1.1");

        assertThat(result.isNotaCerrada()).isFalse();
        assertThat(result.getEstado()).isEqualTo("ACTIVA");
        verify(auditoriaService).registrar(eq("INSCRIPCION"), eq(2L), eq("NOTA_REABIERTA"),
            contains("Error de carga"), isNull(), eq("admin@ipm.edu.ar"), eq("192.168.1.1"));
    }

    // ─── CHK040c: reabrir sin motivo → 400 ──────────────────────────────────

    @Test
    @DisplayName("CHK040c — reabrirNota sin motivo → HTTP 400")
    void chk040c_reabrirSinMotivo400() {
        assertThatThrownBy(() -> inscripcionService.reabrirNota(2L, "", "admin@ipm.edu.ar", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(400);
    }

    // ─── CHK040e: reabrir nota ya abierta → 409 ─────────────────────────────

    @Test
    @DisplayName("CHK040e — reabrirNota con nota ya abierta → HTTP 409")
    void chk040e_reabrirYaAbierta409() {
        when(inscripcionRepository.findById(1L)).thenReturn(Optional.of(inscripcionAbierta));

        assertThatThrownBy(() -> inscripcionService.reabrirNota(1L, "motivo", "admin@ipm.edu.ar", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409);
    }

    // ─── CHK041: PUT + cerrar + PUT → segundo PUT = 409 ────────────────────

    @Test
    @DisplayName("CHK041 — secuencia carga→cierre→carga: segundo PUT = 409")
    void chk041_secuenciaCargaCierreCarga() {
        // Primera carga OK
        when(inscripcionRepository.findById(1L)).thenReturn(Optional.of(inscripcionAbierta));
        when(inscripcionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotaRequest req = new NotaRequest();
        req.setNotaFinal(8.0);
        inscripcionService.cargarNota(1L, req, "doc@ipm.edu.ar", null);

        // Cerrar
        inscripcionAbierta.setNotaFinal(8.0);
        inscripcionService.cerrarNota(1L, "doc@ipm.edu.ar", null);

        // Segunda carga → 409 (nota ya cerrada)
        assertThatThrownBy(() -> inscripcionService.cargarNota(1L, req, "doc@ipm.edu.ar", null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
            .isEqualTo(409);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Inscripcion crearInscripcion(boolean cerrada, String estado) {
        Docente doc = new Docente();
        doc.setEmail("doc@ipm.edu.ar");
        doc.setIdUsuario(10L);

        Materia mat = new Materia();
        mat.setIdMateria(1L);
        mat.setNombre("Matemática");
        mat.setDocente(doc);

        Estudiante est = new Estudiante();
        est.setIdUsuario(20L);
        est.setEmail("alumno@ipm.edu.ar");

        Inscripcion insc = new Inscripcion(est, mat, LocalDate.now());
        insc.setIdInscripcion(cerrada ? 2L : 1L);
        insc.setNotaCerrada(cerrada);
        insc.setEstado(estado);
        if (cerrada) {
            insc.setNotaFinal(7.0);
        }
        return insc;
    }
}
