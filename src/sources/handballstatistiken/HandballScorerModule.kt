package sources.handballstatistiken

import scheduler.MultiModuleScheduler.ScheduledModule
import sources.handballstatistiken.api.HandballStatistikenClient
import sources.handballstatistiken.model.HandballScorerData

/**
 * FeedKrake-Modul: Torjägertabelle von handballstatistiken.de.
 *
 * Lädt die HTML-Seite, parst die Tabelle und gibt sie als
 * formatierte Discord-Nachricht zurück.
 *
 * Verwendung im Testmodus:
 *   val module = HandballScorerModule("https://handballstatistiken.de/NRW/2526/300268")
 *   println(module.execute())
 *
 * Im Produktivmodus über CombinedObserver.addModule(module, ...).
 *
 * @param url           Vollständige URL der Statistik-Seite
 * @param highlightTeam Mannschaft die in der Ausgabe hervorgehoben wird (z.B. "HSG RE/OE")
 * @param limit         Maximale Anzahl anzuzeigender Spieler (Standard: 15)
 */
class HandballScorerModule(
    private val url: String,
    private val highlightTeam: String? = null,
    private val limit: Int = 15
) : ScheduledModule {

    override val name = "Handball: Torjägertabelle"

    private val client = HandballStatistikenClient()

    override fun execute(): String? {
        val data: HandballScorerData = client.fetchScorer(url) ?: run {
            println("❌ [$name] Keine Daten erhalten")
            return null
        }

        printConsole(data)

        return data.discordFormat(highlightTeam, limit)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Konsolen-Ausgabe (Testmodus)
    // ──────────────────────────────────────────────────────────────────────────

    private fun printConsole(data: HandballScorerData) {
        println()
        println("🏆 ═══════════════════════════════════════════════════════════")
        println("   Torjägertabelle  |  ${data.players.size} Spieler  |  ${data.url}")
        println("   ═══════════════════════════════════════════════════════════")
        println()

        val header = "Pl  %-22s  %-22s  Sp  Tore  7m   T/Sp  F/Sp  V  2M  DQ"
            .format("Name", "Mannschaft")
        println(header)
        println("─".repeat(header.length))

        data.topScorer(limit).forEach { p ->
            val highlight = highlightTeam != null &&
                p.mannschaft.contains(highlightTeam, ignoreCase = true)
            val marker = if (highlight) "►" else " "
            println(
                "$marker%2d  %-22s  %-22s  %2d  %4d  %3d  %5.2f  %5.2f  %d  %2d   %d".format(
                    p.rang,
                    p.name.take(22),
                    p.mannschaft.take(22),
                    p.spiele,
                    p.tore,
                    p.siebenmeterTore,
                    p.toreProSpiel,
                    p.feldtoreProSpiel,
                    p.verwarnungen,
                    p.zweiMinuten,
                    p.disqualifikationen
                )
            )
        }

        println()
    }
}
