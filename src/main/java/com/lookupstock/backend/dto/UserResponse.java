package com.lookupstock.backend.dto;

import com.lookupstock.backend.model.User;

public record UserResponse(Long id, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}