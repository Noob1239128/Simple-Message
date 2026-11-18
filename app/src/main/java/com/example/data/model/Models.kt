package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class AccountStatus {
    ACTIVE,
    SUSPENDED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class MessageType {
    TEXT,
    VOICE,
    FILE,
    CALL_LOG;

    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

enum class CallType {
    AUDIO,
    VIDEO;

    companion object {
        fun fromRaw(raw: String?): CallType =
            entries.firstOrNull { it.name == raw } ?: AUDIO
    }
}

enum class CallStatus {
    RINGING,
    ACTIVE,
    ENDED,
    MISSED,
    DECLINED;

    companion object {
        fun fromRaw(raw: String?): CallStatus =
            entries.firstOrNull { it.name == raw } ?: ENDED
    }
}

@Keep
@IgnoreExtraProperties
data class Account(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val nickname: String = "",
    val nicknameLower: String = "",
    val role: String = "USER",
    val status: String = "ACTIVE",
    val online: Boolean = false,
    val bio: String = "",
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val accountStatus: AccountStatus get() = AccountStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AccountCredential(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val passwordHash: String = "",
    val salt: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OwnerConfig(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val username: String = "",
    val usernameLower: String = "",
    val passwordHash: String = "",
    val salt: String = "",
    val githubRepo: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val participantNicknames: List<String> = emptyList(),
    val participantUids: List<String> = emptyList(),
    val lastMessagePreview: String = "",
    val lastMessageType: String = "TEXT",
    val lastSenderNickname: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun partnerNickname(myNicknameLower: String): String =
        participantNicknames.firstOrNull { it != myNicknameLower }
            ?: participantNicknames.firstOrNull().orEmpty()
}

@Keep
@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val participantUids: List<String> = emptyList(),
    val senderNickname: String = "",
    val recipientNickname: String = "",
    val type: String = "TEXT",
    val textContent: String = "",
    val voiceDurationMs: Long = 0L,
    val voiceBase64: String = "",
    val fileName: String = "",
    val fileMimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val fileBase64: String = "",
    val callDurationSec: Long = 0L,
    val callStatus: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val messageType: MessageType get() = MessageType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val participantUids: List<String> = emptyList(),
    val callerNickname: String = "",
    val recipientNickname: String = "",
    val callType: String = "AUDIO",
    val status: String = "RINGING",
    val durationSec: Long = 0L,
    @ServerTimestamp val endedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val callStatusEnum: CallStatus get() = CallStatus.fromRaw(status)
    val callTypeEnum: CallType get() = CallType.fromRaw(callType)
}

sealed interface MessengerSession {
    data object LoggedOut : MessengerSession
    data class User(val account: Account) : MessengerSession
    data class Owner(val config: OwnerConfig) : MessengerSession
}

data class OwnerAnalytics(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val suspendedUsers: Int = 0,
    val onlineUsers: Int = 0,
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val textMessages: Int = 0,
    val voiceMessages: Int = 0,
    val fileMessages: Int = 0,
    val callEvents: Int = 0,
    val totalCallSeconds: Long = 0L,
    val dailyActivity: List<DailyMetric> = emptyList()
)

data class DailyMetric(
    val dayLabel: String,
    val messageCount: Int
)
