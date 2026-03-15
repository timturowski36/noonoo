package config.modules

import java.io.File

/**
 * Konfiguration für den PUBG Observer.
 * Wird aus config/modules/pubg_observer.conf geladen.
 */
data class PubgObserverModuleConfig(
    val players: List<String>,
    val platform: String,
    val channel: String,
    val checkIntervalMinutes: Int,
    val statsIntervalMinutes: Int,
    val activityWindowMinutes: Int
) {
    companion object {
        private const val DEFAULT_PATH = "config/modules/pubg_observer.conf"

        fun load(path: String = DEFAULT_PATH): PubgObserverModuleConfig {
            val props = loadProps(path)
            return PubgObserverModuleConfig(
                players = props["players"]
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?: error("[$path] 'players' fehlt"),
                platform = props["platform"] ?: "steam",
                channel = props["channel"] ?: "allgemein",
                checkIntervalMinutes = props["check_interval"]?.toIntOrNull() ?: 5,
                statsIntervalMinutes = props["stats_interval"]?.toIntOrNull() ?: 30,
                activityWindowMinutes = props["activity_window"]?.toIntOrNull() ?: 90
            ).also {
                println("✅ [PubgObserver] ${it.players.size} Spieler, Check alle ${it.checkIntervalMinutes} Min, Channel: ${it.channel}")
            }
        }
    }
}

/**
 * Liest eine einfache key = value Konfigurationsdatei.
 * Kommentare (#) und Leerzeilen werden ignoriert.
 */
internal fun loadProps(path: String): Map<String, String> {
    val file = File(path)
    if (!file.exists()) error("Konfigurationsdatei nicht gefunden: $path")

    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf('=')
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
}
