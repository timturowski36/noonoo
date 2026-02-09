package sources.pubg.model

data class PlayerStats(
    val wins: Int,
    val matches: Int,
    val kills: Int,
    val deaths: Int   // = Anzahl Nicht-Wins (pro Match genau ein Tod, außer bei Win)
) {
    val kd: Double
        get() = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()

    fun kdFormatted(): String =
        if (deaths > 0) "%.2f".format(kd) else kills.toString()

    fun summary(): String {
        val winsStr = if (wins == 0) "-" else wins.toString()
        return "$winsStr / ${kdFormatted()}"
    }
}