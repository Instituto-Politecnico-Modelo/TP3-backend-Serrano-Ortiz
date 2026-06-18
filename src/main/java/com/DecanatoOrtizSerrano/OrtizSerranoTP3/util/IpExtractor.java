package com.DecanatoOrtizSerrano.OrtizSerranoTP3.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * T046 — Extractor de IP del cliente.
 *
 * Estrategia (en orden de prioridad):
 *  1. Header X-Forwarded-For (proxy inverso / load balancer)
 *  2. Header X-Real-IP (nginx)
 *  3. HttpServletRequest.getRemoteAddr() (conexión directa)
 *
 * Limitado a IPv4/IPv6 (45 chars max — coincide con columna ip_origen VARCHAR(45)).
 */
@Component
public class IpExtractor {

    /**
     * Extrae la IP real del cliente del request HTTP.
     * Nunca retorna null — devuelve "unknown" si no puede determinarse.
     */
    public String extraer(HttpServletRequest request) {
        if (request == null) return "unknown";

        // 1. X-Forwarded-For: puede contener lista "ip1, ip2, ip3" → tomar la primera
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String primera = forwarded.split(",")[0].trim();
            if (!primera.isEmpty()) return truncar(primera);
        }

        // 2. X-Real-IP (nginx)
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncar(realIp.trim());
        }

        // 3. Dirección directa de la conexión TCP
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? truncar(remoteAddr) : "unknown";
    }

    /** Trunca a 45 chars para que quepa en VARCHAR(45) de la columna ip_origen */
    private String truncar(String ip) {
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
