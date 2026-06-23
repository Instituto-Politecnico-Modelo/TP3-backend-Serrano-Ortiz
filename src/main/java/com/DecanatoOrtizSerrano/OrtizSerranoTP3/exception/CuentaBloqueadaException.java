package com.DecanatoOrtizSerrano.OrtizSerranoTP3.exception;

/**
 * T027 — Excepción lanzada por AuthService cuando la cuenta está bloqueada.
 * Lleva los minutos restantes de bloqueo para que el controller los exponga
 * en el header X-Retry-After (RFC 7231).
 */
public class CuentaBloqueadaException extends RuntimeException {

    private final long minutosRestantes;

    public CuentaBloqueadaException(long minutosRestantes) {
        super("Cuenta bloqueada. Intentá de nuevo en " + minutosRestantes + " minutos.");
        this.minutosRestantes = minutosRestantes;
    }

    public long getMinutosRestantes() {
        return minutosRestantes;
    }
}
