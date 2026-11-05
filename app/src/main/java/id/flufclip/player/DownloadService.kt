package id.flufclip.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.text.format.Formatter
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

data class DownloadState(val running: Boolean = false, val message: String = "", val progress: Int? = null, val completed: SavedDownload? = null, val video: DownloadVideo? = null, val phase: DownloadPhase = DownloadPhase.PREPARING, val etaSeconds: Long? = null)
object DownloadStatus { val state = MutableStateFlow(DownloadState()) }

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cancelled = false
    @Volatile private var activeId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            cancelled = true
            activeId?.let { YoutubeDL.destroyProcessById(it) }
            return START_NOT_STICKY
        }
        if (DownloadStatus.state.value.running) return START_NOT_STICKY
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf(startId) }
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "video" }
        val video = runCatching { downloadVideo(org.json.JSONObject(intent.getStringExtra("video").orEmpty())) }.getOrDefault(DownloadVideo(sourceUrl = url, title = title))
        val height = intent.getIntExtra("height", 0)
        val audioOnly = intent.getBooleanExtra("audio", false)
        cancelled = false
        DownloadStatus.state.value = DownloadState(true, "Preparing download…", video = video)
        startForeground(NOTIFICATION, notification("Preparing download…", null))
        scope.launch {
            val temp = File(cacheDir, "download-${UUID.randomUUID()}")
            var cookieFile: File? = null
            var destination: DownloadDestination? = null
            var savedSuccessfully = false
            try {
                check(temp.mkdirs()) { "Cannot create temporary folder" }
                YoutubeDL.init(applicationContext)
                YtDlpBinary.ensure(applicationContext)
                FFmpeg.init(applicationContext)
                if (cancelled) throw CancellationException()
                val id = UUID.randomUUID().toString()
                activeId = id
                if (YouTubeAuth.isLoggedIn) {
                    cookieFile = YouTubeAuth.writeNetscapeCookiesFile(cacheDir)
                }
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--no-overwrites")
                    if (cookieFile != null && cookieFile.exists()) {
                        addOption("--cookies", cookieFile.absolutePath)
                    }
                    addOption("-o", File(temp, "result.%(ext)s").absolutePath)
                    if (audioOnly) {
                        addOption("-f", "bestaudio/best")
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                    } else {
                        val cap = if (height > 0) "[height<=$height]" else ""
                        addOption("-f", "bestvideo[ext=mp4][vcodec^=avc1]$cap+bestaudio[ext=m4a]/best[ext=mp4][vcodec^=avc1]$cap")
                        addOption("--merge-output-format", "mp4")
                    }
                }
                if (cancelled) throw CancellationException()
                YoutubeDL.execute(request, id) { _, eta, line ->
                    if (cancelled) YoutubeDL.destroyProcessById(id)
                    else downloadSignal(line, eta)?.let { signal -> update(signal.phase.label, signal.percent, signal.phase, signal.etaSeconds) }
                }
                if (cancelled) throw CancellationException()
                update("Saving file…", 0, DownloadPhase.COPYING)
                val extension = if (audioOnly) "mp3" else "mp4"
                val result = File(temp, "result.$extension")
                check(result.isFile && result.length() > 0) { "Downloaded file not found" }
                val output = DownloadDestination.create(this@DownloadService, title, audioOnly)
                destination = output
                val copied = output.open().use { out ->
                    result.inputStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        var count = 0L
                        while (true) {
                            if (cancelled) throw CancellationException()
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            count += read
                            update("Saving file…", (count * 100 / result.length()).toInt().coerceIn(0, 100), DownloadPhase.COPYING)
                        }
                        out.flush()
                        count
                    }
                }
                check(copied == result.length()) { "The file was not completely copied" }
                update("Verifying file…", null, DownloadPhase.VERIFYING)
                output.verify(copied)
                if (cancelled) throw CancellationException()
                output.publish()
                val cachedVideo = cacheDownloadArtwork(this@DownloadService, video)
                if (cancelled) throw CancellationException()
                val saved = SavedDownload(output.uri.toString(), SavedDownloads.displayName(this@DownloadService, output.uri, output.name), if (audioOnly) "audio/mpeg" else "video/mp4", copied, cachedVideo)
                SavedDownloads.add(this@DownloadService, saved)
                savedSuccessfully = true
                DownloadStatus.state.value = DownloadState(message = "Download complete · ${Formatter.formatFileSize(this@DownloadService, copied)}", completed = saved, video = video, phase = DownloadPhase.COMPLETED)
                Log.i("FlufPlayDownload", "Saved $copied bytes; audioOnly=$audioOnly")
            } catch (e: Exception) {
                Log.e("FlufPlayDownload", "Download or destination copy failed", e)
                DownloadStatus.state.value = DownloadState(message = if (cancelled || e is CancellationException) "Download canceled." else "Download failed: ${e.message ?: e.javaClass.simpleName}", video = video, phase = if (cancelled || e is CancellationException) DownloadPhase.CANCELLED else DownloadPhase.FAILED)
            } finally {
                if (!savedSuccessfully) runCatching { destination?.abort() }.onFailure {
                    Log.w("FlufPlayDownload", "Could not remove incomplete output", it)
                }
                activeId = null
                cookieFile?.delete()
                temp.deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                val summary = DownloadStatus.state.value.message
                val openIntent = SavedDownloads.openIntent(this@DownloadService, DownloadStatus.state.value.completed)
                val open = PendingIntent.getActivity(this@DownloadService, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION + 1,
                        NotificationCompat.Builder(this@DownloadService, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(getString(R.string.download_notification_title)).setContentText(summary).setStyle(NotificationCompat.BigTextStyle().bigText(summary)).setContentIntent(open).setAutoCancel(true).build())
                } catch (_: SecurityException) { }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
    private var lastUpdate = 0L
    private fun update(message: String, progress: Int?, phase: DownloadPhase, eta: Long? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val before = DownloadStatus.state.value
        if (before.phase == phase && now - lastUpdate < 300 && progress != 100) return
        if (before.phase == phase && before.progress == progress && before.etaSeconds == eta && before.message == message) return
        lastUpdate = now
        DownloadStatus.state.value = before.copy(running = true, message = message, progress = progress, phase = phase, etaSeconds = eta)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message, progress))
    }
    private fun notification(message: String, progress: Int?): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction("cancel"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, SavedDownloads.openIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(getString(R.string.download_notification_title)).setContentText(message).setContentIntent(open).setOngoing(true).setProgress(100, progress ?: 0, progress == null).addAction(0, getString(R.string.cancel), cancel).build()
    }
    override fun onDestroy() { cancelled = true; activeId?.let { YoutubeDL.destroyProcessById(it) }; scope.cancel(); super.onDestroy() }
}
