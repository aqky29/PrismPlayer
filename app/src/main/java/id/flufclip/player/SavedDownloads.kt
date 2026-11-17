package id.flufclip.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedDownload(val uri: String, val name: String, val mime: String, val bytes: Long, val video: DownloadVideo = DownloadVideo()) {
    val title: String get() = video.title.ifBlank { name }
}

internal fun DownloadVideo.json(): JSONObject = JSONObject().put("source", sourceUrl).put("title", title)
    .put("uploader", uploader).put("channel", channelUrl).put("thumbnail", thumbnail).put("avatar", avatar)
    .put("duration", durationMs).put("views", views).put("likes", likes).put("subscribers", subscribers).put("description", description)

internal fun downloadVideo(json: JSONObject?): DownloadVideo = if (json == null) DownloadVideo() else DownloadVideo(
    json.optString("source"), json.optString("title"), json.optString("uploader"), json.optString("channel"),
    json.optString("thumbnail"), json.optString("avatar"), json.optLong("duration"), json.optLong("views", -1),
    json.optLong("likes", -1), json.optLong("subscribers", -1), json.optString("description"))

internal object SavedDownloads {
    val revision = MutableStateFlow(0L)
    val playbackRevision = MutableStateFlow(0L)
    private fun preferences(context: Context) = context.getSharedPreferences("saved_downloads", Context.MODE_PRIVATE)
    fun list(context: Context): List<SavedDownload> = runCatching {
        val entries = JSONArray(preferences(context).getString("entries", "[]"))
        (0 until entries.length()).mapNotNull { index -> runCatching {
            val item = entries.getJSONObject(index)
            SavedDownload(item.getString("uri"), item.getString("name"), item.getString("mime"), item.getLong("bytes"), downloadVideo(item.optJSONObject("video")))
        }.getOrNull() }
    }.getOrDefault(emptyList())

    @Synchronized fun add(context: Context, saved: SavedDownload) {
        val entries = JSONArray()
        (listOf(saved) + list(context).filterNot { it.uri == saved.uri }).forEach { item ->
            entries.put(JSONObject().put("uri", item.uri).put("name", item.name).put("mime", item.mime).put("bytes", item.bytes).put("video", item.video.json()))
        }
        check(preferences(context).edit().putString("entries", entries.toString()).commit()) { "Cannot save download history" }
        revision.update { it + 1 }
    }

    private fun deleteArtworkFile(uriString: String) {
        if (uriString.isNotBlank() && isLocalDownloadUri(uriString)) {
            runCatching {
                val path = Uri.parse(uriString).path
                if (!path.isNullOrBlank()) {
                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                }
            }
        }
    }

    @Synchronized fun forget(context: Context, saved: SavedDownload) {
        val entries = JSONArray()
        list(context).filterNot { it.uri == saved.uri }.forEach { item ->
            entries.put(JSONObject().put("uri", item.uri).put("name", item.name).put("mime", item.mime).put("bytes", item.bytes).put("video", item.video.json()))
        }
        preferences(context).edit().putString("entries", entries.toString()).apply()
        deleteArtworkFile(saved.video.thumbnail)
        deleteArtworkFile(saved.video.avatar)
        revision.update { it + 1 }
    }

    fun displayName(context: Context, uri: Uri, fallback: String): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: fallback

    fun openIntent(context: Context, saved: SavedDownload? = null) = Intent(context, DownloadsActivity::class.java).apply {
        saved?.let { putExtra("saved_uri", it.uri) }
    }

    fun open(context: Context, saved: SavedDownload? = null) {
        context.startActivity(openIntent(context, saved))
    }
}
