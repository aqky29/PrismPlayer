package id.flufclip.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AccountLibrarySection(
    cardWidth: Dp = 164.dp,
    onOpen: (AccountPlaylist) -> Unit,
    onSignIn: () -> Unit
) {
    val revision = YouTubeAuth.sessionRevision
    val loggedIn = YouTubeAuth.isLoggedIn
    val listThumbnailWidth = (LocalConfiguration.current.screenWidthDp.dp * .34f).coerceIn(124.dp, 188.dp)
    var playlists by remember(revision) { mutableStateOf<List<AccountPlaylist>>(emptyList()) }
    var continuation by remember(revision) { mutableStateOf<String?>(null) }
    var loading by remember(revision) { mutableStateOf(false) }
    var error by remember(revision) { mutableStateOf<String?>(null) }
    var retry by remember(revision) { mutableIntStateOf(0) }
    var more by remember(revision) { mutableIntStateOf(0) }
    var showAllPlaylists by remember(revision) { mutableStateOf(false) }

    LaunchedEffect(revision, loggedIn, retry) {
        if (!loggedIn) return@LaunchedEffect
        loading = true; error = null
        try { val page = AccountLibrary.playlists(); playlists = page.playlists; continuation = page.continuation }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load your YouTube library." }
        finally { loading = false }
    }
    LaunchedEffect(revision, more) {
        val token = continuation
        if (more == 0 || token == null || loading || !loggedIn) return@LaunchedEffect
        loading = true; error = null
        try {
            val page = AccountLibrary.morePlaylists(token)
            playlists = (playlists + page.playlists).distinctBy { it.id }
            continuation = page.continuation
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load more playlists." }
        finally { loading = false }
    }

    Column(Modifier.fillMaxWidth()) {
        SectionHeaderRow(
            title = "Playlists",
            subtitle = if (loggedIn) "Liked, Watch later & saved collections" else null,
            actionText = if (loggedIn && playlists.isNotEmpty()) {
                if (showAllPlaylists) "Show shelf" else "View all"
            } else null,
            onAction = if (loggedIn && playlists.isNotEmpty()) {
                { showAllPlaylists = !showAllPlaylists }
            } else null,
            modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
        )

        when {
            !loggedIn -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A))
                        .clickable(onClick = onSignIn)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.yt_w_save),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFFAAAAAA)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Sign in to see your playlists",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Liked videos, Watch later, and saved playlists",
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                    YouTubePillButton(text = "Sign in", onClick = onSignIn)
                }
            }
            loading && playlists.isEmpty() -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Text(
                        text = "Loading playlists…",
                        modifier = Modifier.padding(start = 12.dp),
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            error != null && playlists.isEmpty() -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error.orEmpty(),
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFCCCCCC),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(12.dp))
                    YouTubePillButton(text = "Try again", onClick = { retry++ })
                }
            }
            !showAllPlaylists -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistShelfCard(
                            playlist = playlist,
                            width = cardWidth,
                            onClick = { onOpen(playlist) }
                        )
                    }
                    if (continuation != null) {
                        item(key = "load_more_playlists_card") {
                            Box(
                                modifier = Modifier
                                    .width(132.dp)
                                    .padding(top = 8.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1F1F1F))
                                    .clickable(enabled = !loading) { more++ },
                                contentAlignment = Alignment.Center
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        text = if (error != null) "Try again" else "Load more",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                playlists.forEach { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(playlist) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistStackedThumbnail(
                            playlist = playlist,
                            modifier = Modifier.width(listThumbnailWidth)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp, end = 8.dp)
                        ) {
                            Text(
                                text = playlist.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            if (playlist.subtitle.isNotBlank()) {
                                Text(
                                    text = playlist.subtitle,
                                    modifier = Modifier.padding(top = 2.dp),
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF777777),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (continuation != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        YouTubePillButton(
                            text = if (loading) "Loading…" else if (error != null) "Try again" else "Load more playlists",
                            onClick = { more++ },
                            enabled = !loading
                        )
                    }
                }
            }
        }
        if (playlists.isNotEmpty() && error != null) {
            Text(
                text = error.orEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = Color(0xFFFFB4AB),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlaylistShelfCard(
    playlist: AccountPlaylist,
    width: Dp,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick)
    ) {
        PlaylistStackedThumbnail(
            playlist = playlist,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = playlist.title,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        if (playlist.subtitle.isNotBlank()) {
            Text(
                text = playlist.subtitle,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF9E9E9E),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun PlaylistStackedThumbnail(
    playlist: AccountPlaylist,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    progressFraction: Float? = null
) {
    val fallbackBrush = when (playlist.id) {
        "LL" -> Brush.linearGradient(listOf(Color(0xFF2B3A4A), Color(0xFF1A242F)))
        "WL" -> Brush.linearGradient(listOf(Color(0xFF1E354D), Color(0xFF132232)))
        else -> Brush.linearGradient(listOf(Color(0xFF2E2E2E), Color(0xFF1F1F1F)))
    }
    val badgeIconRes = when (playlist.id) {
        "LL" -> R.drawable.yt_w_like
        "WL" -> R.drawable.yt_w_history
        else -> R.drawable.yt_w_save
    }
    val resolvedBadgeText = badgeText?.takeIf { it.isNotBlank() } ?: when (playlist.id) {
        "LL" -> "Liked"
        "WL" -> "Watch later"
        else -> null
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top stack bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(Color(0xFF383838))
        )
        Spacer(Modifier.height(1.dp))
        // Middle stack bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(4.dp)
                .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                .background(Color(0xFF4C4C4C))
        )
        Spacer(Modifier.height(1.dp))
        // Main 16:9 thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(fallbackBrush),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = playlist.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(badgeIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = Color(0xFFD0D0D0)
                )
            }
            // Subtle bottom-right playlist badge
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(badgeIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
                if (!resolvedBadgeText.isNullOrBlank()) {
                    Text(
                        text = resolvedBadgeText,
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            if (progressFraction != null && progressFraction > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x55FFFFFF))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .background(Color(0xFFFF0033))
                    )
                }
            }
        }
    }
}

@Composable
internal fun SavePlaylistDialog(videoId: String, onDismiss: () -> Unit, onSelectedPlaylists: (Set<String>) -> Unit) {
    val scope = rememberCoroutineScope()
    val revision = YouTubeAuth.sessionRevision
    var choices by remember(videoId, revision) { mutableStateOf<List<PlaylistChoice>>(emptyList()) }
    var loading by remember(videoId, revision) { mutableStateOf(false) }
    var busy by remember(videoId, revision) { mutableStateOf(false) }
    var error by remember(videoId, revision) { mutableStateOf<String?>(null) }
    var refresh by remember(videoId, revision) { mutableIntStateOf(0) }
    var creating by remember(videoId) { mutableStateOf(false) }
    var title by remember(videoId) { mutableStateOf("") }
    var privacy by remember(videoId) { mutableStateOf("PRIVATE") }
    LaunchedEffect(videoId, revision, refresh) {
        loading = true; error = null
        try { choices = AccountLibrary.choices(videoId); onSelectedPlaylists(choices.filter { it.selected }.map { it.id }.toSet()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Couldn't load playlists" }
        finally { loading = false }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF282828)).padding(vertical = 16.dp)) {
            Text(if (creating) "New playlist" else "Save video to…", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (creating) {
                OutlinedTextField(title, { title = it.take(150) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), label = { Text("Title") }, singleLine = true)
                listOf("PRIVATE" to "Private", "UNLISTED" to "Unlisted", "PUBLIC" to "Public").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clickable { privacy = value }.height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = privacy == value, onClick = null); Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { creating = false }, enabled = !busy) { Text("Cancel") }
                    TextButton(onClick = {
                        busy = true; error = null
                        scope.launch {
                            try { AccountLibrary.create(title, privacy, videoId, revision); creating = false; refresh++ }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Couldn't create playlist" }
                            finally { busy = false }
                        }
                    }, enabled = title.isNotBlank() && !busy) { Text("Create") }
                }
            } else {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.heightIn(max = 360.dp)) {
                    LazyColumn {
                        items(choices, key = { it.id }) { choice ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy && !loading) {
                                busy = true; error = null
                                // Tampilkan perubahan segera; kembalikan dan baca ulang server hanya jika permintaan gagal.
                                // Reload setelah sukses dihilangkan karena respons baca ulang bisa menimpa centang.
                                choices = choices.map { if (it.id == choice.id) it.copy(selected = !choice.selected) else it }
                                onSelectedPlaylists(choices.filter { it.selected }.map { it.id }.toSet())
                                scope.launch {
                                    try { AccountLibrary.setChoice(videoId, choice, revision) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) {
                                        choices = choices.map { if (it.id == choice.id) it.copy(selected = choice.selected) else it }
                                        onSelectedPlaylists(choices.filter { it.selected }.map { it.id }.toSet())
                                        error = e.message ?: "Couldn't update playlist"
                                        // Hasil ambigu: baca ulang server, jangan kirim ulang tindakan secara buta.
                                        refresh++
                                    }
                                    finally { busy = false }
                                }
                            }.heightIn(min = 52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = choice.selected, onCheckedChange = null)
                                Text(choice.title, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                TextButton(onClick = { creating = true; error = null }, enabled = !busy, modifier = Modifier.padding(start = 12.dp)) { Icon(Icons.Default.Add, null); Text("New playlist", Modifier.padding(start = 8.dp)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                    if (error != null) TextButton(onClick = { refresh++ }, enabled = !busy) { Text("Retry") }
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") }
                }
            }
            if (error != null) Text(error.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
        }
    }
}
