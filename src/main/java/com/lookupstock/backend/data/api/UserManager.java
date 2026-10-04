package com.lookupstock.backend.data.api;

public interface UserManager {

    String registerUser(String email, String password);
    String logUserOn(String email, String password);
    String getUserFromToken(String token);
    boolean logUserOff(String email);

}
