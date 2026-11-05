package com.lookupstock.backend.dto;

import java.time.Instant;
import com.lookupstock.backend.model.UserRole;

public record LoginResponse(String token, Instant expiresAt, UserRole role) {
}