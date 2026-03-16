package sources.handballstatistiken.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Torjägertabelle einer Handball-Staffel von handballstatistiken.de.
 */
data class HandballScorerData(
    /** Quell-URL, z.B. https://handballstatistiken.de/NRW/2526/300268 */
    val url: String,
    /** Alle Spieler (sortiert nach Rang) */
    val players: List<HandballScorerStats>,
    /** Zeitpunkt des Ladens */
    val fetchedAt: LocalDateTime
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Gibt die Top-N-Torjäger zurück (Standard: alle).
     */
    fun topScorer(limit: Int = players.size): List<HandballScorerStats> =
        players.take(limit)

    /**
     * Alle Einträge einer bestimmten Mannschaft.
     */
    fun byMannschaft(name: String): List<HandballScorerStats> =
        players.filter { it.mannschaft.contains(name, ignoreCase = true) }

    /**
     * Discord-Format: kompakte Torjägertabelle als Code-Block.
     *
     * Pl.  Name                    Mannschaft              Sp  Tore  7m   T/Sp
     */
    fun discordFormat(highlightTeam: String? = null, limit: Int = 15): String {
        val rows = topScorer(limit)
        return buildString {
            appendLine("🏆 **Torjägerliste** | Stand: ${fetchedAt.format(dateFormatter)}")
            appendLine("```")
            appendLine("Pl  Name                    Mannschaft              Sp  Tore  7m   T/Sp")
            appendLine("─".repeat(72))
            rows.forEach { p ->
                val highlight = highlightTeam != null &&
                    p.mannschaft.contains(highlightTeam, ignoreCase = true)
                val marker = if (highlight) "►" else " "
                val pl   = p.rang.toString().padStart(2)
                val name = p.name.take(22).padEnd(22)
                val team = p.mannschaft.take(22).padEnd(22)
                val sp   = p.spiele.toString().padStart(2)
                val tore = p.tore.toString().padStart(4)
                val sm   = p.siebenmeterTore.toString().padStart(3)
                val tps  = String.format("%.2f", p.toreProSpiel).padStart(5)
                appendLine("$marker$pl  $name  $team  $sp  $tore  $sm  $tps")
            }
            appendLine("```")
        }.trimEnd()
    }
}
