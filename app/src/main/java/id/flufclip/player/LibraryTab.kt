package id.flufclip.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun LibraryTab(modifier: Modifier = Modifier, tab: String, videos: List<WatchedVideo>, onHistory: () -> Unit, onOpenVideo: (WatchedVideo) -> Unit, onDownloads: () -> Unit, onAccount: () -> Unit, shortsLoading: Boolean, shortsError: String?, onSearchShorts: () -> Unit) {
    when (tab) {
        "you" -> LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = onAccount).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val profile = YouTubeAuth.profile
                    if (!profile.avatarUrl.isNullOrBlank()) AsyncImage(profile.avatarUrl, null, Modifier.size(64.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.AccountCircle, null, Modifier.size(64.dp), tint = Color.LightGray)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(profile.name.ifBlank { if (YouTubeAuth.isLoggedIn) "Akun YouTube" else "Anda" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (YouTubeAuth.isLoggedIn) "Lihat akun" else "Login ke YouTube", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onAccount, label = { Text("Akun") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) })
                    AssistChip(onClick = onDownloads, label = { Text("Unduhan") }, leadingIcon = { Text("↓") })
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Riwayat", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onHistory) { Text("Lihat semua", color = Color.White) }
                }
            }
            if (videos.isEmpty()) item { Text("Video yang ditonton di perangkat ini akan muncul di sini.", Modifier.padding(16.dp), color = Color.LightGray) }
            else item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(videos.take(10), key = { it.url }) { video ->
                        Column(Modifier.width(165.dp).clickable { onOpenVideo(video) }) {
                            AsyncImage(video.thumbnail, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Surface), contentScale = ContentScale.Crop)
                            Text(video.title, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(video.uploader, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 20.dp), color = Surface)
                LibraryRow("Riwayat tontonan", onHistory) { Text("◷") }
                LibraryRow("Unduhan", onDownloads) { Text("↓") }
            }
        }
        "subscriptions" -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Menu, null, Modifier.size(56.dp), tint = Color.LightGray)
                Spacer(Modifier.height(20.dp))
                Text("Subscription", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (YouTubeAuth.isLoggedIn) "Daftar subscription YouTube belum tersedia di FlufPlay." else "Login untuk melihat subscription Anda.",
                    Modifier.padding(top = 8.dp), color = Color.LightGray
                )
                if (!YouTubeAuth.isLoggedIn) {
                    Button(onClick = onAccount, modifier = Modifier.padding(top = 16.dp)) { Text("Login") }
                }
            }
        }
        else -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(56.dp), tint = Color.LightGray)
                Spacer(Modifier.height(20.dp))
                Text("Shorts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (shortsLoading) CircularProgressIndicator(Modifier.padding(top = 16.dp))
                else {
                    Text(shortsError ?: "Buka video Shorts untuk mulai menonton.", Modifier.padding(top = 8.dp), color = Color.LightGray)
                    Button(onClick = onSearchShorts, modifier = Modifier.padding(top = 16.dp)) { Text(if (shortsError == null) "Buka Shorts" else "Coba lagi") }
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Text(label, Modifier.padding(start = 24.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
