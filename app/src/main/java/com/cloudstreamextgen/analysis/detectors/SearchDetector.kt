package com.cloudstreamextgen.analysis.detectors

import com.cloudstreamextgen.analysis.HtmlAnalyzer
import com.cloudstreamextgen.models.SearchAnalysis
import com.cloudstreamextgen.models.SiteType

class SearchDetector {

    fun detect(html: String, baseUrl: String, siteType: SiteType): SearchAnalysis {
        val analyzer = HtmlAnalyzer()
        val searchForms = analyzer.findSearchForms(html)

        if (searchForms.isNotEmpty()) {
            val form = searchForms.first()
            return SearchAnalysis(
                hasSearch = true,
                searchUrl = form.action.ifEmpty { "$baseUrl/search" },
                searchParamName = form.inputName,
                searchMethod = form.method,
                searchSelector = form.inputSelector,
                isApiSearch = siteType == SiteType.JSON_API || form.action.contains("api"),
                searchResultSelector = findSearchResultSelector(html)
            )
        }

        val searchUrl = detectSearchApi(html, baseUrl)
        if (searchUrl != null) {
            return SearchAnalysis(
                hasSearch = true,
                searchUrl = searchUrl,
                searchParamName = "q",
                isApiSearch = true,
                searchMethod = "GET"
            )
        }

        val navSearch = detectNavSearch(html, baseUrl)
        if (navSearch != null) {
            return SearchAnalysis(
                hasSearch = true,
                searchUrl = navSearch,
                searchParamName = "q",
                searchMethod = "GET"
            )
        }

        return SearchAnalysis(hasSearch = false)
    }

    private fun findSearchResultSelector(html: String): String {
        val resultSelectors = listOf(
            ".search-results .result", ".search-result", ".results-list li",
            ".movie-card", ".show-card", ".content-card", ".item-card",
            "article.card", ".film-list .item", ".content-list .item"
        )
        val document = org.jsoup.Jsoup.parse(html)
        for (selector in resultSelectors) {
            if (document.select(selector).size >= 2) return selector
        }
        return ""
    }

    private fun detectSearchApi(html: String, baseUrl: String): String? {
        val patterns = listOf(
            Regex("""["']([^"']*(?:search|query|find)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:searchUrl|searchApi|searchEndpoint)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](/api/[^"']*search[^"']*)["']""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val url = match.groupValues[1]
                if (url.startsWith("http")) return url
                if (url.startsWith("/")) return "$baseUrl$url"
            }
        }
        return null
    }

    private fun detectNavSearch(html: String, baseUrl: String): String? {
        val document = org.jsoup.Jsoup.parse(html)
        val searchNav = document.select("a[href*=search], .nav-search, .search-nav, [data-action=search]")
        return searchNav.firstOrNull()?.attr("href")?.let { href ->
            if (href.startsWith("http")) href else "$baseUrl$href"
        }
    }
}
