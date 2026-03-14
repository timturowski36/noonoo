import config.EnvConfig
import config.modules.BundesligaModuleConfig
import config.modules.PubgObserverModuleConfig
import scheduler.BundesligaTableModule
import scheduler.CombinedObserver
import scheduler.HandballResultsModule
import scheduler.HandballTableModule
import scheduler.HandballUpcomingModule
import scheduler.MultiModuleScheduler
import scheduler.TagesschauNewsModule
import sources.handball.observer.HandballModule
import sources.pubg.observer.PubgObserver
import kotlin.concurrent.thread

fun main() {
    println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake - Multi-Module                      ║
    ╚═══════════════════════════════════════════════════════════════╝
    """.trimIndent())

    // Config laden
    if (!EnvConfig.load()) {
        println("❌ Konfiguration konnte nicht geladen werden!")
        return
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODUS AUSWÄHLEN
    // ═══════════════════════════════════════════════════════════════════════════
    // "combined"   = PUBG prüfen + Stats alle 30 Min wenn aktiv + Module zeitversetzt
    // "example"    = Einmaliger Testlauf aller Module – Ausgabe nur in Konsole, kein Discord
    // "scheduler"  = Multi-Module Scheduler (primär/sekundär versetzt)
    // "observer"   = Alte Observer (separate Threads)
    // "single"     = Einmaliger Durchlauf
    val mode = "combined"

    when (mode) {
        "combined" -> {
            val pubgConfig = PubgObserverModuleConfig.load()
            val bl1Config = BundesligaModuleConfig.load("config/modules/bundesliga_1.conf")
            val bl2Config = BundesligaModuleConfig.load("config/modules/bundesliga_2.conf")
            runCombinedMode(pubgConfig, bl1Config, bl2Config)
        }
        "example" -> {
            val pubgConfig = PubgObserverModuleConfig.load()
            val bl1Config = BundesligaModuleConfig.load("config/modules/bundesliga_1.conf")
            val bl2Config = BundesligaModuleConfig.load("config/modules/bundesliga_2.conf")
            runExampleMode(pubgConfig, bl1Config, bl2Config)
        }
        "scheduler" -> runSchedulerMode(
            handballTeamId = "handball4all.westfalen.1309001",
            handballTeamName = "HSG RE/OE",
            discordChannel = "allgemein"
        )
        "observer" -> runObserverMode(
            pubgPlayers = listOf("brotrustgaming", "philipnc", "chrissi1970"),
            pubgPlatform = "steam",
            pubgCheckIntervalMinutes = 30,
            pubgDiscordChannel = "allgemein",
            handballTeamId = "handball4all.westfalen.1309001",
            handballSeasonFrom = "2025-07-01",
            handballSeasonTo = "2026-06-30",
            handballDiscordChannel = "allgemein",
            handballCheckIntervalMinutes = 60
        )
        else -> runSingleMode(
            handballTeamId = "handball4all.westfalen.1309001",
            handballSeasonFrom = "2025-07-01",
            handballSeasonTo = "2026-06-30",
            handballDiscordChannel = "allgemein"
        )
    }
}

/**
 * Example-Modus: Führt alle konfigurierten Module einmalig aus und gibt die
 * Ergebnisse in der Konsole aus. Kein Discord-Versand. Gut zum Testen.
 */
fun runExampleMode(
    pubgConfig: PubgObserverModuleConfig,
    bl1Config: BundesligaModuleConfig,
    bl2Config: BundesligaModuleConfig
) {
    println("\n--- EXAMPLE MODUS (kein Discord) ---\n")

    // PUBG Stats (ersten Spieler aus der Config)
    val firstPlayer = pubgConfig.players.firstOrNull()
    if (firstPlayer != null) {
        println("=== PUBG: $firstPlayer ===")
        val apiKey = config.EnvConfig.pubgApiKey()
        if (apiKey != null) {
            val client = sources.`pubg-api`.api.PubgApiClient(apiKey)
            val accountId = client.fetchAccountId(firstPlayer, pubgConfig.platform)
            if (accountId != null) {
                val daily  = client.fetchRecentStats(pubgConfig.platform, accountId, hours = 24, maxMatches = 30)
                val weekly = client.fetchWeeklyStats(pubgConfig.platform, accountId, maxMatches = 50)
                if (daily != null && daily.matches > 0)   println(daily.basicFormat("Tagesstatistik:"))
                if (weekly != null && weekly.matches > 0) { println(weekly.basicFormat("Wochenstatistik:")); println(weekly.weeklyExtras()) }
                if ((daily == null || daily.matches == 0) && (weekly == null || weekly.matches == 0))
                    println("  Keine Matches gefunden.")
            } else {
                println("  Account nicht gefunden.")
            }
        } else {
            println("  PUBG API Key fehlt.")
        }
        println()
    }

    // 1. Bundesliga
    println("=== ${bl1Config.ligaName} (Lieblingsverein: '${bl1Config.lieblingsverein}') ===")
    val bl1Result = BundesligaTableModule.ersteLiga(lieblingsverein = bl1Config.lieblingsverein).execute()
    println(bl1Result ?: "  Keine Daten.")
    println()

    // 2. Bundesliga
    println("=== ${bl2Config.ligaName} (Lieblingsverein: '${bl2Config.lieblingsverein}') ===")
    val bl2Result = BundesligaTableModule.zweiteLiga(lieblingsverein = bl2Config.lieblingsverein).execute()
    println(bl2Result ?: "  Keine Daten.")
    println()

    println("--- Beispielausfuehrung abgeschlossen ---")
}

/**
 * Combined-Modus: PUBG Stats alle 30 Min, Module dazwischen zeitversetzt.
 * Konfiguration kommt aus config/modules/*.conf
 */
fun runCombinedMode(
    pubgConfig: PubgObserverModuleConfig,
    bl1Config: BundesligaModuleConfig,
    bl2Config: BundesligaModuleConfig
) {
    val pubgWebhookUrl = EnvConfig.discordWebhook(pubgConfig.channel)

    val observer = CombinedObserver(
        pubgPlayers = pubgConfig.players,
        pubgPlatform = pubgConfig.platform,
        discordWebhookUrl = pubgWebhookUrl,
        pubgIntervalMinutes = pubgConfig.statsIntervalMinutes,
        checkIntervalMinutes = pubgConfig.checkIntervalMinutes
    )

    // 1. Bundesliga (aus bundesliga_1.conf)
    observer.addModule(
        module = BundesligaTableModule.ersteLiga(lieblingsverein = bl1Config.lieblingsverein),
        minuteOffset = bl1Config.minuteOffset,
        evenHoursOnly = bl1Config.evenHoursOnly,
        oddHoursOnly = bl1Config.oddHoursOnly,
        days = bl1Config.days,
        channel = bl1Config.channel
    )

    // 2. Bundesliga (aus bundesliga_2.conf)
    observer.addModule(
        module = BundesligaTableModule.zweiteLiga(lieblingsverein = bl2Config.lieblingsverein),
        minuteOffset = bl2Config.minuteOffset,
        evenHoursOnly = bl2Config.evenHoursOnly,
        oddHoursOnly = bl2Config.oddHoursOnly,
        days = bl2Config.days,
        channel = bl2Config.channel
    )

    // Handball (feste Konfiguration – noch kein eigenes .conf)
    val handballTeamId = "handball4all.westfalen.1309001"
    val handballTeamName = "HSG RE/OE"
    observer.addModule(HandballResultsModule(handballTeamId, handballTeamName), minuteOffset = 45, evenHoursOnly = true)
    observer.addModule(HandballTableModule(handballTeamId, handballTeamName), minuteOffset = 45, oddHoursOnly = true)

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        observer.stop()
    })

    observer.start()
}

/**
 * Scheduler-Modus: PUBG alle 30 Min, Handball-Module 15 Min versetzt
 */
fun runSchedulerMode(
    handballTeamId: String,
    handballTeamName: String,
    discordChannel: String
) {
    val webhookUrl = EnvConfig.discordWebhook(discordChannel)

    val scheduler = MultiModuleScheduler(
        discordWebhookUrl = webhookUrl,
        intervalMinutes = 30
    )

    // Sekundäre Module (15 Min versetzt)
    scheduler.addSecondaryModule(HandballUpcomingModule(handballTeamId, handballTeamName))
    scheduler.addSecondaryModule(HandballResultsModule(handballTeamId, handballTeamName))
    scheduler.addSecondaryModule(HandballTableModule(handballTeamId, handballTeamName))

    // TODO: Weitere Module hinzufügen:
    // scheduler.addPrimaryModule(PubgStatsModule(...))
    // scheduler.addSecondaryModule(BundesligaTableModule(...))
    // scheduler.addSecondaryModule(HeiseNewsModule(...))

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        scheduler.stop()
    })

    scheduler.start()
}

/**
 * Observer-Modus: Alte Funktionalität mit separaten Threads (Legacy)
 */
fun runObserverMode(
    pubgPlayers: List<String>,
    pubgPlatform: String,
    pubgCheckIntervalMinutes: Int,
    pubgDiscordChannel: String,
    handballTeamId: String,
    handballSeasonFrom: String,
    handballSeasonTo: String,
    handballDiscordChannel: String,
    handballCheckIntervalMinutes: Int,
    enablePubgObserver: Boolean = true,
    enableHandballModule: Boolean = true
) {
    val runningThreads = mutableListOf<Thread>()
    var pubgObserver: PubgObserver? = null
    var handballModule: HandballModule? = null

    // PUBG Observer starten
    if (enablePubgObserver) {
        val webhookUrl = EnvConfig.discordWebhook(pubgDiscordChannel)
        if (webhookUrl == null) {
            println("⚠️ PUBG: Discord Webhook '$pubgDiscordChannel' nicht konfiguriert")
        } else {
            pubgObserver = PubgObserver(
                players = pubgPlayers,
                platform = pubgPlatform,
                discordWebhookUrl = webhookUrl,
                checkIntervalMinutes = pubgCheckIntervalMinutes
            )

            val pubgThread = thread(name = "PUBG-Observer") {
                pubgObserver!!.start()
            }
            runningThreads.add(pubgThread)
            println("✅ PUBG Observer gestartet (Thread: ${pubgThread.name})")
        }
    }

    // Handball Module starten
    if (enableHandballModule) {
        val webhookUrl = EnvConfig.discordWebhook(handballDiscordChannel)

        handballModule = HandballModule(
            teamId = handballTeamId,
            discordWebhookUrl = webhookUrl,
            seasonFrom = handballSeasonFrom,
            seasonTo = handballSeasonTo
        )

        val handballThread = thread(name = "Handball-Observer") {
            handballModule!!.startObserver(handballCheckIntervalMinutes)
        }
        runningThreads.add(handballThread)
        println("✅ Handball Observer gestartet (Thread: ${handballThread.name})")
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        pubgObserver?.stop()
        handballModule?.stop()
    })

    if (runningThreads.isNotEmpty()) {
        println("\n🕐 ${runningThreads.size} Module laufen... (Ctrl+C zum Beenden)")
        runningThreads.forEach { it.join() }
    }
}

/**
 * Single-Modus: Einmaliger Durchlauf
 */
fun runSingleMode(
    handballTeamId: String,
    handballSeasonFrom: String,
    handballSeasonTo: String,
    handballDiscordChannel: String
) {
    val webhookUrl = EnvConfig.discordWebhook(handballDiscordChannel)

    val handballModule = HandballModule(
        teamId = handballTeamId,
        discordWebhookUrl = webhookUrl,
        seasonFrom = handballSeasonFrom,
        seasonTo = handballSeasonTo
    )

    println("\n🤾 Handball Module - Einzeldurchlauf...")
    handballModule.run()

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        handballModule.stop()
    })

    println("\n✅ Alle Module abgeschlossen.")
}
