import domain.model.QueryResult
import outputs.discord.DiscordBot
import outputs.discord.renderers.BundesligaDiscordRenderer
import sources.bundesliga.BundesligaModule
import sources.bundesliga.model.BundesligaSettings
import sources.bundesliga.queries.NaechsteSpieleQuery
import sources.bundesliga.queries.NaechsteSpieleQuerySettings
import sources.bundesliga.queries.TabelleQuery
import sources.bundesliga.queries.TabelleQuerySettings
import sources.bundesliga.queries.Top4TabellenQuerySettings
import sources.bundesliga.queries.Top5TabellenQuery
import sources.pubg.model.PubgSettings
import sources.pubg.queries.AccountIdQuery
import sources.pubg.queries.AccountIdQuerySettings
import sources.pubg.queries.Last12hStatsQuery
import sources.pubg.queries.Last12hStatsQuerySettings
import sources.pubg.queries.TotalWinsQuery
import sources.pubg.queries.TotalWinsQuerySettings

suspend fun main() {

    println("═══════════════════════════════════════")
    println("       🎮 FeedKrake - Discord Test")
    println("═══════════════════════════════════════")

    // ─── Discord Webhook Test ────────────────────────────────────────────

    println("\n=== DISCORD WEBHOOK TEST ===")

    val bot = DiscordBot.create()

    // Channel-Name = Dateiname ohne .txt
    // allgemein → src/outputs/discord/config/allgemein.txt
    val channel = "allgemein"

    // Test-Nachricht
    val testMessage = buildString {
        appendLine("🎮 **PUBG Stats Update** - Test")
        appendLine()
        appendLine("👤 **Spieler:** brotrustgaming")
        appendLine("🖥️ **Plattform:** Steam")
        appendLine()
        appendLine("🏆 **Lifetime Wins:** 42")
        appendLine("📊 **Letzte 12h:** 5 Matches | 2 Wins | 15 Kills | K/D: 3.75")
        appendLine()
        appendLine("✅ *Dies ist eine Testnachricht von FeedKrake*")
    }

    println("📤 Sende Testnachricht an '$channel'...")
    val success = bot.sendMessageToChannel(channel, testMessage)

    if (success) {
        println("✅ Test erfolgreich! Nachricht wurde an Discord gesendet.")
    } else {
        println("❌ Test fehlgeschlagen. Prüfe die Webhook-URL in allgemein.txt")
    }

    println()
    println("═══════════════════════════════════════")
}