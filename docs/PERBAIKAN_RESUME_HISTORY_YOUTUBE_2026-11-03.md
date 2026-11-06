# Perbaikan posisi lanjut History YouTube di FlufPlay

Tanggal: 3 November 2026

## Masalah

Video yang ditonton di FlufPlay sudah muncul di History YouTube dengan garis progres, tetapi ketika dibuka dari History di Brave, video dapat dimulai dari awal. Pengguna membandingkannya dengan tontonan dari aplikasi YouTube di tablet yang dapat dilanjutkan di Brave.

Pada pengujian sebelum perbaikan di tablet Xiaomi `25040RP0AG`, video **Big Buck Bunny** (`YE7VzlLtp-4`) dijeda pada sekitar 0:45 di FlufPlay. Video muncul dengan garis progres di History YouTube pada Brave, tetapi ketika dibuka tidak ada penanda waktu pada URL dan pemutaran dimulai dari awal. Setelah itu, video yang sama diputar di aplikasi YouTube dan dijeda pada 0:42; History pada Brave membukanya dengan `t=42s` dan pemutaran berlanjut di sekitar posisi tersebut. Ini adalah pengujian berurutan pada satu video dan akun, bukan jaminan untuk semua kondisi.

Log FlufPlay sebelum perbaikan menunjukkan respons HTTP 204 untuk beberapa permintaan playback dan watch-time. Respons ini membuktikan adanya respons HTTP, tetapi tidak dengan sendirinya membuktikan bahwa posisi lanjut disimpan oleh YouTube.

## Penyebab yang ditemukan di kode

`PlaybackTracker.stop()` sebelumnya membatalkan job tracking tanpa memakai posisi akhir yang diberikan pemanggil. Saat pause, tracker mengirim segmen tontonan yang telah dikumpulkan, tetapi tidak selalu mengirim pembaruan terpisah untuk posisi pause yang tepat. Kedua temuan ini menjelaskan kelemahan implementasi, meskipun tidak membuktikan seluruh mekanisme internal YouTube.

## Perubahan

Perubahan diterapkan pada `flufplay/app/src/main/java/id/flufclip/player/PlaybackTracker.kt` dan titik penghentian tracker di `MainActivity.kt`:

- Tracker tetap mengirim segmen tontonan berkala, lalu menyiapkan pembaruan posisi akhir ketika pemutaran beralih ke pause atau posisi berubah saat tidak diputar.
- Pembaruan akhir membawa posisi pada `cmt`, `st`, dan `et`, serta `state=paused` dan `final=1`. Posisi dikirim dalam detik dengan tiga angka desimal.
- Saat player ditutup, `stop()` memakai posisi akhir yang diberikan; jika tidak diberikan, tracker memakai posisi terakhir yang diamati.
- Permintaan tracker dijalankan secara serial. Pembaruan akhir pada posisi yang hampir sama tidak dikirim ulang bila yang sebelumnya berhasil.
- Pengiriman tetap memerlukan akun yang sama dan pengaturan **Sync watch time** yang aktif. Logout dan mematikan sinkronisasi menghentikan tracker tanpa meminta laporan akhir.
- Riwayat perangkat di `watch_history` tetap terpisah dari History akun YouTube.

Pengiriman ini menggunakan endpoint internal YouTube dan bersifat *best effort*. Ketersediaan dan format respons dapat berubah; respons HTTP berhasil bukan konfirmasi tersendiri bahwa posisi diterapkan.

## Verifikasi

- Build debug offline berhasil dengan Java 21 dan satu worker.
- Tes terarah `PlaybackTrackingFinalTest` dan `PlaybackTrackingUrlTest` berhasil: total 4 tes, tanpa kegagalan. Tes ini memeriksa format data posisi akhir dan validasi URL, bukan integrasi server YouTube.
- APK hasil build disalin ke `Download/FlufPlay-debug.apk` pada tablet. SHA-256 berkas lokal dan yang disalin cocok: `9c4b83c7af97a3b0610393dc07d20240360e608bdf0c42de37419a9202108815`.
- Setelah APK disiapkan, **pengguna melaporkan telah menguji secara manual dan video berhasil dilanjutkan**. Hasil ini mendukung bahwa perbaikan bekerja pada skenario yang dicoba pengguna. Detail video, posisi, dan jumlah percobaan setelah perbaikan tidak dicatat secara independen.

Belum ada verifikasi menyeluruh untuk semua video, Shorts, live, pergantian akun, kondisi jaringan buruk, atau aplikasi yang dihentikan paksa. Pengujian manual tambahan dapat memakai video biasa yang belum ditonton: putar di FlufPlay, pause dan tutup player, lalu buka History di YouTube/Brave dengan akun yang sama dan periksa posisi awal video.
