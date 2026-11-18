package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.github.GitHubRelease
import com.example.data.github.ObtainiumIntegration
import com.example.data.model.UiState
import com.example.media.FileAttachmentHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObtainiumBottomSheet(
    githubRepoInput: String,
    latestReleaseState: UiState<GitHubRelease>?,
    onRepoInputChange: (String) -> Unit,
    onCheckRelease: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ObtainiumIntegrationCard(
                githubRepoInput = githubRepoInput,
                latestReleaseState = latestReleaseState,
                onRepoInputChange = onRepoInputChange,
                onCheckRelease = onCheckRelease
            )
        }
    }
}

@Composable
fun ObtainiumIntegrationCard(
    githubRepoInput: String,
    latestReleaseState: UiState<GitHubRelease>?,
    onRepoInputChange: (String) -> Unit,
    onCheckRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdateAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "GitHub & Obtainium Updates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Installierte Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Dieses Projekt enthält einen fertigen GitHub-Actions-Release-Workflow (.github/workflows/release.yml) und eine obtainium.json. Trage dein GitHub-Repository ein, um Releases zu prüfen oder direkt in Obtainium hinzuzufügen:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = githubRepoInput,
                    onValueChange = onRepoInputChange,
                    label = { Text("GitHub-Repository (owner/repo)") },
                    placeholder = { Text("z. B. meinname/PulseChat") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("obtainium_repo_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCheckRelease,
                        enabled = githubRepoInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("check_github_release_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Release prüfen")
                    }

                    OutlinedButton(
                        onClick = {
                            val json = ObtainiumIntegration.buildObtainiumJson(githubRepoInput)
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Obtainium Config", json))
                            Toast.makeText(context, "Obtainium-JSON kopiert", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_obtainium_json_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("JSON kopieren")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val deepLink = ObtainiumIntegration.buildObtainiumDeepLink(githubRepoInput)
                            val opened = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)))
                                true
                            }.getOrDefault(false)
                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    "Obtainium ist nicht installiert – öffne GitHub Releases.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                val webUrl = ObtainiumIntegration.buildGithubReleasesUrl(githubRepoInput)
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                }
                            }
                        },
                        enabled = ObtainiumIntegration.parseOwnerRepo(githubRepoInput) != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_in_obtainium_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("In Obtainium")
                    }

                    OutlinedButton(
                        onClick = {
                            val webUrl = ObtainiumIntegration.buildGithubReleasesUrl(githubRepoInput)
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                            }
                        },
                        enabled = ObtainiumIntegration.parseOwnerRepo(githubRepoInput) != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_github_releases_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Releases")
                    }
                }
            }
        }

        when (latestReleaseState) {
            is UiState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("Frage GitHub Releases API ab…", style = MaterialTheme.typography.bodySmall)
                }
            }
            is UiState.Success -> {
                val rel = latestReleaseState.data
                val apkAsset = rel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Aktuelles Release: ${rel.tagName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (apkAsset != null) {
                            Text(
                                text = "APK-Asset: ${apkAsset.name} (${FileAttachmentHelper.formatFileSize(apkAsset.size)})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else {
                            Text(
                                text = "Hinweis: In diesem Release wurde noch keine .apk-Datei angehängt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (rel.body.isNotBlank()) {
                            Text(
                                text = rel.body.take(260),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            is UiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = latestReleaseState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            null -> Unit
        }
    }
}
