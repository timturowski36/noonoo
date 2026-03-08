import outputs.discord.DiscordBot
import sources.`pubg-api`.api.PubgApiClient
import sources.bf6.api.Bf6ApiClient
import sources.bf6.config.Bf6SnapshotManager
import sources.pubg.config.PubgConfigLoader
import sources.claude.api.ClaudeApiClient
import sources.claude.config.ClaudeConfigLoader
import sources.claude.prompts.PromptContexts
import sources.claude.model.HandballSchedule
import sources.claude.model.HandballResults
import sources.claude.model.HandballTable
import sources.claude.model.ClaudeResponse
import sources.claude.cache.HandballCacheManager
import scheduler.FeedKrakeScheduler
import scheduler.config.FeedKrakeConfig
import scheduler.api.FeedKrakeApiServer
import test.ModuleTestRunner

fun main(args: Array<String>) {
    // ══════════════════════════════════════════════════════════════════════════
    // Kommandozeilen-Argumente prüfen
    // ══════════════════════════════════════════════════════════════════════════
    when (args.getOrNull(0)) {
        "--scheduler", "-s" -> {
            startScheduler()
            return
        }
        "--api" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
            startApiServer(port)
            return
        }
        "--server" -> {
            // Beides: Scheduler + API
            val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
            startFullServer(port)
            return
        }
        "--config", "-c" -> {
            showConfig()
            return
        }
        "--help", "-h" -> {
            printHelp()
            return
        }
        "--pubg" -> {
            runPubgLoop()
            return
        }
        "--handball" -> {
            testClaudeHandball()
            return
        }
        "--test" -> {
            ModuleTestRunner.runAllTests()
            return
        }
    }

    // Standard: Interaktives Menü
    interactiveMenu()
}

// ══════════════════════════════════════════════════════════════════════════════
// Interaktives Menü
// ══════════════════════════════════════════════════════════════════════════════

fun interactiveMenu() {
    while (true) {
        println("""

    ╔═══════════════════════════════════════╗
    ║        🐙 FeedKrake - Menü            ║
    ╠═══════════════════════════════════════╣
    ║  1. 🕐 Scheduler starten              ║
    ║  2. 🌐 API Server starten             ║
    ║  3. 🚀 Vollständiger Server           ║
    ║     (Scheduler + API)                 ║
    ║                                       ║
    ║  4. 📋 Konfiguration anzeigen         ║
    ║  5. 🤾 Handball Test                  ║
    ║  6. 🎮 PUBG Stats Loop                ║
    ║  7. 🧪 Alle Module testen             ║
    ║                                       ║
    ║  0. Beenden                           ║
    ╚═══════════════════════════════════════╝

    Auswahl: """.trimIndent())

        when (readlnOrNull()?.trim()) {
            "1" -> startScheduler()
            "2" -> startApiServer()
            "3" -> startFullServer()
            "4" -> showConfig()
            "5" -> testClaudeHandball()
            "6" -> runPubgLoop()
            "7" -> ModuleTestRunner.runAllTests()
            "0", "exit" -> {
                println("Auf Wiedersehen!")
                return
            }
            else -> println("❌ Ungültige Auswahl")
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Scheduler Funktionen
// ══════════════════════════════════════════════════════════════════════════════

fun startScheduler() {
    println("🚀 Starte FeedKrake Scheduler...")
    val scheduler = FeedKrakeScheduler()

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n👋 Scheduler wird beendet...")
        scheduler.stop()
    })

    scheduler.start()
}

fun startApiServer(port: Int = 8080) {
    println("🌐 Starte FeedKrake API Server auf Port $port...")
    val apiServer = FeedKrakeApiServer(port)

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n👋 API Server wird beendet...")
        apiServer.stop()
    })

    apiServer.start()

    // Halte den Thread am Leben
    Thread.currentThread().join()
}

fun startFullServer(port: Int = 8080) {
    println("🚀 Starte FeedKrake (Scheduler + API)...")

    val scheduler = FeedKrakeScheduler()
    val apiServer = FeedKrakeApiServer(port, scheduler)

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n👋 FeedKrake wird beendet...")
        apiServer.stop()
        scheduler.stop()
    })

    // API Server im Hintergrund starten
    Thread { apiServer.start() }.start()

    // Scheduler im Vordergrund
    scheduler.start()
}

fun showConfig() {
    println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake Konfiguration                       ║
    ╚═══════════════════════════════════════════════════════════════╝
    """.trimIndent())

    val config = FeedKrakeConfig.load()

    println("\n📡 Discord Channels (${config.channels.size}):")
    config.channels.forEach { (name, _) ->
        println("   • $name")
    }

    println("\n📋 Jobs (${config.jobs.size}):")
    config.jobs.forEach { job ->
        println("   • ${job.name}: ${job.module} → #${job.channel} (${job.schedule})")
    }

    println("\n📝 Konfiguration bearbeiten: feedkrake.config")
}

fun printHelp() {
    println("""

    🐙 FeedKrake - Hilfe
    ════════════════════════════════════════════════════════════════

    Verwendung:
      java -jar FeedKrake.jar [OPTION]

    Optionen:
      --scheduler, -s    Scheduler starten (Dauerbetrieb)
      --api [PORT]       Nur API Server starten (Standard: 8080)
      --server [PORT]    Scheduler + API Server (empfohlen!)
      --config, -c       Konfiguration anzeigen
      --test             Alle Module testen (sendet an #test)
      --handball         Handball Test ausführen
      --pubg             PUBG Stats Loop starten
      --help, -h         Diese Hilfe anzeigen

    Ohne Argumente:
      Startet das interaktive Menü

    Konfiguration:
      Alle Einstellungen in: feedkrake.config

      [channels]  - Discord Webhook URLs
      [jobs]      - Automatische Jobs mit Zeitplan

    Beispiel feedkrake.config:
    ────────────────────────────────────────────────────────────────
    [channels]
    news = https://discord.com/api/webhooks/...

    [jobs]
    Morgennews | heise.news | täglich 08:00 | news | max=10
    ────────────────────────────────────────────────────────────────

    """.trimIndent())
}

// ══════════════════════════════════════════════════════════════════════════════
// PUBG Stats Loop
// ══════════════════════════════════════════════════════════════════════════════

fun runPubgLoop() {
    println("═══════════════════════════════════════")
    println("       🎮 FeedKrake - Auto Stats")
    println("═══════════════════════════════════════")

    val bot = DiscordBot.create()
    val channel = "allgemein"
    val platform = "steam"
    val players = listOf("brotrustgaming", "philipnc", "chrissi1970")

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

            if (playerName != players.last()) {
                println("⏸️ Warte 5 Sekunden vor dem nächsten Spieler...")
                Thread.sleep(5_000L)
            }
        }

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

// ══════════════════════════════════════════════════════════════════════════════
// Claude API + Handball Test
// ══════════════════════════════════════════════════════════════════════════════

fun testClaudeHandball() {
    println("═══════════════════════════════════════")
    println("   🤾 Claude API + Handball Test")
    println("═══════════════════════════════════════")

    val cache = HandballCacheManager()
    cache.printCacheStatus()

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

    println("\n── Spielplan... ───────────────────────")
    val scheduleResponse: ClaudeResponse? = if (cache.hasScheduleCache()) {
        cache.loadScheduleJson()?.let { ClaudeResponse.fromCache(it) }
    } else {
        val response = client?.extractFromWebpage(url = handballUrl, context = PromptContexts.HANDBALL_SCHEDULE)
        response?.extractJsonBlock()?.let { cache.saveSchedule(it) }
        response
    }

    if (scheduleResponse != null) {
        val schedule = HandballSchedule.fromResponse(scheduleResponse)
        if (schedule != null) println(schedule.discordFormat())
        else println("❌ Konnte Spielplan nicht parsen")
    } else {
        println("❌ Keine Daten verfügbar")
    }

    println("\n── Ergebnisse... ──────────────────────")
    val resultsResponse: ClaudeResponse? = if (cache.hasResultsCache()) {
        cache.loadResultsJson()?.let { ClaudeResponse.fromCache(it) }
    } else {
        val response = client?.extractFromWebpage(url = handballUrl, context = PromptContexts.HANDBALL_RESULTS)
        response?.extractJsonBlock()?.let { cache.saveResults(it) }
        response
    }

    if (resultsResponse != null) {
        val results = HandballResults.fromResponse(resultsResponse)
        if (results != null) println(results.discordFormat())
        else println("❌ Konnte Ergebnisse nicht parsen")
    } else {
        println("❌ Keine Daten verfügbar")
    }

    println("\n── Tabelle... ─────────────────────────")
    val tableResponse: ClaudeResponse? = if (cache.hasTableCache()) {
        cache.loadTableJson()?.let { ClaudeResponse.fromCache(it) }
    } else {
        val response = client?.extractFromWebpage(url = tableUrl, context = PromptContexts.HANDBALL_TABLE)
        response?.extractJsonBlock()?.let { cache.saveTable(it) }
        response
    }

    if (tableResponse != null) {
        val table = HandballTable.fromResponse(tableResponse)
        if (table != null) println(table.discordFormat())
        else println("❌ Konnte Tabelle nicht parsen")
    } else {
        println("❌ Keine Daten verfügbar")
    }

    val apiResponses = listOfNotNull(scheduleResponse, resultsResponse, tableResponse).filter { !it.fromCache }

    if (apiResponses.isNotEmpty()) {
        println("\n── Kosten-Zusammenfassung ─────────────")
        val totalInput = apiResponses.sumOf { it.inputTokens }
        val totalOutput = apiResponses.sumOf { it.outputTokens }
        println("Input Tokens:  $totalInput")
        println("Output Tokens: $totalOutput")
        println("Geschätzte Kosten (Sonnet): ~${"%.4f".format((totalInput * 3.0 + totalOutput * 15.0) / 1_000_000)}")
    } else {
        println("\n── Keine API-Kosten (alles aus Cache) ─")
    }

    println("\n✅ Test abgeschlossen!")
}
