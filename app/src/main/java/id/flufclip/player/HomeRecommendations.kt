package id.flufclip.player

import android.content.SharedPreferences
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/** Bounded local ranking based on watched videos, not an account recommendation model. */
internal object HomeRecommendations {
    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
    private val stopWords = setOf("yang", "untuk", "dengan", "dari", "dalam", "video", "terbaru", "official", "channel", "youtube", "episode", "indonesia", "the", "this", "that", "with", "from", "music", "lyrics", "full", "part")
    private val wordSplit = Regex("[^\\p{L}\\p{N}]+")
    private fun tokens(value: String) = normalize(value).split(wordSplit)
        .filter { it.length >= 4 && it !in stopWords && it.any(Char::isLetter) }.distinct().take(12)

    fun query(history: List<WatchedVideo>, topics: List<String>, index: Int, searches: List<String> = emptyList()): String {
        val recent = history.take(30)
        val channels = recent.map { it.uploader.trim() }.filter { it.isNotBlank() }.distinctBy(::normalize)
        val titles = recent.map { tokens(it.title).take(4).joinToString(" ") }.filter { it.isNotBlank() }.distinct()
        val recentSearches = searches.map { it.trim() }.filter { it.isNotBlank() }.distinctBy(::normalize)
        val slot = (index / 5).coerceAtLeast(0)
        return when (index % 5) {
            0 -> channels.getOrNull(slot % channels.size.coerceAtLeast(1))
            1 -> titles.getOrNull(slot % titles.size.coerceAtLeast(1))
            2 -> channels.getOrNull((slot + 1) % channels.size.coerceAtLeast(1))
            3 -> recentSearches.getOrNull(slot % recentSearches.size.coerceAtLeast(1))
            else -> null
        } ?: topics.getOrNull(slot % topics.size.coerceAtLeast(1)) ?: "video terbaru"
    }

    /**
     * URL channel dari tontonan terakhir, terbaru dulu, untuk dicek siaran live-nya. Entri riwayat lama belum menyimpan URL
     * channel, jadi dicari lewat nama pada [known] (item feed yang sudah dimuat). Entri tanpa keduanya dilewati.
     */
    fun watchedChannels(history: List<WatchedVideo>, known: List<StreamInfoItem>, limit: Int = 6): List<String> {
        val byName = HashMap<String, String>()
        known.forEach { item ->
            val url = item.uploaderUrl.orEmpty()
            if (url.isNotBlank()) byName.putIfAbsent(normalize(item.uploaderName.orEmpty()), url)
        }
        return history.asSequence().take(30)
            .mapNotNull { video -> video.channelUrl?.takeIf { it.isNotBlank() } ?: byName[normalize(video.uploader)] }
            .distinct().take(limit).toList()
    }

    /** Taruh siaran live dari channel yang views di awal feed (paling banyak empat), tanpa menggandakan item yang sama. */
    fun withLive(feed: List<StreamInfoItem>, live: List<StreamInfoItem>, hiddenVideos: Set<String>, hiddenChannels: Set<String>): List<StreamInfoItem> {
        val fresh = live.filter { it.url.isNotBlank() && it.url !in hiddenVideos && it.uploaderUrl.orEmpty() !in hiddenChannels }
            .distinctBy { it.url }.take(4)
        if (fresh.isEmpty()) return feed
        val urls = fresh.mapTo(HashSet()) { it.url }
        return fresh + feed.filter { it.url !in urls }
    }

    fun rank(items: List<StreamInfoItem>, history: List<WatchedVideo>, hiddenVideos: Set<String>, hiddenChannels: Set<String>): List<StreamInfoItem> {
        val recent = history.take(50)
        val watched = history.mapTo(HashSet()) { it.url }
        val channelWeights = mutableMapOf<String, Int>()
        val words = mutableMapOf<String, Int>()
        recent.forEachIndexed { index, video ->
            val weight = if (index < 5) 3 else if (index < 20) 2 else 1
            normalize(video.uploader).takeIf { it.isNotEmpty() }?.let { channelWeights[it] = (channelWeights[it] ?: 0) + weight }
            tokens(video.title).forEach { words[it] = (words[it] ?: 0) + weight }
        }
        data class Candidate(val item: StreamInfoItem, val score: Int, val channel: String)
        val candidates = items.distinctBy { it.url }.filter { it.url.isNotBlank() && it.url !in hiddenVideos && it.uploaderUrl.orEmpty() !in hiddenChannels }
        // Keep reranking bounded even after a long scrolling session.
        val remaining = candidates.take(300)
            .mapIndexed { index, item ->
                val channel = normalize(item.uploaderName.orEmpty())
                val channelScore = (channelWeights[channel] ?: 0).coerceAtMost(9) * 2
                val titleScore = tokens(item.name).sumOf { (words[it] ?: 0).coerceAtMost(4) }.coerceAtMost(14)
                // Tanpa bobot ini siaran live dari kiosk selalu tenggelam di akhir karena tidak cocok dengan riwayat.
                val liveScore = if (!item.isOngoingLive()) 0 else if ((channelWeights[channel] ?: 0) > 0) 16 else 8
                Candidate(item, channelScore + titleScore + liveScore - (if (item.url in watched) 35 else 0) - (index / 12).coerceAtMost(6), channel)
            }.toMutableList()
        val result = ArrayList<StreamInfoItem>(remaining.size)
        val channelCounts = mutableMapOf<String, Int>()
        var previousChannel = ""
        while (remaining.isNotEmpty()) {
            val best = remaining.indices.maxByOrNull { index ->
                val candidate = remaining[index]
                val repeated = if (candidate.channel.isBlank()) 0 else (channelCounts[candidate.channel] ?: 0) * 4 + if (candidate.channel == previousChannel) 8 else 0
                candidate.score - repeated
            } ?: break
            val candidate = remaining.removeAt(best)
            result.add(candidate.item)
            if (candidate.channel.isNotBlank()) channelCounts[candidate.channel] = (channelCounts[candidate.channel] ?: 0) + 1
            previousChannel = candidate.channel
        }
        return result + candidates.drop(300)
    }

    fun read(prefs: SharedPreferences, key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet().orEmpty()
    fun hide(prefs: SharedPreferences, key: String, value: String): Set<String> {
        val updated = (read(prefs, key) + value).toList().takeLast(500).toSet()
        prefs.edit().putStringSet(key, updated).apply()
        return updated
    }
}
