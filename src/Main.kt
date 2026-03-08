import config.EnvConfig
import outputs.discord.DiscordBot
import sources.bf6.api.Bf6ApiClient
import sources.bundesliga.api.TabellenApiClient
import sources.heise.api.HeiseRssClient
import sources.heise.model.HeiseFeed

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
        // TODO: PUBG API aufrufen wenn Key vorhanden
        messages.add("**🎮 PUBG Stats**\n✅ API Key konfiguriert - Stats werden geladen...")
    } else {
        messages.add("**🎮 PUBG Stats**\n⚠️ Kein API Key konfiguriert")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // An Test-Channel senden
    // ═══════════════════════════════════════════════════════════════════════════
    println("\n── Discord Output ──────────────────────────────")

    val fullMessage = buildString {
        appendLine("🐙 **FeedKrake - Live Daten**")
        appendLine()
        messages.forEach { msg ->
            append(msg)
            appendLine()
        }
    }

    println("\n📤 Sende an Test-Channel...")
    val success = discord.sendMessageToChannel("test", fullMessage)

    if (success) {
        println("✅ Erfolgreich gesendet!")
    } else {
        println("❌ Fehler beim Senden - Webhook konfiguriert?")
        println("\n📝 Ausgabe (lokal):")
        println(fullMessage)
    }
}
