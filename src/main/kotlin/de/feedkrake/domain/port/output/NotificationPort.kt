package de.feedkrake.domain.port.output

interface NotificationPort {
    suspend fun send(channel: String, message: String)
}
