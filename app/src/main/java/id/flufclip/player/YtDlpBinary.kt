package id.flufclip.player

import android.content.Context
import java.io.File

/**
 * Binary yt-dlp bawaan library terlalu lama untuk `n` challenge YouTube saat ini.
 * Salin binary terbaru dari assets setelah YoutubeDL.init() mengekstrak paket bawaannya.
 */
internal object YtDlpBinary {
    @Volatile private var copied = false

    fun ensure(context: Context) {
        if (copied) return
        copied = true
        try {
            val dest = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
            dest.parentFile?.mkdirs()
            context.assets.open("yt-dlp-latest").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.setExecutable(true, false)
        } catch (_: Exception) {
        }
    }
}
