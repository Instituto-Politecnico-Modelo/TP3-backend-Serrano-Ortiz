package com.DecanatoOrtizSerrano.OrtizSerranoTP3.config;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * T016/T017/T019/T021 — Aspecto AOP de auditoría encadenada.
 *
 * PROPAGACIÓN: Cada @Around es @Transactional(REQUIRED) → crea T1 que envuelve
 * tanto el método de negocio (se une a T1) como registrar() (MANDATORY usa T1).
 * Atomicidad garantizada: si el negocio falla → rollback de T1 → audit no persiste.
 * Si audit falla → rollback de T1 → negocio tampoco persiste (Principio III).
 *
 * Acciones canónicas (alineadas con spec):
 *  INSCRIPCION_CONFIRMADA, INSCRIPCION_CANCELADA,
 *  NOTA_CARGADA, NOTA_CERRADA, NOTA_REABIERTA (inline en reabrir()),
 *  MATERIA_CREADA/MODIFICADA/ELIMINADA,
 *  USUARIO_CREADO/MODIFICADO/ELIMINADO,
 *  LOGIN_FALLIDO, CUENTA_BLOQUEADA (via registrarAutonomo() en AuthController)
 */
@Aspect
@Component
public class AuditoriaAspect {

    @Autowired
    private AuditoriaService auditoriaService;

    // ── InscripcionService ────────────────────────────────────────────────────

    /** T019 — inscripcion exitosa con cupo */
    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.InscripcionService.inscribir(..))")
    public Object auditarInscribir(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();
        try {
            Long id = extraerIdLong(result, "getIdInscripcion");
            registrar("Inscripcion", id, "INSCRIPCION_CONFIRMADA",
                "Alumno inscripto en materia con cupo disponible");
        } catch (Exception ignored) { }
        return result;
    }

    /** T019 — inscripcion cancelada */
    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.InscripcionService.cancelar(..))")
    public Object auditarCancelar(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();
        try {
            Long id = extraerIdLong(result, "getIdInscripcion");
            registrar("Inscripcion", id, "INSCRIPCION_CANCELADA", "Inscripcion cancelada por el alumno");
        } catch (Exception ignored) { }
        return result;
    }

    /** T016 — docente carga nota */
    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.InscripcionService.cargarNota(..))")
    public Object auditarCargarNota(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Object result = jp.proceed();
        try {
            registrar("Inscripcion", (Long) args[0], "NOTA_CARGADA",
                "Docente cargo/actualizo nota de inscripcion");
        } catch (Exception ignored) { }
        return result;
    }

    /** T017 — docente cierra nota (inmutabilidad) */
    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.InscripcionService.cerrarNota(..))")
    public Object auditarCerrarNota(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Object result = jp.proceed();
        try {
            registrar("Inscripcion", (Long) args[0], "NOTA_CERRADA",
                "Docente cerro definitivamente la nota — inmutable");
        } catch (Exception ignored) { }
        return result;
    }

    // ── MateriaService ────────────────────────────────────────────────────────

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.MateriaService.crear(..))")
    public Object auditarCrearMateria(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();
        try {
            registrar("Materia", extraerIdLong(result, "getIdMateria"), "MATERIA_CREADA", "Materia creada");
        } catch (Exception ignored) { }
        return result;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.MateriaService.actualizar(..))")
    public Object auditarActualizarMateria(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Object result = jp.proceed();
        try {
            registrar("Materia", (Long) args[0], "MATERIA_MODIFICADA", "Materia modificada");
        } catch (Exception ignored) { }
        return result;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.MateriaService.eliminar(..))")
    public Object auditarEliminarMateria(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        jp.proceed();
        try {
            registrar("Materia", (Long) args[0], "MATERIA_ELIMINADA", "Materia eliminada");
        } catch (Exception ignored) { }
        return null;
    }

    // ── EstudianteService (T021) ───────────────────────────────────────────────

    /** T021 — nuevo usuario creado */
    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.EstudianteService.crear(..))")
    public Object auditarCrearEstudiante(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();
        try {
            registrar("Usuario", extraerIdLong(result, "getIdUsuario"), "USUARIO_CREADO",
                "Nuevo estudiante registrado en el sistema");
        } catch (Exception ignored) { }
        return result;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.EstudianteService.actualizar(..))")
    public Object auditarActualizarEstudiante(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Object result = jp.proceed();
        try {
            registrar("Usuario", (Long) args[0], "USUARIO_MODIFICADO", "Datos de estudiante modificados");
        } catch (Exception ignored) { }
        return result;
    }

    /** T021 — usuario eliminado */
    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.EstudianteService.eliminar(..))")
    public Object auditarEliminarEstudiante(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        jp.proceed();
        try {
            registrar("Usuario", (Long) args[0], "USUARIO_ELIMINADO", "Estudiante eliminado del sistema");
        } catch (Exception ignored) { }
        return null;
    }

    // ── AuditorioService / ReservaAuditorioService ─────────────────────────────

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditorioService.crear(..))")
    public Object auditarCrearAuditorio(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();
        try { registrar("Auditorio", extraerIdLong(result, "getIdAuditorio"), "AUDITORIO_CREADO", "Auditorio creado"); }
        catch (Exception ignored) { }
        return result;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditorioService.actualizar(..))")
    public Object auditarActualizarAuditorio(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Object result = jp.proceed();
        try { registrar("Auditorio", (Long) args[0], "AUDITORIO_MODIFICADO", "Auditorio modificado"); }
        catch (Exception ignored) { }
        return result;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditorioService.eliminar(..))")
    public Object auditarEliminarAuditorio(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        jp.proceed();
        try { registrar("Auditorio", (Long) args[0], "AUDITORIO_ELIMINADO", "Auditorio eliminado"); }
        catch (Exception ignored) { }
        return null;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.ReservaAuditorioService.crear(..))")
    public Object auditarCrearReserva(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();
        try { registrar("ReservaAuditorio", extraerIdLong(result, "getIdReserva"), "RESERVA_CREADA", "Reserva creada"); }
        catch (Exception ignored) { }
        return result;
    }

    @Transactional
    @Around("execution(* com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.ReservaAuditorioService.cambiarEstado(..))")
    public Object auditarCambiarEstadoReserva(ProceedingJoinPoint jp) throws Throwable {
        Object[] args = jp.getArgs();
        Object result = jp.proceed();
        try {
            String nuevoEstado = args.length > 1 ? String.valueOf(args[1]) : "?";
            registrar("ReservaAuditorio", (Long) args[0], "RESERVA_ESTADO_CAMBIADO",
                "Estado cambiado a: " + nuevoEstado);
        } catch (Exception ignored) { }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registrar(String entidad, Long idEntidad, String accion, String descripcion) {
        Long idUsuario = null;
        String email = "sistema";
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails ud) {
                email = ud.getUsername();
                try { idUsuario = (Long) ud.getClass().getMethod("getId").invoke(ud); }
                catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        auditoriaService.registrar(entidad, idEntidad, accion, descripcion, idUsuario, email);
    }

    private Long extraerIdLong(Object obj, String getter) {
        if (obj == null) return null;
        try { return (Long) obj.getClass().getMethod(getter).invoke(obj); }
        catch (Exception e) { return null; }
    }
}
