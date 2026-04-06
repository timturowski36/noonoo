package de.feedkrake.adapter.config

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object DatabaseConfig {

    private const val DB_PATH = "data/feedkrake.duckdb"

    fun connect(): Connection {
        File("data").mkdirs()
        return DriverManager.getConnection("jdbc:duckdb:$DB_PATH")
    }

    fun initSchema(connection: Connection) {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS teams (
                    id          INTEGER PRIMARY KEY,
                    name        VARCHAR NOT NULL,
                    short_name  VARCHAR,
                    icon_url    VARCHAR
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS matches (
                    id            INTEGER PRIMARY KEY,
                    league        VARCHAR NOT NULL,
                    season        INTEGER NOT NULL,
                    matchday      INTEGER NOT NULL,
                    home_team_id  INTEGER REFERENCES teams(id),
                    away_team_id  INTEGER REFERENCES teams(id),
                    kickoff_at    TIMESTAMP,
                    home_score_ht INTEGER,
                    away_score_ht INTEGER,
                    home_score_ft INTEGER,
                    away_score_ft INTEGER,
                    is_finished   BOOLEAN NOT NULL DEFAULT false,
                    fetched_at    TIMESTAMP NOT NULL
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS goals (
                    id          INTEGER PRIMARY KEY,
                    match_id    INTEGER REFERENCES matches(id),
                    scorer_name VARCHAR,
                    minute      INTEGER,
                    is_own_goal BOOLEAN NOT NULL DEFAULT false,
                    is_penalty  BOOLEAN NOT NULL DEFAULT false,
                    score_home  INTEGER,
                    score_away  INTEGER
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS standings (
                    league         VARCHAR NOT NULL,
                    season         INTEGER NOT NULL,
                    position       INTEGER NOT NULL,
                    team_id        INTEGER REFERENCES teams(id),
                    played         INTEGER NOT NULL DEFAULT 0,
                    won            INTEGER NOT NULL DEFAULT 0,
                    draw           INTEGER NOT NULL DEFAULT 0,
                    lost           INTEGER NOT NULL DEFAULT 0,
                    goals_for      INTEGER NOT NULL DEFAULT 0,
                    goals_against  INTEGER NOT NULL DEFAULT 0,
                    points         INTEGER NOT NULL DEFAULT 0,
                    fetched_at     TIMESTAMP NOT NULL,
                    PRIMARY KEY (league, season, position)
                )
            """.trimIndent())

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS articles (
                    url          VARCHAR PRIMARY KEY,
                    source       VARCHAR NOT NULL,
                    title        VARCHAR NOT NULL,
                    published_at TIMESTAMP,
                    fetched_at   TIMESTAMP NOT NULL
                )
            """.trimIndent())
        }
    }
}
