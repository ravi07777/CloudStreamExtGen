package com.cloudstreamextgen.analysis.detectors

import com.cloudstreamextgen.models.StreamAnalysis
import com.cloudstreamextgen.models.VideoAnalysis

class StreamDetector {

    fun detect(videoAnalysis: VideoAnalysis, baseUrl: String): StreamAnalysis {
        val m3u8Urls = mutableListOf<String>()
        val mp4Urls = mutableListOf<String>()

        for (url in videoAnalysis.iframeUrls + videoAnalysis.playerApiUrls) {
            when {
                url.contains(".m3u8") -> m3u8Urls.add(url)
                url.contains(".mp4") -> mp4Urls.add(url)
                url.contains("manifest") -> m3u8Urls.add(url)
            }
        }

        val streamHeaders = mutableMapOf<String, String>()
        val refererRequired = videoAnalysis.knownEmbedDomains.isNotEmpty()

        if (refererRequired) {
            streamHeaders["Referer"] = baseUrl
        }

        return StreamAnalysis(
            hasStreams = m3u8Urls.isNotEmpty() || mp4Urls.isNotEmpty(),
            m3u8Urls = m3u8Urls.distinct(),
            mp4Urls = mp4Urls.distinct(),
            streamHeaders = streamHeaders,
            refererRequired = refererRequired,
            quality = detectQuality(m3u8Urls + mp4Urls)
        )
    }

    private fun detectQuality(urls: List<String>): String {
        for (url in urls) {
            val lowerUrl = url.lowercase()
            when {
                lowerUrl.contains("2160") || lowerUrl.contains("4k") -> return "2160p"
                lowerUrl.contains("1080") -> return "1080p"
                lowerUrl.contains("720") -> return "720p"
                lowerUrl.contains("480") -> return "480p"
                lowerUrl.contains("360") -> return "360p"
            }
        }
        return ""
    }
}
