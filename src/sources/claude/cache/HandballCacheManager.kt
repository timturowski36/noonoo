package sources.claude.cache

import sources.claude.model.HandballResults
import sources.claude.model.HandballSchedule
import sources.claude.model.HandballTable
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Cache-Manager für Handball-Daten.
 * Speichert Ergebnisse wöchentlich und verhindert unnötige API-Aufrufe.
 */
class HandballCacheManager(
    private val cacheDir: String = "src/sources/claude/cache/data"
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        File(cacheDir).mkdirs()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wochenstart berechnen (Montag 00:00)
    // ─────────────────────────────────────────────────────────────────────────

    private fun getCurrentWeekStart(): LocalDate {
        val now = LocalDate.now(ZoneId.of("Europe/Berlin"))
        return now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun getCacheFileName(type: String): String {
        val weekStart = getCurrentWeekStart().format(dateFormatter)
        return "$cacheDir/handball_${type}_$weekStart.json"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule Cache
    // ─────────────────────────────────────────────────────────────────────────

    fun hasScheduleCache(): Boolean {
        return File(getCacheFileName("schedule")).exists()
    }

    fun saveSchedule(json: String) {
        File(getCacheFileName("schedule")).writeText(json)
        println("💾 [Cache] Spielplan gespeichert")
    }

    fun loadScheduleJson(): String? {
        val file = File(getCacheFileName("schedule"))
        return if (file.exists()) {
            println("📂 [Cache] Spielplan aus Cache geladen")
            file.readText()
        } else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Results Cache
    // ─────────────────────────────────────────────────────────────────────────

    fun hasResultsCache(): Boolean {
        return File(getCacheFileName("results")).exists()
    }

    fun saveResults(json: String) {
        File(getCacheFileName("results")).writeText(json)
        println("💾 [Cache] Ergebnisse gespeichert")
    }

    fun loadResultsJson(): String? {
        val file = File(getCacheFileName("results"))
        return if (file.exists()) {
            println("📂 [Cache] Ergebnisse aus Cache geladen")
            file.readText()
        } else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Table Cache
    // ─────────────────────────────────────────────────────────────────────────

    fun hasTableCache(): Boolean {
        return File(getCacheFileName("table")).exists()
    }

    fun saveTable(json: String) {
        File(getCacheFileName("table")).writeText(json)
        println("💾 [Cache] Tabelle gespeichert")
    }

    fun loadTableJson(): String? {
        val file = File(getCacheFileName("table"))
        return if (file.exists()) {
            println("📂 [Cache] Tabelle aus Cache geladen")
            file.readText()
        } else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache Status
    // ─────────────────────────────────────────────────────────────────────────

    fun printCacheStatus() {
        val weekStart = getCurrentWeekStart().format(dateFormatter)
        println("📊 [Cache] Woche ab $weekStart:")
        println("   Spielplan: ${if (hasScheduleCache()) "✅" else "❌"}")
        println("   Ergebnisse: ${if (hasResultsCache()) "✅" else "❌"}")
        println("   Tabelle: ${if (hasTableCache()) "✅" else "❌"}")
    }

    /**
     * Löscht alle Cache-Dateien (für manuelles Reset).
     */
    fun clearCache() {
        File(cacheDir).listFiles()?.forEach { it.delete() }
        println("🗑️ [Cache] Cache geleert")
    }
}
