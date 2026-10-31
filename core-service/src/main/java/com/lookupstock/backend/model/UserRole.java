package com.lookupstock.backend.model;

/**
 * Rolle eines Nutzerkontos (E40).
 * Die Rolle wird nicht bei der Registrierung gewählt, sondern vom Team in der Datenbank vergeben.
 */
public enum UserRole {

    /** Normales Nutzerkonto, Standard bei der Registrierung */
    USER,

    /** Geschäftsführung, darf Auswertungen sehen und exportieren (US-11, US-12) */
    MANAGER
}