package sources.claude.prompts

/**
 * Vorgefertigter Kontext für Claude-Anfragen.
 * - systemPrompt: Wird als System-Message an Claude gesendet (Rolle/Verhalten)
 * - userPrefix: Wird vor die User-Nachricht gesetzt
 * - userSuffix: Wird nach die User-Nachricht gesetzt
 */
data class PromptContext(
    val name: String,
    val description: String,
    val systemPrompt: String? = null,
    val userPrefix: String = "",
    val userSuffix: String = ""
)

/**
 * Vordefinierte Kontexte für häufige Anwendungsfälle.
 */
object PromptContexts {

    // ─────────────────────────────────────────────────────────────────────────
    // Sport-Daten (Bundesliga, etc.)
    // ─────────────────────────────────────────────────────────────────────────

    val BUNDESLIGA_TABLE = PromptContext(
        name = "bundesliga_table",
        description = "Aktuelle Bundesliga-Tabelle als strukturierte Daten",
        systemPrompt = """Du bist ein Sport-Daten-Assistent. Du lieferst aktuelle Fußball-Statistiken.
Antworte immer im JSON-Format mit folgendem Schema:
{
  "season": "2024/25",
  "matchday": 25,
  "teams": [
    {"rank": 1, "name": "FC Bayern München", "played": 25, "won": 18, "drawn": 4, "lost": 3, "goalsFor": 65, "goalsAgainst": 25, "goalDiff": 40, "points": 58},
    ...
  ]
}""",
        userPrefix = "Gib mir die aktuelle Bundesliga-Tabelle. ",
        userSuffix = ""
    )

    val MATCH_RESULTS = PromptContext(
        name = "match_results",
        description = "Spielergebnisse eines Spieltags",
        systemPrompt = """Du bist ein Sport-Daten-Assistent. Du lieferst Spielergebnisse.
Antworte immer im JSON-Format:
{
  "matchday": 25,
  "matches": [
    {"home": "Bayern München", "away": "Dortmund", "homeGoals": 2, "awayGoals": 1, "date": "2024-03-15"},
    ...
  ]
}""",
        userPrefix = "Gib mir die Ergebnisse ",
        userSuffix = ""
    )

    // ─────────────────────────────────────────────────────────────────────────
    // News / Zusammenfassungen
    // ─────────────────────────────────────────────────────────────────────────

    val NEWS_SUMMARY = PromptContext(
        name = "news_summary",
        description = "Zusammenfassung von Nachrichten",
        systemPrompt = """Du bist ein Nachrichtenredakteur. Fasse Nachrichten kurz und prägnant zusammen.
Antworte im JSON-Format:
{
  "topic": "Thema",
  "summary": "Kurze Zusammenfassung in 2-3 Sätzen",
  "keyPoints": ["Punkt 1", "Punkt 2", "Punkt 3"]
}""",
        userPrefix = "Fasse folgendes zusammen: ",
        userSuffix = ""
    )

    val GAMING_NEWS = PromptContext(
        name = "gaming_news",
        description = "Gaming-Neuigkeiten zusammenfassen",
        systemPrompt = """Du bist ein Gaming-Journalist. Du kennst dich mit Videospielen aus.
Antworte im JSON-Format:
{
  "game": "Spielname",
  "news": "Neuigkeit",
  "releaseDate": "falls relevant",
  "platforms": ["PC", "PS5", ...]
}""",
        userPrefix = "",
        userSuffix = ""
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Analyse / Verarbeitung
    // ─────────────────────────────────────────────────────────────────────────

    val DATA_EXTRACTION = PromptContext(
        name = "data_extraction",
        description = "Strukturierte Daten aus Text extrahieren",
        systemPrompt = """Du bist ein Daten-Extraktions-Assistent.
Extrahiere strukturierte Informationen aus dem gegebenen Text.
Antworte IMMER im JSON-Format. Erkenne automatisch die passende Struktur.""",
        userPrefix = "Extrahiere die strukturierten Daten aus folgendem Text:\n\n",
        userSuffix = "\n\nGib nur das JSON zurück, keine Erklärungen."
    )

    val TRANSLATE_DE_EN = PromptContext(
        name = "translate_de_en",
        description = "Deutsch nach Englisch übersetzen",
        systemPrompt = "Du bist ein Übersetzer. Übersetze präzise von Deutsch nach Englisch.",
        userPrefix = "Übersetze ins Englische: ",
        userSuffix = ""
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Web-Scraping / Webseiten-Analyse
    // ─────────────────────────────────────────────────────────────────────────

    val WEB_ARTICLE = PromptContext(
        name = "web_article",
        description = "Artikel von Webseite extrahieren",
        systemPrompt = """Du bist ein Web-Scraping-Assistent. Extrahiere den Hauptartikel aus dem Webseiten-Inhalt.
Antworte im JSON-Format:
{
  "title": "Artikel-Titel",
  "author": "Autor (falls vorhanden)",
  "date": "Veröffentlichungsdatum (falls vorhanden)",
  "summary": "Kurze Zusammenfassung in 2-3 Sätzen",
  "content": "Vollständiger Artikeltext"
}""",
        userPrefix = "",
        userSuffix = ""
    )

    val WEB_TABLE = PromptContext(
        name = "web_table",
        description = "Tabellen-Daten von Webseite extrahieren",
        systemPrompt = """Du bist ein Web-Scraping-Assistent. Extrahiere Tabellen-Daten aus dem Webseiten-Inhalt.
Antworte im JSON-Format:
{
  "tableName": "Name/Beschreibung der Tabelle",
  "headers": ["Spalte1", "Spalte2", ...],
  "rows": [
    {"Spalte1": "Wert1", "Spalte2": "Wert2", ...},
    ...
  ]
}""",
        userPrefix = "",
        userSuffix = ""
    )

    val WEB_PRODUCTS = PromptContext(
        name = "web_products",
        description = "Produkt-Informationen von Webseite extrahieren",
        systemPrompt = """Du bist ein Web-Scraping-Assistent. Extrahiere Produkt-Informationen aus dem Webseiten-Inhalt.
Antworte im JSON-Format:
{
  "products": [
    {"name": "Produktname", "price": "Preis", "description": "Beschreibung", "availability": "Verfügbarkeit"},
    ...
  ]
}""",
        userPrefix = "",
        userSuffix = ""
    )

    val WEB_EVENTS = PromptContext(
        name = "web_events",
        description = "Events/Termine von Webseite extrahieren",
        systemPrompt = """Du bist ein Web-Scraping-Assistent. Extrahiere Event- und Termin-Informationen.
Antworte im JSON-Format:
{
  "events": [
    {"name": "Event-Name", "date": "Datum", "time": "Uhrzeit", "location": "Ort", "description": "Beschreibung"},
    ...
  ]
}""",
        userPrefix = "",
        userSuffix = ""
    )

    val WEB_CUSTOM = PromptContext(
        name = "web_custom",
        description = "Benutzerdefinierte Daten-Extraktion",
        systemPrompt = """Du bist ein Web-Scraping-Assistent. Extrahiere die angeforderten Informationen aus dem Webseiten-Inhalt.
Antworte IMMER im JSON-Format. Strukturiere die Daten sinnvoll basierend auf der Anfrage.""",
        userPrefix = "",
        userSuffix = ""
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Freestyle (kein fester Kontext)
    // ─────────────────────────────────────────────────────────────────────────

    val FREESTYLE = PromptContext(
        name = "freestyle",
        description = "Freie Anfrage ohne vordefinierten Kontext",
        systemPrompt = null,
        userPrefix = "",
        userSuffix = ""
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Alle Kontexte als Liste
    // ─────────────────────────────────────────────────────────────────────────

    val ALL = listOf(
        BUNDESLIGA_TABLE,
        MATCH_RESULTS,
        NEWS_SUMMARY,
        GAMING_NEWS,
        DATA_EXTRACTION,
        TRANSLATE_DE_EN,
        WEB_ARTICLE,
        WEB_TABLE,
        WEB_PRODUCTS,
        WEB_EVENTS,
        WEB_CUSTOM,
        FREESTYLE
    )

    fun byName(name: String): PromptContext? = ALL.find { it.name == name }
}
