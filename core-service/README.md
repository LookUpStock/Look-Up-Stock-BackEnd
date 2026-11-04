# LookUpStock – core-service

REST- und GraphQL-API für LookUpStock: Anlagen suchen, Portfolios verwalten, Monte-Carlo-Simulationen rechnen und speichern, Portfolios und Anlagen vergleichen sowie Nutzer registrieren und anmelden.

Der core-service ist der einzige Ansprechpartner des Frontends. Suche und Kursdaten holt er synchron beim [marktdaten-service](../marktdaten-service/README.md), der Twelve Data anbindet und die Caches hält (ADR-07).

## Voraussetzungen

- JDK 25 (laut `pom.xml`; `java -version` zeigt 25, `JAVA_HOME` zeigt auf dieses JDK)
- PostgreSQL (lokal oder erreichbar über `DB_URL`)
- Für Suche und Simulation: laufender marktdaten-service (lokal auf Port 8081)
- Maven ist nicht nötig: Der mitgelieferte Maven Wrapper (`mvnw` / `mvnw.cmd`) lädt die passende Version selbst.

## Einrichtung

1. In PostgreSQL eine leere Datenbank `lookupstock` anlegen, z. B.:
   ```sql
   CREATE DATABASE lookupstock;
   ```
2. Im Ordner `core-service` die `.env.example` nach `.env` kopieren und die Platzhalter durch eigene Werte ersetzen (mindestens `DB_USERNAME`, `DB_PASSWORD`, `INTERNAL_API_KEY`).
   ```powershell
   Copy-Item .env.example .env
   ```
   Die `.env` wird beim Start aus dem Ordner `core-service` geladen und ist per `.gitignore` vom Commit ausgeschlossen. Echte Umgebungsvariablen haben Vorrang vor der `.env` (z. B. auf Heroku).
3. marktdaten-service starten (siehe dessen README), dann im Ordner `core-service` den core-service starten, erreichbar unter `http://localhost:8080`:
   ```powershell
   .\mvnw.cmd spring-boot:run      # Windows
   ```
   ```bash
   ./mvnw spring-boot:run          # Linux/macOS, Git Bash
   ```

## Datenbank

Flyway legt die Tabellen beim Start über die Migrationen in `core-service/src/main/resources/db/migration` an:

| Migration | Inhalt |
|---|---|
| `V1__portfolio_tabellen.sql` | Tabellen `asset`, `portfolio`, `position` |
| `V2__asset_mic_code.sql` | Spalte `mic_code` und Eindeutigkeit (Symbol, MIC) für `asset` |
| `V3__nutzer_und_token.sql` | Tabellen `app_user` und `auth_token` |
| `V4__portfolio_nutzer.sql` | Spalte `user_id` in `portfolio` (Besitzer, NF-04) |
| `V5__kursdaten_geladen.sql` | leer (Platzhalter, bereits angewendet, nicht ändern) |
| `V6__kursdaten_cache.sql` | Tabelle `price_history` (früherer Kurs-Cache; seit BE #106 ungenutzt, wird mit V11 entfernt) |
| `V7__simulation_speichern.sql` | Tabelle `simulation` (gespeicherte Simulationen, US-08) |
| `V8__asset_mic_code_verlaengern.sql` | Spalte `asset.mic_code` auf 50 Zeichen verlängert |

Der Kurs-Cache liegt seit BE #106 im Schema `marktdaten` des marktdaten-service (eigene Flyway-Historie).

Hibernate ändert das Schema nicht, sondern prüft nur, ob es zu den Entities passt (`spring.jpa.hibernate.ddl-auto=validate`). Schemaänderungen daher immer als neue Migration mit der nächsten freien Nummer anlegen, bestehende Migrationen nicht ändern.

Zeitstempel werden in UTC gespeichert (`hibernate.jdbc.time_zone=UTC`) und in der API als ISO-8601 mit `Z` ausgegeben, z. B. `2026-09-30T10:12:00Z`.

## Umgebungsvariablen

Alle Werte werden in `core-service/src/main/resources/application.properties` gelesen. Eine Vorlage mit Platzhaltern liegt in `.env.example`.

| Variable | Standard | Zweck |
|---|---|---|
| `PORT` | `8080` | Port des Backends (Heroku setzt ihn selbst) |
| `APP_CORS_ALLOWED_ORIGINS` | lokale Ports und die Heroku-Adresse des Frontends | Frontend-Adressen, die die API aufrufen dürfen (kommagetrennt) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/lookupstock` | JDBC-Adresse der PostgreSQL-Datenbank |
| `DB_USERNAME` | `postgres` | PostgreSQL-Benutzer |
| `DB_PASSWORD` | – (Pflicht) | Passwort des PostgreSQL-Benutzers |
| `DB_POOL_SIZE` | `5` | Höchstzahl der Datenbankverbindungen; beide Apps teilen sich die Heroku-Datenbank |
| `MARKTDATEN_URL` | `http://localhost:8081` | Basisadresse des marktdaten-service |
| `INTERNAL_API_KEY` | – (Pflicht für Suche und Simulation) | Gemeinsamer Schlüssel mit dem marktdaten-service (Header `X-Internal-Api-Key`). Auf beiden Seiten gleich, nie im Repository. |

Der API-Key von Twelve Data liegt seit BE #106 nur noch beim marktdaten-service. Ohne passendes `DB_PASSWORD` scheitert die Anmeldung an der Datenbank. Ist der marktdaten-service nicht erreichbar oder passt `INTERNAL_API_KEY` nicht, antworten Suche und Simulation mit `503`.

## Tests

```powershell
.\mvnw.cmd test        # Windows
./mvnw test            # Linux/macOS, Git Bash
```

- Die Tests rufen weder Twelve Data noch den marktdaten-service auf. Sie brauchen keinen Internetzugang und keinen echten Schlüssel (NF-12); Kursdaten kommen aus dem `MockMarketDataProvider`, der `MarktdatenClient` wird mit `MockRestServiceServer` geprüft.
- Controller laufen mit MockMvc bzw. `@GraphQlTest`, Services mit Mockito, alle ohne Datenbank.
- Die CI (`.github/workflows/backend-ci.yml`) läuft nur bei Änderungen in `core-service/` und prüft die Abdeckung der Kernlogik (mindestens 80 %, NF-11).

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
| GET | `/api/v1/demo/portfolios` | Beispielportfolios für Gäste (US-09) | nein |
| POST | `/api/v1/demo/simulations` | Beispielsimulation ohne Speichern, höchstens 10.000 Pfade (US-09) | nein |

Außerdem: `GET /api/health` und `GET /actuator/health` liefern den Zustand der Anwendung.

### GraphQL

`POST /api/v1/graphql` mit derselben Anmeldung wie bei REST (`Authorization: Bearer <token>`). Lokal zeigt `GRAPHIQL_ENABLED=true` eine Oberfläche unter `/graphiql`.

| Art | Operation | entspricht |
|---|---|---|
| Query | `searchAssets(query, limit)` | `GET /assets/search` |
| Query | `portfolios`, `portfolio(id)` | `GET /portfolios`, `GET /portfolios/{id}` |
| Query | `savedSimulations`, `savedSimulation(id)` | `GET /simulations`, `GET /simulations/{id}` |
| Mutation | `createPortfolio(input)`, `updatePortfolio(id, input)`, `deletePortfolio(id)` | `POST`, `PUT`, `DELETE /portfolios` |
| Mutation | `simulate(input)`, `deleteSavedSimulation(id)` | `POST /simulations`, `DELETE /simulations/{id}` |
| Mutation | `compare(input)` | `POST /comparisons` |

Fehler haben dieselbe Meldung wie bei REST, der Code steht in `extensions.code` (z. B. `VALIDATION_ERROR`, `INSUFFICIENT_DATA`), Details in `extensions.details`. Das Schema liegt in `src/main/resources/graphql/schema.graphqls`.

## Projektstruktur

Pakete unter `core-service/src/main/java/com/lookupstock/backend/`:

| Paket | Inhalt |
|---|---|
| `config` | Konfiguration: CORS-Regeln, Uhr (`Clock`), Passwort-Hashing (BCrypt) und Registrierung der Anmeldeprüfung (`WebConfig`). |
| `controller` | REST-Controller unter `/api/v1`; `@CurrentUser` und `CurrentUserArgumentResolver` lesen den angemeldeten Nutzer aus dem Header `Authorization`. |
| `service` | Fachlogik, z. B. Registrierung und Anmeldung, Suche, Portfolios, Monatswerte für die Schätzung, Monte-Carlo-Simulation, gespeicherte Simulationen und Vergleiche. |
| `repository` | Spring-Data-JPA-Repositories für den Datenbankzugriff. |
| `model` | JPA-Entities wie `Portfolio`, `Asset`, `User`, `AuthToken`, inklusive fachlicher Prüfregeln. |
| `dto` | Request- und Response-Objekte der API, einschließlich des einheitlichen Fehlerformats. |
| `exception` | Eigene Exceptions, zentrale Fehlerbehandlung (`GlobalExceptionHandler`) und alle Fehlertexte (`Messages`). |
| `client` | Schnittstelle `MarketDataProvider` und `MarktdatenClient` für den marktdaten-service (ADR-07). |
| `graphql` | GraphQL-Controller für Abfragen und Mutations, Auswertung des Tokens und Übersetzung der Fehler. |

## Mitarbeit

Branches, Commits und Pull Requests richten sich nach der `CONTRIBUTING.md` im Repository `Look-Up-Stock-Docs`.
