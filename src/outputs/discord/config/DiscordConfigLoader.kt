package outputs.discord.config

import java.io.File

object DiscordConfigLoader {

    private const val CONFIG_DIR = "input/discord"
    private const val CHANNELS_DIR = "$CONFIG_DIR/channels"
    private const val BOT_TOKEN_FILE = "$CONFIG_DIR/bot_token.txt"

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
     * Die Datei muss unter input/discord/channels/{channelName}.txt liegen
     */
    fun loadChannelId(channelName: String): String? {
        val file = File("$CHANNELS_DIR/$channelName.txt")

        if (!file.exists()) {
            println("❌ Channel '$channelName' nicht gefunden!")
            println("   Bitte erstelle: $CHANNELS_DIR/$channelName.txt")
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
     * Lädt alle verfügbaren Channels aus dem Channels-Ordner
     */
    fun loadAllChannels(): Map<String, String> {
        val channelsDir = File(CHANNELS_DIR)
        if (!channelsDir.exists() || !channelsDir.isDirectory) {
            println("❌ Channels-Ordner nicht gefunden: $CHANNELS_DIR")
            return emptyMap()
        }

        val channels = mutableMapOf<String, String>()
        channelsDir.listFiles { file -> file.extension == "txt" }?.forEach { file ->
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
