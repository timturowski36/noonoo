package domain.model

interface Module<T : ModuleSettings> {
    val name: String
    val settings: T
    fun getAvailableQueries(): List<Query<T, *, *>>
}