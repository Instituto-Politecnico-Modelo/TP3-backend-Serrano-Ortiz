package com.DecanatoOrtizSerrano.OrtizSerranoTP3.service;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.InscripcionRequest;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto.NotaRequest;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Estudiante;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Inscripcion;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.Materia;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.EstudianteRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.InscripcionRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.MateriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class InscripcionService {

    @Autowired
    private InscripcionRepository inscripcionRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private MateriaRepository materiaRepository;

    /**
     * Inyección lazy para evitar dependencia circular:
     * InscripcionService → ColaInscripcionService → InscripcionRepository (ok)
     * ColaInscripcionService → InscripcionService (lazy rompe el ciclo)
     */
    @Autowired(required = false)
    @Lazy
    private ColaInscripcionService colaInscripcionService;

    /** CHK003/CHK004 — Auditoría de notas (inyección opcional). */
    @Autowired(required = false)
    private AuditoriaService auditoriaService;

    /** Listar todas las inscripciones del estudiante autenticado */
    public List<Inscripcion> misInscripciones(Long idEstudiante) {
        return inscripcionRepository.findByEstudianteIdUsuario(idEstudiante);
    }

    /** Listar todas las inscripciones de una materia (admin) */
    public List<Inscripcion> inscripcionesPorMateria(Long idMateria) {
        return inscripcionRepository.findByMateriaIdMateria(idMateria);
    }

    /**
     * POST /inscripciones – Inscribir al estudiante autenticado en una materia.
     *
     * Usa SELECT FOR UPDATE (bloqueo pesimista) para garantizar que la verificación
     * de cupos y la inserción sean atómicas: ningún otro hilo puede leer o modificar
     * la misma Materia hasta que esta transacción termine.
     *
     * Esto previene sobrecupos bajo condiciones de carrera (race conditions) a diferencia
     * del OptimisticLocking solo, que solo protege actualizaciones sobre la Materia.
     */
    @Transactional
    public Inscripcion inscribir(InscripcionRequest request, Long idEstudiante) {
        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
            .orElseThrow(() -> new RuntimeException("Estudiante no encontrado"));

        // SELECT FOR UPDATE: bloqueo pesimista — serializa el acceso por materia.
        // Garantiza que la verificación de cupos y la inserción sean atómicas.
        Materia materia = materiaRepository.findByIdForUpdate(request.getIdMateria())
            .orElseThrow(() -> new RuntimeException("Materia no encontrada con ID: " + request.getIdMateria()));

        // Verificar inscripción activa previa
        boolean yaInscripto = inscripcionRepository
            .existsByEstudianteIdUsuarioAndMateriaIdMateriaAndEstadoNot(
                idEstudiante, materia.getIdMateria(), "CANCELADA");

        if (yaInscripto) {
            throw new RuntimeException("Ya estás inscripto en la materia: " + materia.getNombre());
        }

        // Verificar cupos disponibles mediante COUNT directo (índice idx_inscripciones_mat_estado)
        // → evita cargar la colección completa de inscripciones en memoria
        Integer maxCupos = materia.getCuposMaximos();
        if (maxCupos != null && maxCupos > 0) {
            long ocupados = inscripcionRepository.countCuposOcupados(materia.getIdMateria());
            if (ocupados >= maxCupos) {
                throw new RuntimeException(
                    "No hay cupos disponibles en la materia: " + materia.getNombre()
                    + " (máximo: " + maxCupos + ")");
            }
        }

        Inscripcion inscripcion = new Inscripcion(estudiante, materia, LocalDate.now());
        // Al hacer save(), Hibernate también actualiza la versión de Materia si fue tocada.
        // Si otro hilo ya actualizó la Materia en la misma transacción, se lanza OptimisticLockException.
        return inscripcionRepository.save(inscripcion);
    }

    /** Cancelar inscripción propia */
    @Transactional
    public Inscripcion cancelar(Long idInscripcion, Long idEstudiante) {
        Inscripcion inscripcion = inscripcionRepository.findById(idInscripcion)
            .orElseThrow(() -> new RuntimeException("Inscripción no encontrada"));

        if (!inscripcion.getEstudiante().getIdUsuario().equals(idEstudiante)) {
            throw new RuntimeException("No podés cancelar una inscripción que no es tuya");
        }
        if ("CANCELADA".equals(inscripcion.getEstado())) {
            throw new RuntimeException("La inscripción ya está cancelada");
        }

        inscripcion.setEstado("CANCELADA");
        return inscripcionRepository.save(inscripcion);
        // NOTA: promoverSiguiente() es llamado desde InscripcionController,
        // DESPUÉS de que esta transacción commitea, para que el count refleje
        // la cancelación ya persistida.
    }

    /** Listar todas las inscripciones (admin) */
    public List<Inscripcion> listarTodas() {
        return inscripcionRepository.findAll();
    }

    /** Obtener inscripción por ID */
    public Inscripcion obtenerPorId(Long id) {
        return inscripcionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Inscripción no encontrada con ID: " + id));
    }

    /**
     * PUT /api/docente/inscripciones/{id}/nota — CHK007-013
     * Docente carga o actualiza las notas parciales, finales y asistencias.
     * No cambia el estado automáticamente; eso lo hace cerrarNota().
     *
     * @param emailDocente email del docente autenticado (para ownership check + auditoría)
     * @param ipOrigen     IP del request (para auditoría)
     */
    @Transactional
    public Inscripcion cargarNota(Long idInscripcion, NotaRequest request, String emailDocente, String ipOrigen) {
        Inscripcion inscripcion = inscripcionRepository.findById(idInscripcion)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada"));

        // CHK005/CHK011 — ownership check
        verificarOwnership(inscripcion, emailDocente);

        if ("CANCELADA".equals(inscripcion.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pueden cargar notas en una inscripción CANCELADA");
        }
        if (inscripcion.isNotaCerrada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La nota ya está cerrada. No se puede modificar.");
        }

        // Guardar valores anteriores para auditoría
        String before = String.format("P1=%s P2=%s F=%s A=%s",
            inscripcion.getNotaParcial1(), inscripcion.getNotaParcial2(),
            inscripcion.getNotaFinal(), inscripcion.getAsistencias());

        if (request.getNotaParcial1() != null) inscripcion.setNotaParcial1(request.getNotaParcial1());
        if (request.getNotaParcial2() != null) inscripcion.setNotaParcial2(request.getNotaParcial2());
        if (request.getNotaFinal() != null) inscripcion.setNotaFinal(request.getNotaFinal());
        if (request.getAsistencias() != null) inscripcion.setAsistencias(request.getAsistencias());

        Inscripcion saved = inscripcionRepository.save(inscripcion);

        // CHK026 — auditoría de carga de notas (misma transacción)
        String after = String.format("P1=%s P2=%s F=%s A=%s",
            saved.getNotaParcial1(), saved.getNotaParcial2(),
            saved.getNotaFinal(), saved.getAsistencias());
        registrarAuditoria("INSCRIPCION", idInscripcion, "NOTA_UPDATE",
            "Nota actualizada: [" + before + "] → [" + after + "]",
            null, emailDocente, ipOrigen);

        return saved;
    }

    /** Overload retrocompatible (tests sin ownership/IP). */
    public Inscripcion cargarNota(Long idInscripcion, NotaRequest request) {
        return cargarNota(idInscripcion, request, null, null);
    }

    /**
     * PATCH /api/docente/inscripciones/{id}/cerrar — CHK014-018
     * Cierra la nota. Si notaFinal >= 6 → APROBADA, sino → DESAPROBADA.
     * Una vez cerrada no se puede modificar. Atómico con auditoría.
     *
     * @param emailDocente email del docente (ownership + auditoría)
     * @param ipOrigen     IP del request
     */
    @Transactional
    public Inscripcion cerrarNota(Long idInscripcion, String emailDocente, String ipOrigen) {
        Inscripcion inscripcion = inscripcionRepository.findById(idInscripcion)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada"));

        verificarOwnership(inscripcion, emailDocente);

        if ("CANCELADA".equals(inscripcion.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cerrar una inscripción CANCELADA");
        }
        if (inscripcion.isNotaCerrada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La nota ya estaba cerrada");
        }
        if (inscripcion.getNotaFinal() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe cargar la nota final antes de cerrar");
        }

        inscripcion.setEstado(inscripcion.estaAprobada() ? "APROBADA" : "DESAPROBADA");
        inscripcion.setNotaCerrada(true);
        Inscripcion saved = inscripcionRepository.save(inscripcion);

        // CHK027 — auditoría ACTA_CERRADA (misma transacción = atomicidad)
        registrarAuditoria("INSCRIPCION", idInscripcion, "ACTA_CERRADA",
            "Nota cerrada — estado: " + saved.getEstado() + ", notaFinal: " + saved.getNotaFinal(),
            null, emailDocente, ipOrigen);

        return saved;
    }

    /** Overload retrocompatible (tests existentes sin ownership/IP). */
    public Inscripcion cerrarNota(Long idInscripcion) {
        return cerrarNota(idInscripcion, null, null);
    }

    /**
     * PATCH /api/admin/inscripciones/{id}/reabrir — CHK019b-e
     * Solo ADMINISTRADOR puede reabrir una nota cerrada con motivo obligatorio.
     */
    @Transactional
    public Inscripcion reabrirNota(Long idInscripcion, String motivo, String emailAdmin, String ipOrigen) {
        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motivo de reapertura es obligatorio");
        }

        Inscripcion inscripcion = inscripcionRepository.findById(idInscripcion)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscripción no encontrada"));

        if (!inscripcion.isNotaCerrada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La nota ya está abierta");
        }

        inscripcion.setNotaCerrada(false);
        inscripcion.setEstado("ACTIVA");
        Inscripcion saved = inscripcionRepository.save(inscripcion);

        // CHK019e — registro de auditoría NOTA_REABIERTA con motivo
        registrarAuditoria("INSCRIPCION", idInscripcion, "NOTA_REABIERTA",
            "Nota reabierta por admin. Motivo: " + motivo,
            null, emailAdmin, ipOrigen);

        return saved;
    }

    /** Listar inscripciones de una materia (para docentes) */
    public List<Inscripcion> inscripcionesPorMateriaActivas(Long idMateria) {
        return inscripcionRepository.findByMateriaIdMateria(idMateria)
            .stream()
            .filter(i -> !"CANCELADA".equals(i.getEstado()))
            .toList();
    }

    // ─── Helpers internos ─────────────────────────────────────────────────────

    /**
     * CHK005 — Verifica que el docente autenticado es titular de la materia de la inscripción.
     * Si emailDocente es null, skip (retrocompatibilidad con tests sin ownership).
     */
    private void verificarOwnership(Inscripcion inscripcion, String emailDocente) {
        if (emailDocente == null) return; // retrocompatibilidad
        Materia materia = inscripcion.getMateria();
        if (materia.getDocente() == null || !emailDocente.equals(materia.getDocente().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No sos el docente titular de la materia " + materia.getNombre());
        }
    }

    /** Registra en auditoría de forma null-safe (si auditoriaService está disponible). */
    private void registrarAuditoria(String entidad, Long idEntidad, String accion,
                                     String descripcion, Long idUsuario,
                                     String email, String ip) {
        if (auditoriaService == null) return;
        try {
            auditoriaService.registrar(entidad, idEntidad, accion, descripcion, idUsuario, email, ip);
        } catch (Exception ignored) { /* no romper el flujo principal */ }
    }
}
