import outputs.discord.DiscordBot
import sources.`pubg-api`.api.PubgApiClient
import sources.pubg.config.PubgConfigLoader
import sources.claude.api.ClaudeApiClient
import sources.claude.config.ClaudeConfigLoader
import sources.claude.prompts.PromptContexts
import sources.claude.model.HandballSchedule
import sources.claude.model.HandballResults
import sources.claude.model.HandballTable
import sources.claude.model.ClaudeResponse
import sources.claude.cache.HandballCacheManager

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

    // Cache-Manager initialisieren
    val cache = HandballCacheManager()
    cache.printCacheStatus()

    // 1. API Key laden (nur wenn API-Aufrufe nötig)
    val needsApi = !cache.hasScheduleCache() || !cache.hasResultsCache() || !cache.hasTableCache()

    val client: ClaudeApiClient? = if (needsApi) {
        val apiKey = ClaudeConfigLoader.loadApiKey()
        if (apiKey == null) {
            println("❌ Claude API Key nicht gefunden!")
            println("   Bitte Key in: src/sources/claude/config/claude_api_key.txt ablegen")
            return
        }
        ClaudeApiClient(apiKey)
    } else {
        println("\n✅ Alle Daten aus Cache verfügbar - keine API-Aufrufe nötig!")
        null
    }

    val handballUrl = "https://www.handball.net/mannschaften/handball4all.westfalen.1309001/spielplan"
    val tableUrl = "https://www.handball.net/mannschaften/handball4all.westfalen.1309001/tabelle"

    // 2. Nächste Spiele abfragen (oder aus Cache laden)
    println("\n── Spielplan... ───────────────────────")
    val scheduleResponse: ClaudeResponse? = if (cache.hasScheduleCache()) {
        cache.loadScheduleJson()?.let { ClaudeResponse.fromCache(it) }
    } else {
        val response = client?.extractFromWebpage(
            url = handballUrl,
            context = PromptContexts.HANDBALL_SCHEDULE
        )
        response?.extractJsonBlock()?.let { cache.saveSchedule(it) }
        response
    }

    if (scheduleResponse != null) {
        val schedule = HandballSchedule.fromResponse(scheduleResponse)
        if (schedule != null) {
            println(schedule.discordFormat())
        } else {
            println("❌ Konnte Spielplan nicht parsen")
        }
    } else {
        println("❌ Keine Daten verfügbar")
    }

    // 3. Ergebnisse abfragen (oder aus Cache laden)
    println("\n── Ergebnisse... ──────────────────────")
    val resultsResponse: ClaudeResponse? = if (cache.hasResultsCache()) {
        cache.loadResultsJson()?.let { ClaudeResponse.fromCache(it) }
    } else {
        val response = client?.extractFromWebpage(
            url = handballUrl,
            context = PromptContexts.HANDBALL_RESULTS
        )
        response?.extractJsonBlock()?.let { cache.saveResults(it) }
        response
    }

    if (resultsResponse != null) {
        val results = HandballResults.fromResponse(resultsResponse)
        if (results != null) {
            println(results.discordFormat())
        } else {
            println("❌ Konnte Ergebnisse nicht parsen")
        }
    } else {
        println("❌ Keine Daten verfügbar")
    }

    // 4. Tabelle abfragen (oder aus Cache laden)
    println("\n── Tabelle... ─────────────────────────")
    val tableResponse: ClaudeResponse? = if (cache.hasTableCache()) {
        cache.loadTableJson()?.let { ClaudeResponse.fromCache(it) }
    } else {
        val response = client?.extractFromWebpage(
            url = tableUrl,
            context = PromptContexts.HANDBALL_TABLE
        )
        response?.extractJsonBlock()?.let { cache.saveTable(it) }
        response
    }

    if (tableResponse != null) {
        val table = HandballTable.fromResponse(tableResponse)
        if (table != null) {
            println(table.discordFormat())
        } else {
            println("❌ Konnte Tabelle nicht parsen")
        }
    } else {
        println("❌ Keine Daten verfügbar")
    }

    // 5. Kosten-Zusammenfassung (nur für API-Aufrufe)
    val apiResponses = listOfNotNull(scheduleResponse, resultsResponse, tableResponse)
        .filter { !it.fromCache }

    if (apiResponses.isNotEmpty()) {
        println("\n── Kosten-Zusammenfassung ─────────────")
        val totalInput = apiResponses.sumOf { it.inputTokens }
        val totalOutput = apiResponses.sumOf { it.outputTokens }

        println("Input Tokens:  $totalInput")
        println("Output Tokens: $totalOutput")
        println("Geschätzte Kosten (Sonnet): ~$${"%.4f".format((totalInput * 3.0 + totalOutput * 15.0) / 1_000_000)}")
        println("Geschätzte Kosten (Haiku):  ~$${"%.4f".format((totalInput * 0.8 + totalOutput * 4.0) / 1_000_000)}")
    } else {
        println("\n── Keine API-Kosten (alles aus Cache) ─")
    }

    println("\n✅ Test abgeschlossen!")
}