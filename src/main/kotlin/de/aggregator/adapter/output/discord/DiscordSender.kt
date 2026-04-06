package de.aggregator.adapter.output.discord

import club.minnced.discord.webhook.WebhookClient
import de.aggregator.domain.model.Match
import de.aggregator.domain.model.Standing
import de.aggregator.domain.model.Team
import de.aggregator.domain.port.output.NotificationPort

class DiscordSender(
    private val webhookUrls: Map<String, String>
) : NotificationPort {

    override suspend fun send(channel: String, message: String) {
        val url = requireNotNull(webhookUrls[channel]) {
            "Kein Webhook für Kanal '$channel' konfiguriert."
        }
        WebhookClient.withUrl(url).use { client ->
            client.send(message)
        }
    }

    // ── Formatierungs-Hilfsfunktionen ─────────────────────────────────────────

    companion object {

        fun formatTableSummary(
            standings: List<Standing>,
            teams: Map<Int, Team>,
            league: String,
            date: String
        ): String = buildString {
            appendLine("**Bundesliga Tabelle – Stand $date**")
            appendLine("```")
            appendLine("Pl  Verein                    Sp   Pkt   Diff")
            appendLine("─".repeat(50))
            standings.forEach { s ->
                val teamName = teams[s.teamId]?.name?.take(24)?.padEnd(24) ?: "Unbekannt".padEnd(24)
                val diff = s.goalsFor - s.goalsAgainst
                val diffStr = if (diff >= 0) "+$diff" else "$diff"
                appendLine(
                    "${s.position.toString().padStart(2)}.  $teamName  " +
                    "${s.played.toString().padStart(2)}  ${s.points.toString().padStart(4)}  ${diffStr.padStart(5)}"
                )
            }
            appendLine("```")
        }

        fun formatMatchdayResults(
            matches: List<Match>,
            teams: Map<Int, Team>,
            matchday: Int
        ): String = buildString {
            appendLine("**Bundesliga – $matchday. Spieltag**")
            appendLine("```")
            matches.forEach { m ->
                val home = teams[m.homeTeamId]?.name ?: "?"
                val away = teams[m.awayTeamId]?.name ?: "?"
                val score = if (m.isFinished) "${m.homeScoreFt}:${m.awayScoreFt}" else "- : -"
                appendLine("$home  $score  $away")
                if (m.goals.isNotEmpty()) {
                    val homeGoals = m.goals
                        .filter { !it.isOwnGoal && it.scoreHome > it.scoreAway || it.isOwnGoal && it.scoreHome < it.scoreAway }
                        .joinToString(", ") { "${it.scorerName} ${it.minute}'" }
                    val awayGoals = m.goals
                        .filter { !it.isOwnGoal && it.scoreAway > it.scoreHome || it.isOwnGoal && it.scoreAway < it.scoreHome }
                        .joinToString(", ") { "${it.scorerName} ${it.minute}'" }
                    if (homeGoals.isNotEmpty() || awayGoals.isNotEmpty()) {
                        appendLine("  Tore: $homeGoals | $awayGoals")
                    }
                }
            }
            appendLine("```")
        }
    }
}
