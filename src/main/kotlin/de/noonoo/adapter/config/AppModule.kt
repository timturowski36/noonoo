package de.noonoo.adapter.config

import de.noonoo.adapter.input.scheduler.IngestionScheduler
import de.noonoo.adapter.output.api.OpenLigaDbClient
import de.noonoo.adapter.output.api.RssNewsClient
import de.noonoo.adapter.output.discord.DiscordSender
import de.noonoo.adapter.output.persistence.DuckDbNewsRepository
import de.noonoo.adapter.output.persistence.DuckDbRepository
import de.noonoo.domain.port.input.FetchDataUseCase
import de.noonoo.domain.port.input.FetchNewsUseCase
import de.noonoo.domain.port.input.QueryDataUseCase
import de.noonoo.domain.port.output.FootballApiPort
import de.noonoo.domain.port.output.MatchRepository
import de.noonoo.domain.port.output.NewsApiPort
import de.noonoo.domain.port.output.NewsRepository
import de.noonoo.domain.port.output.NotificationPort
import de.noonoo.domain.service.IngestionService
import de.noonoo.domain.service.NewsIngestionService
import de.noonoo.domain.service.QueryService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val appModule = module {

    // ── Secrets ───────────────────────────────────────────────────────────────
    single {
        dotenv {
            ignoreIfMissing = true
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────
    single<AppConfig> { ConfigLoader.load() }

    // ── Webhook-URLs (Secrets aus .env in Config-Platzhalter einsetzen) ───────
    single<Map<String, String>> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val config = get<AppConfig>()
        config.outputs.discord.channels.mapValues { (_, value) ->
            if (value.startsWith("\${") && value.endsWith("}")) {
                val key = value.removeSurrounding("\${", "}")
                env[key] ?: error("Umgebungsvariable '$key' nicht gesetzt.")
            } else value
        }
    }

    // ── HTTP Client ───────────────────────────────────────────────────────────
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.NONE
            }
        }
    }

    // ── Datenbank ─────────────────────────────────────────────────────────────
    single {
        val connection = DatabaseConfig.connect()
        DatabaseConfig.initSchema(connection)
        connection
    }

    // ── Adapter: Football ─────────────────────────────────────────────────────
    single<FootballApiPort> { OpenLigaDbClient(get()) }
    single<MatchRepository> { DuckDbRepository(get()) }

    // ── Adapter: News ─────────────────────────────────────────────────────────
    single<NewsApiPort> { RssNewsClient(get()) }
    single<NewsRepository> { DuckDbNewsRepository(get()) }

    // ── Adapter: Output ───────────────────────────────────────────────────────
    single<NotificationPort> { DiscordSender(get()) }

    // ── Domain Services ───────────────────────────────────────────────────────
    single<FetchDataUseCase> { IngestionService(get(), get()) }
    single<QueryDataUseCase> { QueryService(get(), get()) }
    single<FetchNewsUseCase> { NewsIngestionService(get(), get()) }

    // ── Scheduler ─────────────────────────────────────────────────────────────
    single {
        IngestionScheduler(
            modules = get<AppConfig>().modules,
            fetchUseCase = get(),
            queryUseCase = get(),
            fetchNewsUseCase = get(),
            newsRepository = get(),
            notificationPort = get(),
            webhookChannels = get()
        )
    }
}
