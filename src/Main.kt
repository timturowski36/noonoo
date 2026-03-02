import outputs.discord.DiscordBot
import sources.`pubg-api`.api.PubgApiClient
import sources.bf6.api.Bf6ApiClient
import sources.bf6.config.Bf6SnapshotManager
import sources.pubg.config.PubgConfigLoader

fun main() {
    println("═══════════════════════════════════════")
    println("       🎮 FeedKrake - Auto Stats")
    println("═══════════════════════════════════════")

    val bot = DiscordBot.create()
    val channel = "allgemein"
    val platform = "steam"
    val players = listOf("brotrustgaming", "philipnc", "chrissi1970")
    val bf6Players = listOf("brotrustgaming", "philipnc")
    val bf6Client = Bf6ApiClient()
    val bf6Snapshots = Bf6SnapshotManager()

    while (true) {
        val timestamp = java.time.LocalDateTime
            .now(java.time.ZoneId.of("Europe/Berlin"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        println("\n[$timestamp] ── Starte Stats-Update ──")

        val apiKey = PubgConfigLoader.loadApiKey()
        if (apiKey == null) {
            println("❌ API Key nicht gefunden. Nächster Versuch in 15 Minuten...")
            Thread.sleep(15 * 60 * 1_000L)
            continue
        }

        val client = PubgApiClient(apiKey)

        for (playerName in players) {
            println("\n── $playerName ──────────────────────────")

            val accountId = client.fetchAccountId(playerName, platform)
            if (accountId == null) {
                println("❌ Account-ID für $playerName nicht gefunden, überspringe...")
                continue
            }
            println("✅ Account-ID gefunden: $accountId")

            Thread.sleep(2_000L)

            val stats12h = client.fetchRecentStats(platform, accountId, hours = 12)
            println("12h Stats: ${stats12h?.extendedSummary() ?: "null"}")

            Thread.sleep(2_000L)

            val hoursWeek = calculateHoursSinceMonday()
            val statsWeek = client.fetchRecentStats(platform, accountId, hours = hoursWeek, maxMatches = 100)
            println("Wochen Stats: ${statsWeek?.extendedSummary() ?: "null"}")

            val message = buildString {
                appendLine("🎮 Player: $playerName (Steam)")
                appendLine()
                appendLine(
                    stats12h?.basicFormat("📊 Tagesstatistik:") ?: "📊 Tagesstatistik:\nKeine Matches in den letzten 12h"
                )
                appendLine()
                appendLine(
                    statsWeek?.basicFormat("📅 Wochenstatistik:") ?: "📅 Wochenstatistik:\nKeine Matches diese Woche"
                )
                appendLine()
                if (statsWeek != null) {
                    appendLine(statsWeek.weeklyExtras())
                    appendLine()
                }
                append("🕐 Stand: $timestamp")
            }

            println("📤 Sende Stats für $playerName an #$channel ...")
            val success = bot.sendMessageToChannel(channel, message)
            println(if (success) "✅ Gesendet." else "❌ Fehler beim Senden – Webhook prüfen.")

            // Kurze Pause zwischen den Spielern (nur Rate Limit, kein langes Warten mehr)
            if (playerName != players.last()) {
                println("⏸️ Warte 5 Sekunden vor dem nächsten Spieler...")
                Thread.sleep(5_000L)
            }
        }

        // Nach allen Spielern: 30 Minuten warten
        println("\n✅ Alle Spieler abgearbeitet. Nächstes Update in 30 Minuten...")
        Thread.sleep(30 * 60 * 1_000L)
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