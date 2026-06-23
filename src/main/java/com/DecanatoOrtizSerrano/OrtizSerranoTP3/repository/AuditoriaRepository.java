package com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /** Último registro insertado (para encadenar el siguiente) */
    @Query("SELECT r FROM RegistroAuditoria r ORDER BY r.idRegistro DESC LIMIT 1")
    Optional<RegistroAuditoria> findUltimoRegistro();

    // ─── Consultas paginadas ──────────────────────────────────────────────────

    Page<RegistroAuditoria> findAllByOrderByIdRegistroAsc(Pageable pageable);

    Page<RegistroAuditoria> findByEntidadOrderByIdRegistroAsc(String entidad, Pageable pageable);

    Page<RegistroAuditoria> findByEntidadAndIdEntidadOrderByIdRegistroAsc(String entidad, Long idEntidad, Pageable pageable);

    Page<RegistroAuditoria> findByIdUsuarioOrderByIdRegistroAsc(Long idUsuario, Pageable pageable);

    Page<RegistroAuditoria> findByAccionOrderByIdRegistroAsc(String accion, Pageable pageable);

    Page<RegistroAuditoria> findByTimestampEventoBetweenOrderByIdRegistroAsc(
        LocalDateTime desde, LocalDateTime hasta, Pageable pageable);

    // ─── Consultas sin paginación (para verificación de integridad) ───────────

    List<RegistroAuditoria> findByEntidadOrderByIdRegistroAsc(String entidad);

    List<RegistroAuditoria> findByEntidadAndIdEntidadOrderByIdRegistroAsc(String entidad, Long idEntidad);

    List<RegistroAuditoria> findByIdUsuarioOrderByIdRegistroAsc(Long idUsuario);

    List<RegistroAuditoria> findByAccionOrderByIdRegistroAsc(String accion);

    List<RegistroAuditoria> findByTimestampEventoBetweenOrderByIdRegistroAsc(
        LocalDateTime desde, LocalDateTime hasta);

    /** Todos los registros en orden para verificar la cadena completa */
    List<RegistroAuditoria> findAllByOrderByIdRegistroAsc();
}
