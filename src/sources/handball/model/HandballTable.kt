package sources.handball.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ein Eintrag in der Handball-Tabelle.
 */
data class HandballTableEntry(
    val position: Int,
    val teamName: String,
    val games: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val points: Int
) {
    /**
     * Formatiert den Eintrag für Discord.
     */
    fun format(highlightTeam: String? = null): String {
        val highlight = if (highlightTeam != null && teamName.contains(highlightTeam, ignoreCase = true)) "**" else ""
        val posStr = position.toString().padStart(2)
        val teamStr = teamName.take(25).padEnd(25)
        val gamesStr = games.toString().padStart(2)
        val winsStr = wins.toString().padStart(2)
        val drawsStr = draws.toString().padStart(2)
        val lossesStr = losses.toString().padStart(2)
        val goalsStr = "$goalsFor:$goalsAgainst".padStart(7)
        val diffStr = (if (goalDiff >= 0) "+$goalDiff" else "$goalDiff").padStart(4)
        val pointsStr = points.toString().padStart(3)

        return "$highlight$posStr. $teamStr $gamesStr  $winsStr-$drawsStr-$lossesStr  $goalsStr ($diffStr)  $pointsStr$highlight"
    }
}

/**
 * Container für eine Handball-Tabelle.
 */
data class HandballTableData(
    val teamId: String,
    val league: String,
    val season: String,
    val entries: List<HandballTableEntry>,
    val fetchedAt: LocalDateTime
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Findet die Position des eigenen Teams.
     */
    fun findTeamPosition(teamName: String): HandballTableEntry? {
        return entries.find { it.teamName.contains(teamName, ignoreCase = true) }
    }

    /**
     * Discord-Format für die Tabelle.
     */
    fun discordFormat(highlightTeam: String? = null): String {
        return buildString {
            appendLine("📊 **$league** - Tabelle")
            appendLine("Saison: $season")
            appendLine("```")
            appendLine("Pl. Team                      Sp   S-U-N   Tore   Diff  Pkt")
            appendLine("─".repeat(65))
            entries.forEach { entry ->
                appendLine(entry.format(highlightTeam))
            }
            appendLine("```")
            append("🕐 Stand: ${fetchedAt.format(dateFormatter)}")
        }
    }

    /**
     * Kurzformat mit nur Top 5 + eigenes Team.
     */
    fun discordCompactFormat(highlightTeam: String): String {
        val ownEntry = findTeamPosition(highlightTeam)
        val top5 = entries.take(5)

        return buildString {
            appendLine("📊 **$league** - Top 5")
            appendLine("```")
            top5.forEach { entry ->
                appendLine(entry.format(highlightTeam))
            }
            if (ownEntry != null && ownEntry.position > 5) {
                appendLine("...")
                appendLine(ownEntry.format(highlightTeam))
            }
            appendLine("```")
            append("🕐 Stand: ${fetchedAt.format(dateFormatter)}")
        }
    }
}
