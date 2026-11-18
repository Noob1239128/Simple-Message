package com.example.media

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File

class AudioVoiceManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentRecordFile: File? = null
    private var recordStartMs: Long = 0L

    private var player: MediaPlayer? = null
    private var playingMessageId: String? = null

    fun startRecording(): Boolean {
        return try {
            stopRecordingAndDiscard()
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(24000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            currentRecordFile = file
            recordStartMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Aufnahme konnte nicht gestartet werden", e)
            stopRecordingAndDiscard()
            false
        }
    }

    fun getAmplitudeNormalized(): Float {
        return try {
            val amp = recorder?.maxAmplitude ?: 0
            (amp / 32767f).coerceIn(0.08f, 1f)
        } catch (_: Exception) {
            0.1f
        }
    }

    data class RecordedVoice(val base64: String, val durationMs: Long)

    fun stopRecordingAndEncode(): RecordedVoice? {
        val file = currentRecordFile ?: return null
        val duration = (System.currentTimeMillis() - recordStartMs).coerceAtLeast(500L)
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentRecordFile = null
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            file.delete()
            if (bytes.size > 330_000) {
                Log.w("AudioVoiceManager", "Sprachnachricht zu groß (${bytes.size} Bytes)")
                return null
            }
            RecordedVoice(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                durationMs = duration
            )
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Aufnahme konnte nicht beendet werden", e)
            stopRecordingAndDiscard()
            null
        }
    }

    fun stopRecordingAndDiscard() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        currentRecordFile?.delete()
        currentRecordFile = null
    }

    fun togglePlayback(
        messageId: String,
        voiceBase64: String,
        onStateChanged: (playingId: String?) -> Unit
    ) {
        if (playingMessageId == messageId && player?.isPlaying == true) {
            stopPlayback()
            onStateChanged(null)
            return
        }
        stopPlayback()
        if (voiceBase64.isBlank()) {
            onStateChanged(null)
            return
        }
        try {
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val outFile = File(dir, "play_$messageId.m4a")
            if (!outFile.exists()) {
                val bytes = Base64.decode(voiceBase64, Base64.DEFAULT)
                outFile.writeBytes(bytes)
            }
            val mp = MediaPlayer().apply {
                setDataSource(outFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    playingMessageId = null
                    onStateChanged(null)
                }
                start()
            }
            player = mp
            playingMessageId = messageId
            onStateChanged(messageId)
        } catch (e: Exception) {
            Log.e("AudioVoiceManager", "Wiedergabe fehlgeschlagen", e)
            stopPlayback()
            onStateChanged(null)
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playingMessageId = null
    }

    fun setCallAudioMode(speakerOn: Boolean, muted: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = speakerOn
            am.isMicrophoneMute = muted
        } catch (e: Exception) {
            Log.w("AudioVoiceManager", "AudioManager-Konfiguration nicht verfügbar", e)
        }
    }

    fun resetCallAudioMode() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.isMicrophoneMute = false
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    fun release() {
        stopRecordingAndDiscard()
        stopPlayback()
        resetCallAudioMode()
    }
}
