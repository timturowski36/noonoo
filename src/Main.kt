import domain.model.QueryResult
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

        // ─── Ausgabe ────────────────────────────────────────────────────────

        println("\n=== PUBG STATS ===")

        when (winsResult) {
            is QueryResult.Success -> println("🏆 Lifetime Wins: ${winsResult.data}")
            is QueryResult.Error   -> println("❌ ${winsResult.message}")
            is QueryResult.Loading -> println("⏳ Lädt...")
        }

        when (statsResult) {
            is QueryResult.Success -> {
                val s = statsResult.data
                println("📊 Letzte 12h: ${s.matches} Matches | ${s.wins} Wins | ${s.kills} Kills | K/D: ${s.kdFormatted()}")
                println("📝 Summary: ${s.summary()}")
            }
            is QueryResult.Error   -> println("❌ ${statsResult.message}")
            is QueryResult.Loading -> println("⏳ Lädt...")
        }

    } else if (accountIdResult is QueryResult.Error) {
        println("❌ PUBG: ${accountIdResult.message}")
    }
}