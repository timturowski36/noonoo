package sources.pubg.model

data class PlayerStats(
    val wins: Int,
    val matches: Int,
    val kills: Int,
    val deaths: Int,
    val damageDealt: Double = 0.0,
    val assists: Int = 0,
    val longestKill: Double = 0.0,
    val headshotKills: Int = 0,
    val topTens: Int = 0
) {
    val kd: Double
        get() = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()

    val avgDamage: Double
        get() = if (matches > 0) damageDealt / matches else 0.0

    val headshotRate: Double
        get() = if (kills > 0) headshotKills.toDouble() / kills * 100 else 0.0

    val topTenRate: Double
        get() = if (matches > 0) topTens.toDouble() / matches * 100 else 0.0

    fun kdFormatted(): String =
        if (deaths > 0) "%.2f".format(kd) else kills.toString()

    fun avgDamageFormatted(): String = "%.0f".format(avgDamage)

    fun discordFormat(label: String): String {
        val winsStr = if (wins == 0) "-" else "$wins"
        return buildString {
            appendLine(label)
            appendLine("Matches: $matches   Wins: $winsStr   K/D: ${kdFormatted()}")
            appendLine("Kills: $kills   Assists: $assists   Ø Schaden: ${avgDamageFormatted()}")
            append("Weitester Kill: ${"%.0f".format(longestKill)}m   Headshots: $headshotKills (${"%.0f".format(headshotRate)}%)   Top 10: $topTens (${"%.0f".format(topTenRate)}%)")
        }
    }

    fun extendedSummary(): String {
        val winsStr = if (wins == 0) "-" else wins.toString()
        return "$matches Matches | $winsStr Wins | $kills Kills | $assists Assists | K/D: ${kdFormatted()} | Avg Dmg: ${avgDamageFormatted()}"
    }

    fun summary(): String {
        val winsStr = if (wins == 0) "-" else wins.toString()
        return "$winsStr / ${kdFormatted()}"
    }
}
