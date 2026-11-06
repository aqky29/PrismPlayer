# Thumbnail dan minimize player offline

## Penyebab sebelum perubahan

`DownloadThumbnail` mengutamakan frame awal file video. Cover YouTube sebenarnya sudah dicache saat download, tetapi hanya dipakai setelah ekstraksi frame tidak menghasilkan gambar. Itu menjelaskan mengapa thumbnail dapat berbeda dari cover aslinya.

Watch offline baru memakai kontrol utama, tetapi belum memakai handler swipe milik floating player. Selain itu, callback kembali mengubah selectedUri menjadi null. Efek pergantian source kemudian memanggil stop/clearMediaItems. Dengan model ini, kembali ke daftar berarti menutup file, bukan memperkecilnya.

## Thumbnail sekarang

Urutan tampilan:

1. Cover asli yang sudah disimpan ke file internal saat download (`DownloadVideo.thumbnail`). Tidak mengambil ulang gambar jaringan untuk baris file selesai.
2. Jika cover tidak tersimpan atau gagal dibaca, ekstrak frame awal dari file video lokal pada IO.
3. Jika keduanya gagal, tampilkan ikon putar. Jangan mencoba lagi cover yang sudah gagal pada render fallback.

Ekstraksi frame tetap satu retriever sekaligus, ukuran maksimum 384×216, dan LRU 12 gambar. Cover yang valid tidak lagi memerlukan ekstraksi frame. Pada Android API26, ekstraksi scaled frame tidak tersedia sehingga fallback terakhir berupa ikon, sementara cover lokal tetap dapat ditampilkan. File lama yang belum memiliki metadata cover memakai fallback frame pada API27+.

## Miniplayer sekarang

- Status UI minimized dipisahkan dari selectedUri. Swipe turun atau chevron memperkecil player ke atas layar Unduhan; source file tidak diubah, di-stop, atau di-prepare ulang karena perubahan mode tersebut.
- Tombol miniplayer X menutup file. Ketuk video memperbesar; drag, pinch, double tap resize, corner snap, dan stash memakai `FloatingPlayer`, `MiniPlayerControls`, dan `MiniPlayerState` yang sama dengan player online.
- Gesture watch mengamati initial pointer pass, hanya mengambil gerakan turun yang dominan vertikal, dan menyisakan area scrub 40dp paling bawah. Commit memakai jarak minimum 64dp atau 35% tinggi player, atau fling turun di atas 800dp/s setelah melewati touch slop. Fling balik ke atas di bawah -800dp/s membatalkan minimize meskipun jarak turun sudah cukup. Nilai ini merupakan pilihan implementasi/adaptasi FlufPlay, bukan pengukuran runtime YouTube.
- Dalam fullscreen, swipe/chevron keluar fullscreen terlebih dahulu. Back pada watch memperkecil; Back berikutnya menutup miniplayer. Tombol tutup tetap terpisah dari expand/minimize.
- Saat pengguna berpindah tab/channel ke MainActivity, `OfflinePlaybackBridge` menyerahkan kepemilikan player ke miniplayer utama. Player dibuat dengan applicationContext. Listener dan pelepasan player pada activity lama dibatasi agar tidak mematikan player yang telah diserahkan.
- Saat miniplayer utama dibesarkan, DownloadsActivity mengambil player yang sama. Jika mediaId sama dengan URI tersimpan, activity tidak memuat ulang source atau seek ke posisi cache lama.
- Playback online lama dihentikan ketika menerima miniplayer offline; membuka video online lain menutup miniplayer offline. Jadi dua sumber tidak sengaja diputar bersamaan.
- Antrean/autoplay lokal dipertahankan ketika memperkecil dan saat handoff. Aplikasi dijeda ketika masuk background; ini miniplayer internal aplikasi, bukan implementasi PiP/background media service baru.

## Berkas terkait

- `DownloadScreens.kt`: prioritas cover/fallback dan handoff ketika navigasi.
- `DownloadedWatch.kt`: pemasangan gesture pada permukaan player.
- `DownloadsActivity.kt`: status minimized, pemilik player, adopsi source yang sama, close vs minimize.
- `OfflineMiniplayer.kt`: handler swipe, renderer mini bersama, dan bridge kepemilikan antar-activity.
- `MainActivity.kt`: akses shared FloatingPlayer, overlay offline di halaman utama, dan penyelesaian lifecycle.

## Verifikasi

Build, unit test, dan lint dibatasi satu inti CPU, nice15, Java21, satu worker, heap1536MiB, Kotlin in-process, offline, tanpa daemon persisten. Pemeriksaan pertama berhasil dengan 33 tes tanpa kegagalan dan lint tanpa error; build final juga berhasil dalam 5m5s: 33 tes lolos, nol kegagalan, lint 0 error dan 77 warning. Log: `flufplay/.verification/offline-mini-thumbnail-release-check.log`.

Tiga tes baru memeriksa gerakan naik/pendek tidak commit, jarak/fling turun yang memenuhi syarat, serta batas mini player yang menyisakan ruang navigasi. Tes tersebut tidak mensimulasikan seluruh dispatch sentuh Android atau kontinuitas surface. Tidak memakai, memasang, atau mengambil screenshot perangkat dalam perbaikan ini. Tampilan dan gesture runtime belum diverifikasi pada perangkat.

APK lokal: `flufplay/app/build/outputs/apk/debug/app-debug.apk`. SHA-256: `8ffe1548ad72612d0afcc09997a60910224eb43c44d9cf7662b6957674e47b4d`. [Hasil verifikasi](evidence/offline-mini-thumbnail/verification.json). Tidak dikirim atau dipasang ke perangkat.
