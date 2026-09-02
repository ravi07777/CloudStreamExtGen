package com.cloudstreamextgen.github

import com.cloudstreamextgen.models.GeneratedExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubIntegration {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun createRepository(
        token: String,
        repoName: String,
        description: String,
        isPrivate: Boolean = false
    ): Result<GitHubRepo> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("name", repoName)
                put("description", description)
                put("private", isPrivate)
                put("auto_init", false)
            }

            val request = Request.Builder()
                .url("https://api.github.com/user/repos")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val jsonObj = JSONObject(body)

            if (response.isSuccessful) {
                Result.success(GitHubRepo(
                    name = jsonObj.getString("name"),
                    fullName = jsonObj.getString("full_name"),
                    url = jsonObj.getString("html_url"),
                    cloneUrl = jsonObj.getString("clone_url"),
                    defaultBranch = jsonObj.optString("default_branch", "main")
                ))
            } else {
                Result.failure(Exception("Failed to create repo: ${jsonObj.optString("message", "Unknown error")}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushFiles(
        token: String,
        owner: String,
        repo: String,
        files: Map<String, String>,
        commitMessage: String = "Initial extension commit"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val shaCache = mutableMapOf<String, String>()

            for ((path, content) in files) {
                val sha = getFileSha(token, owner, repo, path)
                if (sha != null) shaCache[path] = sha
            }

            for ((path, content) in files) {
                val json = JSONObject().apply {
                    put("message", commitMessage)
                    put("content", android.util.Base64.encodeToString(
                        content.toByteArray(),
                        android.util.Base64.NO_WRAP
                    ))
                    shaCache[path]?.let { put("sha", it) }
                }

                val request = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .put(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val error = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Failed to push $path: $error"))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileSha(token: String, owner: String, repo: String, path: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: "{}"
                JSONObject(body).optString("sha")
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createFileInRepo(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sha = getFileSha(token, owner, repo, path)
            val json = JSONObject().apply {
                put("message", message)
                put("content", android.util.Base64.encodeToString(
                    content.toByteArray(),
                    android.util.Base64.NO_WRAP
                ))
                sha?.let { put("sha", it) }
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .put(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed: ${response.body?.string()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateFullRepoFiles(extension: GeneratedExtension, config: RepoConfig): Map<String, String> {
        val files = mutableMapOf<String, String>()
        val providerDir = extension.providerName

        files["settings.gradle.kts"] = generateSettingsGradle()
        files["build.gradle.kts"] = generateRootBuildGradle()
        files["gradle.properties"] = generateGradleProperties()
        files[".gitignore"] = generateGitignore()
        files["gradle/wrapper/gradle-wrapper.properties"] = generateGradleWrapperProps()
        files[".github/workflows/build.yml"] = generateBuildWorkflow(config)
        files["$providerDir/build.gradle.kts"] = extension.buildGradleCode
        files["$providerDir/src/main/AndroidManifest.xml"] = extension.manifestCode
        files["$providerDir/src/main/kotlin/${extension.packageName.replace('.', '/')}/${extension.className}.kt"] = extension.providerCode
        files["$providerDir/src/main/kotlin/${extension.packageName.replace('.', '/')}/${extension.pluginClassName}.kt"] = extension.pluginCode

        if (config.includeRepoJson) {
            files["repo.json"] = extension.repoJson
        }

        return files
    }

    private fun generateSettingsGradle(): String {
        return """rootProject.name = "CloudstreamPlugins"

File(rootDir, ".").eachDir { dir ->
    if (File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
"""
    }

    private fun generateRootBuildGradle(): String {
        return """import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "user/repo")
    }

    android {
        namespace = "com.example"
        defaultConfig {
            minSdk = 21
            compileSdkVersion(35)
            targetSdk = 35
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations
        cloudstream("com.lagradost:cloudstream3:pre-release")
        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")
        implementation("org.jsoup:jsoup:1.18.3")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    }
}
"""
    }

    private fun generateGradleProperties(): String {
        return """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
"""
    }

    private fun generateGitignore(): String {
        return """*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
"""
    }

    private fun generateGradleWrapperProps(): String {
        return """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
    }

    private fun generateBuildWorkflow(config: RepoConfig): String {
        return """name: Build
concurrency:
  group: "build"
  cancel-in-progress: true
on:
  push:
    branches: [master, main]
    paths-ignore: ['*.md']
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@master
        with:
          path: "src"
      - name: Checkout builds
        uses: actions/checkout@master
        with:
          ref: "builds"
          path: "builds"
      - name: Clean old builds
        run: rm -f \$GITHUB_WORKSPACE/builds/*.cs3
      - name: Setup JDK 17
        uses: actions/setup-java@v1
        with:
          java-version: 17
      - name: Setup Android SDK
        uses: android-actions/setup-android@v2
      - name: Build Plugins
        run: |
          cd \$GITHUB_WORKSPACE/src
          chmod +x gradlew
          ./gradlew make makePluginsJson
          cp **/build/*.cs3 \$GITHUB_WORKSPACE/builds/
          cp build/plugins.json \$GITHUB_WORKSPACE/builds/
      - name: Push builds
        run: |
          cd \$GITHUB_WORKSPACE/builds
          git config --local user.email "actions@github.com"
          git config --local user.name "GitHub Actions"
          git add .
          git commit --amend -m "Build \$GITHUB_SHA" || exit 0
          git push --force
"""
    }
}

data class GitHubRepo(
    val name: String,
    val fullName: String,
    val url: String,
    val cloneUrl: String,
    val defaultBranch: String
)

data class RepoConfig(
    val includeRepoJson: Boolean = true,
    val includeGitHubActions: Boolean = true,
    val isPrivate: Boolean = false
)
