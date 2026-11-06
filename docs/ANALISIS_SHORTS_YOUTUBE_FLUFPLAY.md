# Analisis Shorts YouTube 21.43.523 dan FlufPlay

Diperbarui 2026-10-31 setelah pemeriksaan ulang atas kode FlufPlay dan APK. Semua temuan APK di dokumen ini bersifat **statis**; verifikasi runtime belum dilakukan (lihat "Status verifikasi").

## Kesimpulan

1. Sebelum 2026-10-31, tab Shorts FlufPlay dibangun dari **hasil pencarian biasa** yang diurutkan dengan aturan lokal, sehingga isi dan urutannya berbeda dari feed Shorts YouTube, yang memakai endpoint urutan Reel (`reel/reel_watch_sequence`). Endpoint itu terbukti dapat dipanggil sebagai tamu dalam bentuk JSON (lihat "Pengamatan endpoint"). FlufPlay kini memakainya sebagai sumber utama dan menjadikan pencarian lama cadangan (belum diuji di perangkat).
2. Di APK, perpindahan antar-Short ditangani `RecyclerView` khusus dengan arbitrase sentuhan (arah gerakan, child yang bisa digeser horizontal, multipointer) dan smooth scroll kustom. Video ditampilkan dengan pengukuran rasio adaptif (`ASPECT_FIT` atau `ASPECT_FILL`), dan progress digambar sebagai bar bersegmen yang disinkronkan dari event player.
3. Layout `reel_player_right_buttons_offline` dan `reel_player_right_footer_offline` adalah overlay **mode unduhan offline (Smart Downloads)**. Ukuran di dalamnya (64 × 76 dp, 12 sp, dan lainnya) **bukan** bukti geometri overlay Shorts online.
4. Analisis ini belum membuktikan algoritma rekomendasi server, perilaku endpoint untuk akun yang login, atau tampilan aktual di perangkat. Bentuk permintaan dan respons endpoint sudah diamati sebagai tamu.

## Identitas dan batas analisis

- APK: `youtube-21-43-523.apk` (di `referensi/`)
- Package: `com.google.android.youtube`
- Versi: `21.43.523`, versionCode `1561307650` (dicek dengan `aapt2 dump badging`)
- SHA-256: `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`
- Alat: REA dengan JADX headless MCP 0.7.1 (53.653 kelas), `aapt2` (build-tools 37.0.0) untuk resource dan layout, `dexdump` untuk memindai bytecode, `strings` untuk string DEX, dan `curl` untuk pengamatan endpoint sebagai tamu (tanpa cookie atau akun).
- Kode FlufPlay yang diperiksa berada di direktori `flufplay/app/src/main/java/id/flufclip/player/`.
- Nama kelas di luar paket `com.google.android...` (misalnya `mpo`, `mpt`, `mps`, `ardm`, `blhc`, `blgz`, `atot`) telah di-obfuscate. Peran kelas-kelas itu **disimpulkan dari cara pemakaiannya**, bukan dari nama.
- ID bukti lama (`ev_...`) pada versi dokumen sebelumnya dihapus karena tidak dapat dicocokkan ke sesi REA yang aktif. Bukti yang dipakai kini berasal dari pemeriksaan ulang (lihat "Daftar bukti").
- Temuan statis tidak membuktikan tampilan dan perilaku pada semua perangkat, akun, atau eksperimen. Analisis ini belum mencakup screenshot runtime YouTube atau tangkapan lalu lintas jaringan.

## Pengambilan dan urutan video di FlufPlay

### Alur sekarang: urutan Shorts YouTube (sejak 2026-10-31)

Saat tab Shorts dibuka, `MainActivity.kt` (`LaunchedEffect(mainTab, shortsRequest)`, `MainActivity.kt:692`) lebih dulu memanggil [`ShortsFeed.kt`](../flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt):

1. `ShortsFeed.start()` mengambil `https://www.youtube.com/shorts`, membaca versi klien, `sequenceContinuation`, dan Short pembuka, lalu memanggil `reel/reel_watch_sequence` dengan `sequenceParams` (JSON, klien WEB). Hasilnya daftar `StreamInfoItem` berisi URL `/shorts/<id>` dan thumbnail.
2. Urutan dari server dipertahankan; `HomeRecommendations.rank` tidak dipakai untuk sumber ini. Video yang disembunyikan (`hiddenVideos`) disaring.
3. Saat antrean tinggal 4 item atau kurang (`LaunchedEffect(shortsNeedMore, …)`, `MainActivity.kt:767`), `ShortsFeed.more(token)` mengambil halaman berikutnya memakai token continuation, sampai 3 halaman per putaran.
4. Judul, channel, dan stream tetap diambil lewat `StreamInfo.getInfo()`, tetapi sekarang oleh `ShortsResolver` (lihat "Perbedaan UI dan interaksi"), yang me-resolve Short berikutnya lebih dulu. Data prefetch di respons (URL stream dan `reelItemWatchResponse`) belum dipakai.
5. Saat login, permintaan membawa cookie dan SAPISIDHASH dari `YouTubeAuth`; bila gagal, dicoba ulang sebagai tamu. Perilaku dengan akun yang login belum diuji.
6. Bila feed gagal atau kosong, alur cadangan di bawah berjalan. Kegagalan sementara saat mengisi ulang tidak mematikan feed, karena token disimpan untuk dicoba lagi.

Status: kode dikompilasi (`assembleDebug` berhasil). Ekstraksi token, versi klien, dan Short pembuka diuji terhadap halaman nyata di luar aplikasi. **Belum diuji di perangkat.**

### Alur cadangan: pencarian biasa (alur sebelumnya)

Bila feed gagal atau kosong, alur lama berjalan (baris 612-622 untuk muat awal):

1. Mencari kata `shorts` melalui NewPipe Extractor.
2. Memeriksa maksimal tiga halaman awal, atau berhenti lebih cepat jika sudah menemukan sedikitnya 10 kandidat. Jumlah 10 dihitung **sebelum** penghapusan duplikasi (baris 616-618, sedangkan duplikasi dihapus di baris 622), sehingga hasil akhirnya bisa kurang dari 10.
3. Memilih `StreamInfoItem` yang ditandai sebagai video pendek atau URL-nya mengandung `/shorts/`.
4. Menghapus duplikasi berdasarkan ID video, mengurutkan kandidat dengan aturan lokal, lalu membuka item pertama.
5. Ketika antrean hampir habis (4 item atau kurang; baris 632-681), mengambil halaman pencarian berikutnya. Jika halaman habis, aplikasi membuat pencarian baru seperti `<topik> shorts`, dengan topik yang dipilih dari riwayat tontonan, riwayat pencarian, atau daftar topik bawaan. Jika tidak ada item baru, aplikasi menampilkan pesan "Tidak ada Shorts baru. Geser lagi untuk mencoba." (baris 672), dan kegagalan permintaan menampilkan pesan coba ulang (baris 679).

Implementasi: [`MainActivity.kt`](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt) dan [`VideoRepository.kt`](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt). Repository memakai `getSearchExtractor()` dan `getPage()` untuk hasil pencarian dan continuation pencarian.

Pengurutan di [`HomeRecommendations.kt`](../flufplay/app/src/main/java/id/flufclip/player/HomeRecommendations.kt) memakai kecocokan channel dan kata judul dengan riwayat lokal. Video yang sudah ditonton mendapat penalti dan channel berulang dikurangi nilainya. Ini adalah heuristik lokal, bukan algoritma rekomendasi Shorts YouTube.

Setelah item dipilih, FlufPlay menggunakan `StreamInfo.getInfo()` untuk mengambil informasi dan stream. Terdapat fallback yt-dlp pada kondisi tertentu saat pengguna login, kemudian ExoPlayer memutar stream. Saat login, downloader dapat menambahkan header autentikasi ke permintaan yang relevan, tetapi hal itu tidak otomatis mengubah pencarian menjadi feed Shorts akun YouTube.

## Temuan statis APK YouTube

### Pager dan arbitrase sentuhan

- `ReelWatchPagerViewPager` (paket `...extensions.reel.watch.fragment`) hanya memiliki konstruktor, `onInterceptTouchEvent`, dan `onTouchEvent`.
- `ReelRecyclerView` (paket `...libraries.youtube.reel.internal.pager`) meng-override `onInterceptTouchEvent` dengan logika berikut:
  - Pada `ACTION_DOWN`, posisi sentuh disimpan. Jika sebuah feature flag aktif, aplikasi mencari child di bawah jari. Flag itu (ID `45865107`) memiliki default `false`.
  - **Multipointer:** saat kondisi tertentu terpenuhi, `ACTION_POINTER_DOWN` membuat sentuhan **tidak dicegat** dan memicu `a(true)`. `ACTION_UP`, `ACTION_CANCEL`, dan `ACTION_POINTER_UP` dengan dua pointer mereset keadaan. Arti pasti kondisi tersebut (`atot.w()`) belum dibaca.
  - **Arah gerakan:** pada `ACTION_MOVE`, sudut gerakan terhadap sumbu vertikal dihitung. Sentuhan tidak dicegat bila child di bawah jari bisa digeser horizontal ke arah gerakan **dan** sudutnya lebih dari 45°. Ada pula ambang kedua, yang tidak dicegat bila sudut melebihi ambang dari konfigurasi remote (ID `45761824`, default 30°) dan kondisi `ah` serta `atot.bI()` terpenuhi.
  - Nilai efektif flag dan ambang itu berasal dari konfigurasi remote dan **tidak dapat diketahui dari APK**.
- `ReelLinearLayoutManager` hanya memiliki konstruktor, `ao()`, dan `ay(RecyclerView, int)`. `ay` membuat smooth scroller kustom (`athw`) dengan dua parameter float dan posisi target, lalu memulainya. `ao()` mengembalikan boolean yang dapat didelegasikan ke objek lain ketika flag `d` aktif; dari perannya kemungkinan izin scroll, tetapi nama aslinya tidak pasti.
- `ShortsPlayerView` ditemukan, tetapi berada dalam paket **editor** (`...libraries.youtube.creation.editor`); keberadaannya saja tidak membuktikan bahwa kelas tersebut merupakan player feed tontonan.

### Pengukuran rasio video

`ReelPlayerView.onMeasure` memilih salah satu cabang berdasarkan enum mode (`atoh`). Pada cabang adaptif, kelas ini menandai hasilnya sebagai `ASPECT_FIT` atau `ASPECT_FILL` (field `D`), dan menyimpan ukuran crop pada `C`. Penafsiran dekompilasi: video di-crop ringan (`ASPECT_FILL`) bila selisih rasio (`aqzt.L(...)`) tidak melebihi ambang `z`, dan memakai `ASPECT_FIT` bila lebih besar. Ambang `z` diset 0,14 bila field `A` melebihi 1,9 dan `aqzt.R(...)` terpenuhi; arti `A` (kemungkinan rasio aspek) belum dipastikan, dan nilai awal `z` diisi di konstruktor yang belum dibaca. JADX menandai blok ini sebagai kode duplikat, jadi ukuran akhir (`Size`) tidak dapat dibaca secara pasti dari dekompilasi.

### Urutan Reel, continuation, dan prefetch

String DEX memuat:

- `reel/reel_watch_sequence`, `GetReelWatchSequence`, `reelWatchSequenceParams`, `REEL_WATCH_SEQUENCE_REQUEST_SENT`, dan `REEL_WATCH_SEQUENCE_RESPONSE_RECEIVED`.
- `ReelSequenceController.PENDING_NEXT_CONTINUATION_KEY`, `PENDING_PREV_CONTINUATION_KEY`, `PENDING_REFRESH_CONTINUATION_KEY`, dan `PENDING_SOFT_REFRESH_CONTINUATION_KEY`.
- Prefetch: `SHORTS_PREFETCH_REEL_WATCH_SEQUENCE` (beserta `_RECEIVED` dan `_ERROR`), prefetch `GetReelItemWatch` pada `ReelPlaybackController`, dan pesan "Prefetch request for shorts shelves are disabled on power save mode".

Ini mendukung keberadaan konsep **urutan Reel, continuation, dan prefetch** di APK. String tersebut sendiri tidak membuktikan endpoint yang dipakai pada tab Shorts saat runtime, isi respons, jumlah item yang di-prefetch, atau rumus rekomendasi server.

Kelas pembangun permintaan (nama ter-obfuscate; peran disimpulkan dari pemakaian):

- `atna` membangun permintaan `reel/reel_watch_sequence`. Pesan protobuf `bgsx` berisi satu dari dua string, yaitu `continuation` (kemungkinan field 3) atau `sequenceParams` (kemungkinan field 4), ditambah `enableRotation`, satu enum mode (dari `atjp`, bernilai 1 atau 2), dan dua pesan konteks (`bgst`, `bkjl`). Konstruktornya dapat menambahkan opsi berisi flag `true` dan stempel waktu +120 detik (`bpwv`); arti opsi ini belum jelas. Metode `lm()` hanya membuat kunci identitas permintaan (`serviceName`, `identity`, `continuation`, `sequenceParams`, `enableRotation`), bukan body.
- `atmy` membangun permintaan `reel/reel_item_watch`. Pesan `bgso` berisi pesan pemutar (`bhaa`), enum jenis, dua string, dua boolean, dan `bgst`.
- Format kawat di aplikasi adalah protobuf. Nilai awal `sequenceParams` diteruskan antar-layar lewat Bundle dengan kunci `reelWatchSequenceParams` (`aodb.e`), sehingga tampaknya berasal dari endpoint navigasi yang dikirim server, bukan dihitung aplikasi.

### Progress bar

`ShortsPlayerProgressPresenter` adalah View kustom dan menjadi elemen dalam layout `reel_player_overlay` (layout tanpa akhiran `_offline`). Perilakunya:

- **Segmen.** `mpo.M` mengambil objek `blhc` dari respons item (`blgz` lalu `blhd`). `blhc` adalah pesan protobuf dengan field indeks (mulai 1) dan jumlah. Jumlah segmen sama dengan jumlah itu, dan segmen aktif sama dengan indeks dikurangi 1. Teks aksesibilitasnya "Playing video X of Y". Jadi segmen **bukan chapter**. Jika `blhc` tidak ada dan flag `mpo.I` aktif, bar menjadi satu segmen penuh. Respons server mana yang mengisi `blgz` belum ditelusuri.
- **Bentuk.** Maksimal 100 segmen tampil, dengan offset jendela bila jumlahnya lebih besar. Jarak antarsegmen 2 dp. Kode untuk segmen tepi (6 dp) ada, tetapi dekompilasi menunjukkan jumlahnya dibatasi `Math.min(0, ...)` sehingga kemungkinan tidak pernah berjalan. Ketebalan 1 dp atau 2 dp, tergantung flag `mpo.J` yang artinya belum diketahui. Tiga `Paint` dipakai untuk segmen yang sudah lewat, segmen aktif, dan segmen berikutnya. Segmen aktif diisi sebagian dengan `clipRect` sesuai fraksi progress.
- **Sumber waktu.** `mpo.f(ardm)` menyalin posisi dan durasi dari event player (`ardm.a` dan `ardm.d`) ke presenter, dan mengisi cap waktu bila event menandai sedang diputar (`ardm.h`). Di antara event, `b()` menambah posisi dengan selisih `SystemClock.elapsedRealtime()` dan menghitung fraksi, sehingga bar bergerak halus. `c(true)` menjeda bar. Arti field `ardm` disimpulkan dari pemakaiannya.

### Layout overlay dan tombol

- Layout `reel_player_overlay` berisi 6 `ViewStub`, 5 `FrameLayout`, 2 `ImageView`, 1 `ProgressBar`, 1 `View`, dan `ShortsPlayerProgressPresenter`. APK juga memuat Litho; tombol kanan overlay online tidak ditemukan sebagai layout XML, dan jalur pembangunnya belum ditelusuri.
- Layout `reel_player_right_buttons_offline` dan `reel_player_right_footer_offline` adalah **satu-satunya** versi layout tombol kanan dan footer; tidak ada versi tanpa akhiran `_offline`. Keduanya di-inflate oleh `mps.a` dan diikat oleh `mpt`. Klik pada semua view yang diikat hanya membangun prompt dengan string `shorts_smart_downloads_overlay_button_text` atau `emergency_buffer_disabled_button_text` (mode `OFFLINE_MODE_TYPE_EMERGENCY_BUFFER`). Layout ini adalah overlay mode unduhan offline.
- Isi layout tombol: lima `YouTubeTextView` berukuran 64 × 76 dp dengan teks 12 sp dan padding atas 12 dp, serta satu `RoundedFrameLayout`. Footer memuat avatar (`CircularImageView`), nama channel 14 sp, dan `SubscribeButton` 14 sp; margin kiri-kanan footer 16 dp.
- **`SubscribeButton` tidak terikat.** Kelasnya tidak punya field dan hanya meng-override `setCompoundDrawables*`. Tidak ada referensi kode ke kelas ini, tidak ada instruksi yang menyebut tipenya, dan id `0x7f0b1164` pada layout footer tidak dipakai di kode. Karena itu, logika klik subscribe **tidak ditemukan** di APK ini, dan tombol di layout offline tidak dapat dianggap sebagai bukti tombol subscribe pada overlay Shorts online. Pengamatan endpoint (bagian berikut) menunjukkan bahwa tombol subscribe Shorts online dikirim server di dalam `reelPlayerOverlayRenderer`, jadi tidak berasal dari layout XML ini.
- Konstanta dimen terkait (`reel_player_right_cell_width`, `..._cell_height`, `..._cell_padding`, `..._container_margin`, `reel_shorts_button_label_font_size`) tidak dipakai oleh instruksi mana pun; hanya layout XML yang memakainya. Semuanya hanya memiliki nilai default tanpa varian layar lebar.

| Resource | Nilai default |
|---|---|
| `reel_player_right_cell_width` | 64 dp |
| `reel_player_right_cell_height` | 76 dp |
| `reel_player_right_cell_padding` | 4 dp |
| `reel_player_right_cell_top` | 12 dp |
| `reel_player_right_container_margin` | 16 dp |
| `reel_shorts_button_label_font_size` | 12 sp |

### Daftar bukti

Bukti REA berikut berasal dari sesi 2026-10-31 yang sudah ditutup; hasilnya dapat diulang dengan menjalankan kembali pemeriksaan yang sama. Temuan dari `aapt2`, `dexdump`, dan `strings` tidak memiliki ID bukti.

| Temuan | Bukti |
|---|---|
| `ReelWatchPagerViewPager` | `ev_8982a1505eea18ef964e645c9d5f4f178755f47e4e8adfbd9643a7083393d5c1` |
| `ReelRecyclerView` (kelas) | `ev_26fb79495bb795de8f07e30c0b662602ad2e6a8a02b0e700cdcd21702a897174` |
| `ReelRecyclerView.onInterceptTouchEvent` | `ev_bb7e723027220775eeba588dda80fa42a06b1fbd20787485f31564c5ca029cd8` |
| `ReelLinearLayoutManager` (kelas) | `ev_f11422d5b21a0cbbba545446c4841307be06653cf250a12a558e51d16e24e250` |
| `ReelLinearLayoutManager.ay` | `ev_6f5300949e730da71c53e9216d3196591dc14f78730dd7a68ea60c43e7da0128` |
| `ReelLinearLayoutManager.ao` | `ev_28994390f6cc6c1086b6a48df3513342edddeb01537caf6f7e7e2ce4d3aadf04` |
| `ReelPlayerView` (kelas) | `ev_56906c94cb6515e465a12a58fa4a4136458e1d2fccf7e1be0895c6d14d2be687` |
| `ReelPlayerView.onMeasure` | `ev_d71e3ba0ac06b5a42f199d110b2c19c1acc476539d4bc6349da2691042d41073` |
| `ShortsPlayerView` (editor) | `ev_4fab443aee4e70bcb180d8d852bb44c65256618a15e9fb11a47a693c87434754` |
| `ShortsPlayerProgressPresenter.onDraw` | `ev_5c4c2942e24c060b9b58c055315e55e73f5d1253d6d1c0979b538912afe6e142` |
| `ShortsPlayerProgressPresenter.a` | `ev_3b09e13faba160102c11d67a97a6812eff592237d0a74cc664a9365ae612f282` |
| `ShortsPlayerProgressPresenter.b` | `ev_3d99174bcb1eca741c4caf7f53ae144e386ab9d70450a499490a3df59adc6ffb` |
| `ShortsPlayerProgressPresenter.c` | `ev_ba0a090c11bd1a80ba68a3d28097a9f25d13e24ef1b559bfd0b324509018ee0e` |
| `blhc` (pesan protobuf) | `ev_4005a6a47309f0ab7433a40bba561e4310e6921cb61331c0f9dd3074ffa0e2ad` |
| `SubscribeButton` (kelas) | `ev_cf587ecbb2d42609c89f239208499ef341dfbd9f764599d4fb04dd61947ff8a8` |
| Referensi ke `SubscribeButton` (0) | `ev_1e7bb1f2c47f517631eabd1369e0699496f2326ec139f1f67ce1f8bc3ff1203a` |
| Pemanggil `ShortsPlayerProgressPresenter.a` | `ev_352300101217ce2b0175ba526610248b2c4c8c7fe8555c427bea701d86ece544` |
| `mpt` (pengikat layout offline) | `ev_e906fea28eeb1ad25442ce95d0f313186e87357d0d01e1e04a849f7aab02b3f6` |
| `mpt.onClick` | `ev_afc2533769842af8401b883cf82626c153d0891b3da059cf23ccd1d3902d385a` |
| `atna` (kelas) | `ev_230218dff4a98095ede3de5d37f5f2955965dfdd6d1c18f1a74e123b55abb62f` |
| `atna.<init>` | `ev_2c6b37683816a9ea688e9eb4b54cdf8a01675a0f20038c1cc4a5083e052ebbcb` |
| `atna.a` | `ev_7b4de2d1b52ec7cd1b8116e24f96976543da83325ec62d59afc17995e3cd6994` |
| `atna.lm` | `ev_ed3ce4f7103db60b2cb73cc2a475eb040c6cc7209781a636039afdd06cb3f9c9` |
| `atmy` (kelas) | `ev_8069d94ee99282e850a03a96ca191dda0b9efab2ae28a400f8f529e23f98da07` |
| `atmy.a` | `ev_d5a0ef9c7c6831a755e075ac953929fb16643a30ff13d2538313457e0bc6dd0e` |

## Pengamatan endpoint (tamu, 2026-10-31)

Pengamatan memakai `curl` dari komputer pengembang tanpa cookie, akun, atau kunci pribadi. Hanya beberapa permintaan; respons disimpan di scratchpad dan tidak ikut dilampirkan.

1. `GET https://www.youtube.com/shorts` mengembalikan 200 (sekitar 1,17 MB). Halaman memuat `ytcfg` dengan `INNERTUBE_CONTEXT_CLIENT_VERSION`, lalu `ytInitialData` dengan `replacementEndpoint.reelWatchEndpoint` (Short pembuka), dan `sequenceContinuation`, token 176 karakter yang di dalamnya memuat ID video pembuka.
2. `POST https://www.youtube.com/youtubei/v1/reel/reel_watch_sequence?prettyPrint=false` dengan JSON `{"context":{"client":{"clientName":"WEB","clientVersion":"<dari halaman>","hl":"en","gl":"ID"}},"sequenceParams":"<sequenceContinuation>"}` mengembalikan 200. Respons memuat `entries` (9 entri) dan `continuationEndpoint.continuationCommand.token` (760 karakter, `request: CONTINUATION_REQUEST_TYPE_REEL_WATCH_SEQUENCE`). Tiap entri berbentuk `command.reelWatchEndpoint` dengan `videoId`, `playerParams`, `params`, dan `thumbnail`. Respons juga memuat penanda `GetReelWatchSequence_rid`, nama yang sama dengan string di APK. Permintaan ini berhasil tanpa API key, `visitorData`, `Origin`, atau `Referer`, dan ukurannya sekitar 125 KB.
3. Dengan tambahan API key, `visitorData`, `Origin`, dan `Referer`, respons yang sama membesar menjadi sekitar 737 KB. Entri pertama kini membawa `unserializedPrefetchData`: `playerResponse` lengkap (`streamingData` dengan URL, `videoDetails`, caption, storyboard) dan `reelItemWatchResponse` (berisi `reelPlayerOverlayRenderer` dengan `subscribeButton`). Entri lainnya hanya memuat endpoint. Inilah bentuk prefetch yang tersirat dari string `SHORTS_PREFETCH_REEL_WATCH_SEQUENCE` di APK. Penyebab pasti perbedaan ukuran tidak diisolasi satu per satu.
4. `POST` yang sama dengan `"continuation": "<token>"` menggantikan `sequenceParams` mengembalikan 200 dengan 10 entri baru, tanpa tumpang tindih dengan halaman sebelumnya, serta token berikutnya (1280 karakter). Jadi paginasinya berantai lewat token.

Batas pengamatan: semua respons bertanda `loggedOut: true`. Perilaku untuk akun yang login (isi, urutan, keberhasilan dengan header SAPISIDHASH) belum diamati. Judul dan channel hanya ada pada entri dengan prefetch; untuk entri lain, metadata harus diambil terpisah.

## Perbedaan UI dan interaksi

### Sebab keluhan "tidak responsif dan tidak smooth" (kode sebelum 2026-10-31 sore)

Penelusuran kode alur tombol Shorts menemukan penyebab berikut. Semuanya dari membaca kode; durasi sebenarnya belum diukur di perangkat.

- **Layar tunggu.** Menekan tab menampilkan placeholder `LibraryTab` (ikon, judul, spinner). Layar Shorts baru muncul setelah feed diambil **dan** `StreamInfo.getInfo()` untuk Short pertama selesai, lalu berpindah mendadak.
- **Geser tidak mengikuti jari.** `ShortsScreen` memakai `detectVerticalDragGestures` dan baru bertindak saat jari dilepas melewati 48 dp. Tidak ada animasi geser maupun snap.
- **Geser dibuang saat memuat.** `openUrl` langsung `return` bila `loading` true, sehingga geser selama pemuatan hilang tanpa pesan.
- **Resolve baru dimulai setelah geser.** Tiap Short di-resolve satu per satu setelah pengguna menggeser; selama itu video lama tetap tampil, lalu berganti mendadak. Mundur ke Short sebelumnya juga me-resolve ulang.
- **Pekerjaan berlebih per Short.** `openUrl` merangking ulang rekomendasi dan menulis `HomeFeedCache` untuk setiap Short, dan `rememberComments` memuat komentar tiap Short walau panel tidak dibuka.
- **Navigasi.** Layar Shorts menutup bilah navigasi dan memakai tombol X; menutupnya mendarat di placeholder "Buka Shorts" dengan tab Shorts masih aktif. Tombol kembali meminimalkan Short menjadi mini player.

### Perubahan yang dikerjakan (belum diuji di perangkat)

- [`ShortsScreen.kt`](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt) kini `VerticalPager` yang mengikuti jari dan snap satu halaman per fling. Tiap halaman langsung menampilkan poster dari ID video (`frame0.jpg`, cadangan `hqdefault.jpg`) dan rangka judul; video muncul di atasnya. Satu `ExoPlayer` dipasang hanya ke halaman aktif, lewat `PlayerView` bertipe `texture_view` ([`shorts_player_view.xml`](../flufplay/app/src/main/res/layout/shorts_player_view.xml)) agar ikut bergerak bersama halaman.
- [`ShortsResolver.kt`](../flufplay/app/src/main/java/id/flufclip/player/ShortsResolver.kt) menyimpan hasil resolve (maks. 16) dan me-resolve halaman aktif, +1, +2, dan -1 di latar belakang (maks. 2 bersamaan). Efek pemutaran (`MainActivity.kt:739`) mengambil dari cache, sehingga geser ke Short yang sudah siap tidak menunggu jaringan. Efek prefetch ada di baris 761. Hasil resolve kedaluwarsa setelah 30 menit.
- Jalur Shorts tidak lagi melewati `openUrl` dan flag `loading` (`startInfo` untuk video biasa, `startShort` baris 605 untuk Shorts). Tidak ada lagi rangking rekomendasi per Short, dan komentar dimuat hanya saat panel dibuka.
- Menekan tab Shorts langsung membuka layar Shorts (`selectTab`, baris 870) dengan spinner, lalu antrean diisi dari feed. Bilah navigasi tetap tampil, tombol X hanya ada bila Shorts dibuka dari luar tab (hasil pencarian atau channel), dan tombol kembali menutup Short lalu ke Beranda. Pembukaan channel dari Short menutup Shorts; kembali dari channel melanjutkan Shorts.
- Gagal me-resolve satu Short menampilkan pesan dan tombol "Coba lagi" di halaman itu, bukan membekukan antrean.

### Tahap kedua (juga belum diuji di perangkat)

- **Buffer ke depan.** Setelah Short berikutnya selesai di-resolve, `preloadNext` (`MainActivity.kt:625`) menambahkannya ke daftar putar `ExoPlayer` sebagai item kedua; ExoPlayer mem-buffernya setelah item sekarang terisi. Geser ke item itu (`startShort`) hanya pindah item (`seekTo` lalu hapus item lama), tanpa memuat ulang. Daftar putar dijaga maksimal dua item: [sekarang, berikutnya]. Mundur ke Short sebelumnya tidak di-buffer lebih dulu.
- **Loop.** `REPEAT_MODE_ONE` tidak dipakai, karena dengan mode itu ExoPlayer tidak memuat item berikutnya. Sebagai gantinya `pauseAtEndOfMediaItems = true`, dan pemutar mengulang item dari awal saat berhenti di akhir (`onPlayWhenReadyChanged`, atau `STATE_ENDED` untuk item terakhir).
- **Pemulihan.** Bila pemutar error saat Shorts (misalnya item yang di-buffer ditolak server), `recoverShort` (baris 640) membuang cache Short itu dan memutarnya ulang sekali; bila tetap gagal, halaman menampilkan pesan dan "Coba lagi".
- **Short pertama lebih cepat.** `ShortsFeed.start` memanggil `onSeed` begitu Short pembuka diketahui (setelah halaman `/shorts` terbaca, sebelum permintaan urutan selesai), sehingga poster dan resolve-nya berjalan paralel dengan permintaan urutan. Selain itu `ShortsFeed.warm()` (dipanggil 4 detik setelah aplikasi dibuka bila belum di tab Shorts) menyiapkan halaman pertama dan me-resolve Short pertamanya selagi pengguna di beranda; hasilnya dipakai sekali dan dibuang setelah 3 menit karena token urutan terikat sesi. Bila permintaan urutan gagal setelah Short pembuka tampil, Short itu dipertahankan dan antrean diisi dari pencarian.
- **Data prefetch di respons feed tidak dipakai.** Yang terlihat di dalamnya adalah `playerResponse` klien web, tetapi URL stream di sana belum terbukti bisa diputar (kemungkinan butuh transformasi parameter `n` dan token), sedangkan jalur NewPipe sudah terbukti. Latensi dipotong lewat jalur paralel dan warm-up di atas.
- **Bar progress.** Satu segmen setinggi 2 dp di tepi bawah area video (bukan bar bersegmen milik YouTube), digambar langsung dari posisi pemutar per frame tanpa rekomposisi.
- **Suka dan tidak suka.** `ShortsFeed.rate` (`ShortsFeed.kt:94`) mengirim `like/like`, `like/dislike`, atau `like/removelike` ke InnerTube dengan cookie dan SAPISIDHASH. Tampilan memakai status lokal yang langsung berubah dan dikembalikan bila permintaan gagal. Tanpa login, ketukan membuka dialog login. **Bentuk permintaan mengikuti klien web dan belum diuji dengan akun; status suka awal dari akun tidak diketahui.** Jumlah suka di label hanya ditambah 1 secara lokal.
- **Menu Opsi.** Salin tautan, Buka di YouTube, dan Tidak tertarik (menambah video ke daftar sembunyi `hiddenVideos` yang sudah dipakai beranda, lalu pindah ke halaman berikutnya).

### Tahap ketiga: gestur, ukuran, dan panel (2026-10-31, dibuild, belum diuji di perangkat)

Mengikuti rekomendasi di [`ANALISIS_GESTUR_UX_SHORTS_APPBAR_YOUTUBE_REA.md`](ANALISIS_GESTUR_UX_SHORTS_APPBAR_YOUTUBE_REA.md) dan [`ANALISIS_PANEL_SHORTS_YOUTUBE_REA.md`](ANALISIS_PANEL_SHORTS_YOUTUBE_REA.md). Kode: [`ShortsGestures.kt`](../flufplay/app/src/main/java/id/flufclip/player/ShortsGestures.kt), [`ShortsScreen.kt`](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt), `WatchPanels.kt` (`CommentsPanel`), dan `MainActivity.kt` (`HomeNavigation`).

- **Ketuk** = putar/jeda. Bila halaman sudah punya info (bisa disukai), ketuk tunggal ditunda selama jendela ketuk kedua (`doubleTapTimeoutMillis`, bawaan 300 ms) seperti `mna.c` di YouTube; tanpa info langsung. Saat jeda: scrim tipis di seluruh halaman dan ikon putar 96 dp yang muncul dan menghilang dengan animasi (sebelumnya ikon statis 54 dp).
- **Ketuk dua kali** = suka (tidak membatalkan suka yang sudah ada). Hati putih 120 dp muncul di titik ketuk, membesar lalu memudar. Tanpa login, ketukan membuka alur login yang sama dengan tombol suka dan hati tidak muncul.
- **Tahan** = 2x selama ditahan, di mana saja pada video (YouTube membatasi ke zona tepi 20% bila flag zona aktif; lihat dokumen gestur). Getar saat mulai, lencana "2x" 32 dp di tengah-atas dengan ikon maju-cepat, kolom aksi/judul/gradasi/bar atas memudar dalam 125 ms, pager tidak menggulir. Tarik jari lebih dari 20 dp ke bawah lalu lepas = kunci 2x; tahan lagi, tarik ke bawah, lepas = keluar dari kunci. Petunjuk teks ("Tarik ke bawah untuk mengunci 2x", "Lepas untuk mengunci 2x", dan padanan untuk keluar) tampil di tengah-bawah. Lencana tetap tampil saat terkunci (YouTube menyembunyikannya; ini pilihan desain). Kunci direset saat pindah Short; kecepatan awal pemutar dikembalikan saat layar Shorts hilang.
- **Scrub:** area sentuh 32 dp di tepi bawah; geser horizontal menjeda pemutar, menebalkan bar (2 dp menjadi 6 dp), menampilkan cap waktu 24 sp di tengah halaman, dan seek saat dilepas lalu melanjutkan bila tadinya diputar. Mode bersih (overlay memudar) aktif selama scrub. Pratinjau thumbnail belum ada.
- **Ukuran:** gradasi atas 96 dp (sebelumnya 130), gradasi bawah 160 dp (sebelumnya 260), bar progress dengan margin horizontal 8 dp.
- **Tombol keluar:** ikon X diganti panah kembali berlatar lingkaran semi-transparan.
- **Komentar:** scrim 40% di luar panel; ketuk scrim menutup panel, dan saat balasan terbuka mengembalikan ke daftar komentar. `CommentsPanel` menerima `threadState` agar status balasan bisa dikendalikan dari luar.
- **Menu titik tiga:** `DropdownMenu` diganti lembar bawah (`ModalBottomSheet`, sudut atas 16 dp, latar diredupkan, bisa digeser) berisi **Kecepatan putar** (0,25x sampai 2x, daftar yang sama dengan pemutar biasa), Salin tautan, Buka di YouTube, dan Tidak tertarik. Pilihan kecepatan bertahan selama layar Shorts terbuka dan berlaku untuk semua Short.
- **Bilah navigasi:** garis pemisah `#303030` dihapus (di tema gelap YouTube memakai garis transparan); latar tetap `#0F0F0F`.

Belum dikerjakan (dengan alasan): tarik-ke-segarkan, geser kiri ke channel, dan cubit-zoom (prioritas rendah dan tidak terbukti perlu); urutan komentar dan kolom tulis komentar (perlu dukungan extractor dan login); panel bagikan mengintip dan chat live Shorts (belum terbukti bentuk akhirnya); header "Shorts" vs hanya ikon (butuh tangkapan layar YouTube asli). Perilaku yang tidak terbukti dari APK dan sengaja dipilih sendiri: 2x dari mana saja, lencana tetap tampil saat terkunci, dan pager dikunci selama panel komentar terbuka.

### Tahap keempat: paritas dengan screenshot YouTube tablet (2026-10-31, dibuild, belum diuji di perangkat)

Dasar: enam screenshot aplikasi YouTube (Shorts, komentar Shorts tablet, layar penuh, mini player, layar tonton tablet). Bukti APK 21.43.523 bersifat statis; probe endpoint sebagai tamu.

Dikerjakan (paket tanpa login):

- **Bar progress merah** (`reel_time_bar_played_color` = `yt_medium_red` di APK) dengan titik penggeser saat jeda atau scrub; bar naik 6 dp supaya titik tidak tertutup bilah navigasi.
- **Koreksi 2026-11-02:** pada pemeriksaan sebelum implementasi plan, rail ternyata memakai thumbs-up terisi, bukan heart seperti catatan sebelumnya. Kini dipakai heart outline putih dan heart terisi merah saat disukai; slot dislike di rail Shorts dihapus. Ini implementasi berdasarkan pemetaan aset statis, belum diverifikasi pada perangkat.
- **Jumlah komentar** di bawah ikon komentar: dimuat untuk halaman aktif (`commentStates`, maks. 8) dan dipakai ulang oleh panel komentar.
- **Header:** ikon volume (mute, tersimpan selama layar Shorts terbuka) dan search (membuka pencarian beranda) di samping menu titik tiga.
- **Panel komentar samping** untuk lebar >= 840 dp: lebar 31% (batas 340-460 dp; angka dari screenshot, bukan dari APK), tanpa scrim, video bergeser ke tengah area kiri. Di bawah 840 dp tetap bottom sheet 70%.
- **Tombol unduh** (ikon ⤓) di bawah ikon jeda. Layout `reel_player_paused_state` menyediakan wadah tombol (margin atas 80 dp) dan APK punya string "Save to device"; bahwa itu tombol unduh adalah kesimpulan, isi wadahnya diisi kode yang belum dilacak.
- **Avatar di tab Anda** saat login.
- **Layar penuh:** nama channel di bawah judul dan kartu "Video lainnya" (daftar video terkait) di kanan bawah.
- **Mini player:** jeda di kiri atas dan tutup di kanan atas; buka pemutar pindah ke kanan bawah; +-10 detik hanya bila lebar >= 300 dp. XML `modern_miniplayer_controls_overlay` justru menaruh play/pause di tengah dan expand di kiri atas, jadi susunan ini meniru screenshot, bukan XML.

Ditunda atau tidak dikerjakan: chip "Subscriptions" dan "Live" (tab Subscription FlufPlay masih placeholder; `browse FEshorts` ditolak sebagai tamu; layout `reel_browse_root_view_modern_tabs` ada di APK), Subscribe, Save, like di layar tonton dan layar penuh, kirim komentar (semuanya perlu login dan endpoint baru yang belum teruji), sort komentar (NewPipeExtractor v0.26.3 tidak punya API-nya), pill video terkait Shorts, Remix, Sound, Ask (Gemini), Thanks, Report, tombol "+", dan Cast.

### Tahap kelima: koreksi gestur tahan, layar penuh, riwayat (2026-10-31, dibuild, belum diuji di perangkat)

- **Tahan di Shorts:** zona tepi 20% di kiri/kanan tetap 2x (tarik ke bawah = kunci); **zona tengah membuka panel deskripsi** (`DescriptionPanel` dipakai ulang; bottom sheet 70% atau panel samping >= 840 dp). Mengetuk judul juga membuka deskripsi. Dasarnya: `atez.ci(2)` menjalankan perintah server `blgz.x`, dan respons memuat `engagement-panel-structured-description` (lihat koreksi di dokumen gestur). Isi perintah server disimpulkan dari pengamatan pengguna, bukan dari APK.
- **Bar progress:** di Shorts hanya tampil (merah + titik) saat jeda atau scrub; saat video diputar tanpa disentuh bar disembunyikan. Ikon keadaan jeda diganti dua batang (sesuai screenshot). Di layar penuh bar ikut hilang saat kontrol tersembunyi; di pemutar inline garis tipis tetap (asumsi: pengguna menyebut "fokus video"; inline tidak diubah).
- **Layar penuh** (acuan: screenshot YouTube): ikon asli set "Delhi" dari APK (`yt_delhi_*`, WebP per densitas, dipakai tanpa tint karena membawa bayangan sendiri) untuk suka, tidak suka, komentar, simpan, bagikan, lainnya, dan cast. Latar gelap: pil waktu, pil baris aksi, kartu "Video lainnya" (thumbnail berbingkai putih), lingkaran di belakang tombol keluar layar penuh. Tombol cast di kanan atas (inline dan layar penuh) hanya membuka pengaturan Cast/Smart View sistem, bukan Google Cast. Baris aksi: suka/tidak suka lewat `ShortsFeed.rate` (perlu login), komentar sebagai panel samping, simpan lewat `browse/edit_playlist` (playlist WL; **belum diuji dengan akun**), bagikan, dan menu Unduh/Salin tautan/Buka di YouTube. Tombol "Ask" (Gemini) sengaja tidak dibuat.
- **Bar atas beranda/hasil pencarian:** ikon muat ulang, unduhan, dan profil dihapus (tersisa telusuri). Akun dan unduhan tetap bisa dibuka dari tab Anda; muat ulang beranda lewat tarik ke bawah.
- **Riwayat:** entri yang ditonton lewat layar Shorts (`short = true`, disimpan di `WatchedVideo`) kini dibuka di layar Shorts; entri lama tanpa tanda dianggap Short bila durasi <= 60 detik.

### Tahap keenam: ikon player dan layar tonton dari screenshot berdampingan (2026-10-31, dibuild, belum diuji di perangkat)

Acuan: screenshot layar terbagi (kiri aplikasi YouTube 21.43.523, kanan FlufPlay) di tablet; skala ±2,23 px per dp (dihitung dari tombol putar FlufPlay 56 dp = 125 px).

- **Ikon maksimize/minimize:** di YouTube bentuknya panah diagonal ↖↘ (masuk layar penuh) dan panah ke dalam (keluar) di lingkaran gelap tembus pandang ±36 dp; FlufPlay memakai kurung sudut (`player_full_enter`/`exit`). Di APK ada empat varian vektor: `yt_outline_experimental_player_full_enter_alt`/`exit_alt` (panah diagonal) dan `..._full_enter`/`exit` (kurung sudut). Varian `_alt` cocok dengan screenshot dan sekarang dipakai untuk masuk dan keluar. Pemilihan `_alt` untuk *keluar* disimpulkan dari pasangannya (belum terlihat langsung di screenshot).
- **Ikon atas player:** gear enam lobus, CC tebal, cast, dan chevron dari set "experimental" APK (`yt_outline_experimental_gear/closed_captions/chromecast/chevron_down`), diekstrak sebagai vektor putih tanpa bayangan, ukuran ±27 dp (chevron 24 dp), tombol 48 dp (jarak antar ikon di screenshot ±48-52 dp). Sebelumnya gear Material tipis dan cast Delhi bersuara.
- **Bar progress inline:** di YouTube bar selebar video menempel di tepi bawah dan titik merah menumpang di atasnya (di FlufPlay bar bersisi 12 dp dan 14 dp di atas tepi). Sekarang tanpa sisi, 4,5 dp di atas tepi; titik tidak keluar dari tepi kiri/kanan. Titik YouTube sedikit menjorok ke bawah video; FlufPlay tidak bisa karena player dipotong ke batasnya.
- **Waktu inline:** pil tipis (padding 5 dp × 2 dp) dengan teks putih biasa, bukan pil besar dengan angka tebal; di layar penuh tetap pil besar. Tombol layar penuh inline 36 dp, 29 dp dari bawah dan 28 dp dari kanan.
- **Perbaikan sentuh:** area sentuh TimeBar menumpuk dengan tombol layar penuh dan berada di atasnya (menyentuh bagian bawah tombol malah seek). TimeBar dipindah di bawah lapisan kontrol.
- **Layar tonton:** judul 18 sp huruf biasa (sebelumnya YouTube Sans Bold) dan metadata tanpa titik pemisah 13 sp; baris channel dengan avatar 32 dp dan nama biasa; tombol "Lihat channel" diganti **Berlangganan** (terang `#F1F1F1`, tinggi 32 dp; "Telah berlangganan" gelap dengan menu berhenti). Chip aksi tinggi 32 dp dengan ikon garis dari APK: pil gabungan [suka | tidak suka], Bagikan, Simpan, Unduh, Salin link. Kartu komentar: padding diperketat, satu komentar teratas yang bisa digeser (maks 3) dengan tiga titik penanda di kanan atas.
- **Tidak dibuat:** "Ask" (Gemini), "Hype", "Laporkan" (tidak punya tujuan yang masuk akal), dan karusel video besar setelah kartu komentar (FlufPlay menyisipkan baris "Dari channel ini").
- **Belum teruji (butuh akun):** `ShortsFeed.setSubscribed` (`subscription/subscribe` dan `unsubscribe`, nilai `params` dari ingatan atas klien web), suka/tidak suka di layar tonton, dan Simpan. Status awal langganan/suka dari akun tidak diketahui, jadi dimulai sebagai "belum".

### Tahap ketujuh: tampilan siaran live (2026-10-31, dibuild, belum diuji di perangkat)

Dasar: APK 21.43.523 (resource + dexdump + JADX satu kelas) dan respons InnerTube WEB tanpa login untuk video live sungguhan (pencarian dengan filter Live, `next`, `browse` channel CNN). Teks kartu dan metadata dikirim server, jadi bukti utamanya adalah bentuk data itu.

**Terbukti dari APK (kontrol player).** Label live bernama `time_bar_live_label` (layout `youtube_controls_bottom_ui_container`): `TextView` "LIVE" (`live_label`), berat normal, 12 sp (14 sp di sw600dp), putih statis, `drawableStart` = `player_live_dot` dengan `drawablePadding` 8 dp, latar pil yang sama dengan waktu (`YouTubePlayerButton`). Titik 6 dp: merah `#FF0033` saat di ujung live, putih 70% (`player_notlive_dot`) saat tertinggal. Kelas pengatur label (`off`, dicek dengan dexdump + JADX): titik live dipasang bila `h(true)`, titik abu-abu bila tidak; label hanya bisa diketuk saat tertinggal; saat di ujung live teks posisi disembunyikan; sambil menggeser, waktu tertinggal ditampilkan berawalan "-" (`off.e` menambah '-' pada mode live) dan label disembunyikan. Warna rentang seek live = `#E6CC0000`. String aksesibilitas "%1$s behind live" ada.

**Terbukti dari data server.**
- Kartu video live (lockup): lencana LIVE hanya di thumbnail kanan bawah (`THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE`, ikon `LIVE` + teks "LIVE"); metadata hanya baris channel dan baris "7.9K watching"; **tidak ada** chip LIVE di metadata dan **tidak ada** cincin di avatar kartu.
- Layar tonton: `viewCount` = "1,341 watching now", `dateText` = "Started streaming on Jun 15, 2026" (bentuk relatif untuk siaran baru, ambang tepatnya tidak terlihat), hashtag di atas judul; baris pemilik tanpa penanda live. Kartu chat = `videoMetadataCarouselViewModel` berjudul "Live chat" dengan item statis "Join the conversation to interact with the creator and others watching this live stream." lalu pesan chat.
- Header channel: `decoratedAvatarViewModel.liveData.liveBadgeText = "LIVE"` (cincin + lencana di avatar besar). Tidak ada penanda live di sebelah nama channel.

**Perbedaan FlufPlay sebelumnya dan perbaikannya.**

| Bagian | Sebelumnya | Sekarang |
|---|---|---|
| Kartu beranda / pencarian / terkait | chip LIVE merah + teks merah, cincin merah di avatar | teks abu-abu "Channel · N sedang menonton" (terkait: dua baris), tanpa chip dan tanpa cincin; lencana thumbnail tetap |
| Metadata di bawah judul tonton | seluruh baris merah dan kata "LIVE" | abu-abu: "N sedang menonton   Mulai streaming … " + hashtag |
| Baris pemilik tonton | cincin merah + chip LIVE | sama dengan video biasa |
| Header channel | chip LIVE di samping nama | hanya cincin + lencana LIVE di avatar |
| Label LIVE di player | titik merah 7 dp selalu, teks tebal 12 sp tanpa latar | titik 6 dp merah/abu-abu menurut posisi, teks normal, latar pil, bisa diketuk hanya saat tertinggal; sambil menggeser tampil "-m:ss" |
| Panel deskripsi | kolom ketiga "LIVE / Status" | tanggal mulai (bila diketahui) |
| Kartu chat di layar tonton | titik merah, "Teratas", panah | judul "Live chat" saja + baris statis server, lalu pesan terbaru |

Pilihan sendiri (bukan bukti): ambang "tertinggal" = offset target pemutar + 5 detik (cadangan 10 + 5 detik); ambang tujuh hari antara "Mulai streaming 2 jam yang lalu" dan "Mulai streaming pada 15 Jun 2026"; kata Indonesia "Mulai streaming". Tanggal mulai hanya tampil bila extractor memberi `uploadDate` untuk siaran yang sedang berlangsung (tidak diverifikasi). Belum diverifikasi di layar: apakah aplikasi Android memakai satu baris atau dua baris metadata pada kartu beranda, dan gaya lencana thumbnail (ukuran/radius) dibanding versi tablet.

### Tahap kedelapan: siaran live di beranda (2026-10-31, dibuild, belum diuji di perangkat)

Pertanyaan pengguna: kenapa beranda FlufPlay tidak menampilkan siaran live dari channel rekomendasi seperti di YouTube.

Bukti (tamu, WEB InnerTube, 2026-10-31):
- `browse` `FEwhat_to_watch` tanpa login hanya mengembalikan satu bagian "Coba telusuri untuk memulai" (`feedNudgeRenderer`), tanpa satu pun video. Beranda asli yang berisi live dari channel yang relevan hanya terbentuk dengan akun, jadi FlufPlay tidak bisa menirunya dari tamu.
- `browse` `FEtrending` mengembalikan 400 (halaman Tren sudah dihapus).
- NewPipeExtractor 0.26.3 menetapkan kiosk bawaan YouTube = `live` (`YoutubeLiveExtractor`, channel `UC4R8DWoMoI7CAwX8_LjQHig`, params `EgdsaXZldGFikgEDCKEK`). Endpoint itu masih hidup: 15 `videoRenderer` live + satu `continuationItemRenderer`. Jadi `VideoRepository.home()` memang sudah mengembalikan siaran live umum.

Penyebab di kode lama:
1. `HomeRecommendations.rank` menilai item hanya dari kecocokan dengan riwayat (nama channel, kata judul) dikurangi penalti urutan. Siaran dari kiosk tidak cocok dengan riwayat, jadi selalu jatuh ke bagian akhir daftar (bahkan terpotong dari cache 40 item).
2. Tidak ada kode yang mengecek apakah channel yang sering ditonton sedang live.
3. Chip "Live" memanggil `searchPage("Live")`, yaitu pencarian kata "Live", bukan daftar siaran yang berlangsung.
4. Riwayat tonton hanya menyimpan nama pengunggah, tanpa URL channel.
5. Cache feed bisa menyimpan item bertanda live yang sudah berakhir.

Perubahan:
- `WatchedVideo.channelUrl` (disimpan dari `StreamInfo.uploaderUrl`; entri lama dicocokkan lewat nama pada item feed yang sudah dimuat).
- `VideoRepository.liveFromChannels`: tab Live tiap channel (dua permintaan, tiga paralel, batas 12 detik, cache lima menit, gagal = dilewati); `liveKiosk`/`moreLiveKiosk` memakai kiosk `live` dengan paging.
- Beranda "Semua": cek channel berjalan paralel dengan pemuatan feed, hasilnya (maks. 4) disisipkan di awal feed setelah feed tampil; `rank` memberi bobot live (+16 untuk channel yang ditonton, +8 untuk yang umum) agar tidak tenggelam.
- Chip "Live": siaran dari channel yang ditonton lebih dulu, lalu kiosk live dengan scroll tak terbatas.
- `HomeFeedCache` tidak menyimpan dan tidak memakai item live dari cache.

Pilihan sendiri (bukan bukti dari YouTube): jumlah channel yang dicek (6 terbaru), batas 4 item di awal, bobot +16/+8, TTL 5 menit, batas waktu 12 detik. Posisi live di beranda asli ditentukan server, bukan aturan yang bisa dibaca dari APK. Belum diuji: apakah NewPipe mengurai tab Live channel yang sedang siaran sebagai `LIVE_STREAM` pada semua channel (jalur yang sama dipakai `openChannel`), dan waktu muat sebenarnya di perangkat.

### Yang masih berbeda dari YouTube

- **Ukuran video:** `RESIZE_MODE_FIT`; APK YouTube memiliki pengukuran adaptif `ASPECT_FIT`/`ASPECT_FILL`.
- **Progress:** satu segmen; YouTube memakai bar bersegmen (video X dari Y).
- **Tombol:** tombol subscribe Shorts belum ada (di layar tonton sudah, belum teruji). Ikon suka aktif hanya diberi warna biru karena tidak ada aset ikon terisi.
- **Buffer ke belakang:** hanya Short berikutnya yang di-buffer lebih dulu.
- **Waktu mulai:** manfaat warm-up dan jalur paralel belum diukur.

## Status verifikasi

| Butir | Status |
|---|---|
| Versi, SHA-256, versionCode APK | Terbukti |
| Keberadaan kelas Reel/Shorts yang disebut | Terbukti |
| Arbitrase sentuhan `ReelRecyclerView` (arah, child horizontal, multipointer) | Terbukti secara statis |
| Smooth scroll kustom `ReelLinearLayoutManager` | Terbukti; nilai parameter float belum diketahui |
| Mode `ASPECT_FIT`/`ASPECT_FILL` | Terbukti; ukuran akhir tidak pasti dari dekompilasi |
| Kunci continuation, sequence, dan prefetch | Terbukti sebagai string; perilaku runtime belum |
| Endpoint JSON `reel_watch_sequence` (halaman awal dan continuation) dapat dipanggil tamu | Terbukti (pengamatan 2026-10-31) |
| Perilaku endpoint untuk akun yang login | Belum diamati |
| Sumber feed baru di FlufPlay (`ShortsFeed.kt`) | Dikompilasi; belum diuji di perangkat |
| Pager, `ShortsResolver`, dan alur tab Shorts baru | Dikompilasi; belum diuji di perangkat; durasi sebelum/sesudah belum diukur |
| Buffer ke depan lewat daftar putar `ExoPlayer` (asumsi: `MergingMediaSource.getMediaItem()` mengembalikan item video pertama, sehingga `mediaId` dapat dikenali) | Dikompilasi; asumsi belum diuji. Bila salah, pemutaran tetap jalan tetapi tanpa buffer ke depan |
| Suka/tidak suka lewat `like/*` | Dikompilasi; belum diuji dengan akun |
| Gestur Shorts FlufPlay (ketuk dua kali, tahan 2x dan kunci, scrub), scrim komentar, lembar menu Opsi, gradasi 96/160 dp, panah kembali, bilah navigasi tanpa garis | Dibuild (`assembleDebug` berhasil); belum diuji di perangkat. Rasa respons ketuk (jeda 300 ms bila ketuk dua kali mungkin), ambang tarik-bawah 20 dp, dan interaksi pager dengan gestur belum diamati |
| Progress bar bersegmen dan sumber waktunya | Terbukti secara statis |
| Respons server yang mengisi `blgz` (indeks/jumlah segmen) | Belum ditelusuri |
| Nilai efektif ambang sudut dan flag | Tidak dapat dibuktikan secara statis |
| Tombol subscribe Shorts online | Dikirim server di `reelPlayerOverlayRenderer`; layout XML tidak terikat; logika kliknya belum ditelusuri |
| Dimen 64 × 76 dp dan lainnya sebagai ukuran overlay Shorts online | Tidak terbukti (hanya layout offline) |
| Susunan overlay online di perangkat | Belum diamati |

## Arah pemeriksaan dan perbaikan berikutnya

1. **Verifikasi runtime.** YouTube `21.43.523` (versionCode `1561307650`) terpasang di perangkat RMX3630 dan sama persis dengan APK ini; perangkat lain yang tersedia memakai versi berbeda (`21.42.243`). Saat dicek, kedua perangkat terkunci. Langkah yang dibutuhkan: buka kunci perangkat, buka tab Shorts, lalu ambil `uiautomator dump` (hierarki view beserta ukuran) dan satu screenshot. Hasil yang dicari: ukuran dan susunan kolom aksi online, ada tidaknya tombol subscribe, area video (letterbox atau crop), serta bar progress.
2. Uji sumber feed baru di perangkat (buka tab Shorts, swipe sampai antrean terisi ulang, dengan dan tanpa login), lalu bandingkan urutannya dengan Shorts YouTube di perangkat yang sama. Telusuri juga respons item (`blgz`) dan pemicu `SHORTS_PREFETCH_REEL_WATCH_SEQUENCE` (kondisi, jumlah item, mode hemat daya); bedakan bukti kode APK dari perilaku jaringan yang benar-benar diamati. Pertimbangkan memakai data prefetch di respons (URL stream dan metadata) untuk mempercepat pembukaan Short.
3. Uji di perangkat: geser cepat, geser balik, geser saat Short berikutnya belum siap, buka komentar (ketuk scrim, balasan lalu ketuk scrim), tombol kembali, pindah tab, buka Short dari hasil pencarian/channel, menu Opsi (lembar bawah dan kecepatan putar), ketuk, ketuk dua kali, tahan dan kunci 2x, scrub bar progress, dan (setelah login) suka/tidak suka. Ukur waktu dari ketukan tab sampai poster dan sampai frame pertama, dengan dan tanpa warm-up, serta jeda setelah geser ke Short yang sudah di-buffer.
4. Susun ulang overlay berdasarkan hasil verifikasi runtime (lihat butir 1) dan pertimbangkan bar progress bersegmen. Jangan menyatakan algoritma server atau kesamaan piksel sudah terbukti tanpa bukti yang sesuai.
