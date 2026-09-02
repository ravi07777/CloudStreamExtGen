package com.cloudstreamextgen.analysis.detectors

import com.cloudstreamextgen.analysis.HtmlAnalyzer
import com.cloudstreamextgen.models.ContentAnalysis
import com.cloudstreamextgen.models.SiteType

class ContentDetector {

    fun detect(html: String, baseUrl: String, siteType: SiteType): ContentAnalysis {
        val htmlAnalyzer = HtmlAnalyzer()
        val structure = htmlAnalyzer.analyzeStructure(html)
        val contentPatterns = htmlAnalyzer.findContentPatterns(html)
        val pagination = htmlAnalyzer.findPagination(html)

        if (contentPatterns.isNotEmpty()) {
            val bestPattern = contentPatterns.maxByOrNull { it.itemCount }!!
            return ContentAnalysis(
                mainPageUrl = baseUrl,
                contentListSelector = bestPattern.containerSelector,
                contentItemSelector = bestPattern.itemSelector,
                titleSelector = guessTitleSelector(bestPattern.sampleData),
                urlSelector = "a[href]",
                imageSelector = "img[src], img[data-src]",
                hasCategories = detectCategories(html),
                categoryUrls = detectCategoryUrls(html, baseUrl),
                paginationSelector = pagination.selector,
                paginationPattern = pagination.pattern,
                isApiContent = siteType == SiteType.JSON_API,
                apiEndpoint = if (siteType == SiteType.JSON_API) baseUrl else ""
            )
        }

        return ContentAnalysis(
            mainPageUrl = baseUrl,
            isApiContent = siteType == SiteType.JSON_API,
            apiEndpoint = if (siteType == SiteType.JSON_API) baseUrl else ""
        )
    }

    private fun guessTitleSelector(sampleData: Map<String, String>): String {
        if (sampleData.containsKey("title")) return ".title, .name, h2, h3"
        return "h2, h3, .title, .name"
    }

    private fun detectCategories(html: String): Boolean {
        val lowerHtml = html.lowercase()
        return lowerHtml.contains("genre") || lowerHtml.contains("category") ||
                lowerHtml.contains("type") || lowerHtml.contains("filter") ||
                lowerHtml.contains("country") || lowerHtml.contains("year")
    }

    private fun detectCategoryUrls(html: String, baseUrl: String): Map<String, String> {
        val categories = mutableMapOf<String, String>()
        val document = org.jsoup.Jsoup.parse(html)

        val genreLinks = document.select("a[href*=genre], a[href*=category], a[href*=type], .genre a, .category a")
        for (link in genreLinks) {
            val text = link.text().trim()
            val href = link.attr("href")
            if (text.isNotEmpty() && href.isNotEmpty()) {
                categories[text] = if (href.startsWith("http")) href else "$baseUrl$href"
            }
        }
        return categories
    }
}
