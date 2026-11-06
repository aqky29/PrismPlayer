# Plan: Memecah `MainActivity.kt`

Dokumen ini adalah analisis dan rencana kerja untuk memecah
`app/src/main/java/id/flufclip/player/MainActivity.kt` (6.378 baris, ±365 KB) menjadi file-file kecil.
Rencana ini ditulis supaya bisa **dikerjakan oleh model yang lebih kecil** (mis. Gemini Flash,
DeepSeek Flash): setiap tugas kecil, berurutan, punya perintah persis, hasil yang diharapkan,
dan cara verifikasi.

> **Status verifikasi:** Fase 1, 2, dan 3 sudah diuji coba penuh pada salinan proyek
> (snapshot working tree 2026-11-06). Setelah setiap tahap, `:app:compileDebugKotlin` lulus,
> dan `:app:testDebugUnitTest` lulus di akhir. Fase 4 belum diuji coba; itu desain dan butuh review.

---

## 0. Ringkasan

| | Sebelum | Setelah Fase 1 | Setelah Fase 3 |
|---|---|---|---|
| `MainActivity.kt` | 6.378 baris | 210 baris (class `MainActivity` 74 baris + blok import; ±90 baris setelah *Optimize Imports*) | 210 baris |
| Fungsi `App()` | 2.713 baris di `MainActivity.kt` | pindah utuh ke `AppRoot.kt` | `AppRoot.kt` 2.616 baris, ditambah 5 file state/UI baru |
| File baru | – | 13 file | 13 + 5 file |

Strategi utamanya adalah memindahkan kode tanpa menulis ulang:

1. **Fase 1 (risiko sangat rendah):** pindahkan 72 deklarasi top-level di luar `App()` ke 12 file
   bertema, lalu `App()` utuh ke `AppRoot.kt`. Kode tidak berubah satu karakter pun, kecuali `private` yang menjadi `internal`.
   Semua pemindahan dilakukan **dengan skrip**, bukan diketik ulang.
2. **Fase 2 (risiko rendah):** keluarkan blok UI kecil dari dalam `App()` menjadi composable
   stateless dan hapus kode duplikat. Patch sudah disiapkan dalam skrip.
3. **Fase 3 (risiko rendah–sedang):** kumpulkan kelompok state yang terisolasi
   (riwayat pencarian, Subscriptions, item tersembunyi, riwayat akun) ke kelas state holder.
   Patch juga sudah disiapkan dalam skrip.
4. **Fase 4 (risiko sedang–tinggi, opsional):** pecah inti `App()` (feed beranda, pencarian,
   Shorts, pemutar). Bagian ini hanya berupa desain. Kerjakan satu per satu dengan review
   model yang lebih kuat atau manusia.

---

## 1. Analisis kondisi sekarang

### 1.1 Peta isi file (baris di versi saat ini)

| Baris | Isi | Ukuran |
|---|---|---|
| 1–136 | `package` + 133 import (banyak wildcard) | 136 |
| 137–193 | Warna (`Background`, `Surface`, `Accent`), `PendingVideo`, `isWideWatchLayout`, `videoKey`, `rememberPlayerLive`, `causedByBehindLiveWindow` | 57 |
| 194–266 | `class MainActivity` (PiP, keep-screen-on, intent, lifecycle) | 74 |
| 268–273 | `statusBarPixels` | 6 |
| **274–2985** | **`@Composable private fun App()`** (seluruh aplikasi) | **2.713** |
| 2987–3173 | Pending watch screen, gesture mini player, `rememberWatchRecommendations` | 187 |
| 3174–3532 | `WatchScreen`, `WatchPlaylistQueueBar`, `PlaylistQueuePanel` | 359 |
| 3533–3688 | `FloatingPlayer` | 156 |
| 3689–3911, 4141–4241 | `HomeNavigation`, `TopCategoryTabs` | 324 |
| 3912–4435, 4691–4707 | `VideoDetails`, sheet "More", like/dislike, chip, `WatchEngagement` | 540 |
| 4436–4690 | `SubscribeButton`, `NotificationOptionsSheet` | 255 |
| 4708–4723 | `formatCount`, `isOngoingLive` (2 extension) | 16 |
| 4724–5058, 5600–5605, 6304–6378 | Kartu video/playlist/channel, badge | 416 |
| 5059–5599 | Layar Subscriptions dan helper-nya | 541 |
| 5606–5647 | `DownloadDialog` | 42 |
| 5648–5875 | Helper audio, `QualityDialog` | 228 |
| 5876–6303 | `ChannelHeader` | 428 |

### 1.2 Isi `App()` (masalah utama)

`App()` adalah *god composable* yang memegang seluruh aplikasi:

- **118** `var x by remember { mutableStateOf(...) }` dan **23** `val x = remember { ... }`
- **47** fungsi lokal (`fun loadHome`, `fun openUrl`, `fun playQuality`, ...)
- **30** `LaunchedEffect`, **6** `DisposableEffect`, **28** `scope.launch`
- Pembuatan `ExoPlayer` beserta listener analitik, PiP, dan keep-screen-on
- Seluruh pohon UI: feed, pencarian, tab, riwayat, playlist akun, halaman tonton, Shorts,
  pemutar melayang/mini, overlay offline

Kelompok state di dalam `App()` dan tingkat keterkaitannya:

| Kelompok | Variabel utama | Dipakai oleh | Keterkaitan |
|---|---|---|---|
| Riwayat pencarian | `history`, `preferences` | `search()`, saran, `HomeRecommendations.query` | rendah → **Fase 3.1** |
| Subscriptions | `subs*` (9 var), 3 fungsi load | tab Subs, reset sesi | rendah → **Fase 3.2** |
| Item tersembunyi | `hiddenVideos`, `hiddenChannels`, `recommendationPrefs` | feed, Shorts, kategori | rendah (banyak tempat baca) → **Fase 3.3** |
| Riwayat akun | `account*` (7 var) | tab You, layar riwayat, simpan posisi | sedang → **Fase 3.4** (hanya dikumpulkan) |
| Pencarian | `query`, `results`, `searchPage`, `searchMode`, ... | bar atas, grid, `back()`, `selectTab` | sedang → Fase 4 |
| Feed beranda/kategori | `home`, `category*`, `homeChips`, `feed*`, `topicIndex` | `loadHome`, `loadMoreFeed`, `selectChip`, `openUrl` | tinggi → Fase 4 |
| Channel/playlist | `channel*`, `playlist*`, `browse*` | grid, `open()`, `back()` | sedang → Fase 4 |
| Shorts | `shorts*` (14 var), `queue` | 6 efek, `open()`, `selectTab` | tinggi → Fase 4 |
| Pemutar/trek | `quality`, `selectedAudio`, subtitle (4), `forceDirectPlayback`, ... | `playQuality`, dialog kualitas, 2 efek fallback audio | sangat tinggi → Fase 4 |
| Tata letak pemutar | `minimized`, `fullscreen`, `dragProgress`, `exitProgress`, `miniState` | hampir semua | sangat tinggi → biarkan di `App()` |
| Status bersama | `loading`, `error` | dipakai bersama oleh home, search, channel, playlist, Shorts | **jangan dipisah** dulu |

Masalah lain yang perlu diketahui eksekutor:

- Banyak deklarasi `private` top-level. Di Kotlin, `private` top-level artinya hanya terlihat
  di file yang sama. Begitu dipindah ke file lain, deklarasi itu harus menjadi `internal`.
- Semua file berada di satu package `id.flufclip.player`, jadi file baru **tidak perlu**
  import untuk saling memanggil.
- Import di file ini memakai wildcard (`androidx.compose.foundation.layout.*`, dsb.). Cara
  paling aman untuk file baru adalah menyalin seluruh blok import. Import yang tidak terpakai
  hanya memunculkan *warning*, bukan error (build ini tidak memakai `allWarningsAsErrors`).
- `MainActivity.kt` saat ini punya **perubahan besar yang belum di-commit** (`git diff`
  menunjukkan ±4.900 baris berubah) plus banyak file untracked. **Commit dulu** sebelum
  mulai (lihat Fase 0) agar setiap langkah refactor bisa dibaca dan dibatalkan.

---

## 2. Target susunan file

Setelah Fase 1–3 (semua di `app/src/main/java/id/flufclip/player/`):

| File | Isi | Asal |
|---|---|---|
| `MainActivity.kt` | `class MainActivity` saja | tetap |
| `AppRoot.kt` | `App()` (state, efek, pohon UI utama) | Fase 1.13 |
| `AppCommon.kt` | warna, `videoKey`, `isWideWatchLayout`, `wideWatchPlayerWidth`, `rememberPlayerLive`, `formatCount`, `isOngoingLive`, `statusBarPixels`, `causedByBehindLiveWindow` | 1.1 |
| `PendingWatch.kt` | `PendingVideo`, `PendingWatchScreen`, `PendingFloatingPlayer`, gesture mini player | 1.2 |
| `WatchScreen.kt` | `WatchScreen`, antrean playlist, `rememberWatchRecommendations` | 1.3 |
| `FloatingPlayer.kt` | `FloatingPlayer` | 1.4 |
| `HomeNavigation.kt` | `HomeNavigation`, `TopCategoryTabs` | 1.5 |
| `WatchDetails.kt` | `VideoDetails`, sheet More, tombol like/chip, `WatchEngagement` | 1.6 |
| `SubscribeButton.kt` | `SubscribeButton`, `NotificationOptionsSheet` | 1.7 |
| `VideoCards.kt` | `VideoCard`, kartu pencarian, kartu playlist/channel, badge | 1.8 |
| `SubscriptionsScreen.kt` | layar Subscriptions dan helper-nya | 1.9 |
| `DownloadDialog.kt` | `DownloadChoice`, `DownloadDialog` | 1.10 |
| `QualityDialog.kt` | helper audio, `QualityDialog` | 1.11 |
| `ChannelHeader.kt` | `ChannelHeader` | 1.12 |
| `AppChrome.kt` | `FeedTopBar`, `HomeTopTabs`, `DownloadStatusBanner`, `SearchSuggestionList` | 2.1 |
| `SearchHistoryStore.kt` | riwayat kata pencarian | 3.1 |
| `SubscriptionsState.kt` | state + logika tab Subscriptions | 3.2 |
| `HiddenFeedItems.kt` | video/channel yang disembunyikan | 3.3 |
| `AccountHistoryState.kt` | state riwayat akun | 3.4 |

Nama file sudah dicek: tidak ada yang bentrok dengan file yang sudah ada.

---

## 3. Aturan wajib untuk model eksekutor

Tempel aturan ini di setiap prompt ke model kecil.

1. **Jangan ubah perilaku.** Ini refactor murni: tidak ada perbaikan bug, tidak ada
   penggantian nama, tidak ada format ulang, dan teks UI tidak boleh diubah.
2. **Jangan mengetik ulang kode yang dipindah.** Fase 1 memakai `tools/refactor/move_decls.py`,
   Fase 2–3 memakai skrip di `tools/refactor/steps/`. Jalankan skripnya. Jangan menyalin
   blok kode besar secara manual.
3. **Satu tugas = satu commit.** Urutan tugas wajib diikuti; langkah berikutnya bergantung pada
   teks hasil langkah sebelumnya.
4. **Setelah setiap tugas, jalankan kompilasi** (lihat §4.3). Hanya lanjut bila lulus.
5. **Bila skrip berhenti dengan `STEP FAILED` atau kompilasi gagal:** jangan improvisasi.
   Kembalikan perubahan (`git checkout -- . && git clean -fd app/src/main/java`), laporkan
   pesan error lengkapnya, lalu berhenti. Skrip patch tidak menulis apa pun bila ada anchor yang
   tidak cocok.
6. **Jangan sentuh file lain** di luar yang disebut tugas itu.
7. **Jangan hapus import** di file baru (pembersihan import dikerjakan manusia di Android
   Studio pada akhir proyek, lihat §9).
8. Jangan mengubah kunci `remember(...)` / `LaunchedEffect(...)`. Kunci itu menentukan kapan
   state di-reset dan efek dijalankan ulang.
9. Jangan membuat `ViewModel`, Hilt, atau arsitektur baru. Itu di luar cakupan.

---

## 4. Alat bantu

### 4.1 `tools/refactor/move_decls.py`

Memindahkan deklarasi top-level Kotlin antar file **tanpa mengubah isinya**.

```bash
python3 tools/refactor/move_decls.py list app/src/main/java/id/flufclip/player/MainActivity.kt
```
Menampilkan setiap deklarasi top-level: baris awal–akhir, jumlah baris, nama, dan `private`.

```bash
python3 tools/refactor/move_decls.py internalize app/src/main/java/id/flufclip/player/MainActivity.kt
```
Mengubah semua `private` **top-level** menjadi `internal`. Anggota class yang ter-indentasi
(mis. `private val pipSupported`) tidak disentuh.

```bash
python3 tools/refactor/move_decls.py move --from SRC.kt --to DST.kt --names A,B,C [--dry-run]
```
Memotong deklarasi bernama dari `SRC` (termasuk KDoc/komentar/anotasi di atasnya), lalu
menambahkannya di akhir `DST`. Bila `DST` belum ada, file itu dibuat dengan baris `package`
dan seluruh blok import dari `SRC`. Bila ada nama yang tidak ditemukan, skrip berhenti tanpa
menulis apa pun. Nama yang cocok dengan beberapa deklarasi (mis. dua `isOngoingLive`) akan
dipindah semuanya.

### 4.2 `tools/refactor/steps/*.py`

Satu skrip per tugas Fase 2–3. Setiap skrip membaca `AppRoot.kt`, menerapkan penggantian
teks persis (setiap anchor harus ditemukan tepat sebanyak yang diharapkan), lalu menyalin file
baru dari `tools/refactor/snippets/*.kt.txt`. Bila satu anchor saja tidak cocok, skrip berhenti
dengan `STEP FAILED, nothing was written`. Jalankan dari root repo:

```bash
python3 tools/refactor/steps/p2_1_feed_chrome.py
```

### 4.3 Perintah verifikasi

Kompilasi (wajib setelah setiap tugas, ±1–3 menit):

```bash
./gradlew :app:compileDebugKotlin --offline -q
```

Lulus = tidak ada baris yang diawali `e: `, dan exit code 0. Baris `w: ` (warning) boleh diabaikan.

Unit test (wajib di akhir setiap fase):

```bash
./gradlew :app:testDebugUnitTest --offline -q
```

Cek bahwa tidak ada baris kode yang hilang atau berubah (khusus Fase 1). Perintah ini
menghitung hash dari semua baris non-import (diurutkan, dan `private`/`internal` dibuang):

```bash
cat app/src/main/java/id/flufclip/player/*.kt | grep -vE '^(import |package )' | grep -v '^\s*$' | sed -E 's/^((@\w+ )*)(private|internal) /\1/' | sort | md5sum
```
Jalankan sebelum tugas 1.0 dan sesudah tugas 1.13. Kedua hash **harus sama** (sudah diuji).
Setelah Fase 2, hash ini memang berubah.

---

## 5. Fase 0: Persiapan (dikerjakan manusia)

1. Commit semua pekerjaan yang sedang berjalan di branch `watch-channel-uploads`, atau simpan
   di branch lain. Pastikan `git status` bersih untuk `app/src/main/java`.
2. Buat branch baru:
   ```bash
   git checkout -b refactor/split-main-activity
   ```
3. Commit alat bantu (`tools/refactor/`) dan dokumen ini:
   ```bash
   git add tools/refactor PLAN_PECAH_MAINACTIVITY.md && git commit -m "Add MainActivity split plan and refactor tools"
   ```
4. Jalankan kompilasi baseline (§4.3) dan pastikan lulus. Catat hasil `md5sum` normalisasi (§4.3).
5. Pastikan `python3 tools/refactor/move_decls.py list app/src/main/java/id/flufclip/player/MainActivity.kt`
   menampilkan `App` di baris ±274 dan `ChannelCard` sebagai deklarasi terakhir. Bila daftarnya
   berbeda jauh dari tabel §1.1, berarti file sudah berubah sejak plan ini ditulis. Nama-nama di
   Fase 1 mungkin masih berlaku, tetapi skrip Fase 2–3 bisa gagal di anchor; laporkan sebelum lanjut.

---

## 6. Fase 1: Memindahkan deklarasi top-level (mekanis)

Semua perintah dijalankan dari root repo. Supaya ringkas, setiap tugas memakai variabel ini:

```bash
D=app/src/main/java/id/flufclip/player
```

Setiap tugas: jalankan perintah → kompilasi → `git add -A $D && git commit -m "<pesan>"`.

### Tugas 1.0: jadikan `private` top-level menjadi `internal`

```bash
python3 tools/refactor/move_decls.py internalize $D/MainActivity.kt
```
Output yang diharapkan: `internalize: 50 top-level declarations in ...`
Commit: `Refactor: make MainActivity top-level declarations internal`

> Alasan langkah ini dikerjakan paling awal: setelahnya, setiap urutan pemindahan tetap
> terkompilasi, karena semua file berada di package dan modul yang sama.

### Tugas 1.1: `AppCommon.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/AppCommon.kt --names Background,Surface,WatchActionSurface,Accent,isWideWatchLayout,wideWatchPlayerWidth,videoKey,rememberPlayerLive,causedByBehindLiveWindow,statusBarPixels,formatCount,isOngoingLive
```
Diharapkan: `move 13 declarations (76 lines)`. Commit: `Refactor: move shared helpers to AppCommon.kt`

### Tugas 1.2: `PendingWatch.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/PendingWatch.kt --names PendingVideo,PendingWatchScreen,PendingFloatingPlayer,pendingWatchSwipe,rememberMiniPlayerGesture
```
Diharapkan: `move 5 declarations (174 lines)`. Commit: `Refactor: move pending watch UI to PendingWatch.kt`

### Tugas 1.3: `WatchScreen.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/WatchScreen.kt --names rememberWatchRecommendations,WatchScreen,WatchPlaylistQueueBar,PlaylistQueuePanel
```
Diharapkan: `move 4 declarations (375 lines)`. Commit: `Refactor: move WatchScreen to its own file`

### Tugas 1.4: `FloatingPlayer.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/FloatingPlayer.kt --names FloatingPlayer
```
Diharapkan: `move 1 declarations (156 lines)`. Commit: `Refactor: move FloatingPlayer to its own file`

### Tugas 1.5: `HomeNavigation.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/HomeNavigation.kt --names HomeNavigation,TopCategoryTabs
```
Diharapkan: `move 2 declarations (324 lines)`. Commit: `Refactor: move bottom navigation and top tabs to HomeNavigation.kt`

### Tugas 1.6: `WatchDetails.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/WatchDetails.kt --names VideoDetails,WatchMoreBottomSheet,WatchSheetOption,WatchEngagement,WatchAction,WatchChip,AnimatedLikeButton,LikeDislikePill,ActionGlyph
```
Diharapkan: `move 9 declarations (440 lines)`. Commit: `Refactor: move watch details and actions to WatchDetails.kt`

### Tugas 1.7: `SubscribeButton.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/SubscribeButton.kt --names SubscribeButton,NotificationOptionsSheet,NotificationOptionRow
```
Diharapkan: `move 3 declarations (255 lines)`. Commit: `Refactor: move subscribe button and notification sheet`

### Tugas 1.8: `VideoCards.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/VideoCards.kt --names LiveThumbnailBadge,SearchWideVideoCard,SearchWidePlaylistCard,CompactVideoCard,MembersOnlyBadge,VideoCard,DurationBadge,clipCard,PlaylistCard,SearchChannelCard,ChannelCard
```
Diharapkan: `move 11 declarations (416 lines)`. Commit: `Refactor: move video, playlist and channel cards to VideoCards.kt`

### Tugas 1.9: `SubscriptionsScreen.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/SubscriptionsScreen.kt --names SubsChipOption,subsChipOptions,key,matchesSubsChannel,isTodayText,filterSubsEntries,mergeSubsEntries,SubsChannelAvatarRow,SubsChipBar,SubsChannelRail,ShortsShelfRow,SubscriptionsScreen
```
Diharapkan: `move 12 declarations (541 lines)`. Commit: `Refactor: move subscriptions screen to its own file`

> Catatan: `key` di sini adalah `val SubsChannel.key` (extension), bukan kata kunci lain.

### Tugas 1.10: `DownloadDialog.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/DownloadDialog.kt --names DownloadChoice,DownloadDialog,PlayerChoiceRow
```
Diharapkan: `move 3 declarations (42 lines)`. Commit: `Refactor: move download dialog to its own file`

### Tugas 1.11: `QualityDialog.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/QualityDialog.kt --names audioTracks,audioLanguage,audioLabel,embeddedAudioLabel,qualityOptions,QualityDialog,QualityOption,SettingsRow
```
Diharapkan: `move 8 declarations (228 lines)`. Commit: `Refactor: move quality dialog and audio helpers to QualityDialog.kt`

### Tugas 1.12: `ChannelHeader.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/ChannelHeader.kt --names ChannelHeader
```
Diharapkan: `move 1 declarations (428 lines)`. Commit: `Refactor: move ChannelHeader to its own file`

### Tugas 1.13: `AppRoot.kt`
```bash
python3 tools/refactor/move_decls.py move --from $D/MainActivity.kt --to $D/AppRoot.kt --names App
```
Diharapkan: `move 1 declarations (2713 lines)`. Commit: `Refactor: move App() to AppRoot.kt`

### Selesai Fase 1: pemeriksaan

```bash
python3 tools/refactor/move_decls.py list $D/MainActivity.kt
```
Hanya tersisa satu baris: `MainActivity`. Lalu jalankan unit test dan `md5sum` normalisasi
(§4.3); hasilnya harus sama dengan catatan di Fase 0.

**Uji di perangkat (manusia):** jalankan checklist §10 sekali. Fase 1 tidak mengubah logika,
jadi cukup *smoke test*.

---

## 7. Fase 2: Ekstraksi UI kecil dari `App()`

Semua tugas di fase ini mengubah `AppRoot.kt`. Jalankan skrip → kompilasi → commit.

### Tugas 2.1: `AppChrome.kt` (bar atas, tab beranda, banner unduhan, saran pencarian)

```bash
python3 tools/refactor/steps/p2_1_feed_chrome.py
```
Diharapkan: `created .../AppChrome.kt` dan `patched .../AppRoot.kt`.
Commit: `Refactor: extract feed top bar, home tabs, download banner and search suggestions`

Yang dilakukan skrip (untuk review):

| Blok di `App()` (anchor) | Diganti dengan |
|---|---|
| `if (showFeed) { if (searchActive) { ...TextField... } else { Row(...logo/back/share...) } }` | `if (showFeed) FeedTopBar(...)` |
| `if (showTopTabs) { val currentActiveTab = when {...}; TopCategoryTabs(...) }` | `if (showTopTabs) HomeTopTabs(...)` |
| `if (downloadState.message.isNotBlank() && showFeed) { Row(...) }` | `... DownloadStatusBanner(downloadState, onOpen = ...)` |
| `if (showFeed && searchActive) { val recent = ...; LazyColumn(...) }` | `SearchSuggestionList(..., modifier = Modifier.weight(1f))` |

Isi composable baru disalin apa adanya dari blok aslinya. Satu-satunya perbedaan: nilai yang
dibaca dan ditulis lewat parameter/callback. `keyboard` dan `context` diambil lewat
`LocalSoftwareKeyboardController.current` / `LocalContext.current`.

### Tugas 2.2: satukan handler playlist akun yang duplikat

```bash
python3 tools/refactor/steps/p2_2_account_playlist.py
```
Commit: `Refactor: deduplicate account playlist open/play handlers`

`onOpen` dan `onPlay` milik `AccountPlaylistScreen` berisi ±20 baris yang sama. Skrip membuat
fungsi lokal `playAccountPlaylist(collection, entries, video, shortsAsHistory)` tepat di atas
`val historyActions = HistoryActions(`. `onOpen` memanggilnya dengan `shortsAsHistory = true`
(perilaku lama: Short dibuka lewat `openHistoryEntry`), sedangkan `onPlay` memanggilnya dengan
`false` (perilaku lama: tidak ada cabang Short). Pemilik `PlaylistRef` di `onPlay` dulu
`video.uploader` dengan `video = entries.first()`, jadi nilainya sama dengan
`entries.firstOrNull()?.uploader.orEmpty()`.

---

## 8. Fase 3: State holder untuk kelompok state yang terisolasi

Pola yang dipakai: kelas biasa beranotasi `@Stable`, propertinya `by mutableStateOf(...)`,
dibuat dengan `remember { ... }` di `App()` **dengan kunci `remember` yang sama** seperti
variabel aslinya. Logika tidak berubah; yang berubah hanya tempatnya.

Jalankan berurutan (3.1 bergantung pada teks hasil 2.1, sedangkan 3.3 dan 3.4 bergantung pada 2.2):

### Tugas 3.1: `SearchHistoryStore`
```bash
python3 tools/refactor/steps/p3_1_search_history.py
```
- `val preferences` + `var history` → `val searchHistory = remember { SearchHistoryStore(...) }`
- Tambah riwayat di `search()` → `searchHistory.add(term)`
- Hapus dari daftar saran → `searchHistory.remove(it)`
- 5 pemanggilan `HomeRecommendations.query(..., history)` → `searchHistory.terms`

Commit: `Refactor: move search history to SearchHistoryStore`

### Tugas 3.2: `SubscriptionsState`
```bash
python3 tools/refactor/steps/p3_2_subscriptions.py
```
- 9 var `subs*`, `loadSubscriptions`, `loadMoreSubscriptions`, `loadMembership`, dan isi
  `LaunchedEffect(mainTab, ...)` dipindah ke `SubscriptionsState` (`load`, `loadMore`,
  `loadMembership`, `onTabOpened`, `reset`).
- Reset di efek sesi akun → `subs.reset()`.
- Coroutine tetap memakai `scope` milik `App()` (`rememberCoroutineScope`), sama seperti sebelumnya.

Commit: `Refactor: move subscriptions feed state to SubscriptionsState`

### Tugas 3.3: `HiddenFeedItems`
```bash
python3 tools/refactor/steps/p3_3_hidden_feed.py
```
- `hiddenVideos = HomeRecommendations.hide(recommendationPrefs, "videos", x)` → `hidden.hideVideo(x)` (3×)
- `hiddenChannels = ...hide(..., "channels", x)` → `hidden.hideChannel(x)` (1×)
- reset rekomendasi → `hidden.reset()`
- semua bacaan → `hidden.videos` (14×) / `hidden.channels` (12×)

Commit: `Refactor: move hidden recommendations to HiddenFeedItems`

### Tugas 3.4: `AccountHistoryState`
```bash
python3 tools/refactor/steps/p3_4_account_history.py
```
Ketujuh `var` dengan kunci `remember(accountHistorySession, accountHistoryLoggedIn)` diganti
menjadi satu `val accountHistory = remember(accountHistorySession, accountHistoryLoggedIn) { AccountHistoryState(accountHistoryLoggedIn) }`.
Nama baru: `accountEntries`→`accountHistory.entries`, `accountContinuation`→`.continuation`,
`accountLoading`→`.loading`, `accountError`→`.error`, `accountRequest`→`.request`,
`accountNextRequest`→`.nextRequest`, `deviceHistory`→`.deviceHistory`. Ketiga efek pemuatan
**tetap** di `App()`.

Commit: `Refactor: group account history state in AccountHistoryState`

### Selesai Fase 3
Kompilasi, unit test, lalu uji di perangkat dengan checklist §10, terutama bagian
Subscriptions, riwayat, pencarian, dan "Not interested".

---

## 9. Fase 4: Pemecahan inti `App()` (opsional, butuh review)

Bagian ini **tidak** punya skrip dan belum diuji coba. Setiap tugas menyentuh logika yang saling
terkait, jadi kerjakan satu tugas per sesi dan minta review model yang lebih kuat atau manusia
sebelum commit. Urutan yang disarankan dari risiko terendah:

### 4.0 Aturan tambahan untuk Fase 4

- **Jangan pakai cari-ganti buta** untuk nama yang juga dipakai sebagai nama parameter atau
  nama lain: `query`, `loading`, `error`, `channel`, `playlist`, `selected`, `home`,
  `category`, `queue`, `results`, `info`, `items`. Contoh: `query = query` adalah argumen
  bernama; hanya sisi kanan yang boleh diganti. Ganti satu per satu, dipandu error kompilasi
  `Unresolved reference`.
- `loading` dan `error` dipakai bersama oleh home, pencarian, channel, playlist, dan Shorts.
  **Tetap simpan di `App()`.**
- Pola aman: (a) pindahkan deklarasi `var` ke kelas "state bag" dengan properti publik `var`,
  (b) kompilasi, perbaiki referensi, commit, (c) baru di tugas terpisah pindahkan fungsi yang
  hanya menyentuh state bag itu ke dalam kelas.

### 4.1 `ShortsFeedState` (state bag)
Var: `shortsRequest, shortsLoading, shortsError, shortsPage, shortsMoreLoading, shortsMoreError,
shortsMoreRequest, shortsTopicIndex, shortsIndex, shortsRetry, shortsGeneration, shortsTabQueue,
shortsSeqNext, shortsFromFeed, shortsDownloadInfo`. Semua memakai `remember { }` tanpa kunci.
`shortsResolver` (`remember(YouTubeAuth.sessionRevision)`) dan `queue` **tetap di luar** karena
`queue` juga dipakai video biasa. Prefiks yang disarankan: `shorts.index`, `shorts.loading`, dst.
Risiko: sedang (ada 6 efek Shorts dengan kunci yang memakai var ini).

### 4.2 `BrowseState` (channel/playlist)
Var: `channel, channelTab, channelItems, playlist, playlistItems, browseNext, browseLoading,
browseError`. Lalu pindahkan `loadChannelTab` dan `loadMoreBrowse` ke kelas (keduanya hanya
menyentuh var ini ditambah `loading`/`error`; kirim keduanya sebagai callback atau biarkan
fungsinya di `App()`). `openChannel` dan `openPlaylist` **tetap** di `App()` karena menyentuh
pemutar. Risiko: sedang.

### 4.3 `SearchState`
Var: `searchActive, lastSearch, searchFilter, query, results, suggestions, searchPage,
searchMoreLoading, searchMoreError, searchGeneration, searchMode`, ditambah efek saran
(`LaunchedEffect(searchActive, query)`). `search()` **tetap** di `App()` karena membuka
URL/playlist dan menyentuh `mainTab`, `channel`, `playlist`, `loading`, `error`.
`moreSearchResults()` boleh dipindah. Risiko: sedang.

### 4.4 `HomeFeedState`
Var: `homePage, homeContinuationToken, category, categoryItems, categoryPage, liveNext,
homeChips, selectedChip, categoryContinuation, homeChipTitle, feedLoading, feedError, feedEnd,
topicIndex, homeLoadJob, lastAccountState, homeSessionRevision`.
**Perhatian:** `home`, `homeCachePrefs`, dan `relatedCachePrefs` memakai
`remember(YouTubeAuth.sessionScopeId)`. Biarkan ketiganya di luar, atau buat kelas terpisah
dengan kunci yang sama. Fungsi `loadHome`, `loadMoreFeed`, `selectChip`,
`startFallbackCategory`, `selectFallbackCategory`, `selectCategory`, dan `mergeHomeChips` bisa
pindah belakangan, tetapi membaca `watchHistory`, `searchHistory`, `hidden`, `loading`, `error`,
`searchMode`, `channel`, `playlist`, `searchActive`, `selected`, dan `minimized`, jadi semuanya
harus dikirim sebagai parameter/lambda. Risiko: tinggi.

### 4.5 Ekstraksi UI besar
- `FeedGrid(...)`: blok `else if (showFeed) { error?.let ...; LazyVerticalGrid ... }`
  (±155 baris). Bergantung pada ±35 nilai/callback. Kerjakan setelah 4.2–4.4 agar parameternya
  bisa berupa objek state (`browse`, `search`, `feed`).
- `WatchPageHost(...)`: blok `if (info != null && !shortsMode) Box { ... WatchScreen(...);
  QualityDialog(...); DownloadDialog(...) }` (±155 baris). Callback `QualityDialog`
  (pilih audio/embedded/kualitas) sebaiknya dipindah ke fungsi lokal `App()` dulu
  (`onSelectAudio(info, audio)`, dst.), baru diekstrak.
- `PlayerFrame`: perhitungan posisi/ukuran pemutar (`inlineWidth`, `baseX`, `x`, `y`, ...)
  untuk pemutar utama dan pemutar *pending* dijadikan fungsi murni yang mengembalikan
  data class. Fungsi ini mudah dites dengan unit test.

### 4.6 `PlaybackController` (paling berisiko, jangan untuk model kecil)
`playQuality`, `changeSubtitle`, `pickStreams`, `configurePreferredAudio`, `usesAdaptiveAudio`,
`changePreferredAudio`, `startInfo`, `shortSource`, `startShort`, `preloadNext`,
`refreshPlayback`, 2 efek fallback audio, dan listener error pemutar. Semuanya membaca dan
menulis ±12 var trek ditambah `selected`, `minimized`, dan `shortsMode`. Butuh desain ulang
(mis. kelas yang memegang `player` + state trek, dengan callback ke `App()` untuk
`selected`/`minimized`) dan pengujian manual menyeluruh (live, DVR, dubbing, subtitle, PiP).

### 4.7 Pembersihan (manusia, di Android Studio)
- *Code → Optimize Imports* pada semua file baru (menghapus ±100 import tak terpakai per file).
- Opsional: kembalikan `internal` menjadi `private` untuk deklarasi yang hanya dipakai di
  filenya sendiri (Android Studio menandainya dengan inspeksi "Can be private").

---

## 10. Checklist uji manual di perangkat

Jalankan setelah Fase 1, setelah Fase 3, dan setelah setiap tugas Fase 4.

- [ ] Beranda memuat, pull-to-refresh jalan, scroll memuat halaman berikutnya
- [ ] Tab atas: Home / Subs / Music / Live / More (chip) berpindah dengan benar
- [ ] Menu kartu: "Not interested" menghilangkan video; "Don't recommend channel" menghilangkan channel
- [ ] Pencarian: saran muncul, riwayat tersimpan, hapus item riwayat, filter All/Video/Channel/Playlist, scroll memuat lebih banyak
- [ ] Tempel URL video / playlist di kolom pencarian
- [ ] Buka channel: header, tab, tombol bagikan (ikon ⋮), subscribe + lonceng notifikasi
- [ ] Buka video: halaman tonton, like/dislike, simpan, komentar, deskripsi, bab, chat live
- [ ] Geser ke bawah → mini player; geser mini player ke samping (stash); buka lagi
- [ ] Fullscreen, geser keluar dari fullscreen, rotasi
- [ ] Dialog kualitas: ganti kualitas, audio, subtitle, autoplay
- [ ] Unduh dari halaman tonton dan dari Shorts; banner unduhan (Cancel / Open / Close)
- [ ] Tab Shorts: geser, like, simpan, tutup; Shorts dari rak Subscriptions
- [ ] Tab Subscriptions (login): feed, chip, rail channel, video member
- [ ] Tab You: riwayat akun, riwayat perangkat, hapus entri, buka playlist akun, Play all
- [ ] PiP: tekan Home saat video diputar
- [ ] Ganti akun / logout: beranda dan Subscriptions kosong lalu dimuat ulang

### 10.1 Hasil uji di perangkat

Perangkat: emulator Pixel Tablet (Android 16 / API 36, 2560×1600, landscape), dipasang dengan
`pixeltablet install`. APK selalu di-build ulang dari HEAD sebelum diuji. `pixeltablet install`
hanya mem-build bila APK belum ada, jadi APK lama harus dipindah atau dihapus dulu.

#### Setelah Fase 1 (2026-11-06, commit `404d36d`)

Hash normalisasi §4.3 sama sebelum (`923d90a`) dan sesudah: `30d6951965a7f9dd296413eee2236559`.
Tidak ada file di luar `app/src/main/java` yang berubah.

| Checklist | Hasil |
|---|---|
| Beranda, pull-to-refresh, paging | ✔ |
| Tab atas Home / Subs / Music / Live / More (chip Gaming) | ✔ |
| Not interested / Don't recommend channel | ✔ |
| Pencarian: saran, riwayat, filter, scroll | ✔ |
| Tempel URL video / playlist | ✔ |
| Channel: header, tab, bagikan, sheet lonceng | ✔ (tombol Subscribe tidak ditekan) |
| Halaman tonton: like/dislike, simpan, komentar, deskripsi, bab, chat live | ✔ |
| Mini player, stash, buka lagi | ✔ |
| Fullscreen, geser keluar, rotasi | ✔ |
| Dialog kualitas: kualitas, subtitle, autoplay | ✔ (pilihan audio tidak diganti) |
| Unduh dari halaman tonton dan Shorts; banner Cancel / Open / Close | ✔ |
| Shorts: geser, like, simpan; Shorts dari rak Subs | ✔ |
| Subscriptions: feed, chip, rail, video member | ✔ |
| You: riwayat akun & perangkat, hapus entri, playlist akun, Play all | ✔ |
| PiP | ✔ |
| Ganti akun / logout | **belum diuji** (butuh login ulang manual) |

Tidak ada crash aplikasi.

#### Setelah Fase 3 (2026-11-06, commit `b348e38`)

Fokus ke bagian yang disentuh Fase 2–3:

| Tugas | Yang diuji | Hasil |
|---|---|---|
| 2.1 `AppChrome` | Bar atas, tab beranda, daftar saran pencarian | ✔ |
| 2.2 handler playlist akun | Short dari Saved Shorts terbuka di layar Shorts; item ke-3 Liked membuka antrean "Liked videos 3/34"; Play all mulai dari item 1 | ✔ |
| 3.1 `SearchHistoryStore` | Riwayat lama terbaca, kata baru tersimpan, hapus item | ✔ |
| 3.2 `SubscriptionsState` | Feed, chip Membership & Shorts, paging, rail channel, Short dari rak | ✔ |
| 3.3 `HiddenFeedItems` | Item tersembunyi dari Fase 1 tetap tersembunyi; Not interested baru langsung jalan | ✔ |
| 3.4 `AccountHistoryState` | Riwayat akun di tab You, View all, hapus entri | ✔ |

Tidak ada crash aplikasi. Satu crash di buffer berasal dari `com.google.android.bluetooth`
(emulator), bukan dari FlufPlay.

**Belum diuji:**
- Ganti akun / logout. Langkah ini juga menguji `subs.reset()` dan `hidden.reset()`.
- Pemutar, kualitas, dan PiP di build Fase 3 (bagian ini tidak disentuh Fase 2–3).
- Banner unduhan versi `AppChrome` (Fase 2.1).

#### Bug yang sudah ada sebelum refactor (bukan regresi, kodenya identik)
1. Header channel menampilkan "Subscribe" untuk channel yang sudah di-subscribe (halaman tonton benar). Teks nama channel juga tertimpa banner.
2. Setelah ganti subtitle, kualitas manual (mis. 1080p) kembali ke 720p.
3. Antrean "Play all" dari playlist akun tetap menempel di video yang dibuka lewat URL.
4. Tombol cari di bottom nav tidak menampilkan kolom pencarian selama layar playlist akun terbuka.
5. Menu kualitas menampilkan "Auto" untuk preset Data saver/Higher (`QualityDialog.kt`, `qualityLabel`).

---

## 11. Masalah yang mungkin muncul

| Gejala | Penyebab | Tindakan |
|---|---|---|
| `STEP FAILED ... expected 1 occurrence(s), found 0` | `AppRoot.kt` berbeda dari yang diharapkan skrip (langkah sebelumnya belum jalan, atau file diedit manual) | Jalankan langkah sebelumnya; bila tetap gagal, laporkan. Jangan edit skrip. |
| `ERROR: not found in ...MainActivity.kt: X` | Deklarasi `X` sudah dipindah atau berganti nama | Cek `move_decls.py list`; bila memang sudah pindah, hapus nama itu dari perintah |
| `e: ... Cannot access 'X': it is private in file` | Tugas 1.0 terlewat | Jalankan `internalize` pada file asal |
| `e: ... Conflicting declarations` / `Redeclaration` | Deklarasi yang sama ada di dua file (perintah `move` dijalankan dua kali setelah `git checkout` parsial) | `git checkout -- . && git clean -fd $D`, ulangi tugas |
| `e: ... Unresolved reference` setelah Fase 3 | Ada pemakaian nama lama yang tidak ditangani skrip (file berubah sejak plan dibuat) | Laporkan baris dan nama; jangan tebak penggantinya |
| `e: ... Smart cast to 'X' is impossible` (Fase 4) | Properti kelas yang `var` tidak bisa di-smart-cast | Simpan ke `val` lokal dulu: `val c = browse.channel; if (c != null) ...` |

---

## 12. Template prompt untuk model kecil

```
Kamu mengerjakan SATU tugas refactor di repo Android ini. Ikuti PLAN_PECAH_MAINACTIVITY.md.

Tugas: <nomor dan judul, mis. "Tugas 1.6: WatchDetails.kt">

Aturan (wajib):
- Jalankan perintah persis seperti di plan untuk tugas ini. Jangan menulis atau menyalin kode secara manual.
- Setelah itu jalankan: ./gradlew :app:compileDebugKotlin --offline -q
- Bila output skrip atau kompilasi mengandung "STEP FAILED", "ERROR", atau baris "e: ",
  jalankan `git checkout -- . && git clean -fd app/src/main/java`, laporkan output lengkapnya, lalu BERHENTI.
- Bila lulus: git add -A app/src/main/java && git commit -m "<pesan commit di plan>"
- Jangan mengubah file lain. Jangan memperbaiki hal lain yang kamu lihat.
- Laporkan: perintah yang dijalankan, output skrip, hasil kompilasi, hash commit.
```

Untuk Fase 4, tambahkan ke prompt: deskripsi tugas dari §9, daftar var yang dipindah, dan
instruksi untuk berhenti dan meminta review sebelum commit.
