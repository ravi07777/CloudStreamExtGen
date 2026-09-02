package com.cloudstreamextgen.analysis

class JsAnalyzer {

    fun isJsHeavy(html: String): Boolean {
        val scriptCount = Regex("""<script[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(html).count()
        val scriptContent = Regex("""<script[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            .findAll(html).sumOf { it.groupValues[1].length }
        val inlineScriptRatio = scriptContent.toDouble() / html.length.coerceAtLeast(1)

        return scriptCount > 5 || inlineScriptRatio > 0.3 || html.contains("__NEXT_DATA__") ||
                html.contains("__NUXT__") || html.contains("window.__INITIAL_STATE__")
    }

    fun extractJsData(html: String): JsData {
        val variables = extractJsVariables(html)
        val apiCalls = extractApiCalls(html)
        val embedUrls = extractEmbedUrls(html)
        val playerVars = extractPlayerVars(html)
        val initialState = extractInitialState(html)
        val nextData = extractNextData(html)
        val nuxtData = extractNuxtData(html)

        return JsData(
            variables = variables,
            apiCalls = apiCalls,
            embedUrls = embedUrls,
            playerVars = playerVars,
            initialState = initialState,
            nextData = nextData,
            nuxtData = nuxtData
        )
    }

    private fun extractJsVariables(html: String): Map<String, String> {
        val vars = mutableMapOf<String, String>()

        val patterns = listOf(
            Regex("""var\s+(\w+)\s*=\s*["']([^"']+)["']"""),
            Regex("""let\s+(\w+)\s*=\s*["']([^"']+)["']"""),
            Regex("""const\s+(\w+)\s*=\s*["']([^"']+)["']"""),
            Regex("""window\.(\w+)\s*=\s*["']([^"']+)["']"""),
            Regex("""(\w+)\s*:\s*["']([^"']+)["']""")
        )

        for (pattern in patterns) {
            pattern.findAll(html).forEach { match ->
                val name = match.groupValues[1]
                val value = match.groupValues[2]
                if (name.length > 2 && value.length > 3) {
                    vars[name] = value
                }
            }
        }
        return vars
    }

    private fun extractApiCalls(html: String): List<ApiCall> {
        val calls = mutableListOf<ApiCall>()

        val patterns = listOf(
            Regex("""(?:fetch|axios|ajax|http\.get|http\.post)\s*\(\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:url|endpoint|api|link)\s*[:=]\s*["']([^"']*(?:api|json|data|search|video|stream)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']([^"']*(?:\.m3u8|\.mp4|\.mkv|\.ts)[^"']*)["']"""),
            Regex("""["']([^"']*(?:embed|player|video)[^"']*)["']""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.findAll(html).forEach { match ->
                val url = match.groupValues[1]
                if (url.startsWith("http") || url.startsWith("/")) {
                    calls.add(ApiCall(
                        url = url,
                        line = html.substring(0, match.range.first).count { it == '\n' } + 1,
                        context = html.substring(
                            maxOf(0, match.range.first - 50),
                            minOf(html.length, match.range.last + 50)
                        )
                    ))
                }
            }
        }
        return calls.distinctBy { it.url }
    }

    private fun extractEmbedUrls(html: String): List<String> {
        val embedPatterns = listOf(
            Regex("""["']((?:https?:)?//(?:www\.)?(?:youtube\.com/embed|player\.vimeo\.com|dailymotion\.com/embed|streamable\.com|fembed\.com|embed\.sb|vidcloud\.co|streamtape\.com)[^"']*)["']"""),
            Regex("""src\s*=\s*["']((?:https?:)?//[^"']*(?:embed|player|video)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']((?:https?:)?//[^"']*(?:\.m3u8|\.mp4|\.mpd)[^"']*)["']""")
        )

        val urls = mutableListOf<String>()
        for (pattern in embedPatterns) {
            pattern.findAll(html).forEach { match ->
                urls.add(match.groupValues[1])
            }
        }
        return urls.distinct()
    }

    private fun extractPlayerVars(html: String): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        val playerPatterns = listOf(
            Regex("""(?:player|video|source|stream)(?:Url|Src|Link|File)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:file|source|url|hls|m3u8|mp4)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""data-(?:src|url|video|file)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        )

        for (pattern in playerPatterns) {
            pattern.findAll(html).forEach { match ->
                vars[match.groupValues[0].substringBefore("=").trim()] = match.groupValues[1]
            }
        }
        return vars
    }

    private fun extractInitialState(html: String): String? {
        val patterns = listOf(
            Regex("""window\.__INITIAL_STATE__\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
            Regex("""window\.initialState\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL),
            Regex("""var\s+initialData\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL)
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1].take(5000)
        }
        return null
    }

    private fun extractNextData(html: String): String? {
        val match = Regex("""<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
        return match?.groupValues?.get(1)?.take(5000)
    }

    private fun extractNuxtData(html: String): String? {
        val match = Regex("""window\.__NUXT__\s*=\s*(.+?);</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)
        return match?.groupValues?.get(1)?.take(5000)
    }
}

data class JsData(
    val variables: Map<String, String> = emptyMap(),
    val apiCalls: List<ApiCall> = emptyList(),
    val embedUrls: List<String> = emptyList(),
    val playerVars: Map<String, String> = emptyMap(),
    val initialState: String? = null,
    val nextData: String? = null,
    val nuxtData: String? = null
)

data class ApiCall(
    val url: String,
    val line: Int,
    val context: String
)
