package id.flufclip.player

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/** Owns only the new output reserved for this download, so failure cleanup cannot delete older files. */
internal class DownloadDestination private constructor(
    private val context: Context,
    val uri: Uri,
    val name: String,
    val mime: String,
    private val legacyFile: File? = null,
) {
    fun open(): OutputStream = if (legacyFile != null) legacyFile.outputStream()
        else context.contentResolver.openOutputStream(uri, "w") ?: error("Cannot open downloaded file")

    fun openWriteFd(): ParcelFileDescriptor? = if (legacyFile != null) null
        else runCatching { context.contentResolver.openFileDescriptor(uri, "w") }.getOrNull()

    fun legacyFilePath(): String? = legacyFile?.absolutePath

    fun verify(bytes: Long) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            check(descriptor.statSize < 0 || descriptor.statSize == bytes) { "Saved file size does not match the download" }
        } ?: error("Cannot read saved file")
    }

    fun publish() {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            val updated = runCatching { context.contentResolver.update(uri, values, null, null) }.getOrDefault(0)
            check(updated >= 0) { "Cannot publish downloaded file" }
        } else {
            MediaScannerConnection.scanFile(context, arrayOf(checkNotNull(legacyFile).absolutePath), arrayOf(mime), null)
        }
    }

    fun abort() {
        if (legacyFile != null) check(legacyFile.delete() || !legacyFile.exists())
        else context.contentResolver.delete(uri, null, null)
    }

    companion object {
        fun create(context: Context, title: String, audioOnly: Boolean, ext: String = if (audioOnly) "mp3" else "mp4"): DownloadDestination {
            val defaultName = downloadFileName(title, audioOnly)
            val name = if (defaultName.endsWith(".$ext", ignoreCase = true)) defaultName else "${defaultName.substringBeforeLast('.')}.$ext"
            val mime = when (ext.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "opus", "ogg" -> "audio/ogg"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> if (audioOnly) "audio/$ext" else "video/$ext"
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBDIRECTORY/")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values
                ) ?: error("Cannot create a file in the downloads folder")
                return DownloadDestination(context, uri, name, mime)
            }
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_SUBDIRECTORY)
            val file = reserveDownloadFile(folder, name)
            return DownloadDestination(context, Uri.fromFile(file), file.name, mime, file)
        }
    }
}
