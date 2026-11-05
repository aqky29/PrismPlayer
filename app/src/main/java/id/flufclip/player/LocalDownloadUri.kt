package id.flufclip.player

/** A downloaded item must never silently fall back to streaming an online URL. */
internal fun isLocalDownloadUri(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
        "content" -> !uri.authority.isNullOrBlank()
        "file" -> uri.authority.isNullOrEmpty() && uri.path?.startsWith("/") == true
        else -> false
    }
}.getOrDefault(false)
