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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private const val KEY_NAME = "profile_name"
    private const val KEY_EMAIL = "profile_email"
    private const val KEY_AVATAR = "profile_avatar"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    var isLoggedIn by mutableStateOf(false)
        private set

    var profile by mutableStateOf(YouTubeUserProfile())
        private set

    private var rawCookies: String = ""

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rawCookies = prefs.getString(KEY_COOKIES, "").orEmpty()
        val name = prefs.getString(KEY_NAME, "").orEmpty()
        val email = prefs.getString(KEY_EMAIL, "").orEmpty()
        val avatar = prefs.getString(KEY_AVATAR, null)
        profile = YouTubeUserProfile(name, email, avatar)
        isLoggedIn = hasValidAuthCookies(rawCookies)

        if (isLoggedIn && (name.isBlank() || avatar.isNullOrBlank())) {
            refreshProfile(context)
        }
    }

    private fun hasValidAuthCookies(cookies: String): Boolean {
        if (cookies.isBlank()) return false
        return cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=") || cookies.contains("LOGIN_INFO=")
    }

    fun saveAuth(context: Context, cookies: String, name: String? = null, email: String? = null, avatarUrl: String? = null) {
        rawCookies = cookies
        isLoggedIn = hasValidAuthCookies(cookies)
        profile = YouTubeUserProfile(
            name = name ?: profile.name,
            emailOrHandle = email ?: profile.emailOrHandle,
            avatarUrl = avatarUrl ?: profile.avatarUrl
        )

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_COOKIES, cookies)
            .putString(KEY_NAME, profile.name)
            .putString(KEY_EMAIL, profile.emailOrHandle)
            .putString(KEY_AVATAR, profile.avatarUrl)
            .apply()

        if (isLoggedIn) {
            refreshProfile(context)
        }
    }

    fun logout(context: Context) {
        rawCookies = ""
        isLoggedIn = false
        profile = YouTubeUserProfile()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

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

        val sapisid = getSapisid()
        if (!sapisid.isNullOrBlank()) {
            val epochSec = System.currentTimeMillis() / 1000L
            val hash = sha1("$epochSec $sapisid $origin")
            headers["Authorization"] = "SAPISIDHASH ${epochSec}_$hash"
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
        if (!isLoggedIn || rawCookies.isBlank()) return
        scope.launch {
            try {
                val authHeaders = buildAuthHeaders()
                val body = JSONObject().put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", "2.20240101.00.00")))
                val reqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/account/account_menu")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))

                authHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val resString = response.body?.string().orEmpty()
                    if (resString.isBlank()) return@use

                    val json = JSONObject(resString)
                    val actions = json.optJSONArray("actions") ?: return@use
                    val header = actions.optJSONObject(0)
                        ?.optJSONObject("openPopupAction")
                        ?.optJSONObject("popup")
                        ?.optJSONObject("multiPageMenuRenderer")
                        ?.optJSONObject("header")
                        ?.optJSONObject("activeAccountHeaderRenderer") ?: return@use

                    val name = header.optJSONObject("accountName")?.optString("simpleText")
                        ?: header.optJSONObject("accountName")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    val email = header.optJSONObject("email")?.optString("simpleText")
                        ?: header.optJSONObject("channelHandle")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
                    val thumbs = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
                    val avatarUrl = thumbs?.let {
                        if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null
                    }

                    if (name.isNotBlank() || !avatarUrl.isNullOrBlank()) {
                        profile = YouTubeUserProfile(name, email, avatarUrl)
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString(KEY_NAME, name)
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_AVATAR, avatarUrl)
                            .apply()
                    }
                }
            } catch (_: Exception) {
                // Silently ignore network failures in background profile sync
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    onDismiss: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }

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
                            "Masuk ke YouTube",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Untuk akses video Membership & Live Chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
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
                AndroidView(
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
                                    val isYouTube = url?.contains("youtube.com") == true
                                    val hasAuth = cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=") || cookies.contains("LOGIN_INFO=")

                                    if (isYouTube && hasAuth) {
                                        YouTubeAuth.saveAuth(ctx, cookies)
                                        Toast.makeText(ctx, "Berhasil masuk ke akun YouTube", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            }

                            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun YouTubeAccountDialog(
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val profile = YouTubeAuth.profile

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Akun YouTube Terhubung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!profile.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = "Avatar Pengguna",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF0F9D58), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = profile.name.ifBlank { "Pengguna YouTube" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (profile.emailOrHandle.isNotBlank()) {
                    Text(
                        text = profile.emailOrHandle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF003314))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF0F9D58),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Status Login Aktif: Siap akses konten Membership & Badge Live Chat",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB9F6CA)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onLogout,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Keluar (Logout)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        },
        containerColor = Color(0xFF222222),
        shape = RoundedCornerShape(16.dp)
    )
}
