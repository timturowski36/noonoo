plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "de.noonoo"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("de.noonoo.ApplicationKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.2"
val koinVersion = "4.1.1"
val coroutinesVersion = "1.9.0"

dependencies {
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    // Ktor Client (HTTP)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")

    // kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")

    // DuckDB JDBC
    implementation("org.duckdb:duckdb_jdbc:1.5.1.0")

    // Koin Dependency Injection
    implementation("io.insert-koin:koin-core:$koinVersion")

    // Discord Webhooks (bestehende Ausgabe-Module)
    implementation("club.minnced:discord-webhooks:0.8.4")

    // JDA – Discord Gateway für Command-Empfang (!analyse)
    implementation("net.dv8tion:JDA:5.3.0") {
        exclude(module = "opus-java")
    }

    // dotenv (Secrets aus .env)
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")

    // Jsoup (HTML-Parsing)
    implementation("org.jsoup:jsoup:1.22.1")

    // Playwright (Fallback-Scraping für handball_statistics)
    implementation("com.microsoft.playwright:playwright:1.48.0")

    // Anthropic Java SDK (optional, Phase 4)
    implementation("com.anthropic:anthropic-java:2.19.0")

    // YAML Config Parsing
    implementation("com.charleskorn.kaml:kaml:0.65.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "de.feedkrake.ApplicationKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
