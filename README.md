# Bundesliga Data Aggregator

Ein modularer, konfigurierbarer Datenaggregator in Kotlin, der automatisch Bundesliga-Daten von OpenLigaDB abruft, in einer lokalen DuckDB-Datenbank speichert und über Discord-Webhooks ausgibt.

## Architektur

Das Projekt folgt der **Hexagonalen Architektur (Ports & Adapters)**:

- `domain/` – reine Kotlin Data Classes und Interfaces (keine Framework-Abhängigkeiten)
- `adapter/` – implementieren die Ports und verbinden die Domain mit der Außenwelt
- `adapter/config/` – verdrahtet alles via Koin Dependency Injection

### Zwei vollständig getrennte Pipelines

```
INGESTION:  Scheduler → API-Adapter → Domain Service → DuckDB
OUTPUT:     Trigger → Repository → Query Service → Discord
```

## Tech Stack

| Komponente | Technologie | Version |
|---|---|---|
| Sprache | Kotlin | 2.1.0 |
| JVM | Java | 21 |
| Build | Gradle Kotlin DSL | – |
| Datenbank | DuckDB (JDBC) | 1.5.1.0 |
| HTTP Client | Ktor Client (CIO) | 3.4.2 |
| JSON | kotlinx.serialization | 1.7.0 |
| Scheduling | Kotlin Coroutines | 1.9.0 |
| DI | Koin | 4.1.1 |
| Discord | discord-webhooks | 0.8.4 |
| Env-Variablen | dotenv-kotlin | 6.5.1 |
| HTML-Parsing | Jsoup | 1.22.1 |
| KI (optional) | Anthropic Java SDK | 2.19.0 |
| Logging | Logback | 1.5.6 |

## Projektstruktur

```
bundesliga-aggregator/
├── .env                          # Secrets (gitignored)
├── .env.example                  # Vorlage
├── config.yaml                   # Modulkonfiguration
├── build.gradle.kts
├── settings.gradle.kts
├── data/
│   └── bundesliga.duckdb         # Datenbank (gitignored, portabel)
└── src/main/kotlin/de/aggregator/
    ├── Application.kt
    ├── domain/
    │   ├── model/                # Match, Goal, Team, Standing
    │   ├── port/
    │   │   ├── input/            # FetchDataUseCase, QueryDataUseCase
    │   │   └── output/           # MatchRepository, FootballApiPort, NotificationPort
    │   └── service/              # IngestionService, QueryService
    └── adapter/
        ├── input/scheduler/      # IngestionScheduler
        ├── output/
        │   ├── api/              # OpenLigaDbClient
        │   ├── persistence/      # DuckDbRepository
        │   └── discord/          # DiscordSender
        └── config/               # AppModule, ConfigLoader, DatabaseConfig
```

## Setup

### 1. Repository klonen

```bash
git clone https://github.com/timturowski36/feedkrake
cd feedkrake
```

### 2. Secrets konfigurieren

```bash
cp .env.example .env
```

`.env` mit eigenen Werten befüllen:

```env
DISCORD_SPORT_WEBHOOK=https://discord.com/api/webhooks/...
DISCORD_NEWS_WEBHOOK=https://discord.com/api/webhooks/...
```

### 3. Starten

```bash
./gradlew run
```

### Danach läuft alles automatisch

- Datenbank wird alle 15 Minuten aktualisiert (konfigurierbar in `config.yaml`)
- Discord-Nachrichten werden nach den konfigurierten Schedules gesendet
- Keine weitere Interaktion nötig

## Konfiguration

Module werden in `config.yaml` verwaltet:

```yaml
modules:
  - id: bundesliga_1
    type: football
    enabled: true          # auf false setzen zum Deaktivieren
    source: openligadb
    config:
      league: bl1
      season: 2024
    schedule:
      fetchIntervalMinutes: 15
    outputs:
      - type: discord
        channel: sport
        schedule: DAILY_18_00
        format: table_summary
```

### Output-Schedules

| Schlüssel | Beschreibung |
|---|---|
| `DAILY_HH_MM` | Täglich zur angegebenen Uhrzeit |
| `WEEKLY_WEEKDAY_HH_MM` | Wöchentlich am angegebenen Tag |

### Output-Formate

| Format | Beschreibung |
|---|---|
| `table_summary` | Tägliche Tabellenübersicht |
| `matchday_results` | Spieltagsergebnisse mit Torschützen |

## Portabilität

Die `data/bundesliga.duckdb` kann auf einen anderen PC kopiert werden. Nach erneutem `./gradlew run` wird der Betrieb mit dem vorhandenen Datenstand fortgesetzt.

## Datenquelle

[OpenLigaDB](https://api.openligadb.de) – kostenlos, kein API-Key erforderlich.

## Nicht im Scope (Phase 1)

- Web-Frontend oder REST-API
- Cloud-Deployment
- Andere Sportarten
- KI-gestützte Zusammenfassungen
- Google Sheets Integration
