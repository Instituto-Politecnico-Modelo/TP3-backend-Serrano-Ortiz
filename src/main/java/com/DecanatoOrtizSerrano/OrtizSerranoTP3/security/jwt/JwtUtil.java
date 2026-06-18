package com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utilidad para generar y validar tokens JWT
 */
@Component
public class JwtUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /** T007 — Refresh token TTL (default 7 días = 604.800.000 ms). */
    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpirationMs;
    
    /**
     * T007 — Genera un refresh token (TTL 7 días) con claim "type=refresh".
     */
    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtRefreshExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * T007 — Retorna los milisegundos restantes hasta la expiración del token.
     * Retorna 0 si el token ya expiró.
     */
    public long getRemainingTtl(String token) {
        try {
            Date expiration = getExpirationFromToken(token);
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remaining);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Helper de tests T021 — Genera un access token con username y rol explícitos,
     * sin necesitar un objeto Authentication.
     */
    public String generateTokenFromUsernameAndRole(String username, String rol) {
        return Jwts.builder()
                .subject(username)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Genera un token JWT a partir de la autenticación (sin rol en el payload)
     */
    public String generateJwtToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return generateTokenFromUsername(userPrincipal.getUsername());
    }

    /**
     * Genera un token JWT incluyendo el rol del usuario como claim "rol"
     */
    public String generateJwtTokenWithRole(Authentication authentication, String rol) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * Genera un token JWT a partir del username
     */
    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrae el claim "rol" del token JWT. Retorna null si no existe.
     */
    public String getRolFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get("rol", String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Obtiene la clave de firma
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * Extrae el username del token JWT
     */
    public String getUsernameFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
    
    /**
     * Valida el token JWT
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            logger.error("Token JWT inválido o firma incorrecta: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT no soportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string está vacío: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extrae la fecha de expiración del token (sin lanzar excepción si expiró)
     */
    public Date getExpirationFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration();
        } catch (ExpiredJwtException e) {
            // el token expiró pero podemos leer la fecha igual
            return e.getClaims().getExpiration();
        }
    }

    /**
     * Extrae la fecha de emisión del token (sin lanzar excepción si expiró)
     */
    public Date getIssuedAtFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getIssuedAt();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getIssuedAt();
        }
    }

    /**
     * Devuelve el subject del token aunque esté expirado (para inspect).
     * Retorna null si el token es inválido (firma manipulada, malformado, etc.).
     */
    public String getUsernameFromTokenIgnoreExpiry(String token) {
        try {
            return getUsernameFromJwtToken(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determina el motivo de invalidez de un token (para respuesta de inspect)
     */
    public String getInvalidReason(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return null; // válido
        } catch (ExpiredJwtException e) {
            return "TOKEN_EXPIRADO";
        } catch (MalformedJwtException e) {
            return "TOKEN_MALFORMADO";
        } catch (UnsupportedJwtException e) {
            return "TOKEN_NO_SOPORTADO";
        } catch (io.jsonwebtoken.security.SecurityException e) {
            return "FIRMA_INVALIDA";
        } catch (IllegalArgumentException e) {
            return "TOKEN_VACIO";
        }
    }
}
