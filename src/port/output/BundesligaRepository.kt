package port.output

import sources.bundesliga.model.Spiel
import sources.bundesliga.model.TabellenEintrag

interface BundesligaRepository {
    suspend fun getTabelle(liga: String): List<TabellenEintrag>
    suspend fun getNaechsteSpiele(verein: String, liga: String, anzahl: Int): List<Spiel>
}