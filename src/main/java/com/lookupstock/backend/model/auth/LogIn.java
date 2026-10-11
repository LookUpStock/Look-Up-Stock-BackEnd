package com.lookupstock.backend.model.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LogIn {
    @NotBlank(message = "E-Mail ist erforderlich.")
    @Email(message = "E-Mail muss gültig sein.")
    private String email;

    @NotBlank(message = "Passwort ist erforderlich.")
    @Size(min = 6, message = "Passwort muss mindestens 6 Zeichen lang sein.")
    private String password;

    public LogIn() {
    }

    public LogIn(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
