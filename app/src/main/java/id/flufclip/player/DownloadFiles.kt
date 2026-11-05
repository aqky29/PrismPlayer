package id.flufclip.player

import java.io.File

internal const val DOWNLOAD_SUBDIRECTORY = "video-yt-downloader"

internal fun downloadFileName(title: String, audioOnly: Boolean): String {
    val cleaned = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_")
        .trim().trim('.')
    val points = cleaned.codePoints().limit(60).toArray()
    val base = String(points, 0, points.size).trim().ifBlank { "video" }
    return "$base.${if (audioOnly) "mp3" else "mp4"}"
}

/** Reserve a new file atomically; a repeated title must never overwrite an existing download. */
internal fun reserveDownloadFile(directory: File, name: String): File {
    require(name.isNotBlank() && name != "." && name != ".." && File(name).name == name)
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create downloads folder" }
    val stem = name.substringBeforeLast('.', name)
    val suffix = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    for (index in 0..9999) {
        val file = File(directory, if (index == 0) name else "$stem ($index)$suffix")
        if (file.createNewFile()) return file
    }
    error("Too many files with the same name")
}
