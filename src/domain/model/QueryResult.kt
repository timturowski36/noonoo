package domain.model

sealed class QueryResult<out T> {
    data class Success<T>(val data: T) : QueryResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : QueryResult<Nothing>()
    data object Loading : QueryResult<Nothing>()
}