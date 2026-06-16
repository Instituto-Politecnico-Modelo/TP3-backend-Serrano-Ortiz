package com.DecanatoOrtizSerrano.OrtizSerranoTP3;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.AuditoriaRepository;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.AuditoriaService;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.HashChainService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T010 — Pruebas unitarias de AuditoriaService.
 *
 * Cubre:
 *  1. registrar() usa GENESIS_HASH cuando no hay registros previos
 *  2. registrar() encadena al hash del último registro existente
 *  3. registrar() llama a hashChainService.calcularHash() una vez
 *  4. registrar() persiste el registro via auditoriaRepository.save()
 *  5. verificarIntegridad() delega en hashChainService.verificarCadena()
 *  6. verificarIntegridad() propaga la lista de errores del resultado
 *  7. porEntidad() delega en repositorio
 *  8. porAccion() delega en repositorio
 *  9. listarTodos() delega en repositorio
 *
 * NOTA: Los tests de calcularHash() y sha256() se encuentran en HashChainServiceTest (T009).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditoriaService — T010 Pruebas unitarias")
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @Mock
    private HashChainService hashChainService;

    @InjectMocks
    private AuditoriaService auditoriaService;

    private static final String GENESIS = HashChainService.GENESIS_HASH;
    private static final String FAKE_HASH = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";

    // ─── 1. registrar() usa GENESIS cuando no hay registros ──────────────────

    @Test
    @DisplayName("registrar() usa GENESIS_HASH como hashAnterior cuando no hay registros previos")
    void registrar_sinRegistrosPrevios_usaGenesis() {
        when(hashChainService.getUltimoHash()).thenReturn(GENESIS);
        when(hashChainService.calcularHash(any(RegistroAuditoria.class))).thenReturn(FAKE_HASH);
        when(auditoriaRepository.save(any(RegistroAuditoria.class))).thenAnswer(i -> i.getArgument(0));

        RegistroAuditoria resultado = auditoriaService.registrar(
            "Inscripcion", 1L, "CREAR", "Inscripción creada", 10L, "ana@test.com", "127.0.0.1");

        assertEquals(GENESIS, resultado.getHashAnterior());
        assertEquals(FAKE_HASH, resultado.getHashActual());
        verify(auditoriaRepository).save(resultado);
    }

    // ─── 2. registrar() encadena al hash del registro anterior ───────────────

    @Test
    @DisplayName("registrar() usa el hashActual del último registro como hashAnterior")
    void registrar_conRegistroPrevio_encadenaHash() {
        String hashPrevio = "prevhash11223344556677889900aabbccddeeff11223344556677889900aabb";
        when(hashChainService.getUltimoHash()).thenReturn(hashPrevio);
        when(hashChainService.calcularHash(any(RegistroAuditoria.class))).thenReturn(FAKE_HASH);
        when(auditoriaRepository.save(any(RegistroAuditoria.class))).thenAnswer(i -> i.getArgument(0));

        RegistroAuditoria resultado = auditoriaService.registrar(
            "Materia", 5L, "MODIFICAR", "Cupos actualizados", 1L, "admin@decanato.edu", null);

        assertEquals(hashPrevio, resultado.getHashAnterior());
        verify(hashChainService).getUltimoHash();
    }

    // ─── 3. registrar() delega cálculo de hash en HashChainService ───────────

    @Test
    @DisplayName("registrar() llama a hashChainService.calcularHash() exactamente una vez")
    void registrar_llama_calcularHash_una_vez() {
        when(hashChainService.getUltimoHash()).thenReturn(GENESIS);
        when(hashChainService.calcularHash(any(RegistroAuditoria.class))).thenReturn(FAKE_HASH);
        when(auditoriaRepository.save(any(RegistroAuditoria.class))).thenAnswer(i -> i.getArgument(0));

        auditoriaService.registrar("Test", 1L, "ACCION", "desc", 1L, "u@test.com", null);

        verify(hashChainService, times(1)).calcularHash(any(RegistroAuditoria.class));
    }

    // ─── 4. registrar() persiste via save() ──────────────────────────────────

    @Test
    @DisplayName("registrar() siempre llama a auditoriaRepository.save() una vez")
    void registrar_llama_save_una_vez() {
        when(hashChainService.getUltimoHash()).thenReturn(GENESIS);
        when(hashChainService.calcularHash(any(RegistroAuditoria.class))).thenReturn(FAKE_HASH);
        when(auditoriaRepository.save(any(RegistroAuditoria.class))).thenAnswer(i -> i.getArgument(0));

        auditoriaService.registrar("Test", 1L, "ACCION", "desc", 1L, "u@test.com", null);

        verify(auditoriaRepository, times(1)).save(any(RegistroAuditoria.class));
    }

    // ─── 5. verificarIntegridad() delega en HashChainService ─────────────────

    @Test
    @DisplayName("verificarIntegridad() delega en hashChainService.verificarCadena()")
    void verificarIntegridad_delega_en_hashChainService() {
        HashChainService.IntegridadResult fakeResult =
            new HashChainService.IntegridadResult(100, List.of());
        when(hashChainService.verificarCadena()).thenReturn(fakeResult);

        List<String> errores = auditoriaService.verificarIntegridad();

        assertTrue(errores.isEmpty());
        verify(hashChainService).verificarCadena();
    }

    // ─── 6. verificarIntegridad() propaga errores ────────────────────────────

    @Test
    @DisplayName("verificarIntegridad() propaga la lista de errores de HashChainService")
    void verificarIntegridad_propaga_errores() {
        List<String> erroresEsperados = List.of("Registro #5: DATOS MANIPULADOS");
        HashChainService.IntegridadResult fakeResult =
            new HashChainService.IntegridadResult(10, erroresEsperados);
        when(hashChainService.verificarCadena()).thenReturn(fakeResult);

        List<String> resultado = auditoriaService.verificarIntegridad();

        assertEquals(erroresEsperados, resultado);
    }

    // ─── 7. porEntidad() delega en repositorio ────────────────────────────────

    @Test
    @DisplayName("porEntidad() retorna registros filtrados por entidad")
    void porEntidad_delegaEnRepositorio() {
        RegistroAuditoria r1 = new RegistroAuditoria();
        r1.setEntidad("Inscripcion");
        when(auditoriaRepository.findByEntidadOrderByIdRegistroAsc("Inscripcion"))
            .thenReturn(List.of(r1));

        List<RegistroAuditoria> resultado = auditoriaService.porEntidad("Inscripcion");

        assertEquals(1, resultado.size());
        assertEquals("Inscripcion", resultado.get(0).getEntidad());
    }

    // ─── 8. porAccion() delega en repositorio ────────────────────────────────

    @Test
    @DisplayName("porAccion() retorna registros filtrados por acción")
    void porAccion_delegaEnRepositorio() {
        when(auditoriaRepository.findByAccionOrderByIdRegistroAsc("LOGIN_FALLIDO"))
            .thenReturn(List.of());

        List<RegistroAuditoria> resultado = auditoriaService.porAccion("LOGIN_FALLIDO");

        assertTrue(resultado.isEmpty());
        verify(auditoriaRepository).findByAccionOrderByIdRegistroAsc("LOGIN_FALLIDO");
    }

    // ─── 9. listarTodos() delega en repositorio ──────────────────────────────

    @Test
    @DisplayName("listarTodos() retorna todos los registros ordenados")
    void listarTodos_delegaEnRepositorio() {
        RegistroAuditoria r = new RegistroAuditoria();
        when(auditoriaRepository.findAllByOrderByIdRegistroAsc()).thenReturn(List.of(r));

        List<RegistroAuditoria> resultado = auditoriaService.listarTodos();

        assertEquals(1, resultado.size());
        verify(auditoriaRepository).findAllByOrderByIdRegistroAsc();
    }
}
