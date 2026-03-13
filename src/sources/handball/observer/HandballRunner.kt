package sources.handball.observer

import config.EnvConfig

/**
 * Standalone Runner für das Handball-Modul.
 *
 * Kann direkt ausgeführt werden um den Handball-Observer zu starten.
 * Konfiguration erfolgt über .env (DISCORD_WEBHOOK_HANDBALL).
 */
fun main() {
    println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║          🐙 FeedKrake - Handball Module                       ║
    ╚═══════════════════════════════════════════════════════════════╝
    """.trimIndent())

    // Config laden
    if (!EnvConfig.load()) {
        println("❌ Konfiguration konnte nicht geladen werden!")
        return
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    // Team-ID von handball.net (aus der URL)
    val teamId = "handball4all.westfalen.1309001"

    // Saison-Zeitraum
    val seasonFrom = "2025-07-01"
    val seasonTo = "2026-06-30"

    // Discord Channel (Name aus .env, z.B. DISCORD_WEBHOOK_HANDBALL)
    val discordChannel = "handball"

    // Prüfintervall in Minuten (für Observer-Modus)
    val checkIntervalMinutes = 60

    // Modus: "once" für Einzeldurchlauf, "observe" für kontinuierliche Überwachung
    val mode = "once"

    // ═══════════════════════════════════════════════════════════════════════════

    // Discord Webhook holen (optional)
    val webhookUrl = EnvConfig.discordWebhook(discordChannel)
    if (webhookUrl == null) {
        println("⚠️ Discord Webhook '$discordChannel' nicht konfiguriert")
        println("   Ausgabe nur auf Konsole")
    }

    // Modul erstellen
    val module = HandballModule(
        teamId = teamId,
        discordWebhookUrl = webhookUrl,
        seasonFrom = seasonFrom,
        seasonTo = seasonTo
    )

    // Shutdown Hook für sauberes Beenden
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        module.stop()
    })

    // Ausführen
    when (mode) {
        "observe" -> module.startObserver(checkIntervalMinutes)
        else -> module.run()
    }
}
