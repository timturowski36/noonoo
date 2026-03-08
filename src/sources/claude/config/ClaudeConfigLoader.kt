package sources.claude.config

import java.io.File

object ClaudeConfigLoader {

    fun loadApiKey(): String? {
        // 1. Zuerst Umgebungsvariable prüfen
        val envKey = System.getenv("CLAUDE_API_KEY")
        if (!envKey.isNullOrBlank()) {
            println("✅ [Claude] API-Key aus Umgebungsvariable geladen.")
            return envKey
        }

        // 2. Dann .env Datei im Projektroot prüfen
        val envFile = File(".env")
        if (envFile.exists()) {
            val key = parseEnvFile(envFile, "CLAUDE_API_KEY")
            if (!key.isNullOrBlank()) {
                println("✅ [Claude] API-Key aus .env geladen.")
                return key
            }
        }

        // 3. Fallback: alte Datei (für Kompatibilität)
        val legacyFile = File("src/sources/claude/config/claude_api_key.txt")
        if (legacyFile.exists()) {
            val key = legacyFile.readText().trim()
            if (key.isNotBlank()) {
                println("✅ [Claude] API-Key aus legacy-Datei geladen.")
                return key
            }
        }

        println("❌ [Claude] API-Key nicht gefunden!")
        println("   Option 1: CLAUDE_API_KEY in .env setzen")
        println("   Option 2: Umgebungsvariable CLAUDE_API_KEY setzen")
        return null
    }

    private fun parseEnvFile(file: File, key: String): String? {
        return file.readLines()
            .map { it.trim() }
            .filter { !it.startsWith("#") && it.contains("=") }
            .map { line ->
                val idx = line.indexOf("=")
                line.substring(0, idx) to line.substring(idx + 1)
            }
            .firstOrNull { it.first == key }
            ?.second
            ?.trim()
    }
}
