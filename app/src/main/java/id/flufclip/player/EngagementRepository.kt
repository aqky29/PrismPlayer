package id.flufclip.player

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Status akun untuk satu target. `value == null` berarti server belum memberi tahu statusnya; itu bukan "Off".
 * [pending] menandai satu operasi yang sedang berjalan, [uncertain] menandai hasil yang belum dapat dipastikan.
 */
internal data class RatingState(
    val value: Int? = null,
    val confirmed: Int? = null,
    val pending: Boolean = false,
    val uncertain: Boolean = false,
    val baseline: Int? = null,
) {
    /**
     * Perubahan jumlah suka relatif terhadap hitungan server. Tanpa nilai awal yang diketahui, hitungan tidak
     * diubah karena tidak dapat dipastikan apakah angka server sudah memasukkan suka pengguna.
     */
    val likeDelta: Int get() {
        val known = baseline ?: return 0
        return (if (value == 1) 1 else 0) - (if (known == 1) 1 else 0)
    }
}

enum class NotificationPreference {
    ALL,
    PERSONALIZED,
    NONE
}

internal data class SubscriptionState(
    val subscribed: Boolean? = null,
    val confirmed: Boolean? = null,
    val pending: Boolean = false,
    val uncertain: Boolean = false,
    val notificationPreference: NotificationPreference = NotificationPreference.PERSONALIZED,
    val notificationParams: Map<NotificationPreference, String> = emptyMap(),
)

internal data class SaveState(
    val saved: Boolean? = null,
    val playlistIds: Set<String>? = null,
    val watchLater: Boolean? = null,
)

/** Versi operasi per target saat pembacaan server dimulai; dipakai agar respons lama tidak menimpa niat baru. */
internal data class EngagementGeneration(
    val rating: Long = 0,
    val subscription: Long = 0,
    val save: Long = 0,
)

internal sealed interface MutationResult {
    data object Confirmed : MutationResult
    data class Rejected(val message: String?) : MutationResult
    data class Uncertain(val message: String?) : MutationResult
}

internal data class RatingRequest(
    val videoId: String,
    val operation: Long,
    val revision: Int,
    val target: Int,
    val previous: RatingState,
)

internal data class SubscriptionRequest(
    val channelId: String,
    val operation: Long,
    val revision: Int,
    val target: Boolean,
    val previous: SubscriptionState,
)

internal fun ratingAction(target: Int): String = when (target) {
    1 -> "like"
    -1 -> "dislike"
    else -> "removelike"
}

/** Snapshot status akun yang benar-benar dikirim server untuk satu video. Field null berarti tidak diketahui. */
internal data class EngagementSnapshot(
    val rating: Int? = null,
    val subscribed: Boolean? = null,
    val channelId: String? = null,
    val saved: Boolean? = null,
    val notificationPreference: NotificationPreference? = null,
    val notificationParams: Map<NotificationPreference, String> = emptyMap(),
)

internal data class NotificationPreferenceRequest(
    val channelId: String,
    val operation: Long,
    val revision: Int,
    val target: NotificationPreference,
    val previous: NotificationPreference,
    val params: String? = null,
)

/**
 * Satu sumber status suka/simpan/subscribe per sesi akun. Semua layar membaca instance yang sama ([Engagement])
 * sehingga Watch, Shorts, fullscreen, dan player offline menampilkan nilai yang konsisten untuk target yang sama.
 * State dipisahkan berdasarkan [YouTubeAuth.sessionRevision]; hasil operasi sesi lama tidak pernah menulis sesi baru.
 */
internal class EngagementRepository(private val revision: () -> Int = { YouTubeAuth.sessionRevision }) {
    private val ratings = mutableStateMapOf<String, RatingState>()
    private val subscriptions = mutableStateMapOf<String, SubscriptionState>()
    private val saves = mutableStateMapOf<String, SaveState>()
    private val ratingOperations = HashMap<String, Long>()
    private val subscriptionOperations = HashMap<String, Long>()
    private val saveVersions = HashMap<String, Long>()
    private var sequence = 0L

    private fun key(revision: Int, id: String) = "$revision/$id"

    fun rating(videoId: String): RatingState = ratings[key(revision(), videoId)] ?: RatingState()

    fun subscription(channelId: String): SubscriptionState = subscriptions[key(revision(), channelId)] ?: SubscriptionState()

    fun saved(videoId: String): SaveState = saves[key(revision(), videoId)] ?: SaveState()

    /**
     * Terapkan status awal dari server. Nilai lokal yang sudah diketahui atau sedang berubah tidak ditimpa,
     * sehingga respons yang tiba setelah tindakan pengguna tidak mengembalikan UI ke keadaan lama.
     */
    fun seed(videoId: String, snapshot: EngagementSnapshot, session: Int = revision(), channelIdHint: String? = null) {
        if (session != revision()) return
        val ratingKey = key(session, videoId)
        val currentRating = ratings[ratingKey]
        if (snapshot.rating != null && (currentRating == null || (!currentRating.pending && currentRating.value == null))) {
            putRating(ratingKey, RatingState(value = snapshot.rating, confirmed = snapshot.rating, baseline = snapshot.rating))
        }
        val saveKey = key(session, videoId)
        val currentSave = saves[saveKey]
        if (snapshot.saved != null && currentSave?.saved == null) {
            saves[saveKey] = SaveState(saved = snapshot.saved)
        }
        val channelId = snapshot.channelId ?: channelIdHint
        if (snapshot.subscribed != null && !channelId.isNullOrBlank()) {
            val subKey = key(session, channelId)
            val currentSub = subscriptions[subKey]
            val notifPref = snapshot.notificationPreference ?: currentSub?.notificationPreference ?: NotificationPreference.PERSONALIZED
            val mergedParams = if (snapshot.notificationParams.isNotEmpty()) {
                currentSub?.notificationParams.orEmpty() + snapshot.notificationParams
            } else currentSub?.notificationParams.orEmpty()
            if (currentSub == null || (!currentSub.pending && currentSub.subscribed == null)) {
                subscriptions[subKey] = SubscriptionState(subscribed = snapshot.subscribed, confirmed = snapshot.subscribed, notificationPreference = notifPref, notificationParams = mergedParams)
            } else if (currentSub.pending != true) {
                subscriptions[subKey] = currentSub.copy(
                    notificationPreference = snapshot.notificationPreference ?: currentSub.notificationPreference,
                    notificationParams = mergedParams
                )
            }
        }
    }

    /** Versi operasi saat ini; diambil sebelum pembacaan server supaya respons basi dapat ditolak. */
    fun generation(videoId: String, channelId: String? = null): EngagementGeneration {
        val revision = revision()
        return EngagementGeneration(
            rating = ratingOperations[key(revision, videoId)] ?: 0,
            subscription = channelId?.let { subscriptionOperations[key(revision, it)] } ?: 0,
            save = saveVersions[key(revision, videoId)] ?: 0,
        )
    }

    /**
     * Terapkan pembacaan server yang otoritatif. Berbeda dengan [seed], hasil ini boleh mengganti nilai yang sudah
     * diketahui (misalnya setelah hasil uncertain atau perubahan dari aplikasi YouTube), tetapi hanya bila tidak ada
     * operasi lokal yang lebih baru daripada [generation].
     */
    fun refresh(videoId: String, snapshot: EngagementSnapshot, session: Int = revision(), generation: EngagementGeneration = EngagementGeneration(), channelIdHint: String? = null) {
        if (session != revision()) return
        val ratingKey = key(session, videoId)
        if (snapshot.rating != null) {
            val current = ratings[ratingKey]
            if (current?.pending != true && (ratingOperations[ratingKey] ?: 0) == generation.rating) {
                putRating(ratingKey, RatingState(value = snapshot.rating, confirmed = snapshot.rating, baseline = snapshot.rating))
            }
        }
        val saveKey = key(session, videoId)
        if (snapshot.saved != null && (saveVersions[saveKey] ?: 0) == generation.save) {
            val current = saves[saveKey] ?: SaveState()
            saves[saveKey] = current.copy(saved = snapshot.saved)
        }
        val channelId = snapshot.channelId ?: channelIdHint
        if (snapshot.subscribed != null && !channelId.isNullOrBlank()) {
            val subKey = key(session, channelId)
            val currentSub = subscriptions[subKey]
            val notifPref = snapshot.notificationPreference ?: currentSub?.notificationPreference ?: NotificationPreference.PERSONALIZED
            val mergedParams = if (snapshot.notificationParams.isNotEmpty()) {
                currentSub?.notificationParams.orEmpty() + snapshot.notificationParams
            } else currentSub?.notificationParams.orEmpty()
            if (currentSub?.pending != true && (subscriptionOperations[subKey] ?: 0) == generation.subscription) {
                subscriptions[subKey] = SubscriptionState(subscribed = snapshot.subscribed, confirmed = snapshot.subscribed, notificationPreference = notifPref, notificationParams = mergedParams)
            }
        }
    }

    /** Status simpan dari dialog Save (server) bersifat otoritatif, termasuk setelah mutasi yang terkonfirmasi. */
    fun setSaved(videoId: String, saved: Boolean?) {
        val key = key(revision(), videoId)
        saveVersions[key] = ++sequence
        saves[key] = SaveState(saved = saved)
    }

    /** Membership playlist dari dialog Save; Watch later dibedakan dari daftar lain. */
    fun setPlaylists(videoId: String, playlistIds: Set<String>) {
        val key = key(revision(), videoId)
        saveVersions[key] = ++sequence
        saves[key] = SaveState(saved = playlistIds.isNotEmpty(), playlistIds = playlistIds, watchLater = "WL" in playlistIds)
    }

    /** Simpan ke Watch later dari jalur yang tidak membuka dialog playlist. */
    fun setWatchLater(videoId: String, watchLater: Boolean = true) {
        val key = key(revision(), videoId)
        saveVersions[key] = ++sequence
        val current = saves[key] ?: SaveState()
        val ids = (current.playlistIds ?: emptySet()).toMutableSet().apply { if (watchLater) add("WL") else remove("WL") }
        saves[key] = current.copy(saved = ids.isNotEmpty(), playlistIds = ids, watchLater = watchLater)
    }

    /**
     * Akhiri operasi yang coroutine-nya dibatalkan (mis. composable ditutup). Request mungkin sudah terkirim,
     * jadi status ditandai belum pasti dan pending tidak dibiarkan terkunci. Pembacaan server berikutnya yang
     * otoritatif akan membersihkannya lewat [refresh].
     */
    fun abandonRating(request: RatingRequest) {
        if (request.revision != revision()) return
        val key = key(request.revision, request.videoId)
        if (ratingOperations[key] != request.operation) return
        val current = ratings[key] ?: return
        putRating(key, current.copy(value = current.confirmed, pending = false, uncertain = true))
    }

    fun abandonSubscription(request: SubscriptionRequest) {
        if (request.revision != revision()) return
        val key = key(request.revision, request.channelId)
        if (subscriptionOperations[key] != request.operation) return
        val current = subscriptions[key] ?: return
        subscriptions[key] = current.copy(subscribed = current.confirmed, pending = false, uncertain = true)
    }

    /**
     * Mulai tindakan penilaian. Arah [direction] adalah niat pengguna (1 suka, -1 tidak suka), bukan toggle buta:
     * bila status awal belum diketahui, permintaan dikirim sebagai tujuan eksplisit.
     *
     * Ketukan saat operasi sebelumnya masih berjalan tetap mengubah tampilan; niat terakhir dikirim setelah operasi
     * itu selesai lewat [followUpRating]. Hanya satu request yang berjalan per target.
     */
    fun toggleRating(videoId: String, direction: Int): RatingRequest? {
        require(direction == 1 || direction == -1)
        val revision = revision()
        val ratingKey = key(revision, videoId)
        val before = ratings[ratingKey] ?: RatingState()
        val target = when {
            before.value == null -> direction
            before.value == direction -> 0
            else -> direction
        }
        if (before.value != null && target == before.value) return null
        val optimistic = before.copy(value = target, uncertain = false)
        if (before.pending) {
            putRating(ratingKey, optimistic)
            return null
        }
        val operation = ++sequence
        ratingOperations[ratingKey] = operation
        putRating(ratingKey, optimistic.copy(pending = true))
        return RatingRequest(videoId, operation, revision, target, before)
    }

    fun completeRating(request: RatingRequest, result: MutationResult): RatingState? {
        if (request.revision != revision()) return null
        val ratingKey = key(request.revision, request.videoId)
        if (ratingOperations[ratingKey] != request.operation) return null
        val current = ratings[ratingKey] ?: return null
        val applied = when (result) {
            // Ketukan berikutnya yang terjadi selama request berjalan tetap dipertahankan untuk [followUpRating].
            MutationResult.Confirmed -> current.copy(confirmed = request.target, pending = false, uncertain = false)
            is MutationResult.Rejected -> current.copy(value = current.confirmed, pending = false, uncertain = false)
            is MutationResult.Uncertain -> current.copy(value = current.confirmed, pending = false, uncertain = true)
        }
        putRating(ratingKey, applied)
        return applied
    }

    /** Request lanjutan bila niat terakhir pengguna berbeda dari nilai yang sudah dikonfirmasi server. */
    fun followUpRating(videoId: String): RatingRequest? {
        val revision = revision()
        val ratingKey = key(revision, videoId)
        val current = ratings[ratingKey] ?: return null
        if (current.pending) return null
        val desired = current.value ?: return null
        if (desired == current.confirmed) return null
        val operation = ++sequence
        ratingOperations[ratingKey] = operation
        putRating(ratingKey, current.copy(pending = true))
        return RatingRequest(videoId, operation, revision, desired, current)
    }

    fun setSubscription(channelId: String, subscribe: Boolean): SubscriptionRequest? {
        val revision = revision()
        val subKey = key(revision, channelId)
        val before = subscriptions[subKey] ?: SubscriptionState()
        if (before.subscribed == subscribe) return null
        val optimistic = before.copy(subscribed = subscribe, uncertain = false)
        if (before.pending) {
            subscriptions[subKey] = optimistic
            return null
        }
        val operation = ++sequence
        subscriptionOperations[subKey] = operation
        subscriptions[subKey] = optimistic.copy(pending = true)
        return SubscriptionRequest(channelId, operation, revision, subscribe, before)
    }

    fun completeSubscription(request: SubscriptionRequest, result: MutationResult): SubscriptionState? {
        if (request.revision != revision()) return null
        val subKey = key(request.revision, request.channelId)
        if (subscriptionOperations[subKey] != request.operation) return null
        val current = subscriptions[subKey] ?: return null
        val applied = when (result) {
            MutationResult.Confirmed -> current.copy(confirmed = request.target, pending = false, uncertain = false)
            is MutationResult.Rejected -> current.copy(subscribed = current.confirmed, pending = false, uncertain = false)
            is MutationResult.Uncertain -> current.copy(subscribed = current.confirmed, pending = false, uncertain = true)
        }
        subscriptions[subKey] = applied
        return applied
    }

    fun followUpSubscription(channelId: String): SubscriptionRequest? {
        val revision = revision()
        val subKey = key(revision, channelId)
        val current = subscriptions[subKey] ?: return null
        if (current.pending) return null
        val desired = current.subscribed ?: return null
        if (desired == current.confirmed) return null
        val operation = ++sequence
        subscriptionOperations[subKey] = operation
        subscriptions[subKey] = current.copy(pending = true)
        return SubscriptionRequest(channelId, operation, revision, desired, current)
    }

    fun setNotificationPreference(channelId: String, preference: NotificationPreference): NotificationPreferenceRequest? {
        val revision = revision()
        val subKey = key(revision, channelId)
        val current = subscriptions[subKey] ?: SubscriptionState(subscribed = true, confirmed = true)
        if (current.notificationPreference == preference) return null
        val params = current.notificationParams[preference]
        val optimistic = current.copy(notificationPreference = preference)
        subscriptions[subKey] = optimistic
        val operation = ++sequence
        subscriptionOperations[subKey] = operation
        return NotificationPreferenceRequest(channelId, operation, revision, preference, current.notificationPreference, params)
    }

    fun completeNotificationPreference(request: NotificationPreferenceRequest, result: MutationResult) {
        if (request.revision != revision()) return
        val subKey = key(request.revision, request.channelId)
        val current = subscriptions[subKey] ?: return
        if (result is MutationResult.Rejected) {
            subscriptions[subKey] = current.copy(notificationPreference = request.previous)
        }
    }

    private fun putRating(key: String, state: RatingState) {
        // Nilai yang tidak diketahui tidak perlu disimpan sebagai entri kosong.
        if (state == RatingState()) ratings.remove(key) else ratings[key] = state
        if (ratings.size > MAX_ENTRIES) prune(ratings, revision())
    }

    private fun prune(map: MutableMap<String, *>, revision: Int) {
        val prefix = "$revision/"
        map.keys.filterNot { it.startsWith(prefix) }.forEach(map::remove)
    }

    private companion object { const val MAX_ENTRIES = 256 }
}

/** Instance bersama untuk seluruh UI. */
internal val Engagement = EngagementRepository()

/**
 * Statistik engagement akun dari endpoint `next`. Parsing bersifat defensif: bentuk yang tidak dikenali
 * menghasilkan status tidak diketahui, bukan asumsi "Off".
 */
internal object EngagementSource {
    private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

    suspend fun fetch(videoId: String, expectedRevision: Int): EngagementSnapshot = withContext(Dispatchers.IO) {
        require(videoIdPattern.matches(videoId)) { "Invalid video" }
        val snapshot = parse(HomeFeed.accountRequest("next", JSONObject().put("videoId", videoId), expectedRevision))
        // Hanya kategori hasil yang dicatat; tidak ada nilai header, token, atau status akun mentah.
        android.util.Log.d("FlufEngagement", "next like=${if (snapshot.rating != null) "known" else "unknown"} subscribe=${if (snapshot.subscribed != null) "known" else "unknown"}")
        snapshot
    }

    internal fun parse(json: JSONObject): EngagementSnapshot {
        val toggles = HashMap<String, Boolean>()
        val notifParams = HashMap<NotificationPreference, String>()
        visit(json) { node ->
            // Entity mutation menyimpan `entityKey` pada node mutasi, bukan di dalam `toggleButtonMutation`.
            listOfNotNull(node.optJSONObject("toggleButtonMutation"), node.optJSONObject("payload")?.optJSONObject("toggleButtonMutation")).forEach { mutation ->
                val key = mutation.optString("entityKey").ifBlank { node.optString("entityKey") }
                val toggled = toggleValue(mutation)
                if (key.isNotBlank() && toggled != null) toggles[key] = toggled
            }
            node.optJSONObject("toggleButtonRenderer")?.let { renderer ->
                val key = renderer.optString("targetId")
                if (key.isNotBlank() && renderer.has("isToggled")) toggles.putIfAbsent(key, renderer.optBoolean("isToggled"))
            }
            node.optJSONObject("subscriptionNotificationOptionRenderer")?.let { option ->
                val stateId = option.optInt("stateId").takeIf { it != 0 }
                    ?: option.optInt("currentStateId").takeIf { it != 0 }
                val pref = when (stateId) {
                    1 -> NotificationPreference.ALL
                    2 -> NotificationPreference.PERSONALIZED
                    3 -> NotificationPreference.NONE
                    else -> {
                        val label = accessibilityLabel(option).lowercase()
                        when {
                            label.contains("semua") || label.contains("all") -> NotificationPreference.ALL
                            label.contains("personali") -> NotificationPreference.PERSONALIZED
                            label.contains("tidak") || label.contains("none") -> NotificationPreference.NONE
                            else -> null
                        }
                    }
                }
                val p = option.optJSONObject("command")?.optJSONObject("modifyChannelPreferenceEndpoint")?.optString("params")
                    ?: option.optJSONObject("serviceEndpoint")?.optJSONObject("modifyChannelPreferenceEndpoint")?.optString("params")
                    ?: option.optJSONObject("modifyChannelPreferenceEndpoint")?.optString("params")
                if (pref != null && !p.isNullOrBlank()) {
                    notifParams[pref] = p
                }
            }
            node.optJSONObject("subscriptionNotificationToggleButtonRenderer")?.optJSONArray("states")?.let { states ->
                for (i in 0 until states.length()) {
                    val stateObj = states.optJSONObject(i) ?: continue
                    val stateId = stateObj.optInt("stateId")
                    val pref = when (stateId) {
                        1 -> NotificationPreference.ALL
                        2 -> NotificationPreference.PERSONALIZED
                        3 -> NotificationPreference.NONE
                        else -> null
                    }
                    val p = stateObj.optJSONObject("serviceEndpoint")?.optJSONObject("modifyChannelPreferenceEndpoint")?.optString("params")
                        ?: stateObj.optJSONObject("command")?.optJSONObject("modifyChannelPreferenceEndpoint")?.optString("params")
                    if (pref != null && !p.isNullOrBlank()) {
                        notifParams[pref] = p
                    }
                }
            }
        }

        var like: Boolean? = null
        var dislike: Boolean? = null
        var likeEntityKey: String? = null
        var dislikeEntityKey: String? = null
        var subscribed: Boolean? = null
        var channelId: String? = null
        var notificationPreference: NotificationPreference? = null

        fun sectionKey(section: Int, node: JSONObject) {
            val key = node.optString("stateEntityKey")
            if (key.isBlank()) return
            if (section == 1 && likeEntityKey == null) likeEntityKey = key
            else if (section == -1 && dislikeEntityKey == null) dislikeEntityKey = key
        }

        fun walk(value: Any?, section: Int) {
            when (value) {
                is JSONObject -> {
                    sectionKey(section, value)
                    if (section != 0 && value.has("isToggled")) {
                        if (section == 1) like = value.optBoolean("isToggled") else dislike = value.optBoolean("isToggled")
                    }
                    value.optJSONObject("toggleButtonRenderer")?.let { renderer ->
                        val toggled = when {
                            renderer.has("isToggled") -> renderer.optBoolean("isToggled")
                            else -> null
                        }
                        if (toggled != null) {
                            when (section) {
                                1 -> like = toggled
                                -1 -> dislike = toggled
                                else -> {
                                    val id = renderer.optString("targetId")
                                    val label = accessibilityLabel(renderer).lowercase()
                                    when {
                                        id.contains("dislike") || label.contains("dislike") -> dislike = toggled
                                        id.contains("like") || label.contains("like") -> like = toggled
                                    }
                                }
                            }
                        }
                    }
                    value.optJSONObject("likeButtonRenderer")?.let { renderer ->
                        when (renderer.optString("likeStatus").uppercase()) {
                            "LIKE" -> like = true
                            "DISLIKE" -> dislike = true
                            "INDIFFERENT" -> {
                                if (like == null) like = false
                                if (dislike == null) dislike = false
                            }
                        }
                    }
                    value.optJSONObject("subscriptionNotificationToggleButtonRenderer")?.let { renderer ->
                        when (renderer.optInt("currentStateId")) {
                            1 -> notificationPreference = NotificationPreference.ALL
                            2 -> notificationPreference = NotificationPreference.PERSONALIZED
                            3 -> notificationPreference = NotificationPreference.NONE
                        }
                    }
                    value.optJSONObject("subscribeButtonRenderer")?.let { renderer ->
                        if (renderer.has("subscribed")) subscribed = renderer.optBoolean("subscribed")
                        if (renderer.has("channelId")) channelId = renderer.optString("channelId").takeIf { it.isNotBlank() }
                        renderer.optJSONObject("notificationPreferenceButton")
                            ?.optJSONObject("subscriptionNotificationToggleButtonRenderer")
                            ?.let { toggle ->
                                when (toggle.optInt("currentStateId")) {
                                    1 -> notificationPreference = NotificationPreference.ALL
                                    2 -> notificationPreference = NotificationPreference.PERSONALIZED
                                    3 -> notificationPreference = NotificationPreference.NONE
                                }
                            }
                    }
                    value.keys().forEach { key ->
                        when (key) {
                            "likeButtonViewModel", "likeButton" -> value.optJSONObject(key)?.let { walk(it, 1) }
                            "dislikeButtonViewModel", "dislikeButton" -> value.optJSONObject(key)?.let { walk(it, -1) }
                            "likeButtonRenderer", "subscribeButtonRenderer", "toggleButtonRenderer", "subscriptionNotificationToggleButtonRenderer" -> Unit
                            else -> walk(value.opt(key), section)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i), section)
            }
        }
        walk(json, 0)

        like = like ?: likeEntityKey?.let { toggles[it] }
        dislike = dislike ?: dislikeEntityKey?.let { toggles[it] }
        val rating = when {
            like == true -> 1
            dislike == true -> -1
            like == false && dislike == false -> 0
            else -> null
        }
        return EngagementSnapshot(
            rating = rating,
            subscribed = subscribed,
            channelId = channelId,
            notificationPreference = notificationPreference,
            notificationParams = notifParams
        )
    }

    /** Mendukung bentuk mutation lama (`toggled`/`isToggled`) dan bentuk `buttonState` yang lebih baru. */
    private fun toggleValue(mutation: JSONObject): Boolean? {
        if (mutation.has("toggled")) return mutation.optBoolean("toggled")
        if (mutation.has("isToggled")) return mutation.optBoolean("isToggled")
        val state = mutation.optJSONObject("buttonState")?.optString("buttonState").orEmpty()
        return when (state.uppercase()) {
            "TOGGLED" -> true
            "UNTOGGLED" -> false
            else -> null
        }
    }

    private fun accessibilityLabel(renderer: JSONObject): String {
        val data = renderer.optJSONObject("accessibilityData")
        return data?.optJSONObject("accessibilityData")?.optString("label").orEmpty()
            .ifBlank { data?.optString("label").orEmpty() }
            .ifBlank { renderer.optJSONObject("accessibility")?.optString("label").orEmpty() }
    }

    private fun visit(value: Any?, block: (JSONObject) -> Unit) {
        when (value) {
            is JSONObject -> {
                block(value)
                value.keys().forEach { key -> if (key != "trackingParams") visit(value.opt(key), block) }
            }
            is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i), block)
        }
    }
}

/** Pesan singkat untuk pengguna; null berarti operasi terkonfirmasi dan tidak perlu diberitahukan. */
internal fun MutationResult.messageOrNull(): String? = when (this) {
    MutationResult.Confirmed -> null
    is MutationResult.Rejected -> message ?: "YouTube rejected the action."
    is MutationResult.Uncertain -> message ?: "The result could not be confirmed."
}

internal suspend fun sendRating(videoId: String, action: String, revision: Int): MutationResult =
    ShortsFeed.rate(videoId, action, revision)

internal suspend fun sendSubscription(channelId: String, subscribe: Boolean, revision: Int): MutationResult =
    ShortsFeed.setSubscribed(channelId, subscribe, revision)

internal suspend fun sendNotificationPreference(channelId: String, preference: NotificationPreference, params: String?, revision: Int): MutationResult =
    ShortsFeed.setNotificationPreference(channelId, preference, params, revision)

/**
 * Setelah hasil belum pasti, baca ulang server beberapa kali tanpa mengirim ulang tindakan. Generasi diambil
 * sesaat sebelum fetch sehingga ketukan baru pengguna tidak tertimpa oleh pembacaan yang lebih lama.
 */
internal fun CoroutineScope.reconcileEngagement(videoId: String, channelId: String?, revision: Int, attempts: Int = 2) {
    launch {
        repeat(attempts) { attempt ->
            delay(if (attempt == 0) 1_500L else 4_000L)
            if (revision != YouTubeAuth.sessionRevision) return@launch
            val generation = Engagement.generation(videoId, channelId)
            val snapshot = try { EngagementSource.fetch(videoId, revision) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            if (snapshot == null) return@repeat
            Engagement.refresh(videoId, snapshot, revision, generation, channelId)
            return@launch
        }
    }
}
