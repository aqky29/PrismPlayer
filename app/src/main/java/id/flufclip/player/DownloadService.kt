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

data class DownloadState(
    val running: Boolean = false,
    val message: String = "",
    val progress: Int? = null,
    val completed: SavedDownload? = null,
    val video: DownloadVideo? = null,
    val phase: DownloadPhase = DownloadPhase.PREPARING,
    val etaSeconds: Long? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val totalEstimated: Boolean = false,
)
object DownloadStatus { val state = MutableStateFlow(DownloadState()) }

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var cancelled = false
    @Volatile private var activeId: String? = null
    @Volatile private var activeCancelToken: Long = 0L

    private fun cancelActiveWork() {
        cancelled = true
        activeId?.let { YoutubeDL.destroyProcessById(it) }
        activeCancelToken.takeIf { it != 0L }?.let { NativeDownloader.nativeCancel(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        cleanOrphanedTempDirs()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW))
    }

    private fun cleanOrphanedTempDirs() {
        runCatching {
            cacheDir.listFiles { file -> file.isDirectory && file.name.startsWith("download-") }
                ?.forEach { dir -> dir.deleteRecursively() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "cancel") {
            cancelActiveWork()
            return START_NOT_STICKY
        }
        if (DownloadStatus.state.value.running) {
            val current = DownloadStatus.state.value
            startForeground(NOTIFICATION, notification(current.message.ifBlank { "Downloading…" }, current.progress, current.video?.title))
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra("url")
        if (url.isNullOrBlank()) {
            startForeground(NOTIFICATION, notification("Download failed", null))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "video" }
        val video = runCatching { downloadVideo(org.json.JSONObject(intent.getStringExtra("video").orEmpty())) }.getOrDefault(DownloadVideo(sourceUrl = url, title = title))
        val height = intent.getIntExtra("height", 0)
        val audioOnly = intent.getBooleanExtra("audio", false)
        cancelled = false
        DownloadStatus.state.value = DownloadState(true, "Preparing download…", video = video)
        startForeground(NOTIFICATION, notification("Preparing download…", null, video.title))
        scope.launch {
            val temp = File(cacheDir, "download-${UUID.randomUUID()}")
            var cookieFile: File? = null
            var destination: DownloadDestination? = null
            var savedSuccessfully = false
            var finalCompleted: SavedDownload? = null
            var finalMessage = ""
            var finalPhase = DownloadPhase.FAILED
            try {
                check(temp.mkdirs()) { "Cannot create temporary folder" }
                if (cancelled) throw CancellationException()
                val fastResult = tryRustDirectDownload(url, title, height, audioOnly, temp)
                val (resolvedOutput, copied) = if (fastResult != null) {
                    destination = fastResult.first
                    fastResult
                } else {
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
                        addOption("--newline")
                        addOption("--color", "never")
                        addOption(
                            "--progress-template",
                            "download:[download] %(progress._default_template)s |bytes=%(progress.downloaded_bytes|0)s/%(progress.total_bytes|0)s/%(progress.total_bytes_estimate|0)s|"
                        )
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
                            addOption("-f", "bestvideo$cap+bestaudio/best$cap/best")
                            addOption("--merge-output-format", "mp4/mkv")
                        }
                    }
                    if (cancelled) throw CancellationException()
                    var transferPart = 0
                    YoutubeDL.execute(request, id) { _, eta, line ->
                        if (cancelled) YoutubeDL.destroyProcessById(id)
                        else downloadSignal(line, eta)?.let { signal ->
                            if (signal.newDestination) {
                                transferPart++
                                val destLabel = if (!audioOnly && transferPart >= 2) "Downloading audio…" else "Downloading…"
                                update(destLabel, null, DownloadPhase.TRANSFERRING, force = true)
                            } else {
                                val message = when (signal.phase) {
                                    DownloadPhase.TRANSFERRING -> {
                                        val phaseLabel = if (!audioOnly && transferPart >= 2) "Downloading audio" else signal.phase.label
                                        formatTransferProgressMessage(
                                            label = phaseLabel,
                                            percent = signal.percent,
                                            downloadedBytes = signal.downloadedBytes,
                                            totalBytes = signal.totalBytes,
                                            totalEstimated = signal.totalEstimated,
                                        ) { Formatter.formatFileSize(this@DownloadService, it) }
                                    }
                                    else -> signal.phase.label
                                }
                                update(
                                    message = message,
                                    progress = signal.percent,
                                    phase = signal.phase,
                                    eta = signal.etaSeconds,
                                    downloadedBytes = signal.downloadedBytes,
                                    totalBytes = signal.totalBytes,
                                    totalEstimated = signal.totalEstimated,
                                )
                            }
                        }
                    }
                    if (cancelled) throw CancellationException()
                    val result = temp.listFiles()?.firstOrNull { file ->
                        file.isFile && file.name.startsWith("result.") && file.length() > 0 &&
                            !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
                    } ?: error("Downloaded file not found")
                    val ext = result.extension.lowercase()
                    val output = DownloadDestination.create(this@DownloadService, title, audioOnly, ext)
                    destination = output
                    val copiedBytes = copyFileToDestination(result, output)
                    output to copiedBytes
                }
                val output = resolvedOutput
                update("Verifying file…", null, DownloadPhase.VERIFYING, force = true)
                output.verify(copied)
                if (cancelled) throw CancellationException()
                output.publish()
                savedSuccessfully = true
                val cachedVideo = runCatching { cacheDownloadArtwork(this@DownloadService, video) }.getOrDefault(video)
                val saved = SavedDownload(output.uri.toString(), SavedDownloads.displayName(this@DownloadService, output.uri, output.name), output.mime, copied, cachedVideo)
                runCatching { SavedDownloads.add(this@DownloadService, saved) }
                finalCompleted = saved
                finalMessage = "Download complete · ${Formatter.formatFileSize(this@DownloadService, copied)}"
                finalPhase = DownloadPhase.COMPLETED
                DownloadStatus.state.value = DownloadState(running = true, message = finalMessage, completed = saved, video = video, phase = finalPhase)
                Log.i("FlufPlayDownload", "Saved $copied bytes; audioOnly=$audioOnly")
            } catch (e: Exception) {
                Log.e("FlufPlayDownload", "Download or destination copy failed", e)
                finalCompleted = null
                finalMessage = if (cancelled || e is CancellationException) "Download canceled." else "Download failed: ${e.message ?: e.javaClass.simpleName}"
                finalPhase = if (cancelled || e is CancellationException) DownloadPhase.CANCELLED else DownloadPhase.FAILED
                DownloadStatus.state.value = DownloadState(running = true, message = finalMessage, video = video, phase = finalPhase)
            } finally {
                if (!savedSuccessfully) runCatching { destination?.abort() }.onFailure {
                    Log.w("FlufPlayDownload", "Could not remove incomplete output", it)
                }
                activeId = null
                cookieFile?.delete()
                temp.deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                val summary = finalMessage
                val openIntent = SavedDownloads.openIntent(this@DownloadService, finalCompleted)
                val open = PendingIntent.getActivity(this@DownloadService, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION + 1,
                        NotificationCompat.Builder(this@DownloadService, CHANNEL)
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle(video.title.ifBlank { getString(R.string.download_notification_title) })
                            .setContentText(summary)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build())
                } catch (_: SecurityException) { }
                DownloadStatus.state.value = DownloadState(running = false, message = finalMessage, completed = finalCompleted, video = video, phase = finalPhase)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private var lastUpdate = 0L
    private fun update(
        message: String,
        progress: Int?,
        phase: DownloadPhase,
        eta: Long? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        totalEstimated: Boolean = false,
        force: Boolean = false,
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        val before = DownloadStatus.state.value
        if (!force && before.phase == phase && now - lastUpdate < 400 && progress != 100) return
        if (before.phase == phase && before.progress == progress && before.etaSeconds == eta &&
            before.downloadedBytes == downloadedBytes && before.totalBytes == totalBytes &&
            before.totalEstimated == totalEstimated && before.message == message
        ) return
        lastUpdate = now
        DownloadStatus.state.value = before.copy(
            running = true,
            message = message,
            progress = progress,
            phase = phase,
            etaSeconds = eta,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            totalEstimated = totalEstimated,
        )
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message, progress, before.video?.title))
        } catch (_: SecurityException) { }
    }
    private fun notification(message: String, progress: Int?, videoTitle: String? = null): Notification {
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction("cancel"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, SavedDownloads.openIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(videoTitle?.takeIf { it.isNotBlank() } ?: getString(R.string.download_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, progress ?: 0, progress == null)
            .addAction(0, getString(R.string.cancel), cancel)
            .build()
    }
    private suspend fun copyFileToDestination(source: File, output: DownloadDestination): Long {
        val totalLength = source.length()
        val initialCopyMessage = formatTransferProgressMessage("Saving file", 0, 0L, totalLength, false) {
            Formatter.formatFileSize(this@DownloadService, it)
        }
        update(initialCopyMessage, 0, DownloadPhase.COPYING, downloadedBytes = 0L, totalBytes = totalLength, force = true)
        val copied = output.open().use { out ->
            source.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                var count = 0L
                while (true) {
                    if (cancelled) throw CancellationException()
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    count += read
                    val pct = if (totalLength > 0L) (count * 100 / totalLength).toInt().coerceIn(0, 100) else null
                    val copyMessage = formatTransferProgressMessage("Saving file", pct, count, totalLength, false) {
                        Formatter.formatFileSize(this@DownloadService, it)
                    }
                    update(copyMessage, pct, DownloadPhase.COPYING, downloadedBytes = count, totalBytes = totalLength)
                }
                out.flush()
                count
            }
        }
        check(copied == source.length()) { "The file was not completely copied" }
        return copied
    }

    private suspend fun tryRustDirectDownload(
        url: String,
        title: String,
        height: Int,
        audioOnly: Boolean,
        temp: File,
    ): Pair<DownloadDestination, Long>? {
        if (audioOnly || !NativeDownloader.isAvailable) return null
        val info = runCatching { VideoRepository.resolve(url, applicationContext) }.getOrNull() ?: return null
        if (cancelled) throw CancellationException()
        val plan = selectRustDirectDownloadPlan(info, height, audioOnly) ?: return null

        val token = NativeDownloader.nativeCreateCancelToken()
        activeCancelToken = token
        if (cancelled) {
            NativeDownloader.nativeCancel(token)
            activeCancelToken = 0L
            NativeDownloader.nativeDestroyCancelToken(token)
            throw CancellationException()
        }

        var partialOutput: DownloadDestination? = null
        try {
            fun downloadStream(streamUrl: String, targetFile: File, label: String) {
                val headers = NativeDownloader.flattenHeaders(playbackRequestHeaders(streamUrl, YouTubeAuth.getCookies()))
                update("$label…", null, DownloadPhase.TRANSFERRING, force = true)
                NativeDownloader.nativeDownloadFile(
                    url = streamUrl,
                    headerPairs = headers,
                    destPath = targetFile.absolutePath,
                    cancelToken = token,
                    listener = object : DownloadProgressCallback {
                        override fun onProgress(downloadedBytes: Long, totalBytes: Long, etaSeconds: Long) {
                            val total = totalBytes.takeIf { it > 0L }
                            val pct = if (total != null) ((downloadedBytes * 100L) / total).toInt().coerceIn(0, 100) else null
                            val eta = etaSeconds.takeIf { it >= 0L }
                            val msg = formatTransferProgressMessage(
                                label = label,
                                percent = pct,
                                downloadedBytes = downloadedBytes,
                                totalBytes = total,
                                totalEstimated = false,
                            ) { Formatter.formatFileSize(this@DownloadService, it) }
                            update(
                                message = msg,
                                progress = pct,
                                phase = DownloadPhase.TRANSFERRING,
                                eta = eta,
                                downloadedBytes = downloadedBytes,
                                totalBytes = total,
                                totalEstimated = false,
                            )
                        }
                    },
                )
            }

            return when (plan) {
                is RustDownloadPlan.CombinedMp4 -> {
                    val rawVideo = File(temp, "rust_video.mp4")
                    downloadStream(plan.videoUrl, rawVideo, "Downloading")
                    if (cancelled) throw CancellationException()
                    val output = DownloadDestination.create(this@DownloadService, title, false, "mp4")
                    partialOutput = output
                    val copied = copyFileToDestination(rawVideo, output)
                    partialOutput = null
                    output to copied
                }
                is RustDownloadPlan.MuxMp4 -> {
                    val rawVideo = File(temp, "rust_video.mp4")
                    val rawAudio = File(temp, "rust_audio.m4a")
                    downloadStream(plan.videoUrl, rawVideo, "Downloading")
                    if (cancelled) throw CancellationException()
                    downloadStream(plan.audioUrl, rawAudio, "Downloading audio")
                    if (cancelled) throw CancellationException()

                    update(DownloadPhase.PROCESSING.label, null, DownloadPhase.PROCESSING, force = true)
                    val output = DownloadDestination.create(this@DownloadService, title, false, "mp4")
                    partialOutput = output
                    val legacyPath = output.legacyFilePath()
                    val finalBytes = if (legacyPath != null) {
                        NativeDownloader.nativeMuxMp4(rawVideo.absolutePath, rawAudio.absolutePath, legacyPath, token)
                    } else {
                        val pfd = output.openWriteFd()
                        if (pfd != null) {
                            pfd.use { fd ->
                                NativeDownloader.nativeMuxMp4ToFd(rawVideo.absolutePath, rawAudio.absolutePath, fd.fd, token)
                            }
                        } else {
                            val muxedTemp = File(temp, "rust_muxed.mp4")
                            NativeDownloader.nativeMuxMp4(rawVideo.absolutePath, rawAudio.absolutePath, muxedTemp.absolutePath, token)
                            copyFileToDestination(muxedTemp, output)
                        }
                    }
                    partialOutput = null
                    output to finalBytes
                }
            }
        } catch (e: CancellationException) {
            runCatching { partialOutput?.abort() }
            throw e
        } catch (e: Exception) {
            runCatching { partialOutput?.abort() }
            if (cancelled) throw CancellationException()
            Log.w("FlufPlayDownload", "Rust fast-path failed, falling back to yt-dlp", e)
            VideoRepository.evict(url)
            temp.listFiles()?.forEach { it.deleteRecursively() }
            return null
        } finally {
            activeCancelToken = 0L
            NativeDownloader.nativeDestroyCancelToken(token)
        }
    }

    override fun onDestroy() { cancelActiveWork(); scope.cancel(); super.onDestroy() }
}
