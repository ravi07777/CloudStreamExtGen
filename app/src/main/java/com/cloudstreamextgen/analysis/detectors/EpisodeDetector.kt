package com.cloudstreamextgen.analysis.detectors

import com.cloudstreamextgen.models.EpisodeAnalysis
import com.cloudstreamextgen.models.SiteType

class EpisodeDetector {

    fun detect(html: String, baseUrl: String, siteType: SiteType): EpisodeAnalysis {
        val document = org.jsoup.Jsoup.parse(html)

        val hasEpisodes = document.select(
            ".episode, .ep-item, .ep-list, .episode-list, [data-episode], " +
            ".episodes, .ep, .video-list, .server-list"
        ).isNotEmpty()

        val hasSeasons = document.select(
            ".season, .seasons, .season-list, [data-season], " +
            ".season-tabs, .season-dropdown"
        ).isNotEmpty()

        if (!hasEpisodes && !hasSeasons) {
            return EpisodeAnalysis()
        }

        return EpisodeAnalysis(
            hasEpisodes = hasEpisodes,
            hasSeasons = hasSeasons,
            seasonSelector = findSeasonSelector(document),
            episodeSelector = findEpisodeSelector(document),
            episodeTitleSelector = findEpisodeTitleSelector(document),
            episodeNumberSelector = findEpisodeNumberSelector(document),
            episodeUrlSelector = findEpisodeUrlSelector(document),
            episodeImageSelector = findEpisodeImageSelector(document),
            isApiEpisodes = siteType == SiteType.JSON_API,
            episodeApiEndpoint = if (siteType == SiteType.JSON_API) baseUrl else ""
        )
    }

    private fun findSeasonSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".season", ".seasons", "#seasons", ".season-list",
            ".season-tabs", "[data-season]", ".season-dropdown",
            ".seasons-menu", "#season-list"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".season, .seasons"
    }

    private fun findEpisodeSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".episode", ".ep-item", ".ep", ".episodes li",
            ".episode-list li", "[data-episode]", ".video-item",
            ".server-list li", ".episode-link"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".episode, .ep-item"
    }

    private fun findEpisodeTitleSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".episode-title", ".ep-title", ".ep-name",
            ".episode-name", "span.title", ".name"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".episode-title, .ep-title"
    }

    private fun findEpisodeNumberSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".episode-number", ".ep-number", ".ep-num",
            "[data-number]", ".number"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".episode-number"
    }

    private fun findEpisodeUrlSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".episode a[href]", ".ep-item a[href]", ".ep a[href]",
            ".episode-link", ".episode-list a"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".episode a[href]"
    }

    private fun findEpisodeImageSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".episode-img img", ".ep-img img", ".episode-image img",
            ".thumb img", ".episode img"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".episode img"
    }
}
