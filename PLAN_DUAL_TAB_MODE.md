# Plan: Dual Tab Mode (Putar 2 Video Bersamaan)

Dokumen ini adalah rencana implementasi **Dual Tab Mode** di **FlufPlay / PrismPlayer** (`id.flufclip.player`), yaitu mode khusus untuk memutar **2 video YouTube sekaligus dalam satu aplikasi** dengan konsumsi RAM jauh lebih hemat dibanding membuka 2 tab di browser.

> **Referensi Desain Interaktif:** [`docs/prototypes/dual-mode-prototype.html`](docs/prototypes/dual-mode-prototype.html)

---

## Status Revisi Implementasi

Hasil review kode branch `feat/dual-tab-mode`. Seluruh item revisi R1–R5 telah diselesaikan dan diverifikasi ulang:

| ID | Status | Fase | Masalah | Keputusan / Perbaikan | File |
|---|---|---|---|---|---|
| R1 | ✅ Selesai | 1 | `applyDualAudioRouting` menyalakan `handleAudioFocus` di player baru sebelum mematikannya di player lama. | Matikan `handleAudioFocus` di player non-fokus dulu, baru nyalakan di player fokus. | `PlaybackService.kt` |
| R2 | ✅ Selesai | 5 | Cabang Dual Mode di `open()` dijalankan sebelum deteksi playlist. | Dipindahkan setelah deteksi playlist (`meta?.isPlaylist` / param `list`) agar kartu playlist tetap membuka playlist. | `AppRoot.kt` |
| R3 | ✅ Selesai | 4–5 | Menutup `DualWatchScreen` atau keluar Shorts meninggalkan audio tanpa UI. | Kedua slot di-pause saat `onClose` / `onPickVideoForSlot`; keluar Shorts otomatis membuka kembali `DualWatchScreen`. | `AppRoot.kt` |
| R4 | ✅ Selesai (b) | 5 | Tap video saat Dual Mode ON dan `pickingSlot == null`. | Dipertahankan: semua tap video non-short/non-playlist saat Dual Mode ON masuk ke slot Dual (slot kosong, atau menimpa slot fokus). | `AppRoot.kt` |
| R5 | ✅ Selesai (a) | 5 | Background playback & notifikasi media saat aplikasi masuk background. | Slot fokus (Slot 1 atau Slot 2) boleh lanjut di background sesuai `PlayerSettings.backgroundPlayback` dan terikat ke notifikasi; slot non-fokus selalu di-pause. | `AppRoot.kt`, `MainActivity.kt`, `DualWatchScreen.kt` |

---

## 0. Prinsip Desain & Ruang Lingkup

1. **Masuk lewat tab `You / Me` (`LibraryTab.kt`)**:
   - Mode ini diaktifkan secara sadar lewat toggle **Dual Tab Mode** di layar profil/Me.
   - Saat **OFF**, aplikasi berjalan 100% seperti kondisi sekarang (1 `ExoPlayer`, UI normal).
   - Saat **ON**, tersedia layar tonton dual adaptif (Mobile Portrait vs Tablet Landscape) dan alur **Pilih Video untuk Slot** lewat Home/Search.
2. **Tetap Pragmatis Sesuai Arsitektur Saat Ini**:
   - Tidak menambah library DI/navigation baru atau layer `UseCase`/`ViewModel` berlebih.
   - Mengikuti pola state holder & fungsi `internal` di package `id.flufclip.player`.
3. **Target RAM & Hardware Safety**:
   - Memakai native `ExoPlayer` tanpa web/JS engine.
   - Slot 2 memakai buffer lebih kecil dan resolusi maksimal `720p` agar 2 instance `MediaCodec` hardware tidak kehabisan decoder di perangkat kelas menengah (target uji: perangkat RAM 4 GB).
4. **Lingkup V1 (MVP)**:
   - V1 hanya: toggle di `You`, 2 slot player, `DualWatchScreen`, dan mode pilih video untuk slot.
   - **Ditunda ke V2** (baru dikerjakan bila V1 stabil): tab nav `"dual"` di `HomeNavigation.kt`, `DualBrowseTargetStrip` di `AppChrome.kt`, dan `DualMiniBar`.

### Estimasi RAM (acuan)

| Skenario | Tambahan RAM vs mode normal |
|---|---|
| Slot 2 aktif, 720p, buffer ≤15 detik | ~35–50 MB |
| Slot 2 aktif, 1080p | ~55–90 MB |
| Pembanding: 1 tab YouTube tambahan di Brave (diukur via `tdev`) | ~120–150 MB |

---

## 1. Pemetaan Perubahan File

| File | Peran Saat Ini | Perubahan Rencana |
|---|---|---|
| `PlayerControls.kt` | Menyimpan `PlayerSettings` | Tambah flag preferensi `dualModeEnabled` dan `dualAudioPolicy` (`"focus"` / `"mix"`). **Tidak** menambah setelan resolusi baru — pakai `maxVideoHeight` yang sudah ada. |
| `PlaybackNet.kt` | `playbackLoadControl()` (buffer 12–30 detik) | Tambah `secondaryLoadControl()` dengan buffer lebih kecil (mis. min 6 detik, max 15 detik) khusus Slot 2. |
| `PlaybackService.kt` | `PlaybackSessionHost` hanya memegang 1 `onlinePlayer` | Tambah `obtainSecondaryPlayer(context)` / `releaseSecondaryPlayer()` dengan `secondaryLoadControl()` dan `DefaultRenderersFactory(...).setEnableDecoderFallback(true)`. |
| `DualTabState.kt` *(baru)* | – | State holder ringan untuk 2 slot: `StreamInfoItem`, `StreamInfo`, `PlaybackTrackState` per slot, status `loading`, `error`, `muted`, `focusedSlot`, `pickingSlot`. |
| `LibraryTab.kt` | Layar `You / Me` (`ProfileHeaderSection`, baris 413) | Tambah kartu **Dual Tab Mode** (switch ON/OFF, pilihan audio policy, tombol **Buka Dual Player**). |
| `DualWatchScreen.kt` *(baru)* | – | UI Dual Watch adaptif: **Stacked Vertikal + Focus Drawer** di Mobile Portrait; **Side-by-Side 50:50** di Tablet Landscape (`isWideWatchLayout`, `AppCommon.kt:176`), plus workspace bar (`Swap`, `Audio Focus/Mix`, `Ganti Video`). |
| `InlineFeedPlayback.kt` | `isInlinePlaybackEligible(...)` (baris 99) | Tambah parameter `dualModeEnabled` → `false` saat Dual Mode aktif. Cek seluruh call-site (`AppRoot.kt:638`). |
| `AppRoot.kt` | Root state & routing `App()` | Routing `open(item)` (baris 1386) saat `pickingSlot != null`, tampilkan `DualWatchScreen`, penanganan Shorts saat Dual Mode, dan pause Slot 2 saat background. |
| `MainActivity.kt` | `onStop()` / PiP untuk 1 player | Pause Slot 2 di `onStop()` dan saat masuk PiP. |

---

## 2. Fase Implementasi Bertahap

### Fase 1: Preferensi & Manajemen 2 Instance `ExoPlayer`

**Tujuan:** Memastikan 2 `ExoPlayer` dapat hidup dan memutar media bersamaan tanpa saling mematikan audio focus atau membocorkan resource saat mode dimatikan.

1. **Preferensi di `PlayerControls.kt` (`PlayerSettings`)**
   - Tambah properti persisten (`SharedPreferences`):
     - `dualModeEnabled: Boolean` (default `false`)
     - `dualAudioPolicy: String` (`"focus"` atau `"mix"`, default `"focus"`)
   - Resolusi Slot 2 = `min(maxVideoHeight, 720)`, mengikuti pola inline feed (`AppRoot.kt:687`). `maxVideoHeight` sudah ada di `AppRoot.kt:282` (pref `max_video_height`).
2. **Buffer Slot 2 di `PlaybackNet.kt`**
   - `secondaryLoadControl()`: `setBufferDurationsMsForStreaming(6_000, 15_000, 500, 1_000)`.
3. **Pool 2 Player di `PlaybackService.kt` (`PlaybackSessionHost`)**
   - Pertahankan `obtainOnlinePlayer(context)` untuk Slot 1 / Mode Normal.
   - Tambah `obtainSecondaryPlayer(context): ExoPlayer` (pakai `secondaryLoadControl()`, `playbackLiveSpeedControl()`, dan decoder fallback).
   - Tambah `releaseSecondaryPlayer()` yang wajib dipanggil saat `dualModeEnabled` dimatikan atau aplikasi keluar.
4. **Aturan Audio Focus (wajib)**
   - **Tepat 1 player** yang memegang `setAudioAttributes(AudioAttributes.DEFAULT, true)`: player milik `focusedSlot`. Player lain memakai `handleAudioFocus = false`.
   - Saat `focusedSlot` berganti, matikan `handleAudioFocus` di player non-fokus terlebih dahulu, baru nyalakan di player fokus (`✅ R1`).
   - Suara diatur lewat `player.volume`:
     - Policy `"focus"`: slot fokus `1f`, slot lain `0f`.
     - Policy `"mix"`: kedua slot `1f` (hanya slot fokus yang tetap memegang audio focus).
   - Saat player fokus berhenti karena kehilangan audio focus (`Player.Listener.onPlayWhenReadyChanged` dengan alasan `PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS`, mis. telepon masuk), **pause juga slot lain**.
5. **Notifikasi Media (`PlaybackSessionHost.activeTarget`)**
   - Saat Dual Mode aktif, `MediaSession` diikat ke player milik `focusedSlot`.

**Verifikasi Fase 1:**
- Build via CI (GitHub Actions). Build Gradle lokal hanya jika diminta user (perangkat Termux RAM rendah).
- Uji manual: dua player tidak saling pause saat mulai play bersamaan; telepon masuk mem-pause keduanya.

---

### Fase 2: State Holder `DualTabState.kt` & Stream Loader Slot

**Tujuan:** Memisahkan state pemutaran dua slot dari state tunggal `selected` / `info` di `AppRoot.kt`.

1. Buat `DualTabState.kt` berisi:
   - `data class DualTabSlotState`: `id` (`1`/`2`), `item: StreamInfoItem?`, `info: StreamInfo?`, `loading: Boolean`, `error: String?`, `muted: Boolean`.
   - `class DualTabController`:
     - Menyimpan `slot1`, `slot2`, `focusedSlot` (`1`/`2`), `pickingSlot` (`1`/`2`/`null`), dan **satu `PlaybackTrackState` per slot**.
     - Fungsi `loadIntoSlot(slotId, item, context, scope)` memakai jalur yang sudah ada:
       1. `InnerTubePlayer.resolve(item.url)` → `StreamInfo` (`InnerTubePlayer.kt:184`).
       2. `pickStreams(info, height)` (`PlaybackTrackState.kt:237`).
       3. `trackState.playQuality(player, context, height, info, video, audio, 0L, true)` milik slot tersebut.
     - Fungsi `swapSlots()`, `toggleMute(slotId)`, `focusSlot(slotId)` (sekalian menerapkan aturan audio focus Fase 1), dan `releaseAll()`.
2. **Sinkronisasi Transisi Mode**:
   - Saat Dual Mode dinyalakan ketika sudah ada 1 video aktif di mode normal: video aktif menjadi `Slot 1` tanpa reload (player yang sama = `onlinePlayer`). `Slot 2` kosong/siap dipilih.
   - Saat Dual Mode dimatikan: stop & `releaseSecondaryPlayer()`, Slot 1 tetap berjalan sebagai video mode normal.

**Verifikasi Fase 2:**
- Build via CI.
- Unit test sederhana untuk logika `DualTabController` (`swapSlots`, `focusSlot` dengan policy `"focus"` vs `"mix"`, set/clear `pickingSlot`). Bagian yang menyentuh `ExoPlayer` dipisah agar logika state bisa dites tanpa player.

---

### Fase 3: Entry Point di Screen `You / Me` (`LibraryTab.kt`)

**Tujuan:** Memberi user satu pintu masuk yang jelas untuk mengaktifkan/menonaktifkan Dual Mode dan mengonfigurasinya.

1. Di `LibraryTab.kt` (tepat di bawah `ProfileHeaderSection`, sebelum bagian `History`):
   - Tambah composable `DualModeCard(...)` dengan warna surface solid sesuai tema (`#181A1F` / divider `#272727`, aksen `#3EA6FF`).
   - Isi card:
     - Switch ON/OFF untuk `dualModeEnabled`.
     - Saat ON:
       - **Audio saat 2 video play**: `1 Suara Aktif (Focus)` / `Mix Kedua Audio`.
       - Tombol **"Buka Dual Player"** untuk membuka `DualWatchScreen`.

**Verifikasi Fase 3:**
- Build via CI.
- Tampilan `LibraryTab` tetap rapi di layar sempit (mobile) maupun lebar (tablet).

---

### Fase 4: UI Dual Watch Adaptif (`DualWatchScreen.kt`)

**Tujuan:** Menyajikan pengalaman menonton 2 video bersamaan yang nyaman di **Android Mobile (Portrait)** dan **Tablet Landscape**.

1. **Workspace Bar (Atas)**
   - Tombol `Swap` (tukar posisi tampilan Slot 1 ↔ Slot 2 — cukup tukar mapping UI, player tidak dipindah antar slot).
   - Toggle cepat `Focus Audio` ↔ `Mix Audio`.
   - Tombol `Ganti Video` → pause kedua slot, set `pickingSlot`, lalu buka Home/Search (lihat Fase 5).
   - Back / tutup layar Dual mem-pause kedua slot karena V1 belum punya `DualMiniBar` (`✅ R3`).
2. **Layout Mobile Portrait (`!isWideWatchLayout(maxWidth, maxHeight)`)**
   - **Area Atas (Stacked Vertikal):** `Slot 1` dan `Slot 2`, masing-masing header mini + viewport player + kontrol Play/Pause & Mute/Unmute. Tinggi viewport proporsional (~180–210dp tergantung tinggi layar) agar dua video + drawer muat.
   - **Area Bawah (Focused Detail Drawer):** switcher `Tab 1` / `Tab 2`, judul, channel, tombol `Ganti video Tab X`, dan daftar *Up Next / Related* slot tersebut.
3. **Layout Tablet Landscape (`isWideWatchLayout(maxWidth, maxHeight)`)**
   - 2 kolom side-by-side (50:50): tiap kolom = player 16:9 + info video + *Up Next*, scroll independen.
4. **Surface Player (`PlayerView`)**
   - Pakai `floating_player.xml` (`surface_view`) karena kedua slot berada di kotak terpisah yang tidak bertumpuk.

**Verifikasi Fase 4:**
- Build via CI.
- Uji rotasi Portrait ↔ Landscape: kedua player tidak reload stream (hanya `PlayerView` yang re-bind ke `ExoPlayer` yang sama, karena player hidup di `PlaybackSessionHost`).

---

### Fase 5: Integrasi Minimal di `AppRoot.kt` & `InlineFeedPlayback.kt`

**Tujuan:** Alur memilih video untuk slot dan mencegah konflik player, dengan perubahan sesedikit mungkin di `AppRoot.kt`.

1. **Mode Pilih & Routing Video di Dual Mode**
   - Saat `Ganti Video` / slot kosong ditekan di `DualWatchScreen`: pause kedua slot, set `dualController.pickingSlot = slotId`, lalu pindah ke Home/Search.
   - Di `open(item)` (`AppRoot.kt`): deteksi playlist (`meta?.isPlaylist` / param `list`) dijalankan terlebih dahulu (`✅ R2`). Untuk video non-short/non-playlist saat Dual Mode ON, tap video masuk ke `pickingSlot` jika aktif, atau otomatis mengisi slot kosong / menimpa `focusedSlot` lalu membuka `DualWatchScreen` (`✅ R4`).
   - Tombol Back saat mode pilih → batal (`pickingSlot = null`) dan kembali ke `DualWatchScreen`.
2. **Inline Feed Playback**
   - Tambah parameter `dualModeEnabled` di `isInlinePlaybackEligible(...)` (`InlineFeedPlayback.kt:99`) → `false` saat Dual Mode aktif, karena inline memakai `onlinePlayer` yang sama dengan Slot 1. Perbarui semua call-site.
3. **Shorts (keputusan final)**
   - Shorts memakai `onlinePlayer` yang sama dengan Slot 1 (`AppRoot.kt`). Saat user membuka Shorts dalam Dual Mode:
     1. Simpan `item` + posisi Slot 1, pause Slot 2.
     2. Shorts berjalan normal memakai `onlinePlayer`.
     3. Saat keluar dari Shorts, muat ulang Slot 1 dari posisi tersimpan dan buka kembali `DualWatchScreen` (`✅ R3`). Slot 2 tetap pause sampai user menekan play.
4. **Background, PiP & Lifecycle (`MainActivity.kt`)**
   - `onStop()`: slot non-fokus selalu di-pause; `focusedSlot` (Slot 1 atau Slot 2) mengikuti `PlayerSettings.backgroundPlayback` dan terikat ke `MediaSession` (`✅ R5`).
   - PiP: hanya `focusedSlot` yang ditampilkan, slot non-fokus di-pause.

**Verifikasi Fase 5:**
- Build & unit test via CI (`:app:compileDebugKotlin :app:testDebugUnitTest`).
- Jika build lokal diminta: pakai `--no-daemon` dan jalankan `./gradlew --stop` setelah selesai.

---

## 3. Ditunda ke V2

Baru dikerjakan setelah V1 stabil dan memang dibutuhkan:

- Tab nav `"dual"` di `HomeNavigation.kt` (tabs saat ini: `home`, `shorts`, `search`, `you`).
- `DualBrowseTargetStrip` (`Tab 1` / `Tab 2`) di `AppChrome.kt`.
- `DualMiniBar` di atas navigasi saat browsing.
- Menu "Putar di Tab 2" di kartu video (kartu Home/Search belum punya menu overflow; saat ini hanya kartu channel, `VideoCards.kt:407`).
- Opsi resolusi Slot 2 di atas 720p.

---

## 4. Checklist Edge Case yang Wajib Diuji

- [ ] **Inline Feed Playback**: tidak berjalan saat `dualModeEnabled == true`.
- [ ] **Shorts**: Slot 1 tersimpan & dimuat ulang dari posisi terakhir setelah keluar Shorts; Slot 2 tetap pause.
- [ ] **Audio Focus**: telepon masuk / app lain memutar audio → kedua slot pause; tidak ada player yang saling mem-pause saat play bersamaan.
- [ ] **PiP**: hanya `focusedSlot` yang tampil, slot lain pause.
- [ ] **Lifecycle Background**: Slot 2 selalu pause di `onStop`; Slot 1 mengikuti `PlayerSettings.backgroundPlayback`.
- [ ] **Matikan Dual Mode**: `releaseSecondaryPlayer()` terpanggil, Slot 1 tetap berjalan normal.
- [ ] **Hardware Decoder Limit**: decoder fallback aktif; jika tetap gagal, turunkan Slot 2 ke `480p`/`360p` atau tampilkan pesan yang jelas.
- [ ] **Pindah Fokus Saat Keduanya Play** (R1): fokus Slot 2 → Slot 1 dan sebaliknya, kedua video tetap berjalan.
- [ ] **Kartu Playlist di Dual Mode** (R2): tap kartu playlist tidak membuat slot error.
- [ ] **Tutup Layar Dual** (R3): Back dari `DualWatchScreen` tidak meninggalkan audio tanpa UI.
- [ ] **Memory Footprint**: ukur dengan `tdev sh "dumpsys meminfo id.flufclip.player"` saat 1 video vs 2 video 720p diputar 2–3 menit; tambahan target ≤ ~50 MB, tanpa kebocoran memori atau crash OOM.
