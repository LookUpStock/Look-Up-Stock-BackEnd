package com.mosbach.demo.data.api;

public interface UserManager {

    String logUserOn(String email, String password);
    String getUserFromToken(String token);
    boolean logUserOff(String email);

}
