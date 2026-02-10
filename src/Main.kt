import outputs.discord.DiscordBot
import sources.pubg.config.PubgConfigLoader
import sources.`pubg-api`.api.PubgApiClient

fun main() {
    println("═══════════════════════════════════════")
    println("         🎮 FeedKrake - PUBG Stats")
    println("═══════════════════════════════════════")
    println()

    // 1. API Key laden
    val apiKey = PubgConfigLoader.loadApiKey()
    if (apiKey == null) {
        println("Programm beendet.")
        return
    }

    val client = PubgApiClient(apiKey)
    val platform = "steam"
    val playerName = "brotrustgaming"

    // 2. Account-ID abrufen
    println()
    println("── Account-ID ──────────────────────────")
    val accountId = client.fetchAccountId(playerName, platform)
    if (accountId == null) {
        println("Programm beendet.")
        return
    }

    // 3. Lifetime Wins abrufen
    println()
    println("── Lifetime Stats ──────────────────────")
    val lifetimeWins = client.fetchLifetimeWins(platform, accountId)
    if (lifetimeWins != null) {
        println("🏆 Gesamt-Wins: $lifetimeWins")
    }

    // 4. Letzte 12 Stunden Stats
    println()
    println("── Letzte 12 Stunden ───────────────────")
    val stats12h = client.fetchRecentStats(platform, accountId, hours = 12)
    if (stats12h != null) {
        println("📊 ${stats12h.extendedSummary()}")
    }

    // 5. Wochenbericht (seit Montag 6 Uhr)
    println()
    println("── Wochenbericht ───────────────────────")
    val hoursSinceMonday = calculateHoursSinceMonday()
    val statsWeek = client.fetchRecentStats(platform, accountId, hours = hoursSinceMonday, maxMatches = 100)
    if (statsWeek != null) {
        println("📊 ${statsWeek.extendedSummary()}")
    }

    println()
    println("═══════════════════════════════════════")
    println("         ✅ Fertig!")
    println("═══════════════════════════════════════")

    // ─── Discord-Ausgabe ───────────────────────────────────────────────

    println("\n=== DISCORD BOT ===")

    val bot = DiscordBot.create()
    if (bot != null) {
        // Channels an die gesendet werden soll (Dateiname ohne .txt)
        val channels = listOf("allgemein")

        // Nachricht zusammenbauen
        val discordMessage = buildString {
            appendLine("🎮 **PUBG Stats Update**")
            appendLine()
            if (lifetimeWins != null) {
                appendLine("🏆 **Lifetime Wins:** $lifetimeWins")
            }
            if ("sdfasd" != null) {
                appendLine("📊 **Letzte 12h:** ")
            }
        }

        // An alle konfigurierten Channels senden
        bot.sendMessageToChannels(channels, discordMessage)
    } else {
        println("⚠️ Discord Bot nicht konfiguriert - überspringe")
    }
}

fun calculateHoursSinceMonday(): Int {
    val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Berlin"))
    val lastMonday = if (now.dayOfWeek == java.time.DayOfWeek.MONDAY && now.hour >= 6) {
        now.toLocalDate()
    } else {
        now.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    }
    val weekStart = lastMonday.atTime(6, 0)
    return java.time.temporal.ChronoUnit.HOURS.between(weekStart, now).toInt().coerceAtLeast(1)
}