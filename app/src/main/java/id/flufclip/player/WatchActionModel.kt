package id.flufclip.player

internal enum class WatchActionKind { RATING, ASK, LIVE_CHAT, REPLAY_CHAT, SHARE, DOWNLOAD, SAVE, MORE }

/**
 * Capabilities implemented by this client; unknown official Remix/Clip support is not assumed. [askAvailable] hanya benar bila
 * server mengirim panel Ask untuk video dan akun ini (lihat [AskYouTube.panel]); tanpa itu tombolnya tidak dibuat.
 */
internal fun watchActions(live: Boolean, chatAvailable: Boolean, replayAvailable: Boolean, downloadable: Boolean, askAvailable: Boolean = false): List<WatchActionKind> = buildList {
    add(WatchActionKind.RATING)
    if (live && chatAvailable) add(WatchActionKind.LIVE_CHAT)
    else if (!live && replayAvailable) add(WatchActionKind.REPLAY_CHAT)
    add(WatchActionKind.SHARE)
    if (askAvailable) add(WatchActionKind.ASK)
    if (!live && downloadable) add(WatchActionKind.DOWNLOAD)
    add(WatchActionKind.SAVE)
    add(WatchActionKind.MORE)
}
