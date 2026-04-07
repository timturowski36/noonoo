# NooNoo

**NooNoo** ist ein modularer Datenaggregator in Kotlin – benannt nach dem fleißigen kleinen Roboter aus den Teletubbys, der unermüdlich alles aufsammelt und aufräumt.

NooNoo saugt automatisch Daten aus verschiedenen Quellen (Bundesliga, PUBG, News-RSS), speichert sie lokal in einer DuckDB-Datenbank und liefert aufbereitete Zusammenfassungen über konfigurierbare Ausgabekanäle (Discord) – genau dann, wenn man sie braucht.

Die Ingestion und die Ausgabe laufen vollständig entkoppelt: Die Datenbank wird kontinuierlich im Hintergrund aktuell gehalten; Ausgaben folgen einem eigenen, konfigurierbaren Schedule.

Langfristig soll NooNoo als persönlicher Planungsassistent fungieren – strukturierte Daten, im richtigen Format, zur richtigen Zeit, auf dem richtigen Kanal.

---

## Architektur

NooNoo folgt der **Hexagonalen Architektur (Ports & Adapters)**. Dadurch kann jede Datenquelle und jeder Ausgabekanal unabhängig ausgetauscht oder erweitert werden, ohne die Kernlogik zu berühren.

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

| Phase | Fokus | Details | Status |
|---|---|---|---|
| 1 | Bundesliga – Grundgerüst | Projektstruktur, Domain Models, OpenLigaDB-Client, DuckDB, Discord-Output | Abgeschlossen |
| 2 | Bundesliga – Ausgaben ausbauen | Weitere Discord-Formate und Output-Schedules für das bestehende Bundesliga-Modul | Abgeschlossen |
| 3 | News-Module | RSS-Feeds (Heise, Tagesschau) → Discord mit Keyword-Filterung | Abgeschlossen |
| 4 | PUBG-Daten | Spieler- & Match-Statistiken via PUBG API → DuckDB → Discord | Abgeschlossen |
| 5 | Erster Scraper | Web-Scraping einer Datenquelle ohne offizielle API | Geplant |
| 6 | Google Sheets Export | Daten aus DuckDB → Google Sheets | Geplant |
| 7 | Wetter- & Finanzdaten | Wetter-API, Finance-API → Discord | Geplant |
| 8 | KI-Zusammenfassungen | Claude API für aufbereitete Textzusammenfassungen | Geplant |
| 9 | Web-Frontend | Einfaches Frontend für manuelle Abfragen | Geplant |
| 10 | Digitaler Bilderrahmen | Visualisierung auf dediziertem Display | Geplant |

Jedes Modul wird als eigenständiger Adapter implementiert und über `config.yaml` aktiviert oder deaktiviert – ohne Code-Änderungen.

---

## Tech Stack

| Komponente | Technologie | Version |
|---|---|---|
| Sprache | Kotlin | 2.3.0 |
| JVM | Java | 21 |
| Build | Gradle Kotlin DSL | – |
| Datenbank | DuckDB (JDBC) | 1.5.1.0 |
| HTTP Client | Ktor Client (CIO) | 3.4.2 |
| JSON | kotlinx.serialization | 1.7.0 |
| Scheduling | Kotlin Coroutines | 1.9.0 |
| DI | Koin | 4.1.1 |
| Discord | discord-webhooks | 0.8.4 |
| Env-Variablen | dotenv-kotlin | 6.5.1 |
| HTML/RSS-Parsing | Jsoup | 1.22.1 |
| KI (optional) | Anthropic Java SDK | 2.19.0 |
| Logging | Logback | 1.5.6 |

---

## Projektstruktur

```
noonoo/
├── .env                          # Secrets (gitignored)
├── .env.example                  # Vorlage
├── config.yaml                   # Modulkonfiguration
├── build.gradle.kts
├── settings.gradle.kts
├── data/
│   └── noonoo.duckdb             # Datenbank (gitignored, portabel)
└── src/main/kotlin/de/noonoo/
    ├── Application.kt
    ├── domain/
    │   ├── model/                # Match, Goal, Team, Standing, NewsArticle,
    │   │                         # PubgPlayer, PubgMatch, PubgMatchParticipant,
    │   │                         # PubgSeasonStats, PubgPeriodStats, PubgMapStat,
    │   │                         # PubgPersonalRecords, ...
    │   ├── port/
    │   │   ├── input/            # FetchDataUseCase, QueryDataUseCase,
    │   │   │                     # FetchNewsUseCase, FetchPubgDataUseCase,
    │   │   │                     # QueryPubgDataUseCase
    │   │   └── output/           # MatchRepository, FootballApiPort,
    │   │                         # NewsRepository, PubgApiPort, PubgRepository
    │   └── service/              # IngestionService, QueryService,
    │                             # NewsIngestionService, PubgIngestionService,
    │                             # PubgQueryService
    └── adapter/
        ├── input/scheduler/      # IngestionScheduler
        ├── output/
        │   ├── api/              # OpenLigaDbClient, RssNewsClient, PubgApiClient
        │   ├── persistence/      # DuckDbRepository, DuckDbNewsRepository,
        │   │                     # DuckDbPubgRepository
        │   └── discord/          # DiscordSender, PubgDiscordFormatter
        └── config/               # AppModule, ConfigLoader, DatabaseConfig
```

---

## Setup

### 1. Repository klonen

```bash
git clone https://github.com/timturowski36/noonoo
cd noonoo
```

### 2. Secrets konfigurieren

```bash
cp .env.example .env
```

`.env` mit eigenen Werten befüllen:

```env
DISCORD_SPORT_WEBHOOK=https://discord.com/api/webhooks/...
DISCORD_NEWS_WEBHOOK=https://discord.com/api/webhooks/...
DISCORD_GAMING_WEBHOOK=https://discord.com/api/webhooks/...   # für PUBG-Ausgaben

PUBG_API_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...         # PUBG Developer Portal
```

### 3. Starten

```bash
./gradlew run
```

Danach läuft NooNoo vollautomatisch: Daten werden im Hintergrund aktualisiert, Ausgaben nach konfiguriertem Schedule gesendet.

---

## Konfiguration

Alle Module werden in `config.yaml` verwaltet. Ein Modul aktivieren oder deaktivieren erfordert keine Code-Änderung:

```yaml
modules:
  - id: bundesliga_1
    type: football
    enabled: true
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
        params:
          leagueName: "1. Bundesliga"

  - id: heise_tech
    type: news
    enabled: true
    source: rss
    config:
      url: "https://www.heise.de/rss/heise-atom.xml"
      sourceName: "Heise Online"
    schedule:
      fetchIntervalMinutes: 60
    outputs:
      - type: discord
        channel: news
        schedule: DAILY_08_00
        format: news_compact
        params:
          limit: "5"
          keywords: "KI,GPU,Linux,Windows,Sicherheit"

  - id: pubg_friends
    type: pubg
    enabled: true
    source: pubg_api
    config:
      platform: steam        # steam | xbox | psn
    players:
      - "MeinSpielerName"
      - "FreundName1"
    schedule:
      fetchIntervalMinutes: 30
    outputs:
      - type: discord
        channel: gaming
        schedule: DAILY_09_00
        format: pubg_daily_stats
      - type: discord
        channel: gaming
        schedule: WEEKLY_MONDAY_09_00
        format: pubg_weekly_stats
```

### Output-Schedules

| Schlüssel | Beschreibung |
|---|---|
| `DAILY_HH_MM` | Täglich zur angegebenen Uhrzeit |
| `WEEKLY_WEEKDAY_HH_MM` | Wöchentlich am angegebenen Tag (z. B. `WEEKLY_MONDAY_09_00`) |

### Output-Formate

| Format | Modul | Beschreibung |
|---|---|---|
| `table_summary` | football | Tabellenübersicht der Liga |
| `weekend_summary` | football | Spieltagsergebnisse mit Torschützen |
| `team_last_matches` | football | Letzte Spiele eines oder mehrerer Teams |
| `team_next_matches` | football | Nächste Spiele eines oder mehrerer Teams |
| `team_summary` | football | Kompakte Team-Zusammenfassung |
| `team_top_scorers` | football | Torjäger eines Teams |
| `league_top_scorers` | football | Liga-Torjägerliste |
| `next_matchday` | football | Nächster Spieltag (Datum + Paarungen) |
| `matchday_preview` | football | Spieltag-Vorschau mit Anstoßzeiten |
| `news_compact` | news | Kompakte Nachrichtenübersicht mit Links |
| `pubg_daily_stats` | pubg | Tagesstatistik (Matches, K/D, Ø Schaden) |
| `pubg_weekly_stats` | pubg | Wochenstatistik inkl. Headshots, Revives, weitester Kill |
| `pubg_weekly_ranking` | pubg | Gruppenranking nach K/D für die aktuelle Woche |
| `pubg_weekly_progress` | pubg | Vergleich diese Woche vs. letzte Woche |
| `pubg_records` | pubg | Persönliche Rekorde (Kills, Schaden, weitester Kill, Lifetime Wins) |
| `pubg_recent_matches` | pubg | Letzte N Matches als Tabelle (Datum, Map, Platzierung, Kills, Schaden) |
| `pubg_map_stats` | pubg | Statistiken nach Map (Matches, K/D, Ø Schaden, Siege) |

---

## PUBG-Modul

Das PUBG-Modul speichert Match- und Spielerdaten dauerhaft in der lokalen DuckDB. Da die PUBG API Matchdaten nach **14 Tagen permanent löscht**, ist die lokale Datenbank essenziell – historische Stats bleiben erhalten, solange NooNoo regelmäßig läuft.

### Funktionsweise

1. Alle konfigurierten Spielernamen werden per API aufgelöst (Account-IDs werden gespeichert).
2. Neue Matches werden automatisch erkannt und vollständig (alle Teilnehmer) gespeichert.
3. Lifetime-Stats werden nach jedem Ingestion-Lauf aktualisiert.
4. Deduplication verhindert, dass Matches mehrfach gespeichert werden, auch wenn mehrere getrackte Spieler im gleichen Match waren.

### Voraussetzungen

- PUBG API Key vom [PUBG Developer Portal](https://developer.pubg.com/)
- `PUBG_API_KEY` in `.env` eintragen
- `DISCORD_GAMING_WEBHOOK` in `.env` eintragen
- Spielernamen in `config.yaml` unter `players:` eintragen
- Modul auf `enabled: true` setzen

---

## Portabilität

Die `data/noonoo.duckdb` kann auf einen anderen PC kopiert werden. Nach erneutem Start wird der Betrieb mit dem vorhandenen Datenstand fortgesetzt – kein erneutes Setup nötig.
