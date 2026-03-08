package scheduler.config

import java.io.File

/**
 * Einfache, zentrale Konfiguration für FeedKrake.
 * Eine Datei für alles: Module, Schedules, Discord-Channels.
 */
data class FeedKrakeConfig(
    val channels: Map<String, String>,          // Channel-Name -> Webhook-URL
    val jobs: List<JobConfig>                   // Alle konfigurierten Jobs
) {
    companion object {
        private const val CONFIG_FILE = "feedkrake.config"

        fun load(): FeedKrakeConfig {
            val file = File(CONFIG_FILE)
            if (!file.exists()) {
                createDefaultConfig(file)
                println("📝 Beispielkonfiguration erstellt: $CONFIG_FILE")
                println("   Bitte anpassen und neu starten!")
            }
            return parse(file.readText())
        }

        private fun createDefaultConfig(file: File) {
            file.writeText("""
# ═══════════════════════════════════════════════════════════════════════════════
# 🐙 FeedKrake Konfiguration
# ═══════════════════════════════════════════════════════════════════════════════
#
# Einfaches Format:
#   [channels]     - Discord Webhooks definieren
#   [jobs]         - Automatische Jobs definieren
#
# ═══════════════════════════════════════════════════════════════════════════════

[channels]
# Format: name = webhook_url
# Webhook erstellen: Discord Server → Einstellungen → Integrationen → Webhooks

news      = https://discord.com/api/webhooks/DEINE_WEBHOOK_URL_HIER
gaming    = https://discord.com/api/webhooks/DEINE_WEBHOOK_URL_HIER
handball  = https://discord.com/api/webhooks/DEINE_WEBHOOK_URL_HIER

[jobs]
# ─────────────────────────────────────────────────────────────────────────────
# Format: name | modul | zeitplan | channel | optionen
#
# Module:
#   heise.news          - Heise Online News
#   heise.security      - Heise Security
#   heise.developer     - Heise Developer
#   handball.tabelle    - Handball Tabelle
#   handball.spiele     - Handball Spielplan
#   handball.ergebnisse - Handball Ergebnisse
#   pubg.activity       - PUBG Aktivitäts-Check
#   pubg.weekly         - PUBG Wochenstatistik
#
# Zeitplan:
#   täglich 08:00       - Jeden Tag um 8 Uhr
#   montags 18:00       - Jeden Montag um 18 Uhr
#   mo,mi,fr 12:00      - Montag, Mittwoch, Freitag
#   alle 30min          - Alle 30 Minuten
#   beim start          - Einmalig beim Programmstart
#
# Optionen (optional):
#   max=10              - Maximale Anzahl Artikel
#   spieler=Tim,Max     - Spielernamen für PUBG
# ─────────────────────────────────────────────────────────────────────────────

# Beispiel-Jobs (# entfernen zum Aktivieren):

# Heise News täglich um 8 Uhr
Morgennews | heise.news | täglich 08:00 | news | max=10

# Heise Security alle 4 Stunden
Security-Alerts | heise.security | alle 240min | news | max=5

# Handball Tabelle jeden Montag
Handball Update | handball.tabelle | montags 18:00 | handball

# PUBG Check alle 30 Minuten
PUBG Watch | pubg.activity | alle 30min | gaming | spieler=brotrustgaming,philipnc

# PUBG Wochenstatistik Sonntag Abend
PUBG Woche | pubg.weekly | sonntags 20:00 | gaming | spieler=brotrustgaming,philipnc,chrissi1970

            """.trimIndent())
        }

        private fun parse(content: String): FeedKrakeConfig {
            val channels = mutableMapOf<String, String>()
            val jobs = mutableListOf<JobConfig>()
            var currentSection = ""

            content.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line ->
                    when {
                        line == "[channels]" -> currentSection = "channels"
                        line == "[jobs]" -> currentSection = "jobs"
                        currentSection == "channels" && line.contains("=") -> {
                            val (name, url) = line.split("=", limit = 2).map { it.trim() }
                            if (url.startsWith("https://discord.com/api/webhooks/")) {
                                channels[name] = url
                            }
                        }
                        currentSection == "jobs" && line.contains("|") -> {
                            parseJob(line)?.let { jobs.add(it) }
                        }
                    }
                }

            return FeedKrakeConfig(channels, jobs)
        }

        private fun parseJob(line: String): JobConfig? {
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 4) return null

            val name = parts[0]
            val module = parts[1]
            val schedule = parts[2]
            val channel = parts[3]
            val options = if (parts.size > 4) parseOptions(parts[4]) else emptyMap()

            return JobConfig(name, module, schedule, channel, options)
        }

        private fun parseOptions(optionsStr: String): Map<String, String> {
            return optionsStr.split(",")
                .mapNotNull { opt ->
                    val kv = opt.trim().split("=", limit = 2)
                    if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
                }
                .toMap()
        }
    }
}

/**
 * Konfiguration für einen einzelnen Job.
 */
data class JobConfig(
    val name: String,
    val module: String,
    val schedule: String,
    val channel: String,
    val options: Map<String, String> = emptyMap()
) {
    fun getOption(key: String, default: String = ""): String = options[key] ?: default
    fun getIntOption(key: String, default: Int = 0): Int = options[key]?.toIntOrNull() ?: default
    fun getListOption(key: String): List<String> = options[key]?.split(",")?.map { it.trim() } ?: emptyList()
}
