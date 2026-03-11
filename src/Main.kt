import config.EnvConfig
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

    // ═══════════════════════════════════════════════════════════════════════════
    // MODUS AUSWÄHLEN
    // ═══════════════════════════════════════════════════════════════════════════
    // "combined"   = PUBG prüfen + Stats alle 45 Min wenn aktiv + Module zur vollen Stunde
    // "scheduler"  = Multi-Module Scheduler (primär/sekundär versetzt)
    // "observer"   = Alte Observer (separate Threads)
    // "single"     = Einmaliger Durchlauf
    val mode = "combined"

    // ═══════════════════════════════════════════════════════════════════════════
    // MODUL-KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    // Welche Module sollen laufen?
    val enablePubgObserver = true
    val enableHandballModule = true

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBG KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    val pubgPlayers = listOf("brotrustgaming", "philipnc", "chrissi1970")
    val pubgPlatform = "steam"
    val pubgCheckIntervalMinutes = 30
    val pubgDiscordChannel = "allgemein"

    // ═══════════════════════════════════════════════════════════════════════════
    // HANDBALL KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    val handballTeamId = "handball4all.westfalen.1309001"
    val handballTeamName = "HSG RE/OE"
    val handballSeasonFrom = "2025-07-01"
    val handballSeasonTo = "2026-06-30"
    val handballDiscordChannel = "allgemein"
    val handballCheckIntervalMinutes = 60
    val handballObserverMode = false  // true = kontinuierlich, false = einmalig

    // ═══════════════════════════════════════════════════════════════════════════

    // Config laden
    if (!EnvConfig.load()) {
        println("❌ Konfiguration konnte nicht geladen werden!")
        return
    }

    when (mode) {
        "combined" -> runCombinedMode(
            pubgPlayers = pubgPlayers,
            pubgPlatform = pubgPlatform,
            discordChannel = pubgDiscordChannel,
            handballTeamId = handballTeamId,
            handballTeamName = handballTeamName
        )
        "scheduler" -> runSchedulerMode(
            handballTeamId = handballTeamId,
            handballTeamName = handballTeamName,
            discordChannel = handballDiscordChannel
        )
        "observer" -> runObserverMode(
            enablePubgObserver = enablePubgObserver,
            enableHandballModule = enableHandballModule,
            pubgPlayers = pubgPlayers,
            pubgPlatform = pubgPlatform,
            pubgCheckIntervalMinutes = pubgCheckIntervalMinutes,
            pubgDiscordChannel = pubgDiscordChannel,
            handballTeamId = handballTeamId,
            handballSeasonFrom = handballSeasonFrom,
            handballSeasonTo = handballSeasonTo,
            handballDiscordChannel = handballDiscordChannel,
            handballCheckIntervalMinutes = handballCheckIntervalMinutes
        )
        else -> runSingleMode(
            handballTeamId = handballTeamId,
            handballSeasonFrom = handballSeasonFrom,
            handballSeasonTo = handballSeasonTo,
            handballDiscordChannel = handballDiscordChannel
        )
    }
}

/**
 * Combined-Modus: PUBG Stats alle 30 Min, Module dazwischen bei :15 und :45
 */
fun runCombinedMode(
    pubgPlayers: List<String>,
    pubgPlatform: String,
    discordChannel: String,
    handballTeamId: String,
    handballTeamName: String
) {
    val webhookUrl = EnvConfig.discordWebhook(discordChannel)

    val observer = CombinedObserver(
        pubgPlayers = pubgPlayers,
        pubgPlatform = pubgPlatform,
        discordWebhookUrl = webhookUrl,
        pubgIntervalMinutes = 30,   // PUBG Stats alle 30 Min (:00 und :30)
        checkIntervalMinutes = 5     // Status-Check alle 5 Min
    )

    // Zeitplan: Stats → Modul → Stats → Modul (2 Module pro Stunde)
    // :00 → PUBG Stats
    // :15 → Modul (Bundesliga oder Handball)
    // :30 → PUBG Stats
    // :45 → Modul (Tagesschau oder Handball)

    // :15 - Bundesliga Tabellen (rotierend)
    observer.addModule(BundesligaTableModule.ersteLiga(), minuteOffset = 15, evenHoursOnly = true)
    observer.addModule(BundesligaTableModule.zweiteLiga(), minuteOffset = 15, oddHoursOnly = true)

    // :45 - Handball und Tagesschau (rotierend über Stunden)
    // Stunde 0,3,6... → Handball Ergebnisse
    // Stunde 1,4,7... → Handball Tabelle
    // Stunde 2,5,8... → Tagesschau
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
 * Observer-Modus: Alte Funktionalität mit separaten Threads
 */
fun runObserverMode(
    enablePubgObserver: Boolean,
    enableHandballModule: Boolean,
    pubgPlayers: List<String>,
    pubgPlatform: String,
    pubgCheckIntervalMinutes: Int,
    pubgDiscordChannel: String,
    handballTeamId: String,
    handballSeasonFrom: String,
    handballSeasonTo: String,
    handballDiscordChannel: String,
    handballCheckIntervalMinutes: Int
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
