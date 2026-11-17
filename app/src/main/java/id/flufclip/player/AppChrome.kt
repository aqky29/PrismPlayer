package id.flufclip.player

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Bar atas feed: kolom pencarian saat [searchActive], selain itu tombol kembali + judul pencarian / bagikan channel / logo. */
@Composable
internal fun FeedTopBar(
    searchActive: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    searchTitle: String?,
    onEditSearch: () -> Unit,
    onClearSearch: (() -> Unit)? = null,
    onVoiceSearchResult: ((String) -> Unit)? = null,
    activeSearchFilter: String = "all",
    onSelectSearchFilter: ((String) -> Unit)? = null,
    channelShareUrl: String?,
    channelName: String? = null,
    channelTitleVisible: Boolean = true,
    channelSearchActive: Boolean = false,
    channelSearchQuery: String = "",
    onChannelSearchQueryChange: ((String) -> Unit)? = null,
    onToggleChannelSearch: ((Boolean) -> Unit)? = null,
    onSubmitChannelSearch: (() -> Unit)? = null,
    onStartSearch: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                if (onVoiceSearchResult != null) {
                    onVoiceSearchResult(spoken)
                } else {
                    onQueryChange(TextFieldValue(spoken, TextRange(spoken.length)))
                    onSearch()
                }
            }
        }
    }
    val triggerVoiceSearch: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { Toast.makeText(context, "Voice search is unavailable on this device", Toast.LENGTH_SHORT).show() }
    }
    if (searchActive) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCloseSearch,
                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Surface)
                    .padding(start = 14.dp, end = if (query.text.isNotEmpty()) 2.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.text.isEmpty()) {
                        Text(
                            text = "Search YouTube",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (query.text.isNotEmpty()) {
                    SearchPillClearButton { onQueryChange(TextFieldValue("", TextRange.Zero)) }
                }
            }
            Spacer(Modifier.width(4.dp))
            if (query.text.isEmpty()) {
                SearchMicButton(onClick = triggerVoiceSearch)
            } else {
                IconButton(
                    onClick = onSearch,
                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextPrimary,
                    )
                }
            }
        }
    } else if (channelShareUrl != null && channelSearchActive) {
        val channelFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { channelFocusRequester.requestFocus(); keyboard?.show() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { keyboard?.hide(); onToggleChannelSearch?.invoke(false) },
                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close channel search", tint = TextPrimary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Surface)
                    .padding(start = 14.dp, end = if (channelSearchQuery.isNotEmpty()) 2.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (channelSearchQuery.isEmpty()) {
                        Text(
                            text = "Search ${channelName?.takeIf { it.isNotBlank() } ?: "channel"}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = channelSearchQuery,
                        onValueChange = { onChannelSearchQueryChange?.invoke(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(TextPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            onSubmitChannelSearch?.invoke()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(channelFocusRequester),
                    )
                }
                if (channelSearchQuery.isNotEmpty()) {
                    SearchPillClearButton { onChannelSearchQueryChange?.invoke("") }
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = {
                    keyboard?.hide()
                    onSubmitChannelSearch?.invoke()
                },
                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search in channel", tint = TextPrimary)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
            }
            if (searchTitle != null) {
                var filterMenuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Surface)
                        .clickable(role = Role.Button, onClick = onEditSearch)
                        .padding(start = 14.dp, end = if (onClearSearch != null) 2.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = searchTitle,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onClearSearch != null) {
                        SearchPillClearButton(onClick = onClearSearch)
                    }
                }
                Spacer(Modifier.width(4.dp))
                SearchMicButton(onClick = triggerVoiceSearch)
                Box {
                    IconButton(
                        onClick = { filterMenuOpen = true },
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Search filters",
                            tint = TextPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = filterMenuOpen,
                        onDismissRequest = { filterMenuOpen = false },
                        modifier = Modifier.background(SurfaceElevated),
                    ) {
                        Text(
                            text = "Search filter",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        SEARCH_FILTER_CHIPS.forEach { (filterValue, filterLabel) ->
                            val selected = activeSearchFilter == filterValue
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = filterLabel,
                                        color = if (selected) TextPrimary else TextSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                trailingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp)) }
                                } else null,
                                onClick = {
                                    filterMenuOpen = false
                                    onSelectSearchFilter?.invoke(filterValue)
                                },
                            )
                        }
                    }
                }
            } else if (channelShareUrl != null) {
                val titleAlpha by animateFloatAsState(
                    targetValue = if (channelTitleVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 160),
                    label = "channelTopBarTitleAlpha"
                )
                Text(
                    text = channelName?.takeIf { it.isNotBlank() } ?: "Channel",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 4.dp)
                        .graphicsLayer { alpha = titleAlpha }
                )
                if (onToggleChannelSearch != null) {
                    IconButton(
                        onClick = { onToggleChannelSearch(true) },
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search channel", tint = TextPrimary)
                    }
                }
                var channelMenuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { channelMenuOpen = true },
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, "Channel options", tint = TextPrimary)
                    }
                    DropdownMenu(
                        expanded = channelMenuOpen,
                        onDismissRequest = { channelMenuOpen = false },
                        modifier = Modifier.background(SurfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share channel", color = TextPrimary) },
                            leadingIcon = { Icon(painterResource(R.drawable.yt_w_share), null, tint = TextPrimary, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                channelMenuOpen = false
                                runCatching {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, channelShareUrl)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share channel"))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy channel link", color = TextPrimary) },
                            leadingIcon = { Icon(painterResource(R.drawable.yt_w_copy), null, tint = TextPrimary, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                channelMenuOpen = false
                                runCatching {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("Channel link", channelShareUrl))
                                    Toast.makeText(context, "Channel link copied", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in browser", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextPrimary, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                channelMenuOpen = false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channelShareUrl))) }
                            }
                        )
                    }
                }
            } else {
                Box(Modifier.padding(start = 4.dp).size(width = 34.dp, height = 24.dp).background(Accent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(
                    "FlufPlay",
                    modifier = Modifier.padding(start = 7.dp).weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = YouTubeSansBold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp
                    )
                )
                if (onStartSearch != null) {
                    IconButton(onClick = onStartSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPillClearButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Clear search",
            tint = TextPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SearchMicButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.yt_mic),
                contentDescription = "Voice search",
                tint = TextPrimary,
                modifier = Modifier.size(21.dp),
            )
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

/** Riwayat pencarian (cocok dengan [query]) lalu saran online yang belum ada di riwayat. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchSuggestionList(
    query: String,
    historyEntries: List<SearchHistoryEntry>,
    suggestions: List<String>,
    onSearch: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteTerm by remember { mutableStateOf<String?>(null) }
    val recent = remember(historyEntries, query) {
        historyEntries.filter { query.isBlank() || it.term.contains(query, ignoreCase = true) }
    }
    val online = remember(suggestions, recent) {
        suggestions.filterNot { suggestion -> recent.any { it.term.equals(suggestion, ignoreCase = true) } }
    }

    pendingDeleteTerm?.let { termToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTerm = null },
            title = {
                Text(
                    text = termToDelete,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            },
            text = {
                Text(
                    text = "Remove from search history?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveHistory(termToDelete)
                        pendingDeleteTerm = null
                    },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Remove", color = CallToActionBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteTerm = null },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = SurfaceElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            shape = RoundedCornerShape(16.dp),
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        items(recent, key = { "history:${it.term}" }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onSearch(entry.term) },
                        onLongClick = { pendingDeleteTerm = entry.term },
                    )
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.yt_w_history),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 12.dp),
                ) {
                    Text(
                        text = entry.term,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                    )
                    if (!entry.subtitle.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (entry.hasNewVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CallToActionBlue),
                                )
                            }
                            Text(
                                text = entry.subtitle,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            )
                        }
                    }
                }
                if (!entry.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Surface),
                    )
                } else if (!entry.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(width = 64.dp, height = 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Surface),
                    )
                }
                IconButton(
                    onClick = { onPickSuggestion(entry.term) },
                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Fill search box with ${entry.term}",
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(rotationZ = 45f),
                    )
                }
            }
        }
        items(online, key = { "online:$it" }) { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onSearch(term) }
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
                val q = query.trim()
                val idx = if (q.isNotEmpty()) term.indexOf(q, ignoreCase = true) else -1
                val annotated = remember(term, q) {
                    if (idx >= 0) {
                        buildAnnotatedString {
                            append(term.substring(0, idx))
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = TextSecondary)) {
                                append(term.substring(idx, idx + q.length))
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                                append(term.substring(idx + q.length))
                            }
                        }
                    } else {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                                append(term)
                            }
                        }
                    }
                }
                Text(
                    text = annotated,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                    ),
                )
                IconButton(
                    onClick = { onPickSuggestion(term) },
                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Fill search box with $term",
                        tint = TextPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer(rotationZ = 45f),
                    )
                }
            }
        }
    }
}
