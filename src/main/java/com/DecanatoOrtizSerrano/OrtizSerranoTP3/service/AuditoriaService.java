package com.DecanatoOrtizSerrano.OrtizSerranoTP3.service;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.AuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T008 — Servicio de Auditoría Encadenada.
 *
 * Delega el cálculo y verificación de hashes en {@link HashChainService}.
 *
 * PROPAGACIÓN MANDATORY — registrar() se ejecuta dentro de la transacción del llamador.
 * Si registrar() falla → rollback completo de la operación de negocio (nota, inscripción, etc.).
 * Si la operación de negocio falla → el registro de auditoría tampoco se persiste.
 * Esto garantiza la atomicidad nota+auditoría requerida por el Principio III (Inmutabilidad).
 *
 * Callers DEBEN tener @Transactional activo. Si no, lanza IllegalTransactionStateException.
 */
@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private HashChainService hashChainService;

    // ─── Registro de eventos ──────────────────────────────────────────────────

    /**
     * Registra un evento de auditoría y lo encadena al registro anterior.
     *
     * Propagation.MANDATORY: requiere una transacción activa del llamador.
     * Nunca crea su propia transacción — rollback atómico con la operación de negocio.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistroAuditoria registrar(
            String entidad,
            Long idEntidad,
            String accion,
            String descripcion,
            Long idUsuario,
            String emailUsuario,
            String ipOrigen) {

        String hashAnterior = hashChainService.getUltimoHash();

        RegistroAuditoria registro = new RegistroAuditoria();
        registro.setEntidad(entidad);
        registro.setIdEntidad(idEntidad);
        registro.setAccion(accion);
        registro.setDescripcion(descripcion);
        registro.setIdUsuario(idUsuario);
        registro.setEmailUsuario(emailUsuario);
        registro.setIpOrigen(ipOrigen);
        registro.setTimestampEvento(LocalDateTime.now());
        registro.setHashAnterior(hashAnterior);
        registro.setHashActual(hashChainService.calcularHash(registro));

        return auditoriaRepository.save(registro);
    }

    /** Sobrecarga sin IP (para uso interno del sistema) */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistroAuditoria registrar(String entidad, Long idEntidad,
                                       String accion, String descripcion,
                                       Long idUsuario, String emailUsuario) {
        return registrar(entidad, idEntidad, accion, descripcion, idUsuario, emailUsuario, null);
    }

    // ─── Consultas paginadas (para GET /api/admin/auditoria — US2) ────────────

    public Page<RegistroAuditoria> listarTodosPaginado(Pageable pageable) {
        return auditoriaRepository.findAll(pageable);
    }

    public Page<RegistroAuditoria> porEntidadPaginado(String entidad, Pageable pageable) {
        return auditoriaRepository.findByEntidad(entidad, pageable);
    }

    public Page<RegistroAuditoria> porUsuarioPaginado(Long idUsuario, Pageable pageable) {
        return auditoriaRepository.findByIdUsuario(idUsuario, pageable);
    }

    public Page<RegistroAuditoria> porAccionPaginado(String accion, Pageable pageable) {
        return auditoriaRepository.findByAccion(accion, pageable);
    }

    // ─── Consultas sin paginación (retrocompat / uso interno) ─────────────────

    public List<RegistroAuditoria> listarTodos() {
        return auditoriaRepository.findAllByOrderByIdRegistroAsc();
    }

    public List<RegistroAuditoria> porEntidad(String entidad) {
        return auditoriaRepository.findByEntidadOrderByIdRegistroAsc(entidad);
    }

    public List<RegistroAuditoria> porEntidadYId(String entidad, Long idEntidad) {
        return auditoriaRepository.findByEntidadAndIdEntidadOrderByIdRegistroAsc(entidad, idEntidad);
    }

    public List<RegistroAuditoria> porUsuario(Long idUsuario) {
        return auditoriaRepository.findByIdUsuarioOrderByIdRegistroAsc(idUsuario);
    }

    public List<RegistroAuditoria> porAccion(String accion) {
        return auditoriaRepository.findByAccionOrderByIdRegistroAsc(accion);
    }

    public List<RegistroAuditoria> porRangoFecha(LocalDateTime desde, LocalDateTime hasta) {
        return auditoriaRepository.findByTimestampEventoBetweenOrderByIdRegistroAsc(desde, hasta);
    }

    // ─── Verificación de integridad ───────────────────────────────────────────

    /**
     * Delega en HashChainService para verificar la cadena completa de hashes.
     * Retorna lista de errores (vacía = cadena íntegra).
     */
    public List<String> verificarIntegridad() {
        return hashChainService.verificarCadena().getErrores();
    }
}



