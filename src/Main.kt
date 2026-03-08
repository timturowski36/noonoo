import config.EnvConfig
import sources.pubg.observer.PubgObserver

fun main() {
    // ═══════════════════════════════════════════════════════════════════════════
    // KONFIGURATION - Spielernamen hier eintragen!
    // ═══════════════════════════════════════════════════════════════════════════
    val players = listOf(
        "brotrustgaming",
        "philipnc"
    )

    // Platform: "steam", "xbox", "psn", "kakao", "stadia"
    val platform = "steam"

    // Alle X Minuten nach Aktivität prüfen
    val checkIntervalMinutes = 30

    // Discord Channel (Name aus .env, z.B. DISCORD_WEBHOOK_GAMING)
    val discordChannel = "gaming"
    // ═══════════════════════════════════════════════════════════════════════════

    println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake - PUBG Observer                     ║
    ╚═══════════════════════════════════════════════════════════════╝
    """.trimIndent())

    // Config laden
    if (!EnvConfig.load()) {
        println("❌ Konfiguration konnte nicht geladen werden!")
        return
    }

    // Discord Webhook holen
    val webhookUrl = EnvConfig.discordWebhook(discordChannel)
    if (webhookUrl == null) {
        println("❌ Discord Webhook '$discordChannel' nicht konfiguriert!")
        println("   Füge in .env hinzu: DISCORD_WEBHOOK_${discordChannel.uppercase()}=https://...")
        return
    }

    // Observer starten
    val observer = PubgObserver(
        players = players,
        platform = platform,
        discordWebhookUrl = webhookUrl,
        checkIntervalMinutes = checkIntervalMinutes
    )

    // Shutdown Hook für sauberes Beenden
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        observer.stop()
    })

    observer.start()
}
