import config.EnvConfig
import java.lang.IO.println

fun main(args: Array<String>) {
    println("═══════════════════════════════════════")
    println("   🐙 FeedKrake - Config Demo")
    println("═══════════════════════════════════════")

    // Konfiguration laden und Status anzeigen
    EnvConfig.load()
    EnvConfig.printStatus()

    // Beispiel: Discord Webhook abrufen
    println("\n── Test: Discord Webhook ──────────────")
    val gamingWebhook = EnvConfig.discordWebhook("test")
    if (gamingWebhook != null) {
        println("✅ Gaming-Webhook geladen")
    }

    // Beispiel: API Keys prüfen
    println("\n── Test: API Keys ─────────────────────")
    if (EnvConfig.pubgApiKey() != null) {
        println("✅ PUBG API Key verfügbar")
    }
    if (EnvConfig.claudeApiKey() != null) {
        println("✅ Claude API Key verfügbar")
    }

    println("\n═══════════════════════════════════════")
    println("   ✅ Config-Test abgeschlossen")
    println("═══════════════════════════════════════")
}
