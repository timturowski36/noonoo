package sources.pubg.model

import domain.model.ModuleSettings

data class PubgSettings(
    val playerName: String,
    val platform: String = "steam",
    val apiKey: String
) : ModuleSettings