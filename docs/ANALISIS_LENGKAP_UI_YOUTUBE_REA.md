# Analisis Lengkap UI YouTube 21.43.523 dengan REA

## Identitas dan cakupan

- APK: `youtube-21-43-523.apk`
- SHA-256: `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`
- Package: `com.google.android.youtube`; versi `21.43.523` (`1561307650`); min SDK 29, target SDK 37.
- REA menggunakan JADX headless MCP 0.7.1, menemukan 53.653 kelas dan melaporkan 17.022 resource. Bukti: `ev_b2abc6c5b0628ea042312fe682746cd915f3a317ad6d28402f3adbd60d2107ed`.
- Ini analisis statis awal terhadap APK ini. Tidak ada pengamatan tampilan, interaksi, respons jaringan, variasi perangkat, eksperimen, atau verifikasi tanda tangan. Nama kelas yang ditemukan tidak membuktikan kelas tersebut digunakan pada layar tertentu saat aplikasi berjalan.

## Navigasi bawah / PivotBar

`com.google.android.libraries.youtube.rendering.ui.pivotbar.PivotBar` menyediakan operasi untuk membuat atau menambahkan item, mengambil style, menangani sentuhan dan perubahan konfigurasi. Inventaris kelas: `ev_35662fce5d2a73457029dbc7bd107138ae67cc4a7f8f17602cc9b5bc33b7970d`.

- Metode `c(avbz, boolean, int, bkij)` mengonfigurasi item, membaca styled attributes, memasukkan item ke daftar, dan menambahkan view; terdapat pembedaan tipe `PIVOT_BAR_NAVIGATION_TYPE_UNSELECTABLE_TAB`. Bukti: `ev_7c5c9d64c186f8457e4106cb39a1ccc54a728909fd187f958eca3c1100e7f49f`.
- Metode `j(TypedArray, boolean, boolean)` memilih drawable latar dari atribut, dengan kemungkinan membungkusnya memakai warna dan dimensi sebelum `setBackground`. Tidak cukup bukti untuk menyimpulkan efek visual tertentu. Bukti: `ev_0ec06283350a9efd6b1002ef78878e2618618ddce41dae8a779b5d6fa129f652`.
- `aapt dump --values resources` menunjukkan nilai resource default berikut melalui rantai referensi: `pivot_bar_height` → 48 dp; `pivot_bar_image_size` → 24 dp; `pivot_bar_image_mask_size` → 28 dp. Ini nilai resource, bukan pengukuran UI pada perangkat.
- Ada resource `navigation_bar_background_dark` → `#FF0F0F0F` dan `navigation_bar_background_light` → `#FFFFFFFF`; nama resource saja tidak membuktikan bahwa keduanya dipakai sebagai latar PivotBar dalam semua kondisi.

## Player dan time bar

- Kelas `com.google.android.libraries.youtube.player.ui.PlayerView` ada dalam APK; anggota yang terlihat sebagian memakai nama terobfuscasi, sehingga perannya yang lebih rinci belum terpetakan. Bukti: `ev_2c834364d077ffd125148316bf1e82b9c7451cbfdec4864c183f64e82f7c7c60`.
- `com.google.android.libraries.youtube.player.features.overlay.timebar.TimeBar` mempunyai `draw`, `onMeasure`, dan metode accessibility. Bukti inventaris: `ev_de46bbea1067a54501a719dcca589bdd648abe90141c42399b9c16d266d871cf`.
- `draw(Canvas)` menggambar beberapa bidang persegi, sebuah drawable alternatif pada kondisi tertentu, lingkaran pada kondisi tertentu, teks waktu, dan penanda posisi. Kode tidak cukup untuk memberi nama pasti semua lapisan yang dipakai setiap kondisi. Bukti: `ev_f57ea69a6f35d7dac2d5ee8551ec3cc10c39ec537c2f2f3d6b3980efe1d0d81c`.
- `onMeasure` memilih tinggi berdasarkan kondisi dan density; salah satu jalur menggunakan dua kali density. JADX memberi peringatan blok kode terduplikasi. Bukti: `ev_5750fbeda52401952c97086b264af08cb943b5f47c35a76347aa22b38a7d7311`.
- Resource `inline_time_bar_size` bernilai 2 dp; `inline_time_bar_scrubber_default_size` mengarah ke 12 dp, dan `inline_time_bar_scrubber_drag_size` 20 dp. Resource tersebut belum ditelusuri hingga pemakaian pada kelas `TimeBar`, sehingga tidak dapat diasumsikan identik untuk semua tampilan.

## Struktur layar watch dan mini player

Pada varian layout `watch_while_activity_with_next_gen_view_stub_and_bottom_bar_refactor_for_player_reparenting_optimized` (`res/6nv.xml`), terdapat `AccessibilityLayerLayout`, layout konten watch yang di-include, serta footer yang memuat `PivotBar`. Layout yang di-include (`res/AUq.xml`) memiliki `NextGenWatchContainerLayout`, `CoordinatorLayout`, `InlinePlayerContainer`, scrim, dan `ViewStub` menuju `next_gen_watch_layout_no_player_fragment_container` (`res/REd.xml`).

`NextGenWatchLayout` pada `res/REd.xml` menghubungkan atribut `playerViewId`, `metadataViewId`, `floatyBarControlsViewId`, `watchWhileTimeBarId`, `floatyBarTimeBarId`, dan `miniplayerChevronContainerId` ke child atau ViewStub. Artinya layout tersebut menyiapkan tempat untuk player, metadata, dan kontrol yang dapat di-inflate kemudian; XML saja tidak menentukan kapan semuanya aktif.

## Bagaimana gesture dan perubahan ukuran diproses

1. `NextGenWatchLayout` memiliki metode `onInterceptTouchEvent` dan `onTouchEvent`. Pada cabang kode yang diperiksa, ia memeriksa lokasi sentuhan terhadap beberapa `Rect`, kemudian meneruskan event ke objek penangan gesture. REA terpaksa menggunakan smali karena dekompilasi Java untuk kedua metode tidak andal. Bukti: `ev_8350a2141934aa979473ff98ab564ed6bb5acf06335192e877d7491f35c9b21c`, `ev_eac103dfa47a6a62e8aaed54454e4939fac1926fa19df5788721712f3fb443d0`.
2. Kelas `FlexyBehavior` adalah behavior untuk `CoordinatorLayout`. `nR` mencatat gesture awal, memeriksa apakah gerakan memenuhi syarat, dan ketika mengambil alih gesture meminta parent tidak mengintersep sentuhan. Bukti: `ev_0c202c64b6390e16e81f066f7aa40ed102e0c0b17529e4c21aeb50f032d0251c`.
3. Pada metode `m`, saat gerak berlangsung, delta gesture dikirim ke `qvu.O(...)`; ketika sentuhan dilepas, gesture handler memilih salah satu dari dua nilai dan memanggil `qvo.b(0/1, true)`. Pada pembatalan, pelacakan dibersihkan. Bukti: `ev_09e9a70342e16ae2289d7681536c9673b22f124992250076348093b22843d726`.
4. `qvu.O(int)` menerima posisi dalam batas tertentu dan meneruskannya ke perhitungan rasio; `qvu.b(int, boolean)` memilih salah satu dari dua nilai tujuan. `qvu.S` membatasi nilai, menghitung ukuran/keadaan, lalu membatalkan animator sebelumnya dan memilih penerapan langsung atau `ValueAnimator.start()`. Ini menunjukkan jalur interpolasi/animasi berdasarkan state. Bukti: `ev_d48be54ec61978efaa323f4b21d577eaa44d5a3d0594a0a9ff44ffda32cdb349`, `ev_e76c3ade66e324912094abe0ac96096105a15c9ea55208d05f42b639921ab09c`, `ev_267d3f78e02d95b9150812aa81379394ff5afe0d98fa712be352b3025fa7552e`.
5. `NextGenWatchLayout.onLayout` mengambil kumpulan `Rect`, me-layout view pada batas yang dihitung, dan menerapkan alpha untuk sebagian view. Bukti: `ev_7883ffc19627ae880c2e22e83ad2a9ed6fe126936be1da0e3196c3672dd35774`.

Kode ini memperlihatkan mekanisme yang cocok untuk perubahan panel dengan gesture dan animasi. **Belum terpetakan secara pasti** nilai state mana yang berarti video terbuka atau mini player, batas snap, durasi animasi, kurva easing, dan apakah jalur Flexy tersebut mengendalikan mini player modern pada semua perangkat.

## Kontrol mini player

- `modern_miniplayer_controls_overlay` (`res/7l9.xml`) merupakan `ConstraintLayout` yang memenuhi parent. XML memuat kontrol dengan ID close, expand, rewind, forward, action button, progress, skip ad, dan subtitle/badge. Beberapa kontrol disetel tidak terlihat pada kondisi awal XML; kode yang kemudian mengubah visibility belum ditelusuri.
- `floaty_bar_controls` (`res/RgV.xml`) memuat judul, teks sekunder, play/pause, close, progress, dan kontrol rewind/forward. APK menyediakan kedua layout; keberadaannya tidak membuktikan keduanya aktif bersamaan atau mana yang dipilih pada suatu konfigurasi.
- `miniplayer_chevron_container` (`res/CE6.xml`) memuat dua image chevron dan content description. ID aksi aksesibilitas untuk reposition dan resize juga ada dalam resource; belum dibuktikan implementasi atau ketersediaan runtime.
- Nilai resource default: `miniplayer_corner_radius` → 8 dp; `miniplayer_elevation` → 2 dp; `miniplayer_handle_height` → 108 dp; `miniplayer_handle_width` → 24 dp; `miniplayer_wide_handle_width` → 32 dp; `miniplayer_seek_icon_size` → 48 dp. Resource `miniplayer_max_size` dan `miniplayer_inset_offset` memiliki varian konfigurasi; jangan dianggap ukuran aktual pada semua perangkat.

## Player dan pemasangan view

`InlinePlayerContainer.a(view, view2)` menambahkan kedua view bila belum mempunyai parent, lalu menambahkan child container lain; `b(view, view2)` menghapus semua child jika view pertama cocok dengan referensi tersimpan. Bukti: `ev_79025647e6e92144e18dd5b39ae1d8f4bd5845d4540a6f62dfa5e87ed8b38bae`, `ev_8a76ee7fdb296ca167a13f178eb945d16e92bbdf1815378b73cf9a686e563861`. Ini bukti mekanisme pemasangan/pelepasan view, tetapi belum bukti bahwa aksi minimize tertentu memakainya atau bahwa decoder/video surface tetap sama sepanjang transisi.

`YouTubePlayerViewNotForReflection.onLayout` memanggil layout induk lalu memilih warna background; `requestLayout` juga memaksa child tertentu untuk layout ulang. `onTouchEvent` kelas ini mengembalikan false. Bukti: `ev_a985584b184e2f70e616d91dd9a71617b61ecb82ad006b779f07901b0d84335d`, `ev_0a0348c4175a8471c2759e5263c454c9e984234cdb566713235393ec68718655`, `ev_7cc1d024db2353fa799157b495157064d0a390d2bb7b7037998033da07f2066a`.

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

## Resource dan kelas lain yang ditemukan

- Resource layout meliputi `modern_miniplayer_controls_overlay`, `watch_while_time_bar`, `watch_while_timebar_overlay`, dan beberapa layout bottom sheet. Ini menunjukkan keberadaan aset, bukan hierarki layar aktual.
- Inventaris nama kelas mencakup `DefaultTabsBar`, `ReelPlayerView`, `ShortsPlayerView`, `YouTubeButton`, dan `YouTubeTextView`; belum ditelusuri alur layar dan penggunaannya.
- APK juga memuat `assets/youtube_sans.xml`, beberapa definisi bobot YouTube Sans, dan `assets/youtube_sans_bold_font.ttf`. Tidak semua teks UI pasti menggunakan font tersebut.

## Pertanyaan yang masih terbuka

- Pemetaan state tujuan ke layar watch, mini player, dan kondisi lain, serta urutan callback saat transisi.
- Animasi yang terlihat, kurva, durasi, ambang dan arah gesture pada konfigurasi perangkat tertentu.
- Pemilihan kontrol modern vs floaty, posisi docking, ukuran responsif, dan kapan pivot bar ditampilkan.
- Kontinuitas instance player/Surface dan playback selama minimize, termasuk saat masuk Picture-in-Picture sistem.

Untuk memastikannya diperlukan penelusuran state terobfuscasi lebih lanjut dan observasi perangkat nyata; jangan menyamakan mini player internal aplikasi dengan Picture-in-Picture Android hanya berdasarkan resource ini.
