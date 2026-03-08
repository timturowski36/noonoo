import config.EnvConfig
import sources.bf6.api.Bf6ApiClient

fun main() {
    println("FeedKrake gestartet")

    // Konfiguration laden
    EnvConfig.printStatus()

    // BF6 Stats abfragen (kein API Key nötig)
    println("\n── Battlefield 6 Stats ──────────────────")
    val bf6Client = Bf6ApiClient()
    val stats = bf6Client.fetchStats("PhilipNC", "pc")

    if (stats != null) {
        println(stats.discordFormat())
    }
}
