package sources.bf6.model

data class Bf6Stats(
    val userName: String,
    val kills: Int,
    val deaths: Int,
    val killDeath: Double,
    val wins: Int,
    val losses: Int,
    val matchesPlayed: Int,
    val winPercent: String,
    val killAssists: Int,
    val headShots: Int,
    val headshotPercent: String,
    val revives: Int,
    val accuracy: String,
    val timePlayed: String,
    val killsPerMatch: Double
) {
    fun discordFormat(): String {
        return buildString {
            appendLine("📊 Gesamtstatistik:")
            appendLine("Matches: $matchesPlayed   Wins: $wins ($winPercent)   K/D: ${"%.2f".format(killDeath)}")
            appendLine("Kills: $kills   Assists: $killAssists   Kills/Match: ${"%.1f".format(killsPerMatch)}")
            appendLine("Headshots: $headShots ($headshotPercent)   Revives: $revives")
            append("Accuracy: $accuracy   Spielzeit: $timePlayed")
        }
    }

    fun weeklyFormat(baseline: Bf6Stats): String {
        val weeklyKills    = kills - baseline.kills
        val weeklyDeaths   = deaths - baseline.deaths
        val weeklyMatches  = matchesPlayed - baseline.matchesPlayed
        val weeklyWins     = wins - baseline.wins
        val weeklyAssists  = killAssists - baseline.killAssists
        val weeklyHeadshots = headShots - baseline.headShots
        val weeklyRevives  = revives - baseline.revives

        val weeklyKD       = if (weeklyDeaths > 0) weeklyKills.toDouble() / weeklyDeaths else weeklyKills.toDouble()
        val weeklyKPM      = if (weeklyMatches > 0) weeklyKills.toDouble() / weeklyMatches else 0.0
        val weeklyHSRate   = if (weeklyKills > 0) weeklyHeadshots.toDouble() / weeklyKills * 100 else 0.0

        return buildString {
            appendLine("📅 Wochenstatistik:")
            appendLine("Matches: $weeklyMatches   Wins: $weeklyWins   K/D: ${"%.2f".format(weeklyKD)}")
            appendLine("Kills: $weeklyKills   Assists: $weeklyAssists   Kills/Match: ${"%.1f".format(weeklyKPM)}")
            append("Headshots: $weeklyHeadshots (${"%.0f".format(weeklyHSRate)}%)   Revives: $weeklyRevives")
        }
    }
}
