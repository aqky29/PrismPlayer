"""Fase 2.1: pindahkan bar atas, tab beranda, banner unduhan, dan daftar saran pencarian dari App() ke AppChrome.kt."""
from _patch import Patch

p = Patch('AppRoot.kt')
p.install('AppChrome.kt.txt', 'AppChrome.kt')
p.between('            if (showFeed) {\n                if (searchActive) {', '            val showTopTabs', '''            if (showFeed) FeedTopBar(
                searchActive = searchActive, query = query, onQueryChange = { query = it }, onSearch = { search() },
                onCloseSearch = { searchActive = false; keyboard?.hide() },
                showBack = playlist != null || channel != null || searchMode, onBack = { back() },
                searchTitle = lastSearch.takeIf { searchMode && playlist == null && channel == null },
                onEditSearch = { query = lastSearch; searchActive = true },
                channelShareUrl = channel?.info?.url,
            )
''')
p.between('            if (showTopTabs) {', '            if (downloadState.message.isNotBlank() && showFeed) {',
          '            if (showTopTabs) HomeTopTabs(mainTab, category, homeChipTitle, visibleChips, selectTab, { selectCategory(it) }, { selectChip(it) })\n')
p.between('            if (downloadState.message.isNotBlank() && showFeed) {', '            if (loading && showFeed && (searchMode',
          '            if (downloadState.message.isNotBlank() && showFeed) DownloadStatusBanner(downloadState, onOpen = { saved -> player.pause(); SavedDownloads.open(context, saved) })\n')
p.between('            if (showFeed && searchActive) {', '            } else if (showFeed && mainTab != "home"', '''            if (showFeed && searchActive) {
                SearchSuggestionList(
                    query = query, history = history, suggestions = suggestions,
                    onSearch = { search(it) },
                    onRemoveHistory = { term -> history = history.filterNot { it == term }; preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply() },
                    onPickSuggestion = { query = it },
                    modifier = Modifier.weight(1f),
                )
''')
p.save()
