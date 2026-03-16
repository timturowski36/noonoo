package sources.handballstatistiken.api

import sources.claude.api.WebFetcher
import sources.handballstatistiken.model.HandballScorerData
import sources.handballstatistiken.model.HandballScorerStats
import java.time.LocalDateTime

/**
 * Lädt und parst die Torjägertabelle von handballstatistiken.de.
 *
 * Die Seite liefert eine HTML-Tabelle mit folgenden Spalten (0-basiert):
 *   0  #          Rang
 *   1  Name       Spielername
 *   2  Mannschaft Vereinsname
 *   3  TrNr.      Trikotnummer
 *   4  Spiele
 *   5  Tore
 *   6  Feldtore
 *   7  7m Tore
 *   8  7m gew.    7-Meter geworfen
 *   9  7m %
 *  10  letztes Spiel
 *  11  Tore/Spiel
 *  12  Feldt./Spiel
 *  13  Verw.
 *  14  2 Min.
 *  15  Disq.
 *
 * Nutzung:
 *   val client = HandballStatistikenClient()
 *   val data   = client.fetchScorer("https://handballstatistiken.de/NRW/2526/300268")
 */
class HandballStatistikenClient {

    private val fetcher = WebFetcher()

    // ──────────────────────────────────────────────────────────────────────────
    // Öffentliche API
    // ──────────────────────────────────────────────────────────────────────────

    fun fetchScorer(url: String): HandballScorerData? {
        println("📊 [HandballStatistiken] Lade Torjägertabelle ...")
        println("   URL: $url")

        val content = fetcher.fetch(url) ?: return null

        val players = parseTable(content.html)

        if (players.isEmpty()) {
            println("❌ [HandballStatistiken] Keine Spieler in der Tabelle gefunden")
            return null
        }

        println("✅ [HandballStatistiken] ${players.size} Spieler geladen")

        return HandballScorerData(
            url       = url,
            players   = players,
            fetchedAt = LocalDateTime.now()
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HTML-Parsing
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Extrahiert alle Datenzeilen aus der ersten Tabelle auf der Seite
     * und wandelt sie in HandballScorerStats-Objekte um.
     */
    private fun parseTable(html: String): List<HandballScorerStats> {
        // Suche <tbody> … </tbody>
        val tbodyRegex = Regex("""<tbody[^>]*>([\s\S]*?)</tbody>""", RegexOption.IGNORE_CASE)
        val tbodyMatch = tbodyRegex.find(html) ?: run {
            println("⚠️  [HandballStatistiken] Kein <tbody> gefunden – versuche direkt <tr>")
            return parseRows(html)
        }
        return parseRows(tbodyMatch.groupValues[1])
    }

    private fun parseRows(html: String): List<HandballScorerStats> {
        val trRegex = Regex("""<tr[^>]*>([\s\S]*?)</tr>""", RegexOption.IGNORE_CASE)
        val players = mutableListOf<HandballScorerStats>()

        for (trMatch in trRegex.findAll(html)) {
            val row = trMatch.groupValues[1]
            val cells = extractCells(row)

            // Header-Zeilen überspringen (weniger als 16 Zellen oder erster Wert ist kein Int)
            if (cells.size < 16) continue
            val rang = cells[0].toIntOrNull() ?: continue

            val player = parsePlayer(rang, cells) ?: continue
            players.add(player)
        }

        return players
    }

    /**
     * Gibt den reinen Text aller <td>-Zellen einer Tabellenzeile zurück.
     */
    private fun extractCells(rowHtml: String): List<String> {
        val tdRegex = Regex("""<td[^>]*>([\s\S]*?)</td>""", RegexOption.IGNORE_CASE)
        return tdRegex.findAll(rowHtml).map { match ->
            stripHtml(match.groupValues[1]).trim()
        }.toList()
    }

    /**
     * Entfernt HTML-Tags und dekodiert Entities.
     */
    private fun stripHtml(html: String): String =
        html
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
            }
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Baut aus einer Zeile (16+ Zellen) ein HandballScorerStats-Objekt.
     */
    private fun parsePlayer(rang: Int, cells: List<String>): HandballScorerStats? {
        return try {
            HandballScorerStats(
                rang                  = rang,
                name                  = cells[1],
                mannschaft            = cells[2],
                trNr                  = cells[3],
                spiele                = cells[4].toIntOrNull() ?: 0,
                tore                  = cells[5].toIntOrNull() ?: 0,
                feldtore              = cells[6].toIntOrNull() ?: 0,
                siebenmeterTore       = cells[7].toIntOrNull() ?: 0,
                siebenmeterGeworfen   = cells[8].toIntOrNull() ?: 0,
                siebenmeterProzent    = cells[9],
                letztesSpiel          = cells[10].ifBlank { "-" },
                toreProSpiel          = cells[11].replace(",", ".").toDoubleOrNull() ?: 0.0,
                feldtoreProSpiel      = cells[12].replace(",", ".").toDoubleOrNull() ?: 0.0,
                verwarnungen          = cells[13].toIntOrNull() ?: 0,
                zweiMinuten           = cells[14].toIntOrNull() ?: 0,
                disqualifikationen    = cells[15].toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            println("⚠️  [HandballStatistiken] Zeile $rang konnte nicht geparst werden: ${e.message}")
            null
        }
    }
}
