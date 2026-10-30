package id.flufclip.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID

private const val CHANNEL = "flufplay_downloads"
private const val NOTIFICATION = 71

data class DownloadState(val running: Boolean = false, val message: String = "", val progress: Int? = null)
object DownloadStatus { val state = MutableStateFlow(DownloadState()) }

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cancelled = false
    @Volatile private var activeId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Unduhan", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            cancelled = true
            activeId?.let { YoutubeDL.destroyProcessById(it) }
            return START_NOT_STICKY
        }
        if (DownloadStatus.state.value.running) return START_NOT_STICKY
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf(startId) }
        val destination = intent.getStringExtra("destination")?.let(Uri::parse) ?: return START_NOT_STICKY.also { stopSelf(startId) }
        val height = intent.getIntExtra("height", 0)
        val audioOnly = intent.getBooleanExtra("audio", false)
        cancelled = false
        DownloadStatus.state.value = DownloadState(true, "Menyiapkan unduhan…")
        startForeground(NOTIFICATION, notification("Menyiapkan unduhan…", null))
        scope.launch {
            val temp = File(cacheDir, "download-${UUID.randomUUID()}")
            try {
                check(temp.mkdirs()) { "Tidak bisa membuat folder sementara" }
                YoutubeDL.init(applicationContext)
                FFmpeg.init(applicationContext)
                if (cancelled) throw CancellationException()
                val id = UUID.randomUUID().toString()
                activeId = id
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--no-overwrites")
                    addOption("-o", File(temp, "result.%(ext)s").absolutePath)
                    if (audioOnly) {
                        addOption("-f", "bestaudio/best")
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    } else {
                        val cap = if (height > 0) "[height<=$height]" else ""
                        addOption("-f", "bestvideo[ext=mp4]$cap+bestaudio[ext=m4a]/best[ext=mp4]$cap")
                        addOption("--merge-output-format", "mp4")
                    }
                }
                if (cancelled) throw CancellationException()
                YoutubeDL.execute(request, id) { progress, _, _ ->
                    if (cancelled) YoutubeDL.destroyProcessById(id) else update("Mengunduh atau memproses…", progress.toInt().coerceIn(0, 100))
                }
                if (cancelled) throw CancellationException()
                update("Menyimpan file…", null)
                val extension = if (audioOnly) "mp3" else "mp4"
                val result = File(temp, "result.$extension")
                check(result.isFile && result.length() > 0) { "File hasil unduhan tidak ditemukan" }
                contentResolver.openOutputStream(destination, "w")?.use { out -> result.inputStream().use { it.copyTo(out) } } ?: error("Tidak bisa membuka lokasi penyimpanan")
                if (cancelled) throw CancellationException()
                DownloadStatus.state.value = DownloadState(message = "Unduhan selesai dan tersimpan.")
            } catch (e: Exception) {
                DownloadStatus.state.value = DownloadState(message = if (cancelled || e is CancellationException) "Unduhan dibatalkan." else "Unduhan gagal: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                activeId = null
                temp.deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                val summary = DownloadStatus.state.value.message
                val open = PendingIntent.getActivity(this@DownloadService, 0, Intent(this@DownloadService, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION + 1,
                        NotificationCompat.Builder(this@DownloadService, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("FlufPlay · Unduhan").setContentText(summary).setStyle(NotificationCompat.BigTextStyle().bigText(summary)).setContentIntent(open).setAutoCancel(true).build())
                } catch (_: SecurityException) { }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
    private fun update(message: String, progress: Int?) {
        DownloadStatus.state.value = DownloadState(true, message, progress)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message, progress))
    }
    private fun notification(message: String, progress: Int?): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction("cancel"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("FlufPlay · Unduhan").setContentText(message).setContentIntent(open).setOngoing(true).setProgress(100, progress ?: 0, progress == null).addAction(0, "Batalkan", cancel).build()
    }
    override fun onDestroy() { cancelled = true; activeId?.let { YoutubeDL.destroyProcessById(it) }; scope.cancel(); super.onDestroy() }
}
