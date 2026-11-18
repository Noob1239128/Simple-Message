package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.data.model.Account
import com.example.data.model.AccountCredential
import com.example.data.model.AccountStatus
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.OwnerConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessagingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Google-Anmeldung erforderlich, bevor auf Firestore zugegriffen wird.")

    private suspend fun isGlobalAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.email == "janowitzleon@gmail.com" && user.isEmailVerified) return true
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    // --- User Registration & Login (Nickname + Password) ---

    suspend fun registerUser(nicknameRaw: String, password: String, bio: String = ""): Result<Account> {
        val norm = CryptoUtil.normalizeNickname(nicknameRaw)
        val path = "accounts/$norm"
        return try {
            val uid = requireUserId()
            require(CryptoUtil.isValidNickname(nicknameRaw)) {
                "Nickname muss 3–24 Zeichen lang sein (a-z, 0-9, _ oder -)."
            }
            require(password.length >= 4) { "Passwort muss mindestens 4 Zeichen lang sein." }
            val trimmedBio = bio.trim().take(160)

            val accountRef = db.collection("accounts").document(norm)
            val existing = accountRef.get().await()
            if (existing.exists()) {
                return Result.failure(IllegalStateException("Der Nickname \"$norm\" ist bereits vergeben."))
            }

            val salt = CryptoUtil.generateSalt()
            val passwordHash = CryptoUtil.hashPassword(password, salt)

            val accountPayload = mapOf(
                "ownerUid" to uid,
                "nickname" to nicknameRaw.trim().take(24),
                "nicknameLower" to norm,
                "role" to "USER",
                "status" to "ACTIVE",
                "online" to true,
                "bio" to trimmedBio,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            accountRef.set(accountPayload).await()

            val credRef = accountRef.collection("credentials").document("auth")
            val credPayload = mapOf(
                "ownerUid" to uid,
                "passwordHash" to passwordHash,
                "salt" to salt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            credRef.set(credPayload).await()

            val created = accountRef.get().await()
                .toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Account(
                    id = norm,
                    ownerUid = uid,
                    nickname = nicknameRaw.trim(),
                    nicknameLower = norm,
                    role = "USER",
                    status = "ACTIVE",
                    online = true,
                    bio = trimmedBio
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun loginUser(nicknameRaw: String, password: String): Result<Account> {
        val norm = CryptoUtil.normalizeNickname(nicknameRaw)
        val path = "accounts/$norm"
        return try {
            val uid = requireUserId()
            require(CryptoUtil.isValidNickname(nicknameRaw)) {
                "Bitte gültigen Nicknamen (3–24 Zeichen) eingeben."
            }
            require(password.isNotEmpty()) { "Bitte Passwort eingeben." }

            val accountRef = db.collection("accounts").document(norm)
            val accountSnap = accountRef.get().await()
            if (!accountSnap.exists()) {
                return Result.failure(IllegalArgumentException("Nutzer \"$norm\" wurde nicht gefunden."))
            }

            val account = accountSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Kontodaten konnten nicht gelesen werden."))

            if (account.accountStatus == AccountStatus.SUSPENDED) {
                return Result.failure(IllegalStateException("Dieses Konto wurde vom Owner gesperrt."))
            }

            val credSnap = accountRef.collection("credentials").document("auth").get().await()
            val cred = credSnap.toObject(AccountCredential::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Anmeldedaten nicht zugänglich (anderer Cloud-Workspace)."))

            if (!CryptoUtil.verifyPassword(password, cred.salt, cred.passwordHash)) {
                return Result.failure(IllegalArgumentException("Falsches Passwort für \"$norm\"."))
            }

            if (account.ownerUid == uid || isGlobalAdmin()) {
                accountRef.update(
                    mapOf(
                        "online" to true,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val fresh = accountRef.get().await()
                .toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: account
            Result.success(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun setOnlineStatus(nicknameLower: String, online: Boolean): Result<Unit> {
        val path = "accounts/$nicknameLower"
        return try {
            requireUserId()
            db.collection("accounts").document(nicknameLower).update(
                mapOf(
                    "online" to online,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Owner Config Registration & Login ---

    suspend fun getOwnerConfig(): Result<OwnerConfig?> {
        val uid = auth.currentUser?.uid
        val path = "owner_configs/${uid ?: "unauthenticated"}"
        return try {
            val safeUid = requireUserId()
            val snap = db.collection("owner_configs").document(safeUid).get().await()
            val config = if (snap.exists()) {
                snap.toObject(OwnerConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(config)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun setupOrLoginOwner(usernameRaw: String, password: String): Result<OwnerConfig> {
        val uid = auth.currentUser?.uid
        val path = "owner_configs/${uid ?: "unauthenticated"}"
        return try {
            val safeUid = requireUserId()
            val norm = CryptoUtil.normalizeNickname(usernameRaw)
            require(CryptoUtil.isValidNickname(usernameRaw)) {
                "Owner-Nutzername muss 3–24 Zeichen lang sein (a-z, 0-9, _ oder -)."
            }
            require(password.length >= 4) { "Passwort muss mindestens 4 Zeichen lang sein." }

            val docRef = db.collection("owner_configs").document(safeUid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(OwnerConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return Result.failure(IllegalStateException("Owner-Konfiguration ungültig."))
                if (existing.usernameLower != norm) {
                    return Result.failure(IllegalArgumentException("Owner-Nutzername stimmt nicht überein (${existing.username})."))
                }
                if (!CryptoUtil.verifyPassword(password, existing.salt, existing.passwordHash)) {
                    return Result.failure(IllegalArgumentException("Falsches Owner-Passwort."))
                }
                Result.success(existing)
            } else {
                val salt = CryptoUtil.generateSalt()
                val hash = CryptoUtil.hashPassword(password, salt)
                val payload = mapOf(
                    "ownerUid" to safeUid,
                    "username" to usernameRaw.trim().take(24),
                    "usernameLower" to norm,
                    "passwordHash" to hash,
                    "salt" to salt,
                    "githubRepo" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await()
                    .toObject(OwnerConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: OwnerConfig(id = safeUid, ownerUid = safeUid, username = usernameRaw.trim(), usernameLower = norm)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnerGithubRepo(githubRepo: String): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "owner_configs/${uid ?: "unauthenticated"}"
        return try {
            val safeUid = requireUserId()
            db.collection("owner_configs").document(safeUid)
                .update(
                    mapOf(
                        "githubRepo" to githubRepo.trim().take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Conversations & Nickname Lookup ---

    suspend fun openOrCreateConversationByNickname(
        currentUser: Account,
        targetNicknameRaw: String
    ): Result<Conversation> {
        val targetNorm = CryptoUtil.normalizeNickname(targetNicknameRaw)
        val myNorm = currentUser.nicknameLower
        return try {
            val uid = requireUserId()
            require(CryptoUtil.isValidNickname(targetNicknameRaw)) {
                "Bitte einen gültigen Nicknamen eingeben (3–24 Zeichen)."
            }
            require(targetNorm != myNorm) {
                "Du kannst keinen Chat mit deinem eigenen Nicknamen starten."
            }

            val targetSnap = db.collection("accounts").document(targetNorm).get().await()
            if (!targetSnap.exists()) {
                return Result.failure(IllegalArgumentException("Nutzer \"$targetNorm\" existiert nicht."))
            }
            val targetAccount = targetSnap.toObject(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Zielkonto nicht lesbar."))

            val convId = CryptoUtil.conversationIdFor(myNorm, targetNorm)
            val convRef = db.collection("conversations").document(convId)
            val existingConv = convRef.get().await()
            val participantUids = listOf(uid, targetAccount.ownerUid).distinct().take(2)
            val participantNicknames = listOf(myNorm, targetNorm).sorted()

            if (!existingConv.exists()) {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "participantNicknames" to participantNicknames,
                    "participantUids" to participantUids,
                    "lastMessagePreview" to "Konversation gestartet",
                    "lastMessageType" to MessageType.TEXT.name,
                    "lastSenderNickname" to myNorm,
                    "messageCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                convRef.set(payload).await()
            } else {
                val currentUids = existingConv.toObject(Conversation::class.java)?.participantUids ?: emptyList()
                val mergedUids = (currentUids + participantUids).distinct().take(2)
                if (mergedUids != currentUids) {
                    convRef.update(
                        mapOf(
                            "participantUids" to mergedUids,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val conv = convRef.get().await()
                .toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: Conversation(
                    id = convId,
                    ownerUid = uid,
                    participantNicknames = participantNicknames,
                    participantUids = participantUids
                )
            Result.success(conv)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "conversations/${CryptoUtil.conversationIdFor(myNorm, targetNorm)}")
            Result.failure(e)
        }
    }

    fun observeUserConversations(myNicknameLower: String): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        val path = "conversations"
        emitAll(
            db.collection("conversations")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { myNicknameLower in it.participantNicknames }
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(conversationId: String, forOwnerView: Boolean = false): Flow<List<Message>> = flow {
        val uid = requireUserId()
        val admin = isGlobalAdmin()
        val path = "conversations/$conversationId/messages"
        val query = when {
            admin -> db.collection("conversations").document(conversationId).collection("messages")
            forOwnerView -> db.collection("conversations").document(conversationId).collection("messages")
                .whereEqualTo("ownerUid", uid)
            else -> db.collection("conversations").document(conversationId).collection("messages")
                .whereArrayContains("participantUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendTextMessage(
        conversation: Conversation,
        senderNickname: String,
        recipientNickname: String,
        text: String
    ): Result<Unit> {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 2000) { "Nachricht muss 1–2000 Zeichen lang sein." }
        return sendMessageInternal(
            conversation = conversation,
            senderNickname = senderNickname,
            recipientNickname = recipientNickname,
            type = MessageType.TEXT,
            preview = trimmed.take(120),
            extraFields = mapOf("textContent" to trimmed)
        )
    }

    suspend fun sendVoiceMessage(
        conversation: Conversation,
        senderNickname: String,
        recipientNickname: String,
        durationMs: Long,
        voiceBase64: String
    ): Result<Unit> {
        require(voiceBase64.length <= 450_000) { "Sprachnachricht überschreitet das Cloud-Limit." }
        val seconds = (durationMs / 1000L).coerceAtLeast(1L)
        return sendMessageInternal(
            conversation = conversation,
            senderNickname = senderNickname,
            recipientNickname = recipientNickname,
            type = MessageType.VOICE,
            preview = "Sprachnachricht (${seconds}s)",
            extraFields = mapOf(
                "voiceDurationMs" to durationMs.coerceIn(0L, 600_000L),
                "voiceBase64" to voiceBase64
            )
        )
    }

    suspend fun sendFileMessage(
        conversation: Conversation,
        senderNickname: String,
        recipientNickname: String,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        fileBase64: String
    ): Result<Unit> {
        require(fileBase64.length <= 450_000) { "Dateianhang ist zu groß für die direkte Übertragung (max. ~320 KB)." }
        val safeName = fileName.trim().take(150).ifEmpty { "Anhang" }
        val safeMime = mimeType.trim().take(120).ifEmpty { "application/octet-stream" }
        return sendMessageInternal(
            conversation = conversation,
            senderNickname = senderNickname,
            recipientNickname = recipientNickname,
            type = MessageType.FILE,
            preview = "Datei: $safeName",
            extraFields = mapOf(
                "fileName" to safeName,
                "fileMimeType" to safeMime,
                "fileSizeBytes" to fileSizeBytes.coerceIn(0L, 104_857_600L),
                "fileBase64" to fileBase64
            )
        )
    }

    private suspend fun sendMessageInternal(
        conversation: Conversation,
        senderNickname: String,
        recipientNickname: String,
        type: MessageType,
        preview: String,
        extraFields: Map<String, Any?>
    ): Result<Unit> {
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "conversations/${conversation.id}/messages/$msgId"
        return try {
            val uid = requireUserId()
            val participantUids = (conversation.participantUids + uid).distinct().take(2)
            val convRef = db.collection("conversations").document(conversation.id)
            val msgRef = convRef.collection("messages").document(msgId)

            val payload = mutableMapOf<String, Any?>(
                "ownerUid" to conversation.ownerUid.ifBlank { uid },
                "participantUids" to participantUids,
                "senderNickname" to senderNickname,
                "recipientNickname" to recipientNickname,
                "type" to type.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            payload.putAll(extraFields)
            @Suppress("UNCHECKED_CAST")
            val cleanPayload = payload.filterValues { it != null } as Map<String, Any>

            msgRef.set(cleanPayload).await()
            convRef.update(
                mapOf(
                    "participantUids" to participantUids,
                    "lastMessagePreview" to preview.take(200),
                    "lastMessageType" to type.name,
                    "lastSenderNickname" to senderNickname.take(24),
                    "messageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Real-Time Calls ---

    suspend fun startCall(
        callerNickname: String,
        recipientNickname: String,
        callType: CallType
    ): Result<CallSession> {
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "calls/$callId"
        return try {
            val uid = requireUserId()
            val targetSnap = db.collection("accounts").document(recipientNickname).get().await()
            val targetOwnerUid = targetSnap.getString("ownerUid") ?: uid
            val participantUids = listOf(uid, targetOwnerUid).distinct().take(2)

            val payload = mapOf(
                "ownerUid" to uid,
                "participantUids" to participantUids,
                "callerNickname" to callerNickname,
                "recipientNickname" to recipientNickname,
                "callType" to callType.name,
                "status" to CallStatus.RINGING.name,
                "durationSec" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val callRef = db.collection("calls").document(callId)
            callRef.set(payload).await()
            val session = callRef.get().await()
                .toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: CallSession(
                    id = callId,
                    ownerUid = uid,
                    participantUids = participantUids,
                    callerNickname = callerNickname,
                    recipientNickname = recipientNickname,
                    callType = callType.name,
                    status = CallStatus.RINGING.name
                )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        call: CallSession,
        newStatus: CallStatus,
        durationSec: Long = 0L
    ): Result<Unit> {
        val path = "calls/${call.id}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "durationSec" to durationSec.coerceIn(0L, 86400L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStatus in listOf(CallStatus.ENDED, CallStatus.MISSED, CallStatus.DECLINED)) {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            db.collection("calls").document(call.id).update(updates).await()

            if (newStatus in listOf(CallStatus.ENDED, CallStatus.MISSED, CallStatus.DECLINED)) {
                val convId = CryptoUtil.conversationIdFor(call.callerNickname, call.recipientNickname)
                val convSnap = db.collection("conversations").document(convId).get().await()
                val conv = convSnap.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (conv != null) {
                    val label = when (newStatus) {
                        CallStatus.ENDED -> "${if (call.callTypeEnum == CallType.VIDEO) "Videoanruf" else "Sprachanruf"} • ${durationSec}s"
                        CallStatus.DECLINED -> "Anruf abgelehnt"
                        CallStatus.MISSED -> "Verpasster Anruf"
                        else -> "Anruf"
                    }
                    sendMessageInternal(
                        conversation = conv,
                        senderNickname = call.callerNickname,
                        recipientNickname = call.recipientNickname,
                        type = MessageType.CALL_LOG,
                        preview = label,
                        extraFields = mapOf(
                            "textContent" to label,
                            "callDurationSec" to durationSec.coerceIn(0L, 86400L),
                            "callStatus" to newStatus.name
                        )
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeCall(callId: String): Flow<CallSession?> = flow {
        val uid = requireUserId()
        val path = "calls/$callId"
        emitAll(
            db.collection("calls").document(callId)
                .snapshots()
                .map { snap -> snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeIncomingCalls(myNicknameLower: String): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        val path = "calls"
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.recipientNickname == myNicknameLower && it.callStatusEnum == CallStatus.RINGING }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    // --- Owner Console Oversight & User Management ---

    fun observeOwnerAccounts(): Flow<List<Account>> = flow {
        val uid = requireUserId()
        val admin = isGlobalAdmin()
        val path = "accounts"
        val query = if (admin) db.collection("accounts") else db.collection("accounts").whereEqualTo("ownerUid", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeOwnerConversations(): Flow<List<Conversation>> = flow {
        val uid = requireUserId()
        val admin = isGlobalAdmin()
        val path = "conversations"
        val query = if (admin) db.collection("conversations") else db.collection("conversations").whereEqualTo("ownerUid", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeOwnerCalls(): Flow<List<CallSession>> = flow {
        val uid = requireUserId()
        val admin = isGlobalAdmin()
        val path = "calls"
        val query = if (admin) db.collection("calls") else db.collection("calls").whereEqualTo("ownerUid", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun fetchAllMessagesForOwnerAnalytics(conversations: List<Conversation>): List<Message> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val admin = isGlobalAdmin()
        val all = mutableListOf<Message>()
        for (conv in conversations.take(30)) {
            try {
                val query = if (admin) {
                    db.collection("conversations").document(conv.id).collection("messages")
                } else {
                    db.collection("conversations").document(conv.id).collection("messages")
                        .whereEqualTo("ownerUid", uid)
                }
                val snap = query.get().await()
                all.addAll(snap.toObjects(Message::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "conversations/${conv.id}/messages")
            }
        }
        return all
    }

    suspend fun setAccountStatus(accountId: String, newStatus: AccountStatus): Result<Unit> {
        val path = "accounts/$accountId"
        return try {
            requireUserId()
            db.collection("accounts").document(accountId).update(
                mapOf(
                    "status" to newStatus.name,
                    "online" to (newStatus == AccountStatus.ACTIVE),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteUserAccount(accountId: String): Result<Unit> {
        val path = "accounts/$accountId"
        return try {
            requireUserId()
            val ref = db.collection("accounts").document(accountId)
            try {
                ref.collection("credentials").document("auth").delete().await()
            } catch (_: Exception) {
                // Credential doc may not exist
            }
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
