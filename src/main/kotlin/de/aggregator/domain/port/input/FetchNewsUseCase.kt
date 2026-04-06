package de.aggregator.domain.port.input

interface FetchNewsUseCase {
    suspend fun fetchAndStoreNews(url: String, sourceName: String)
}
