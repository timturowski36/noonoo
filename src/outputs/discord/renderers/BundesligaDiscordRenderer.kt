package outputs.discord.renderers

import sources.bundesliga.model.TabellenEintrag

class BundesligaDiscordRenderer {
    fun createDiscordMessage(tabellenEintraege: List<TabellenEintrag>): String {
        return tabellenEintraege.joinToString("\n") { eintrag ->
            val tdFormatted = if (eintrag.goalDiff > 0) "+${eintrag.goalDiff}" else "${eintrag.goalDiff}"
            "[${eintrag.liga.uppercase()}] ${eintrag.platz} - ${eintrag.shortName} - ${eintrag.points} Punkte aus ${eintrag.matches} Spielen - TD: $tdFormatted"
        }
    }
}