# Analisis UI layar video dan mini player — YouTube 21.43.523

## Cakupan

APK `youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`. Hasil ini berasal dari pembacaan statis REA/JADX 0.7.1 dan XML/resource APK melalui `aapt`. Tidak ada observasi perangkat atau jaringan. Beberapa nama metode dan state terobfuscasi; nama kelas dan resource adalah petunjuk, bukan bukti bahwa seluruh jalur dipakai pada perangkat tertentu.

## Struktur yang ditemukan

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

## Pertanyaan yang masih terbuka

- Pemetaan state tujuan ke layar watch, mini player, dan kondisi lain, serta urutan callback saat transisi.
- Animasi yang terlihat, kurva, durasi, ambang dan arah gesture pada konfigurasi perangkat tertentu.
- Pemilihan kontrol modern vs floaty, posisi docking, ukuran responsif, dan kapan pivot bar ditampilkan.
- Kontinuitas instance player/Surface dan playback selama minimize, termasuk saat masuk Picture-in-Picture sistem.

Untuk memastikannya diperlukan penelusuran state terobfuscasi lebih lanjut dan observasi perangkat nyata; jangan menyamakan mini player internal aplikasi dengan Picture-in-Picture Android hanya berdasarkan resource ini.

## Perbandingan transisi dengan FlufPlay saat ini

Di FlufPlay, gesture mengumpulkan jarak sentuhan dan memanggil `back()` ketika geser ke bawah mencapai 80 dp. `back()` langsung mengubah `minimized = true`, lalu komposisi mengganti layar watch dengan bar `MiniPlayer`. Tidak ada nilai progres transisi yang menggerakkan posisi, ukuran, atau alpha sepanjang gerakan; buka kembali juga langsung mengubah boolean. Hal ini menjelaskan mengapa perpindahan saat ini tampak mendadak. Lihat `flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt` pada `back()`, `VideoPlayer`, dan `MiniPlayer`.

Pada jalur `FlexyBehavior` yang telah diperiksa di APK YouTube, delta gesture dikirim selama gerakan, kemudian target state dipilih ketika dilepas. `qvu.S` dapat menjalankan `ValueAnimator`, sedangkan `NextGenWatchLayout.onLayout` menggunakan `Rect` dan alpha. Ini mendukung adanya jalur perubahan layout terkoordinasi, namun belum memastikan konfigurasi animasi mini player modern, nilai ambang, durasi, easing, atau pengalaman sebenarnya pada perangkat.

Rancangan implementasi yang dapat diuji: simpan progres drag kontinu, interpolasi batas video dari posisi watch ke posisi mini player, sesuaikan sudut dan alpha elemen yang terlibat, lalu animasikan ke target atau kembali saat dilepas berdasarkan jarak/kecepatan. Arah sebaliknya untuk expand. Playback harus tetap menggunakan instance player yang sama; perpindahan surface dan kontinuitas visual perlu diuji pada perangkat. Nilai awal rancangan bukan hasil pengukuran YouTube dan perlu disesuaikan dari referensi visual.
