# AGENTS.md

Dokumen ini berisi aturan operasional wajib untuk seluruh AI Agent (khususnya **Gemini 3.8 Flash** dan model kelas Flash lainnya) saat mengerjakan tugas ringan maupun berat di repositori **PrismPlayer / FlufPlay**.

---

## 1. Pemahaman Konteks Proyek (Anti-Kurang Paham Project)

Sebelum menyentuh kode apa pun, pahami struktur asli proyek ini agar tidak menebak-nebak atau membuat asumsi yang salah:

- **Stack Utama**: Aplikasi Android satu modul (`:app`), package `id.flufclip.player` (`app/src/main/java/id/flufclip/player/`), menggunakan **Kotlin + Jetpack Compose**, **Media3 ExoPlayer**, **NewPipe Extractor**, dan **youtubedl-android** (`yt-dlp` + `FFmpeg`).
- **Arsitektur Saat Ini**:
  - Proyek ini **TIDAK** menggunakan Clean Architecture berlapis, Dagger/Hilt, Retrofit, atau Jetpack Navigation Graph XML/Compose.
  - State dikelola secara pragmatis di dalam `AppRoot.kt` (`@Composable internal fun App()`) bersama file-file state holder & domain spesifik di package yang sama (`*State.kt`, `HomeFeed.kt`, `VideoRepository.kt`, `YouTubeAuth.kt`, dll.).
- **Peta File Utama** (selalu cek file ini sebelum membuat fungsi baru):
  - **Entry & Shell**: `MainActivity.kt` (lifecycle, PiP, intent), `AppRoot.kt` (root state & layout host), `AppChrome.kt`, `AppCommon.kt`, `HomeNavigation.kt`.
  - **Beranda & Feed**: `HomeFeed.kt`, `HomeFeedState.kt`, `HomeFeedCache.kt`, `FeedGrid.kt`, `VideoCards.kt`, `InlineFeedPlayback.kt`.
  - **Halaman Tonton & Player**: `WatchPageHost.kt`, `WatchScreen.kt`, `WatchDetails.kt`, `WatchPanels.kt`, `WatchActionSurface.kt`, `PlayerFrame.kt`, `PlayerControls.kt`, `MiniPlayer.kt`, `OfflineMiniplayer.kt`, `InnerTubePlayer.kt`, `PlaybackManifest.kt`.
  - **Shorts, Channel, & Subscriptions**: `ShortsScreen.kt`, `ShortsFeed.kt`, `ShortsFeedState.kt`, `ChannelHeader.kt`, `SubscriptionsScreen.kt`, `SubscriptionsState.kt`.
  - **Akun, Library, & Unduhan**: `YouTubeAuth.kt`, `AccountVault.kt`, `AccountLibrary.kt`, `AccountLibraryUi.kt`, `AccountHistory.kt`, `LibraryTab.kt`, `SavedDownloads.kt`, `YtDlpFormats.kt`.
- **Kewajiban Baca Sebelum Edit (Read-Before-Write)**:
  - **DILARANG** menebak isi file, nama variabel, signature fungsi, atau warna/token UI.
  - Wajib membaca baris kode terkait dengan `read_file` atau mencari referensi dengan `grep` sebelum melakukan perubahan.

---

## 2. Aturan Anti Over-Engineering (Strict YAGNI & Minimal Diff)

Model kelas Flash sering terjebak membuat abstraksi berlebihan atau menulis ulang kode yang sudah berjalan. Patuhi batasan keras berikut:

1. **Dilarang Mengubah Arsitektur Tanpa Diminta**
   - Jangan membuat layer baru (`UseCase`, `ViewModel`, `Repository` baru, `UiEvent`/`UiEffect` sealed class kompleks, atau Dependency Injection) kecuali diminta secara eksplisit oleh user.
   - Ikuti gaya kode yang sudah ada di file target (fungsi `internal`, state holder sederhana, dan helper top-level di package `id.flufclip.player`).
2. **Prinsip *Surgical Edit* (Perubahan Sekecil Mungkin)**
   - Ubah **hanya** baris yang diperlukan untuk menyelesaikan masalah user.
   - Jangan melakukan refactoring "sekalian jalan" (mengganti nama variabel yang tidak terkait, memindahkan fungsi antar-file, mengubah format kode di luar area kerja, atau menghapus fitur yang sudah ada).
   - Jangan membuat fungsi/komponen *reusable* atau parameter konfigurasi tambahan untuk skenario masa depan yang belum diminta (*no speculative generality*).
3. **Larangan *AI Slop* pada UI & Kode**
   - Jangan menambahkan efek visual berlebihan (seperti *liquid glass* berlapis, bayangan tebal, animasi rumit, atau *wrapper* Composable yang tidak perlu). Gunakan komponen solid dan bersih sesuai tema gelap aplikasi (`Background = Color(0xFF0F0F0F)`, divider `Color(0xFF272727)`).
   - Jangan meninggalkan ekspresi mati (mis. `|| true`), komentar dekoratif panjang (`// ===...===`), atau kode komentar lama yang sudah tidak dipakai.

---

## 3. Aturan Anti Keluar Konteks (Scope Lock & Disiplin Tugas Berat)

Saat menjalankan tugas berat atau multi-langkah, jaga fokus agar tidak keluar dari instruksi user:

1. **Kunci Ruang Lingkup (*Scope Lock*)**
   - Di awal pengerjaan, tetapkan daftar pasti: **(a)** item/masalah yang akan dikerjakan, dan **(b)** file yang boleh disentuh.
   - Jika mengacu pada dokumen rencana atau audit (mis. `PLAN_*.md` atau `anti-slop/audit-*.md`), **HANYA** kerjakan nomor item yang disetujui user pada prompt saat ini. Item lain **harus dibiarkan utuh**.
2. **Eksekusi Bertahap (*Chunking* untuk Tugas Berat)**
   - Jangan mencoba mengubah >3 file besar sekaligus dalam satu langkah tanpa verifikasi.
   - Pecah tugas berat menjadi *checkpoint* kecil:
     1. Baca kode target & pastikan dependensi antar-fungsi.
     2. Terapkan edit pada 1–2 file secara presisi.
     3. Periksa kembali apakah perubahan sudah menjawab permintaan asli user (tidak kurang, tidak lebih).
3. **Verifikasi Kontrak Fungsi (*Signature & Call-Site Check*)**
   - Setiap kali mengubah parameter Composable atau fungsi `internal`, wajib cari seluruh *call-site* di `app/src/main/java/id/flufclip/player/` agar tidak merusak pemanggil di file lain.
4. **Tanya Jika Ambigu, Jangan Mengarang**
   - Jika instruksi user bertentangan dengan kode yang ada atau memiliki dua interpretasi berisiko tinggi, **berhenti dan tanyakan secara singkat** daripada mengarang solusi sendiri.

---

## 4. Resource & Process Cleanup Rules

When performing **compilation, debugging, testing, linting, or static analysis** that consumes significant RAM or CPU, **always clean up and terminate leftover processes as soon as the task is complete** (including after failures or timeouts).

### Mandatory Cleanup Steps

1. **Gradle & JVM / Kotlin Daemons**
   - Prefer `--no-daemon` for one-off builds, compiles, or checks when daemon persistence is unnecessary:
     ```bash
     ./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest
     ```
     *(or `.\gradlew.bat --no-daemon ...` on Windows)*
   - After finishing a build, compile, test, or lint session, stop Gradle daemons:
     ```powershell
     .\gradlew.bat --stop
     ```
     *(or `./gradlew --stop` on Linux/macOS)*
   - Ensure no orphaned `java` / `java.exe` / Gradle / Kotlin compiler daemon processes spawned by the session remain running unnecessarily.

2. **Debug, Logcat & Analysis Processes**
   - Always terminate any background processes started during debugging or analysis (e.g., `adb logcat`, profilers, file watchers, test servers, or long-running CLI tools) once inspection is done.
   - If using agent background tasks, explicitly kill/clean up any lingering tasks before finishing your response.

3. **Verification Before Finishing**
   - Before reporting completion of a build, debug, or analysis task, verify that heavy temporary processes spawned during the session have exited cleanly.

### Safety Guardrails
- **Do not** kill unrelated user processes (such as the user's IDE, browser, or pre-existing emulator/device connections not started by the agent).
- Only clean up processes, daemons, and background tasks associated with the current build, debug, or analysis workflow.

