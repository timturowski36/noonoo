# FeedKrake

**FeedKrake** ist ein modularer, konfigurierbarer Datenaggregator in Kotlin. Er sammelt automatisch Daten aus verschiedenen Quellen (Sportdaten, News, Wetter, Finanzen u.v.m.), speichert sie lokal in einer DuckDB-Datenbank und liefert aufbereitete Zusammenfassungen über konfigurierbare Ausgabekanäle – um den Alltag und persönliche Planungsprozesse zu unterstützen.

**Phase 1:** Bundesliga-Daten via OpenLigaDB → Discord

---

## Idee

Viele Informationen, die täglich relevant sind – Spieltermine, Tabellen, Nachrichten, Wetter, Finanzdaten – sind über verschiedene Quellen verstreut. FeedKrake bündelt diese Daten automatisch, bereitet sie auf und liefert sie genau dann, wenn man sie braucht: als tägliche Discord-Zusammenfassung, wöchentlicher Überblick oder direkt nach einem Ereignis.

Langfristig soll FeedKrake als persönlicher Planungsassistent fungieren: strukturierte Daten im richtigen Format, zur richtigen Zeit, auf dem richtigen Kanal.

---

## Architektur

FeedKrake folgt der **Hexagonalen Architektur (Ports & Adapters)**. Dadurch kann jede Datenquelle und jeder Ausgabekanal unabhängig ausgetauscht oder erweitert werden, ohne die Kernlogik zu berühren.

```
domain/          – reine Kotlin Data Classes und Interfaces (keine Framework-Abhängigkeiten)
adapter/         – verbinden die Domain mit der Außenwelt (APIs, Datenbank, Discord, ...)
adapter/config/  – verdrahtet alles via Koin Dependency Injection
```

### Zwei vollständig getrennte Pipelines

```
INGESTION:  Scheduler → API-Adapter → Domain Service → DuckDB
OUTPUT:     Trigger   → Repository  → Query Service  → Ausgabe-Adapter
```

Die Datenbank wird kontinuierlich im Hintergrund aktuell gehalten. Ausgaben werden unabhängig davon nach eigenem Schedule ausgelöst.

---

## Module (Roadmap)

| Phase | Modul | Quelle | Status |
|---|---|---|---|
| 1 | Bundesliga 1 (Tabelle, Ergebnisse) | OpenLigaDB | In Entwicklung |
| 2 | Bundesliga 2, Champions League | OpenLigaDB | Geplant |
| 2 | E-Mail-Ausgabe | SMTP | Geplant |
| 3 | News (Heise, Tagesschau) | RSS | Geplant |
| 3 | Wetterdaten | Wetter-API | Geplant |
| 3 | Finanzdaten | Finance-API | Geplant |
| 4 | Web-Frontend (manuelle Abfragen) | – | Geplant |
| 4 | KI-Zusammenfassungen | Claude API | Geplant |
| 5 | Google Sheets Export | Sheets API | Geplant |
| 5 | Digitaler Bilderrahmen | – | Geplant |

Jedes Modul wird als eigenständiger Adapter implementiert und über `config.yaml` aktiviert oder deaktiviert – ohne Code-Änderungen.

---

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

---

## Projektstruktur

```
feedkrake/
├── .env                          # Secrets (gitignored)
├── .env.example                  # Vorlage
├── config.yaml                   # Modulkonfiguration
├── build.gradle.kts
├── settings.gradle.kts
├── data/
│   └── feedkrake.duckdb          # Datenbank (gitignored, portabel)
└── src/main/kotlin/de/aggregator/
    ├── Application.kt
    ├── domain/
    │   ├── model/                # Domain-Modelle (Match, Goal, Team, Standing, ...)
    │   ├── port/
    │   │   ├── input/            # Use Cases (FetchDataUseCase, QueryDataUseCase)
    │   │   └── output/           # Ports (MatchRepository, FootballApiPort, NotificationPort)
    │   └── service/              # Domain-Services (IngestionService, QueryService)
    └── adapter/
        ├── input/scheduler/      # IngestionScheduler
        ├── output/
        │   ├── api/              # OpenLigaDbClient (weitere APIs folgen)
        │   ├── persistence/      # DuckDbRepository
        │   └── discord/          # DiscordSender (weitere Kanäle folgen)
        └── config/               # AppModule, ConfigLoader, DatabaseConfig
```

---

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

Danach läuft FeedKrake vollautomatisch: Daten werden im Hintergrund aktualisiert, Ausgaben nach konfiguriertem Schedule gesendet.

---

## Konfiguration

Alle Module werden in `config.yaml` verwaltet. Ein Modul aktivieren oder deaktivieren erfordert keine Code-Änderung:

```yaml
modules:
  - id: bundesliga_1
    type: football
    enabled: true           # auf false setzen zum Deaktivieren
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
      - type: discord
        channel: sport
        schedule: WEEKLY_MONDAY_09_00
        format: matchday_results
```

### Output-Schedules

| Schlüssel | Beschreibung |
|---|---|
| `DAILY_HH_MM` | Täglich zur angegebenen Uhrzeit |
| `WEEKLY_WEEKDAY_HH_MM` | Wöchentlich am angegebenen Tag |
| `ON_MATCH_END` | Direkt nach Spielschluss (Phase 2) |

### Output-Formate (Phase 1)

| Format | Beschreibung |
|---|---|
| `table_summary` | Tägliche Tabellenübersicht |
| `matchday_results` | Spieltagsergebnisse mit Torschützen |

---

## Portabilität

Die `data/feedkrake.duckdb` kann auf einen anderen PC kopiert werden. Nach erneutem `./gradlew run` wird der Betrieb mit dem vorhandenen Datenstand fortgesetzt – kein erneutes Setup nötig.
