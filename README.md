# FeedKrake

# 🚀 DatenLoader

> Dein persönlicher Feed-Aggregator mit modularem Baukasten-Prinzip

---

## 🎯 Was ist DatenLoader?

DatenLoader ist eine App, mit der du dir deinen **eigenen personalisierten Feed** zusammenbauen kannst – ganz nach deinen Bedürfnissen. 

Stell dir vor: Jeden Morgen um 7 Uhr bekommst du automatisch die nächsten Spiele deines Lieblingsvereins auf Discord. Und auf deinem digitalen Bilderrahmen im Flur rotiert die aktuelle Bundesliga-Tabelle zusammen mit den neuesten Heise-News. 

**Das ist DatenLoader.** 📲

---

## 🧩 Das Konzept

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   SOURCES   │  →   │   QUERIES   │  →   │   OUTPUTS   │
│  (Quellen)  │      │  (Abfragen) │      │  (Ausgabe)  │
└─────────────┘      └─────────────┘      └─────────────┘
```

### 📥 Sources (Datenquellen)
Woher kommen die Daten?

| Source | Beschreibung |
|--------|--------------|
| ⚽ Bundesliga | Spielpläne, Tabellen, Vereinsinfos |
| 📰 RSS Feeds | Heise, Spiegel, dein Lieblingsblog |
| 🌤️ Wetter | Aktuelle Wetterdaten |
| *...und mehr* | Erweiterbar durch Plugins |

### 🔍 Queries (Abfragen)
Was genau willst du wissen?

- **Bundesliga:** Tabelle, Nächste Spiele, Top 5 Ligen
- **RSS:** Neueste Artikel, Gefiltert nach Keywords
- **Wetter:** Aktuell, 7-Tage-Vorhersage

### 📤 Outputs (Ausgabekanäle)
Wo sollen die Daten hin?

| Output | Beschreibung |
|--------|--------------|
| 💬 Discord | Nachrichten in deinen Channel |
| 📧 E-Mail | Tägliche Zusammenfassung |

---

## ⚙️ Einstellungen auf jeder Ebene

### Source-Einstellungen
> *"Ich bin Schalke-Fan und verfolge die 1. Bundesliga"*

```
Lieblingsverein: Schalke 04
Liga: Bundesliga 1
```

### Query-Einstellungen
> *"Zeig mir die nächsten 3 Spiele, auch Auswärtsspiele"*

```
Anzahl: 3
Nur Heimspiele: Nein
```

---

## 🗓️ Der Scheduler

Bestimme selbst **wann** und **wie oft** deine Feeds laufen:

- ☀️ **Täglich um 7:00** – Morgen-Briefing auf Discord
- 🔄 **Alle 30 Minuten** – Bilderrahmen-Update
- 📅 **Samstags 14:00** – Spieltag-Reminder

---

## 🖥️ So funktioniert's

1. **Anmelden** – Erstelle deinen Account
2. **Outputs verbinden** – Discord-Webhook, Whatsapp, was auch immer ...
3. **Pipelines bauen** – Drag & Drop deine Module zusammen
4. **Scheduler einrichten** – Wann soll was laufen?
5. **Zurücklehnen** – DatenLoader erledigt den Rest 😎

---

## 🏗️ Technologie

- **Backend:** Kotlin
- **APIs:** Nur frei verfügbare Quellen aus dem Internet



<p align="center">
  <i>Made with ☕ und Liebe zu Daten</i>
</p>
