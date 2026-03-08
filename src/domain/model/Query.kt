package domain.model

interface Query<MS : ModuleSettings, QS : QuerySettings, R> {
    val name: String
    val defaultSettings: QS
    suspend fun execute(moduleSettings: MS, querySettings: QS): QueryResult<R>
}