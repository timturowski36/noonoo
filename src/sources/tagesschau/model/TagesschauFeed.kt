package sources.tagesschau.model

enum class TagesschauFeed(val url: String, val displayName: String) {
    ALLE("https://www.tagesschau.de/xml/rss2", "Tagesschau"),
    INLAND("https://www.tagesschau.de/xml/rss2_inland", "Inland"),
    AUSLAND("https://www.tagesschau.de/xml/rss2_ausland", "Ausland"),
    WIRTSCHAFT("https://www.tagesschau.de/xml/rss2_wirtschaft", "Wirtschaft"),
    SPORT("https://www.tagesschau.de/xml/rss2_sport", "Sport"),
    INVESTIGATIV("https://www.tagesschau.de/xml/rss2_investigativ", "Investigativ"),
    WISSEN("https://www.tagesschau.de/xml/rss2_wissen", "Wissen"),
    FAKTENFINDER("https://www.tagesschau.de/xml/rss2_faktenfinder", "Faktenfinder")
}
