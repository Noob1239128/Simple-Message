package com.example.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

object FileAttachmentHelper {
    data class PreparedAttachment(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val base64: String
    )

    fun readAndPrepareAttachment(context: Context, uri: Uri): Result<PreparedAttachment> {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var fileName = "datei_${System.currentTimeMillis()}"
            var reportedSize = 0L

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) reportedSize = cursor.getLong(sizeIdx)
                }
            }

            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Datei konnte nicht gelesen werden."))

            val finalBytes = if (mimeType.startsWith("image/") && rawBytes.size > 260_000) {
                compressImageToFit(rawBytes) ?: rawBytes
            } else {
                rawBytes
            }

            if (finalBytes.size > 325_000) {
                return Result.failure(
                    IllegalArgumentException(
                        "Datei \"$fileName\" ist mit ${formatFileSize(finalBytes.size.toLong())} zu groß für den Cloud-Versand (max. 320 KB)."
                    )
                )
            }

            val encoded = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
            Result.success(
                PreparedAttachment(
                    fileName = fileName,
                    mimeType = if (mimeType.startsWith("image/") && finalBytes !== rawBytes) "image/jpeg" else mimeType,
                    sizeBytes = (if (reportedSize > 0L && finalBytes === rawBytes) reportedSize else finalBytes.size.toLong()),
                    base64 = encoded
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compressImageToFit(bytes: ByteArray): ByteArray? {
        return try {
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxDim = 960
            val scale = minOf(1f, maxDim.toFloat() / maxOf(original.width, original.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else original

            var quality = 78
            var result: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                result = out.toByteArray()
                quality -= 14
            } while (result.size > 260_000 && quality >= 25)
            result
        } catch (_: Exception) {
            null
        }
    }

    fun decodeAttachmentToUri(context: Context, messageId: String, fileName: String, base64: String): Uri? {
        return try {
            val dir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val file = File(dir, "${messageId}_$sanitizedName")
            if (!file.exists()) {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                file.writeBytes(bytes)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun openAttachment(context: Context, messageId: String, fileName: String, mimeType: String, base64: String): Boolean {
        val uri = decodeAttachmentToUri(context, messageId, fileName, base64) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, fileName))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format("%.1f MB", bytes / (1024f * 1024f))
        }
    }
}
