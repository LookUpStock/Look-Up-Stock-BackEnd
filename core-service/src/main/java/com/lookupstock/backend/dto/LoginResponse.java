package com.lookupstock.backend.dto;

import java.time.Instant;
import com.lookupstock.backend.model.UserRole;

/**
 * Antwort auf POST /auth/login (US-01).
 * role steuert im Frontend nur die Navigation; geprüft wird die Rolle im Backend (E40).
 */
public record LoginResponse(String token, Instant expiresAt, UserRole role) {
}