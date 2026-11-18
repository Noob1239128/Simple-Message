package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.MessagingRepository
import com.example.data.github.GitHubRelease
import com.example.data.github.ObtainiumIntegration
import com.example.data.model.Account
import com.example.data.model.AccountStatus
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.DailyMetric
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.MessengerSession
import com.example.data.model.OwnerAnalytics
import com.example.data.model.OwnerConfig
import com.example.data.model.UiState
import com.example.data.prefs.SessionPreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PulseChatViewModel(
    private val repository: MessagingRepository,
    private val prefs: SessionPreferences,
    val currentGoogleUid: String
) : ViewModel() {

    private val _session = MutableStateFlow<MessengerSession>(MessengerSession.LoggedOut)
    val session: StateFlow<MessengerSession> = _session.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _hasExistingOwnerConfig = MutableStateFlow<Boolean?>(null)
    val hasExistingOwnerConfig: StateFlow<Boolean?> = _hasExistingOwnerConfig.asStateFlow()

    private val _selectedConversation = MutableStateFlow<Conversation?>(null)
    val selectedConversation: StateFlow<Conversation?> = _selectedConversation.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    private var callTimerJob: Job? = null
    private val _callElapsedSeconds = MutableStateFlow(0L)
    val callElapsedSeconds: StateFlow<Long> = _callElapsedSeconds.asStateFlow()

    private val _githubRepoInput = MutableStateFlow("")
    val githubRepoInput: StateFlow<String> = _githubRepoInput.asStateFlow()

    private val _latestReleaseState = MutableStateFlow<UiState<GitHubRelease>?>(null)
    val latestReleaseState: StateFlow<UiState<GitHubRelease>?> = _latestReleaseState.asStateFlow()

    val darkTheme: StateFlow<Boolean> = prefs.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    private val _ownerAllMessages = MutableStateFlow<List<Message>>(emptyList())

    init {
        viewModelScope.launch {
            prefs.githubRepoFlow.collect { saved ->
                if (_githubRepoInput.value.isBlank() && saved.isNotBlank()) {
                    _githubRepoInput.value = saved
                }
            }
        }
        refreshOwnerConfigPresence()
    }

    fun refreshOwnerConfigPresence() {
        viewModelScope.launch {
            repository.getOwnerConfig().onSuccess { cfg ->
                _hasExistingOwnerConfig.value = cfg != null
                if (cfg != null && cfg.githubRepo.isNotBlank()) {
                    _githubRepoInput.value = cfg.githubRepo
                    prefs.saveGithubRepo(cfg.githubRepo)
                }
            }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            prefs.setDarkTheme(!darkTheme.value)
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    // --- User / Owner Login & Registration ---

    fun registerUser(nickname: String, password: String, bio: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _statusBanner.value = null
            repository.registerUser(nickname, password, bio)
                .onSuccess { account ->
                    _session.value = MessengerSession.User(account)
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Registrierung fehlgeschlagen."
                }
            _authBusy.value = false
        }
    }

    fun loginUser(nickname: String, password: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _statusBanner.value = null
            repository.loginUser(nickname, password)
                .onSuccess { account ->
                    _session.value = MessengerSession.User(account)
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Anmeldung fehlgeschlagen."
                }
            _authBusy.value = false
        }
    }

    fun loginOrSetupOwner(username: String, password: String) {
        viewModelScope.launch {
            _authBusy.value = true
            _statusBanner.value = null
            repository.setupOrLoginOwner(username, password)
                .onSuccess { config ->
                    _hasExistingOwnerConfig.value = true
                    if (config.githubRepo.isNotBlank()) {
                        _githubRepoInput.value = config.githubRepo
                        prefs.saveGithubRepo(config.githubRepo)
                    }
                    _session.value = MessengerSession.Owner(config)
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Owner-Anmeldung fehlgeschlagen."
                }
            _authBusy.value = false
        }
    }

    fun logoutMessengerSession() {
        val current = _session.value
        _selectedConversation.value = null
        _activeCallId.value = null
        stopCallTimer()
        _session.value = MessengerSession.LoggedOut
        if (current is MessengerSession.User) {
            viewModelScope.launch {
                repository.setOnlineStatus(current.account.nicknameLower, false)
            }
        }
    }

    // --- User Mode Streams ---

    val userConversations: StateFlow<UiState<List<Conversation>>> = _session
        .flatMapLatest { sess ->
            if (sess is MessengerSession.User) {
                repository.observeUserConversations(sess.account.nicknameLower)
                    .map<List<Conversation>, UiState<List<Conversation>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing user conversations", root)
                        emit(UiState.Error(root.message ?: "Chats konnten nicht geladen werden."))
                    }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeMessages: StateFlow<UiState<List<Message>>> = combine(
        _session,
        _selectedConversation
    ) { sess, conv -> sess to conv }
        .flatMapLatest { (sess, conv) ->
            if (conv == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                val forOwner = sess is MessengerSession.Owner
                repository.observeMessages(conv.id, forOwnerView = forOwner)
                    .map<List<Message>, UiState<List<Message>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        val root = fsErr ?: error
                        Log.w(TAG, "Error observing messages", root)
                        emit(UiState.Error(root.message ?: "Nachrichten konnten nicht geladen werden."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val incomingCalls: StateFlow<List<CallSession>> = _session
        .flatMapLatest { sess ->
            if (sess is MessengerSession.User) {
                repository.observeIncomingCalls(sess.account.nicknameLower)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeCall: StateFlow<CallSession?> = _activeCallId
        .flatMapLatest { callId ->
            if (callId == null) {
                flowOf(null)
            } else {
                repository.observeCall(callId)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(null)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // --- User Messaging & Call Actions ---

    fun startChatByNickname(targetNickname: String) {
        val sess = _session.value as? MessengerSession.User ?: return
        viewModelScope.launch {
            repository.openOrCreateConversationByNickname(sess.account, targetNickname)
                .onSuccess { conv ->
                    _selectedConversation.value = conv
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Chat konnte nicht geöffnet werden."
                }
        }
    }

    fun selectConversation(conversation: Conversation?) {
        _selectedConversation.value = conversation
    }

    fun sendText(text: String) {
        val sess = _session.value as? MessengerSession.User ?: return
        val conv = _selectedConversation.value ?: return
        val partner = conv.partnerNickname(sess.account.nicknameLower)
        viewModelScope.launch {
            repository.sendTextMessage(conv, sess.account.nicknameLower, partner, text)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Nachricht konnte nicht gesendet werden."
                }
        }
    }

    fun sendVoice(durationMs: Long, voiceBase64: String) {
        val sess = _session.value as? MessengerSession.User ?: return
        val conv = _selectedConversation.value ?: return
        val partner = conv.partnerNickname(sess.account.nicknameLower)
        viewModelScope.launch {
            repository.sendVoiceMessage(conv, sess.account.nicknameLower, partner, durationMs, voiceBase64)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Sprachnachricht fehlgeschlagen."
                }
        }
    }

    fun sendFile(fileName: String, mimeType: String, sizeBytes: Long, base64: String) {
        val sess = _session.value as? MessengerSession.User ?: return
        val conv = _selectedConversation.value ?: return
        val partner = conv.partnerNickname(sess.account.nicknameLower)
        viewModelScope.launch {
            repository.sendFileMessage(conv, sess.account.nicknameLower, partner, fileName, mimeType, sizeBytes, base64)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Datei konnte nicht gesendet werden."
                }
        }
    }

    fun startCall(recipientNickname: String, callType: CallType) {
        val sess = _session.value as? MessengerSession.User ?: return
        viewModelScope.launch {
            repository.startCall(sess.account.nicknameLower, recipientNickname, callType)
                .onSuccess { call ->
                    _activeCallId.value = call.id
                    _callElapsedSeconds.value = 0L
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Anruf konnte nicht gestartet werden."
                }
        }
    }

    fun acceptCall(call: CallSession) {
        _activeCallId.value = call.id
        startCallTimer()
        viewModelScope.launch {
            repository.updateCallStatus(call, CallStatus.ACTIVE, 0L)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Anruf konnte nicht angenommen werden."
                }
        }
    }

    fun declineCall(call: CallSession) {
        stopCallTimer()
        if (_activeCallId.value == call.id) _activeCallId.value = null
        viewModelScope.launch {
            repository.updateCallStatus(call, CallStatus.DECLINED, 0L)
        }
    }

    fun endActiveCall(call: CallSession) {
        val elapsed = _callElapsedSeconds.value
        val finalStatus = if (call.callStatusEnum == CallStatus.RINGING) CallStatus.MISSED else CallStatus.ENDED
        stopCallTimer()
        _activeCallId.value = null
        viewModelScope.launch {
            repository.updateCallStatus(call, finalStatus, elapsed)
        }
    }

    fun simulateConnectIfRinging(call: CallSession) {
        if (call.callStatusEnum == CallStatus.RINGING) {
            startCallTimer()
            viewModelScope.launch {
                repository.updateCallStatus(call, CallStatus.ACTIVE, 0L)
            }
        }
    }

    private fun startCallTimer() {
        if (callTimerJob?.isActive == true) return
        _callElapsedSeconds.value = 0L
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _callElapsedSeconds.value += 1L
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
        _callElapsedSeconds.value = 0L
    }

    // --- Owner Console Streams & Analytics ---

    val ownerAccounts: StateFlow<UiState<List<Account>>> = _session
        .flatMapLatest { sess ->
            if (sess is MessengerSession.Owner) {
                repository.observeOwnerAccounts()
                    .map<List<Account>, UiState<List<Account>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Nutzerliste nicht ladbar."))
                    }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerConversations: StateFlow<UiState<List<Conversation>>> = _session
        .flatMapLatest { sess ->
            if (sess is MessengerSession.Owner) {
                repository.observeOwnerConversations()
                    .map<List<Conversation>, UiState<List<Conversation>>> { convs ->
                        viewModelScope.launch {
                            _ownerAllMessages.value = repository.fetchAllMessagesForOwnerAnalytics(convs)
                        }
                        UiState.Success(convs)
                    }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Konversationen nicht ladbar."))
                    }
            } else {
                flowOf(UiState.Success(emptyList()))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val ownerCalls: StateFlow<List<CallSession>> = _session
        .flatMapLatest { sess ->
            if (sess is MessengerSession.Owner) {
                repository.observeOwnerCalls()
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val ownerAnalytics: StateFlow<OwnerAnalytics> = combine(
        ownerAccounts,
        ownerConversations,
        ownerCalls,
        _ownerAllMessages
    ) { accState, convState, calls, messages ->
        val accounts = (accState as? UiState.Success)?.data ?: emptyList()
        val convs = (convState as? UiState.Success)?.data ?: emptyList()

        val activeCount = accounts.count { it.accountStatus == AccountStatus.ACTIVE }
        val suspendedCount = accounts.count { it.accountStatus == AccountStatus.SUSPENDED }
        val onlineCount = accounts.count { it.online }

        val textCount = messages.count { it.messageType == MessageType.TEXT }
        val voiceCount = messages.count { it.messageType == MessageType.VOICE }
        val fileCount = messages.count { it.messageType == MessageType.FILE }
        val callLogCount = messages.count { it.messageType == MessageType.CALL_LOG }
        val totalMsgFallback = maxOf(messages.size, convs.sumOf { it.messageCount }.toInt())

        val cal = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("EE", Locale.GERMAN)
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val msgCountsByDay = messages.groupingBy { msg ->
            msg.createdAt?.toDate()?.let { keyFmt.format(it) } ?: ""
        }.eachCount()

        val daily = (6 downTo 0).map { offset ->
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val key = keyFmt.format(dayCal.time)
            DailyMetric(
                dayLabel = dayFmt.format(dayCal.time).take(2),
                messageCount = msgCountsByDay[key] ?: 0
            )
        }

        OwnerAnalytics(
            totalUsers = accounts.size,
            activeUsers = activeCount,
            suspendedUsers = suspendedCount,
            onlineUsers = onlineCount,
            totalConversations = convs.size,
            totalMessages = totalMsgFallback,
            textMessages = textCount,
            voiceMessages = voiceCount,
            fileMessages = fileCount,
            callEvents = maxOf(calls.size, callLogCount),
            totalCallSeconds = calls.sumOf { it.durationSec },
            dailyActivity = daily
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), OwnerAnalytics())

    fun toggleAccountSuspension(account: Account) {
        val next = if (account.accountStatus == AccountStatus.ACTIVE) {
            AccountStatus.SUSPENDED
        } else {
            AccountStatus.ACTIVE
        }
        viewModelScope.launch {
            repository.setAccountStatus(account.id, next)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Status konnte nicht geändert werden."
                }
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.deleteUserAccount(account.id)
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Konto konnte nicht gelöscht werden."
                }
        }
    }

    // --- GitHub & Obtainium Actions ---

    fun updateGithubRepoInput(value: String) {
        _githubRepoInput.value = value
    }

    fun saveAndCheckGithubRelease() {
        val repo = _githubRepoInput.value.trim()
        viewModelScope.launch {
            prefs.saveGithubRepo(repo)
            if (_session.value is MessengerSession.Owner) {
                repository.updateOwnerGithubRepo(repo)
            }
            _latestReleaseState.value = UiState.Loading
            ObtainiumIntegration.fetchLatestRelease(repo)
                .onSuccess { release ->
                    _latestReleaseState.value = UiState.Success(release)
                }
                .onFailure { err ->
                    _latestReleaseState.value = UiState.Error(
                        err.localizedMessage ?: "GitHub Release konnte nicht geladen werden."
                    )
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCallTimer()
    }

    private companion object {
        const val TAG = "PulseChatVM"
    }
}
