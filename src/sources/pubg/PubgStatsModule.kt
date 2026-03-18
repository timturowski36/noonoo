package sources.pubg

import config.EnvConfig
import scheduler.ScheduledModule
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.model.PlayerStats

/**
 * Einmaliger PUBG-Stats-Abruf als ScheduledModule (Testmodus).
 *
 * Holt für jeden Spieler 24h- und Wochenstats und gibt sie als
 * formatierten String zurück – ohne Discord, ohne Polling.
 *
 * @param players  Liste der PUBG-Spielernamen
 * @param platform z.B. "steam", "xbox", "psn"
 */
class PubgStatsModule(
    private val players: List<String>,
    private val platform: String = "steam"
) : ScheduledModule {

    override val name = "PUBG: Spielerstatistiken"

    override fun execute(): String? {
        val apiKey = EnvConfig.pubgApiKey() ?: run {
            println("❌ [$name] PUBG_API_KEY nicht in .env konfiguriert!")
            return null
        }

        val client = PubgApiClient(apiKey)
        val results = mutableListOf<String>()

        for (playerName in players) {
            println("🔍 [$name] Lade Stats für '$playerName'...")

            val accountId = client.fetchAccountId(playerName, platform) ?: run {
                println("   ❌ Account-ID nicht gefunden")
                results += "❌ **$playerName** – nicht gefunden"
                continue
            }

            val dailyStats  = client.fetchRecentStats(platform, accountId, hours = 24)
            val weeklyStats = client.fetchWeeklyStats(platform, accountId)

            results += formatPlayer(playerName, dailyStats, weeklyStats)
        }

        return results.joinToString("\n\n")
    }

    private fun formatPlayer(
        playerName: String,
        dailyStats: PlayerStats?,
        weeklyStats: PlayerStats?
    ): String = buildString {
        appendLine("🎮 **$playerName** (${platform.replaceFirstChar { it.uppercase() }})")

        if (dailyStats != null && dailyStats.matches > 0) {
            appendLine("📊 **Heute (24h):** ${formatStats(dailyStats)}")
            appendLine(formatExtras(dailyStats))
        } else {
            appendLine("📊 **Heute (24h):** keine Matches")
        }

        if (weeklyStats != null && weeklyStats.matches > 0) {
            appendLine("📅 **Woche:** ${formatStats(weeklyStats)}")
            append(formatExtras(weeklyStats))
        } else {
            append("📅 **Woche:** keine Matches")
        }
    }

    private fun formatStats(s: PlayerStats): String {
        val wins = if (s.wins == 0) "-" else "${s.wins}"
        return "Matches: ${s.matches}  Wins: $wins  K/D: ${s.kdFormatted()}  Kills: ${s.kills}  Assists: ${s.assists}  Ø Schaden: ${s.avgDamageFormatted()}"
    }

    private fun formatExtras(s: PlayerStats): String = buildString {
        appendLine("   Weitester Kill: ${"%.0f".format(s.longestKill)}m  |  Headshots: ${s.headshotKills} (${"%.0f".format(s.headshotRate)}%)")
        append("   Revives: ${s.revives}  |  Knockdowns: ${s.knockdowns}  |  Top 10: ${s.topTens} (${"%.0f".format(s.topTenRate)}%)")
    }
}
