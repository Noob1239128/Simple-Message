package com.example.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.github.GitHubRelease
import com.example.data.model.Account
import com.example.data.model.AccountStatus
import com.example.data.model.Conversation
import com.example.data.model.OwnerAnalytics
import com.example.data.model.OwnerConfig
import com.example.data.model.UiState
import com.example.ui.settings.ObtainiumIntegrationCard
import com.example.ui.theme.EmeraldOnline
import java.text.SimpleDateFormat
import java.util.Locale

private enum class OwnerTab(val label: String, val icon: ImageVector) {
    ANALYTICS("Analysen", Icons.Rounded.Analytics),
    CONVERSATIONS("Chats", Icons.AutoMirrored.Rounded.Chat),
    USERS("Nutzer", Icons.Rounded.Group),
    GITHUB("GitHub", Icons.Rounded.SystemUpdateAlt)
}

private enum class UserFilter(val label: String) {
    ALL("Alle"),
    ACTIVE("Aktiv"),
    SUSPENDED("Gesperrt"),
    ONLINE("Online")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(
    ownerConfig: OwnerConfig,
    analytics: OwnerAnalytics,
    accountsState: UiState<List<Account>>,
    conversationsState: UiState<List<Conversation>>,
    githubRepoInput: String,
    latestReleaseState: UiState<GitHubRelease>?,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onInspectConversation: (Conversation) -> Unit,
    onToggleAccountSuspension: (Account) -> Unit,
    onDeleteAccount: (Account) -> Unit,
    onRepoInputChange: (String) -> Unit,
    onCheckGithubRelease: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(OwnerTab.ANALYTICS) }
    var userSearch by remember { mutableStateOf("") }
    var userFilter by remember { mutableStateOf(UserFilter.ALL) }
    var chatSearch by remember { mutableStateOf("") }
    var accountPendingDelete by remember { mutableStateOf<Account?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.GERMAN) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Owner: ${ownerConfig.username}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Verwaltung & App-Analysen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Design wechseln"
                            )
                        }
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier.testTag("owner_logout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Abmelden")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        OwnerTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("owner_nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OwnerTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("owner_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (selectedTab) {
                        OwnerTab.ANALYTICS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 820.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    KpiTile(
                                        label = "Registrierte Nutzer",
                                        value = "${analytics.totalUsers}",
                                        sub = "${analytics.onlineUsers} online",
                                        modifier = Modifier.weight(1f)
                                    )
                                    KpiTile(
                                        label = "Konversationen",
                                        value = "${analytics.totalConversations}",
                                        sub = "${analytics.totalMessages} Nachrichten",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    KpiTile(
                                        label = "Sprache & Dateien",
                                        value = "${analytics.voiceMessages + analytics.fileMessages}",
                                        sub = "${analytics.voiceMessages} Audio • ${analytics.fileMessages} Dateien",
                                        modifier = Modifier.weight(1f)
                                    )
                                    KpiTile(
                                        label = "Anrufe",
                                        value = "${analytics.callEvents}",
                                        sub = "${analytics.totalCallSeconds}s gesamt",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                DailyActivityBarChart(analytics = analytics)
                                MessageBreakdownCard(analytics = analytics)
                            }
                        }

                        OwnerTab.CONVERSATIONS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 820.dp)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = chatSearch,
                                    onValueChange = { chatSearch = it },
                                    placeholder = { Text("Konversation nach Nickname filtern…") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("owner_chat_search")
                                )

                                when (conversationsState) {
                                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                    is UiState.Error -> Text(conversationsState.message, color = MaterialTheme.colorScheme.error)
                                    is UiState.Success -> {
                                        val filtered = conversationsState.data.filter { conv ->
                                            chatSearch.isBlank() ||
                                                conv.participantNicknames.any { it.contains(chatSearch.trim(), ignoreCase = true) } ||
                                                conv.lastMessagePreview.contains(chatSearch.trim(), ignoreCase = true)
                                        }
                                        if (filtered.isEmpty()) {
                                            Text(
                                                text = "Keine Konversationen gefunden.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 24.dp)
                                            )
                                        } else {
                                            LazyColumn(
                                                contentPadding = PaddingValues(bottom = 24.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(filtered, key = { it.id }) { conv ->
                                                    val participants = conv.participantNicknames.joinToString(" ↔ ") { "@$it" }
                                                    val updated = conv.updatedAt?.toDate()?.let { dateFmt.format(it) } ?: ""
                                                    Surface(
                                                        shape = RoundedCornerShape(18.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(18.dp))
                                                            .clickable { onInspectConversation(conv) }
                                                            .testTag("owner_conv_item_${conv.id}")
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = participants,
                                                                    style = MaterialTheme.typography.titleSmall,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Text(
                                                                    text = "${conv.messageCount} Nachr.",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            Text(
                                                                text = conv.lastMessagePreview,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (updated.isNotBlank()) {
                                                                Text(
                                                                    text = "Aktualisiert: $updated",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OwnerTab.USERS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 820.dp)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = userSearch,
                                    onValueChange = { userSearch = it },
                                    placeholder = { Text("Registrierten Nutzer suchen…") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("owner_user_search")
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    UserFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = userFilter == filter,
                                            onClick = { userFilter = filter },
                                            label = { Text(filter.label) }
                                        )
                                    }
                                }

                                when (accountsState) {
                                    is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                    is UiState.Error -> Text(accountsState.message, color = MaterialTheme.colorScheme.error)
                                    is UiState.Success -> {
                                        val users = accountsState.data.filter { acc ->
                                            val matchesQuery = userSearch.isBlank() ||
                                                acc.nickname.contains(userSearch.trim(), ignoreCase = true) ||
                                                acc.bio.contains(userSearch.trim(), ignoreCase = true)
                                            val matchesFilter = when (userFilter) {
                                                UserFilter.ALL -> true
                                                UserFilter.ACTIVE -> acc.accountStatus == AccountStatus.ACTIVE
                                                UserFilter.SUSPENDED -> acc.accountStatus == AccountStatus.SUSPENDED
                                                UserFilter.ONLINE -> acc.online
                                            }
                                            matchesQuery && matchesFilter
                                        }
                                        if (users.isEmpty()) {
                                            Text(
                                                text = "Keine registrierten Nutzer gefunden.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 24.dp)
                                            )
                                        } else {
                                            LazyColumn(
                                                contentPadding = PaddingValues(bottom = 24.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(users, key = { it.id }) { acc ->
                                                    val isSuspended = acc.accountStatus == AccountStatus.SUSPENDED
                                                    val regDate = acc.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Neu"

                                                    Card(
                                                        colors = CardDefaults.cardColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                                        ),
                                                        shape = RoundedCornerShape(18.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("owner_user_item_${acc.id}")
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(14.dp),
                                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                                ) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(42.dp)
                                                                            .clip(CircleShape)
                                                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Text(
                                                                            text = acc.nickname.take(1).uppercase(),
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                                        )
                                                                    }
                                                                    Column {
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "@${acc.nickname}",
                                                                                style = MaterialTheme.typography.titleMedium,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                            if (acc.online) {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .size(8.dp)
                                                                                        .clip(CircleShape)
                                                                                        .background(EmeraldOnline)
                                                                                )
                                                                            }
                                                                        }
                                                                        Text(
                                                                            text = "Registriert: $regDate",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                        )
                                                                    }
                                                                }

                                                                Surface(
                                                                    shape = RoundedCornerShape(99.dp),
                                                                    color = if (isSuspended)
                                                                        MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                                                    else
                                                                        MaterialTheme.colorScheme.primaryContainer
                                                                ) {
                                                                    Text(
                                                                        text = if (isSuspended) "Gesperrt" else "Aktiv",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = if (isSuspended)
                                                                            MaterialTheme.colorScheme.error
                                                                        else
                                                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                                    )
                                                                }
                                                            }

                                                            if (acc.bio.isNotBlank()) {
                                                                Text(
                                                                    text = acc.bio,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }

                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                OutlinedButton(
                                                                    onClick = { onToggleAccountSuspension(acc) },
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(48.dp)
                                                                        .testTag("toggle_suspend_${acc.id}"),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (isSuspended) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.size(6.dp))
                                                                    Text(if (isSuspended) "Entsperren" else "Sperren")
                                                                }

                                                                OutlinedButton(
                                                                    onClick = { accountPendingDelete = acc },
                                                                    modifier = Modifier
                                                                        .height(48.dp)
                                                                        .testTag("delete_user_${acc.id}"),
                                                                    shape = RoundedCornerShape(12.dp),
                                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                                        contentColor = MaterialTheme.colorScheme.error
                                                                    )
                                                                ) {
                                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Nutzer löschen")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OwnerTab.GITHUB -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 680.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                ObtainiumIntegrationCard(
                                    githubRepoInput = githubRepoInput,
                                    latestReleaseState = latestReleaseState,
                                    onRepoInputChange = onRepoInputChange,
                                    onCheckRelease = onCheckGithubRelease
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val toDelete = accountPendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { accountPendingDelete = null },
            title = { Text("Nutzerkonto löschen?") },
            text = { Text("Möchtest du das Konto @${toDelete.nickname} dauerhaft entfernen?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(toDelete)
                        accountPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
