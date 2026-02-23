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
}
