package outputs.discord.config

import config.EnvConfig

object DiscordConfigLoader {

    /**
     * Lädt die Webhook-URL für einen bestimmten Channel-Namen aus .env
     * Format in .env: DISCORD_WEBHOOK_{CHANNEL_NAME}=url
     */
    fun loadWebhookUrl(channelName: String): String? {
        return EnvConfig.discordWebhook(channelName)
    }

    /**
     * Lädt alle verfügbaren Webhooks aus der .env
     */
    fun loadAllWebhooks(): Map<String, String> {
        val channels = EnvConfig.discordChannels()
        val webhooks = mutableMapOf<String, String>()

        channels.forEach { channel ->
            EnvConfig.discordWebhook(channel)?.let { url ->
                webhooks[channel] = url
            }
        }

        if (webhooks.isNotEmpty()) {
            println("✅ ${webhooks.size} Webhooks geladen: ${webhooks.keys.joinToString(", ")}")
        }
        return webhooks
    }
}
