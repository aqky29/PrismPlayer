"""Fase 3.4: tujuh var riwayat akun (accountEntries, accountLoading, ...) dikumpulkan ke AccountHistoryState. Logika tetap di App()."""
from _patch import Patch

p = Patch('AppRoot.kt')
p.install('AccountHistoryState.kt.txt', 'AccountHistoryState.kt')
p.between('    var accountEntries by remember(', '    var deviceHistory by remember(',
          '    val accountHistory = remember(accountHistorySession, accountHistoryLoggedIn) { AccountHistoryState(accountHistoryLoggedIn) }\n')
p.rep('    var deviceHistory by remember(accountHistorySession, accountHistoryLoggedIn) { mutableStateOf(false) }\n', '')
renames = {
    'accountEntries': ('entries', 26), 'accountContinuation': ('continuation', 5), 'accountLoading': ('loading', 7),
    'accountError': ('error', 6), 'accountRequest': ('request', 4), 'accountNextRequest': ('nextRequest', 3),
    'deviceHistory': ('deviceHistory', 5),
}
for old, (new, count) in renames.items():
    p.sub(r'(?<![.\w])' + old + r'\b', 'accountHistory.' + new, expected=count)
p.save()
