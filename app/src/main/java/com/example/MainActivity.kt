package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firestore.MessagingRepository
import com.example.data.model.MessengerSession
import com.example.data.prefs.SessionPreferences
import com.example.ui.PulseChatViewModel
import com.example.ui.auth.GoogleAuthScreen
import com.example.ui.auth.MessengerLoginHubScreen
import com.example.ui.auth.signOutGoogle
import com.example.ui.owner.OwnerDashboardScreen
import com.example.ui.settings.ObtainiumBottomSheet
import com.example.ui.theme.PulseChatTheme
import com.example.ui.user.ActiveCallOverlay
import com.example.ui.user.ChatDetailScreen
import com.example.ui.user.UserChatListScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseChatRoot()
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun PulseChatRoot(auth: FirebaseAuth? = null) {
    val context = LocalContext.current
    val resolvedAuth = remember(auth, context) {
        auth ?: if (com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) {
            Firebase.auth
        } else {
            null
        }
    }

    if (resolvedAuth == null) {
        PulseChatTheme(darkTheme = true) {
            GoogleAuthScreen(onAuthSuccess = {})
        }
        return
    }

    val currentUser by resolvedAuth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = resolvedAuth.currentUser)
    val user = currentUser

    if (user == null) {
        PulseChatTheme(darkTheme = true) {
            GoogleAuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            AuthenticatedPulseChatApp(currentGoogleUid = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedPulseChatApp(currentGoogleUid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: PulseChatViewModel = viewModel(
        key = currentGoogleUid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PulseChatViewModel(
                    repository = MessagingRepository(db),
                    prefs = SessionPreferences(app),
                    currentGoogleUid = currentGoogleUid
                )
            }
        }
    )

    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val hasExistingOwner by viewModel.hasExistingOwnerConfig.collectAsStateWithLifecycle()
    val selectedConversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val activeMessagesState by viewModel.activeMessages.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val callElapsedSeconds by viewModel.callElapsedSeconds.collectAsStateWithLifecycle()
    val incomingCalls by viewModel.incomingCalls.collectAsStateWithLifecycle()
    val githubRepoInput by viewModel.githubRepoInput.collectAsStateWithLifecycle()
    val latestReleaseState by viewModel.latestReleaseState.collectAsStateWithLifecycle()

    var showObtainiumSheet by remember { mutableStateOf(false) }

    PulseChatTheme(darkTheme = darkTheme) {
        when (val currentSession = session) {
            is MessengerSession.LoggedOut -> {
                MessengerLoginHubScreen(
                    isBusy = authBusy,
                    errorMessage = statusBanner,
                    hasExistingOwnerConfig = hasExistingOwner,
                    darkTheme = darkTheme,
                    onToggleTheme = { viewModel.toggleDarkTheme() },
                    onUserLogin = { nick, pass -> viewModel.loginUser(nick, pass) },
                    onUserRegister = { nick, pass, bio -> viewModel.registerUser(nick, pass, bio) },
                    onOwnerLoginOrSetup = { user, pass -> viewModel.loginOrSetupOwner(user, pass) },
                    onOpenObtainiumSheet = { showObtainiumSheet = true },
                    onSignOutGoogle = {
                        viewModel.logoutMessengerSession()
                        signOutGoogle(credentialManager, scope)
                    },
                    onDismissError = { viewModel.clearStatusBanner() }
                )
            }

            is MessengerSession.User -> {
                val conversationsState by viewModel.userConversations.collectAsStateWithLifecycle()
                val conv = selectedConversation
                val call = activeCall

                if (call != null) {
                    ActiveCallOverlay(
                        call = call,
                        myNicknameLower = currentSession.account.nicknameLower,
                        elapsedSeconds = callElapsedSeconds,
                        onSimulateConnect = { viewModel.simulateConnectIfRinging(call) },
                        onHangUp = { viewModel.endActiveCall(call) }
                    )
                } else if (conv != null) {
                    ChatDetailScreen(
                        conversation = conv,
                        myNicknameLower = currentSession.account.nicknameLower,
                        messagesState = activeMessagesState,
                        readOnlyOwnerAudit = false,
                        onBack = { viewModel.selectConversation(null) },
                        onSendText = { text -> viewModel.sendText(text) },
                        onSendVoice = { dur, b64 -> viewModel.sendVoice(dur, b64) },
                        onSendFile = { name, mime, size, b64 -> viewModel.sendFile(name, mime, size, b64) },
                        onStartCall = { recipient, type -> viewModel.startCall(recipient, type) }
                    )
                } else {
                    UserChatListScreen(
                        currentAccount = currentSession.account,
                        conversationsState = conversationsState,
                        incomingCalls = incomingCalls,
                        statusBanner = statusBanner,
                        darkTheme = darkTheme,
                        onToggleTheme = { viewModel.toggleDarkTheme() },
                        onOpenObtainium = { showObtainiumSheet = true },
                        onDismissBanner = { viewModel.clearStatusBanner() },
                        onStartChatByNickname = { nick -> viewModel.startChatByNickname(nick) },
                        onOpenConversation = { c -> viewModel.selectConversation(c) },
                        onAcceptCall = { c -> viewModel.acceptCall(c) },
                        onDeclineCall = { c -> viewModel.declineCall(c) },
                        onLogout = { viewModel.logoutMessengerSession() }
                    )
                }
            }

            is MessengerSession.Owner -> {
                val ownerAccountsState by viewModel.ownerAccounts.collectAsStateWithLifecycle()
                val ownerConversationsState by viewModel.ownerConversations.collectAsStateWithLifecycle()
                val ownerAnalytics by viewModel.ownerAnalytics.collectAsStateWithLifecycle()
                val conv = selectedConversation

                if (conv != null) {
                    ChatDetailScreen(
                        conversation = conv,
                        myNicknameLower = "",
                        messagesState = activeMessagesState,
                        readOnlyOwnerAudit = true,
                        onBack = { viewModel.selectConversation(null) },
                        onSendText = {},
                        onSendVoice = { _, _ -> },
                        onSendFile = { _, _, _, _ -> },
                        onStartCall = { _, _ -> }
                    )
                } else {
                    OwnerDashboardScreen(
                        ownerConfig = currentSession.config,
                        analytics = ownerAnalytics,
                        accountsState = ownerAccountsState,
                        conversationsState = ownerConversationsState,
                        githubRepoInput = githubRepoInput,
                        latestReleaseState = latestReleaseState,
                        darkTheme = darkTheme,
                        onToggleTheme = { viewModel.toggleDarkTheme() },
                        onInspectConversation = { c -> viewModel.selectConversation(c) },
                        onToggleAccountSuspension = { acc -> viewModel.toggleAccountSuspension(acc) },
                        onDeleteAccount = { acc -> viewModel.deleteAccount(acc) },
                        onRepoInputChange = { viewModel.updateGithubRepoInput(it) },
                        onCheckGithubRelease = { viewModel.saveAndCheckGithubRelease() },
                        onLogout = { viewModel.logoutMessengerSession() }
                    )
                }
            }
        }

        if (showObtainiumSheet) {
            ObtainiumBottomSheet(
                githubRepoInput = githubRepoInput,
                latestReleaseState = latestReleaseState,
                onRepoInputChange = { viewModel.updateGithubRepoInput(it) },
                onCheckRelease = { viewModel.saveAndCheckGithubRelease() },
                onDismiss = { showObtainiumSheet = false }
            )
        }
    }
}
