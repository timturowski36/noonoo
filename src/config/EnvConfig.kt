package config

import java.io.File

/**
 * Zentrale Konfiguration für alle API-Keys und Credentials.
 * Liest Werte aus der .env Datei im Root-Verzeichnis.
 */
object EnvConfig {

    private const val ENV_FILE = ".env"
    private var config: Map<String, String> = emptyMap()
    private var loaded = false

    /**
     * Lädt die .env Datei. Wird automatisch beim ersten Zugriff aufgerufen.
     */
    fun load(): Boolean {
        if (loaded) return config.isNotEmpty()

        val file = File(ENV_FILE)
        if (!file.exists()) {
            println("❌ [Config] .env Datei nicht gefunden!")
            println("   Kopiere .env.example zu .env und trage deine Keys ein:")
            println("   cp .env.example .env")
            loaded = true
            return false
        }

        config = file.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }

        loaded = true
        println("✅ [Config] ${config.size} Einträge aus .env geladen")
        return true
    }

    /**
     * Holt einen Wert aus der Konfiguration.
     */
    fun get(key: String): String? {
        if (!loaded) load()
        return config[key]
    }

    /**
     * Holt einen Wert oder wirft eine Exception wenn nicht vorhanden.
     */
    fun getRequired(key: String): String {
        return get(key) ?: throw IllegalStateException("Config key '$key' nicht gefunden in .env")
    }

    /**
     * Prüft ob ein Key existiert und einen Wert hat.
     */
    fun has(key: String): Boolean {
        if (!loaded) load()
        val value = config[key]
        return value != null && value.isNotBlank() && !value.contains("DEIN_")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Discord Webhooks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Holt die Webhook-URL für einen Discord-Channel.
     * Der Channel-Name wird automatisch in Großbuchstaben konvertiert.
     *
     * Beispiel: discordWebhook("gaming") → DISCORD_WEBHOOK_GAMING
     */
    fun discordWebhook(channel: String): String? {
        val key = "DISCORD_WEBHOOK_${channel.uppercase()}"
        val value = get(key)

        if (value == null) {
            println("⚠️ [Config] Discord Webhook '$channel' nicht konfiguriert")
            println("   Füge in .env hinzu: $key=https://discord.com/api/webhooks/...")
            return null
        }

        if (!value.startsWith("https://discord.com/api/webhooks/") || value.contains("DEIN_")) {
            println("⚠️ [Config] Ungültiger Webhook für '$channel'")
            return null
        }

        return value
    }

    /**
     * Listet alle konfigurierten Discord-Channels auf.
     */
    fun discordChannels(): List<String> {
        if (!loaded) load()
        return config.keys
            .filter { it.startsWith("DISCORD_WEBHOOK_") }
            .map { it.removePrefix("DISCORD_WEBHOOK_").lowercase() }
            .filter { has("DISCORD_WEBHOOK_${it.uppercase()}") }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // API Keys
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * PUBG API Key
     */
    fun pubgApiKey(): String? {
        val key = get("PUBG_API_KEY")
        if (key == null || key.contains("dein-")) {
            println("⚠️ [Config] PUBG API Key nicht konfiguriert")
            println("   Hole dir einen Key von: https://developer.pubg.com/")
            return null
        }
        return key
    }

    /**
     * Claude API Key
     */
    fun claudeApiKey(): String? {
        val key = get("CLAUDE_API_KEY")
        if (key == null || key.contains("dein-")) {
            println("⚠️ [Config] Claude API Key nicht konfiguriert")
            println("   Hole dir einen Key von: https://console.anthropic.com/")
            return null
        }
        return key
    }

    /**
     * BF6 API Key
     */
    fun bf6ApiKey(): String? {
        val key = get("BF6_API_KEY")
        if (key == null || key.contains("dein-")) {
            println("⚠️ [Config] BF6 API Key nicht konfiguriert")
            return null
        }
        return key
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Debug / Status
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Zeigt den Konfigurationsstatus (Keys werden maskiert).
     */
    fun printStatus() {
        if (!loaded) load()

        println("""

    ╔═══════════════════════════════════════════════════════════════╗
    ║              🐙 FeedKrake - Konfiguration                     ║
    ╚═══════════════════════════════════════════════════════════════╝
        """.trimIndent())

        println("\n📡 Discord Webhooks:")
        discordChannels().forEach { channel ->
            println("   ✅ $channel")
        }
        if (discordChannels().isEmpty()) {
            println("   ❌ Keine Webhooks konfiguriert")
        }

        println("\n🔑 API Keys:")
        println("   PUBG:   ${if (has("PUBG_API_KEY")) "✅ Konfiguriert" else "❌ Fehlt"}")
        println("   Claude: ${if (has("CLAUDE_API_KEY")) "✅ Konfiguriert" else "❌ Fehlt"}")
        println("   BF6:    ${if (has("BF6_API_KEY")) "✅ Konfiguriert" else "❌ Fehlt"}")

        println("\n📝 Konfiguration: .env")
    }

    /**
     * Maskiert einen API-Key für die Anzeige.
     */
    private fun maskKey(key: String?): String {
        if (key == null || key.length < 8) return "***"
        return "${key.take(4)}...${key.takeLast(4)}"
    }
}
