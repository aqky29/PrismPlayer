"""Fase 3.1: riwayat kata pencarian (`history` + `preferences`) menjadi SearchHistoryStore."""
from _patch import Patch

p = Patch('AppRoot.kt')
p.install('SearchHistoryStore.kt.txt', 'SearchHistoryStore.kt')
p.rep('''    val preferences = remember { context.getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    var history by remember { mutableStateOf(runCatching {
        val json = org.json.JSONArray(preferences.getString("recent", "[]"))
        (0 until json.length()).map { json.getString(it) }
    }.getOrDefault(emptyList())) }
''', '''    val searchHistory = remember { SearchHistoryStore(context.getSharedPreferences("search_history", Context.MODE_PRIVATE)) }
''')
p.rep('''        history = (listOf(term) + history.filterNot { it.equals(term, ignoreCase = true) }).take(20)
        preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply()
''', '''        searchHistory.add(term)
''')
p.rep('onRemoveHistory = { term -> history = history.filterNot { it == term }; preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply() }',
      'onRemoveHistory = { searchHistory.remove(it) }')
p.rep('query = query, history = history, suggestions', 'query = query, history = searchHistory.terms, suggestions')
p.sub(r'(HomeRecommendations\.query\(watchHistory, topics, [^)]*?), history\)', r'\1, searchHistory.terms)', expected=5)
p.save()
