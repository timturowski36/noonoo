package config.modules

import java.time.DayOfWeek

/**
 * Konfiguration für das "Nächste Spiele"-Modul eines Vereins.
 * Wird aus config/modules/naechste_spiele_*.conf geladen.
 */
data class NaechsteSpieleModuleConfig(
    val team: String,
    val liga: String,
    val anzahl: Int,
    val channel: String,
    val minuteOffset: Int,
    val days: List<DayOfWeek>
) {
    val ligaName: String get() = when (liga) {
        "bl1" -> "1. Bundesliga"
        "bl2" -> "2. Bundesliga"
        else  -> liga
    }

    companion object {
        private val dayMap = mapOf(
            "mo" to DayOfWeek.MONDAY,    "montag" to DayOfWeek.MONDAY,
            "di" to DayOfWeek.TUESDAY,   "dienstag" to DayOfWeek.TUESDAY,
            "mi" to DayOfWeek.WEDNESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
            "do" to DayOfWeek.THURSDAY,  "donnerstag" to DayOfWeek.THURSDAY,
            "fr" to DayOfWeek.FRIDAY,    "freitag" to DayOfWeek.FRIDAY,
            "sa" to DayOfWeek.SATURDAY,  "samstag" to DayOfWeek.SATURDAY,
            "so" to DayOfWeek.SUNDAY,    "sonntag" to DayOfWeek.SUNDAY
        )

        fun load(path: String): NaechsteSpieleModuleConfig {
            val props = loadProps(path)
            val daysRaw = props["days"]?.trim() ?: ""
            val days = if (daysRaw.isBlank()) emptyList()
                       else daysRaw.split(",").mapNotNull { dayMap[it.trim().lowercase()] }

            return NaechsteSpieleModuleConfig(
                team         = props["team"] ?: error("[$path] 'team' fehlt"),
                liga         = props["liga"] ?: "bl1",
                anzahl       = props["anzahl"]?.toIntOrNull() ?: 3,
                channel      = props["channel"] ?: "sportnews",
                minuteOffset = props["minute_offset"]?.toIntOrNull() ?: 0,
                days         = days
            ).also {
                val daysStr = if (it.days.isEmpty()) "täglich" else it.days.joinToString(", ") { d -> d.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                println("✅ [Nächste Spiele] ${it.team} (${it.ligaName}), ${it.anzahl} Spiele, Channel: ${it.channel}, Tage: $daysStr")
            }
        }
    }
}
