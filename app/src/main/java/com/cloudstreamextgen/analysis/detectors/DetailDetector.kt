package com.cloudstreamextgen.analysis.detectors

import com.cloudstreamextgen.models.DetailAnalysis
import com.cloudstreamextgen.models.SiteType

class DetailDetector {

    fun detect(html: String, baseUrl: String, siteType: SiteType): DetailAnalysis {
        val document = org.jsoup.Jsoup.parse(html)

        val hasDetails = document.select(".movie-info, .show-info, .detail, .film-detail, .content-detail, article, .entry-content").isNotEmpty()

        if (!hasDetails) {
            return DetailAnalysis()
        }

        return DetailAnalysis(
            detailSelector = findDetailSelector(document),
            titleSelector = findTitleSelector(document),
            descriptionSelector = findDescriptionSelector(document),
            imageSelector = findImageSelector(document),
            yearSelector = findYearSelector(document),
            ratingSelector = findRatingSelector(document),
            genreSelector = findGenreSelector(document),
            directorSelector = findDirectorSelector(document),
            castSelector = findCastSelector(document),
            durationSelector = findDurationSelector(document),
            qualitySelector = findQualitySelector(document),
            hasSeasons = document.select(".season, [data-season], .seasons-list, #seasons").isNotEmpty(),
            seasonSelector = findSeasonSelector(document)
        )
    }

    private fun findDetailSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".movie-info", ".show-info", ".detail", ".film-detail",
            ".content-detail", ".entry-content", "article", ".info",
            ".film-info", ".video-info"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: "article"
    }

    private fun findTitleSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".movie-title", ".show-title", ".film-title", ".title",
            "h1.title", "h1", ".entry-title", ".content-title"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: "h1"
    }

    private fun findDescriptionSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".movie-description", ".show-description", ".description",
            ".plot", ".synopsis", ".summary", ".overview", ".content-description",
            "meta[name=description]", ".entry-content p"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".description, .plot, .summary"
    }

    private fun findImageSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".movie-poster", ".show-poster", ".film-poster", ".poster img",
            ".movie-image img", "meta[property=og:image]", ".backdrop img",
            ".content-image img", ".thumb img"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".poster img, .movie-image img"
    }

    private fun findYearSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".year", ".release-year", ".movie-year", ".date",
            "time", ".release-date", "[itemprop=datePublished]"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".year, .date"
    }

    private fun findRatingSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".rating", ".score", ".stars", ".imdb", ".rating-value",
            "[itemprop=ratingValue]", ".vote", ".user-rating"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".rating, .score"
    }

    private fun findGenreSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".genre", ".genres", ".category", ".tags", "[itemprop=genre]",
            ".movie-genre", ".show-genre", ".film-genre"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".genre, .genres, .category"
    }

    private fun findDirectorSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".director", "[itemprop=director]", ".movie-director",
            ".show-director", ".crew-director"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".director"
    }

    private fun findCastSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".cast", ".actors", ".starring", "[itemprop=actor]",
            ".movie-cast", ".show-cast"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".cast, .actors"
    }

    private fun findDurationSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".duration", ".runtime", "[itemprop=duration]", ".length",
            ".movie-duration"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".duration, .runtime"
    }

    private fun findQualitySelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".quality", ".resolution", ".video-quality", ".hd",
            ".badge-quality"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".quality"
    }

    private fun findSeasonSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".season", ".seasons", "#seasons", ".season-list",
            "[data-season]", ".season-tabs"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".season, .seasons"
    }
}
