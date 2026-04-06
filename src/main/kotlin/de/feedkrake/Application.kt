package de.feedkrake

import de.feedkrake.adapter.config.appModule
import de.feedkrake.adapter.input.scheduler.IngestionScheduler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

private val log = KotlinLogging.logger {}

fun main(): Unit = runBlocking {
    log.info { "FeedKrake startet..." }

    startKoin {
        modules(appModule)
    }

    val scheduler = getKoin().get<IngestionScheduler>()
    scheduler.start()

    log.info { "FeedKrake läuft. Drücke Ctrl+C zum Beenden." }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "FeedKrake wird beendet..." }
        scheduler.stop()
    })

    // Blockiert bis JVM beendet wird
    Thread.currentThread().join()
}
