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

    /** Último registro insertado — Spring Data naming convention para HashChainService */
    Optional<RegistroAuditoria> findTopByOrderByIdRegistroDesc();

    /** Último registro insertado (retrocompat — JPQL explícito) */
    @Query("SELECT r FROM RegistroAuditoria r ORDER BY r.idRegistro DESC LIMIT 1")
    Optional<RegistroAuditoria> findUltimoRegistro();

    List<RegistroAuditoria> findByEntidadOrderByIdRegistroAsc(String entidad);

    List<RegistroAuditoria> findByEntidadAndIdEntidadOrderByIdRegistroAsc(String entidad, Long idEntidad);

    List<RegistroAuditoria> findByIdUsuarioOrderByIdRegistroAsc(Long idUsuario);

    List<RegistroAuditoria> findByAccionOrderByIdRegistroAsc(String accion);

    List<RegistroAuditoria> findByTimestampEventoBetweenOrderByIdRegistroAsc(
        LocalDateTime desde, LocalDateTime hasta);

    /** Todos los registros en orden para verificar la cadena completa */
    List<RegistroAuditoria> findAllByOrderByIdRegistroAsc();

    // ─── Versiones paginadas (para GET /api/admin/auditoria — US2) ─────────────

    /** Paginado genérico: hereda de JpaRepository.findAll(Pageable) */

    Page<RegistroAuditoria> findByEntidad(String entidad, Pageable pageable);

    Page<RegistroAuditoria> findByEntidadAndIdEntidad(String entidad, Long idEntidad, Pageable pageable);

    Page<RegistroAuditoria> findByIdUsuario(Long idUsuario, Pageable pageable);

    Page<RegistroAuditoria> findByAccion(String accion, Pageable pageable);
}
