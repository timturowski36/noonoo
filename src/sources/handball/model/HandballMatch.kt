package sources.handball.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Repräsentiert ein Handball-Spiel (vergangen oder kommend).
 */
data class HandballMatch(
    val id: String,
    val date: LocalDateTime,
    val homeTeam: String,
    val awayTeam: String,
    val venue: String?,
    val scoreHome: Int?,
    val scoreAway: Int?,
    val isPlayed: Boolean
) {
    companion object {
        private val displayFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        private val shortDateFormatter = DateTimeFormatter.ofPattern("dd.MM.")
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }

    /**
     * Prüft ob das eigene Team Heimspiel hat.
     */
    fun isHomeGame(teamName: String): Boolean {
        return homeTeam.contains(teamName, ignoreCase = true)
    }

    /**
     * Discord-Format für kommende Spiele.
     */
    fun upcomingFormat(teamName: String): String {
        val marker = if (isHomeGame(teamName)) "🏠" else "✈️"
        val dateStr = date.format(shortDateFormatter)
        val timeStr = date.format(timeFormatter)
        return "$marker **$dateStr** $timeStr: $homeTeam vs $awayTeam" +
            (venue?.let { " • $it" } ?: "")
    }

    /**
     * Discord-Format für gespielte Spiele.
     */
    fun resultFormat(teamName: String): String {
        val isHome = isHomeGame(teamName)
        val won = when {
            scoreHome == null || scoreAway == null -> null
            isHome -> scoreHome > scoreAway
            else -> scoreAway > scoreHome
        }
        val marker = when (won) {
            true -> "✅"
            false -> "❌"
            null -> "⏸️"
        }
        val score = if (scoreHome != null && scoreAway != null) "$scoreHome:$scoreAway" else "-:-"
        return "$marker ${date.format(shortDateFormatter)}: $homeTeam vs $awayTeam → $score"
    }
}

/**
 * Container für alle Spiele einer Mannschaft mit Metadaten.
 */
data class HandballScheduleData(
    val teamId: String,
    val teamName: String,
    val season: String,
    val matches: List<HandballMatch>,
    val fetchedAt: LocalDateTime
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    }

    /**
     * Nächste X Spiele die noch nicht gespielt wurden.
     */
    fun upcomingMatches(limit: Int = 5): List<HandballMatch> {
        val now = LocalDateTime.now()
        return matches
            .filter { !it.isPlayed && it.date.isAfter(now) }
            .sortedBy { it.date }
            .take(limit)
    }

    /**
     * Letzte X gespielte Spiele.
     */
    fun recentResults(limit: Int = 5): List<HandballMatch> {
        return matches
            .filter { it.isPlayed }
            .sortedByDescending { it.date }
            .take(limit)
    }

    /**
     * Das nächste Spiel.
     */
    fun nextMatch(): HandballMatch? {
        return upcomingMatches(1).firstOrNull()
    }

    /**
     * Discord-Format für die nächsten Spiele.
     */
    fun discordUpcomingFormat(): String {
        val upcoming = upcomingMatches(5)
        return buildString {
            appendLine("🤾 **$teamName** - Nächste Spiele")
            appendLine("Saison: $season")
            appendLine("```")
            if (upcoming.isEmpty()) {
                appendLine("Keine kommenden Spiele gefunden.")
            } else {
                upcoming.forEach { match ->
                    appendLine(match.upcomingFormat(teamName))
                }
            }
            appendLine("```")
            append("🕐 Stand: ${fetchedAt.format(dateFormatter)}")
        }
    }

    /**
     * Discord-Format für die letzten Ergebnisse.
     */
    fun discordResultsFormat(): String {
        val results = recentResults(5)
        return buildString {
            appendLine("🤾 **$teamName** - Letzte Ergebnisse")
            appendLine("Saison: $season")
            appendLine("```")
            if (results.isEmpty()) {
                appendLine("Keine Ergebnisse gefunden.")
            } else {
                results.forEach { match ->
                    appendLine(match.resultFormat(teamName))
                }
            }
            appendLine("```")
            append("🕐 Stand: ${fetchedAt.format(dateFormatter)}")
        }
    }
}
