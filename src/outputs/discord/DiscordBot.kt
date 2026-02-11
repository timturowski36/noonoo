package outputs.discord

import outputs.discord.config.DiscordConfigLoader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Discord Webhook Client - sendet Nachrichten über Discord Webhooks
 */
class DiscordBot {
    private val httpClient = HttpClient.newHttpClient()

    /**
     * Sendet eine Nachricht an eine Webhook-URL
     */
    fun sendToWebhook(webhookUrl: String, message: String): Boolean {
        return try {
            val jsonBody = """{"content": "${escapeJson(message)}"}"""

            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() in 200..299) {
                println("✅ Nachricht gesendet!")
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
     * Lädt die Webhook-URL automatisch aus der Konfiguration
     */
    fun sendMessageToChannel(channelName: String, message: String): Boolean {
        val webhookUrl = DiscordConfigLoader.loadWebhookUrl(channelName)
        if (webhookUrl == null) {
            println("❌ Konnte Nachricht nicht senden - Webhook '$channelName' nicht konfiguriert")
            return false
        }
        return sendToWebhook(webhookUrl, message)
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
         * Erstellt einen DiscordBot (kein Token nötig für Webhooks)
         */
        fun create(): DiscordBot {
            return DiscordBot()
        }
    }
}
