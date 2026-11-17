package id.flufclip.player

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class YouTubeUserProfile(
    val name: String = "",
    val emailOrHandle: String = "",
    val avatarUrl: String? = null
)

object YouTubeAuth {
    private const val PREFS_NAME = "youtube_auth"
    private const val KEY_COOKIES = "raw_cookies"
    private const val KEY_SCOPE = "session_scope"
    private const val KEY_NAME = "profile_name"
    private const val KEY_EMAIL = "profile_email"
    private const val KEY_AVATAR = "profile_avatar"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).addNetworkInterceptor(accountHeaderInterceptor).build()

    var isLoggedIn by mutableStateOf(false)
        private set

    var sessionRevision by mutableIntStateOf(0)
        private set

    var sessionScopeId by mutableStateOf("")
        private set

    var profile by mutableStateOf(YouTubeUserProfile())
        private set

    var profileLoading by mutableStateOf(false)
        private set

    var profileError by mutableStateOf<String?>(null)
        private set

    internal var savedAccounts by mutableStateOf<List<SavedAccount>>(emptyList())
        private set
    internal var accountStorageError by mutableStateOf(false)
        private set

    private var rawCookies: String = ""
    private var profileJob: Job? = null

    @Volatile
    private var profileRequestId = 0

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sessionScopeId = prefs.getString(KEY_SCOPE, null)?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString(KEY_SCOPE, it).apply() }
        rawCookies = prefs.getString(KEY_COOKIES, "").orEmpty()
        val name = prefs.getString(KEY_NAME, "").orEmpty()
        val email = prefs.getString(KEY_EMAIL, "").orEmpty()
        val avatar = prefs.getString(KEY_AVATAR, null)
        profile = YouTubeUserProfile(name, email, avatar)
        isLoggedIn = hasValidAuthCookies(rawCookies)

        try {
            savedAccounts = AccountVault.read(context)
            accountStorageError = false
            if (isLoggedIn) scope.launch {
                try { saveCurrentAccount(context) } catch (_: Exception) { accountStorageError = true }
            }
        } catch (_: Exception) { accountStorageError = true }

        if (isLoggedIn && (name.isBlank() || avatar.isNullOrBlank())) {
            refreshProfile(context)
        }
    }

    private fun hasValidAuthCookies(cookies: String): Boolean {
        if (cookies.isBlank()) return false
        return cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=") || cookies.contains("LOGIN_INFO=")
    }

    private fun saveCurrentAccount(context: Context) {
        if (!isLoggedIn || rawCookies.isBlank()) return
        val updated = savedAccounts.filterNot { it.id == sessionScopeId } + SavedAccount(sessionScopeId, rawCookies, profile)
        AccountVault.write(context, updated)
        savedAccounts = updated
    }

    internal fun prepareAddAccount(context: Context): Boolean = try {
        if (accountStorageError) false else { saveCurrentAccount(context); true }
    } catch (_: Exception) { accountStorageError = true; false }

    internal fun switchAccount(context: Context, id: String): Boolean {
        val selected = savedAccounts.firstOrNull { it.id == id } ?: return false
        if (id == sessionScopeId && isLoggedIn) return true
        if (!prepareAddAccount(context) || !hasValidAuthCookies(selected.cookies)) return false
        PlaybackTracker.stop(sendFinal = false)
        profileJob?.cancel()
        profileJob = null
        profileLoading = false
        profileError = null
        sessionRevision++
        sessionScopeId = selected.id
        rawCookies = selected.cookies
        profile = selected.profile
        isLoggedIn = true
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SCOPE, sessionScopeId).putString(KEY_COOKIES, rawCookies)
            .putString(KEY_NAME, profile.name).putString(KEY_EMAIL, profile.emailOrHandle)
            .putString(KEY_AVATAR, profile.avatarUrl).apply()
        refreshProfile(context)
        return true
    }

    fun saveAuth(context: Context, cookies: String, name: String? = null, email: String? = null, avatarUrl: String? = null) {
        if (isLoggedIn) PlaybackTracker.stop(sendFinal = false)
        sessionRevision++
        sessionScopeId = java.util.UUID.randomUUID().toString()
        profileJob?.cancel()
        profileJob = null
        profileLoading = false
        profileError = null
        rawCookies = cookies
        isLoggedIn = hasValidAuthCookies(cookies)
        profile = YouTubeUserProfile(
            name = name.orEmpty(),
            emailOrHandle = email.orEmpty(),
            avatarUrl = avatarUrl
        )

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SCOPE, sessionScopeId)
            .putString(KEY_COOKIES, cookies)
            .putString(KEY_NAME, profile.name)
            .putString(KEY_EMAIL, profile.emailOrHandle)
            .putString(KEY_AVATAR, profile.avatarUrl)
            .apply()

        if (isLoggedIn) {
            scope.launch {
                try { saveCurrentAccount(context) } catch (_: Exception) { accountStorageError = true }
            }
            refreshProfile(context)
        }
    }

    fun logout(context: Context) {
        try {
            val retained = savedAccounts.filterNot { it.id == sessionScopeId }
            if (accountStorageError) error("Account storage unavailable")
            AccountVault.write(context, retained)
            savedAccounts = retained
        } catch (_: Exception) {
            accountStorageError = true
            Toast.makeText(context, "Unable to remove saved account. Try again.", Toast.LENGTH_LONG).show()
            return
        }
        PlaybackTracker.stop(sendFinal = false)
        sessionRevision++
        sessionScopeId = java.util.UUID.randomUUID().toString()
        profileJob?.cancel()
        profileJob = null
        profileLoading = false
        profileError = null
        rawCookies = ""
        isLoggedIn = false
        profile = YouTubeUserProfile()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().putString(KEY_SCOPE, sessionScopeId).apply()

        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
    }

    fun getCookies(): String = rawCookies

    fun extractCookie(cookieName: String): String? {
        if (rawCookies.isBlank()) return null
        val parts = rawCookies.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$cookieName=")) {
                return trimmed.removePrefix("$cookieName=").trim()
            }
        }
        return null
    }

    fun getSapisid(): String? {
        return extractCookie("SAPISID")
            ?: extractCookie("__Secure-3PAPISID")
            ?: extractCookie("__Secure-1PAPISID")
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildAuthHeaders(origin: String = "https://www.youtube.com"): Map<String, String> {
        if (!isLoggedIn || rawCookies.isBlank()) return emptyMap()
        val headers = mutableMapOf<String, String>()
        headers["Cookie"] = rawCookies
        headers["Origin"] = origin
        headers["X-Origin"] = origin
        headers["X-Goog-AuthUser"] = "0"

        val epochSec = System.currentTimeMillis() / 1000L
        val authParts = mutableListOf<String>()
        val sapisid = extractCookie("SAPISID")
        val sapisid1p = extractCookie("__Secure-1PAPISID")
        val sapisid3p = extractCookie("__Secure-3PAPISID")

        val primarySapisid = sapisid ?: sapisid3p ?: sapisid1p
        if (!primarySapisid.isNullOrBlank()) {
            authParts.add("SAPISIDHASH ${epochSec}_${sha1("$epochSec $primarySapisid $origin")}")
        }
        if (!sapisid1p.isNullOrBlank()) {
            authParts.add("SAPISID1PHASH ${epochSec}_${sha1("$epochSec $sapisid1p $origin")}")
        }
        if (!sapisid3p.isNullOrBlank()) {
            authParts.add("SAPISID3PHASH ${epochSec}_${sha1("$epochSec $sapisid3p $origin")}")
        }
        if (authParts.isNotEmpty()) {
            headers["Authorization"] = authParts.joinToString(" ")
        }
        return headers
    }

    fun writeNetscapeCookiesFile(cacheDir: File): File? {
        if (rawCookies.isBlank()) return null
        return runCatching {
            val file = File(cacheDir, "yt_cookies_${System.currentTimeMillis()}.txt")
            val sb = StringBuilder()
            sb.append("# Netscape HTTP Cookie File\n")
            sb.append("# Generated by FlufPlay YouTubeAuth\n\n")

            val pairs = rawCookies.split(";")
            val expiry = (System.currentTimeMillis() / 1000L) + 31536000L // 1 year expiry

            for (pair in pairs) {
                val trimmed = pair.trim()
                if (trimmed.isEmpty()) continue
                val idx = trimmed.indexOf('=')
                if (idx > 0) {
                    val key = trimmed.substring(0, idx).trim()
                    val value = trimmed.substring(idx + 1).trim()
                    if (key.isNotEmpty()) {
                        val isSecure = key.startsWith("__Secure-") || key == "SAPISID"
                        sb.append(".youtube.com\tTRUE\t/\t${if (isSecure) "TRUE" else "FALSE"}\t$expiry\t$key\t$value\n")
                    }
                }
            }
            file.writeText(sb.toString())
            file
        }.getOrNull()
    }

    fun refreshProfile(context: Context) {
        if (!isLoggedIn || rawCookies.isBlank()) {
            profileJob?.cancel()
            profileJob = null
            profileLoading = false
            profileError = null
            return
        }

        profileJob?.cancel()
        val revision = sessionRevision
        val requestId = ++profileRequestId
        val appContext = context.applicationContext
        profileLoading = true
        profileError = null

        fun isCurrent(): Boolean = requestId == profileRequestId && revision == sessionRevision && isLoggedIn

        suspend fun reportFailure(message: String) {
            withContext(Dispatchers.Main.immediate) {
                if (isCurrent()) profileError = message
            }
        }

        profileJob = scope.launch {
            try {
                val authHeaders = buildAuthHeaders()
                val body = JSONObject().put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", "2.20240101.00.00")))
                val reqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/account/account_menu")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))

                authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        reportFailure("YouTube couldn't load profile details (HTTP ${response.code}).")
                        return@use
                    }
                    val resString = response.body?.string().orEmpty()
                    if (resString.isBlank()) {
                        reportFailure("YouTube returned an empty profile response.")
                        return@use
                    }

                    val json = JSONObject(resString)
                    val actions = json.optJSONArray("actions")
                    val header = actions?.optJSONObject(0)
                        ?.optJSONObject("openPopupAction")
                        ?.optJSONObject("popup")
                        ?.optJSONObject("multiPageMenuRenderer")
                        ?.optJSONObject("header")
                        ?.optJSONObject("activeAccountHeaderRenderer")

                    if (header == null) {
                        reportFailure("YouTube didn't return profile details.")
                        return@use
                    }

                    val accountName = header.optJSONObject("accountName")
                    val name = accountName?.optString("simpleText").orEmpty().ifBlank {
                        accountName?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    }
                    val handle = header.optJSONObject("email")?.optString("simpleText").orEmpty().ifBlank {
                        val channelHandle = header.optJSONObject("channelHandle")
                        channelHandle?.optString("simpleText").orEmpty().ifBlank {
                            channelHandle?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                        }
                    }
                    val thumbs = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
                    val avatarUrl = thumbs?.let {
                        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                    }

                    if (name.isBlank() && avatarUrl.isNullOrBlank()) {
                        reportFailure("YouTube returned no profile details.")
                        return@use
                    }

                    withContext(Dispatchers.Main.immediate) {
                        if (!isCurrent()) return@withContext
                        val updated = YouTubeUserProfile(
                            name = name.ifBlank { profile.name },
                            emailOrHandle = handle.ifBlank { profile.emailOrHandle },
                            avatarUrl = avatarUrl ?: profile.avatarUrl
                        )
                        profile = updated
                        profileError = null
                        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putString(KEY_NAME, updated.name)
                            .putString(KEY_EMAIL, updated.emailOrHandle)
                            .putString(KEY_AVATAR, updated.avatarUrl)
                            .apply()
                    }
                    if (!accountStorageError) {
                        try {
                            withContext(Dispatchers.IO) {
                                saveCurrentAccount(appContext)
                            }
                        } catch (_: Exception) {
                            accountStorageError = true
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reportFailure("Couldn't reach YouTube to load profile details.")
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (isCurrent()) profileLoading = false
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    onDismiss: () -> Unit,
    addAccount: Boolean = false
) {
    var loading by remember { mutableStateOf(true) }
    var webReady by remember(addAccount) { mutableStateOf(!addAccount) }
    var completed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val previousCookies = remember(addAccount) { if (addAccount) YouTubeAuth.getCookies() else "" }
    DisposableEffect(addAccount) {
        if (addAccount) CookieManager.getInstance().removeAllCookies { webReady = true }
        onDispose {
            if (addAccount && !completed && previousCookies.isNotBlank()) {
                val manager = CookieManager.getInstance()
                manager.removeAllCookies {
                    previousCookies.split(';').map { it.trim() }.filter { it.contains('=') }.forEach {
                        manager.setCookie("https://www.youtube.com", "$it; Domain=.youtube.com; Path=/; Secure")
                    }
                    manager.flush()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Sign in to YouTube",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "For access to membership videos and live chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFF0000)
                    )
                }

                HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)

                // WebView Container
                if (webReady) AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                // Clean Chrome Mobile User-Agent without WebView identifiers to ensure Google Sign-In passes
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    loading = false
                                    val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: ""
                                    val host = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                                    val isYouTube = host == "youtube.com" || host.endsWith(".youtube.com")
                                    val hasAuth = cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=") || cookies.contains("LOGIN_INFO=")

                                    if (isYouTube && hasAuth) {
                                        completed = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            YouTubeAuth.saveAuth(ctx, cookies)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(ctx, "Signed in to YouTube", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        }
                                    }
                                }
                            }

                            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
                        }
                    },
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                    }
                )
            }
        }
    }
}

@Composable
fun YouTubeAccountDialog(
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onAddAccount: () -> Unit
) {
    val profile = YouTubeAuth.profile
    val context = LocalContext.current
    val signedIn = YouTubeAuth.isLoggedIn
    var syncEnabled by remember { mutableStateOf(PlaybackTracker.isSyncEnabled(context)) }

    LaunchedEffect(Unit) { YouTubeAuth.refreshProfile(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1F1F1F),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Account",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Active Profile Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF282828))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF383838))
                                .border(
                                    width = 1.5.dp,
                                    color = if (signedIn) Color(0xFF2BA640) else Color(0xFF444444),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!profile.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile.avatarUrl,
                                    contentDescription = "User avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = Color(0xFFBBBBBB)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                        ) {
                            Text(
                                text = profile.name.ifBlank { if (signedIn) "YouTube user" else "Not signed in" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (profile.emailOrHandle.isNotBlank()) {
                                Text(
                                    text = profile.emailOrHandle,
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontSize = 13.sp,
                                    color = Color(0xFFAAAAAA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (YouTubeAuth.profileLoading) {
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (profile.name.isBlank() && profile.avatarUrl.isNullOrBlank()) "Loading profile…" else "Refreshing profile…",
                                        modifier = Modifier.padding(start = 6.dp),
                                        fontSize = 11.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }
                        }
                    }

                    YouTubeAuth.profileError?.let { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF3A2424))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = Color(0xFFFFCDD2)
                            )
                            TextButton(
                                onClick = { YouTubeAuth.refreshProfile(context) },
                                enabled = !YouTubeAuth.profileLoading,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Try again", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    if (signedIn) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFF363636)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2BA640),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Signed in on this device",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Membership access depends on your YouTube account.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF9E9E9E)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Accounts Switcher Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF282828))
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "Accounts",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFAAAAAA)
                    )

                    YouTubeAuth.savedAccounts.forEach { account ->
                        val isCurrent = account.id == YouTubeAuth.sessionScopeId && signedIn
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (YouTubeAuth.switchAccount(context, account.id)) onDismiss()
                                    else Toast.makeText(context, "Couldn't switch account", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF383838)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!account.profile.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = account.profile.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                        tint = Color(0xFFBBBBBB)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = account.profile.name.ifBlank { "YouTube account" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (account.profile.emailOrHandle.isNotBlank()) {
                                    Text(
                                        text = account.profile.emailOrHandle,
                                        color = Color(0xFF9E9E9E),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Current account",
                                    tint = Color(0xFF2BA640),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (YouTubeAuth.prepareAddAccount(context)) onAddAccount()
                                else Toast.makeText(context, "Couldn't securely save the current account", Toast.LENGTH_LONG).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF383838)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Add account",
                            modifier = Modifier.padding(start = 12.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    if (YouTubeAuth.accountStorageError) {
                        Text(
                            text = "Saved accounts are unavailable on this device.",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            color = Color(0xFFFFCDD2),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sync Watch Time Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF282828))
                        .clickable {
                            val next = !syncEnabled
                            syncEnabled = next
                            PlaybackTracker.setSyncEnabled(context, next)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Sync watch time",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Send playback and watch-time pings so YouTube recommendations reflect what you watch.",
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = {
                            syncEnabled = it
                            PlaybackTracker.setSyncEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2BA640)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (signedIn) {
                        Surface(
                            onClick = onLogout,
                            shape = CircleShape,
                            color = Color(0xFF382222),
                            contentColor = Color(0xFFFF6B6B),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sign out",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = Color(0xFF303030),
                        contentColor = Color.White,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Close",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
