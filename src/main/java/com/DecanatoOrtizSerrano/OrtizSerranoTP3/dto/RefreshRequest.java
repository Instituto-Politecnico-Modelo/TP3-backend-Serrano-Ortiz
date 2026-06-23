package com.DecanatoOrtizSerrano.OrtizSerranoTP3.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para POST /api/auth/refresh — renueva el access token usando el refresh token.
 */
public class RefreshRequest {

    @NotBlank(message = "refreshToken es requerido")
    private String refreshToken;

    public RefreshRequest() {}

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
