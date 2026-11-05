package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Feed tab Subscriptions: entri, daftar channel, dan video khusus member. Respons dari sesi akun lama dibuang lewat sessionRevision. */
@Stable
internal class SubscriptionsState(private val scope: CoroutineScope) {
    var entries by mutableStateOf<List<SubsEntry>>(emptyList()); private set
    var continuationToken by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var end by mutableStateOf(false); private set
    var channels by mutableStateOf<List<SubsChannel>>(emptyList()); private set
    var channelsAttempted by mutableStateOf(false); private set
    var membership by mutableStateOf<List<SubsEntry.Video>>(emptyList()); private set
    var membershipLoading by mutableStateOf(false); private set

    fun load(refresh: Boolean = false) {
        if (!YouTubeAuth.isLoggedIn || loading) return
        val revision = YouTubeAuth.sessionRevision
        loading = true
        error = null
        scope.launch {
            try {
                val page = HomeFeed.subscriptions(revision)
                if (revision != YouTubeAuth.sessionRevision) return@launch
                entries = page.entries
                continuationToken = page.continuationToken
                end = page.continuationToken.isNullOrBlank()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == YouTubeAuth.sessionRevision) error = "Failed to load subscriptions: ${e.message ?: "try again"}"
            } finally {
                if (revision == YouTubeAuth.sessionRevision) loading = false
            }
        }
    }

    fun loadMore() {
        val token = continuationToken ?: return
        if (loading || end) return
        val revision = YouTubeAuth.sessionRevision
        loading = true
        scope.launch {
            try {
                val page = HomeFeed.moreSubscriptions(token, revision)
                if (revision != YouTubeAuth.sessionRevision || continuationToken != token) return@launch
                entries = mergeSubsEntries(entries, page.entries)
                continuationToken = page.continuationToken
                end = page.continuationToken.isNullOrBlank()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == YouTubeAuth.sessionRevision) error = "Failed to load the next page: ${e.message ?: "try again"}"
            } finally {
                if (revision == YouTubeAuth.sessionRevision) loading = false
            }
        }
    }

    fun loadMembership() {
        if (!YouTubeAuth.isLoggedIn || membershipLoading || membership.isNotEmpty()) return
        val revision = YouTubeAuth.sessionRevision
        // Hanya channel yang punya item "Members only" di feed; playlist UUMO-nya berisi seluruh video member channel itu.
        val memberChannels = channels.filter { channel ->
            entries.filterIsInstance<SubsEntry.Video>().any { it.meta.membersOnly && matchesSubsChannel(it.item, channel) }
        }
        if (memberChannels.isEmpty()) return
        membershipLoading = true
        scope.launch {
            try {
                val result = mutableListOf<SubsEntry.Video>()
                for (channel in memberChannels) {
                    if (revision != YouTubeAuth.sessionRevision) return@launch
                    runCatching { HomeFeed.membershipVideos(channel.id, revision) }.getOrNull()?.let { result.addAll(it) }
                }
                if (revision != YouTubeAuth.sessionRevision) return@launch
                membership = result.distinctBy { it.item.url }
            } finally {
                if (revision == YouTubeAuth.sessionRevision) membershipLoading = false
            }
        }
    }

    /** Tab Subscriptions dibuka (sudah login): muat halaman pertama bila kosong dan daftar channel sekali per sesi. */
    fun onTabOpened() {
        if (entries.isEmpty() && !loading) load()
        if (!channelsAttempted) {
            channelsAttempted = true
            val revision = YouTubeAuth.sessionRevision
            scope.launch {
                runCatching { HomeFeed.channels(revision) }.onSuccess { if (revision == YouTubeAuth.sessionRevision) channels = it }
            }
        }
    }

    /** Sesi akun berganti atau logout: buang semua data langganan. */
    fun reset() {
        entries = emptyList()
        channels = emptyList()
        channelsAttempted = false
        membership = emptyList()
        continuationToken = null
        loading = false
        membershipLoading = false
        error = null
        end = false
    }
}
