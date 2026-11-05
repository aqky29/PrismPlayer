"""Fase 3.3: video/channel yang disembunyikan (hiddenVideos, hiddenChannels, recommendationPrefs) menjadi HiddenFeedItems."""
from _patch import Patch

p = Patch('AppRoot.kt')
p.install('HiddenFeedItems.kt.txt', 'HiddenFeedItems.kt')
p.rep('''    val recommendationPrefs = remember { context.getSharedPreferences("home_recommendations", Context.MODE_PRIVATE) }
    var hiddenVideos by remember { mutableStateOf(HomeRecommendations.read(recommendationPrefs, "videos")) }
    var hiddenChannels by remember { mutableStateOf(HomeRecommendations.read(recommendationPrefs, "channels")) }
''', '''    val hidden = remember { HiddenFeedItems(context.getSharedPreferences("home_recommendations", Context.MODE_PRIVATE)) }
''')
p.sub(r'hiddenVideos = HomeRecommendations\.hide\(recommendationPrefs, "videos", ([^)]+)\)', r'hidden.hideVideo(\1)', expected=3)
p.sub(r'hiddenChannels = HomeRecommendations\.hide\(recommendationPrefs, "channels", ([^)]+)\)', r'hidden.hideChannel(\1)', expected=1)
p.rep('{{ recommendationPrefs.edit().clear().apply(); hiddenVideos = emptySet(); hiddenChannels = emptySet() }}', '{{ hidden.reset() }}')
p.sub(r'\bhiddenVideos\b', 'hidden.videos', expected=14)
p.sub(r'\bhiddenChannels\b', 'hidden.channels', expected=12)
if 'recommendationPrefs' in p.text:
    from _patch import fail
    fail('recommendationPrefs is still referenced')
p.save()
