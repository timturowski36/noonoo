import config.EnvConfig
import config.modules.BundesligaModuleConfig
import config.modules.NaechsteSpieleModuleConfig
import config.modules.PubgObserverModuleConfig
import scheduler.BundesligaNaechsteSpieleModule
import scheduler.BundesligaTableModule
import scheduler.CombinedObserver
import scheduler.HandballResultsModule
import scheduler.HandballTableModule
import sources.`pubg-api`.api.PubgApiClient

fun main() {
    println("""
    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake - Multi-Module                      ║
    ╚═══════════════════════════════════════════════════════════════╝
    """.trimIndent())

    if (!EnvConfig.load()) {
        println("❌ Konfiguration konnte nicht geladen werden!")
        return
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODUS AUSWÄHLEN
    // ═══════════════════════════════════════════════════════════════════════════
    // "combined"  →  Dauerbetrieb: PUBG + Module nach Zeitplan, sendet an Discord
    // "single"    →  Einmaliger Durchlauf aller Module, sendet an Discord
    // "example"   →  Testlauf aller Module, Ausgabe nur in Konsole (kein Discord)
    val mode = "combined"
    // ═══════════════════════════════════════════════════════════════════════════

    val pubgConfig     = PubgObserverModuleConfig.load()
    val bl1Config      = BundesligaModuleConfig.load("config/modules/bundesliga_1.conf")
    val bl2Config      = BundesligaModuleConfig.load("config/modules/bundesliga_2.conf")
    val spieleSchalke  = NaechsteSpieleModuleConfig.load("config/modules/naechste_spiele_schalke.conf")
    val spieleDortmund = NaechsteSpieleModuleConfig.load("config/modules/naechste_spiele_dortmund.conf")

    when (mode) {
        "combined" -> runCombinedMode(pubgConfig, bl1Config, bl2Config, spieleSchalke, spieleDortmund)
        "single"   -> runSingleMode(pubgConfig, bl1Config, bl2Config)
        "example"  -> runExampleMode(pubgConfig, bl1Config, bl2Config)
        else       -> println("❌ Unbekannter Modus: $mode")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MODI
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Dauerbetrieb: PUBG Observer läuft kontinuierlich, Module werden zeitversetzt
 * ausgeführt. Alles wird an Discord gesendet.
 */
fun runCombinedMode(
    pubgConfig: PubgObserverModuleConfig,
    bl1Config: BundesligaModuleConfig,
    bl2Config: BundesligaModuleConfig,
    spieleSchalke: NaechsteSpieleModuleConfig,
    spieleDortmund: NaechsteSpieleModuleConfig
) {
    val observer = CombinedObserver(
        pubgPlayers = pubgConfig.players,
        pubgPlatform = pubgConfig.platform,
        discordWebhookUrl = EnvConfig.discordWebhook(pubgConfig.channel),
        pubgIntervalMinutes = pubgConfig.statsIntervalMinutes,
        checkIntervalMinutes = pubgConfig.checkIntervalMinutes
    )

    observer.addModule(
        module        = BundesligaTableModule.ersteLiga(lieblingsverein = bl1Config.lieblingsverein),
        minuteOffset  = bl1Config.minuteOffset,
        evenHoursOnly = bl1Config.evenHoursOnly,
        oddHoursOnly  = bl1Config.oddHoursOnly,
        days          = bl1Config.days,
        channel       = bl1Config.channel
    )
    observer.addModule(
        module        = BundesligaTableModule.zweiteLiga(lieblingsverein = bl2Config.lieblingsverein),
        minuteOffset  = bl2Config.minuteOffset,
        evenHoursOnly = bl2Config.evenHoursOnly,
        oddHoursOnly  = bl2Config.oddHoursOnly,
        days          = bl2Config.days,
        channel       = bl2Config.channel
    )

    // Nächste Spiele (aus naechste_spiele_*.conf)
    listOf(spieleSchalke, spieleDortmund).forEach { cfg ->
        observer.addModule(
            module       = BundesligaNaechsteSpieleModule(cfg.team, cfg.liga, cfg.anzahl),
            minuteOffset = cfg.minuteOffset,
            days         = cfg.days,
            channel      = cfg.channel
        )
    }

    // Handball (noch kein eigenes .conf)
    observer.addModule(HandballResultsModule("handball4all.westfalen.1309001", "HSG RE/OE"), minuteOffset = 45, evenHoursOnly = true)
    observer.addModule(HandballTableModule("handball4all.westfalen.1309001",   "HSG RE/OE"), minuteOffset = 45, oddHoursOnly  = true)

    Runtime.getRuntime().addShutdownHook(Thread { observer.stop() })
    observer.start()
}

/**
 * Einmaliger Durchlauf: Alle Module werden einmal ausgeführt und an Discord gesendet.
 */
fun runSingleMode(
    pubgConfig: PubgObserverModuleConfig,
    bl1Config: BundesligaModuleConfig,
    bl2Config: BundesligaModuleConfig
) {
    println("\n--- SINGLE MODUS ---\n")

    val apiKey = EnvConfig.pubgApiKey()
    if (apiKey != null) {
        val client = PubgApiClient(apiKey)
        val pubgWebhook = EnvConfig.discordWebhook(pubgConfig.channel)
            ?.let { scheduler.discord.DiscordWebhook(it) }

        pubgConfig.players.forEach { player ->
            val accountId = client.fetchAccountId(player, pubgConfig.platform) ?: return@forEach
            val daily  = client.fetchRecentStats(pubgConfig.platform, accountId, hours = 24, maxMatches = 30)
            val weekly = client.fetchWeeklyStats(pubgConfig.platform, accountId, maxMatches = 50)
            if (daily != null || weekly != null) {
                val msg = buildString {
                    appendLine("👥 Player: $player")
                    if (daily  != null && daily.matches  > 0) { appendLine(); appendLine(daily.basicFormat("📅 Tagesstatistik:")) }
                    if (weekly != null && weekly.matches > 0) { appendLine(); appendLine(weekly.basicFormat("🗓 Wochenstatistik:")); append(weekly.weeklyExtras()) }
                }.trimEnd()
                pubgWebhook?.send(msg)
                println("✅ PUBG $player gesendet")
            }
        }
    }

    listOf(
        bl1Config to BundesligaTableModule.ersteLiga(lieblingsverein = bl1Config.lieblingsverein),
        bl2Config to BundesligaTableModule.zweiteLiga(lieblingsverein = bl2Config.lieblingsverein)
    ).forEach { (cfg, module) ->
        val result = module.execute() ?: return@forEach
        val webhook = EnvConfig.discordWebhook(cfg.channel)?.let { scheduler.discord.DiscordWebhook(it) }
        webhook?.send(result)
        println("✅ ${module.name} gesendet")
    }

    println("\n--- Abgeschlossen ---")
}

/**
 * Testlauf: Alle Module werden einmal ausgeführt, Ausgabe nur in der Konsole.
 * Kein Discord-Versand. Ideal zum Prüfen ob die Konfiguration stimmt.
 */
fun runExampleMode(
    pubgConfig: PubgObserverModuleConfig,
    bl1Config: BundesligaModuleConfig,
    bl2Config: BundesligaModuleConfig
) {
    println("\n--- EXAMPLE MODUS (kein Discord) ---\n")

    val apiKey = EnvConfig.pubgApiKey()
    if (apiKey != null) {
        val client = PubgApiClient(apiKey)
        val firstPlayer = pubgConfig.players.firstOrNull() ?: return
        println("=== PUBG: $firstPlayer ===")
        val accountId = client.fetchAccountId(firstPlayer, pubgConfig.platform)
        if (accountId != null) {
            val daily  = client.fetchRecentStats(pubgConfig.platform, accountId, hours = 24, maxMatches = 30)
            val weekly = client.fetchWeeklyStats(pubgConfig.platform, accountId, maxMatches = 50)
            if (daily  != null && daily.matches  > 0) println(daily.basicFormat("Tagesstatistik:"))
            if (weekly != null && weekly.matches > 0) { println(weekly.basicFormat("Wochenstatistik:")); println(weekly.weeklyExtras()) }
            if ((daily == null || daily.matches == 0) && (weekly == null || weekly.matches == 0)) println("  Keine Matches gefunden.")
        } else {
            println("  Account nicht gefunden.")
        }
        println()
    }

    println("=== ${bl1Config.ligaName} ===")
    println(BundesligaTableModule.ersteLiga(lieblingsverein = bl1Config.lieblingsverein).execute() ?: "  Keine Daten.")
    println()

    println("=== ${bl2Config.ligaName} ===")
    println(BundesligaTableModule.zweiteLiga(lieblingsverein = bl2Config.lieblingsverein).execute() ?: "  Keine Daten.")

    println("\n--- Abgeschlossen ---")
}
