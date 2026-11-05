package id.flufclip.player

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Bar atas feed: kolom pencarian saat [searchActive], selain itu tombol kembali + judul pencarian / bagikan channel / logo. */
@Composable
internal fun FeedTopBar(
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    searchTitle: String?,
    onEditSearch: () -> Unit,
    channelShareUrl: String?,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (searchActive) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCloseSearch) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            TextField(value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f).focusRequester(focusRequester), placeholder = { Text("Telusuri YouTube") }, singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Delete") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }), shape = RoundedCornerShape(50))
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            if (searchTitle != null) {
                Text(searchTitle, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Surface).clickable { onEditSearch() }.padding(horizontal = 16.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (channelShareUrl != null) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, channelShareUrl)
                        }
                        context.startActivity(Intent.createChooser(intent, "Bagikan Channel"))
                    }
                }) { Icon(Icons.Default.MoreVert, "Lainnya") }
            } else {
                Box(Modifier.size(width = 34.dp, height = 24.dp).background(Accent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text("FlufPlay", modifier = Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            // Tombol Search sudah berada di Floating Bottom Navigation; AppBar atas bersih tanpa ikon Search ganda.
        }
    }
}

/** Tab Home / Subs / Music / Live / More di atas beranda. Memetakan tab ke tab utama, kategori, atau chip. */
@Composable
internal fun HomeTopTabs(
    mainTab: String,
    category: String,
    homeChipTitle: String,
    visibleChips: List<HomeChip>,
    selectTab: (String) -> Unit,
    selectCategory: (String) -> Unit,
    selectChip: (HomeChip) -> Unit,
) {
    val currentActiveTab = when {
        mainTab == "subscriptions" -> "Subs"
        category == "Music" -> "Music"
        category == "Live" -> "Live"
        category != homeChipTitle -> "More"
        else -> "Home"
    }
    TopCategoryTabs(
        activeTab = currentActiveTab,
        moreCategories = visibleChips.filterNot { it.title in listOf("All", "Music", "Live", homeChipTitle) },
        onSelectTab = { tab ->
            when (tab) {
                "Home" -> {
                    if (mainTab != "home") selectTab("home")
                    if (category != homeChipTitle) selectCategory(homeChipTitle)
                }
                "Subs" -> {
                    selectTab("subscriptions")
                }
                "Music" -> {
                    if (mainTab != "home") selectTab("home")
                    selectCategory("Music")
                }
                "Live" -> {
                    if (mainTab != "home") selectTab("home")
                    selectCategory("Live")
                }
            }
        },
        onSelectMoreChip = { chip ->
            if (mainTab != "home") selectTab("home")
            selectChip(chip)
        }
    )
}

/** Status unduhan yang sedang berjalan atau baru selesai, tampil di atas feed. */
@Composable
internal fun DownloadStatusBanner(state: DownloadState, onOpen: (SavedDownload) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.message, style = MaterialTheme.typography.bodySmall)
            if (state.running) {
                val value = state.progress
                if (value == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                else LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        if (state.running) TextButton(onClick = { context.startService(Intent(context, DownloadService::class.java).setAction("cancel")) }) { Text("Cancel") }
        else {
            state.completed?.let { saved -> TextButton(onClick = { onOpen(saved) }) { Text("Open") } }
            TextButton(onClick = { DownloadStatus.state.value = DownloadState() }) { Text("Close") }
        }
    }
}

/** Riwayat pencarian (cocok dengan [query]) lalu saran online yang belum ada di riwayat. */
@Composable
internal fun SearchSuggestionList(
    query: String,
    history: List<String>,
    suggestions: List<String>,
    onSearch: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent = history.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
    val online = suggestions.filterNot { suggestion -> recent.any { it.equals(suggestion, ignoreCase = true) } }
    LazyColumn(modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(recent) { term ->
            Row(Modifier.fillMaxWidth().clickable { onSearch(term) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.yt_w_history), contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                Text(term, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onRemoveHistory(term) }) { Icon(Icons.Default.Close, "Remove from history", tint = Color.LightGray, modifier = Modifier.size(18.dp)) }
            }
        }
        items(online) { term ->
            Row(Modifier.fillMaxWidth().clickable { onSearch(term) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                val q = query.trim()
                val idx = if (q.isNotEmpty()) term.indexOf(q, ignoreCase = true) else -1
                val annotated = remember(term, q) {
                    if (idx >= 0) {
                        buildAnnotatedString {
                            append(term.substring(0, idx))
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color.LightGray)) {
                                append(term.substring(idx, idx + q.length))
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                append(term.substring(idx + q.length))
                            }
                        }
                    } else {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                append(term)
                            }
                        }
                    }
                }
                Text(annotated, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onPickSuggestion(term) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Pilih saran", tint = Color.LightGray, modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = -45f))
                }
            }
        }
    }
}
