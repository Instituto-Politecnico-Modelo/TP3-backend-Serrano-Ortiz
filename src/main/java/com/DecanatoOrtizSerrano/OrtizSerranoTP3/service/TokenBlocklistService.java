package com.DecanatoOrtizSerrano.OrtizSerranoTP3.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * CHK023d — Servicio de blocklist de tokens JWT.
 *
 * Almacena tokens revocados en Redis con TTL = tiempo restante del token.
 * Una vez expirado el token de forma natural, Redis lo elimina automáticamente.
 *
 * Si Redis no está disponible, el servicio degrada gracefully (log warning,
 * pero no bloquea el flujo — el token expirará naturalmente en <= 1h).
 */
@Service
public class TokenBlocklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlocklistService.class);
    private static final String KEY_PREFIX = "blacklist:token:";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * Agrega un token a la blocklist con TTL en milisegundos.
     *
     * @param token el JWT a revocar
     * @param ttlMs milisegundos hasta la expiración natural del token
     */
    public void revocar(String token, long ttlMs) {
        if (redisTemplate == null) {
            log.warn("Redis no disponible — el token revocado expirará naturalmente");
            return;
        }
        if (ttlMs <= 0) {
            return; // ya expiró, no hace falta almacenar
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + token, "revoked", ttlMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Error al revocar token en Redis: {}", e.getMessage());
        }
    }

    /**
     * Verifica si un token está en la blocklist.
     *
     * @param token el JWT a verificar
     * @return true si fue revocado y aún no expiró
     */
    public boolean estaRevocado(String token) {
        if (redisTemplate == null) {
            return false; // degradación: si Redis no está, tokens no revocados
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
        } catch (Exception e) {
            log.error("Error al consultar blocklist en Redis: {}", e.getMessage());
            return false; // en duda, permitir (mejor disponibilidad)
        }
    }
}
