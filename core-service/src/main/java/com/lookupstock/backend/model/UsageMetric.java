package com.lookupstock.backend.model;

/**
 * Die neun Nutzungskennzahlen je Monat in fester Reihenfolge (E39, API-Design „Auswertungen“).
 * key und label erscheinen unverändert im Dashboard und in der CSV-Datei (US-12).
 */
public enum UsageMetric {

    REGISTRATIONS("registrations", "Neue Registrierungen", UsageEventType.REGISTRATION),
    LOGINS("logins", "Anmeldungen", UsageEventType.LOGIN),
    PORTFOLIOS_CREATED("portfoliosCreated", "Angelegte Portfolios", UsageEventType.PORTFOLIO_CREATED),
    SIMULATIONS_RUN("simulationsRun", "Durchgeführte Simulationen", UsageEventType.SIMULATION_RUN),
    SIMULATIONS_SAVED("simulationsSaved", "Gespeicherte Simulationen", UsageEventType.SIMULATION_SAVED),
    COMPARISONS_RUN("comparisonsRun", "Durchgeführte Vergleiche", UsageEventType.COMPARISON_RUN),
    DEMO_SIMULATIONS_RUN("demoSimulationsRun", "Beispielsimulationen (Gäste)", UsageEventType.DEMO_SIMULATION_RUN),
    ASSET_SEARCHES("assetSearches", "Anlagensuchen", UsageEventType.ASSET_SEARCH),
    // Kein Ereignis, sondern Zählung der Nutzerkonten (ohne Personenbezug, E36)
    TOTAL_ACCOUNTS("totalAccounts", "Nutzerkonten insgesamt am Monatsende", null);

    private final String key;
    private final String label;
    private final UsageEventType eventType;

    UsageMetric(String key, String label, UsageEventType eventType) {
        this.key = key;
        this.label = label;
        this.eventType = eventType;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }

    /** Zugehörige Ereignisart; null bei TOTAL_ACCOUNTS */
    public UsageEventType getEventType() { return eventType; }
}
