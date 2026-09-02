package com.cloudstreamextgen.analysis

import com.cloudstreamextgen.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class WebsiteAnalyzer {

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", UA_DESKTOP)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val htmlAnalyzer = HtmlAnalyzer()
    private val jsonAnalyzer = JsonAnalyzer()
    private val jsAnalyzer = JsAnalyzer()
    private val searchDetector = SearchDetector()
    private val contentDetector = ContentDetector()
    private val detailDetector = DetailDetector()
    private val episodeDetector = EpisodeDetector()
    private val videoDetector = VideoDetector()
    private val streamDetector = StreamDetector()

    suspend fun analyze(url: String, onProgress: (AnalysisStatus, String) -> Unit = { _, _ -> }): SiteAnalysis {
        var analysis = SiteAnalysis(url = normalizeUrl(url))

        return try {
            onProgress(AnalysisStatus.FETCHING, "Fetching website...")
            val (html, response) = fetchPage(url)
            analysis = analysis.copy(rawHtml = html)

            onProgress(AnalysisStatus.FETCHING, "Checking anti-bot protection...")
            val metadata = analyzeMetadata(html, response)
            val antiBot = detectAntiBot(html, response)
            analysis = analysis.copy(metadata = metadata, antiBot = antiBot)

            onProgress(AnalysisStatus.ANALYZING_STRUCTURE, "Analyzing site structure...")
            val siteType = detectSiteType(html, url)
            val siteName = extractSiteName(html, url)
            val contentType = detectContentType(html, url)
            analysis = analysis.copy(
                siteType = siteType,
                siteName = siteName,
                contentType = contentType
            )

            coroutineScope {
                val searchDeferred = async {
                    onProgress(AnalysisStatus.DETECTING_SEARCH, "Detecting search functionality...")
                    searchDetector.detect(html, url, siteType)
                }

                val contentDeferred = async {
                    onProgress(AnalysisStatus.DETECTING_CONTENT, "Analyzing content listing...")
                    contentDetector.detect(html, url, siteType)
                }

                val detailDeferred = async {
                    onProgress(AnalysisStatus.DETECTING_DETAILS, "Analyzing detail pages...")
                    detailDetector.detect(html, url, siteType)
                }

                val episodeDeferred = async {
                    onProgress(AnalysisStatus.DETECTING_EPISODES, "Detecting episode structure...")
                    episodeDetector.detect(html, url, siteType)
                }

                val videoDeferred = async {
                    onProgress(AnalysisStatus.DETECTING_VIDEO, "Analyzing video/embed logic...")
                    videoDetector.detect(html, url, siteType)
                }

                analysis = analysis.copy(
                    searchAnalysis = searchDeferred.await(),
                    contentAnalysis = contentDeferred.await(),
                    detailAnalysis = detailDeferred.await(),
                    episodeAnalysis = episodeDeferred.await(),
                    videoAnalysis = videoDeferred.await()
                )
            }

            onProgress(AnalysisStatus.EXTRACTING_STREAMS, "Detecting stream sources...")
            val streamAnalysis = streamDetector.detect(analysis.videoAnalysis, url)
            analysis = analysis.copy(streamAnalysis = streamAnalysis)

            onProgress(AnalysisStatus.COMPLETED, "Analysis complete!")
            analysis
        } catch (e: Exception) {
            analysis.copy(
                status = AnalysisStatus.ERROR,
                errors = analysis.errors + (e.message ?: "Unknown error")
            )
        }
    }

    private fun fetchPage(url: String): Pair<String, okhttp3.Response> {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        return Pair(body, response)
    }

    private fun detectSiteType(html: String, url: String): SiteType {
        val hasJsonIndicators = html.trimStart().startsWith("{") || html.trimStart().startsWith("[")
        if (hasJsonIndicators) return SiteType.JSON_API

        val hasReactRoot = html.contains("id=\"root\"") || html.contains("id=\"app\"")
        val hasVueApp = html.contains("id=\"app\"") && html.contains("vue")
        val hasAngularApp = html.contains("ng-app") || html.contains("app-root")
        val hasEmberApp = html.contains("ember") || html.contains("data-ember")
        val hasNextData = html.contains("__NEXT_DATA__")
        val hasNuxtData = html.contains("__NUXT__")

        val jsHeavy = jsAnalyzer.isJsHeavy(html)
        val hasOnlyScripts = html.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), "")
            .trim()
            .length < 200

        return when {
            hasReactRoot || hasVueApp || hasAngularApp || hasEmberApp || hasNextData || hasNuxtData -> SiteType.JS_RENDERED
            jsHeavy && hasOnlyScripts -> SiteType.DYNAMIC
            hasOnlyScripts && jsHeavy -> SiteType.DYNAMIC
            else -> SiteType.HTML
        }
    }

    private fun extractSiteName(html: String, url: String): String {
        val ogTitle = Regex("""<meta[^>]*property="og:title"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        val titleTag = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
        val siteName = Regex("""<meta[^>]*property="og:site_name"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)

        return siteName ?: ogTitle ?: titleTag ?: try {
            java.net.URL(url).host.removePrefix("www.")
        } catch (_: Exception) {
            url
        }
    }

    private fun detectContentType(html: String, url: String): ContentType {
        val lowerHtml = html.lowercase()
        val lowerUrl = url.lowercase()

        if (lowerUrl.contains("anime") || lowerHtml.contains("anime") && lowerHtml.contains("episode"))
            return ContentType.Anime
        if (lowerHtml.contains("season") && lowerHtml.contains("episode"))
            return ContentType.TV
        if (lowerHtml.contains("full movie") || lowerHtml.contains("watch movie") ||
            lowerHtml.contains("movie") && lowerHtml.contains("stream"))
            return ContentType.Movie
        if (lowerHtml.contains("documentary") || lowerHtml.contains("docu"))
            return ContentType.Documentary
        if (lowerHtml.contains("live stream") || lowerHtml.contains("live tv") ||
            lowerHtml.contains("live channel"))
            return ContentType.Live

        return ContentType.Movie
    }

    private fun analyzeMetadata(html: String, response: okhttp3.Response): SiteMetadata {
        val language = Regex("""<html[^>]*lang="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: "en"
        val title = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim() ?: ""
        val description = Regex("""<meta[^>]*name="description"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: ""
        val favicon = Regex("""<link[^>]*rel="(?:shortcut )?icon"[^>]*href="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: ""

        val headers = mutableMapOf<String, String>()
        response.headers.forEach { (name, value) -> headers[name] = value }

        return SiteMetadata(
            title = title,
            description = description,
            favicon = favicon,
            language = language,
            responseHeaders = headers
        )
    }

    private fun detectAntiBot(html: String, response: okhttp3.Response): AntiBotInfo {
        val lowerHtml = html.lowercase()
        val methods = mutableListOf<String>()

        val hasCloudflare = lowerHtml.contains("cloudflare") || lowerHtml.contains("cf-browser-verification") ||
                lowerHtml.contains("cf_chl_opt") || lowerHtml.contains("ray id")
        val hasRecaptcha = lowerHtml.contains("recaptcha") || lowerHtml.contains("grecaptcha")
        val hasHcaptcha = lowerHtml.contains("hcaptcha")
        val valHasTurnstile = lowerHtml.contains("turnstile") || lowerHtml.contains("cf-turnstile")
        val hasCaptcha = hasRecaptcha || hasHcaptcha

        if (hasCloudflare) methods.add("Cloudflare challenge")
        if (hasRecaptcha) methods.add("reCAPTCHA")
        if (hasHcaptcha) methods.add("hCaptcha")
        if (valHasTurnstile) methods.add("Cloudflare Turnstile")
        if (lowerHtml.contains("checking your browser")) methods.add("Browser verification")
        if (lowerHtml.contains("just a moment")) methods.add("Waiting page")

        val bypassStrategy = when {
            hasCloudflare && hasRecaptcha -> "webview_with_js_execution"
            hasCloudflare -> "webview_with_cookie_passthrough"
            hasRecaptcha || hasHcaptcha -> "webview_with_captcha_handling"
            valHasTurnstile -> "webview_turnstile_handling"
            methods.isEmpty() -> "direct_http"
            else -> "webview_with_js_execution"
        }

        return AntiBotInfo(
            hasCloudflare = hasCloudflare,
            hasCaptcha = hasCaptcha,
            hasRecaptcha = hasRecaptcha,
            hasHcaptcha = hasHcaptcha,
            hasTurnstile = valHasTurnstile,
            requiresWebView = methods.isNotEmpty(),
            detectionMethods = methods,
            bypassStrategy = bypassStrategy
        )
    }

    companion object {
        const val UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val UA_MOBILE = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun normalizeUrl(url: String): String {
            var normalized = url.trim()
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://$normalized"
            }
            return normalized
        }
    }
}
