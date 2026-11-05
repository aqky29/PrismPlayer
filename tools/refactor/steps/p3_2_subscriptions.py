"""Fase 3.2: state + logika tab Subscriptions (subs*, loadSubscriptions, loadMoreSubscriptions, loadMembership) menjadi SubscriptionsState."""
from _patch import Patch

p = Patch('AppRoot.kt')
p.install('SubscriptionsState.kt.txt', 'SubscriptionsState.kt')
p.between('    var subsEntries by remember', '    var shortsRequest by remember', '''    val subs = remember { SubscriptionsState(scope) }
    LaunchedEffect(mainTab, YouTubeAuth.sessionRevision, YouTubeAuth.isLoggedIn) {
        if (mainTab == "subscriptions" && YouTubeAuth.isLoggedIn) subs.onTabOpened()
    }
''')
p.rep('''            subsEntries = emptyList()
            subsChannels = emptyList()
            subsChannelsAttempted = false
            subsMembership = emptyList()
            subsContinuationToken = null
            subsLoading = false
            subsMembershipLoading = false
            subsError = null
            subsEnd = false
''', '''            subs.reset()
''')
p.rep('if (loggedIn && mainTab == "subscriptions") loadSubscriptions()', 'if (loggedIn && mainTab == "subscriptions") subs.load()')
p.rep('''                            entries = subsEntries,
                            channels = subsChannels,
                            membershipEntries = subsMembership,
                            membershipLoading = subsMembershipLoading,''', '''                            entries = subs.entries,
                            channels = subs.channels,
                            membershipEntries = subs.membership,
                            membershipLoading = subs.membershipLoading,''')
p.rep('''                            loading = subsLoading,
                            error = subsError,
                            canLoadMore = !subsEnd,
                            onRefresh = { loadSubscriptions(true) },
                            onLoadMore = { loadMoreSubscriptions() },
                            onLoadMembership = { loadMembership() },''', '''                            loading = subs.loading,
                            error = subs.error,
                            canLoadMore = !subs.end,
                            onRefresh = { subs.load(true) },
                            onLoadMore = { subs.loadMore() },
                            onLoadMembership = { subs.loadMembership() },''')
p.save()
