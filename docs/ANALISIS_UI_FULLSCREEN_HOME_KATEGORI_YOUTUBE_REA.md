# Analisis fullscreen, Home, dan kategori — YouTube 21.43.523

APK `youtube-21-43-523.apk`; SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`. Analisis statis menggunakan REA/JADX dan `aapt` terhadap resource APK. Nama resource atau komponen tidak membuktikan bahwa elemen muncul pada semua akun atau perangkat; aplikasi tidak dijalankan.

## Fullscreen dan kontrol video

Resource `youtube_controls_overlay` (`res/zmf.xml`) memiliki scrim, scrim gradien atas/bawah, loading, dan `ViewStub` untuk controls layout, quick seek, zoom, fine scrubbing, dan time bar. `youtube_controls_layout` (`res/HrB.xml`) menyertakan collapse, overflow, autonav, container kontrol tambahan, dan stub untuk group tombol serta kontrol bawah. Stub memungkinkan pembuatan view saat diperlukan, tetapi waktu pemunculannya belum ditelusuri.

`youtube_controls_bottom_ui_container` (`res/QYO.xml`) menyiapkan teks waktu saat ini dan durasi, label live, judul chapter, mute, quick actions, dan `youtube_controls_fullscreen_button_stub`. Stub tersebut merujuk `youtube_controls_fullscreen_button` (`res/fTD.xml`), berupa `TouchImageView` dengan ID `fullscreen_button` dan ukuran layout 60 × 60 dp. XML ini membuktikan tombol dan tempatnya, bukan cara callback mengubah orientasi atau mode sistem.

`youtube_controls_button_group_layout` (`res/zi9.xml`) memuat play/pause/replay, rewind, fast forward, previous, dan next; masing-masing ditempatkan dalam area sentuh. Ketersediaan setiap tombol bergantung keadaan yang belum ditelusuri. Resource lain menyediakan opsi overflow, subtitle, live chat, judul dan mini player; tidak berarti semua tampil bersamaan.

Untuk overlay panel fullscreen, `FullscreenEngagementPanelCoordinatorLayout.onMeasure` mempunyai jalur pengukuran yang menghitung lebar dari ruang layar dan batas min/maks yang dikonversi melalui display metrics; jalur lain meneruskan ukuran biasa. Bukti REA `ev_d1282161e705fb3d62a50ef439f3998efb5ab0556ee989ec8ca8474543bb941b`.

Terdapat juga beberapa varian layout watch dengan akhiran `fullscreen`, serta `fullscreen_watch_next_chips`. Penamaan tidak membuktikan kondisi pemilihan varian. **Belum ditemukan callback tombol fullscreen, pemetaan orientasi, perubahan system bars/insets, atau urutan enter/exit fullscreen.** Jangan menyimpulkan bahwa rotasi otomatis terjadi dari XML saja.

## Isi kartu video yang tersedia

`video_item_layout` (`res/pNR.xml`) memuat area thumbnail, judul, author, details, menu kontekstual, dan badge opsional termasuk privacy. `video_card_item` (`res/7py.xml`) memuat thumbnail, judul, author/details melalui ViewStub, menu, dan badge opsional. `compact_video_item` (`res/PLN.xml`) memakai `SwipeLayout` dan memuat thumbnail, avatar channel, judul, author/details, menu, serta beberapa ViewStub badge. Susunannya berbeda antar layout.

Ini membuktikan struktur kartu yang disertakan APK, tetapi **belum membuktikan layout mana dipakai oleh Home** pada kondisi tertentu atau bahwa isi `details` selalu berupa jumlah view dan usia video. Data video aktual, pemilihan rekomendasi, urutan feed, akun, dan respons server tidak ditentukan oleh XML tersebut. APK juga memiliki layout promoted video, namun inventaris nama tidak membuktikan posisi atau frekuensi iklan di feed.

## Kategori dan tab

APK memiliki `chip_cloud_chip_modern_text`, varian chip dengan ikon/avatar/checkbox, dan `ChipCloudView`. `ChipCloudView.onMeasure` mengukur child, membatasi jumlah baris/child serta memindah chip ke baris baru ketika ruang tidak cukup; `onLayout` menempatkan chip dan mendukung arah RTL. Bukti REA: `ev_aceac3c6ddfcc922c0a5c31583cca557f0f8191dc4fa316b2e46b07ba3ac397e`, `ev_303d2f91e66d5470b25546c71dc72fe97dc8d479ed24e814c69d112666581594`. Ini komponen chip generik; belum dibuktikan komponen yang sama dipakai sebagai kategori Home dalam semua varian.

Kelas `rendering.ui.hometabs.DefaultTabsBar` membuat tab dari teks dan ikon yang diterima sebagai parameter, memberikan content description, dan mengatur pewarnaan. Bukti: `ev_869488263ae462ee6c7f729720007ce6c39334ef1ed59635178e5370952e596d`. Metode `i(int, boolean)` mengganti pemilihan tab dan pada kondisi tertentu memindahkan `RtlAwareViewPager` ke halaman yang sesuai. Bukti: `ev_ef42f07422ec0d8fb80a0482653ab68aa9ca45d148609cb724f654dc8a1bc8f8`. Tab Home dan chip filter kategori adalah komponen/konsep berbeda; hubungan langsung keduanya belum terbukti.

Belum ditemukan daftar kategori aktif, asal label kategori, request saat kategori dipilih, atau hubungan pemilihan chip dengan refresh feed. Label yang berubah berdasarkan akun/kondisi harus diamati dari runtime atau ditelusuri lebih lanjut di binding dan data respons, bukan ditebak dari nama resource.
