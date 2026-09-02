package com.cloudstreamextgen.analysis.detectors

import com.cloudstreamextgen.analysis.JsAnalyzer
import com.cloudstreamextgen.models.VideoAnalysis

class VideoDetector {

    private val knownEmbedDomains = listOf(
        "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com",
        "streamable.com", "fembed.com", "embed.su", "vidcloud.co",
        "streamtape.com", "mixdrop.co", "upstream.to", "streamsb.com",
        "doodstream.com", "mp4upload.com", "clicknupload.org",
        "filemoon.sx", "streamlare.com", "veoh.com", "twitch.tv",
        "ok.ru", "mail.ru", "googlevideo.com", "bit.ly",
        "rapidgator.net", "uploaded.net", "nitroflare.com"
    )

    fun detect(html: String, baseUrl: String, videoAnalysis: VideoAnalysis? = null): VideoAnalysis {
        val document = org.jsoup.Jsoup.parse(html)
        val jsAnalyzer = JsAnalyzer()
        val jsData = jsAnalyzer.extractJsData(html)

        val iframeUrls = findIframeUrls(document)
        val embedPatterns = findEmbedPatterns(html)
        val playerSelector = findPlayerSelector(document)
        val playerScriptPattern = findPlayerScriptPattern(html)
        val requiresDecryption = detectDecryptionNeeded(html)

        val allEmbedUrls = (iframeUrls + jsData.embedUrls).distinct()

        return VideoAnalysis(
            hasVideo = iframeUrls.isNotEmpty() || jsData.embedUrls.isNotEmpty() || playerSelector.isNotEmpty(),
            iframeSelector = findIframeSelector(document),
            iframeUrls = allEmbedUrls,
            embedPatterns = embedPatterns,
            videoElementSelector = findVideoElementSelector(document),
            playerSelector = playerSelector,
            playerScriptPattern = playerScriptPattern,
            playerApiUrls = jsData.apiCalls.map { it.url }.filter {
                it.contains("player") || it.contains("video") || it.contains("embed")
            },
            knownEmbedDomains = findKnownDomains(allEmbedUrls),
            requiresDecryption = requiresDecryption
        )
    }

    private fun findIframeUrls(document: org.jsoup.nodes.Document): List<String> {
        return document.select("iframe[src]").map { it.attr("src") }
            .filter { it.isNotEmpty() && it != "about:blank" }
    }

    private fun findIframeSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".player iframe", "#player iframe", ".embed-responsive iframe",
            ".video-container iframe", "iframe[src*=embed]", "iframe[src*=player]",
            "iframe[src*=video]", "#video-player iframe", ".movie-player iframe"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: "iframe"
    }

    private fun findEmbedPatterns(html: String): List<String> {
        val patterns = mutableListOf<String>()

        val embedPatterns = listOf(
            Regex("""["']([^"']*(?:embed|player|video)[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']([^"']*(?:fembed|feurl|streamsb|dood|mixdrop|streamtape|filemoon)[^"']*)["']""", RegexOption.IGNORE_CASE)
        )

        for (pattern in embedPatterns) {
            pattern.findAll(html).forEach { match ->
                val url = match.groupValues[1]
                if (url.length > 5) patterns.add(url)
            }
        }
        return patterns.distinct()
    }

    private fun findVideoElementSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            "video source[src]", "video[src]", ".video-js video",
            "#player video", ".plyr video", "video"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: "video"
    }

    private fun findPlayerSelector(document: org.jsoup.nodes.Document): String {
        val selectors = listOf(
            ".player", "#player", ".video-player", "#video-player",
            ".movie-player", ".plyr", ".video-js", "#video",
            ".jwplayer", ".flowplayer", ".video-container"
        )
        return selectors.firstOrNull { document.select(it).isNotEmpty() } ?: ".player, #player"
    }

    private fun findPlayerScriptPattern(html: String): String {
        val patterns = listOf(
            Regex("""player\s*\(\s*\{[^}]*file\s*:\s*["']([^"']+)["']"""),
            Regex("""VideoPlayer\s*\(\s*["']([^"']+)["']"""),
            Regex("""\.source\s*\(\s*\{[^}]*src\s*:\s*["']([^"']+)["']"""),
            Regex("""jwplayer\s*\([^)]*\)\s*\.\s*setup\s*\(\s*\{[^}]*file\s*:\s*["']([^"']+)["']""")
        )

        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }

    private fun detectDecryptionNeeded(html: String): Boolean {
        val lowerHtml = html.lowercase()
        return lowerHtml.contains("decrypt") || lowerHtml.contains("atob") ||
                lowerHtml.contains("crypto") || lowerHtml.contains("cipher") ||
                lowerHtml.contains("unpack") || lowerHtml.contains("eval(function")
    }

    private fun findKnownDomains(urls: List<String>): List<String> {
        return urls.mapNotNull { url ->
            try {
                val host = java.net.URL(url).host
                knownEmbedDomains.firstOrNull { host.contains(it) }
            } catch (_: Exception) {
                null
            }
        }.distinct()
    }
}
