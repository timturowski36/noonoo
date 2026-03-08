package sources.bundesliga.model

import domain.model.ModuleSettings

data class BundesligaSettings(
    val lieblingsverein: String = "Schalke",
    val liga: String = "bl2"
) : ModuleSettings