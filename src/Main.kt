import outputs.discord.DiscordBot
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.config.PubgConfigLoader
import sources.claude.api.ClaudeApiClient
import sources.claude.config.ClaudeConfigLoader
import sources.claude.prompts.PromptContexts
import sources.claude.model.HandballSchedule
import sources.claude.model.HandballResults
import sources.claude.model.HandballTable

fun main() {
    // ══════════════════════════════════════════════════════════════════════════
    // Claude API + Handball Test (zum Testen auskommentieren/einkommentieren)
    // ══════════════════════════════════════════════════════════════════════════
    testClaudeHandball()
    return  // Beende hier für den Test, entfernen für normalen Betrieb

    // ══════════════════════════════════════════════════════════════════════════
    // PUBG Stats Loop (normaler Betrieb)
    // ══════════════════════════════════════════════════════════════════════════
    println("═══════════════════════════════════════")
    println("       🎮 FeedKrake - Auto Stats")
    println("═══════════════════════════════════════")

    val bot = DiscordBot.create()
    val channel = "allgemein"
    val platform = "steam"
    val players = listOf("brotrustgaming", "philipnc")

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

            // Kurze Pause zwischen den API-Calls (Rate Limit)
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
                appendLine(stats12h?.basicFormat("📊 Tagesstatistik:") ?: "📊 Tagesstatistik:\nKeine Matches in den letzten 12h")
                appendLine()
                appendLine(statsWeek?.basicFormat("📅 Wochenstatistik:") ?: "📅 Wochenstatistik:\nKeine Matches diese Woche")
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

            // Pause zwischen den Spielern
            if (playerName != players.last()) {
                println("⏸️ Warte 5 Sekunden vor dem nächsten Spieler...")
                Thread.sleep(5_000L)
            }
        }

        println("\n⏳ Nächster Durchlauf in 15 Minuten...")
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

// ══════════════════════════════════════════════════════════════════════════════
// Claude API + Handball Test
// ══════════════════════════════════════════════════════════════════════════════

fun testClaudeHandball() {
    println("═══════════════════════════════════════")
    println("   🤾 Claude API + Handball Test")
    println("═══════════════════════════════════════")

    // 1. API Key laden
    val apiKey = ClaudeConfigLoader.loadApiKey()
    if (apiKey == null) {
        println("❌ Claude API Key nicht gefunden!")
        println("   Bitte Key in: src/sources/claude/config/claude_api_key.txt ablegen")
        return
    }

    val client = ClaudeApiClient(apiKey)
    val handballUrl = "https://www.handball.net/mannschaften/handball4all.westfalen.1309001/spielplan"

    // 2. Nächste Spiele abfragen
    println("\n── Lade Spielplan... ──────────────────")
    val scheduleResponse = client.extractFromWebpage(
        url = handballUrl,
        context = PromptContexts.HANDBALL_SCHEDULE
    )

    if (scheduleResponse != null) {
        println("\n📋 Raw Response (gekürzt):")
        println(scheduleResponse.text.take(500) + "...")

        val schedule = HandballSchedule.fromResponse(scheduleResponse)
        if (schedule != null) {
            println("\n" + schedule.discordFormat())
        } else {
            println("❌ Konnte Spielplan nicht parsen")
        }
    } else {
        println("❌ Keine Antwort erhalten")
    }

    // 3. Ergebnisse abfragen
    println("\n── Lade Ergebnisse... ─────────────────")
    val resultsResponse = client.extractFromWebpage(
        url = handballUrl,
        context = PromptContexts.HANDBALL_RESULTS
    )

    if (resultsResponse != null) {
        val results = HandballResults.fromResponse(resultsResponse)
        if (results != null) {
            println("\n" + results.discordFormat())
        } else {
            println("❌ Konnte Ergebnisse nicht parsen")
        }
    }

    // 4. Tabelle abfragen (andere URL)
    println("\n── Lade Tabelle... ────────────────────")
    val tableUrl = "https://www.handball.net/mannschaften/handball4all.westfalen.1309001/tabelle"
    val tableResponse = client.extractFromWebpage(
        url = tableUrl,
        context = PromptContexts.HANDBALL_TABLE
    )

    if (tableResponse != null) {
        val table = HandballTable.fromResponse(tableResponse)
        if (table != null) {
            println("\n" + table.discordFormat())
        } else {
            println("❌ Konnte Tabelle nicht parsen")
        }
    }

    // 5. Token-Kosten anzeigen
    println("\n── Kosten-Zusammenfassung ─────────────")
    val totalInput = listOfNotNull(scheduleResponse, resultsResponse, tableResponse)
        .sumOf { it.inputTokens }
    val totalOutput = listOfNotNull(scheduleResponse, resultsResponse, tableResponse)
        .sumOf { it.outputTokens }

    println("Input Tokens:  $totalInput")
    println("Output Tokens: $totalOutput")
    println("Geschätzte Kosten (Sonnet): ~$${"%.4f".format((totalInput * 3.0 + totalOutput * 15.0) / 1_000_000)}")
    println("Geschätzte Kosten (Haiku):  ~$${"%.4f".format((totalInput * 0.8 + totalOutput * 4.0) / 1_000_000)}")

    println("\n✅ Test abgeschlossen!")
}