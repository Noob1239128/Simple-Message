package com.example.ui.user

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.UiState
import com.example.media.AudioVoiceManager
import com.example.media.FileAttachmentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversation: Conversation,
    myNicknameLower: String,
    messagesState: UiState<List<Message>>,
    readOnlyOwnerAudit: Boolean = false,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onSendVoice: (durationMs: Long, voiceBase64: String) -> Unit,
    onSendFile: (fileName: String, mimeType: String, sizeBytes: Long, base64: String) -> Unit,
    onStartCall: (recipientNickname: String, callType: CallType) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { AudioVoiceManager(context) }
    DisposableEffect(Unit) {
        onDispose { audioManager.release() }
    }

    val partnerNickname = if (readOnlyOwnerAudit) {
        conversation.participantNicknames.joinToString(" ↔ ") { "@$it" }
    } else {
        "@${conversation.partnerNickname(myNicknameLower)}"
    }
    val rawPartner = conversation.partnerNickname(myNicknameLower)

    var messageText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableLongStateOf(0L) }
    var recordingAmp by remember { mutableFloatStateOf(0.15f) }
    var playingMessageId by remember { mutableStateOf<String?>(null) }
    var pendingCallType by remember { mutableStateOf<CallType?>(null) }
    var pendingVoiceAfterPermission by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMAN) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0L
            while (isRecording) {
                delay(200L)
                recordingAmp = audioManager.getAmplitudeNormalized()
                if (System.currentTimeMillis() % 1000L < 220L) {
                    recordingSeconds += 1L
                }
            }
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val callT = pendingCallType
            if (callT != null) {
                pendingCallType = null
                onStartCall(rawPartner, callT)
            } else if (pendingVoiceAfterPermission) {
                pendingVoiceAfterPermission = false
                isRecording = audioManager.startRecording()
            }
        } else {
            pendingCallType = null
            pendingVoiceAfterPermission = false
            Toast.makeText(context, "Mikrofonberechtigung wird für Audio benötigt.", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    FileAttachmentHelper.readAndPrepareAttachment(context, uri)
                }
                res.onSuccess { prep ->
                    onSendFile(prep.fileName, prep.mimeType, prep.sizeBytes, prep.base64)
                }.onFailure { err ->
                    Toast.makeText(context, err.localizedMessage ?: "Foto konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    FileAttachmentHelper.readAndPrepareAttachment(context, uri)
                }
                res.onSuccess { prep ->
                    onSendFile(prep.fileName, prep.mimeType, prep.sizeBytes, prep.base64)
                }.onFailure { err ->
                    Toast.makeText(context, err.localizedMessage ?: "Datei konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun requestMicAndRun(callType: CallType?, forVoice: Boolean) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (callType != null) onStartCall(rawPartner, callType)
            else if (forVoice) isRecording = audioManager.startRecording()
        } else {
            pendingCallType = callType
            pendingVoiceAfterPermission = forVoice
            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = partnerNickname,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (readOnlyOwnerAudit) "Owner-Konversationsansicht" else "Direktnachricht",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (!readOnlyOwnerAudit) {
                        IconButton(
                            onClick = { requestMicAndRun(CallType.AUDIO, false) },
                            modifier = Modifier.testTag("start_audio_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Sprachanruf starten")
                        }
                        IconButton(
                            onClick = { requestMicAndRun(CallType.VIDEO, false) },
                            modifier = Modifier.testTag("start_video_call_button")
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = "Videoanruf starten")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!readOnlyOwnerAudit) {
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        AnimatedVisibility(visible = isRecording) {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Aufnahme läuft (${recordingSeconds}s)…",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = {
                                                isRecording = false
                                                audioManager.stopRecordingAndDiscard()
                                            }
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Aufnahme verwerfen")
                                        }
                                        FilledIconButton(
                                            onClick = {
                                                isRecording = false
                                                val rec = audioManager.stopRecordingAndEncode()
                                                if (rec != null) {
                                                    onSendVoice(rec.durationMs, rec.base64)
                                                } else {
                                                    Toast.makeText(context, "Sprachnachricht konnte nicht gespeichert werden.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.testTag("send_voice_button")
                                        ) {
                                            Icon(Icons.Rounded.Stop, contentDescription = "Aufnahme senden")
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showAttachMenu = true },
                                    modifier = Modifier.testTag("attach_file_button")
                                ) {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = "Datei oder Foto anhängen")
                                }
                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Foto / Bild auswählen") },
                                        leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                                        onClick = {
                                            showAttachMenu = false
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Dokument / Datei senden") },
                                        leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                                        onClick = {
                                            showAttachMenu = false
                                            docPickerLauncher.launch(arrayOf("*/*"))
                                        }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = { Text("Nachricht schreiben…") },
                                maxLines = 4,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("message_text_input")
                            )

                            if (messageText.isNotBlank()) {
                                FilledIconButton(
                                    onClick = {
                                        onSendText(messageText)
                                        messageText = ""
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("send_text_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Nachricht senden")
                                }
                            } else {
                                FilledIconButton(
                                    onClick = {
                                        if (isRecording) {
                                            isRecording = false
                                            val rec = audioManager.stopRecordingAndEncode()
                                            if (rec != null) onSendVoice(rec.durationMs, rec.base64)
                                        } else {
                                            requestMicAndRun(null, true)
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("record_voice_button")
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                        contentDescription = "Sprachnachricht aufnehmen"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when (messagesState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(messagesState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val messages = messagesState.data
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = !readOnlyOwnerAudit && msg.senderNickname == myNicknameLower
                        val timeText = msg.createdAt?.toDate()?.let { timeFmt.format(it) } ?: ""

                        if (msg.messageType == MessageType.CALL_LOG) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(99.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Call,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${msg.textContent.ifBlank { "Anruf" }} • $timeText",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isMine) 18.dp else 4.dp,
                                        bottomEnd = if (isMine) 4.dp else 18.dp
                                    ),
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.widthIn(max = 310.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (readOnlyOwnerAudit || !isMine) {
                                            Text(
                                                text = "@${msg.senderNickname}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        when (msg.messageType) {
                                            MessageType.TEXT -> {
                                                Text(
                                                    text = msg.textContent,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }
                                            MessageType.VOICE -> {
                                                val isPlaying = playingMessageId == msg.id
                                                val secs = (msg.voiceDurationMs / 1000L).coerceAtLeast(1L)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    FilledIconButton(
                                                        onClick = {
                                                            audioManager.togglePlayback(msg.id, msg.voiceBase64) { active ->
                                                                playingMessageId = active
                                                            }
                                                        },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                            contentDescription = "Sprachnachricht abspielen"
                                                        )
                                                    }
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        listOf(10, 18, 26, 14, 24, 16, 22, 12, 20, 14).forEach { h ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(3.dp)
                                                                    .height(h.dp)
                                                                    .clip(RoundedCornerShape(2.dp))
                                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 1f else 0.6f))
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = String.format("0:%02d", secs),
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                }
                                            }
                                            MessageType.FILE -> {
                                                val isImage = msg.fileMimeType.startsWith("image/") && msg.fileBase64.isNotBlank()
                                                val decodedBitmap = remember(msg.id, msg.fileBase64) {
                                                    if (isImage) {
                                                        runCatching {
                                                            val bytes = Base64.decode(msg.fileBase64, Base64.DEFAULT)
                                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                                        }.getOrNull()
                                                    } else null
                                                }
                                                if (decodedBitmap != null) {
                                                    Image(
                                                        bitmap = decodedBitmap,
                                                        contentDescription = msg.fileName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(170.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                FileAttachmentHelper.openAttachment(
                                                                    context, msg.id, msg.fileName, msg.fileMimeType, msg.fileBase64
                                                                )
                                                            }
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            val opened = FileAttachmentHelper.openAttachment(
                                                                context, msg.id, msg.fileName, msg.fileMimeType, msg.fileBase64
                                                            )
                                                            if (!opened) {
                                                                Toast.makeText(context, "Keine App zum Öffnen gefunden.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        .padding(vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Description, contentDescription = null)
                                                    Column {
                                                        Text(
                                                            text = msg.fileName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${FileAttachmentHelper.formatFileSize(msg.fileSizeBytes)} • Antippen zum Öffnen",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            MessageType.CALL_LOG -> Unit
                                        }

                                        Text(
                                            text = timeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.End)
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
