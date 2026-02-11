package outputs.discord.config

import java.io.File

object DiscordConfigLoader {

    private const val CONFIG_DIR = "input/discord"
    private const val BOT_TOKEN_FILE = "$CONFIG_DIR/bot_token.txt"

    /**
     * Lädt den Bot-Token aus der Konfigurationsdatei
     */
    fun loadBotToken(): String? {
        val file = File(BOT_TOKEN_FILE)

        // Debug: Zeige Working Directory
        println("📂 Working Directory: ${System.getProperty("user.dir")}")
        println("📂 Suche Bot Token in: ${file.absolutePath}")

        if (!file.exists()) {
            println("❌ Discord Bot Token nicht gefunden!")
            println("   Bitte erstelle: $BOT_TOKEN_FILE")
            println("   Inhalt: Dein Discord Bot Token")
            return null
        }

        val token = file.readText().trim()
        if (token.isEmpty()) {
            println("❌ Discord Bot Token Datei ist leer!")
            return null
        }

        println("✅ Discord Bot Token geladen")
        return token
    }

    /**
     * Lädt die Channel-ID für einen bestimmten Channel-Namen
     * Die Datei muss unter input/discord/{channelName}.txt liegen
     */
    fun loadChannelId(channelName: String): String? {
        val file = File("$CONFIG_DIR/$channelName.txt")

        println("📂 Suche Channel '$channelName' in: ${file.absolutePath}")

        if (!file.exists()) {
            println("❌ Channel '$channelName' nicht gefunden!")
            println("   Bitte erstelle: $CONFIG_DIR/$channelName.txt")
            println("   Inhalt: Die Channel-ID (Rechtsklick auf Channel → ID kopieren)")
            listAvailableChannels()
            return null
        }

        val channelId = file.readText().trim()
        if (channelId.isEmpty()) {
            println("❌ Channel-Datei '$channelName.txt' ist leer!")
            return null
        }

        println("✅ Channel '$channelName' geladen: $channelId")
        return channelId
    }

    /**
     * Listet alle verfügbaren Channel-Dateien im Config-Ordner auf
     */
    private fun listAvailableChannels() {
        val configDir = File(CONFIG_DIR)
        if (configDir.exists() && configDir.isDirectory) {
            val files = configDir.listFiles { file ->
                file.extension == "txt" && file.name != "bot_token.txt"
            }
            if (files != null && files.isNotEmpty()) {
                println("   Verfügbare Channels: ${files.map { it.nameWithoutExtension }.joinToString(", ")}")
            } else {
                println("   Keine Channel-Dateien gefunden in: ${configDir.absolutePath}")
            }
        } else {
            println("   Config-Ordner existiert nicht: ${configDir.absolutePath}")
        }
    }

    /**
     * Lädt alle verfügbaren Channels aus dem Config-Ordner
     * (alle .txt Dateien außer bot_token.txt)
     */
    fun loadAllChannels(): Map<String, String> {
        val configDir = File(CONFIG_DIR)
        if (!configDir.exists() || !configDir.isDirectory) {
            println("❌ Config-Ordner nicht gefunden: $CONFIG_DIR")
            println("   Absoluter Pfad: ${configDir.absolutePath}")
            return emptyMap()
        }

        val channels = mutableMapOf<String, String>()
        configDir.listFiles { file ->
            file.extension == "txt" && file.name != "bot_token.txt"
        }?.forEach { file ->
            val channelName = file.nameWithoutExtension
            val channelId = file.readText().trim()
            if (channelId.isNotEmpty()) {
                channels[channelName] = channelId
            }
        }

        println("✅ ${channels.size} Channels geladen: ${channels.keys.joinToString(", ")}")
        return channels
    }
}
