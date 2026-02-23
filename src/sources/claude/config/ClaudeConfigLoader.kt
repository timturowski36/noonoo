package sources.claude.config

import java.io.File

object ClaudeConfigLoader {
    private val configDir = File("src/sources/claude/config")
    private val apiKeyFile = File(configDir, "claude_api_key.txt")

    fun loadApiKey(): String? {
        if (!apiKeyFile.exists()) {
            println("❌ [Claude] API-Key Datei nicht gefunden: ${apiKeyFile.absolutePath}")
            return null
        }
        val key = apiKeyFile.readText().trim()
        if (key.isEmpty()) {
            println("❌ [Claude] API-Key Datei ist leer.")
            return null
        }
        println("✅ [Claude] API-Key geladen.")
        return key
    }
}
