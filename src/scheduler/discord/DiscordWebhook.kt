package scheduler.discord

import java.net.HttpURLConnection
import java.net.URI

/**
 * Sendet Nachrichten an Discord via Webhook.
 */
class DiscordWebhook(private val webhookUrl: String) {

    /**
     * Sendet eine einfache Textnachricht.
     */
    fun send(message: String): Boolean {
        return sendPayload("""{"content": ${escapeJson(message)}}""")
    }

    /**
     * Sendet eine Embed-Nachricht mit Titel und Beschreibung.
     */
    fun sendEmbed(
        title: String,
        description: String,
        color: Int = 0x5865F2,
        footer: String? = null
    ): Boolean {
        val footerJson = footer?.let { """, "footer": {"text": ${escapeJson(it)}}""" } ?: ""
        val payload = """
            {
                "embeds": [{
                    "title": ${escapeJson(title)},
                    "description": ${escapeJson(description)},
                    "color": $color
                    $footerJson
                }]
            }
        """.trimIndent()
        return sendPayload(payload)
    }

    /**
     * Sendet eine Embed-Nachricht mit Code-Block.
     */
    fun sendCodeBlock(title: String, content: String, language: String = ""): Boolean {
        val codeBlock = "```$language\n$content\n```"
        return sendEmbed(title, codeBlock)
    }

    private fun sendPayload(jsonPayload: String): Boolean {
        return try {
            val connection = URI(webhookUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                println("✅ [Discord] Nachricht gesendet")
                true
            } else {
                println("❌ [Discord] Fehler: HTTP $responseCode")
                false
            }
        } catch (e: Exception) {
            println("❌ [Discord] Fehler: ${e.message}")
            false
        }
    }

    private fun escapeJson(text: String): String {
        val escaped = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    companion object {
        // Discord Embed Farben
        const val COLOR_GREEN = 0x57F287
        const val COLOR_RED = 0xED4245
        const val COLOR_BLUE = 0x5865F2
        const val COLOR_YELLOW = 0xFEE75C
        const val COLOR_ORANGE = 0xE67E22
    }
}
