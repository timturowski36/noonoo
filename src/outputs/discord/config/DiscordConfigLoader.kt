package outputs.discord.config

import java.io.File

object DiscordConfigLoader {

    // Config-Verzeichnis liegt im selben Ordner wie diese Klasse
    private const val CONFIG_DIR = "src/outputs/discord/config"

    /**
     * Lädt die Webhook-URL für einen bestimmten Channel-Namen
     * Die Datei muss unter src/outputs/discord/config/{channelName}.txt liegen
     */
    fun loadWebhookUrl(channelName: String): String? {
        val file = File("$CONFIG_DIR/$channelName.txt")

        println("📂 Suche Webhook '$channelName' in: ${file.absolutePath}")

        if (!file.exists()) {
            println("❌ Webhook '$channelName' nicht gefunden!")
            println("   Bitte erstelle: $CONFIG_DIR/$channelName.txt")
            println("   Inhalt: Die Webhook-URL (Server Settings → Integrations → Webhooks)")
            listAvailableWebhooks()
            return null
        }

        val webhookUrl = file.readText().trim()
        if (webhookUrl.isEmpty()) {
            println("❌ Webhook-Datei '$channelName.txt' ist leer!")
            return null
        }

        if (!webhookUrl.startsWith("https://discord.com/api/webhooks/")) {
            println("⚠️ Ungültige Webhook-URL in '$channelName.txt'")
            println("   URL muss mit 'https://discord.com/api/webhooks/' beginnen")
            return null
        }

        println("✅ Webhook '$channelName' geladen")
        return webhookUrl
    }

    /**
     * Listet alle verfügbaren Webhook-Dateien im Config-Ordner auf
     */
    private fun listAvailableWebhooks() {
        val configDir = File(CONFIG_DIR)
        if (configDir.exists() && configDir.isDirectory) {
            val files = configDir.listFiles { file ->
                file.extension == "txt"
            }
            if (files != null && files.isNotEmpty()) {
                println("   Verfügbare Webhooks: ${files.map { it.nameWithoutExtension }.joinToString(", ")}")
            } else {
                println("   Keine Webhook-Dateien gefunden in: ${configDir.absolutePath}")
            }
        }
    }

    /**
     * Lädt alle verfügbaren Webhooks aus dem Config-Ordner
     */
    fun loadAllWebhooks(): Map<String, String> {
        val configDir = File(CONFIG_DIR)
        if (!configDir.exists() || !configDir.isDirectory) {
            println("❌ Config-Ordner nicht gefunden: ${configDir.absolutePath}")
            return emptyMap()
        }

        val webhooks = mutableMapOf<String, String>()
        configDir.listFiles { file ->
            file.extension == "txt"
        }?.forEach { file ->
            val channelName = file.nameWithoutExtension
            val webhookUrl = file.readText().trim()
            if (webhookUrl.startsWith("https://discord.com/api/webhooks/")) {
                webhooks[channelName] = webhookUrl
            }
        }

        println("✅ ${webhooks.size} Webhooks geladen: ${webhooks.keys.joinToString(", ")}")
        return webhooks
    }
}
