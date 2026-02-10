package sources.pubg.model

data class PlayerStats(
    val wins: Int,
    val matches: Int,
    val kills: Int,
    val deaths: Int,   // = Anzahl Nicht-Wins (pro Match genau ein Tod, außer bei Win)
    val damageDealt: Double = 0.0,
    val assists: Int = 0
) {
    val kd: Double
        get() = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()

    val avgDamage: Double
        get() = if (matches > 0) damageDealt / matches else 0.0

    fun kdFormatted(): String =
        if (deaths > 0) "%.2f".format(kd) else kills.toString()

    fun avgDamageFormatted(): String = "%.0f".format(avgDamage)

    fun summary(): String {
        val winsStr = if (wins == 0) "-" else wins.toString()
        return "$winsStr / ${kdFormatted()}"
    }

    fun extendedSummary(): String {
        val winsStr = if (wins == 0) "-" else wins.toString()
        return "$matches Matches | $winsStr Wins | $kills Kills | $assists Assists | K/D: ${kdFormatted()} | Avg Dmg: ${avgDamageFormatted()}"
    }
}