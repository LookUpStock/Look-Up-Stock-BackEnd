# LookUpStock – Backend

Das Backend besteht aus zwei Diensten (ADR-07):

| Dienst | Ordner | Aufgabe | Heroku |
|---|---|---|---|
| core-service | [`core-service/`](core-service/README.md) | REST- und GraphQL-API für das Frontend: Anmeldung, Portfolios, Simulation, Vergleich, gespeicherte Simulationen, Beispielsimulation, Auswertungen | `lookupstock` |
| marktdaten-service | [`marktdaten-service/`](marktdaten-service/README.md) | Anbindung an Twelve Data mit Such- und Kurs-Cache, nur für den core-service | `lookupstock-marktdaten` |

Der core-service ruft den marktdaten-service für Suche und Kurse synchron über HTTP auf (begründete Ausnahme in ADR-07), geschützt mit dem Header `X-Internal-Api-Key`. Asynchron laufen über die Queue-Tabelle `queue_message` die Nutzungsereignisse (`nutzung.ereignis`, innerhalb des core-service) und das Vorladen der Kurse (`kursdaten.vorladen`, vom core-service zum marktdaten-service). Beide Dienste nutzen dieselbe PostgreSQL-Datenbank mit getrennten Schemas (`public` und `marktdaten`) und haben eigene Flyway-Migrationen, eigene CI-Workflows mit Pfadfiltern und eigene Heroku-Apps (Monorepo-Buildpack mit `APP_BASE`).

## Lokal starten

1. PostgreSQL mit Datenbank `lookupstock` bereitstellen.
2. In beiden Ordnern `.env.example` nach `.env` kopieren und Werte eintragen; `INTERNAL_API_KEY` muss in beiden gleich sein.
3. Zuerst den marktdaten-service (Port 8081), dann den core-service (Port 8080) starten, jeweils im eigenen Ordner mit `./mvnw spring-boot:run`.

## Deployment (Heroku)

Beide Apps sind mit diesem Repository verbunden und deployen automatisch von `main`. Buildpacks je App: zuerst `https://github.com/lstoll/heroku-buildpack-monorepo`, dann `heroku/java`.

| App | `APP_BASE` | weitere Konfigurationsvariablen |
|---|---|---|
| `lookupstock` | `core-service` | `MARKTDATEN_URL`, `INTERNAL_API_KEY` |
| `lookupstock-marktdaten` | `marktdaten-service` | `TWELVE_DATA_API_KEY`, `INTERNAL_API_KEY` |

Die Datenbank ist an beide Apps angehängt (`heroku addons:attach`). Schlüssel stehen nur in den Konfigurationsvariablen, nie im Repository.

## Mitarbeit

Branches, Commits und Pull Requests richten sich nach der `CONTRIBUTING.md` im Repository `Look-Up-Stock-Docs`.
