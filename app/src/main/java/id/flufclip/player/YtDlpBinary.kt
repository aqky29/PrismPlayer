package id.flufclip.player

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Binary yt-dlp bawaan library terlalu lama untuk `n` challenge YouTube saat ini.
 * Salin binary terbaru dari assets setelah YoutubeDL.init() mengekstrak paket bawaannya.
 */
internal object YtDlpBinary {
    @Volatile private var copied = false

    @Synchronized
    fun ensure(context: Context) {
        if (copied) return
        val dir = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
        val dest = File(dir, "yt-dlp")
        val temp = File(dir, "yt-dlp.tmp")
        try {
            dir.mkdirs()
            context.assets.open("yt-dlp-latest").use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            temp.setExecutable(true, false)
            if (dest.exists()) dest.delete()
            if (temp.renameTo(dest)) {
                dest.setExecutable(true, false)
                copied = true
            } else {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
                dest.setExecutable(true, false)
                copied = true
            }
        } catch (e: Exception) {
            temp.delete()
            Log.e("YtDlpBinary", "Failed to extract yt-dlp binary", e)
        }
    }
}
