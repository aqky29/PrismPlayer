# Library, playlist, Shorts, dan menu History — YouTube 21.43.523

## Cakupan dan bukti

Referensi: `referensi/youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`. Analisis statis, bukan pengamatan perilaku runtime APK. Kelas aplikasi banyak terobfuscasi dan sebagian layar serta aksi dibentuk oleh respons server.

Evidence REA mentah disimpan di `docs/evidence/library-playlist-rea/`; respons akun dan kredensial tidak disimpan di sana.

- REA `inspect_android_package`: `ev_b328773c74bdf4e2373fb7792f4fd02ca3917f4df4ec112f5457943c88eb94c5`; 53.653 kelas, 17.022 resource, JADX 0.7.1, satu worker, heap 1600 MiB.
- REA `search_android_classes` untuk `Playlist`: `ev_68385163f33b4fe67d12877f92ffca39641f943f7a158c3921af0d2e95685054`. Ditemukan `PlaylistHeaderActionBarView`, `PlaylistThumbnailView`, dan `PlaylistLoopButtonView`.
- REA `inspect_android_class` untuk header: `ev_1e86dd2f6783f9baa3050697c44b6fdbbf8aa586b707b496de19ba74274722b1`. REA `inspect_android_method` `onMeasure`: `ev_d37d9a101eb7ad7498de056638e16730dfa651217f2dde975e79e6e65a5ae2f0`. Metode mengukur lebar anak dan menambah baris ketika lebar tidak cukup; bukti ini tidak menentukan seluruh tampilan playlist.
- Resource `playlist_add_to_entry` memiliki checkbox, judul dua baris, dan padding; `playlist_add_to_fragment` memakai daftar yang dapat digulir. Bundle EML memuat `playlist_add_to_option`, controller checkbox, dan `save_to_playlist_button`. Controller merujuk status tercentang dan perintah model. Ini bukan bukti bahwa semua konfigurasi perangkat memakai layout lama tersebut.
- Resource header: padding item 12 dp, jarak item 8 dp, lebar maksimum 720 dp, padding atas 16 dp atau 24 dp pada `sw600dp`. Ikon antrean next/last menggunakan `yt_outline_experimental_queue_next_black_24` dan `yt_outline_experimental_queue_last_black_24`, masing-masing dengan lima varian density.
- Screenshot tablet 2026-11-03 00:02:10 memperlihatkan menu History dengan antrean berikutnya/terakhir, Watch later, playlist, berbagi, dan hapus riwayat. Screenshot 00:02:00 memperlihatkan Library di bawah History dan Liked videos. Screenshot 2026-11-02 23:44:58 memperlihatkan opsi titik tiga pada kartu Shorts dan video di History.

REA dijalankan terpisah menggunakan `systemd-run --user --scope`, `CPUQuota=50%`, `MemoryMax=2300M`, `nice -n 15`, satu worker dan `REA_JADX_HEAP_MIB=1600`. `cpu.max` dan `memory.max` diperiksa pada proses analisis. Resource dan layout diperiksa terarah dengan `aapt2`; APK tidak dieksekusi untuk analisis ini.

## Respons akun yang dibaca

Pemeriksaan baca saja memakai sesi FlufPlay yang sudah ada di tablet. Cookie dan token tidak dicatat dalam dokumen ini, dan pemeriksaan tidak menambah/menghapus playlist atau riwayat.

- `browse/FElibrary` mengembalikan `lockupViewModel` dengan `LOCKUP_CONTENT_TYPE_PLAYLIST`, ID dan metadata judul. Endpoint `FEplaylist_aggregation` ditemukan dalam navigasi Library dan mengembalikan koleksi lebih banyak daripada shelf awal pada akun yang diperiksa. Pada akun tersebut, ID Liked videos adalah `LL`; `browse/VLLL` mengembalikan 31 video pada halaman pertama. `VLLM` adalah koleksi berbeda dan tidak dipakai sebagai Liked videos.
- `playlist/get_add_to_playlist` menolak body yang hanya berisi `videoId` dengan HTTP 400, tetapi menerima `videoIds` berupa array. Pilihan yang dikembalikan memuat `containsSelectedVideos`, `addToPlaylistServiceEndpoint`, dan `removeFromPlaylistServiceEndpoint`. Keduanya menyediakan `playlistEditEndpoint` dan action yang sesuai. Ini observasi respons WEB saat pemeriksaan, bukan klaim bahwa APK native mengirim payload yang sama.
- Respons History memuat `listItemViewModel` berjudul `Remove from watch history` yang terhubung ke `feedbackEndpoint`; data aksi berasal dari server. Pengiriman aksi hapus tidak diuji.

## Penerapan di FlufPlay

Library akun, layar koleksi, pemilih simpan dan pembuatan playlist menggunakan sesi akun yang ada. Pilihan memuat ulang state server setelah aksi; UI tidak melaporkan keberhasilan bila respons tidak mengonfirmasinya. Aksi tersedia dari menu History, layar tonton, dan menu Shorts. Riwayat perangkat tetap terpisah dari riwayat akun. Menu History berbagi implementasi antrean, berbagi dan simpan; Cast membuka pengaturan pencerminan layar sistem, bukan sesi Google Cast.

Kompilasi dan pengujian lokal tidak membuktikan bahwa endpoint tulis, pembuatan playlist, penghapusan History, playback, atau hasil visual cocok di perangkat. Bentuk respons server, status playlist kosong, akun berbeda dan kelanjutan halaman tetap perlu diuji di aplikasi yang terpasang. Screenshot yang tersedia tidak menunjukkan layar detail playlist maupun pemilih simpan, sehingga kesamaan visual layar-layar itu belum dapat dipastikan.

## Penyempurnaan tampilan koleksi (2026-11-03)

Bagian ini menambahkan bukti screenshot tablet dan respons akun yang dibaca pada 2026-11-03. Referensi visual: `yt-liked-ref2.png`, `yt-watchlater-ref.png`, `yt-savedshorts-ref.png` (tablet 2560×1600, density 360 dpi, 1 dp = 2,25 px). Screenshot FlufPlay pembanding diambil dalam mode split screen.

### Bukti dari respons YouTube

Probe baca saja ke `browse` untuk `VLLL` (Liked videos) dan `VLWL` (Watch later) menunjukkan:

- `playlistHeaderRenderer.cinematicContainer.cinematicContainerRenderer.backgroundImageConfig.thumbnail.thumbnails[0]` berisi URL gambar 405×720. Gambar ini dipakai YouTube sebagai latar header yang diburamkan di klien (`config.applyClientImageBlur = true`).
- `cinematicContainerRenderer.config.darkThemeBackgroundColor` berisi warna dasar header: `0xFF475945` untuk Liked videos dan `0xFF133E72` untuk Watch later.
- `cinematicContainerRenderer.gradientColorConfig[]` berisi tiga stop dengan `startLocation` 0, 0,33, dan 1. Nilai `darkThemeColor` adalah `0xCC475945`/`0xCC133E72`, `0x4C475945`/`0x4C133E72`, lalu `0xFF0F0F0F`. Jadi YouTube menggambar gambar buram, menimpanya dengan gradient ber-alpha, dan menutupnya dengan `#0F0F0F` di bawah header.
- `chipBarViewModel.chips[].chipViewModel` menyediakan filter `All`, `Videos`, dan `Shorts` dengan `selected` dan `tapCommand.innertubeCommand.browseEndpoint` (`browseId` `VLLL`, `params` `wgYCEAE%3D` dan `wgYCGAE%3D`). Nilai `params` diterima YouTube baik dalam bentuk ter-encode maupun sudah di-decode pada probe.
- Metadata header yang dipakai: `ownerText` (`Iqbalhs`), `privacy` (`PRIVATE`), `numVideosText` (`33 videos`), dan `viewCountText` (`No views`).

Pengukuran screenshot referensi: judul header sekitar 36 sp; baris byline sekitar 16 sp; baris metadata sekitar 13 sp; tombol Play all dan dua tombol lingkaran sekitar 34–36 dp; thumbnail Liked videos 2:3 selebar ±240 dp; thumbnail Watch later 16:9 selebar ±284 dp; baris video memakai thumbnail 16:9 ±279 dp pada layar 1138 dp. Saved Shorts memakai judul 32 sp, kartu grid ±142×236 dp (rasio 0,60) tanpa baris views.

### Perubahan kode

- **`WatchHistory.kt`**: `WatchedVideo` mendapat field `viewsText` dan `publishedText` agar metadata dari lockup playlist bisa ditampilkan.
- **`AccountLibrary.kt`**: `AccountPlaylistHeader` menyimpan `backgroundImage` dan daftar `gradient` (posisi, ARGB). `parseVideos` membaca `cinematicContainerRenderer` (gambar latar, warna dasar, tiga stop gradient) dan `chipBarViewModel` (filter All/Videos/Shorts beserta `params`). `videos()` meneruskan `params` pada permintaan filter dan menandai hasil filter Shorts sebagai Shorts. Label header memakai `numVideosText`, `ownerText`, `privacy`, dan `viewCountText` dari respons.
- **`AccountPlaylistScreens.kt`**: layar detail koleksi dipindahkan dari `AccountLibraryUi.kt` ke file ini.
  - Header memakai gambar latar dari YouTube yang di-decode kecil (96 px) lalu di-upscale dengan `ContentScale.Crop`, sehingga tampak buram tanpa `RenderEffect`/layer offscreen tambahan; gambar ditimpa gradient `gradientColorConfig`. Bila data gradient tidak ada, fallback memakai warna dasar ke `#0F0F0F`.
  - Ukuran disesuaikan dengan hasil ukur: judul 36 sp pada lebar ≥ 700 dp dan 24 sp di bawahnya, byline 14 sp, metadata 13 sp, avatar 28 dp, tombol Play all dan tombol lingkaran 36 dp.
  - `Saved Shorts` memakai grid kartu 0,60 dengan judul 15 sp di tepi bawah dan tanpa baris views, sesuai screenshot referensi.
- **`AccountLibraryUi.kt`**: definisi layar playlist lama dihapus agar tidak ada dua implementasi.

### Verifikasi

- `taskset -c 0-1 nice -n 15 ./gradlew :app:compileDebugKotlin --offline --max-workers=1 --console=plain` di `flufplay`: `BUILD SUCCESSFUL`. Kelas hasil kompilasi memuat field baru `backgroundImage` dan `gradient`.
- Pemanggilan `AccountPlaylistScreen` di `MainActivity.kt` sudah memakai parameter `onSave`/`onShare` yang baru.
- Percobaan kompilasi pertama gagal karena cache incremental Kotlin yang basi (unresolved reference ke simbol lintas-file); kompilasi ulang berhasil tanpa perubahan kode.

### Batasan

- APK tidak dibangun, tidak dipasang, dan tidak diuji di perangkat pada sesi ini. Pemuatan gambar latar dan hasil komposisi gradient belum dilihat langsung.
- Gambar latar di-decode 96 px lalu di-upscale; ketajaman dan kecocokan tone akhirnya belum diverifikasi di layar.
- Ukuran di atas adalah pembacaan screenshot JPEG 2560×1600; toleransi beberapa dp tetap ada dan bukan pengukuran layout resmi YouTube.
- Aksi tulis (Play all, filter, hapus dari playlist) belum diuji dengan perubahan nyata pada akun.
