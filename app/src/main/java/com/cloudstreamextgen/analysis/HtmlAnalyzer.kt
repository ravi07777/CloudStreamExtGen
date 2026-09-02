package com.cloudstreamextgen.analysis

import com.cloudstreamextgen.models.SiteType

class HtmlAnalyzer {

    fun analyzeStructure(html: String): HtmlStructure {
        val document = org.jsoup.Jsoup.parse(html)

        return HtmlStructure(
            title = document.title(),
            metaTags = extractMetaTags(document),
            links = extractLinks(document),
            images = extractImages(document),
            forms = extractForms(document),
            scripts = extractScripts(document),
            iframes = extractIframes(document),
            headings = extractHeadings(document),
            divClasses = extractDivClasses(document),
            dataAttributes = extractDataAttributes(document)
        )
    }

    fun findContentPatterns(html: String): List<ContentPattern> {
        val document = org.jsoup.Jsoup.parse(html)
        val patterns = mutableListOf<ContentPattern>()

        val listContainers = document.select("ul, ol, .list, .grid, .content-list, .items, .card-deck")
        for (container in listContainers) {
            val items = container.children().filter { it.tag().name == "li" || it.hasClass("item") || it.hasClass("card") }
            if (items.size >= 3) {
                val firstItem = items.first()
                patterns.add(ContentPattern(
                    containerSelector = buildSelector(container),
                    itemSelector = buildSelector(firstItem),
                    itemCount = items.size,
                    sampleData = extractItemData(firstItem)
                ))
            }
        }

        val cardElements = document.select(".card, .movie-card, .show-card, .content-card, article")
        if (cardElements.size >= 3) {
            val firstCard = cardElements.first()
            patterns.add(ContentPattern(
                containerSelector = buildSelector(firstCard.parent()),
                itemSelector = buildSelector(firstCard),
                itemCount = cardElements.size,
                sampleData = extractItemData(firstCard)
            ))
        }

        return patterns
    }

    fun findSearchForms(html: String): List<SearchFormInfo> {
        val document = org.jsoup.Jsoup.parse(html)
        val forms = document.select("form")
        val searchForms = mutableListOf<SearchFormInfo>()

        for (form in forms) {
            val action = form.attr("action")
            val method = form.attr("method").uppercase().ifEmpty { "GET" }
            val inputs = form.select("input[type=text], input[type=search], input:not([type])")
            val searchInput = inputs.firstOrNull()

            if (searchInput != null || form.html().lowercase().contains("search")) {
                searchForms.add(SearchFormInfo(
                    action = resolveUrl(form.baseUri(), action),
                    method = method,
                    inputName = searchInput?.attr("name") ?: "q",
                    inputSelector = searchInput?.let { buildSelector(it) } ?: "",
                    hasSearchButton = form.select("button[type=submit], input[type=submit]").isNotEmpty()
                ))
            }
        }

        val searchLinks = document.select("a[href*=search], a.search, .search-link, [data-search]")
        for (link in searchLinks) {
            val href = link.attr("href")
            if (href.isNotEmpty()) {
                searchForms.add(SearchFormInfo(
                    action = resolveUrl(html, href),
                    method = "GET",
                    inputName = "q",
                    inputSelector = "",
                    hasSearchButton = true,
                    isLinkBased = true
                ))
            }
        }

        return searchForms
    }

    fun findPagination(html: String): PaginationInfo {
        val document = org.jsoup.Jsoup.parse(html)
        val paginationSelectors = listOf(
            ".pagination", ".pager", ".page-numbers", "nav[aria-label*=page]",
            ".next", ".prev", "[rel=next]", "[rel=prev]",
            "a.page-link", ".pages a", "ul.pagination"
        )

        for (selector in paginationSelectors) {
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                val nextLink = document.select("[rel=next], .next a, a:contains(Next), a:contains(»)")
                    .firstOrNull()?.attr("href")

                return PaginationInfo(
                    hasPagination = true,
                    selector = selector,
                    nextUrl = nextLink?.let { resolveUrl(html, it) },
                    pattern = detectPaginationPattern(nextLink ?: "")
                )
            }
        }

        return PaginationInfo(hasPagination = false)
    }

    private fun extractMetaTags(document: org.jsoup.nodes.Document): Map<String, String> {
        val metaTags = mutableMapOf<String, String>()
        document.select("meta").forEach { meta ->
            val name = meta.attr("name").ifEmpty { meta.attr("property") }
            val content = meta.attr("content")
            if (name.isNotEmpty() && content.isNotEmpty()) {
                metaTags[name] = content
            }
        }
        return metaTags
    }

    private fun extractLinks(document: org.jsoup.nodes.Document): List<LinkInfo> {
        return document.select("a[href]").map { link ->
            LinkInfo(
                href = link.attr("href"),
                text = link.text().trim(),
                selector = buildSelector(link)
            )
        }.filter { it.href.isNotEmpty() }
    }

    private fun extractImages(document: org.jsoup.nodes.Document): List<ImageInfo> {
        return document.select("img[src], img[data-src]").map { img ->
            ImageInfo(
                src = img.attr("src").ifEmpty { img.attr("data-src") },
                alt = img.attr("alt"),
                selector = buildSelector(img)
            )
        }.filter { it.src.isNotEmpty() }
    }

    private fun extractForms(document: org.jsoup.nodes.Document): List<FormInfo> {
        return document.select("form").map { form ->
            FormInfo(
                action = form.attr("action"),
                method = form.attr("method"),
                inputs = form.select("input").map { input ->
                    InputInfo(
                        name = input.attr("name"),
                        type = input.attr("type"),
                        value = input.attr("value")
                    )
                }
            )
        }
    }

    private fun extractScripts(document: org.jsoup.nodes.Document): List<String> {
        return document.select("script[src]").map { it.attr("src") }
    }

    private fun extractIframes(document: org.jsoup.nodes.Document): List<IframeInfo> {
        return document.select("iframe").map { iframe ->
            IframeInfo(
                src = iframe.attr("src"),
                width = iframe.attr("width"),
                height = iframe.attr("height"),
                selector = buildSelector(iframe)
            )
        }
    }

    private fun extractHeadings(document: org.jsoup.nodes.Document): Map<String, Int> {
        val headings = mutableMapOf<String, Int>()
        for (i in 1..6) {
            val count = document.select("h$i").size
            if (count > 0) headings["h$i"] = count
        }
        return headings
    }

    private fun extractDivClasses(document: org.jsoup.nodes.Document): Map<String, Int> {
        val classCounts = mutableMapOf<String, Int>()
        document.select("[class]").forEach { element ->
            element.classNames().forEach { className ->
                classCounts[className] = (classCounts[className] ?: 0) + 1
            }
        }
        return classCounts.filter { it.value >= 2 }.toList()
            .sortedByDescending { it.second }.take(50).toMap()
    }

    private fun extractDataAttributes(document: org.jsoup.nodes.Document): Map<String, Set<String>> {
        val dataAttrs = mutableMapOf<String, MutableSet<String>>()
        document.select("[data-id], [data-type], [data-title], [data-url], [data-src]").forEach { element ->
            element.attributes().filter { it.key.startsWith("data-") }.forEach { attr ->
                dataAttrs.getOrPut(attr.key) { mutableSetOf() }.add(attr.value)
            }
        }
        return dataAttrs
    }

    private fun buildSelector(element: org.jsoup.nodes.Element): String {
        val tag = element.tagName()
        val id = element.id()
        val classes = element.classNames().toList()

        return buildString {
            append(tag)
            if (id.isNotEmpty()) append("#$id")
            classes.filter { it.isNotEmpty() }.take(3).forEach { append(".$it") }
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        return try {
            java.net.URL(java.net.URL(base), relative).toString()
        } catch (_: Exception) {
            relative
        }
    }

    private fun detectPaginationPattern(url: String): String {
        val patterns = listOf(
            Regex("(\\d+)") to "number",
            Regex("page[=/?](\\d+)") to "page_param",
            Regex("p[=/?](\\d+)") to "p_param",
            Regex("offset[=/?](\\d+)") to "offset"
        )
        for ((regex, name) in patterns) {
            if (regex.containsMatchIn(url)) return name
        }
        return "unknown"
    }

    private fun extractItemData(element: org.jsoup.nodes.Element): Map<String, String> {
        val data = mutableMapOf<String, String>()
        element.select("a[href]").firstOrNull()?.let { data["url"] = it.attr("href") }
        element.select("img").firstOrNull()?.let {
            data["image"] = it.attr("src").ifEmpty { it.attr("data-src") }
            data["alt"] = it.attr("alt")
        }
        element.select("h1, h2, h3, h4, .title, .name").firstOrNull()?.let { data["title"] = it.text() }
        element.select(".rating, .score, .stars").firstOrNull()?.let { data["rating"] = it.text() }
        element.select(".year, .date, time").firstOrNull()?.let { data["year"] = it.text() }
        element.select(".description, .summary, .plot, p").firstOrNull()?.let { data["description"] = it.text() }
        return data
    }
}

data class HtmlStructure(
    val title: String,
    val metaTags: Map<String, String>,
    val links: List<LinkInfo>,
    val images: List<ImageInfo>,
    val forms: List<FormInfo>,
    val scripts: List<String>,
    val iframes: List<IframeInfo>,
    val headings: Map<String, Int>,
    val divClasses: Map<String, Int>,
    val dataAttributes: Map<String, Set<String>>
)

data class ContentPattern(
    val containerSelector: String,
    val itemSelector: String,
    val itemCount: Int,
    val sampleData: Map<String, String>
)

data class SearchFormInfo(
    val action: String,
    val method: String,
    val inputName: String,
    val inputSelector: String,
    val hasSearchButton: Boolean,
    val isLinkBased: Boolean = false
)

data class PaginationInfo(
    val hasPagination: Boolean,
    val selector: String = "",
    val nextUrl: String? = null,
    val pattern: String = ""
)

data class LinkInfo(val href: String, val text: String, val selector: String)
data class ImageInfo(val src: String, val alt: String, val selector: String)
data class FormInfo(val action: String, val method: String, val inputs: List<InputInfo>)
data class InputInfo(val name: String, val type: String, val value: String)
data class IframeInfo(val src: String, val width: String, val height: String, val selector: String)
