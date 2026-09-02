package com.cloudstreamextgen.analysis

import com.cloudstreamextgen.models.SiteType
import org.json.JSONObject
import org.json.JSONArray

class JsonAnalyzer {

    fun isJsonResponse(html: String): Boolean {
        val trimmed = html.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    fun analyzeJsonStructure(json: String): JsonStructure {
        return try {
            if (json.trim().startsWith("[")) {
                val array = JSONArray(json)
                if (array.length() > 0) {
                    val firstItem = array.getJSONObject(0)
                    JsonStructure(
                        isArray = true,
                        itemCount = array.length(),
                        keys = extractKeys(firstItem),
                        nestedObjects = extractNestedObjects(firstItem),
                        arrayFields = extractArrayFields(firstItem),
                        sampleItem = firstItem.toString().take(500)
                    )
                } else {
                    JsonStructure(isArray = true, itemCount = 0)
                }
            } else {
                val obj = JSONObject(json)
                JsonStructure(
                    isArray = false,
                    keys = extractKeys(obj),
                    nestedObjects = extractNestedObjects(obj),
                    arrayFields = extractArrayFields(obj),
                    sampleItem = obj.toString().take(500)
                )
            }
        } catch (_: Exception) {
            JsonStructure()
        }
    }

    fun findApiEndpoints(html: String, baseUrl: String): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()

        val urlPattern = Regex("""["']((?:https?://|/api/|/v\d+/)[^"'\s]+)["']""")
        urlPattern.findAll(html).forEach { match ->
            val url = match.groupValues[1]
            endpoints.add(ApiEndpoint(
                url = if (url.startsWith("/")) "$baseUrl$url" else url,
                method = guessMethod(html, url),
                description = "URL found in source"
            ))
        }

        val fetchPattern = Regex("""fetch\s*\(\s*["']([^"']+)["']""")
        fetchPattern.findAll(html).forEach { match ->
            endpoints.add(ApiEndpoint(
                url = resolveRelativeUrl(baseUrl, match.groupValues[1]),
                method = "GET",
                description = "fetch() call"
            ))
        }

        val axiosPattern = Regex("""axios\.(get|post|put|delete)\s*\(\s*["']([^"']+)["']""")
        axiosPattern.findAll(html).forEach { match ->
            endpoints.add(ApiEndpoint(
                url = resolveRelativeUrl(baseUrl, match.groupValues[2]),
                method = match.groupValues[1].uppercase(),
                description = "axios.${match.groupValues[1]}() call"
            ))
        }

        val ajaxPattern = Regex("""\$\.(get|post|ajax)\s*\(\s*["']([^"']+)["']""")
        ajaxPattern.findAll(html).forEach { match ->
            endpoints.add(ApiEndpoint(
                url = resolveRelativeUrl(baseUrl, match.groupValues[2]),
                method = match.groupValues[1].uppercase(),
                description = "jQuery ajax call"
            ))
        }

        val xhrPattern = Regex("""XMLHttpRequest|\.open\s*\(\s*["'](\w+)["']\s*,\s*["']([^"']+)["']""")
        xhrPattern.findAll(html).forEach { match ->
            endpoints.add(ApiEndpoint(
                url = resolveRelativeUrl(baseUrl, match.groupValues[2]),
                method = match.groupValues[1],
                description = "XMLHttpRequest"
            ))
        }

        return endpoints.distinctBy { it.url }
    }

    fun detectJsonKeys(json: String): Map<String, String> {
        return try {
            val obj = if (json.trim().startsWith("[")) {
                JSONArray(json).optJSONObject(0) ?: JSONObject()
            } else {
                JSONObject(json)
            }
            extractKeyTypes(obj)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun extractKeys(obj: JSONObject): List<String> {
        return obj.keys().asSequence().toList()
    }

    private fun extractNestedObjects(obj: JSONObject): Map<String, List<String>> {
        val nested = mutableMapOf<String, List<String>>()
        for (key in obj.keys()) {
            val value = obj.opt(key)
            if (value is JSONObject) {
                nested[key] = extractKeys(value)
            } else if (value is JSONArray && value.length() > 0 && value.opt(0) is JSONObject) {
                nested[key] = extractKeys(value.getJSONObject(0))
            }
        }
        return nested
    }

    private fun extractArrayFields(obj: JSONObject): Map<String, Int> {
        val arrays = mutableMapOf<String, Int>()
        for (key in obj.keys()) {
            val value = obj.opt(key)
            if (value is JSONArray) {
                arrays[key] = value.length()
            }
        }
        return arrays
    }

    private fun extractKeyTypes(obj: JSONObject): Map<String, String> {
        val types = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            val value = obj.opt(key)
            types[key] = when (value) {
                is String -> "String"
                is Int, is Long -> "Int"
                is Double, is Float -> "Double"
                is Boolean -> "Boolean"
                is JSONObject -> "Object"
                is JSONArray -> {
                    if (value.length() > 0) {
                        when (value.opt(0)) {
                            is String -> "List<String>"
                            is JSONObject -> "List<Object>"
                            else -> "List<Any>"
                        }
                    } else "List<Any>"
                }
                null -> "Any?"
                else -> "Any"
            }
        }
        return types
    }

    private fun guessMethod(html: String, url: String): String {
        val lowerHtml = html.lowercase()
        if (lowerHtml.contains("post") && lowerHtml.contains(url)) return "POST"
        return "GET"
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        return try {
            java.net.URL(java.net.URL(baseUrl), relative).toString()
        } catch (_: Exception) {
            relative
        }
    }
}

data class JsonStructure(
    val isArray: Boolean = false,
    val itemCount: Int = 0,
    val keys: List<String> = emptyList(),
    val nestedObjects: Map<String, List<String>> = emptyMap(),
    val arrayFields: Map<String, Int> = emptyMap(),
    val sampleItem: String = ""
)

data class ApiEndpoint(
    val url: String,
    val method: String,
    val description: String
)
