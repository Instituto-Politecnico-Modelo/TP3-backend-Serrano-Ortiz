package com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.security.UserDetailsImpl;
import com.DecanatoOrtizSerrano.OrtizSerranoTP3.service.TokenBlocklistService;
import java.io.IOException;
import java.util.List;

/**
 * Filtro que intercepta las peticiones HTTP y valida el token JWT
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    @Autowired
    private JwtUtil jwtUtil;

    /** CHK023f — Consulta la blocklist para tokens revocados por logout. */
    @Autowired(required = false)
    private TokenBlocklistService tokenBlocklistService;

    // ⚠️  T008: NO se inyecta UserDetailsService — la validación es puramente stateless.
    // La identidad y el rol se leen directamente del JWT; cero queries a MySQL por request.
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            
            if (jwt != null && jwtUtil.validateJwtToken(jwt)) {
                // CHK023f — Rechazar tokens revocados por logout
                if (tokenBlocklistService != null && tokenBlocklistService.estaRevocado(jwt)) {
                    logger.debug("Token revocado (blocklist) — rechazado");
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtUtil.getUsernameFromJwtToken(jwt);
                String rol      = jwtUtil.getRolFromToken(jwt);
                Long   userId   = jwtUtil.getIdFromToken(jwt);

                // Construir autoridades desde el claim "rol" del JWT — sin tocar la BD.
                List<SimpleGrantedAuthority> authorities = rol != null
                        ? List.of(new SimpleGrantedAuthority(rol))
                        : List.of();

                // Construir UserDetailsImpl para que los controllers puedan hacer cast correctamente
                UserDetailsImpl userDetails = new UserDetailsImpl(
                        userId != null ? userId : 0L,
                        username, username, null, authorities);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("No se puede establecer la autenticación del usuario: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Extrae el token JWT del header Authorization
     */
    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        
        return null;
    }
}
