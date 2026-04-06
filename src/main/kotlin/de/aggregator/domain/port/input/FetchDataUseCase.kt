package de.aggregator.domain.port.input

interface FetchDataUseCase {
    suspend fun fetchAndStore(league: String, season: Int)
}
