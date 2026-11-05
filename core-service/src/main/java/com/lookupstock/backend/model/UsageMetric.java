package com.lookupstock.backend.model;

public enum UsageMetric {

    REGISTRATIONS("registrations", "Neue Registrierungen", UsageEventType.REGISTRATION),
    LOGINS("logins", "Anmeldungen", UsageEventType.LOGIN),
    PORTFOLIOS_CREATED("portfoliosCreated", "Angelegte Portfolios", UsageEventType.PORTFOLIO_CREATED),
    SIMULATIONS_RUN("simulationsRun", "Durchgeführte Simulationen", UsageEventType.SIMULATION_RUN),
    SIMULATIONS_SAVED("simulationsSaved", "Gespeicherte Simulationen", UsageEventType.SIMULATION_SAVED),
    COMPARISONS_RUN("comparisonsRun", "Durchgeführte Vergleiche", UsageEventType.COMPARISON_RUN),
    DEMO_SIMULATIONS_RUN("demoSimulationsRun", "Beispielsimulationen (Gäste)", UsageEventType.DEMO_SIMULATION_RUN),
    ASSET_SEARCHES("assetSearches", "Anlagensuchen", UsageEventType.ASSET_SEARCH),
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

    public UsageEventType getEventType() { return eventType; }
}
