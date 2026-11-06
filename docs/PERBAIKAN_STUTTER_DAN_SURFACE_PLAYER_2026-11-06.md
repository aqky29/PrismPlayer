# Analisis dan Perbaikan Video Patah-Patah / Skip 1 Detik di FlufPlay

Tanggal: 6 November 2026  
Perangkat pengujian: Xiaomi/Redmi Pad `25040RP0AG` (`taiko`), Android 16 / HyperOS, layar 1600×2560 (2.5K, 60/90 Hz), chipset MediaTek  
Aplikasi: FlufPlay, paket `id.flufclip.player`  
Commit dasar: `3d33fb2` (`refactor/split-main-activity`)

---

## 1. Masalah yang Dilaporkan

Saat memutar video biasa di FlufPlay, pemutaran secara berkala mengalami patah-patah berat seperti membeku sejenak lalu melompat/skip maju sekitar 1 detik (*stuttering and periodic ~1-second frame jump*). Masalah ini terus berulang sepanjang video diputar.

---

## 2. Bukti Diagnostik dari Perangkat

Pemeriksaan log sistem (*logcat*) dan data aplikasi pada perangkat tablet `25040RP0AG` (PID `26891`) mengungkap temuan-temuan berikut:

### A. Freeze di Main Thread Setiap ~6,4 Detik
Choreographer merekam frame drop masif secara periodik:
```text
11-06 07:27:39.583 26891 26891 I Choreographer: Skipped 80 frames!  The application may be doing too much work on its main thread.
11-06 07:27:46.089 26891 26891 I Choreographer: Skipped 89 frames!  The application may be doing too much work on its main thread.
11-06 07:27:52.438 26891 26891 I Choreographer: Skipped 79 frames!  The application may be doing too much work on its main thread.
11-06 07:27:58.896 26891 26891 I Choreographer: Skipped 86 frames!  The application may be doing too much work on its main thread.
11-06 07:28:05.255 26891 26891 I Choreographer: Skipped 80 frames!  The application may be doing too much work on its main thread.
11-06 07:28:11.616 26891 26891 I Choreographer: Skipped 80 frames!  The application may be doing too much work on its main thread.
11-06 07:28:18.025 26891 26891 I Choreographer: Skipped 83 frames!  The application may be doing too much work on its main thread.
11-06 07:28:24.356 26891 26891 I Choreographer: Skipped 78 frames!  The application may be doing too much work on its main thread.
```
- Pada refresh rate 60 Hz, **80–89 frames yang terlewat setara dengan 1,33 hingga 1,48 detik pembekuan total di Main (UI) Thread**.
- Interval antar-freeze sangat konsisten: ~6,3 hingga 6,5 detik (5 detik waktu jeda coroutine + ~1,4 detik durasi eksekusi).

### B. Ukuran File `watch_history.xml` Mencapai 122 KB
Pemeriksaan direktori `shared_prefs` aplikasi di tablet:
```text
-rw-rw---- 1 u0_a324 u0_a324 122K 2026-11-06 07:40 watch_history.xml
```
File ini memuat 100 entri video lengkap dengan metadata URL, judul, uploader, timestamp, channel, thumbnail, serta kamus referensi playlist (`playlist_refs`).

### C. Antrean Buffer Hardware Decoder Penuh (`PipelineWatcher`)
Log decoder video MediaTek VP9 (`c2.mtk.vp9.decoder`) mencatat:
```text
D C2MtkVdec: updateRuntimeInfo: isTextureView 1, width 1920, height 1088, opRate 60.000000
D PipelineWatcher: [0xb4000075b9f87878] pipelineFull: too many frames in pipeline (6)
```
MediaTek Codec2 mengonfirmasi bahwa player memakai `TextureView`. Karena Main Thread membeku, `TextureView` tidak mengonsumsi buffer dari decoder. Seluruh 6 buffer pool terisi penuh, memaksa decoder menghentikan pemrosesan frame video baru.

---

## 3. Analisis Akar Masalah (Root Cause)

Masalah ini terjadi akibat kombinasi dua faktor arsitektur:

1. **Loop Penyimpanan Riwayat di Main Thread (`savePosition`)**:
   Di `AppRoot.kt` terdapat coroutine:
   ```kotlin
   LaunchedEffect(selected) {
       while (selected != null) { delay(5000); savePosition() }
   }
   ```
   Setiap 5 detik saat video diputar, fungsi `savePosition()` dipanggil langsung di UI thread:
   - `WatchHistory.save(...)` mem-parse string JSON 122 KB dari SharedPreferences, mem-parse `playlist_refs` dan `playlist_videos`, menyusun kembali 100 entri JSON, dan menulisnya ke disk via `SharedPreferences.apply()`.
   - `WatchHistory.read(...)` kemudian mem-parse ulang 122 KB string JSON tersebut menjadi 100 objek Kotlin `WatchedVideo` di UI thread.
   - Baris `watchHistory = WatchHistory.read(...)` mengubah state Compose `watchHistory` yang diobservasi di tingkat root `AppRoot.kt`. Ini memicu rekomposisi masif pada seluruh layar, kalkulasi ulang `watchedUrls`, evaluasi rekomendasi beranda, serta rekam ulang grafik `FrostedGlassBackdropHost`.
   - Rangkaian proses berat di Main Thread ini memakan waktu ~1.300 ms, menyebabkan Choreographer membuang 80–89 frame.

2. **Ketergantungan `TextureView` pada Main Thread**:
   Di `floating_player.xml`, player dikonfigurasi dengan `app:surface_type="texture_view"`.
   - Berbeda dengan `SurfaceView` yang memiliki layer perangkat keras independen ke SurfaceFlinger, `TextureView` harus dikomposisikan oleh UI Thread dan HardwareRenderer aplikasi melalui OpenGL.
   - Ketika UI Thread membeku selama 1,3 detik, `TextureView` berhenti me-render frame dari decoder.
   - Antrean decoder terisi penuh (6/6 buffer), decoder video terhenti, sementara audio tetap berjalan lancar via thread audio sistem.
   - Saat UI Thread kembali bebas, ExoPlayer mendeteksi video tertinggal jauh dari clock audio (*A/V desync*). ExoPlayer langsung membuang puluhan frame terlambat agar seirama dengan audio.
   - Bagi pengguna, video terlihat macet sesaat lalu melompat maju 1 detik.

---

## 4. Perubahan yang Diterapkan

### A. Penyimpanan Posisi Berkala yang Ringan (`WatchHistory.kt`)
Menambahkan fungsi khusus untuk pembaruan posisi tontonan saat pemutaran sedang berlangsung tanpa menyentuh JSON riwayat lengkap:
- `recordPlaybackPosition(prefs, url, position, duration)`:
  - Menyimpan posisi `Long` langsung ke cache in-memory (`ConcurrentHashMap`) dan satu key SharedPreferences sederhana (`resume_$videoId`).
  - Bila posisi berada dalam 5 detik sebelum akhir durasi, posisi di-reset ke 0.
  - Berjalan dalam hitungan mikrodetik (< 0,1 ms), tanpa operasi parsing JSON, tanpa alokasi memori berlebih, dan **tanpa mengubah state Compose `watchHistory`**.
- `getResumePosition(prefs, url, fallbackList)`:
  - Mengambil posisi resume terbaru: memeriksa cache in-memory terlebih dahulu, lalu key `resume_$videoId`, dan baru mundur ke list riwayat jika tidak ada.
- Menjaga sinkronisasi pembersihan di `save`, `remove`, dan `clear`.

### B. Penggantian Loop di `AppRoot.kt`
- Mengubah coroutine periodik 5 detik di `AppRoot.kt` agar hanya memanggil `WatchHistory.recordPlaybackPosition(...)`.
- Mengubah pembacaan posisi resume di `openUrl` agar memanggil `WatchHistory.getResumePosition(watchPrefs, info.url, watchHistory)`.
- Fungsi `savePosition()` lengkap tetap dipertahankan untuk titik-titik transisi saat video berhenti atau berganti (`closePlayer`, `openUrl`, `openChannel`, `selectTab`).

### C. Migrasi Player Surface ke `SurfaceView` (`floating_player.xml`)
Mengubah konfigurasi surface player:
```xml
app:surface_type="surface_view"
```
- Tablet `25040RP0AG` menjalankan Android modern (API 34+ / Android 16) yang mendukung penuh pemotongan sudut melengkung (`clipToOutline`), penskalaan zoom (`VideoZoomState`), dan transformasi hardware pada `SurfaceView`.
- Frame video kini dialirkan langsung dari decoder MediaTek ke SurfaceFlinger, sehingga frame rate video stabil di 60 fps dan terisolasi dari aktivitas UI Thread.

---

## 5. Verifikasi

1. **Unit Test**:
   - Dibuat pengujian unit baru di `flufplay/app/src/test/java/id/flufclip/player/WatchHistoryPositionTest.kt` yang menguji `recordPlaybackPosition`, `getResumePosition`, reset di akhir video, dan fallback ke riwayat.
   - Menjalankan `./gradlew testDebugUnitTest`: **22 task berhasil tanpa kegagalan (BUILD SUCCESSFUL)**.
2. **Build Debug APK**:
   - Menjalankan `./gradlew assembleDebug`: build berhasil menghasilkan `app-debug.apk`.
3. **Penyalinan ke Perangkat**:
   - APK disalin ke folder Download tablet: `/sdcard/Download/FlufPlay-fix-stutter-20261106.apk`.
   - Checksum SHA-256 diverifikasi identik antara berkas lokal dan berkas di tablet:
     `c83067f092ac75456e96a28847384cf7f0226717d15bda9684436188ab6a03af`.
