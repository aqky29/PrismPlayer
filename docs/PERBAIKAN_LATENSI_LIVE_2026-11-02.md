# Perbaikan latensi livestream FlufPlay

## Masalah dan bukti

Video uji: https://www.youtube.com/watch?v=yvPXR4HPIwQ. Pengguna melaporkan gambar tertinggal sekitar dua menit dibanding browser desktop, termasuk saat pertama membuka siaran.

Pemeriksaan jaringan desktop pada 2026-11-02 memakai NewPipeExtractor v0.26.3 tanpa cookie, sesuai versi dependensi FlufPlay. Respons YouTube menandai siaran sebagai ultra-low-latency. DASH dan HLS tersedia.

Dua sampel DASH menempatkan akhir segmen sekitar 101 detik di depan waktu server menurut pemetaan `availabilityStartTime`, `Period.start`, `presentationTimeOffset`, dan `SegmentTimeline`. Pencocokan nomor segmen DASH 22877 dengan HLS menunjukkan perbedaan pemetaan waktu 103,789 detik. HLS menyediakan `EXT-X-PROGRAM-DATE-TIME`, segmen sekitar satu detik, dan rentang DVR satu jam; DASH menyediakan DVR dua jam.

DASH tidak menyertakan `suggestedPresentationDelay` atau `ServiceDescription`. `DashMediaSource` Media3 1.11.1 memakai target fallback 30 detik dan menghitung posisi awal dari waktu server dikurangi target. Untuk sampel ini, posisi awal diperkirakan tertinggal sekitar 131 detik dari segmen terakhir. Ini hasil perhitungan manifest dan source, bukan pengukuran pemutaran APK sebelum perbaikan.

Rumus Media3: https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer_dash/src/main/java/androidx/media3/exoplayer/dash/DashMediaSource.java (`processManifest`, `updateLiveConfiguration`).

## Perubahan

- Siaran yang masih live mengutamakan HLS jika tersedia. DASH tetap menjadi fallback ketika HLS tidak tersedia. Video biasa dan rekaman mempertahankan pemilihan sumber sebelumnya.
- HLS live diberi target offset lima detik serta rentang kecepatan otomatis 0,97–1,03 untuk mengoreksi penyimpangan kecil. Target ini bukan jaminan latensi aktual; ketersediaan segmen dan jaringan tetap menentukan hasil.
- Membuka live mengabaikan posisi riwayat dan mengembalikan kecepatan dasar ke 1x. Pergantian sumber saat sudah menonton tetap menerima posisi pemutaran yang sedang berjalan. Rekaman tetap bisa melanjutkan riwayat.
- Pemilihan kualitas live HLS tetap membatasi tinggi video melalui track selection.

Tidak ada koreksi waktu tetap 104 detik, karena pergeseran ini tidak boleh diasumsikan berlaku untuk semua siaran. DVR HLS mengikuti rentang yang benar-benar diberikan server; panjangnya bisa berbeda dari DASH. Kasus live yang hanya menyediakan DASH belum dinormalisasi oleh perbaikan ini.

## Validasi

`testDebugUnitTest` dan `assembleDebug` berhasil dengan Java 21, offline, satu worker. Seluruh 18 tes unit lulus, termasuk tiga tes baru untuk pemilihan HLS saat live memiliki kedua manifest, fallback DASH saat HLS kosong, serta pemilihan video biasa/rekaman.

APK: `flufplay/app/build/outputs/apk/debug/app-debug.apk`. Log build: `flufplay/.verification/live-latency-build.log`.

Pemeriksaan whitespace pada file source yang disentuh bersih. Pemeriksaan seluruh checkout juga mendapati blank line di EOF `MiniPlayer.kt` yang sudah ada di luar perubahan ini; file tersebut tidak diubah.

Pemasangan otomatis di Xiaomi `25040RP0AG` tidak berhasil (`INSTALL_FAILED_USER_RESTRICTED`). Pengguna kemudian meminta APK disalin ke folder Download untuk pemasangan manual. Pengukuran offset live, rewind, dan perbandingan gambar terhadap browser pada APK hasil perbaikan belum dilakukan.
