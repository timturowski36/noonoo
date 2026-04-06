package de.aggregator.domain.port.output

interface NotificationPort {
    suspend fun send(channel: String, message: String)
}
