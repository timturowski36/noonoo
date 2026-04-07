package de.noonoo.adapter.output.discord

import de.noonoo.domain.model.HandballScorer
import de.noonoo.domain.model.HandballScorerList
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object HandballStatisticsDiscordFormatter {

    private val NOW_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    // ── handball_scorer_table ─────────────────────────────────────────────────
    //
    // Kompakte Ligaübersicht: Rang, Name, Mannschaft, Spiele, Tore, Tore/Spiel.
    // Bei > 30 Einträgen wird abgeschnitten (Discord-Zeichenlimit).
    //
    // Beispiel:
    //   🥅 Torschützenliste — Bezirksliga Ruhrgebiet | Stand: 07.04.2026 14:00
    //   ```
    //    #  Name                Mannschaft        Sp  Tore  T/Sp
    //   ─────────────────────────────────────────────────────────
    //    1  Kevin Polnik        HSG RE/OE         16   125  7.81
    //    2  Chr. Pottkaemper    HSG Gevelsberg 2  16   106  6.63
    //    3  ...
    //   ```

    fun formatScorerTable(
        scorerList: HandballScorerList,
        now: LocalDateTime,
        limit: Int = 30
    ): String? {
        if (scorerList.scorers.isEmpty()) return null

        val scorers = scorerList.scorers.take(limit)
        val truncated = scorerList.scorers.size > limit

        val header = "🥅 **Torschützenliste** — ${scorerList.leagueName.ifBlank { "Liga ${scorerList.leagueId}" }}" +
            "  |  Stand: ${now.format(NOW_FMT)}"

        val colHeader = " #  %-19s %-16s  Sp  Tore  T/Sp".format("Name", "Mannschaft")
        val sep = "─".repeat(57)

        val rows = scorers.joinToString("\n") { s ->
            val pos  = s.position.toString().padStart(2)
            val name = s.playerName.take(19).padEnd(19)
            val team = s.teamName.take(16).padEnd(16)
            val sp   = s.gamesPlayed.toString().padStart(2)
            val tore = s.totalGoals.toString().padStart(4)
            val tpg  = "%.2f".format(s.goalsPerGame).padStart(5)
            "$pos  $name $team  $sp  $tore  $tpg"
        }

        val suffix = if (truncated) "\n(+${scorerList.scorers.size - limit} weitere)" else ""
        return "$header\n```\n$colHeader\n$sep\n$rows$suffix\n```"
    }

    // ── handball_scorer_team ──────────────────────────────────────────────────
    //
    // Detailansicht für alle Spieler einer Mannschaft: volle Statistik pro Spieler.
    //
    // Beispiel:
    //   🥅 Torschützen HSG RE/OE — Bezirksliga Ruhrgebiet | Stand: 07.04.2026 14:00
    //   ```
    //    #  Name                  Nr  Sp  Tore  Feld  7m  7m%    T/Sp
    //   ────────────────────────────────────────────────────────────────
    //    1  Kevin Polnik           8  16   125   106  19  63.3%  7.81
    //    2  Christian Pottkaemper  5  16   106    71  35  74.5%  6.63
    //   ```

    fun formatScorerTeam(
        scorerList: HandballScorerList,
        teamName: String,
        now: LocalDateTime
    ): String? {
        val teamScorers = scorerList.scorers.filter {
            it.teamName.equals(teamName, ignoreCase = true)
        }
        if (teamScorers.isEmpty()) return null

        val leagueLabel = scorerList.leagueName.ifBlank { "Liga ${scorerList.leagueId}" }
        val header = "🥅 **Torschützen $teamName** — $leagueLabel  |  Stand: ${now.format(NOW_FMT)}"

        val colHeader = " #  %-22s  Nr  Sp  Tore  Feld  7m  7m%%    T/Sp".format("Name")
        val sep = "─".repeat(64)

        val rows = teamScorers.joinToString("\n") { s ->
            val pos  = s.position.toString().padStart(2)
            val name = s.playerName.take(22).padEnd(22)
            val nr   = (s.jerseyNumber?.toString() ?: "–").padStart(2)
            val sp   = s.gamesPlayed.toString().padStart(2)
            val tore = s.totalGoals.toString().padStart(4)
            val feld = s.fieldGoals.toString().padStart(4)
            val sm   = s.sevenMeterGoals.toString().padStart(2)
            val pct  = if (s.sevenMeterAttempted > 0)
                "${"%.1f".format(s.sevenMeterPercentage)}%".padStart(5)
            else
                "  –  "
            val tpg  = "%.2f".format(s.goalsPerGame).padStart(5)
            "$pos  $name  $nr  $sp  $tore  $feld  $sm  $pct  $tpg"
        }

        val summary = buildTeamSummary(teamScorers)
        return "$header\n```\n$colHeader\n$sep\n$rows\n$sep\n$summary\n```"
    }

    private fun buildTeamSummary(scorers: List<HandballScorer>): String {
        val totalGoals = scorers.sumOf { it.totalGoals }
        val fieldGoals = scorers.sumOf { it.fieldGoals }
        val smGoals    = scorers.sumOf { it.sevenMeterGoals }
        val smAtt      = scorers.sumOf { it.sevenMeterAttempted }
        val smPct      = if (smAtt > 0) "%.1f%%".format(smGoals * 100.0 / smAtt) else "–"
        val warnings   = scorers.sumOf { it.warnings }
        val twoMin     = scorers.sumOf { it.twoMinuteSuspensions }
        val disq       = scorers.sumOf { it.disqualifications }

        return "∑  %d Tore  (Feld: %d  7m: %d/%d %s)  Verw: %d  2min: %d  Disq: %d".format(
            totalGoals, fieldGoals, smGoals, smAtt, smPct, warnings, twoMin, disq
        )
    }
}
