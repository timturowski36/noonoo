package outputs.discord.config

import java.io.File

object DiscordConfigLoader {

    private const val CONFIG_DIR = "src/outputs/discord/config"
    private const val BOT_TOKEN_FILE = "$CONFIG_DIR/allgemein.txt"

    /**
     * Lädt den Bot-Token aus der Konfigurationsdatei
     */
    fun loadBotToken(): String? {
        val file = File(BOT_TOKEN_FILE)

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

        if (!file.exists()) {
            println("❌ Channel '$channelName' nicht gefunden!")
            println("   Bitte erstelle: $CONFIG_DIR/$channelName.txt")
            println("   Inhalt: Die Channel-ID (Rechtsklick auf Channel → ID kopieren)")
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
     * Lädt alle verfügbaren Channels aus dem Config-Ordner
     * (alle .txt Dateien außer bot_token.txt)
     */
    fun loadAllChannels(): Map<String, String> {
        val configDir = File(CONFIG_DIR)
        if (!configDir.exists() || !configDir.isDirectory) {
            println("❌ Config-Ordner nicht gefunden: $CONFIG_DIR")
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
