package id.flufclip.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/** Optional bounded artwork cache. Failure never prevents saving or playing the downloaded media. */
internal suspend fun cacheDownloadArtwork(context: Context, video: DownloadVideo): DownloadVideo = withContext(Dispatchers.IO) {
    suspend fun save(url: String, width: Int, height: Int): String {
        if (url.isBlank() || isLocalDownloadUri(url)) return url
        return withTimeoutOrNull(3000) {
            try {
                val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(width, height).allowHardware(false).build()) as? SuccessResult
                val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap ?: return@withTimeoutOrNull null
                val folder = File(context.filesDir, "download-artwork")
                if (!folder.isDirectory && !folder.mkdirs()) return@withTimeoutOrNull null
                val file = File(folder, "${UUID.randomUUID()}.jpg")
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
                Uri.fromFile(file).toString()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        } ?: ""
    }
    video.copy(thumbnail = save(video.thumbnail, 512, 288), avatar = save(video.avatar, 96, 96))
}
