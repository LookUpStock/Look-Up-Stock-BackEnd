package com.lookupstock.backend.data.impl;

import com.lookupstock.backend.data.api.UserManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;

public class PropertyFileUserManagerImpl implements UserManager {

    private final String filePath;

    public PropertyFileUserManagerImpl(String filePath) {
        this.filePath = filePath;
        ensureFileExists();
    }

    public static PropertyFileUserManagerImpl getPropertyFileUserManagerImpl(String filePath) {
        return new PropertyFileUserManagerImpl(filePath);
    }

    private void ensureFileExists() {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                Properties properties = new Properties();
                properties.store(outputStream, "Users");
            } catch (IOException ignored) {
                // ignored intentionally; the caller handles the resulting error state
            }
        }
    }

    private Properties loadProperties() {
        try (FileInputStream inputStream = new FileInputStream(filePath)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean saveProperties(Properties properties) {
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            properties.store(outputStream, "Users");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String registerUser(String email, String password) {
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            return "NO-FILE-ACCESS";
        }

        Properties properties = loadProperties();
        if (properties == null) {
            return "NO-FILE-ACCESS";
        }

        String normalizedEmail = email.trim();
        if (properties.containsKey(normalizedEmail + ".password")) {
            return "USER-EXISTS";
        }

        String token = UUID.randomUUID().toString();
        properties.setProperty(normalizedEmail + ".password", password);
        properties.setProperty(normalizedEmail + ".token", token);
        properties.setProperty(normalizedEmail + ".loggedIn", "true");

        if (!saveProperties(properties)) {
            return "NO-FILE-ACCESS";
        }

        return token;
    }

    @Override
    public String logUserOn(String email, String password) {
        if (email == null || password == null || email.isBlank() || password.isBlank()) {
            return "NOT-FOUND";
        }

        Properties properties = loadProperties();
        if (properties == null) {
            return "NO-FILE-ACCESS";
        }

        String normalizedEmail = email.trim();
        String savedPassword = properties.getProperty(normalizedEmail + ".password");
        if (savedPassword == null || !savedPassword.equals(password)) {
            return "NOT-FOUND";
        }

        String token = UUID.randomUUID().toString();
        properties.setProperty(normalizedEmail + ".token", token);
        properties.setProperty(normalizedEmail + ".loggedIn", "true");

        if (!saveProperties(properties)) {
            return "NO-FILE-ACCESS";
        }

        return token;
    }

    @Override
    public String getUserFromToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }

        Properties properties = loadProperties();
        if (properties == null) {
            return "";
        }

        for (String key : properties.stringPropertyNames()) {
            if (key.endsWith(".token") && token.equals(properties.getProperty(key))) {
                return key.substring(0, key.length() - ".token".length());
            }
        }

        return "";
    }

    @Override
    public boolean logUserOff(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        Properties properties = loadProperties();
        if (properties == null) {
            return false;
        }

        String normalizedEmail = email.trim();
        if (!properties.containsKey(normalizedEmail + ".password")) {
            return false;
        }

        properties.remove(normalizedEmail + ".token");
        properties.setProperty(normalizedEmail + ".loggedIn", "false");

        return saveProperties(properties);
    }
}
