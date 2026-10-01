# LookUpStock – Backend

REST-API für LookUpStock: Anlagen suchen, Portfolios verwalten und mögliche Kursverläufe per Monte-Carlo-Simulation berechnen.

## Tech-Stack

- Java 25
- Spring Boot (Version siehe `pom.xml`)
- Maven (über den mitgelieferten Maven Wrapper, keine Installation nötig)

## Voraussetzungen

- JDK 25 (`java -version` zeigt 25)
- Umgebungsvariable `JAVA_HOME` zeigt auf das JDK

## Starten, Testen, Bauen

Alle Befehle im Hauptordner dieses Repos ausführen (Windows PowerShell):

```powershell
.\mvnw.cmd spring-boot:run   # App starten, erreichbar unter http://localhost:8080
.\mvnw.cmd test              # Tests ausführen
.\mvnw.cmd package           # ausführbare .jar in target/ bauen
```

Unter Linux/macOS bzw. Git Bash: `./mvnw` statt `.\mvnw.cmd`.

## Endpunkte

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/v1/ping` | Erreichbarkeitstest, Antwort `{"status":"ok"}` |
| GET | `/actuator/health` | Zustand der App, Antwort `{"status":"UP"}` |

Die fachlichen Endpunkte (Assets, Portfolios, Simulationen) sind im API-Design im Repository `Look-Up-Stock-Docs` beschrieben. Basis-URL: `/api/v1`.

## Konfiguration

Werte stehen in `src/main/resources/application.properties` und lassen sich über Umgebungsvariablen überschreiben. Eine Vorlage liegt in `.env.example`.

| Variable | Standard | Bedeutung |
|---|---|---|
| `PORT` | `8080` | Port des Backends |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5500,http://127.0.0.1:5500` | Frontend-Adressen, die die API aufrufen dürfen |

Beispiel in PowerShell:

```powershell
$env:PORT = "9090"
.\mvnw.cmd spring-boot:run
```

Zugangsdaten (z. B. API-Keys) niemals committen, sondern nur als Umgebungsvariable setzen.

## Projektstruktur

```
src/main/java/com/lookupstock/backend/
├── BackendApplication.java   Startpunkt der App
├── config/CorsConfig.java    CORS-Regeln für das Frontend
└── controller/               REST-Controller (/api/v1/...)
src/main/resources/
└── application.properties    Einstellungen
src/test/java/...             Tests (JUnit 5, MockMvc)
```

## Mitarbeit

Branches, Commits und Pull Requests nach der `CONTRIBUTING.md` im Repository `Look-Up-Stock-Docs`.