# Gestur, UX, dan app bar Shorts YouTube 21.43.523 serta rekomendasi untuk FlufPlay

> **Status implementasi (2026-10-31, dibuild, belum diuji di perangkat):** rekomendasi prioritas 1 dan 2 di bagian "Seharusnya seperti apa" sudah dikerjakan di FlufPlay; rinciannya di bagian "Tahap ketiga" pada [`ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md`](ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md). Tabel "FlufPlay sekarang vs YouTube" di bawah menggambarkan keadaan **sebelum** perubahan itu. Prioritas 3 (tarik-ke-segarkan, geser kiri, cubit) belum dikerjakan.

Dibuat 2026-10-31. Semua temuan **statis** (APK tidak dijalankan). Melengkapi [`ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md`](ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md), yang membahas sumber feed dan pager.

## Kesimpulan

1. Layar Shorts YouTube punya **tujuh gestur** yang bisa ditunjukkan di APK: geser vertikal antar-Short, ketuk (putar/jeda), ketuk dua kali (suka), tahan (kecepatan 2x), geser kiri (aksi horizontal), cubit (zoom), dan geser di bar progress (scrub dengan pratinjau). Tarik-ke-segarkan di Short pertama terlihat di layout. FlufPlay baru punya dua: geser vertikal dan ketuk untuk jeda. "Tahan" bukan satu gestur tetapi keluarga: 2x dari zona tepi (20% kiri dan kanan layar), kunci 2x dengan menarik ≥ 50 px ke bawah saat menahan, dan tahan di tengah yang di mode unduhan offline menampilkan pesan (lihat "Gestur tahan di Shorts").
2. Pengendali geser vertikal bukan sekadar RecyclerView. Ada mesin status sentuhan sendiri (`atiw`, kelas ter-obfuscate) yang memisahkan geser vertikal, geser horizontal-kiri, tahan, dan multi-jari. Saat tahan aktif, pager berhenti menggulir.
3. Umpan balik visual dibuat dengan animasi Lottie, bukan ikon statis: hati 240 dp (di titik ketuk bila flag `atot.y()` aktif, kalau tidak di tengah), ikon putar/jeda 96 dp di tengah, lencana "2x" 32 dp di tengah-atas.
4. Bilah bawah (PivotBar) menelan semua sentuhan dan mencari tab dari **koordinat sentuh**, bukan dari view yang kena sentuh. Geser yang dimulai di bar diteruskan ke konten di atasnya.
5. Yang paling terasa bedanya di FlufPlay: tidak ada ketuk dua kali, tidak ada tahan-2x, bar progress 2 dp tidak bisa disentuh, dan jeda hanya ikon kecil 54 dp tanpa animasi. Rincian dan urutan kerja ada di bagian "Seharusnya seperti apa".
6. **Belum terbukti dari APK:** apakah header atas feed Shorts memakai judul "Shorts" atau hanya ikon, dan apakah bilah bawah selalu tampil di atas Shorts. Dua hal itu butuh tangkapan layar YouTube asli (lihat "Belum terbukti").

## Batas analisis

- Alat: REA (JADX headless 0.7.1) untuk kelas dan metode; `aapt2` untuk resource, layout, dan string; `dexdump` untuk memetakan string dan ID resource ke kelas.
- Nama kelas di luar paket `com.google.android...` sudah di-obfuscate. Peran kelas di bawah **disimpulkan dari cara pakainya**. Kelas yang berasal dari paket Reel/Shorts diberi label "Shorts"; kelas yang tidak bisa dipastikan diberi label "belum pasti".
- Ambang, flag, dan eksperimen banyak yang berasal dari konfigurasi remote (mis. `45613566`, `45855104`, `45761824`). Nilai efektifnya di perangkat **tidak bisa diketahui dari APK**.
- **Dekompilasi JADX bisa menghilangkan pernyataan.** Konstruktor `mna` di JADX tidak menampilkan dua baris penulisan field (`q`, `r`) yang ada di bytecode. Konstanta yang menentukan kesimpulan (ambang, waktu, ID flag) diperiksa ulang lewat `dexdump -d`, bukan hanya dari keluaran JADX.
- Ukuran dp berasal dari resource (`aapt2 dump resources`), bukan pengukuran layar. Layout `reel_player_right_*_offline` bukan bukti geometri overlay online (lihat dokumen Shorts).

## Peta gestur Shorts

Kolom "Bukti": **K** = terbaca di kode, **L** = terlihat di layout/resource, **S** = hanya string atau nama kelas.

| Gestur | Perilaku yang terbukti | Bukti |
|---|---|---|
| **Geser vertikal** | `atiw` (OnItemTouchListener untuk pager) mencatat titik sentuh awal. Geser dianggap vertikal bila perpindahan Y lebih dari **150 px** dan sudutnya tidak lebih dari **22,5°** dari sumbu vertikal; arah ke atas atau bawah dikirim ke halaman aktif lewat `n(x, y, arah)`. 150 px adalah piksel mentah, tidak dikali density (sekitar 50 dp di layar 3x). Angka ini untuk klasifikasi arah, bukan ambang pindah halaman. Pindah halaman sendiri dikerjakan RecyclerView dengan smooth scroller kustom. | K |
| **Ketuk** (putar/jeda) | Penangan sentuh per halaman `mna.c` (dipakai `mpo`, pengikat overlay Shorts). Ketuk dihitung bila jari diangkat dalam **199 ms** (`q = max(199, ViewConfiguration.getTapTimeout())`; getTapTimeout bawaan Android 100 ms, jadi praktisnya 199 ms) dan bergeser tidak lebih dari touch slop (`getScaledTouchSlop()`). Bila **ketuk dua kali mungkin** (`E() != 1`), aksi ketuk tunggal **ditunda `r` ms** menunggu ketukan kedua, dengan `r = ViewConfiguration.getDoubleTapTimeout()` (bawaan Android 300 ms); bila tidak mungkin, langsung. Aksinya (`mna.e`): bila sedang diputar, simpan keadaan lalu jeda dan animasikan ikon jeda; bila sedang dijeda oleh ketuk, lanjutkan dan animasikan ikon putar. Ikon Lottie `reel_feedback_play` / `reel_feedback_pause` berukuran **96 dp** di tengah; ada lapisan keadaan jeda (`reel_player_paused_state`) dengan scrim gradasi. Dekompilasi JADX menghilangkan dua baris di konstruktor `mna` yang menulis `q` dan `r`; keduanya terbaca dari bytecode (`iput` di offset `0x36` dan `0x3c`). | K, L |
| **Ketuk dua kali** (suka) | Di `mna.c`, ketukan kedua dalam jendela tunggu memanggil `mnb.H()` (bila flag `atot.bQ()`) dan, bila halaman bisa disukai (`E() == 2`) dan pengendali animasi ada, `atgc.a(atnp, false, event)`: hati Lottie `reel_feedback_like` **240 dp**. Bila flag `atot.y()` aktif, hati diposisikan **di titik ketuk** (`setX(x − lebar/2)`, `setY(y − tinggi/2)`); bila mati, di tengah. Ada varian hati dengan Lens. Efek ke server (mengirim suka) tidak dibaca; kelas `ReelTapLikeControllerImpl` ada. | K, L |
| **Tahan** | Keluarga gestur, bukan satu aksi; rinci di bagian "Gestur tahan di Shorts" di bawah. Ringkasnya: tahan di **zona tepi** (20% lebar layar di kiri dan kanan) = **2x** selama ditahan; tarik ke bawah ≥ 50 px saat menahan lalu lepas = **kunci 2x**; tahan di **zona tengah** menjalankan perintah tahan dari server (membuka panel deskripsi; lihat koreksi di bagian "Gestur tahan di Shorts"). Pesan "unduhan offline" hanya untuk Shorts offline. Selama menahan, pager tidak menggulir dan UI (kolom kanan, footer, lapisan interaksi) memudar. | K, L, S |
| **Geser kiri** | Di `atiw`, keadaan 5 dimasuki bila bit konfigurasi `16` aktif, halaman mendukung, jarak X melebihi slop, sudut berada di jendela **67,5°–112,5°** dari vertikal (sekitar horizontal), dan arahnya ke kiri. Saat jari diangkat dalam keadaan ini, callback `bT()` dipanggil. Bila jari kembali ke kanan lebih dari 0,8 × slop, keadaan batal. Ada juga layout `reel_horizontal_pager` (RecyclerView horizontal). Tujuan aksi (mis. halaman channel) tidak dibaca. | K, L |
| **Cubit** (zoom) | `atnq` (ScaleGestureDetector): skala dibatasi **0,9–4,0**; mode zoom masuk bila rentang jari > **1,2 ×** rentang awal, keluar bila < **0,833 ×**; fokus jari menggeser view. Kunci `shorts_pinch_to_zoom_active_global_key` ditulis oleh `atnm`. Selama zoom atau lebih dari satu jari, `atiw` membatalkan keadaan geser sendiri. `atnq` juga diberikan ke kelas `mna` (peran belum pasti), jadi fitur ini kemungkinan dipakai bersama dengan layar lain. | K |
| **Geser di bar progress** (scrub) | `ReelScrubbedPreviewView` + layout `reel_scrubbed_preview_view` / `..._extended_timestamp`: thumbnail berbingkai putih 1 dp dan stempel waktu **24 sp** berjarak 16 dp di bawahnya. `ReelTimeBarController` dan view acuan `time_bar_reference_view` (tinggi **1 dp**, di tepi bawah fragmen) ada. Status mode bersih: `REEL_CONTENT_CLEAR_MODE_STATE_ACTIVE / GESTURE_IN_PROGRESS / INACTIVE`, dengan tombol keluar `reel_clear_mode_exit_button` (kanan-bawah, padding 12 dp). Jadi saat menggeser bar, overlay disembunyikan. Pemicu persis mode bersih tidak dibaca. | L, S |
| **Tarik-ke-segarkan** | `reel_watch_refresher` adalah `SwipeRefreshLayout` yang membungkus `reel_recycler`. Scrim atas bertambah dari 96 dp ke **140 dp** pada varian refresh. | L |
| **Geser cepat ditelan** | `ReelTouchCaptureView` (`reel_recycler_touch_capture`, awalnya `gone`, layar penuh): pada `ACTION_DOWN`, bila flag `45613566` mati, ia mencatat `REEL_FAST_SWIPE_SUPPRESSED` dan **menelan sentuhan**; bila aktif, mencatat `REEL_FAST_SWIPE_ALLOWED` dan meneruskan. Kapan view ini ditampilkan tidak dibaca. Artinya YouTube punya mode yang mengabaikan geser baru selagi transisi belum selesai; **jangan ditiru**. | K |
| **Aksesibilitas** | Kontrol sebelumnya / putar-jeda / berikutnya 48 dp (`reel_player_navigation_controls`), hanya relevan untuk pembaca layar. | L |
| **Edukasi pertama** | Lapisan hitam alpha 80% dengan teks "Swipe up for next video" dan animasi Lottie (`reel_player_edu_vertical`). | L |

**Koreksi (revisi pertama dokumen ini salah):** `mmz` dan `mna` sebelumnya saya keluarkan dari bukti Shorts dengan dugaan bahwa keduanya milik pemutar tontonan. Itu keliru. `mna` menerima `atot` (konfigurasi Reel), `atnn`, `atnq`, dan `mnb`. `mpo`, pengikat overlay Shorts yang memuat `ShortsPlayerProgressPresenter` (field `d`), memegang `mna` (field `b`) dan mengimplementasi `mnb` (hasil pemindaian `dexdump`). `zyq.Y` hanya pabrik (Dagger) yang membuat `mna`, jadi namanya bukan bukti keluarga kelas. String aksesibilitas "Seeking backward at 2x speed" tidak ditemukan dipakai di jalur ini (pemakainya di tempat lain belum dicari); jalur ini memakai teks lencana `reel_speedmaster_indicator_text` = "2x".

## Gestur tahan di Shorts

Ada tiga lapis pendengar tahan. Dua lapis pertama memang milik Shorts:

| Lapis | Kelas | Perilaku |
|---|---|---|
| Penangan halaman | `mmz` (di dalam `mna`) → `mnb.G(zona)` → `mpo.G` | Gestur tahan utama (di bawah) |
| Pager | `ativ` (di dalam `atiw`) | Menandai tahan (`a = true`) sehingga `onInterceptTouchEvent` mengembalikan true dan **pager tidak menggulir**. Bila halaman mengizinkan (`D(event)`), memanggil `atoo.G(2)`. Untuk `mpo`, `D()` selalu `false`, jadi lapis ini tidak memicu apa pun pada halaman video biasa; implementer lain `mmq` dan `mpe` belum diperiksa. |
| ViewPager luar | `moo` (di `ReelWatchPagerViewPager`) | Hanya menandai flag `i = true`. Tujuannya tidak dibaca. |

**Zona.** `mna.d(event)` mengembalikan **1** bila flag `atot.N()` aktif dan sentuhan berada dalam `fraksi × lebar layar` dari tepi kiri atau kanan, selain itu **2**. Fraksi bawaan **0,2** (dari konfigurasi remote `45751590`), jadi tepi kiri 20% dan tepi kanan 20% adalah zona 1; 60% tengah adalah zona 2. Kedua tepi menghasilkan nilai yang sama, jadi arah (kiri atau kanan) tidak dibedakan oleh `d()`. Lebar diambil dari ukuran layar saat konstruktor dan tiap sentuhan.

**Zona 1 (tepi) → 2x** (`mpo.G(1)`), bila halaman mengizinkan:
1. Umpan balik getar (`performHapticFeedback(0)`) dan log UI.
2. Kecepatan asli disimpan (`mpr.f`), kecepatan diset **2,0** (`aske.T(2.0f)`), dan penanda `mpr.a = true` (2x aktif).
3. Bila flag `atot.ao()`: lencana "2x" (`reel_speedmaster_indicator_chip`) ditampilkan; teksnya `reel_speedmaster_indicator_text` ("2x"), ikon maju-cepat 18 dp.
4. Bila flag `atot.ap()` (mode baru dengan kunci): kolom kanan (`reel_right_dyn_bar`), footer (`reel_player_footer_container`), lapisan interaksi (`reel_video_interactions`), dan satu `LinearLayout` lain **memudar ke alpha 0 dalam 125 ms**, lalu disembunyikan; petunjuk `reel_speedmaster_edu` ditampilkan. Mode baru ini juga tampaknya memungkinkan 2x dimulai **saat video sedang dijeda**: bila `aske.ao()` false, `l()` dan `aske.J()` dipanggil lebih dulu (arti `ao()` dan `J()`, kemungkinan "sedang diputar" dan "lanjutkan", disimpulkan dari pemakaian).
5. `requestDisallowInterceptTouchEvent(true)`.

**Lepas jari (tanpa kunci)** → `mpo.v()`: kecepatan asli dikembalikan (`mpr.a()`), lencana disembunyikan, UI dimunculkan lagi, intercept dilepas.

**Kunci 2x** (hanya bila `atot.ap()` aktif dan tahan dimulai di zona 1, karena `mna` baru menandai `l = 2` pada kondisi itu):
- Saat menahan, jari yang bergerak **lebih dari 50 px (piksel mentah) ke bawah** dari titik awal menggeser keadaan `l` dari 2 ke 3 dan memanggil `mpo.o()` → petunjuk berubah menjadi "**Release to lock 2x speed**" dengan ikon gembok. Jari naik lagi melewati ambang itu mengembalikan keadaan ke 2 dan memanggil `mnb.t()` (tidak dibaca; diduga mengembalikan petunjuk ke "Pull down to lock 2x speed" dengan panah bawah).
- Lepas jari saat `l == 3` → `mpo.u()`: bila belum terkunci (`mpr.b` false) dan 2x aktif, `mpr.b = true` (terkunci) dan kecepatan **tidak** dikembalikan. `T()` kemudian **menyembunyikan lencana "2x"** (`T()` hanya mengubah visibilitas view lencana menjadi `GONE`), petunjuk disembunyikan (`p()`), dan UI dimunculkan lagi. Jadi setelah terkunci, tidak ada penanda "2x" di layar menurut kode ini; video hanya tetap berjalan 2x.
- Petunjuk saat turun-tarik menjelang batas: `mpo.t()` (jari naik lagi melewati ambang) memanggil `af(false)`, yaitu kembali ke teks "Pull down to lock/exit 2x speed" dengan panah bawah.
- **Keluar dari kunci (terbaca penuh):**
  1. Sentuh dan tahan lagi di **zona tepi**. Kecepatan asli yang tersimpan (`mpr.f`) tidak ditimpa karena sudah terisi. Lencana tidak ditampilkan (`T()`, karena terkunci). Petunjuk "**Pull down to exit 2x speed**" dengan panah bawah muncul.
  2. Tarik lebih dari 50 px ke bawah: petunjuk menjadi "**Release to exit 2x speed**" (ikon disembunyikan).
  3. Lepas: `mpo.u()` melihat `mpr.b` true dan memanggil `ad()` → `mpr.a()`: kecepatan asli dikembalikan, `a = false`, `b = false`, penanda aktif dimatikan, lalu lencana dan petunjuk disembunyikan.
  - Menahan lagi lalu **lepas tanpa menarik** (`mpo.v()`) tidak keluar dari kunci: `v()` hanya memanggil `mpr.a()` bila `mpr.b` false.
  - **Ketuk biasa** saat terkunci tidak keluar dari kunci: `mna.e` memanggil `mnb.y()` (kosong di `mpo`) lalu hanya menjeda atau melanjutkan video; kecepatan tidak disentuh.

**Zona 2 (tengah) → `mpo.G(2)` lalu `atez.ci(2)` (KOREKSI 2026-10-31):** analisis awal menyimpulkan bahwa tahan di tengah hanya menampilkan pesan "unduhan offline". Itu **salah** (dikoreksi atas pengamatan pengguna di aplikasi asli). `mpo.G(2)` memang mencatat UI event 134942, getar, dan memanggil `ae()` (pesan "This action isn't available for downloaded shorts") **hanya** bila item bertanda offline (`E.c & 64` dan `aqzt.e(E.q)`). Tetapi di akhir `G(int)` kedua zona memanggil `this.i.ci(zona)`, dan `i` bertipe `atnt`, yang diimplementasikan oleh `atez` (fragmen Shorts). `atez.ci`: bila renderer item (`blgz`, dari `bz()`) punya bit tertentu, zona 1 hanya mencatat client event (`bi().J(1025, ...)`), sedangkan **zona 2 menjalankan perintah dari server**: `aksq.a(blgz.x)` (`bdso` = Command InnerTube, dieksekusi oleh `aksq`). Jadi tahan di tengah = **perintah tahan yang dikirim server per Short**. Isi perintahnya tidak ada di APK (nama field protobuf di-obfuscate dan klien web tidak mengirimnya); dari pengamatan pengguna isinya membuka panel deskripsi. Panel itu terbukti ada di respons: `engagementPanels` berisi `engagement-panel-structured-description`, dan `reelPlayerHeaderRenderer.reelTitleOnClickCommand` adalah `showEngagementPanelEndpoint` (surface `ENGAGEMENT_PANEL_SURFACE_SHORTS`, tag `engagement-panel-structured-description`), jadi **mengetuk judul juga membuka deskripsi**. Kesimpulan: tahan di zona tepi 20% = 2x; tahan di zona tengah = deskripsi (isi perintah server disimpulkan dari pengamatan, bukan dari APK).

**Flag yang mengatur gestur tahan.** Semua dibaca lewat `aktm.m(grup, id, bawaan)` dengan bawaan `false` di kode; nilai efektif datang dari konfigurasi server, atau dari penyimpanan konfigurasi lokal bila ada. `aktm.m` memilih penyimpanan terindeks (`awae`) bila mode itu aktif, kalau tidak peta konfigurasi server per ID, dan jatuh ke nilai bawaan bila ID tidak ada.

| Pembaca | ID flag | Bawaan | Mengatur |
|---|---|---|---|
| `atot.N()` | 45751092 | false | Zona tepi: tanpa ini `mna.d` selalu mengembalikan zona tengah, sehingga 2x tidak pernah mulai dari jalur `G(1)` dasar |
| (fraksi zona) | 45751590 | 0,2 | Lebar zona tepi per sisi (20% lebar layar) |
| `atot.ao()` | 45761231 | false | Lencana "2x" di tengah-atas |
| `atot.ap()` | 45769270 | false | Mode baru: kunci dengan tarik-bawah, UI memudar, petunjuk, 2x dari video yang dijeda |
| `atot.aq()` | 45795831 | false | Varian cara memudarkan UI (`B.e` atau `ah`) |
| `atot.w()` | 45797801 | false | Pencegatan cubit dan multi-jari |
| `atot.y()` | 45766165 | false | Hati di titik ketuk |
| `atot.bQ()` | 45625787 | dibaca lewat `aktm.n(grup, id)` (tanpa parameter bawaan; nilai bawaannya tidak sepenuhnya terbaca) | Pesan tahan-tengah dan callback ketuk dua kali |

Konsekuensinya: **APK sendiri tidak membuktikan apakah tahan di tengah menjalankan 2x di perangkat nyata.** Yang terbukti: bila `N()` false, tidak ada zona tepi dan jalur 2x dasar tidak berjalan; bila `ap()` true, mode baru berjalan. Tidak ada salinan nilai konfigurasi di dalam APK (`assets/phenotype/com_youtube_mainapp_android_package_metadata.binarypb` hanya memuat nama paket `com.youtube.mainapp.android`, dan `embedded_filegroups_embedded_datapush_proto.dat` adalah indeks aset). Nilai efektif hanya bisa diamati di perangkat.

**Halaman lain dengan tahan sendiri.** Dua implementer lain dari antarmuka halaman, `mmq` (turunan `atok`) dan `mpe`, mengembalikan `D() = true` bila sentuhan di luar area tertentu, sehingga tahan tingkat pager (`ativ`) memanggil `G(2)` pada mereka: `mmq` getar lalu menjalankan aksi yang dikirim server (`aksq.a`) bila sentuhan di luar area isinya, dan `mpe` membaca entri perintah dari daftar renderer. Keduanya bukan halaman video biasa dan tidak mengubah kecepatan.

**Sentuhan sebelum tahan.** `mna.c` pada `ACTION_DOWN` menjadwalkan callback 200 ms (`mdu` kasus 17) yang memanggil `mna.a(event)` bila belum dijeda oleh ketuk; fungsinya melaporkan posisi jari dan memeriksa ambang tarik-bawah. Cubit (`atnq`) yang aktif membatalkan keadaan tahan (`mnb.v()` dipanggil, `f(true)` mereset).

## Susunan layar Shorts (bawah ke atas)

Dari `reel_watch_fragment` dan `reel_player_overlay`:

1. `ReelPlayerView` layar penuh dan `reel_player_page_frame0` (bingkai awal) serta `reel_player_page_current_frame_snapshot` (bingkai terakhir saat transisi). Pemutar dipisah dari halaman RecyclerView, mirip satu `PlayerView` bersama di FlufPlay.
2. `SwipeRefreshLayout` → `ReelRecyclerView` (pager).
3. Scrim atas **96 dp** (140 dp saat refresh), scrim bawah **160 dp**.
4. `reel_video_interactions`: lapisan sasaran sentuh.
5. Bar progress: tinggi **2 dp**, margin horizontal **8 dp**.
6. `reel_player_footer_container`: tinggi cadangan **184 dp** untuk baris channel dan judul (isinya dikirim server).
7. Header atas: tombol kembali, judul kontekstual, wadah opsi (lihat bawah).
8. Lencana kecepatan (tengah-atas) dan petunjuk (tengah-bawah).
9. Lapisan umpan balik ketuk, paling atas.
10. Bila memuat: `reel_watch_splash_screen_*` (judul di tengah di atas latar tema), `reel_generic_ghost_loader` (kerangka Lottie kiri-bawah, margin 16 dp kiri-kanan dan 24 dp bawah), `reel_playback_loading_spinner` di tengah. Bila gagal: "Tap to retry". Bila habis: "All caught up."

Footer aksi menurut urutan string: **Like, Dislike, Comment, Share, Remix** (`reel_footer_*`). FlufPlay sudah mengikuti empat yang pertama.

## App bar

### Bilah bawah (PivotBar)

Nilai resource (bukan pengukuran layar):

| Item | Nilai |
|---|---|
| Tinggi | 48 dp (`pivot_bar_height`); varian 56 dp (`pivot_bar_cairo_height`) |
| Ikon | 24 dp; topeng avatar 28 dp |
| Lebar tab minimum | 48 dp; **96 dp** pada layar `sw600dp` |
| Padding atas tab tanpa label | 16 dp |
| Latar gelap | `navigation_bar_background_dark` = `yt_black3` (FlufPlay `Background` = `#0F0F0F`) |
| Garis pemisah gelap | `navigation_bar_divider_dark` → `0x0106000d` (`android:color/transparent`); ukuran tanpa garis 0 dp |

Gestur (kode `PivotBar` dan `avbv`):

- `dispatchTouchEvent` menelan semua sentuhan (`return true`) dan memberikannya ke pendeteksi gestur, kecuali pembaca layar aktif.
- **Ketuk:** `onSingleTapUp` mencari tab dari **koordinat mentah** lewat hit-test, lalu `performClick()`. Ketukan di sela ikon atau sedikit meleset tetap mengenai tab.
- **Tahan:** hanya bila fitur aktif (`i`); jika tidak, melempar `UnsupportedOperationException("Long presses not supported")`. Bila ada jeda terkonfigurasi (`m` ms), tahan dijadwalkan; angkat jari sebelum jeda habis tampaknya dihitung sebagai ketuk (peran `sX()`/`sY()` sebagai batal/selesai disimpulkan dari pemakaian). Hasilnya `performLongClick()`, atau `performClick()` bila tab tidak punya aksi tahan.
- **Geser/lempar:** `onScroll` dan `onFling`, bila terdeteksi sebagai geser, **meneruskan event ke konten di atas bar** dengan Y digeser setinggi bar (`PivotBar.g`). Jadi menggulir yang dimulai di bar tetap menggulir halaman.
- Tab dikirim server (renderer `avbz`); jumlah dan urutan tab tidak ada di APK.

### Header atas Shorts

- `reel_player_static_header`: tombol kembali (ikon panah 24 dp dengan latar lingkaran, padding 12 dp, margin 4 dp), judul kontekstual berukuran 22 (dimen `reel_contextual_header_font_size`), dan `reel_header_options_container` (ikon opsi dari server).
- `reel_player_back_button`: tombol kembali kiri-atas dengan **ikon panah**, latar lingkaran, padding 16 dp.
- Judul kontekstual dipakai untuk konteks (mis. halaman hashtag atau suara). Apakah **feed utama** memakainya, tidak bisa dipastikan dari APK.

## FlufPlay sekarang vs YouTube

| Aspek | FlufPlay (`ShortsScreen.kt`, `MainActivity.kt`) | YouTube |
|---|---|---|
| Geser vertikal | `VerticalPager`, mengikuti jari, snap | RecyclerView + mesin status sentuh |
| Ketuk | Beralih putar/jeda langsung; ikon jeda statis 54 dp saat jeda | Lottie 96 dp; scrim jeda |
| Ketuk dua kali | **Tidak ada** | Suka + hati 240 dp di titik ketuk |
| Tahan | **Tidak ada** | 2x, lencana 32 dp, pager berhenti menggulir |
| Scrub | **Tidak ada**; bar progress 2 dp penuh lebar tanpa sentuhan | Pratinjau + waktu 24 sp; overlay disembunyikan |
| Tarik-ke-segarkan | **Tidak ada** | Ada (layout) |
| Geser kiri | **Tidak ada** | Ada (callback `bT`) |
| Scrim | atas 130 dp, bawah 260 dp | atas 96 dp, bawah 160 dp |
| Bar progress | 2 dp, tanpa margin horizontal | 2 dp, margin 8 dp |
| Tombol keluar | Ikon **X** | Ikon **panah kembali** dengan latar lingkaran |
| Header | Judul "Shorts" 22 sp tebal + menu Opsi | Belum pasti untuk feed utama |
| Bilah bawah | 56 dp, **garis pemisah `#303030`**, 4 tab, seluruh sel bisa diketuk | 48/56 dp, garis pemisah transparan di tema gelap, hit-test dari koordinat |
| Status memuat | Poster + kerangka judul + spinner | Judul splash + kerangka Lottie + spinner |

## Seharusnya seperti apa

Urutan di bawah mengikuti dampaknya pada rasa "responsif dan halus"; semua bisa dibuat dengan Compose tanpa dependensi baru (animasi memakai `Animatable`, bukan Lottie).

**Prioritas 1 — gestur yang paling terasa**

1. **Ketuk dua kali = suka.** Hati putih berukuran 240 dp yang muncul di titik ketuk, membesar lalu memudar (~600–800 ms; durasi tepat tidak ada di APK, ini pilihan desain). Bila belum login, buka dialog login seperti tombol suka. YouTube sendiri **menunda ketuk tunggal** sampai jendela ketuk kedua lewat bila ketuk dua kali mungkin (`mna.c`); jadi `detectTapGestures(onTap, onDoubleTap)` Compose, yang menunda `onTap` sekitar 300 ms, sudah menyerupai perilakunya. Tidak perlu detektor kustom.
2. **Umpan balik ketuk.** Ganti ikon statis 54 dp dengan ikon putar/jeda 96 dp yang muncul lalu memudar di tengah. Saat jeda, tambahkan scrim gradasi tipis di seluruh halaman.
3. **Tahan = 2x.** `player.setPlaybackSpeed(2f)` selama ditahan, kembali ke kecepatan sebelumnya saat dilepas (simpan nilai awal, seperti `mpr.f`). Berikan getar pendek saat mulai. Lencana "2x" tinggi 32 dp di tengah-atas. **Matikan scroll pager selama menahan** (`userScrollEnabled = false`), dan **pudarkan kolom kanan, footer, dan judul ke alpha 0 dalam 125 ms** lalu munculkan lagi saat dilepas, seperti `mpo.ah`. Boleh dimulai dari video yang sedang dijeda.
   - *Zona:* YouTube hanya memicu dari 20% tepi kiri dan kanan layar, bila flag zona aktif. Untuk FlufPlay lebih aman **menahan di mana saja** memicu 2x, karena pengguna biasanya mengharapkan itu; pakai aturan tepi hanya bila ingin meniru kode APK apa adanya.
   - *Kunci:* tarik ke bawah lebih dari 50 px (setara sekitar 17–20 dp pada layar 2,6–3x; angka YouTube adalah piksel mentah) mengganti petunjuk menjadi "Release to lock 2x speed" dengan ikon gembok; lepas saat itu mengunci 2x. Setelah terkunci YouTube menyembunyikan lencana "2x", jadi tidak ada penanda; untuk FlufPlay sebaiknya lencana tetap tampil agar pengguna tahu video sedang 2x. Untuk keluar: tahan lagi (muncul "Pull down to exit 2x speed"), tarik bawah, lepas. Ketuk biasa tidak keluar dari kunci dan menahan lalu melepas tanpa menarik juga tidak. Pakai petunjuk teks yang sama dalam Bahasa Indonesia.
   - *Waktu ketuk:* ketuk valid bila selesai dalam 199 ms (`max(199, getTapTimeout())`) dan jari bergeser tidak lebih dari touch slop. Ketuk tunggal ditunda selama `getDoubleTapTimeout()` (bawaan Android 300 ms) bila ketuk dua kali mungkin. Compose `detectTapGestures` memakai batas serupa, jadi tidak perlu angka khusus.
4. **Scrub bar progress.** Beri area sentuh sekitar 24–32 dp di sekitar bar 2 dp; saat disentuh, bar menebal, stempel waktu tampil, dan judul/tombol dipudarkan (mode bersih). Pratinjau thumbnail opsional (NewPipe menyediakan data frame pratinjau; belum diperiksa untuk Shorts).

**Prioritas 2 — ukuran dan susunan**

5. Scrim atas 96 dp dan bawah 160 dp, supaya video lebih terlihat.
6. Bar progress dengan margin horizontal 8 dp.
7. Ganti tombol X dengan panah kembali berlatar lingkaran semi-transparan, 16 dp padding, di kiri-atas.
8. Pertahankan kolom aksi Like, Dislike, Comment, Share. Remix tidak mungkin dibuat.

**App bar bawah**

9. Hapus `HorizontalDivider(#303030)` di tema gelap (menurut resource, YouTube memakai garis transparan) dan pertahankan latar `#0F0F0F` yang sudah sama dengan `yt_black3`.
10. Pertahankan tinggi 56 dp (setara varian Cairo 56 dp) dan seluruh sel sebagai area ketuk. Di layar lebar (`sw600dp`), lebar tab minimum 96 dp.
11. Bilah tetap tampil pada tab Shorts dan tersembunyi saat Shorts dibuka dari pencarian atau channel (itu sudah benar). Perilaku "ketuk tab yang sedang aktif" di YouTube tidak terbukti dari APK, jadi tidak ditiru.

**Prioritas 3 — nanti**

12. Tarik-ke-segarkan di Short pertama (`PullToRefreshBox` tampaknya tersedia di Material3 dari BOM 2024.12.01, belum dicek; bersarang dengan `VerticalPager` berisiko, uji di perangkat).
13. Geser kiri untuk membuka channel (sekarang lewat avatar).
14. Kunci 2x lewat tarik-bawah dan cubit-zoom: nilai kecil, bisa dilewati.

**Jangan ditiru:** mode yang menelan geser cepat saat transisi (`ReelTouchCaptureView`), karena justru memperburuk keluhan "tidak responsif".

## Belum terbukti

- Isi header atas feed Shorts (judul atau hanya ikon, ikon apa) dan apakah bilah bawah selalu tampil di atas Shorts. Cara menutupnya: buka YouTube 21.43.523 di RMX3630 (versi sama dengan APK), masuk ke Shorts, lalu ambil tangkapan layar dan `uiautomator dump`. Perangkat itu terkunci terakhir kali dicek; butuh dibuka oleh pemilik.
- Durasi dan kurva semua animasi (hati, ikon putar/jeda, lencana), karena ada di file Lottie dan kode animator yang tidak dibaca.
- Nilai efektif flag remote: ketuk dua kali di titik ketuk (`atot.y()`), geser kiri (bit `16`), geser cepat ditelan (`45613566`), pencegatan multi-jari (`atot.w()`).
- Nilai efektif flag gestur tahan (`N`, `ao`, `ap`, `aq`, `w`, `y`, `bQ`; ID di bagian "Flag yang mengatur gestur tahan"). Bawaan di kode semuanya `false`; APK tidak membawa salinan nilai server. Cara menutupnya: amati di perangkat, yaitu tahan di tengah dan di tepi pada Shorts yang berjalan lalu bandingkan laju video. Ini mengubah riwayat tontonan akun di aplikasi YouTube yang terpasang, jadi perlu izin pemilik perangkat (atau mode penyamaran/tamu).
- Perilaku kunci 2x setelah pindah ke Short berikutnya (apakah `mpr` direset), karena siklus hidup `mpr` per halaman tidak dibaca.
- Tujuan aksi geser kiri (`bT()`) dan pemicu mode bersih.

## Daftar bukti

Pemeriksaan REA 2026-10-31 (sesi sudah ditutup; hasil bisa diulang dengan pemeriksaan yang sama). Pemetaan nama ke peran memakai `dexdump` (string dan ID resource di dalam metode).

| Temuan | Kelas / sumber | Bukti |
|---|---|---|
| Inventaris kelas Reel (termasuk `ReelTouchCaptureView`, `ReelScrubbedPreviewView`) | `search_android_classes "Reel"` | `ev_d2572fe43cdf0f7d40e7b2dbeb29ed034cc1184fb4adc75a0ea4505d304854cb` |
| Geser cepat ditelan | `ReelTouchCaptureView.onTouchEvent` | `ev_bc18a7ee63d1bc32fa9df34604fabedd73046e00345b3c22e30b62e79ff3f077` |
| Mesin status sentuh pager (intercept) | `atiw.l` | `ev_709fc1906146a674cbcfdabdfc9425b77abfa4eb30ee9f9551abc48f9995cb10` |
| Mesin status sentuh pager (touch) | `atiw.f` | `ev_5dae2d7175341a703a5f0306060ab58bc189270faf934a9a51b6fa10c0496653` |
| Klasifikasi vertikal/horizontal | `atiw.h` | `ev_a39fb2c8cedbd3756f863ad979f29d62a2136904903a2ca9fc177b4416c7d5c4` |
| Tahan di pager | `ativ.onLongPress` | `ev_13234bf6959385432cd4b996583815d9b3859af2f312e717d735bf6ec7ff0e0b` |
| Tahan di ViewPager | `moo.onLongPress` | `ev_9a8257e89b5c153fda96bf77ce0e9da284869ca35915c7e2f8a180374c4f6c0b` |
| Tahan di halaman | `mmz.onLongPress` | `ev_f0da21fbff028202d1c96f13d0c36ac9c9dca576c2da961101a5645aa6f8d4a1` |
| Penangan sentuh halaman (ketuk, dua kali, tahan) | `mna.c` | `ev_f4ca344e337aab7fbcd290eda845cdd180e423874d7aa57185989be6c7180d42` |
| Zona tepi 20% | `mna.d` | `ev_52761b0ea29fa4897dd2ef0084219426a419f7f90d8b7f55215ca07b13857cc6` |
| Tarik-bawah 50 px | `mna.a` | `ev_3ee9aff7db553d5aec683af36bb8bc294058594fe569a0f62804a0fb8a05a899` |
| Aksi ketuk (jeda/putar) | `mna.e` | `ev_e3127ed14cfc79f2f11f3ad573cd609d3d630673baa80be94fd0dea0f10adf1c` |
| Reset keadaan tahan | `mna.f` | `ev_3785c840a4408d1991fda30703295bfc5c46e5f3e250b1a1877000c8b6e8b18b` |
| Konstruktor (touch slop, lebar layar) | `mna.<init>` | `ev_ac879ec9551dd256dd0e856fab3f0f69d644d31d32ee099f9bf58bab0764930c` |
| Mulai 2x / tahan tengah | `mpo.G` | `ev_17fb43f336a9c96908be0d03fda9395db07c8938de7714cb20444ccf7730550e` |
| Petunjuk "lock/exit" | `mpo.af` | `ev_c4a6058d3c2bf79041fb4aa57257a9ecf1f7068b5a38ddf46909ebc66acf09c0` |
| Pudarkan UI saat menahan | `mpo.ah` | `ev_09d88cface8a2dafdfa004103bf930870f0dfbfc355c49f3411d09e9c0d86ee8` |
| Petunjuk muncul saat ditarik ke bawah | `mpo.o` | `ev_fce02513d597c3fc08706713d5cb229183f9fb02dffab000b23c69b28cc0be48` |
| Kunci 2x saat dilepas | `mpo.u` | `ev_5eb5697423cb43c7ed306d031d1440a571516a5906fad24d3d5b827f60ab68c0` |
| Keluar kunci (kembalikan kecepatan) | `mpo.ad`, `mpr.a` | `ev_16fb16fb2a0282077d2011bd6bfbd19a00f29572d1e8b9a5975a5f69d6353371`, `ev_a34b6dad43cdf34a498103fa9f6c7f0bf962ca2e881fc893178f865fd340c1fd` |
| Lepas biasa | `mpo.v` | `ev_8bb5ce63fdd5e59199074a66304efc9d0a4bf5dd8d0a48462975ef53ac3305ea` |
| Callback 200 ms setelah sentuh | `mdu.run` (kasus 17) | `ev_4616a81a4e743ba13f011e3fa672cd8962d94c754023e9fa5b9398725a7d37ae` |
| `mpo.D` selalu false | `mpo.D` | `ev_e216820c310c9822baeb9b9e34b455b6369972b550c090814718969cee074ff1` |
| Tahan di tengah (pesan offline) | `mpo.ae` | `ev_23d3437643ef518a3d9201959ec99b72ef28fcf5cf74ad13bf126aabc089449f` |
| Daftar pembaca flag (185 metode) | `atot` | `ev_0b01666d846ffcf93de9f29ec09ca31b4242a0b062c0ccfe7c18db5725315df5` |
| `q`, `r`, `p` ditulis di konstruktor | `mna.<init>` | `dexdump -d` (offset `0x2a`, `0x36`, `0x3c`); tanpa ID bukti REA, karena JADX tidak menampilkannya |
| ID flag dan bawaan `false` | `atot.N`, `ao`, `ap`, `aq`, `w`, `y`, `z`, `bQ`, `ah` | `dexdump -d` (grup dan ID `const-wide/32`); tanpa ID bukti REA |
| Pembaca flag | `aktm.m`, `aktm.n`, `aktm.a` | `dexdump -d`; tanpa ID bukti REA |
| `T()` menyembunyikan lencana, `p()` menyembunyikan petunjuk, `y()` kosong | `mpo.T`, `mpo.p`, `mpo.y`, `mpo.t` | `dexdump -d`; tanpa ID bukti REA |
| Halaman dengan tahan sendiri | `mmq.G/D`, `mpe.G/D` | `dexdump -d`; tanpa ID bukti REA |
| Cubit | `atnq.onScale` | `ev_ac765752167a6073f371fe07f3505176090af5125098ba08bc9227e8c4c178e1` |
| Hati di titik ketuk | `atnp.m`, `atnp.f` | `ev_bf91ca155bd975e293e097641a38f24d50d11d7b1c8702b7d0293ae7d9dff9e5`, `ev_b7f287d574a82a8aa19d3763fb3ff8615f9e2071bb4b2dd7db8a574ff23bfcec` |
| Bar bawah: kelas | `PivotBar` | `ev_adc82bbe10a768d78f7352e02d661f181367424a26d4e62c48f9123e9fa528f7` |
| Bar bawah: telan sentuhan | `PivotBar.dispatchTouchEvent` | `ev_4876d85113314e2cb2a5534f4b0f1cfb905adf6cc02081b30b54c613f32e0771` |
| Bar bawah: teruskan ke konten | `PivotBar.g` | `ev_6f3f5db045543136a4a912c133b5f25faeac77ba4748597749fa6ac6982e9af5` |
| Bar bawah: hit-test | `PivotBar.h`, `avbv.onSingleTapUp` | `ev_a503255a79ddebb717ea608a89d517bdf6b0931ec13a0ce9eca7cb9e3c86445a`, `ev_4c0e876d52e27723843c91b59f52385eb3bf865d66c6288bd701f725fad40ceb` |
| Bar bawah: tahan, geser, lempar | `avbv.onLongPress`, `onScroll`, `onFling` | `ev_b7539e7cf3f2bdc886938afa973a758b1c8e11cf13504fd4065b25f5ced19274`, `ev_81c3bc8e56baafed5c6b2b89d957f8e2dd1569e0326c6cde2b24c227534122e8`, `ev_0d4f57987ea23d6bb7cbf9846af757774ff38dac7bc58f36b806649c75b7210c` |

Peta nama ter-obfuscate (disimpulkan; `dexdump` menunjukkan string nama sumber di dalam metode kelas itu): `atez` = `ReelWatchFragmentImpl`, `atit` = `ReelPageController` (membuat `ReelLinearLayoutManager` bersama `atiw`), `atia` = `ReelPageAdapter`, `mwf` = `ReelTimeBarController`, `lyc` = `ReelWatchPaneFragment`. `ShortsBottomBarController` adalah milik **kamera pembuatan Shorts**, bukan layar tonton.

Layout dan resource: `reel_watch_fragment`, `reel_watch_fragment_linear`, `reel_player_page`, `reel_player_overlay`, `reel_player_overlay_scrims`, `reel_player_overlay_tap_feedback`, `reel_player_paused_state`, `reel_speedmaster_indicator_chip`, `reel_speedmaster_edu`, `reel_scrubbed_preview_view`, `reel_scrubbed_preview_extended_timestamp`, `reel_player_static_header`, `reel_player_back_button`, `reel_player_edu_vertical`, `reel_watch_splash_title_screen`, `reel_generic_ghost_loader`. Nilai dimen dan string dibaca dari `aapt2 dump resources`.
