package sources.claude.model

/**
 * DTO für ein einzelnes Handball-Spiel (Ergebnis).
 */
data class HandballResult(
    val date: String,
    val home: String,
    val away: String,
    val scoreHome: Int,
    val scoreAway: Int,
    val isHome: Boolean,
    val won: Boolean
) {
    fun discordFormat(): String {
        val result = if (won) "✅" else "❌"
        val score = "$scoreHome:$scoreAway"
        return if (isHome) {
            "$result $date: **$home** vs $away → $score"
        } else {
            "$result $date: $home vs **$away** → $score"
        }
    }
}

/**
 * DTO für ein kommendes Handball-Spiel.
 */
data class HandballUpcoming(
    val date: String,
    val time: String,
    val home: String,
    val away: String,
    val venue: String?,
    val isHome: Boolean
) {
    fun discordFormat(): String {
        val marker = if (isHome) "🏠" else "✈️"
        return "$marker $date $time: $home vs $away" + (venue?.let { " ($it)" } ?: "")
    }
}

/**
 * DTO für alle Ergebnisse einer Mannschaft.
 */
data class HandballResults(
    val team: String,
    val league: String,
    val season: String,
    val results: List<HandballResult>
) {
    val wins: Int get() = results.count { it.won }
    val losses: Int get() = results.count { !it.won }

    fun discordFormat(): String {
        return buildString {
            appendLine("🤾 **$team** – Ergebnisse ($season)")
            appendLine("Liga: $league | Bilanz: $wins Siege, $losses Niederlagen")
            appendLine("```")
            results.take(10).forEach { appendLine(it.discordFormat()) }
            if (results.size > 10) appendLine("... und ${results.size - 10} weitere")
            append("```")
        }
    }

    companion object {
        fun fromResponse(response: ClaudeResponse): HandballResults? {
            val json = response.extractJsonBlock() ?: return null

            val team = extractString(json, "team") ?: "Unbekannt"
            val league = extractString(json, "league") ?: "Unbekannt"
            val season = extractString(json, "season") ?: "Unbekannt"

            val resultsArray = extractArray(json, "results") ?: return null
            val results = resultsArray.mapNotNull { parseResult(it) }

            if (results.isEmpty()) return null
            return HandballResults(team, league, season, results)
        }

        private fun parseResult(json: String): HandballResult? {
            return HandballResult(
                date = extractString(json, "date") ?: return null,
                home = extractString(json, "home") ?: return null,
                away = extractString(json, "away") ?: return null,
                scoreHome = extractInt(json, "scoreHome") ?: return null,
                scoreAway = extractInt(json, "scoreAway") ?: return null,
                isHome = extractBoolean(json, "isHome") ?: true,
                won = extractBoolean(json, "won") ?: false
            )
        }
    }
}

/**
 * DTO für den Spielplan einer Mannschaft.
 */
data class HandballSchedule(
    val team: String,
    val league: String,
    val season: String,
    val upcomingMatches: List<HandballUpcoming>
) {
    fun discordFormat(): String {
        return buildString {
            appendLine("🤾 **$team** – Nächste Spiele ($season)")
            appendLine("Liga: $league")
            appendLine("```")
            upcomingMatches.take(5).forEach { appendLine(it.discordFormat()) }
            if (upcomingMatches.size > 5) appendLine("... und ${upcomingMatches.size - 5} weitere")
            append("```")
        }
    }

    companion object {
        fun fromResponse(response: ClaudeResponse): HandballSchedule? {
            val json = response.extractJsonBlock() ?: return null

            val team = extractString(json, "team") ?: "Unbekannt"
            val league = extractString(json, "league") ?: "Unbekannt"
            val season = extractString(json, "season") ?: "Unbekannt"

            val matchesArray = extractArray(json, "upcomingMatches") ?: return null
            val matches = matchesArray.mapNotNull { parseUpcoming(it) }

            if (matches.isEmpty()) return null
            return HandballSchedule(team, league, season, matches)
        }

        private fun parseUpcoming(json: String): HandballUpcoming? {
            return HandballUpcoming(
                date = extractString(json, "date") ?: return null,
                time = extractString(json, "time") ?: "TBD",
                home = extractString(json, "home") ?: return null,
                away = extractString(json, "away") ?: return null,
                venue = extractString(json, "venue"),
                isHome = extractBoolean(json, "isHome") ?: true
            )
        }
    }
}

/**
 * DTO für ein Team in der Handball-Tabelle.
 */
data class HandballTeamStanding(
    val rank: Int,
    val name: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val points: Int
) {
    fun discordFormat(): String {
        val nameFormatted = name.take(22).padEnd(22)
        return "${"$rank.".padStart(3)} $nameFormatted | ${"$played".padStart(2)} | $won-$drawn-$lost | $goalsFor:$goalsAgainst | ${"$points".padStart(2)}"
    }
}

/**
 * DTO für die Handball-Ligatabelle.
 */
data class HandballTable(
    val league: String,
    val season: String,
    val teams: List<HandballTeamStanding>
) {
    fun discordFormat(): String {
        return buildString {
            appendLine("🤾 **$league** – Tabelle ($season)")
            appendLine("```")
            appendLine("Pl. Team                   | Sp | S-U-N | Tore    | Pkt")
            appendLine("─".repeat(58))
            teams.forEach { appendLine(it.discordFormat()) }
            append("```")
        }
    }

    companion object {
        fun fromResponse(response: ClaudeResponse): HandballTable? {
            val json = response.extractJsonBlock() ?: return null

            val league = extractString(json, "league") ?: "Unbekannt"
            val season = extractString(json, "season") ?: "Unbekannt"

            val teamsArray = extractArray(json, "teams") ?: return null
            val teams = teamsArray.mapNotNull { parseTeam(it) }

            if (teams.isEmpty()) return null
            return HandballTable(league, season, teams)
        }

        private fun parseTeam(json: String): HandballTeamStanding? {
            return HandballTeamStanding(
                rank = extractInt(json, "rank") ?: return null,
                name = extractString(json, "name") ?: return null,
                played = extractInt(json, "played") ?: 0,
                won = extractInt(json, "won") ?: 0,
                drawn = extractInt(json, "drawn") ?: 0,
                lost = extractInt(json, "lost") ?: 0,
                goalsFor = extractInt(json, "goalsFor") ?: 0,
                goalsAgainst = extractInt(json, "goalsAgainst") ?: 0,
                goalDiff = extractInt(json, "goalDiff") ?: 0,
                points = extractInt(json, "points") ?: 0
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hilfsfunktionen für JSON-Parsing (ohne Library)
// ─────────────────────────────────────────────────────────────────────────────

private fun extractString(json: String, key: String): String? {
    val regex = Regex(""""$key"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
    return regex.find(json)?.groupValues?.get(1)
        ?.replace("\\n", "\n")
        ?.replace("\\\"", "\"")
}

private fun extractInt(json: String, key: String): Int? {
    val regex = Regex(""""$key"\s*:\s*(-?\d+)""")
    return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
}

private fun extractBoolean(json: String, key: String): Boolean? {
    val regex = Regex(""""$key"\s*:\s*(true|false)""")
    return regex.find(json)?.groupValues?.get(1)?.toBoolean()
}

private fun extractArray(json: String, key: String): List<String>? {
    val arrayRegex = Regex(""""$key"\s*:\s*\[([\s\S]*?)]""")
    val arrayContent = arrayRegex.find(json)?.groupValues?.get(1) ?: return null
    val objectRegex = Regex("""\{[^{}]*}""")
    return objectRegex.findAll(arrayContent).map { it.value }.toList()
}
