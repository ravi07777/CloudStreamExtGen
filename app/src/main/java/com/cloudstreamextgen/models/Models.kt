package com.cloudstreamextgen.models

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType {
    Movie, TV, Anime, Documentary, Live, Unknown
}

@Serializable
enum class SiteType {
    HTML, JSON_API, HYBRID, JS_RENDERED, IFRAME_EMBED, DYNAMIC
}

@Serializable
enum class AnalysisStatus {
    IDLE, FETCHING, ANALYZING_STRUCTURE, DETECTING_SEARCH,
    DETECTING_CONTENT, DETECTING_DETAILS, DETECTING_EPISODES,
    DETECTING_VIDEO, EXTRACTING_STREAMS, COMPLETED, ERROR
}

@Serializable
data class SiteAnalysis(
    val url: String,
    val siteName: String = "",
    val siteType: SiteType = SiteType.HTML,
    val contentType: ContentType = ContentType.Unknown,
    val status: AnalysisStatus = AnalysisStatus.IDLE,
    val searchAnalysis: SearchAnalysis = SearchAnalysis(),
    val contentAnalysis: ContentAnalysis = ContentAnalysis(),
    val detailAnalysis: DetailAnalysis = DetailAnalysis(),
    val episodeAnalysis: EpisodeAnalysis = EpisodeAnalysis(),
    val videoAnalysis: VideoAnalysis = VideoAnalysis(),
    val streamAnalysis: StreamAnalysis = StreamAnalysis(),
    val metadata: SiteMetadata = SiteMetadata(),
    val antiBot: AntiBotInfo = AntiBotInfo(),
    val errors: List<String> = emptyList(),
    val rawHtml: String = "",
    val detectedApis: List<DetectedApi> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SearchAnalysis(
    val hasSearch: Boolean = false,
    val searchUrl: String = "",
    val searchSelector: String = "",
    val searchParamName: String = "q",
    val searchMethod: String = "GET",
    val searchResultSelector: String = "",
    val titleSelector: String = "",
    val urlSelector: String = "",
    val imageSelector: String = "",
    val searchPattern: String = "",
    val isApiSearch: Boolean = false,
    val searchHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class ContentAnalysis(
    val mainPageUrl: String = "",
    val contentListSelector: String = "",
    val contentItemSelector: String = "",
    val titleSelector: String = "",
    val urlSelector: String = "",
    val imageSelector: String = "",
    val ratingSelector: String = "",
    val yearSelector: String = "",
    val descriptionSelector: String = "",
    val genreSelector: String = "",
    val hasCategories: Boolean = false,
    val categoryUrls: Map<String, String> = emptyMap(),
    val paginationSelector: String = "",
    val paginationPattern: String = "",
    val loadMoreSelector: String = "",
    val isApiContent: Boolean = false,
    val apiEndpoint: String = "",
    val apiParams: Map<String, String> = emptyMap()
)

@Serializable
data class DetailAnalysis(
    val detailSelector: String = "",
    val titleSelector: String = "",
    val descriptionSelector: String = "",
    val imageSelector: String = "",
    val yearSelector: String = "",
    val ratingSelector: String = "",
    val genreSelector: String = "",
    val directorSelector: String = "",
    val castSelector: String = "",
    val durationSelector: String = "",
    val qualitySelector: String = "",
    val subtitleSelector: String = "",
    val relatedSelector: String = "",
    val hasSeasons: Boolean = false,
    val seasonSelector: String = ""
)

@Serializable
data class EpisodeAnalysis(
    val hasEpisodes: Boolean = false,
    val hasSeasons: Boolean = false,
    val seasonSelector: String = "",
    val episodeSelector: String = "",
    val episodeTitleSelector: String = "",
    val episodeNumberSelector: String = "",
    val episodeUrlSelector: String = "",
    val episodeImageSelector: String = "",
    val episodeDateSelector: String = "",
    val episodeDescriptionSelector: String = "",
    val seasonNumberPattern: String = "",
    val episodeNumberPattern: String = "",
    val isApiEpisodes: Boolean = false,
    val episodeApiEndpoint: String = ""
)

@Serializable
data class VideoAnalysis(
    val hasVideo: Boolean = false,
    val iframeSelector: String = "",
    val iframeUrls: List<String> = emptyList(),
    val embedPatterns: List<String> = emptyList(),
    val videoElementSelector: String = "",
    val playerSelector: String = "",
    val playerScriptPattern: String = "",
    val playerApiUrls: List<String> = emptyList(),
    val knownEmbedDomains: List<String> = emptyList(),
    val requiresDecryption: Boolean = false,
    val decryptionPattern: String = ""
)

@Serializable
data class StreamAnalysis(
    val hasStreams: Boolean = false,
    val m3u8Urls: List<String> = emptyList(),
    val mp4Urls: List<String> = emptyList(),
    val streamHeaders: Map<String, String> = emptyMap(),
    val refererRequired: Boolean = false,
    val quality: String = "",
    val subtitleUrls: List<String> = emptyList(),
    val dubSubRelations: Map<String, String> = emptyMap()
)

@Serializable
data class SiteMetadata(
    val title: String = "",
    val description: String = "",
    val favicon: String = "",
    val language: String = "en",
    val hasCaptcha: Boolean = false,
    val hasCloudflare: Boolean = false,
    val hasRecaptcha: Boolean = false,
    val hasHcaptcha: Boolean = false,
    val requiresCookies: Boolean = false,
    val requiresAuth: Boolean = false,
    val responseHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class AntiBotInfo(
    val hasCloudflare: Boolean = false,
    val hasCaptcha: Boolean = false,
    val hasRecaptcha: Boolean = false,
    val hasHcaptcha: Boolean = false,
    val hasTurnstile: Boolean = false,
    val requiresWebView: Boolean = false,
    val detectionMethods: List<String> = emptyList(),
    val bypassStrategy: String = ""
)

@Serializable
data class DetectedApi(
    val url: String,
    val method: String = "GET",
    val params: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val type: String = "REST",
    val description: String = ""
)

@Serializable
data class GeneratedExtension(
    val providerName: String,
    val packageName: String,
    val mainUrl: String,
    val className: String,
    val pluginClassName: String,
    val providerCode: String,
    val pluginCode: String,
    val buildGradleCode: String,
    val manifestCode: String,
    val settingsCode: String? = null,
    val resourcesJson: String? = null,
    val repoJson: String,
    val pluginsJson: String,
    val analysis: SiteAnalysis,
    val buildStatus: BuildStatus = BuildStatus.NOT_BUILT,
    val buildErrors: List<String> = emptyList()
)

@Serializable
data class BuildStatus(
    val status: String = "not_built",
    val apkUrl: String? = null,
    val cs3Url: String? = null,
    val buildLog: String = "",
    val errors: List<String> = emptyList()
) {
    companion object {
        val NOT_BUILT = BuildStatus(status = "not_built")
        val BUILDING = BuildStatus(status = "building")
        fun success(apkUrl: String? = null, cs3Url: String? = null) =
            BuildStatus(status = "success", apkUrl = apkUrl, cs3Url = cs3Url)
        fun error(errors: List<String>) =
            BuildStatus(status = "error", errors = errors)
    }
}

@Serializable
data class SavedExtension(
    val id: String,
    val providerName: String,
    val mainUrl: String,
    val packageName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val analysis: SiteAnalysis,
    val generatedExtension: GeneratedExtension? = null,
    val githubRepoUrl: String? = null,
    val isActive: Boolean = true
)
