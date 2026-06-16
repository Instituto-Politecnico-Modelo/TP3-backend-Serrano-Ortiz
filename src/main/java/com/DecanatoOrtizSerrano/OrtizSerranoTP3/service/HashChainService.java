package com.DecanatoOrtizSerrano.OrtizSerranoTP3.service;

import com.DecanatoOrtizSerrano.OrtizSerranoTP3.model.RegistroAuditoria;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.repository.AuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * T007 — Servicio responsable del cálculo y verificación de la cadena de hashes SHA-256.
 *
 * GENESIS_HASH = SHA-256("GENESIS-AUDITORIA-INSTITUCIONAL")
 * Inmutable, conocido, verificable — marca el origen de la cadena.
 *
 * La cadena funciona como un blockchain simplificado:
 * cada registro contiene el hash del anterior → cualquier modificación retroactiva
 * rompe la cadena y es detectable por verificarCadena().
 *
 * Separado de AuditoriaService para:
 *  - Responsabilidad única (SRP)
 *  - Testabilidad independiente de la criptografía
 *  - Reutilización en AuditoriaController y verificación de integridad
 */
@Service
public class HashChainService {

    /**
     * Hash inicial de la cadena.
     * SHA-256("GENESIS-AUDITORIA-INSTITUCIONAL") — calculado una sola vez al cargar la clase.
     * Cualquier sistema puede re-verificar este valor de forma independiente.
     */
    public static final String GENESIS_HASH = sha256Static("GENESIS-AUDITORIA-INSTITUCIONAL");

    /** Tamaño de lote para verificarCadena() — evita cargar 500k registros en memoria */
    private static final int VERIFICATION_BATCH_SIZE = 1000;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    // ─── API pública ──────────────────────────────────────────────────────────

    /**
     * Retorna el hashActual del último registro, o GENESIS_HASH si la tabla está vacía.
     * Usado por AuditoriaService.registrar() para encadenar el siguiente registro.
     */
    public String getUltimoHash() {
        return auditoriaRepository.findTopByOrderByIdRegistroDesc()
            .map(RegistroAuditoria::getHashActual)
            .orElse(GENESIS_HASH);
    }

    /**
     * Calcula el hashActual de un registro a partir de sus campos y su hashAnterior.
     *
     * Fórmula: SHA-256(hashAnterior | entidad | idEntidad | accion | descripcion | timestamp | idUsuario)
     *
     * Precondición: el registro debe tener hashAnterior y timestampEvento ya seteados.
     */
    public String calcularHash(RegistroAuditoria r) {
        String input = String.join("|",
            safeStr(r.getHashAnterior()),
            safeStr(r.getEntidad()),
            safeStr(r.getIdEntidad()),
            safeStr(r.getAccion()),
            safeStr(r.getDescripcion()),
            safeStr(r.getTimestampEvento()),
            safeStr(r.getIdUsuario())
        );
        return sha256(input);
    }

    /**
     * SHA-256 de un string arbitrario. Output: hex lowercase de 64 caracteres.
     * Expuesto como método público para testing y verificación externa.
     */
    public String sha256(String input) {
        return sha256Static(input);
    }

    // ─── Verificación de integridad (T035 / T036) ─────────────────────────────

    /**
     * Verifica la integridad completa de la cadena de hashes.
     * Procesa en lotes de VERIFICATION_BATCH_SIZE registros para soportar
     * tablas de gran tamaño sin cargar todo en memoria (SC-002: 10k registros ≤ 30s).
     *
     * Para cada registro verifica:
     *  1. hashAnterior coincide con el hashActual del registro anterior (o GENESIS_HASH)
     *  2. hashActual almacenado coincide con la recomputación de los campos
     *
     * @return IntegridadResult con lista de errores (vacía = cadena íntegra)
     */
    public IntegridadResult verificarCadena() {
        List<String> errores = new ArrayList<>();
        String hashEsperadoAnterior = GENESIS_HASH;
        int totalRegistros = 0;
        int page = 0;
        boolean hayMas = true;

        while (hayMas) {
            var batch = auditoriaRepository.findAll(
                PageRequest.of(page, VERIFICATION_BATCH_SIZE, Sort.by("idRegistro").ascending())
            );

            for (RegistroAuditoria r : batch.getContent()) {
                totalRegistros++;

                // 1. Verificar que hashAnterior coincide con el esperado
                if (!hashEsperadoAnterior.equals(r.getHashAnterior())) {
                    errores.add(String.format(
                        "Registro #%d: hashAnterior esperado='%s' pero tiene='%s' — cadena rota",
                        r.getIdRegistro(), hashEsperadoAnterior, r.getHashAnterior()
                    ));
                }

                // 2. Recomputar hashActual y comparar con el almacenado
                String hashRecomputado = calcularHash(r);
                if (!hashRecomputado.equals(r.getHashActual())) {
                    errores.add(String.format(
                        "Registro #%d: hashActual almacenado='%s' pero recomputado='%s' — DATOS MANIPULADOS",
                        r.getIdRegistro(), r.getHashActual(), hashRecomputado
                    ));
                }

                hashEsperadoAnterior = r.getHashActual();
            }

            hayMas = !batch.isLast();
            page++;
        }

        return new IntegridadResult(totalRegistros, errores);
    }

    // ─── SHA-256 estático (para inicialización de GENESIS_HASH) ───────────────

    private static String sha256Static(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible en esta JVM", e);
        }
    }

    private String safeStr(Object obj) {
        return obj == null ? "null" : obj.toString();
    }

    // ─── DTO resultado de verificación ───────────────────────────────────────

    /**
     * Resultado de verificarCadena().
     * Alineado con el DTO TypeScript IntegridadResponse del frontend.
     */
    public static class IntegridadResult {

        private final int totalRegistros;
        private final List<String> errores;

        public IntegridadResult(int totalRegistros, List<String> errores) {
            this.totalRegistros = totalRegistros;
            this.errores = errores;
        }

        public boolean isIntegra() {
            return errores.isEmpty();
        }

        public int getTotalRegistros() {
            return totalRegistros;
        }

        public List<String> getErrores() {
            return errores;
        }

        public String getMensaje() {
            return isIntegra()
                ? "Cadena íntegra — " + totalRegistros + " registros verificados"
                : "¡Cadena COMPROMETIDA! " + errores.size() + " registro(s) con errores detectados";
        }
    }
}
