# LookUpStock – Backend

REST-API für LookUpStock: Anlagen suchen, Portfolios verwalten, Monte-Carlo-Simulationen rechnen und speichern, Portfolios und Anlagen vergleichen sowie Nutzer registrieren und anmelden.

## Voraussetzungen

- JDK 25 (laut `pom.xml`; `java -version` zeigt 25, `JAVA_HOME` zeigt auf dieses JDK)
- PostgreSQL (lokal oder erreichbar über `DB_URL`)
- Maven ist nicht nötig: Der mitgelieferte Maven Wrapper (`mvnw` / `mvnw.cmd`) lädt die passende Version selbst.

## Einrichtung

1. In PostgreSQL eine leere Datenbank `lookupstock` anlegen, z. B.:
   ```sql
   CREATE DATABASE lookupstock;
   ```
2. `.env.example` nach `.env` kopieren und die Platzhalter durch eigene Werte ersetzen (mindestens `DB_USERNAME`, `DB_PASSWORD`, `TWELVE_DATA_API_KEY`).
   ```powershell
   Copy-Item .env.example .env
   ```
   Die `.env` wird beim Start aus dem Hauptordner geladen und ist per `.gitignore` vom Commit ausgeschlossen. Echte Umgebungsvariablen haben Vorrang vor der `.env` (z. B. auf Heroku).
3. Backend starten, erreichbar unter `http://localhost:8080`:
   ```powershell
   .\mvnw.cmd spring-boot:run      # Windows
   ```
   ```bash
   ./mvnw spring-boot:run          # Linux/macOS, Git Bash
   ```

## Datenbank

Flyway legt die Tabellen beim Start über die Migrationen in `src/main/resources/db/migration` an:

| Migration | Inhalt |
|---|---|
| `V1__portfolio_tabellen.sql` | Tabellen `asset`, `portfolio`, `position` |
| `V2__asset_mic_code.sql` | Spalte `mic_code` und Eindeutigkeit (Symbol, MIC) für `asset` |
| `V3__nutzer_und_token.sql` | Tabellen `app_user` und `auth_token` |
| `V4__portfolio_nutzer.sql` | Spalte `user_id` in `portfolio` (Besitzer, NF-04) |
| `V5__kursdaten_geladen.sql` | leer (Platzhalter, bereits angewendet, nicht ändern) |
| `V6__kursdaten_cache.sql` | Tabelle `price_history` (Kurs-Cache, NF-07) |
| `V7__simulation_speichern.sql` | Tabelle `simulation` (gespeicherte Simulationen, US-08) |

Hibernate ändert das Schema nicht, sondern prüft nur, ob es zu den Entities passt (`spring.jpa.hibernate.ddl-auto=validate`). Schemaänderungen daher immer als neue Migration `V8__...sql` anlegen, bestehende Migrationen nicht ändern.

Zeitstempel werden in UTC gespeichert (`hibernate.jdbc.time_zone=UTC`) und in der API als ISO-8601 mit `Z` ausgegeben, z. B. `2026-09-30T10:12:00Z`.

## Umgebungsvariablen

Alle Werte werden in `src/main/resources/application.properties` gelesen. Eine Vorlage mit Platzhaltern liegt in `.env.example`.

| Variable | Standard | Zweck |
|---|---|---|
| `PORT` | `8080` | Port des Backends (Heroku setzt ihn selbst) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5500,http://127.0.0.1:5500` | Frontend-Adressen, die die API aufrufen dürfen (kommagetrennt) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/lookupstock` | JDBC-Adresse der PostgreSQL-Datenbank |
| `DB_USERNAME` | `postgres` | PostgreSQL-Benutzer |
| `DB_PASSWORD` | – (Pflicht) | Passwort des PostgreSQL-Benutzers |
| `TWELVE_DATA_BASE_URL` | `https://api.twelvedata.com` | Basisadresse der Kursdaten-API Twelve Data |
| `TWELVE_DATA_API_KEY` | – (Pflicht) | API-Key für Twelve Data. Nur im Backend verwenden, nie im Frontend oder im Repository (NF-06). |

Ohne `TWELVE_DATA_API_KEY` startet die Anwendung nicht. Ohne passendes `DB_PASSWORD` scheitert die Anmeldung an der Datenbank.

## Tests

```powershell
.\mvnw.cmd test        # Windows
./mvnw test            # Linux/macOS, Git Bash
```

- Die Tests rufen die Twelve-Data-API nicht auf. Sie brauchen keinen Internetzugang und keinen echten API-Key (NF-12).
- Ausnahme beim Setup: `BackendApplicationTests` startet den vollständigen Spring-Kontext. Dafür muss eine PostgreSQL-Datenbank laufen (Flyway migriert sie), und `TWELVE_DATA_API_KEY` muss gesetzt sein; ein beliebiger Platzhalter reicht.
- Alle übrigen Tests (Controller mit MockMvc, Services mit Mockito) laufen ohne Datenbank.
- Die CI (`.github/workflows/backend-ci.yml`) startet dafür einen PostgreSQL-Container und setzt einen Dummy-Key.

## Endpunkte

Basis-URL: `/api/v1`. Verbindlich ist das API-Design im Repository `Look-Up-Stock-Docs`; die Tabelle zeigt den aktuellen Stand im Code.

Geschützte Endpunkte erwarten den Header `Authorization: Bearer <token>` (ADR-06). Fehlt er oder ist das Token ungültig oder abgelaufen, antwortet die API mit `401`.

| Methode | Pfad | Beschreibung | Anmeldung |
|---|---|---|---|
| GET | `/api/v1/ping` | Erreichbarkeitstest | nein |
| POST | `/api/v1/auth/register` | Nutzer registrieren (E-Mail, Passwort) | nein |
| POST | `/api/v1/auth/login` | Anmelden, liefert Token und Ablaufzeit (24 h) | nein |
| POST | `/api/v1/auth/logout` | Token ungültig machen, immer `204` | Header optional |
| GET | `/api/v1/assets/search?q=…&limit=…` | Anlagen suchen (`limit` 1–25, Standard 10) | nein |
| GET | `/api/v1/assets/{symbol}/stats` | Kennzahlen einer Anlage | nein |
| GET | `/api/v1/assets/{symbol}/prices` | Kursverlauf einer Anlage | nein |
| POST | `/api/v1/portfolios` | Portfolio anlegen | ja |
| GET | `/api/v1/portfolios` | Eigene Portfolios auflisten | ja |
| GET | `/api/v1/portfolios/{id}` | Ein Portfolio mit Positionen | ja |
| PUT | `/api/v1/portfolios/{id}` | Portfolio ändern (US-06) | ja |
| DELETE | `/api/v1/portfolios/{id}` | Portfolio löschen (US-06) | ja |
| POST | `/api/v1/simulations` | Simulation rechnen, mit `save: true` speichern | ja |
| GET | `/api/v1/simulations` | Gespeicherte Simulationen auflisten (US-08) | ja |
| GET | `/api/v1/simulations/{id}` | Gespeicherte Simulation abrufen | ja |
| DELETE | `/api/v1/simulations/{id}` | Gespeicherte Simulation löschen | ja |
| POST | `/api/v1/comparisons` | Zwei Einträge vergleichen (US-07) | ja |

Außerdem: `GET /api/health` und `GET /actuator/health` liefern den Zustand der Anwendung.

## Projektstruktur

Pakete unter `src/main/java/com/lookupstock/backend/`:

| Paket | Inhalt |
|---|---|
| `config` | Konfiguration: CORS-Regeln, Uhr (`Clock`), Passwort-Hashing (BCrypt) und Registrierung der Anmeldeprüfung (`WebConfig`). |
| `controller` | REST-Controller unter `/api/v1`; `@CurrentUser` und `CurrentUserArgumentResolver` lesen den angemeldeten Nutzer aus dem Header `Authorization`. |
| `service` | Fachlogik, z. B. Registrierung und Anmeldung, Suche, Portfolios, Kurs-Cache, Monte-Carlo-Simulation, gespeicherte Simulationen und Vergleiche. |
| `repository` | Spring-Data-JPA-Repositories für den Datenbankzugriff. |
| `model` | JPA-Entities wie `Portfolio`, `Asset`, `User`, `AuthToken`, inklusive fachlicher Prüfregeln. |
| `dto` | Request- und Response-Objekte der API, einschließlich des einheitlichen Fehlerformats. |
| `exception` | Eigene Exceptions, zentrale Fehlerbehandlung (`GlobalExceptionHandler`) und alle Fehlertexte (`Messages`). |
| `client` | Anbindung an externe Dienste, derzeit die Kursdaten-API Twelve Data. |

## Mitarbeit

Branches, Commits und Pull Requests richten sich nach der `CONTRIBUTING.md` im Repository `Look-Up-Stock-Docs`.
