import config.EnvConfig
import config.modules.BundesligaModuleConfig
import config.modules.HandballModuleConfig
import config.modules.NaechsteSpieleModuleConfig
import config.modules.PubgObserverModuleConfig
import scheduler.BundesligaNaechsteSpieleModule
import scheduler.BundesligaTableModule
import scheduler.CombinedObserver
import scheduler.HandballResultsModule
import scheduler.HandballTableModule
import scheduler.HandballUpcomingModule
import sources.handballstatistiken.HandballScorerModule

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
    // "production"  →  Dauerbetrieb: alle Module nach Zeitplan, Discord-Versand
    // "test"        →  Einmaliger Durchlauf aller Module, nur Konsolenausgabe
    val mode = "production"
    // ═══════════════════════════════════════════════════════════════════════════

    val configs = loadAllConfigs()

    when (mode) {
        "production" -> runProductionMode(configs)
        "test"       -> runTestMode(configs)
        else         -> println("❌ Unbekannter Modus: $mode (gültig: production, test)")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Konfigurationen
// ═══════════════════════════════════════════════════════════════════════════════

data class AppConfigs(
    val pubg:              PubgObserverModuleConfig,
    val bl1:               BundesligaModuleConfig,
    val bl2:               BundesligaModuleConfig,
    val spieleSchalke:     NaechsteSpieleModuleConfig,
    val spieleDortmund:    NaechsteSpieleModuleConfig,
    val hsgTabelle:        HandballModuleConfig,
    val hsgNaechsteSpiele: HandballModuleConfig,
    val hsgErgebnisse:     HandballModuleConfig
)

fun loadAllConfigs() = AppConfigs(
    pubg              = PubgObserverModuleConfig.load(),
    bl1               = BundesligaModuleConfig.load("config/modules/bundesliga_1.conf"),
    bl2               = BundesligaModuleConfig.load("config/modules/bundesliga_2.conf"),
    spieleSchalke     = NaechsteSpieleModuleConfig.load("config/modules/naechste_spiele_schalke.conf"),
    spieleDortmund    = NaechsteSpieleModuleConfig.load("config/modules/naechste_spiele_dortmund.conf"),
    hsgTabelle        = HandballModuleConfig.load("config/modules/handball_tabelle.conf"),
    hsgNaechsteSpiele = HandballModuleConfig.load("config/modules/handball_naechste_spiele.conf"),
    hsgErgebnisse     = HandballModuleConfig.load("config/modules/handball_ergebnisse.conf")
)

// ═══════════════════════════════════════════════════════════════════════════════
// MODI
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Dauerbetrieb (Production):
 * - PUBG Observer: alle X Minuten prüfen ob jemand spielt → Stats an #allgemein
 * - Alle anderen Module: laufen nach ihrem Zeitplan aus den .conf-Dateien
 *   unabhängig von PUBG-Aktivität → jeweils in ihren konfigurierten Channel
 */
fun runProductionMode(c: AppConfigs) {
    val observer = CombinedObserver(
        pubgPlayers          = c.pubg.players,
        pubgPlatform         = c.pubg.platform,
        discordWebhookUrl    = EnvConfig.discordWebhook(c.pubg.channel),
        pubgIntervalMinutes  = c.pubg.statsIntervalMinutes,
        checkIntervalMinutes = c.pubg.checkIntervalMinutes
    )

    // ── Bundesliga ────────────────────────────────────────────────────────────
    observer.addModule(
        module        = BundesligaTableModule.ersteLiga(lieblingsverein = c.bl1.lieblingsverein),
        minuteOffset  = c.bl1.minuteOffset,
        evenHoursOnly = c.bl1.evenHoursOnly,
        oddHoursOnly  = c.bl1.oddHoursOnly,
        days          = c.bl1.days,
        channel       = c.bl1.channel,
        targetHour    = c.bl1.hour
    )
    observer.addModule(
        module        = BundesligaTableModule.zweiteLiga(lieblingsverein = c.bl2.lieblingsverein),
        minuteOffset  = c.bl2.minuteOffset,
        evenHoursOnly = c.bl2.evenHoursOnly,
        oddHoursOnly  = c.bl2.oddHoursOnly,
        days          = c.bl2.days,
        channel       = c.bl2.channel,
        targetHour    = c.bl2.hour
    )

    // ── Nächste Spiele Bundesliga ─────────────────────────────────────────────
    listOf(c.spieleSchalke, c.spieleDortmund).forEach { cfg ->
        observer.addModule(
            module       = BundesligaNaechsteSpieleModule(cfg.team, cfg.liga, cfg.anzahl),
            minuteOffset = cfg.minuteOffset,
            days         = cfg.days,
            channel      = cfg.channel,
            targetHour   = cfg.hour
        )
    }

    // ── Handball HSG RE/OE ────────────────────────────────────────────────────
    observer.addModule(
        module        = HandballResultsModule(c.hsgErgebnisse.teamId, c.hsgErgebnisse.teamName),
        minuteOffset  = c.hsgErgebnisse.minuteOffset,
        evenHoursOnly = c.hsgErgebnisse.evenHoursOnly,
        oddHoursOnly  = c.hsgErgebnisse.oddHoursOnly,
        days          = c.hsgErgebnisse.days,
        channel       = c.hsgErgebnisse.channel,
        targetHour    = c.hsgErgebnisse.hour
    )
    observer.addModule(
        module        = HandballUpcomingModule(c.hsgNaechsteSpiele.teamId, c.hsgNaechsteSpiele.teamName),
        minuteOffset  = c.hsgNaechsteSpiele.minuteOffset,
        evenHoursOnly = c.hsgNaechsteSpiele.evenHoursOnly,
        oddHoursOnly  = c.hsgNaechsteSpiele.oddHoursOnly,
        days          = c.hsgNaechsteSpiele.days,
        channel       = c.hsgNaechsteSpiele.channel,
        targetHour    = c.hsgNaechsteSpiele.hour
    )
    observer.addModule(
        module        = HandballTableModule(c.hsgTabelle.teamId, c.hsgTabelle.teamName),
        minuteOffset  = c.hsgTabelle.minuteOffset,
        evenHoursOnly = c.hsgTabelle.evenHoursOnly,
        oddHoursOnly  = c.hsgTabelle.oddHoursOnly,
        days          = c.hsgTabelle.days,
        channel       = c.hsgTabelle.channel,
        targetHour    = c.hsgTabelle.hour
    )

    Runtime.getRuntime().addShutdownHook(Thread { observer.stop() })
    observer.start()
}

/**
 * Testlauf (Test):
 * - Alle Module werden einmal sofort ausgeführt
 * - Ausgabe nur in der Konsole, kein Discord
 * - Wochentag- und Zeitplan-Filter werden ignoriert
 */
fun runTestMode(c: AppConfigs) {
    println("\n--- TEST MODUS (nur Konsole, kein Discord) ---\n")

    val modules = listOf(
        "1. Bundesliga Tabelle"           to BundesligaTableModule.ersteLiga(lieblingsverein = c.bl1.lieblingsverein),
        "2. Bundesliga Tabelle"           to BundesligaTableModule.zweiteLiga(lieblingsverein = c.bl2.lieblingsverein),
        "Nächste Spiele: ${c.spieleSchalke.team}"  to BundesligaNaechsteSpieleModule(c.spieleSchalke.team,  c.spieleSchalke.liga,  c.spieleSchalke.anzahl),
        "Nächste Spiele: ${c.spieleDortmund.team}" to BundesligaNaechsteSpieleModule(c.spieleDortmund.team, c.spieleDortmund.liga, c.spieleDortmund.anzahl),
        "Handball: Ergebnisse"            to HandballResultsModule(c.hsgErgebnisse.teamId,     c.hsgErgebnisse.teamName),
        "Handball: Nächste Spiele"        to HandballUpcomingModule(c.hsgNaechsteSpiele.teamId, c.hsgNaechsteSpiele.teamName),
        "Handball: Tabelle"               to HandballTableModule(c.hsgTabelle.teamId,           c.hsgTabelle.teamName),
        "Handball: Torjägertabelle NRW"   to HandballScorerModule(
            url           = "https://handballstatistiken.de/NRW/2526/300268",
            highlightTeam = "HSG RE/OE",
            limit         = 15
        )
    )

    modules.forEach { (label, module) ->
        println("=== $label ===")
        println(module.execute() ?: "  Keine Daten.")
        println()
    }

    println("--- Abgeschlossen ---")
}
