package sources.bundesliga

import domain.model.Module
import domain.model.Query
import sources.bundesliga.model.BundesligaSettings
import sources.bundesliga.queries.TabelleQuery

class BundesligaModule(
override val settings: BundesligaSettings
) : Module<BundesligaSettings> {
    override val name: String = "Bundesliga"

    override fun getAvailableQueries(): List<Query<BundesligaSettings, *, *>> {
        return listOf(
            TabelleQuery()
        )
    }
}