package outputs.discord

import outputs.discord.config.DiscordConfigLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DiscordBot(
    private val botToken: String
) {
    private val httpClient = HttpClient.newHttpClient()
    private val baseUrl = "https://discord.com/api/v10"

    /**
     * Sendet eine Nachricht an einen Channel (per Channel-ID)
     */
    fun sendMessage(channelId: String, message: String): Boolean {
        return try {
            val jsonBody = """{"content": "${escapeJson(message)}"}"""

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/channels/$channelId/messages"))
                .header("Authorization", "Bot $botToken")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() in 200..299) {
                println("✅ Nachricht gesendet an Channel $channelId")
                true
            } else {
                println("❌ Fehler beim Senden: HTTP ${response.statusCode()}")
                println("   Response: ${response.body()}")
                false
            }
        } catch (e: Exception) {
            println("❌ Fehler beim Senden: ${e.message}")
            false
        }
    }

    /**
     * Sendet eine Nachricht an einen Channel (per Channel-Name)
     * Lädt die Channel-ID automatisch aus der Konfiguration
     */
    fun sendMessageToChannel(channelName: String, message: String): Boolean {
        val channelId = DiscordConfigLoader.loadChannelId(channelName)
        if (channelId == null) {
            println("❌ Konnte Nachricht nicht senden - Channel '$channelName' nicht konfiguriert")
            return false
        }
        return sendMessage(channelId, message)
    }

    /**
     * Sendet eine Nachricht an mehrere Channels
     */
    fun sendMessageToChannels(channelNames: List<String>, message: String): Map<String, Boolean> {
        return channelNames.associateWith { channelName ->
            sendMessageToChannel(channelName, message)
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        /**
         * Erstellt einen DiscordBot mit Token aus der Konfiguration
         */
        fun create(): DiscordBot? {
            val token = DiscordConfigLoader.loadBotToken() ?: return null
            return DiscordBot(token)
        }
    }
}
