package sources.claude.model

/**
 * DTO für ein Team in der Bundesliga-Tabelle.
 */
data class BundesligaTeam(
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
        val nameFormatted = name.padEnd(25)
        return "$rank. $nameFormatted | $played | $won-$drawn-$lost | $goalsFor:$goalsAgainst | $points Pkt"
    }
}

/**
 * DTO für die gesamte Bundesliga-Tabelle.
 */
data class BundesligaTable(
    val season: String,
    val matchday: Int,
    val teams: List<BundesligaTeam>
) {
    fun discordFormat(): String {
        return buildString {
            appendLine("⚽ **Bundesliga $season** (Spieltag $matchday)")
            appendLine("```")
            appendLine("Pl. Team                      | Sp | S-U-N  | Tore  | Pkt")
            appendLine("─".repeat(60))
            teams.forEach { appendLine(it.discordFormat()) }
            append("```")
        }
    }

    companion object {
        /**
         * Parsed ein BundesligaTable aus einer ClaudeResponse.
         */
        fun fromResponse(response: ClaudeResponse): BundesligaTable? {
            val json = response.extractJsonBlock() ?: return null

            // Season extrahieren
            val seasonRegex = Regex(""""season"\s*:\s*"([^"]+)"""")
            val season = seasonRegex.find(json)?.groupValues?.get(1) ?: "unbekannt"

            // Matchday extrahieren
            val matchdayRegex = Regex(""""matchday"\s*:\s*(\d+)""")
            val matchday = matchdayRegex.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // Teams-Array extrahieren
            val teamsArrayRegex = Regex(""""teams"\s*:\s*\[([\s\S]*?)]""")
            val teamsJson = teamsArrayRegex.find(json)?.groupValues?.get(1) ?: return null

            // Einzelne Team-Objekte parsen
            val teamObjectRegex = Regex("""\{[^{}]*}""")
            val teams = teamObjectRegex.findAll(teamsJson).mapNotNull { match ->
                parseTeam(match.value)
            }.toList()

            if (teams.isEmpty()) return null

            return BundesligaTable(season, matchday, teams)
        }

        private fun parseTeam(teamJson: String): BundesligaTeam? {
            fun extractInt(key: String): Int? {
                val regex = Regex(""""$key"\s*:\s*(\d+)""")
                return regex.find(teamJson)?.groupValues?.get(1)?.toIntOrNull()
            }

            fun extractString(key: String): String? {
                val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
                return regex.find(teamJson)?.groupValues?.get(1)
            }

            return BundesligaTeam(
                rank = extractInt("rank") ?: return null,
                name = extractString("name") ?: return null,
                played = extractInt("played") ?: 0,
                won = extractInt("won") ?: 0,
                drawn = extractInt("drawn") ?: 0,
                lost = extractInt("lost") ?: 0,
                goalsFor = extractInt("goalsFor") ?: 0,
                goalsAgainst = extractInt("goalsAgainst") ?: 0,
                goalDiff = extractInt("goalDiff") ?: 0,
                points = extractInt("points") ?: 0
            )
        }
    }
}
