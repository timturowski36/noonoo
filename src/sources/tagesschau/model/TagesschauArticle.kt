package sources.tagesschau.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TagesschauArticle(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: Instant,
    val guid: String,
    val category: String? = null
) {
    fun isNewerThan(other: TagesschauArticle): Boolean = pubDate.isAfter(other.pubDate)

    fun containsKeyword(keyword: String): Boolean {
        val lowerKeyword = keyword.lowercase()
        return title.lowercase().contains(lowerKeyword) ||
               description.lowercase().contains(lowerKeyword)
    }

    fun discordFormat(): String {
        val dateStr = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"))
            .format(pubDate)
        return "$dateStr | $title"
    }

    fun discordFormatDetailed(): String {
        val dateStr = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"))
            .format(pubDate)
        val desc = if (description.length > 150) description.take(150) + "..." else description
        val categoryTag = category?.let { "[$it] " } ?: ""
        return "**$categoryTag$title**\n   $dateStr\n   $desc\n   $link"
    }
}
