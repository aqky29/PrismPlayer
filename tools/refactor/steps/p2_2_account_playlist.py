"""Fase 2.2: satukan handler onOpen/onPlay AccountPlaylistScreen yang duplikat menjadi fungsi lokal playAccountPlaylist()."""
from _patch import Patch

p = Patch('AppRoot.kt')
p.between('            AccountPlaylistScreen(collection, onBack = { accountPlaylist = null }, onNavigate = selectTab, onOpen = { video, entries ->',
          '            }, onSave = historyActions.save, onShare = historyActions.share)', '''            AccountPlaylistScreen(collection, onBack = { accountPlaylist = null }, onNavigate = selectTab,
                onOpen = { video, entries -> playAccountPlaylist(collection, entries, video, shortsAsHistory = true) },
                onPlay = { entries -> entries.firstOrNull()?.let { playAccountPlaylist(collection, entries, it, shortsAsHistory = false) } },
                onSave = historyActions.save, onShare = historyActions.share)
''', include_end=True)
anchor = '    val historyActions = HistoryActions('
p.rep(anchor, '''    /** Putar [video] dari playlist akun [collection] dengan [entries] sebagai antrean. Bila [shortsAsHistory], Short dibuka di layar Shorts. */
    fun playAccountPlaylist(collection: AccountPlaylist, entries: List<WatchedVideo>, video: WatchedVideo, shortsAsHistory: Boolean) {
        val pid = collection.id.removePrefix("VL")
        val resolvedTitle = entries.firstOrNull()?.playlistTitle?.takeIf { it.isNotBlank() } ?: collection.title
        val resolvedCount = "${entries.size} videos"
        WatchHistory.rememberPlaylistVideos(
            watchPrefs,
            PlaylistRef(pid, resolvedTitle, resolvedCount, entries.firstOrNull()?.uploader.orEmpty()),
            entries.map { it.url }
        )
        watchHistory = WatchHistory.read(watchPrefs)
        if (accountEntries.isNotEmpty()) accountEntries = WatchHistory.enrichAccountEntries(accountEntries, watchPrefs)
        if (shortsAsHistory && video.isShort) {
            openHistoryEntry(video)
            return
        }
        playlistQueueJob?.cancel()
        activePlaylistId = pid
        activePlaylistTitle = resolvedTitle
        activePlaylistCount = resolvedCount
        showHistory = false; shortsMode = false; queue = entries.map(::queuedItem)
        openUrl(video.url, PendingVideo(video.url, video.title, video.uploader, video.thumbnail.ifBlank { null }), resumeLocal = false)
    }
''' + anchor)
p.save()
