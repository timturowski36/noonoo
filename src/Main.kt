import config.EnvConfig
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
    // MODUL-KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════

    // Welche Module sollen laufen?
    val enablePubgObserver = true
    val enableHandballModule = true

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBG KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    val pubgPlayers = listOf("brotrustgaming", "philipnc")
    val pubgPlatform = "steam"
    val pubgCheckIntervalMinutes = 30
    val pubgDiscordChannel = "gaming"

    // ═══════════════════════════════════════════════════════════════════════════
    // HANDBALL KONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════
    val handballTeamId = "handball4all.westfalen.1309001"
    val handballSeasonFrom = "2025-07-01"
    val handballSeasonTo = "2026-06-30"
    val handballDiscordChannel = "handball"
    val handballCheckIntervalMinutes = 60
    val handballObserverMode = false  // true = kontinuierlich, false = einmalig

    // ═══════════════════════════════════════════════════════════════════════════

    // Config laden
    if (!EnvConfig.load()) {
        println("❌ Konfiguration konnte nicht geladen werden!")
        return
    }

    val runningThreads = mutableListOf<Thread>()
    var pubgObserver: PubgObserver? = null
    var handballModule: HandballModule? = null

    // ─────────────────────────────────────────────────────────────────────────────
    // PUBG Observer starten
    // ─────────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────────
    // Handball Module starten
    // ─────────────────────────────────────────────────────────────────────────────
    if (enableHandballModule) {
        val webhookUrl = EnvConfig.discordWebhook(handballDiscordChannel)

        handballModule = HandballModule(
            teamId = handballTeamId,
            discordWebhookUrl = webhookUrl,
            seasonFrom = handballSeasonFrom,
            seasonTo = handballSeasonTo
        )

        if (handballObserverMode) {
            // Kontinuierlicher Modus - als Thread
            val handballThread = thread(name = "Handball-Observer") {
                handballModule!!.startObserver(handballCheckIntervalMinutes)
            }
            runningThreads.add(handballThread)
            println("✅ Handball Observer gestartet (Thread: ${handballThread.name})")
        } else {
            // Einmaliger Durchlauf - blockierend ausführen
            println("\n🤾 Handball Module - Einzeldurchlauf...")
            handballModule.run()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Shutdown Hook
    // ─────────────────────────────────────────────────────────────────────────────
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\n🛑 Shutdown Signal empfangen...")
        pubgObserver?.stop()
        handballModule?.stop()
    })

    // ─────────────────────────────────────────────────────────────────────────────
    // Auf Threads warten (wenn vorhanden)
    // ─────────────────────────────────────────────────────────────────────────────
    if (runningThreads.isNotEmpty()) {
        println("\n🕐 ${runningThreads.size} Module laufen... (Ctrl+C zum Beenden)")
        runningThreads.forEach { it.join() }
    } else {
        println("\n✅ Alle Module abgeschlossen.")
    }
}
