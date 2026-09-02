package com.cloudstreamextgen.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudstreamextgen.analysis.WebsiteAnalyzer
import com.cloudstreamextgen.generator.ExtensionGenerator
import com.cloudstreamextgen.generator.GeneratorConfig
import com.cloudstreamextgen.github.GitHubIntegration
import com.cloudstreamextgen.models.*
import com.cloudstreamextgen.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(initialUrl: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val analyzer = remember { WebsiteAnalyzer() }
    val generator = remember { ExtensionGenerator() }

    var urlInput by remember { mutableStateOf(initialUrl) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<SiteAnalysis?>(null) }
    var generatedExtension by remember { mutableStateOf<GeneratedExtension?>(null) }
    var progressStatus by remember { mutableStateOf("") }
    var showCodePreview by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }

    if (showPublishDialog && generatedExtension != null) {
        PublishDialog(
            extension = generatedExtension!!,
            onDismiss = { showPublishDialog = false },
            onPublish = { token, username, repoName ->
                scope.launch {
                    val github = GitHubIntegration()
                    val config = RepoConfig()
                    val files = github.generateFullRepoFiles(generatedExtension!!, config)

                    val result = github.createRepository(
                        token = token,
                        repoName = repoName,
                        description = "Cloudstream extension for ${generatedExtension!!.providerName}"
                    )

                    result.fold(
                        onSuccess = { repo ->
                            github.pushFiles(
                                token = token,
                                owner = username,
                                repo = repoName,
                                files = files
                            )
                            Toast.makeText(context, "Published to ${repo.url}", Toast.LENGTH_LONG).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyze Website", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Website URL") },
                        placeholder = { Text("https://example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isAnalyzing
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                scope.launch {
                                    isAnalyzing = true
                                    analysisResult = null
                                    generatedExtension = null

                                    analysisResult = analyzer.analyze(urlInput) { status, message ->
                                        progressStatus = message
                                    }

                                    if (analysisResult?.status != AnalysisStatus.ERROR) {
                                        val config = GeneratorConfig(
                                            providerName = analysisResult?.siteName?.replace(Regex("[^a-zA-Z]"), "")?.take(20) ?: "Site",
                                            packageName = "com.generated.${analysisResult?.siteName?.replace(Regex("[^a-zA-Z]"), "")?.lowercase()?.take(20) ?: "site"}"
                                        )
                                        generatedExtension = generator.generate(analysisResult!!, config)
                                    }

                                    isAnalyzing = false
                                    progressStatus = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = urlInput.isNotBlank() && !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(progressStatus)
                        } else {
                            Icon(Icons.Filled.Analytics, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze Website")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isAnalyzing) {
                AnalysisProgressCard(progressStatus)
            }

            analysisResult?.let { result ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { AnalysisSummaryCard(result) }
                    item { SiteTypeCard(result) }
                    if (result.searchAnalysis.hasSearch) {
                        item { SearchInfoCard(result.searchAnalysis) }
                    }
                    if (result.videoAnalysis.hasVideo) {
                        item { VideoInfoCard(result.videoAnalysis) }
                    }
                    if (result.antiBot.detectionMethods.isNotEmpty()) {
                        item { AntiBotCard(result.antiBot) }
                    }
                    if (generatedExtension != null) {
                        item {
                            Button(
                                onClick = { showCodePreview = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Code, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Preview Generated Code")
                            }
                        }
                        item {
                            OutlinedButton(
                                onClick = { showPublishDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publish to GitHub")
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            if (showCodePreview && generatedExtension != null) {
                CodePreviewSheet(
                    extension = generatedExtension!!,
                    onDismiss = { showCodePreview = false },
                    context = context
                )
            }
        }
    }
}

@Composable
fun AnalysisProgressCard(status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AnalysisSummaryCard(analysis: SiteAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Analysis Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("Site Name", analysis.siteName)
            InfoRow("Content Type", analysis.contentType.name)
            InfoRow("Language", analysis.metadata.language)
            InfoRow("Total Errors", analysis.errors.size.toString())
        }
    }
}

@Composable
fun SiteTypeCard(analysis: SiteAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Site Architecture",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            val siteTypeColor = when (analysis.siteType) {
                SiteType.HTML -> AccentBlue
                SiteType.JSON_API -> AccentGreen
                SiteType.JS_RENDERED -> AccentOrange
                SiteType.DYNAMIC -> AccentRed
                SiteType.HYBRID -> AccentPurple
                SiteType.IFRAME_EMBED -> AccentBlue
            }
            AssistChip(
                onClick = {},
                label = { Text(analysis.siteType.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(siteTypeColor)
                    )
                }
            )
            if (analysis.detectedApis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${analysis.detectedApis.size} API endpoints detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SearchInfoCard(search: SearchAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentGreen.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Search Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Method", search.searchMethod)
            InfoRow("Search Param", search.searchParamName)
            if (search.searchUrl.isNotEmpty()) {
                InfoRow("URL", search.searchUrl)
            }
            InfoRow("API Search", if (search.isApiSearch) "Yes" else "No")
        }
    }
}

@Composable
fun VideoInfoCard(video: VideoAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentPurple.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Video Sources Found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Iframe URLs", video.iframeUrls.size.toString())
            InfoRow("Known Domains", video.knownEmbedDomains.size.toString())
            if (video.requiresDecryption) {
                InfoRow("Requires Decryption", "Yes")
            }
        }
    }
}

@Composable
fun AntiBotCard(antiBot: AntiBotInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentOrange.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Anti-Bot Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            antiBot.detectionMethods.forEach { method ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        method,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow("Bypass Strategy", antiBot.bypassStrategy)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodePreviewSheet(
    extension: GeneratedExtension,
    onDismiss: () -> Unit,
    context: Context
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Provider", "Plugin", "Build", "Repo")
    val codeFiles = listOf(
        extension.providerCode,
        extension.pluginCode,
        extension.buildGradleCode,
        extension.repoJson
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Generated Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", codeFiles[selectedTab])
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 16.dp),
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = codeFiles[selectedTab],
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishDialog(
    extension: GeneratedExtension,
    onDismiss: () -> Unit,
    onPublish: (token: String, username: String, repoName: String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("${extension.providerName.lowercase()}-extension") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish to GitHub") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("GitHub Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text("Repository Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (token.isNotBlank() && username.isNotBlank() && repoName.isNotBlank()) {
                        onPublish(token, username, repoName)
                        onDismiss()
                    }
                }
            ) {
                Text("Publish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
