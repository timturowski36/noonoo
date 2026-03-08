import config.EnvConfig
import outputs.discord.DiscordBot
import sources.bf6.api.Bf6ApiClient
import sources.bundesliga.api.TabellenApiClient
import sources.heise.api.HeiseRssClient
import sources.heise.model.HeiseFeed
import sources.`pubg-api`.api.PubgApiClient

fun main() {
    println("🐙 FeedKrake - Alle Module ausführen")
    EnvConfig.load()

    val discord = DiscordBot.create()
    val messages = mutableListOf<String>()

    // ═══════════════════════════════════════════════════════════════════════════
    // BF6 Stats (GameTools API - kein Key nötig)
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n── Battlefield 6 ──────────────────────────────")
    val bf6Client = Bf6ApiClient()

    val bf6Message = buildString {
        appendLine("**🪖 Battlefield 6 Stats**")

        val brotrust = bf6Client.fetchStats("brotrustgaming", "pc")
        if (brotrust != null) {
            appendLine()
            appendLine("**${brotrust.userName}**")
            appendLine("```")
            appendLine(brotrust.discordFormat())
            appendLine("```")
        } else {
            appendLine("❌ brotrustgaming nicht gefunden")
        }

        val philip = bf6Client.fetchStats("philipnc", "pc")
        if (philip != null) {
            appendLine()
            appendLine("**${philip.userName}**")
            appendLine("```")
            appendLine(philip.discordFormat())
            appendLine("```")
        } else {
            appendLine("❌ philipnc nicht gefunden")
        }
    }
    messages.add(bf6Message)

    // ═══════════════════════════════════════════════════════════════════════════
    // Bundesliga 2 - Schalke
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n── 2. Bundesliga ──────────────────────────────")
    val bundesligaClient = TabellenApiClient()
    val tabelle = bundesligaClient.fetchTabelle("bl2", "2025")

    val bundesligaMessage = buildString {
        appendLine("**⚽ 2. Bundesliga Tabelle**")
        appendLine("```")
        appendLine("Pl  Team                   Sp   S  U  N   Tore    Diff   Pkt")
        appendLine("─────────────────────────────────────────────────────────────")
        tabelle.forEach { t ->
            val team = t.teamName.padEnd(20).take(20)
            val tore = "${t.goals}:${t.opponentGoals}".padStart(6)
            val diff = (if (t.goalDiff >= 0) "+${t.goalDiff}" else "${t.goalDiff}").padStart(5)
            appendLine("${t.platz.toString().padStart(2)}  $team ${t.matches.toString().padStart(2)}  ${t.won.toString().padStart(2)} ${t.draw.toString().padStart(2)} ${t.lost.toString().padStart(2)}  $tore  $diff   ${t.points.toString().padStart(2)}")
        }
        appendLine("```")
    }
    messages.add(bundesligaMessage)

    // ═══════════════════════════════════════════════════════════════════════════
    // Heise News (RSS)
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n── Heise News ──────────────────────────────────")
    val heiseClient = HeiseRssClient()
    val heiseResult = heiseClient.fetchArticles(HeiseFeed.ALLE)

    val heiseMessage = buildString {
        appendLine("**📰 Heise News (Letzte 10)**")
        heiseResult.onSuccess { articles ->
            val filtered = articles
                .filter { !it.isSponsored }
                .take(10)
            filtered.forEach { article ->
                appendLine("• ${article.discordFormat()}")
            }
        }.onFailure { error ->
            appendLine("❌ Fehler: ${error.message}")
        }
    }
    messages.add(heiseMessage)

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBG Stats (nur wenn API Key vorhanden)
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n── PUBG ──────────────────────────────────────")
    val pubgKey = EnvConfig.pubgApiKey()
    if (pubgKey != null) {
        val pubgClient = PubgApiClient(pubgKey)
        val pubgPlayers = listOf("brotrustgaming", "philipnc")
        val platform = "steam"

        val pubgMessage = buildString {
            appendLine("**🎮 PUBG Stats (letzte 12h)**")

            pubgPlayers.forEach { playerName ->
                val accountId = pubgClient.fetchAccountId(playerName, platform)
                if (accountId != null) {
                    val stats = pubgClient.fetchRecentStats(platform, accountId, hours = 12)
                    if (stats != null && stats.matches > 0) {
                        appendLine()
                        appendLine("**$playerName**")
                        appendLine("```")
                        appendLine(stats.basicFormat("📊 Letzte 12 Stunden:"))
                        appendLine(stats.weeklyExtras())
                        appendLine("```")
                    } else if (stats != null) {
                        appendLine("**$playerName** - Keine Matches in den letzten 12h")
                    } else {
                        appendLine("❌ $playerName - Stats konnten nicht geladen werden")
                    }
                } else {
                    appendLine("❌ $playerName nicht gefunden")
                }
            }
        }
        messages.add(pubgMessage)
    } else {
        messages.add("**🎮 PUBG Stats**\n⚠️ Kein API Key konfiguriert")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // An Test-Channel senden (einzelne Nachrichten wegen 2000 Zeichen Limit)
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n── Discord Output ──────────────────────────────")
    println("📤 Sende ${messages.size} Nachrichten an Test-Channel...")

    // Header senden
    discord.sendMessageToChannel("test", "🐙 **FeedKrake - Live Daten**")
    Thread.sleep(500)

    // Jede Nachricht einzeln senden
    var successCount = 0
    messages.forEach { msg ->
        if (discord.sendMessageToChannel("test", msg)) {
            successCount++
        }
        Thread.sleep(500) // Rate Limit beachten
    }

    println("✅ $successCount/${messages.size} Nachrichten gesendet!")
}
