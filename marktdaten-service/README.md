# LookUpStock – marktdaten-service

Kapselt die Anbindung an die Kursdaten-API Twelve Data (ADR-07): Anlagensuche mit Such-Cache (24 h, NF-02) und historische Kurse mit Kurs-Cache in PostgreSQL (24 h, NF-07). Der Dienst wird nur vom core-service aufgerufen, nie direkt vom Frontend. Der API-Key von Twelve Data liegt nur hier.

## Voraussetzungen

- JDK 25; Maven ist nicht nötig, der Maven Wrapper (`mvnw` / `mvnw.cmd`) lädt die passende Version.
- Dieselbe PostgreSQL-Datenbank wie der core-service. Flyway legt beim Start das Schema `marktdaten` mit eigener Migrationshistorie an; Tabellen des core-service werden nicht gelesen.

## Einrichtung

1. Im Ordner `marktdaten-service` die `.env.example` nach `.env` kopieren und eigene Werte eintragen (`DB_PASSWORD`, `TWELVE_DATA_API_KEY`, `INTERNAL_API_KEY`).
2. Starten, erreichbar unter `http://localhost:8081`:
   ```powershell
   .\mvnw.cmd spring-boot:run      # Windows
   ```
   ```bash
   ./mvnw spring-boot:run          # Linux/macOS, Git Bash
   ```
3. Im core-service `MARKTDATEN_URL=http://localhost:8081` und denselben `INTERNAL_API_KEY` setzen.

## Umgebungsvariablen

| Variable | Standard | Zweck |
|---|---|---|
| `PORT` | `8081` | Port (Heroku setzt ihn selbst) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | wie core-service | PostgreSQL; auf Heroku über `JDBC_DATABASE_URL` der gemeinsamen Datenbank |
| `DB_POOL_SIZE` | `5` | Höchstzahl der Datenbankverbindungen |
| `TWELVE_DATA_BASE_URL` | `https://api.twelvedata.com` | Basisadresse von Twelve Data |
| `TWELVE_DATA_API_KEY` | – (Pflicht) | API-Key für Twelve Data, nie im Repository (NF-06) |
| `QUEUE_PRELOAD_INTERVAL_MS` | `15000` | Abstand der Durchläufe für `kursdaten.vorladen` |
| `INTERNAL_API_KEY` | – (Pflicht) | Gemeinsamer Schlüssel mit dem core-service. Ohne Wert werden alle Anfragen an `/internal` mit `401` abgewiesen. |

## Endpunkte

Alle Endpunkte unter `/internal` verlangen den Header `X-Internal-Api-Key`; fehlt er oder passt er nicht, folgt `401 UNAUTHORIZED`.

| Methode | Pfad | Beschreibung |
|---|---|---|
| GET | `/internal/search?q=…` | Suchtreffer in der Reihenfolge von Twelve Data; 24 h im Arbeitsspeicher zwischengespeichert |
| GET | `/internal/prices?symbol=…&mic=…&interval=1month&outputsize=120` | Kurse aufsteigend nach Datum. Monatswerte kommen 24 h aus der Tabelle `marktdaten.price_cache`; andere Intervalle (z. B. `1day`) werden durchgereicht |
| GET | `/actuator/health` | Zustand der Anwendung, ohne Schlüssel |

## Asynchrone Verarbeitung: kursdaten.vorladen

Legt ein Nutzer ein Portfolio an oder ändert es, stellt der core-service eine Nachricht mit Symbol und MIC-Code der Anlagen in die Queue-Tabelle `public.queue_message` (Thema `kursdaten.vorladen`, ADR-07). Der `PricePreloadConsumer` holt alle 15 Sekunden höchstens 3 Nachrichten ab (`FOR UPDATE SKIP LOCKED`) und lädt fehlende oder veraltete Monatswerte in den Kurs-Cache. Die erste Simulation findet die Kurse dann meist schon im Cache. Schlägt das Laden fehl, wird die Nachricht bis zu dreimal erneut versucht und danach als fehlgeschlagen markiert. Der Abstand ist mit `QUEUE_PRELOAD_INTERVAL_MS` einstellbar.

Ist Twelve Data nicht erreichbar oder das Kontingent erschöpft, antwortet der Dienst mit `503 UPSTREAM_UNAVAILABLE`; der core-service gibt das wie bisher als `503` weiter. Unbekannte Symbole oder Börsen außerhalb des Tarifs liefern eine leere Liste.

## Tests

```powershell
.\mvnw.cmd test        # Windows
./mvnw test            # Linux/macOS, Git Bash
```

Die Tests brauchen weder Datenbank noch Internet (NF-12): `InternalControllerTest` prüft Endpunkte und Schlüssel mit MockMvc, `PriceServiceTest` und `SearchServiceTest` die Caches mit Mockito. Die CI (`.github/workflows/marktdaten-ci.yml`) läuft nur bei Änderungen in `marktdaten-service/`.

## Projektstruktur

| Paket unter `com.lookupstock.marktdaten` | Inhalt |
|---|---|
| `twelvedata` | `MarketDataProvider` und `TwelveDataProvider` mit Fehlerzuordnung (`TwelveDataError`) |
| `service` | `SearchService` mit `SearchCache`, `PriceService` mit `PriceCacheRepository` (JdbcTemplate) |
| `web` | `InternalController`, `InternalApiKeyFilter`, `ErrorHandler` |
| `queue` | `PricePreloadConsumer` und `QueueRepository` für das Thema `kursdaten.vorladen` |
| `model`, `exception`, `config` | `SearchHit`, `PricePoint`, `UpstreamUnavailableException`, Uhr |
