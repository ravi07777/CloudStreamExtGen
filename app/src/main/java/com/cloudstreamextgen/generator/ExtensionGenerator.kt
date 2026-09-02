package com.cloudstreamextgen.generator

import com.cloudstreamextgen.models.*

class ExtensionGenerator {

    fun generate(analysis: SiteAnalysis, config: GeneratorConfig): GeneratedExtension {
        val providerName = config.providerName.ifEmpty { generateProviderName(analysis.siteName) }
        val packageName = config.packageName.ifEmpty { "com.generated.${providerName.lowercase()}" }
        val className = "${providerName}Provider"
        val pluginClassName = "${providerName}Plugin"

        val providerCode = generateProviderCode(analysis, config, className, packageName)
        val pluginCode = generatePluginCode(analysis, pluginClassName, className, packageName)
        val buildGradleCode = generateBuildGradle(analysis, config, providerName)
        val manifestCode = generateManifest()
        val repoJson = generateRepoJson(config)
        val pluginsJson = generatePluginsJson(config, providerName)

        return GeneratedExtension(
            providerName = providerName,
            packageName = packageName,
            mainUrl = analysis.url,
            className = className,
            pluginClassName = pluginClassName,
            providerCode = providerCode,
            pluginCode = pluginCode,
            buildGradleCode = buildGradleCode,
            manifestCode = manifestCode,
            repoJson = repoJson,
            pluginsJson = pluginsJson,
            analysis = analysis
        )
    }

    private fun generateProviderCode(
        analysis: SiteAnalysis,
        config: GeneratorConfig,
        className: String,
        packageName: String
    ): String {
        val mainUrl = analysis.url
        val siteName = analysis.siteName.ifEmpty { config.providerName }
        val lang = analysis.metadata.language
        val tvTypes = mapContentType(analysis.contentType)
        val hasMainPage = analysis.contentAnalysis.mainPageUrl.isNotEmpty() || true
        val hasSearch = analysis.searchAnalysis.hasSearch

        val imports = generateImports(analysis)
        val classBody = buildString {
            appendLine("package $packageName")
            appendLine()
            imports.forEach { appendLine(it) }
            appendLine()
            appendLine("class $className : MainAPI() {")
            appendLine("    override var mainUrl = \"$mainUrl\"")
            appendLine("    override var name = \"$siteName\"")
            appendLine("    override var lang = \"$lang\"")
            appendLine("    override val supportedTypes = setOf($tvTypes)")
            appendLine("    override val hasMainPage = $hasMainPage")

            if (analysis.siteType == SiteType.JSON_API) {
                appendLine()
                appendLine("    // JSON API based provider")
                appendLine("    private val gson = com.google.gson.Gson()")
            }

            if (analysis.searchAnalysis.isApiSearch) {
                appendLine()
                appendLine("    // API search endpoint")
                appendLine("    override suspend fun search(query: String): List<SearchResponse> {")
                appendLine("        val searchUrl = \"${analysis.searchAnalysis.searchUrl}\"")
                appendLine("        val response = app.get(searchUrl, params = mapOf(\"${analysis.searchAnalysis.searchParamName}\" to query))")
                appendLine("        // TODO: Parse search results from response")
                appendLine("        return emptyList()")
                appendLine("    }")
            } else if (hasSearch) {
                appendLine()
                appendLine("    override suspend fun search(query: String): List<SearchResponse> {")
                appendLine("        val response = app.get(\"${analysis.searchAnalysis.searchUrl}/$\" + query)")
                appendLine("        // TODO: Parse search results from HTML response")
                appendLine("        return emptyList()")
                appendLine("    }")
            } else {
                appendLine()
                appendLine("    override suspend fun search(query: String): List<SearchResponse> {")
                appendLine("        return emptyList()")
                appendLine("    }")
            }

            if (hasMainPage) {
                appendLine()
                appendLine("    override suspend fun getMainPage(page: Int, category: String): HomePageList? {")
                appendLine("        val url = \"${mainUrl}\"")
                appendLine("        val response = app.get(url)")
                appendLine("        // TODO: Parse main page content")
                appendLine("        return null")
                appendLine("    }")
            }

            appendLine()
            appendLine("    override suspend fun load(url: String): LoadResponse? {")
            appendLine("        val response = app.get(url)")
            appendLine("        val document = response.document")
            appendLine()

            if (analysis.detailAnalysis.titleSelector.isNotEmpty()) {
                appendLine("        val title = document.selectFirst(\"${analysis.detailAnalysis.titleSelector}\")?.text() ?: return null")
            } else {
                appendLine("        val title = document.selectFirst(\"h1\")?.text() ?: return null")
            }

            if (analysis.detailAnalysis.imageSelector.isNotEmpty()) {
                appendLine("        val posterUrl = document.selectFirst(\"${analysis.detailAnalysis.imageSelector}\")?.attr(\"src\")")
            } else {
                appendLine("        val posterUrl = document.selectFirst(\".poster img, .movie-poster img\")?.attr(\"src\")")
            }

            if (analysis.detailAnalysis.descriptionSelector.isNotEmpty()) {
                appendLine("        val description = document.selectFirst(\"${analysis.detailAnalysis.descriptionSelector}\")?.text()")
            } else {
                appendLine("        val description = document.selectFirst(\".description, .plot, .summary\")?.text()")
            }

            if (analysis.detailAnalysis.yearSelector.isNotEmpty()) {
                appendLine("        val year = document.selectFirst(\"${analysis.detailAnalysis.yearSelector}\")?.text()?.toIntOrNull()")
            }

            if (analysis.detailAnalysis.genreSelector.isNotEmpty()) {
                appendLine("        val genres = document.select(\"${analysis.detailAnalysis.genreSelector}\").map { it.text() }")
            } else {
                appendLine("        val genres = document.select(\".genre, .genres, .category\").map { it.text() }")
            }

            appendLine()
            appendLine("        return newTvSeriesLoadResponse(title, url, TvType.TvSeries) {")

            if (analysis.episodeAnalysis.hasEpisodes || analysis.episodeAnalysis.hasSeasons) {
                appendLine("            // Season/Episode data")
                if (analysis.episodeAnalysis.hasSeasons && analysis.episodeAnalysis.seasonSelector.isNotEmpty()) {
                    appendLine("            val seasons = document.select(\"${analysis.episodeAnalysis.seasonSelector}\")")
                    appendLine("            seasons.forEachIndexed { seasonIndex, season ->")
                    appendLine("                val episodes = season.select(\"${analysis.episodeAnalysis.episodeSelector}\")")
                    appendLine("                episodes.forEachIndexed { epIndex, ep ->")
                    appendLine("                    val epTitle = ep.selectFirst(\"${analysis.episodeAnalysis.episodeTitleSelector}\")?.text()")
                    appendLine("                    val epUrl = ep.selectFirst(\"a[href]\")?.attr(\"abs:href\")")
                    appendLine("                    // Add episode to season")
                    appendLine("                }")
                    appendLine("            }")
                } else {
                    appendLine("            // Parse episodes from the page")
                }
            } else {
                appendLine("            // Single movie content")
                appendLine("            addEpisodes(DubStatus.Subbed, listOf(")
                appendLine("                Episode(title, url, posterUrl = posterUrl)")
                appendLine("            ))")
            }

            appendLine("            this.posterUrl = posterUrl")
            appendLine("            this.plot = description")
            appendLine("            this.year = year")
            appendLine("            this.tags = genres")
            appendLine("        }")
            appendLine("    }")

            if (analysis.videoAnalysis.hasVideo) {
                appendLine()
                appendLine("    override suspend fun loadLinks(")
                appendLine("        url: String,")
                appendLine("        isCasting: Boolean,")
                appendLine("        subtitleCallback: (SubtitleFile) -> Unit,")
                appendLine("        callback: (ExtractorLink) -> Unit")
                appendLine("    ): Boolean {")
                appendLine("        val response = app.get(url)")
                appendLine("        val document = response.document")
                appendLine()

                if (analysis.videoAnalysis.iframeSelector.isNotEmpty()) {
                    appendLine("        // Extract iframe sources")
                    appendLine("        val iframes = document.select(\"${analysis.videoAnalysis.iframeSelector}\")")
                    appendLine("        iframes.forEach { iframe ->")
                    appendLine("            val src = iframe.attr(\"src\")")
                    appendLine("            if (src.isNotEmpty()) {")
                    appendLine("                // Use extractor to load video from iframe URL")
                    appendLine("                loadExtractor(src, document, callback)")
                    appendLine("            }")
                    appendLine("        }")
                } else {
                    appendLine("        // Find and extract video sources")
                    appendLine("        val embedUrls = document.select(\"iframe[src]\").map { it.attr(\"src\") }")
                    appendLine("        embedUrls.forEach { embedUrl ->")
                    appendLine("            loadExtractor(embedUrl, document, callback)")
                    appendLine("        }")
                }

                appendLine("        return true")
                appendLine("    }")
            }

            appendLine("}")
        }

        return classBody
    }

    private fun generatePluginCode(
        analysis: SiteAnalysis,
        pluginClassName: String,
        providerClassName: String,
        packageName: String
    ): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import android.content.Context")
            appendLine("import com.lagradost.cloudstream3.plugins.CloudstreamPlugin")
            appendLine("import com.lagradost.cloudstream3.plugins.Plugin")
            appendLine("import androidx.appcompat.app.AppCompatActivity")
            appendLine()
            appendLine("@CloudstreamPlugin")
            appendLine("class $pluginClassName : Plugin() {")
            appendLine("    override fun load(context: Context) {")
            appendLine("        val activity = context as? AppCompatActivity")
            appendLine("        registerMainAPI($providerClassName())")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateBuildGradle(analysis: SiteAnalysis, config: GeneratorConfig, providerName: String): String {
        return buildString {
            appendLine("dependencies {")
            appendLine("    implementation(\"com.google.android.material:material:1.12.0\")")
            appendLine("}")
            appendLine()
            appendLine("version = ${config.version}")
            appendLine()
            appendLine("cloudstream {")
            appendLine("    description = \"${config.description.ifEmpty { "Auto-generated extension for ${analysis.siteName}\" }}\"")
            appendLine("    authors = listOf(\"${config.author.ifEmpty { "ExtGen" } }\")")
            appendLine("    status = ${config.status}")
            if (analysis.contentType != ContentType.Unknown) {
                appendLine("    tvTypes = listOf(\"${analysis.contentType.name}\")")
            }
            appendLine("    language = \"${analysis.metadata.language}\"")
            if (config.iconUrl.isNotEmpty()) {
                appendLine("    iconUrl = \"${config.iconUrl}\"")
            }
            appendLine("}")
            appendLine()
            appendLine("android {")
            appendLine("    buildFeatures {")
            appendLine("        buildConfig = true")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateManifest(): String {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest />"
    }

    private fun generateRepoJson(config: GeneratorConfig): String {
        return buildString {
            appendLine("{")
            appendLine("    \"name\": \"${config.repoName.ifEmpty { "${config.providerName} Repository" }}\",")
            appendLine("    \"description\": \"${config.repoDescription.ifEmpty { "Cloudstream extension repository" }}\",")
            appendLine("    \"manifestVersion\": 1,")
            appendLine("    \"pluginLists\": [")
            appendLine("        \"https://raw.githubusercontent.com/${config.githubUsername}/${config.repoName}/builds/plugins.json\"")
            appendLine("    ]")
            appendLine("}")
        }
    }

    private fun generatePluginsJson(config: GeneratorConfig, providerName: String): String {
        return buildString {
            appendLine("[")
            appendLine("    {")
            appendLine("        \"name\": \"$providerName\",")
            appendLine("        \"url\": \"https://github.com/${config.githubUsername}/${config.repoName}/releases/download/latest/${providerName}.cs3\",")
            appendLine("        \"version\": ${config.version},")
            appendLine("        \"changelog\": \"Initial release\",")
            appendLine("        \"repository\": \"${config.repoName}\",")
            appendLine("        \"status\": ${config.status},")
            appendLine("        \"apiUrl\": \"\"")
            appendLine("    }")
            appendLine("]")
        }
    }

    private fun generateImports(analysis: SiteAnalysis): List<String> {
        val imports = mutableListOf(
            "import com.lagradost.cloudstream3.MainAPI",
            "import com.lagradost.cloudstream3.SearchResponse",
            "import com.lagradost.cloudstream3.TvType",
            "import com.lagradost.cloudstream3.LoadResponse",
            "import com.lagradost.cloudstream3.Episode",
            "import com.lagradost.cloudstream3.SubtitleFile",
            "import com.lagradost.cloudstream3.ExtractorLink",
            "import com.lagradost.cloudstream3.DubStatus",
            "import com.lagradost.cloudstream3.app"
        )

        if (analysis.siteType == SiteType.JSON_API) {
            imports.add("import com.google.gson.Gson")
        }

        return imports
    }

    private fun mapContentType(type: ContentType): String {
        return when (type) {
            ContentType.Movie -> "TvType.Movie"
            ContentType.TV -> "TvType.TvSeries"
            ContentType.Anime -> "TvType.Anime"
            ContentType.Documentary -> "TvType.Documentary"
            ContentType.Live -> "TvType.Live"
            ContentType.Unknown -> "TvType.Movie"
        }
    }

    private fun generateProviderName(siteName: String): String {
        return siteName
            .replace(Regex("[^a-zA-Z0-9]"), "")
            .replace(Regex("\\b(the|a|an|and|or|of|in|for|to|with)\\b", RegexOption.IGNORE_CASE), "")
            .take(20)
            .ifEmpty { "Generated" }
    }
}

data class GeneratorConfig(
    val providerName: String = "",
    val packageName: String = "",
    val description: String = "",
    val author: String = "",
    val iconUrl: String = "",
    val version: Int = 1,
    val status: Int = 1,
    val language: String = "en",
    val repoName: String = "",
    val repoDescription: String = "",
    val repoUrl: String = "",
    val githubUsername: String = ""
)
