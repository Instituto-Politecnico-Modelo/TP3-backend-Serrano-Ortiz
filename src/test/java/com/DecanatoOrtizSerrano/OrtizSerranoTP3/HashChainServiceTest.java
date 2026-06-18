package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.AuditoriaRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.HashChainService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * T009 — Pruebas unitarias de HashChainService.
 *
 * Cubre:
 *  1. GENESIS_HASH = sha256("GENESIS-AUDITORIA-INSTITUCIONAL") — 64 chars hex lowercase
 *  2. sha256() produce hex de 64 caracteres para cualquier input
 *  3. calcularHash() es determinístico (mismo RegistroAuditoria → mismo hash)
 *  4. calcularHash() es sensible a cambios en descripcion
 *  5. calcularHash() es sensible a cambios en accion
 *  6. calcularHash() es sensible a cambios en hashAnterior
 *  7. getUltimoHash() retorna GENESIS_HASH cuando no hay registros
 *  8. getUltimoHash() retorna hashActual del último registro cuando hay registros
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HashChainService — T009 Pruebas unitarias")
class HashChainServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @InjectMocks
    private HashChainService hashChainService;

    private static final LocalDateTime TS = LocalDateTime.of(2026, 6, 16, 10, 0, 0);

    // ─── 1. GENESIS_HASH tiene 64 caracteres hex ──────────────────────────────

    @Test
    @DisplayName("GENESIS_HASH es SHA-256 de 64 caracteres hex lowercase")
    void genesis_hash_es_64_chars_hex() {
        assertNotNull(HashChainService.GENESIS_HASH);
        assertEquals(64, HashChainService.GENESIS_HASH.length(),
            "SHA-256 debe producir exactamente 64 caracteres hex");
        assertTrue(HashChainService.GENESIS_HASH.matches("[0-9a-f]{64}"),
            "Debe ser hex en minúsculas (sin mayúsculas)");
    }

    // ─── 2. GENESIS_HASH coincide con sha256("GENESIS-AUDITORIA-INSTITUCIONAL") ─

    @Test
    @DisplayName("GENESIS_HASH coincide con sha256 de la cadena canónica")
    void genesis_hash_coincide_con_sha256_canonico() {
        String recalculado = hashChainService.sha256("GENESIS-AUDITORIA-INSTITUCIONAL");
        assertEquals(recalculado, HashChainService.GENESIS_HASH,
            "GENESIS_HASH debe ser sha256('GENESIS-AUDITORIA-INSTITUCIONAL')");
    }

    // ─── 3. sha256() produce hex de 64 chars ─────────────────────────────────

    @Test
    @DisplayName("sha256() produce hex de 64 caracteres para cualquier input")
    void sha256_produce_hex_64_chars() {
        String hash = hashChainService.sha256("input de prueba arbitrario");
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("sha256() es determinístico")
    void sha256_es_deterministico() {
        String h1 = hashChainService.sha256("mismo input");
        String h2 = hashChainService.sha256("mismo input");
        assertEquals(h1, h2);
    }

    // ─── 4. calcularHash() es determinístico ─────────────────────────────────

    @Test
    @DisplayName("calcularHash() produce el mismo hash para el mismo registro")
    void calcular_hash_deterministico() {
        RegistroAuditoria r = buildRegistro(HashChainService.GENESIS_HASH,
            "Inscripcion", 1L, "CREAR", "desc", TS, 10L);

        String h1 = hashChainService.calcularHash(r);
        String h2 = hashChainService.calcularHash(r);

        assertEquals(h1, h2, "calcularHash() debe ser determinístico");
        assertEquals(64, h1.length(), "Debe tener 64 caracteres hex");
    }

    // ─── 5. calcularHash() es sensible a cambios en descripcion ──────────────

    @Test
    @DisplayName("calcularHash() produce hash distinto si cambia la descripcion")
    void calcular_hash_sensible_a_descripcion() {
        RegistroAuditoria r1 = buildRegistro(HashChainService.GENESIS_HASH,
            "Inscripcion", 1L, "CREAR", "original", TS, 10L);
        RegistroAuditoria r2 = buildRegistro(HashChainService.GENESIS_HASH,
            "Inscripcion", 1L, "CREAR", "MODIFICADO", TS, 10L);

        assertNotEquals(hashChainService.calcularHash(r1), hashChainService.calcularHash(r2),
            "Hash debe diferir si cambia la descripción");
    }

    // ─── 6. calcularHash() es sensible a cambios en accion ───────────────────

    @Test
    @DisplayName("calcularHash() produce hash distinto si cambia la accion")
    void calcular_hash_sensible_a_accion() {
        RegistroAuditoria r1 = buildRegistro(HashChainService.GENESIS_HASH,
            "Inscripcion", 1L, "CREAR", "desc", TS, 10L);
        RegistroAuditoria r2 = buildRegistro(HashChainService.GENESIS_HASH,
            "Inscripcion", 1L, "ELIMINAR", "desc", TS, 10L);

        assertNotEquals(hashChainService.calcularHash(r1), hashChainService.calcularHash(r2));
    }

    // ─── 7. calcularHash() es sensible a cambios en hashAnterior ─────────────

    @Test
    @DisplayName("calcularHash() produce hash distinto si cambia hashAnterior")
    void calcular_hash_sensible_a_hash_anterior() {
        RegistroAuditoria r1 = buildRegistro("hash_a_aabbcc112233",
            "Inscripcion", 1L, "CREAR", "desc", TS, 10L);
        RegistroAuditoria r2 = buildRegistro("hash_b_ddeeff445566",
            "Inscripcion", 1L, "CREAR", "desc", TS, 10L);

        assertNotEquals(hashChainService.calcularHash(r1), hashChainService.calcularHash(r2));
    }

    // ─── 8. getUltimoHash() sin registros previos → GENESIS_HASH ─────────────

    @Test
    @DisplayName("getUltimoHash() retorna GENESIS_HASH cuando no hay registros")
    void get_ultimo_hash_sin_registros_retorna_genesis() {
        when(auditoriaRepository.findTopByOrderByIdRegistroDesc()).thenReturn(Optional.empty());

        String resultado = hashChainService.getUltimoHash();

        assertEquals(HashChainService.GENESIS_HASH, resultado);
    }

    // ─── 9. getUltimoHash() con registros → hashActual del último ─────────────

    @Test
    @DisplayName("getUltimoHash() retorna hashActual del último registro")
    void get_ultimo_hash_con_registros_retorna_hash_actual() {
        RegistroAuditoria ultimo = new RegistroAuditoria();
        ultimo.setHashActual("abc123def456abc123def456abc123def456abc123def456abc123def456abcd");
        when(auditoriaRepository.findTopByOrderByIdRegistroDesc()).thenReturn(Optional.of(ultimo));

        String resultado = hashChainService.getUltimoHash();

        assertEquals(ultimo.getHashActual(), resultado);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private RegistroAuditoria buildRegistro(String hashAnterior, String entidad, Long idEntidad,
            String accion, String descripcion, LocalDateTime ts, Long idUsuario) {
        RegistroAuditoria r = new RegistroAuditoria();
        r.setHashAnterior(hashAnterior);
        r.setEntidad(entidad);
        r.setIdEntidad(idEntidad);
        r.setAccion(accion);
        r.setDescripcion(descripcion);
        r.setTimestampEvento(ts);
        r.setIdUsuario(idUsuario);
        return r;
    }

    // ─── T033: verificarCadena() — cadena íntegra ─────────────────────────────

    @Test
    @DisplayName("T033 — verificarCadena() con cadena íntegra → integra=true, errores=[]")
    void t033_verificarCadena_cadena_integra() {
        // Construir cadena de 3 registros correctamente encadenados
        RegistroAuditoria r1 = buildRegistro(HashChainService.GENESIS_HASH,
                "Inscripcion", 1L, "INSCRIPCION_CONFIRMADA", "Juan Perez inscripto", TS, 10L);
        r1.setIdRegistro(1L);
        r1.setHashActual(hashChainService.calcularHash(r1));

        RegistroAuditoria r2 = buildRegistro(r1.getHashActual(),
                "Inscripcion", 1L, "NOTA_CARGADA", "nota 8 cargada", TS, 20L);
        r2.setIdRegistro(2L);
        r2.setHashActual(hashChainService.calcularHash(r2));

        RegistroAuditoria r3 = buildRegistro(r2.getHashActual(),
                "Usuario", 5L, "LOGIN_FALLIDO", "intento fallido desde 192.168.1.1", TS, null);
        r3.setIdRegistro(3L);
        r3.setHashActual(hashChainService.calcularHash(r3));

        Page<RegistroAuditoria> pagina = new PageImpl<>(
                List.of(r1, r2, r3),
                PageRequest.of(0, 1000, Sort.by("idRegistro").ascending()),
                3L);
        when(auditoriaRepository.findAll(any(Pageable.class))).thenReturn(pagina);

        HashChainService.IntegridadResult result = hashChainService.verificarCadena();

        assertTrue(result.isIntegra(), "Cadena íntegra → integra debe ser true");
        assertTrue(result.getErrores().isEmpty(), "Cadena íntegra → errores debe estar vacío");
        assertEquals(3, result.getTotalRegistros(), "Debe haber procesado 3 registros");
    }

    // ─── T034: verificarCadena() — detecta manipulación directa en BD ─────────

    @Test
    @DisplayName("T034 — verificarCadena() detecta hash inválido por manipulación directa en BD")
    void t034_verificarCadena_detecta_manipulacion() {
        // Registro 1: íntegro
        RegistroAuditoria r1 = buildRegistro(HashChainService.GENESIS_HASH,
                "Inscripcion", 1L, "INSCRIPCION_CONFIRMADA", "descripcion original", TS, 10L);
        r1.setIdRegistro(1L);
        r1.setHashActual(hashChainService.calcularHash(r1));

        // Registro 2: hash calculado correctamente, luego se adultera la descripción
        // Simula un UPDATE directo en la BD que cambia un campo sin recalcular el hash
        RegistroAuditoria r2 = buildRegistro(r1.getHashActual(),
                "Inscripcion", 1L, "NOTA_CARGADA", "nota original", TS, 20L);
        r2.setIdRegistro(2L);
        r2.setHashActual(hashChainService.calcularHash(r2)); // hash correcto antes de adulteración
        r2.setDescripcion("nota ADULTERADA directamente en la BD");  // manipulación detectada

        Page<RegistroAuditoria> pagina = new PageImpl<>(
                List.of(r1, r2),
                PageRequest.of(0, 1000, Sort.by("idRegistro").ascending()),
                2L);
        when(auditoriaRepository.findAll(any(Pageable.class))).thenReturn(pagina);

        HashChainService.IntegridadResult result = hashChainService.verificarCadena();

        assertFalse(result.isIntegra(), "Cadena comprometida → integra debe ser false");
        assertFalse(result.getErrores().isEmpty(), "Debe reportar al menos un error");
        assertTrue(result.getErrores().stream().anyMatch(e -> e.contains("MANIPULADOS")),
                "El error debe indicar datos manipulados: " + result.getErrores());
        assertEquals(2, result.getTotalRegistros(), "Debe haber procesado 2 registros");
    }

    // ─── T035: performance (requiere DB real) ─────────────────────────────────

    @Test
    @Disabled("T035 — Requiere DB real con 10.000 registros. Ejecutar en DatabaseVolumeTest con @ActiveProfiles(\"test\").")
    @DisplayName("T035 — verificarCadena() con 10.000 registros completa en ≤30s (SC-002)")
    void t035_verificarCadena_performance_10k() {
        // Cubrir en suite de integración con DB real y datos de volumen
    }
}
