import domain.model.QueryResult
import outputs.discord.DiscordBot
import sources.pubg.model.PubgSettings
import sources.pubg.queries.AccountIdQuery
import sources.pubg.queries.AccountIdQuerySettings
import sources.pubg.queries.Last12hStatsQuery
import sources.pubg.queries.Last12hStatsQuerySettings
import sources.pubg.queries.TotalWinsQuery
import sources.pubg.queries.TotalWinsQuerySettings

suspend fun main() {

    val pubgSettings = PubgSettings(
        playerName = "philip_nc",
        platform = "psn",
        apiKey = ""
    )

    // Schritt 1: Account-ID auflösen (muss zuerst laufen)
    val accountIdQuery = AccountIdQuery()
    val accountIdResult = accountIdQuery.execute(pubgSettings, AccountIdQuerySettings())

    if (accountIdResult is QueryResult.Success) {
        val accountId = accountIdResult.data.accountId

        // Schritt 2: Lifetime Wins
        val winsQuery = TotalWinsQuery()
        val winsResult = winsQuery.execute(
            pubgSettings,
            TotalWinsQuerySettings(accountId = accountId)
        )

        // Schritt 3: Letzte 12h Stats
        val statsQuery = Last12hStatsQuery()
        val statsResult = statsQuery.execute(
            pubgSettings,
            Last12hStatsQuerySettings(accountId = accountId)
        )

        // ─── Konsolen-Ausgabe ──────────────────────────────────────────────

        println("\n=== PUBG STATS ===")

        var lifetimeWins: Int? = null
        var statsMessage: String? = null

        when (winsResult) {
            is QueryResult.Success -> {
                lifetimeWins = winsResult.data
                println("🏆 Lifetime Wins: $lifetimeWins")
            }
            is QueryResult.Error   -> println("❌ ${winsResult.message}")
            is QueryResult.Loading -> println("⏳ Lädt...")
        }

        when (statsResult) {
            is QueryResult.Success -> {
                val s = statsResult.data
                statsMessage = "${s.matches} Matches | ${s.wins} Wins | ${s.kills} Kills | K/D: ${s.kdFormatted()}"
                println("📊 Letzte 12h: $statsMessage")
                println("📝 Summary: ${s.summary()}")
            }
            is QueryResult.Error   -> println("❌ ${statsResult.message}")
            is QueryResult.Loading -> println("⏳ Lädt...")
        }

        // ─── Discord-Ausgabe ───────────────────────────────────────────────

        println("\n=== DISCORD BOT ===")

        val bot = DiscordBot.create()
        if (bot != null) {
            // Channels an die gesendet werden soll (Dateiname ohne .txt)
            val channels = listOf("Allgemein", "Stats")

            // Nachricht zusammenbauen
            val discordMessage = buildString {
                appendLine("🎮 **PUBG Stats Update**")
                appendLine()
                if (lifetimeWins != null) {
                    appendLine("🏆 **Lifetime Wins:** $lifetimeWins")
                }
                if (statsMessage != null) {
                    appendLine("📊 **Letzte 12h:** $statsMessage")
                }
            }

            // An alle konfigurierten Channels senden
            bot.sendMessageToChannels(channels, discordMessage)
        } else {
            println("⚠️ Discord Bot nicht konfiguriert - überspringe")
        }

    } else if (accountIdResult is QueryResult.Error) {
        println("❌ PUBG: ${accountIdResult.message}")
    }
}