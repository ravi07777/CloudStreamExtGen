package com.cloudstreamextgen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    "Generation",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Code,
                    title = "Default Language",
                    subtitle = "English (en)"
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Security,
                    title = "Anti-Bot Strategy",
                    subtitle = "WebView with JS execution"
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Tune,
                    title = "Request Timeout",
                    subtitle = "30 seconds"
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.UserAgent,
                    title = "User Agent",
                    subtitle = "Desktop Chrome"
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Output",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Folder,
                    title = "Save Location",
                    subtitle = "App internal storage"
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.CloudUpload,
                    title = "Auto-Publish",
                    subtitle = "Disabled"
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Build,
                    title = "Build via GitHub Actions",
                    subtitle = "Enabled"
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "Version",
                    subtitle = "1.0.0"
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Filled.Description,
                    title = "License",
                    subtitle = "MIT License"
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}
