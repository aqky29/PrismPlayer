# Rencana perbaikan FlufPlay secara lengkap

Tanggal penyusunan: **4 November 2026**.

Status dokumen: **implementasi P1 sedang berjalan; M01, M02, dan M03 belum memenuhi seluruh kriteria selesai**. Lihat catatan progres di bawah.

Dokumen sumber: [Analisis kekurangan FlufPlay](ANALISIS_KEKURANGAN_FLUFPLAY_2026-11-04.md). Rencana ini mencakup seluruh 11 temuan, memecahnya menjadi pekerjaan yang dapat dijalankan, dan menetapkan bukti yang diperlukan sebelum masing-masing pekerjaan dinyatakan selesai.

Pembuatan dokumen ini hanya menulis Markdown. Kode aplikasi, konfigurasi build, database, akun, dan file unduhan tidak diubah. Tidak dilakukan build, eksekusi test aplikasi, instalasi, atau pengujian perangkat.

### Catatan progres P1 — 4 November 2026

Perubahan berikut dilakukan setelah rencana awal disusun, pada checkout lokal yang sudah memiliki perubahan pengguna dan file yang belum dilacak:

- **M01 — sebagian:** jalur HLS dan DASH pada `playQuality()` menggunakan `DefaultMediaSourceFactory` dengan konfigurasi subtitle eksternal dan MIME manifest; konfigurasi live mengikuti jenis konten. Audio terpisah tetap dirangkai melalui `MergingMediaSource`.
- **M02 — sebagian:** pemuatan Beranda dibatalkan saat revisi sesi berubah dan memeriksa revisi sebelum menerapkan hasil. `HomeFeed.accountRequest()` mengambil snapshot header sebelum menunggu pemuatan versi client dan memeriksa kembali revisi sesi.
- **M03 — sebagian:** header media bawaan yang sensitif dibersihkan dan tujuan cookie diperiksa berdasarkan policy HTTPS, host, dan port. Network interceptor HomeFeed membersihkan header sensitif pada hop redirect ke tujuan yang tidak diizinkan.

**Verifikasi:** kompilasi dan unit test terarah `PlaybackRequestHeadersTest` serta `AuthHeaderPolicyTest` lulus menggunakan Gradle offline, Java 21, dan satu worker. `git diff --check` melaporkan tiga trailing whitespace yang sudah ada di `ShortsFeed.kt`; bagian tersebut tidak diubah dalam pekerjaan ini.

**Belum selesai:** audit dan isolasi seluruh request, state, continuation, dan cache akun; pengamanan redirect pada seluruh transport termasuk media; penanganan dan pengujian lengkap subtitle, termasuk Off serta kegagalan subtitle; dan pengujian integrasi serta perangkat untuk playback dan pergantian akun. Belum ada bukti bahwa subtitle benar-benar tampil atau bahwa seluruh jalur akun dan redirect sudah aman. Checklist M01–M03 di bagian 20 tetap belum dicentang.

### Hasil audit P1 — 4 November 2026

**M01, M02, dan M03 tetap berstatus sebagian; 0 dari 3 paket memenuhi seluruh kriteria selesai.** Audit kode dan laporan test yang tersedia menemukan delapan kelompok pekerjaan terbuka. Rincian, lokasi kode, serta uji penutup ada di [Audit P1 FlufPlay](AUDIT_P1_FLUFPLAY_2026-11-04.md).

| Paket | Sisa pengerjaan yang perlu didahulukan |
| --- | --- |
| M01 | Factory progresif masih berbeda; Off/CC belum mengikuti trek manifest; error subtitle dan DASH multi-period belum ditangani/diuji lengkap |
| M02 | Mutasi masih memakai auth global; respons langganan/paginasi lama dan cache/urutan Shorts belum terisolasi penuh per sesi |
| M03 | Policy hop belum mencakup seluruh transport; redirect media dan request akun lain belum dibuktikan lewat test jaringan |

Laporan test yang tersedia mencatat lima kasus helper header lulus. Audit tidak menjalankan ulang test/build dan tidak menguji akun/perangkat. Bagian masalah pada paket di bawah merupakan baseline rencana awal; catatan progres dan audit ini menjelaskan perubahan yang sudah masuk serta pekerjaan yang masih tersisa. Checklist M01–M03 tetap terbuka.

### Lanjutan implementasi setelah audit — 4 November 2026

- **M01:** source progresif kini memakai factory yang sama dengan HLS/DASH. Pilihan Off menonaktifkan trek teks pada Watch; saat masuk Shorts, seleksi teks dikembalikan agar pengaturan Watch tidak terbawa.
- **M02:** aksi Like/Subscribe Watch, Shorts, dan player unduhan membawa revisi yang ditangkap saat tindakan dimulai; hasil sesi lama diabaikan. Pengiriman live chat memakai snapshot header dan revisi. Pemanggilan HomeFeed untuk Beranda, langganan, History, kategori, dan continuation memakai jalur request akun dengan pemeriksaan revisi. Hasil langganan dan paginasi diperiksa sebelum diterapkan. Scope login opaque disimpan untuk memisahkan cache Beranda/related; cache resolve video, cache awal Shorts, dan state aksi yang disentuh dipisahkan menurut revisi sesi.
- **M03:** interceptor pemeriksaan tujuan dipasang pada transport Shorts, InnerTube, live chat, profil, dan media. Media memakai `media3-datasource-okhttp` versi 1.11.1 agar redirect melewati interceptor per hop. User-Agent media tetap ditentukan oleh client pada URL.
- **Verifikasi otomatis:** seluruh 82 unit test lulus, termasuk pengujian dua hop redirect HTTP lokal dengan header sintetis. Pengujian lokal ini memeriksa pembuangan header pada tujuan HTTP yang tidak diizinkan; belum membuktikan redirect HTTPS dari host YouTube yang diizinkan. Kompilasi dan build debug lulus. Tidak dilakukan instalasi atau pengujian perangkat/akun.

**Status tetap sebagian.** Audit lanjutan masih diperlukan pada seluruh pemanggil akun, termasuk operasi yang memulai coroutine sebelum menangkap sesi, serta cache/prefetch dan lifecycle yang belum dicakup. Subtitle trek manifest belum memiliki alur pemilihan/indikator lengkap; kegagalan subtitle terpisah dan batas DASH multi-period belum diverifikasi. Test integrasi Media3, redirect HTTPS lintas host, serta pergantian akun dengan respons tertunda masih belum tersedia. Build tidak membuktikan subtitle, playback, atau mutasi akun berhasil di perangkat.

**Uji perangkat yang diperlukan:** putar video progresif, HLS, DASH dan live; aktifkan subtitle, ganti bahasa, pilih Off, seek dan ubah kualitas; periksa audio manual, Auto HLS, Shorts dan player unduhan. Dengan akun uji, ganti akun saat feed atau tindakan sedang berjalan lalu periksa Beranda, Langganan, Shorts, Like/Subscribe dan player unduhan. Lakukan perubahan akun hanya bila pengguna siap dan jangan menganggap hasil HTTP sebagai bukti perubahan server.

### Laporan uji pengguna dan tindak lanjut CC — 4 November 2026

Pengguna melaporkan livestream, video biasa, Shorts, perubahan kualitas, dan hasil unduhan aman pada pengujian manual. Pergantian akun belum diuji. Pada perubahan CC, pengguna melihat buffering singkat dan progress bar sempat melompat ke awal sebelum kembali ke posisi tontonan. Laporan ini tidak menyatakan bahwa semua kombinasi format, jaringan, atau perangkat telah diuji.

Tindak lanjut source: Off dan pengaktifan kembali subtitle yang sama menggunakan seleksi trek tanpa mengganti sumber video. Saat subtitle pertama kali dipasang atau bahasa berubah, sumber masih harus diganti; posisi awal diberikan langsung saat memasang source, dan durasi VOD terakhir dipertahankan sementara ketika timeline belum tersedia saat buffering. Perubahan ini memerlukan uji ulang perangkat; buffering pertama kali atau saat berganti bahasa masih mungkin terjadi. Kriteria M01 tetap terbuka.

**Uji ulang pengguna setelah memasang APK perbaikan di Xiaomi:** CC aman saat dinyalakan dan dimatikan. Download video tetap aman setelah logout, saat aplikasi tanpa akun, dan setelah login ke akun lain. Pengguna menyebut livestream, video biasa, Shorts, dan perubahan kualitas juga aman pada pengujian sebelumnya. Pergantian akun sudah dicoba untuk alur download saja; feed, History, Like/Subscribe, serta request yang tertunda saat akun berganti belum dilaporkan teruji. Hasil ini memvalidasi skenario yang dicoba, tetapi belum menutup seluruh kriteria P1.

### Catatan progres P2 — 4 November 2026

P2 dimulai dari T04/M04. Perubahan yang masuk pada working tree:

- **M04 — sebagian:** status suka, simpan, dan subscribe kini dimiliki satu `EngagementRepository` yang dipisahkan menurut `YouTubeAuth.sessionRevision`, dengan status `null` berarti belum diketahui (bukan "Off"). Batas satu operasi per target, operation ID, dan pemeriksaan revisi mencegah hasil operasi lama menimpa operasi baru atau sesi baru.
- **M04 — status awal dari server:** `EngagementSource` membaca endpoint `next` yang sudah dipakai aplikasi dan memetakan `likeButtonRenderer`/`toggleButtonRenderer`, state entity, serta `subscribeButtonRenderer`. Bentuk yang tidak dikenali tetap `null`, bukan asumsi tidak menyukai/tidak berlangganan. Pemetaan awal dijalankan pada layar Watch dan player offline; Shorts yang dibuka tanpa pernah membuka Watch tetap memakai status tidak diketahui.
- **M04 — hasil mutasi:** hasil transport diklasifikasikan menjadi terkonfirmasi, ditolak, atau belum pasti. Penolakan dan ketidakpastian mengembalikan nilai sebelumnya dan menampilkan pesan; tidak ada replay otomatis. Setelah hasil belum pasti, server dibaca ulang otomatis agar status akhirnya mengikuti server. Perubahan jumlah suka hanya memakai delta dari status awal yang diketahui.
- **M04 — konsistensi layar:** Watch, Shorts, fullscreen, dan player offline membaca state yang sama. Tombol suka/subscribe dinonaktifkan selama operasinya menunggu. Dialog Save menulis status "tersimpan di playlist mana pun" ke state bersama setelah server membacanya.

**Verifikasi otomatis:** 96 unit test lulus, termasuk 14 test baru untuk repository (isolasi sesi, operasi ganda, penolakan/ketidakpastian, delta suka) dan parser status server (legacy, toggle view model, entity key, data tidak dikenal). `:app:lintDebug` dan `:app:assembleDebug` lulus dengan Gradle offline, Java 21, dan satu worker. Tidak dilakukan instalasi APK, pengujian akun, atau pengujian perangkat.

**Catatan status implementasi awal:** pemuatan status Short aktif, pembacaan ulang otomatis setelah `uncertain`, pelepasan pending, dan pemisahan playlist/Watch later semula belum lengkap. Keempatnya sudah ditindaklanjuti pada source setelah audit P2; hasil 103 test dan uji Xiaomi build 20:53 dicatat di bawah. Checklist M04 tetap terbuka untuk skenario khusus yang belum dilaporkan teruji.

### Tindak lanjut uji manual M04 — 4 November 2026

Uji manual pada perangkat Xiaomi menemukan tiga masalah. Perbaikannya:

- **Status like awal tidak terbaca (A/B):** subscribe berhasil dipetakan, tetapi like tidak. Parser kini juga membaca `entityKey` yang berada pada node mutasi `frameworkUpdates` (bukan hanya di dalam `toggleButtonMutation`) dan bentuk `buttonState` `TOGGLED`/`UNTOGGLED`. Saat Watch dibuka, hasil pemetaan dicatat sebagai kategori `known`/`unknown` pada log `FlufEngagement` tanpa nilai header atau token.
- **Paritas ketukan cepat salah (C):** ketukan selama request berjalan dulu dibuang atau hanya sebagian terkirim. Kini setiap ketukan langsung mengubah niat yang tampil, sementara hanya satu request berjalan; setelah request selesai, niat terakhir dikirim lewat `followUpRating`/`followUpSubscription`. Tombol tidak lagi dinonaktifkan selama menunggu.
- **Chip Save berkedip dan centang dialog terlambat (E):** status "tersimpan" kini diambil dari server saat Watch/player offline dibuka, sehingga chip tidak tampil nonaktif lebih dulu. Centang playlist berubah segera saat diketuk dan dikembalikan bila server menolak; dialog tidak lagi memuat ulang daftar setelah setiap klik, karena respons baca ulang yang belum konsisten membuat UI terasa lambat dan centang tampak tidak berubah.

**Verifikasi:** 103 unit test lulus setelah tindak lanjut audit P2 (termasuk refresh otoritatif, generation, pelepasan pending yang dibatalkan, dan pemisahan playlist/Watch later); `:app:lintDebug` dan `:app:assembleDebug` lulus. Uji perangkat pada Xiaomi: status awal like/subscribe, konsistensi Watch/fullscreen setelah restart, ketukan cepat, offline, pergantian akun, player offline, dialog Save, serta rekonsiliasi otomatis setelah hasil belum pasti dinyatakan lulus oleh pengguna pada build 20:53. Rincian ada di [Testing.md](Testing.md). Bukti khusus yang masih kurang: request sengaja ditahan lalu layar ditutup, respons diputus setelah mutasi diterima server, perubahan dari aplikasi YouTube, Shorts terhadap Watch untuk target yang sama, dan kombinasi membership beberapa playlist/Watch later.

### Hasil audit P2 — 4 November 2026

Perubahan P2 terbaru berfokus pada M04, dengan tambahan unit test terkait M10. **M04 tetap sebagian; M05–M09 belum mendapat penerapan paket lanjutan sesuai rencana.** Laporan rinci tersedia di [Audit P2 FlufPlay](AUDIT_P2_FLUFPLAY_2026-11-04.md).

Status empat temuan sesudah tindak lanjut:

- P2-A01: `abandonRating`/`abandonSubscription` dan `try/finally` sudah melepas pending ketika operasi dibatalkan; uji perangkat request sengaja ditahan lalu layar ditutup belum dilaporkan terpisah.
- P2-A02: `refresh()` dengan generation dan rekonsiliasi otomatis sudah dibuat; skenario `Subscribed?` hilang sendiri dan nilainya mengikuti server telah diuji pada build 20:53.
- P2-A03: Short aktif kini memuat status akun sendiri; konsistensi Shorts ↔ Watch untuk target sama masih memerlukan bukti perangkat yang spesifik.
- P2-A04: `playlistIds`/`watchLater` sudah dipisahkan dan dialog merekonsiliasi kegagalan; kombinasi multi-playlist dan Watch later masih memerlukan uji khusus.

Audit awal mencatat 99 unit test dan XML lint sebelum patch Save final. Tindak lanjut menggantikannya dengan 103 unit test serta lint/build sekitar 20:53; hasil itu dikonfirmasi dari laporan/artifact yang tersedia tanpa menjalankan ulang build/test pada pemeriksaan status ini. Catatan uji pengguna pada [Testing.md](Testing.md) berlaku untuk skenario yang dicoba. Checklist M04 tetap terbuka karena batas bukti khusus, sedangkan paket P2 berikutnya tetap mengikuti rencana.

### Catatan progres P3 — 4 November 2026

**M11 sedang berjalan, belum selesai.** Seek Watch menyediakan semantics rentang/posisi, `setProgress`, dan aksi mundur/maju sesuai interval seek yang dipilih. Seek Shorts menyediakan semantics rentang/posisi dan `setProgress`. Aksi seek live memakai batas DVR saat itu; pembekuan batas selama gesture jari tetap dipertahankan. Resource Inggris dan Indonesia ditambahkan untuk label seek dan sebagian teks notifikasi unduhan; filter build mencakup kedua bahasa. README diperbarui agar membedakan kemampuan yang diterapkan dan bukti perangkat yang tersedia.

**Verifikasi otomatis:** 103 unit test, lint debug, dan build debug lulus dengan Gradle offline, Java 21, dan satu worker. Build terakhir setelah koreksi qualifier resource lulus; `aapt2 dump resources` menunjukkan label seek dan notifikasi bahasa Indonesia ada dalam APK. Belum ada instalasi atau uji perangkat/TalkBack.

Pekerjaan yang masih terbuka: memindahkan seluruh teks UI, error, dialog dan notifikasi ke resource dan menerjemahkannya; label/state untuk semua kontrol termasuk engagement dan status unknown/pending; urutan serta pengembalian fokus; aksesibilitas dan area sentuh semua kontrol; format locale dan layout kedua bahasa/font besar. Seek, termasuk live DVR dan Shorts, masih memerlukan uji TalkBack dan keyboard pada perangkat. P2 belum seluruhnya selesai, sehingga perubahan P3 mengikuti kontrol yang tersedia sekarang dan perlu ditinjau lagi saat paket terkait berubah. Checklist M11 tetap terbuka.

Navigasi: [Pemetaan prioritas](#3-pemetaan-seluruh-temuan-ke-paket-perbaikan) · [Urutan kerja](#16-urutan-pengerjaan-yang-dapat-dijalankan) · [Matriks uji](#17-matriks-pengujian-penerimaan) · [Prosedur verifikasi](#18-prosedur-verifikasi-saat-implementasi-nanti) · [Checklist penyelesaian](#20-checklist-penyelesaian-perbaikan).

## 1. Kondisi dasar dan cara membaca dokumen

Proyek Android berada di `/home/iqbal/Documents/flufclip/flufplay` dan memakai satu modul `:app`. HEAD saat pemeriksaan adalah `ec47f85`; working tree memiliki perubahan lokal yang harus ikut diperhitungkan. Commit itu bukan representasi lengkap kode yang dianalisis.

| Bagian | Kondisi yang diperiksa |
| --- | --- |
| Bahasa dan UI | Kotlin, Jetpack Compose, Material 3 |
| Android | `minSdk 26`, `targetSdk 35`, `compileSdk 36` |
| Media | Media3 `1.11.1`, ExoPlayer, HLS, DASH, progresif |
| Ekstraksi | NewPipe Extractor `v0.26.3`, InnerTube internal, yt-dlp |
| Unduhan | yt-dlp/FFmpeg, foreground service, MediaStore pada Android 10 ke atas |
| Penyimpanan | SharedPreferences untuk History, preferensi, dan daftar unduhan; sejumlah cache hanya di memori |
| Inventaris source | 49 file Kotlin produksi, sekitar 15.642 baris |
| Test yang ditemukan | 18 file unit test dengan 79 anotasi `@Test`; tidak ditemukan source `androidTest` dalam checkout |
| Area besar | `MainActivity.kt` sekitar 4.187 baris; `LiveChat.kt` sekitar 1.810 baris |

Angka dan versi di tabel adalah kondisi source saat penyusunan, bukan anjuran memperbarui semua dependency. Dokumentasi Android resmi digunakan untuk rancangan API platform; kompatibilitas penerapannya tetap diperiksa terhadap versi dependency proyek ketika implementasi dimulai.

Istilah dalam dokumen:

- **P1:** didahulukan karena menyangkut fungsi yang tidak tersambung atau pemisahan sesi/autentikasi.
- **P2:** memperkuat keandalan dan melengkapi alur pemakaian utama.
- **P3:** perapian pengalaman, dokumentasi, dan pemeliharaan setelah alur utama stabil.
- **Kriteria selesai:** perilaku dan bukti yang harus tersedia, bukan sekadar file kode sudah dibuat.
- **Nama file baru:** usulan lokasi tanggung jawab; file tersebut belum ada kecuali disebut sebagai file yang sudah tersedia.
- **Investigasi kontrak server:** pekerjaan memahami respons dan tindakan yang benar-benar diberikan server, terutama untuk antarmuka internal YouTube.

## 2. Hasil akhir dan batas perilaku yang dipertahankan

Hasil yang dituju adalah player dengan subtitle dan recovery yang konsisten, data akun yang mengikuti sesi aktif, pekerjaan unduh yang dapat dipulihkan, serta tindakan UI yang sesuai kemampuan sebenarnya.

Ketentuan implementasi lintas tahap:

1. Pertahankan perubahan lokal pengguna. Buat patch dan commit hanya untuk pekerjaan yang sedang dikerjakan; jangan membersihkan checkout secara menyeluruh.
2. Pertahankan pilihan kualitas/audio manual, perilaku Shorts, miniplayer, fullscreen, PiP, dan player offline ketika membenahi jalur lain.
3. Pada Auto, pertahankan HLS ketika tersedia dan DASH tidak tersedia. Trek audio tanpa label bahasa adalah informasi belum diketahui, bukan bukti bahasa tidak cocok.
4. Pisahkan History akun, History perangkat, dan pelaporan playback. Perubahan pada salah satunya tidak boleh diam-diam mengganti sumber data yang lain.
5. Pertahankan pelaporan posisi akhir tontonan saat menutup/mengganti video. Jangan mengganti pembuktian History akun dengan status HTTP atau kemunculan progress bar.
6. Pertahankan URI unduhan yang sudah tersimpan, file lama, serta metadata lama yang masih dapat dibaca. Migrasi harus menambah kemampuan tanpa menghapus data tersebut.
7. Operasi akun menggunakan sesi yang ditangkap ketika pengguna memulai tindakan. Permintaan yang hasilnya tidak pasti tidak diulang otomatis sebagai mutasi baru.
8. Cookie, header autentikasi, token continuation akun, dan URL media bertanda tangan tidak dimasukkan ke log, fixture publik, atau snapshot sesi player.
9. Bedakan status fitur: tersedia, sedang dimuat, belum diketahui, gagal, dan tidak didukung. Tampilan tidak boleh menganggap semuanya sebagai keadaan kosong atau Off.
10. Perubahan tampilan mengikuti kebutuhan alur yang dibenahi. Penyesuaian ikon, font, dimensi, atau efek visual yang tidak terkait dicatat sebagai pekerjaan terpisah.

## 3. Pemetaan seluruh temuan ke paket perbaikan

| ID | Temuan dari analisis | Prioritas | Hasil yang dituju | Ketergantungan utama |
| --- | --- | --- | --- | --- |
| M01 | Subtitle eksternal HLS/DASH | P1 | Satu jalur pembentukan sumber media yang memasang subtitle dengan benar | Dasar test media |
| M02 | Request/state tidak konsisten terhadap sesi akun | P1 | Setiap operasi dan cache akun memiliki pemilik sesi | M03 untuk transport autentikasi |
| M03 | Pencocokan URL tujuan header terlalu longgar | P1 | Header akun hanya menuju host dan koneksi yang diizinkan | Dasar test jaringan |
| M04 | Status suka, simpan, subscribe tidak mencerminkan akun | P2 | State bersama berdasarkan informasi server dan hasil mutasi | M02, M03 |
| M05 | Recovery player belum merata | P2 | Kebijakan retry berdasarkan jenis error dan niat pemutaran | M01, M02, M03 |
| M06 | Konteks player tidak pulih setelah proses mati | P2 | Snapshot sesi yang dapat dipulihkan tanpa memakai URL stream lama | M05, ekstraksi coordinator bertahap |
| M07 | Antrean, resume, audio, dan pengelolaan unduhan belum lengkap | P2 | Pekerjaan persisten dengan kepemilikan output yang jelas | M02, M03, penyimpanan pekerjaan |
| M08 | Komentar biasa dan playlist masih sebagian | P2 | Tindakan yang benar-benar terhubung dan status kemampuan yang jelas | M02, M03, M04; unduh playlist memerlukan M07 |
| M09 | Background audio, link masuk, Cast | P2 | Integrasi pemutaran yang sesuai cakupan masing-masing | M05, M06; bagian Cast memerlukan bukti receiver |
| M10 | Test integrasi dan pembagian tanggung jawab | P2 | Test terhadap batas berisiko dan struktur kode yang lebih terpisah | Dikerjakan bersama paket lain |
| M11 | Aksesibilitas seek, bahasa, README | P3 | Kontrol dapat dioperasikan, bahasa konsisten, dokumentasi terkini | Mengikuti kontrol dan alur yang disentuh |

## 4. Bentuk arsitektur yang dituju

Gunakan modul `:app` yang ada. Pisahkan tanggung jawab melalui class/file terlebih dahulu; pemecahan menjadi banyak modul Gradle belum diperlukan oleh temuan ini.

| Komponen yang diusulkan | Tanggung jawab | Pengguna utamanya |
| --- | --- | --- |
| `AccountSessionStore` | Sesi aktif, pergantian sesi, identitas scope akun/persona | Semua repository akun |
| `AccountRequestExecutor` | Snapshot autentikasi, validasi sesi, pengiriman, klasifikasi hasil | Feed, History, Library, rating, subscription, komentar |
| `AuthHeaderPolicy` | Kebijakan header berdasarkan URL tujuan dan peran endpoint | Transport ekstraksi dan media |
| `EngagementRepository` | Status rating, membership playlist, subscription, operasi yang sedang berjalan | Watch, Shorts, metadata online pada player offline |
| `PlaybackCoordinator` | Permintaan play, pilihan source, recovery, antrean, status sesi | UI watch/miniplayer/Shorts; kelak layanan playback |
| `PlaybackSourceBuilder` | MediaItem, factory, subtitle, audio terpisah, metadata source | Coordinator |
| `PlaybackSessionStore` | Snapshot versi, posisi, antrean, preferensi per sesi | Coordinator dan pemulihan aplikasi |
| `DownloadJobStore` | Penyimpanan transaksional tugas unduh | Scheduler, runner, Downloads UI |
| `DownloadRunner` | yt-dlp, FFmpeg, copy, verifikasi, publikasi hasil | Service/job adapter |
| `IncomingLinkParser` | Validasi dan normalisasi permintaan dari intent | Activity dan navigasi |

Nama di atas menyatakan tanggung jawab yang berbeda. Gabungkan dua komponen kecil bila pemisahannya tidak memberi manfaat yang nyata; hindari lapisan pembungkus yang hanya meneruskan fungsi.

Pemilik ExoPlayer dipindahkan secara bertahap. Pada perbaikan awal, hindari mengganti seluruh lifecycle player. Setelah coordinator dan snapshot stabil, paket background playback memindahkan kepemilikan player ke layanan dan membuat UI menjadi pengendali. Pada setiap tahap, hanya satu sumber yang boleh menghasilkan audio aktif.

### Peta file untuk pelaksanaan

Nama Kotlin pada tabel berikut berada di `flufplay/app/src/main/java/id/flufclip/player/`. File baru adalah usulan; seluruhnya dibuat hanya saat paket tersebut diimplementasikan.

| Paket | File yang perlu disentuh atau diaudit | Tambahan utama yang diusulkan |
| --- | --- | --- |
| M01 | `MainActivity.kt`, `PlaybackNet.kt`, `PlayerControls.kt` | `PlaybackSourceBuilder.kt`, test konstruksi/integrasi subtitle |
| M02 | `YouTubeAuth.kt`, `MainActivity.kt`, `HomeFeed.kt`, `AccountHistory.kt`, `AccountLibrary.kt`, `ShortsFeed.kt`, `ShortsResolver.kt`, `VideoRepository.kt`, `AskPanel.kt`, `DownloadedWatch.kt` | `AccountSessionStore.kt`, `AccountRequestExecutor.kt`, test pergantian sesi |
| M03 | `VideoRepository.kt`, `PlaybackNet.kt`, transport `HomeFeed.kt`/`ShortsFeed.kt`/`InnerTubePlayer.kt`, validasi tracking | `AuthHeaderPolicy.kt`, test header dan redirect |
| M04 | `MainActivity.kt`, `ShortsScreen.kt`, `DownloadedWatch.kt`, `AccountLibraryUi.kt`, parser metadata akun | `EngagementRepository.kt`, model hasil mutasi, fixture status akun |
| M05 | `PlaybackRecovery.kt`, `MainActivity.kt`, `ShortsResolver.kt`, `AudioTrackSelection.kt`, `PlaybackNet.kt` | Model error/recovery serta test anggaran dan generation |
| M06 | `MainActivity.kt`, `DownloadsActivity.kt`, `OfflineMiniplayer.kt`, integrasi checkpoint History | `PlaybackSessionStore.kt`, model snapshot/migrasi |
| M07 | `DownloadService.kt`, `DownloadDestination.kt`, `DownloadScreens.kt`, `SavedDownloads.kt`, `YtDlpFormats.kt`, `AudioTrackSelection.kt`, `DownloadsActivity.kt`, manifest | `DownloadJobStore.kt`, `DownloadRunner.kt`, scheduler dan adapter UIDT |
| M08 | `WatchPanels.kt`, `VideoRepository.kt`, `AccountLibrary.kt`, `AccountLibraryUi.kt`, `AccountPlaylistScreens.kt` | Repository/adapter komentar akun, model kemampuan, batch playlist |
| M09 | `MainActivity.kt`, `DownloadsActivity.kt`, `OfflineMiniplayer.kt`, `PlaybackTracker.kt`, `app/build.gradle.kts`, manifest | `IncomingLinkParser.kt`, `PlaybackService.kt`, adapter controller/Cast sesuai hasil kelayakan |
| M10 | File yang diekstrak, `app/src/test`, `app/src/androidTest`, konfigurasi dependency test | Fixture sintetis, harness Media3/storage, workflow CI |
| M11 | `PlayerControls.kt`, `ShortsScreen.kt`, `WatchPanels.kt`, `LibraryTab.kt`, screen/dialog terkait, resource, README | Resource string/plural dan test semantics/locale |

Setiap patch hanya mengubah file yang diperlukan oleh subpekerjaan tersebut. Tabel ini bukan instruksi mengubah semua file sekaligus.

## 5. M01 — Sambungkan subtitle eksternal ke seluruh jalur media

### Masalah dan lokasi saat ini

`playQuality()` memasang subtitle pada helper `media()`, tetapi cabang HLS/DASH membuat source lewat helper lain. Bukti ada pada [MainActivity.kt:777](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:777) dan [PlaybackNet.kt:65](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:65).

Memasukkan `SubtitleConfiguration` ke MediaItem saja belum cukup jika source dibuat lewat jalur yang tidak merangkai subtitle eksternal. Dokumentasi Media3 menjelaskan peran `DefaultMediaSourceFactory` dalam penggabungan subtitle dan mencatat batas sideload subtitle untuk DASH multi-period. Rancangan perlu mengikuti factory yang benar-benar digunakan, bukan hanya state pilihan CC. [Media3: media items](https://developer.android.com/media/media3/exoplayer/media-items).

### Perubahan yang direncanakan

1. Ekstrak konstruksi source dari `playQuality()` ke `PlaybackSourceBuilder.kt`.
2. Buat satu MediaItem utama yang membawa `mediaId`, URI, MIME manifest bila diperlukan, konfigurasi live yang relevan, serta daftar subtitle eksternal.
3. Gunakan `DefaultMediaSourceFactory` yang dikonfigurasi dengan DataSource/header dan load-error policy proyek. Pastikan jalur HLS/DASH juga melewati mekanisme perangkaian subtitle factory ini.
4. Tetapkan MIME HLS/DASH secara eksplisit ketika URL manifest tidak dapat dikenali dengan aman dari ekstensi.
5. Untuk video-only + audio terpisah, rangkai source video yang sudah memuat subtitle dengan source audio. Hindari memasang subtitle dua kali atau menggabungkan audio yang sudah tertanam.
6. Pasang konfigurasi live hanya untuk media yang benar-benar live. Ganti argumen `isLive = true` yang digunakan tanpa melihat jenis konten pada cabang manifest HLS.
7. Pisahkan pilihan subtitle eksternal dari trek teks bawaan manifest. Off menonaktifkan trek teks yang aktif sesuai kebijakan pilihan pengguna.
8. Kembalikan posisi, `playWhenReady`, kualitas, speed, dan pilihan audio setelah pergantian subtitle yang memerlukan penggantian source.
9. Jangan tampilkan indikator subtitle aktif hanya karena variabel pilihan berubah. Gunakan pilihan yang diterapkan dan keadaan trek/error untuk menggambarkan hasilnya.
10. Untuk manifest yang belum didukung, pertahankan playback dan tampilkan ketidaktersediaan subtitle secara jelas; jangan diam-diam mengganti source ke progresif hanya agar CC terlihat aktif.

### Uji yang diperlukan

- Unit test: pemetaan VTT/TTML/SRT, MediaItem membawa bahasa/MIME, konfigurasi live hanya untuk live, dan konstruksi source tidak menggandakan trek.
- Integrasi Media3 lokal: video progresif, HLS, dan DASH single-period dengan teks subtitle yang dapat dikenali pada waktu tertentu; uji DASH multi-period untuk perilaku batas dukungan.
- Perangkat: aktifkan subtitle, ganti bahasa, pilih Off, seek, ganti kualitas, minimalkan, lalu fullscreen. Amati teks dan sinkronisasinya.
- Regresi: audio manual, Auto HLS, live tanpa subtitle, dan video tanpa subtitle tetap berfungsi.

### Kriteria selesai dan rollback

- [ ] Subtitle eksternal benar-benar tampil pada progresif, HLS, dan DASH yang didukung.
- [ ] Off menghilangkan teks dan tidak mengubah posisi/audio.
- [ ] Kegagalan subtitle tidak mematikan pemutaran video yang masih valid.
- [ ] Batas DASH multi-period dicatat dan diuji, tanpa klaim dukungan yang tidak terbukti.

Rollback dibatasi pada builder dan pilihan subtitle yang baru. Bila diperlukan, nonaktifkan kemampuan subtitle pada jalur bermasalah sambil mempertahankan source playback yang stabil; jangan membalik perubahan pilihan audio lain.

## 6. M02 — Ikat request, state, dan cache ke sesi akun

### Masalah dan lokasi saat ini

`sessionRevision` sudah dipakai di beberapa alur, tetapi Beranda utama bergantung pada boolean login. `loadHome()` dapat menulis hasil tanpa pemeriksaan revisi sesi. Aksi rating/subscription mengambil autentikasi global ketika dijalankan. Lihat [MainActivity.kt:802](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:802), [MainActivity.kt:883](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:883), dan [ShortsFeed.kt:102](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:102).

### Kontrak sesi yang direncanakan

| Data | Tujuan | Persistensi |
| --- | --- | --- |
| `revision` | Membatalkan/mengabaikan pekerjaan lama dalam proses yang sedang berjalan | Memori |
| `scopeId` | Memisahkan data akun yang disimpan dari sesi akun lain | ID opaque yang disimpan bersama sesi login |
| `personaId` bila tersedia | Membedakan channel/persona dalam akun | Hanya setelah identitas terverifikasi |
| Snapshot header | Mencegah operasi lama mengambil kredensial sesi baru | Hanya memori selama request |
| `requestId`/generation | Membedakan permintaan baru untuk layar dan media yang sama | Memori |

Jangan memakai nama tampilan/email sebagai kunci akun. Jangan memakai `revision` saja untuk cache disk, karena nilainya dapat kembali ke keadaan awal setelah proses dibuat ulang. Jika identitas persona belum dapat dibuktikan, buat scope login baru dan hindari berbagi cache dengan login sebelumnya.

### Perubahan yang direncanakan

1. Tambahkan snapshot sesi yang konsisten pada `YouTubeAuth`/`AccountSessionStore`: revision, scope, status, dan autentikasi untuk request tersebut.
2. Saat login baru/logout/perubahan persona, naikkan revision, ganti scope bila diperlukan, batalkan pekerjaan akun, dan kosongkan state milik scope sebelumnya.
3. Ubah key efek Beranda, Subscriptions, Shorts akun, Ask, Library, dan engagement menjadi identitas sesi yang relevan, bukan hanya `isLoggedIn`.
4. Tangkap sesi sebelum penemuan versi client dan sebelum menunggu operasi jaringan lain. Validasi lagi sebelum kirim serta sebelum hasil diterapkan.
5. Ekstrak pola `HomeFeed.accountRequest()` menjadi jalur umum yang dapat menerima expected session. Audit pemanggil agar request yang baru dimulai sesudah pergantian akun tidak mewarisi tujuan tindakan lama.
6. Snapshot header tidak boleh dibangun ulang dari global auth setelah operasi berpindah thread atau menunggu respons lain.
7. Batalkan Call jaringan ketika coroutine dibatalkan jika transport mendukungnya. Tetap periksa revision setelah respons, karena pembatalan bukan jaminan server tidak menerima request.
8. Tambahkan generation untuk feed/paginasi/filter. Respons halaman lama tidak boleh masuk ke kategori, continuation, atau sesi yang baru.
9. Partisi cache Beranda/related yang personal dengan scope dan versi schema; tandai continuation dengan scope dan sumber query-nya.
10. Audit `resolveCache`, `resolveJobs`, prefetch Shorts, dan metadata membership. Cache yang dapat bergantung pada hak akun harus membawa scope atau diinvalidasi saat sesi berubah. Cache avatar publik dapat tetap digunakan bersama bila memang tidak memuat data personal.
11. Terapkan pola yang sama pada tindakan dari `DownloadedWatch.kt`: metadata dan aksi akunnya tetap mengikuti sesi aktif, sedangkan file lokal tetap dapat diputar.
12. Pertahankan History perangkat sebagai data perangkat; jangan menghapusnya saat logout akun.

Request mutasi yang sudah dikirim sebelum logout tidak dapat ditarik kembali secara pasti. Jaminan aplikasi adalah tidak mengganti kredensialnya menjadi akun baru, tidak mengirim ulang otomatis sebagai tindakan baru, dan tidak memasukkan hasilnya ke state sesi berikutnya.

### Uji yang diperlukan

- Tahan respons feed A, logout, login B, lalu lepaskan respons A: UI/cache B tetap bersih dari A.
- Ganti sesi ketika versi client sedang dimuat: mutasi lama tidak dikirim memakai kredensial B.
- Kirim mutasi A, ganti sesi sebelum respons: hasil A tidak mengubah state B.
- Ulangi skenario pada continuation History/Library, feed kategori, prefetch, dan tindakan player offline.
- Buka ulang proses dengan nomor revision yang sama secara kebetulan: cache scope lain tetap tidak terbaca.

### Kriteria selesai dan rollback

- [ ] Tidak ada request akun yang memakai kredensial global yang berubah setelah snapshot operasinya dibuat.
- [ ] Respons sesi/generation lama ditolak sebelum menulis UI maupun cache.
- [ ] Seluruh state akun ter-reset/terpartisi secara konsisten.
- [ ] Data perangkat dan file unduhan tetap tersedia setelah logout.

Rollback dilakukan per pemanggil repository. Bila jalur akun tertentu belum lolos, nonaktifkan tindakan itu sementara dengan status yang jelas; jangan mengembalikan request tanpa pembatasan sesi.

## 7. M03 — Perketat tujuan header autentikasi

### Masalah dan lokasi saat ini

Downloader NewPipe memeriksa substring di seluruh URL sebelum menambahkan header akun. Lihat [VideoRepository.kt:70](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt:70). Kebijakan host pada [PlaybackNet.kt:22](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:22) dapat menjadi dasar penyamaan, dengan audit tambahan atas semua header sensitif.

### Kebijakan tujuan

| Tujuan | Kebijakan awal |
| --- | --- |
| HTTPS `youtube.com` dan subdomain yang memang digunakan aplikasi | Izinkan header akun sesuai peran endpoint dan snapshot sesi |
| `youtubei.googleapis.com` | Buat aturan eksplisit hanya bila request yang dibutuhkan membuktikan jenis header yang benar; jangan mewarisi seluruh header secara otomatis |
| `googlevideo.com`/subdomain CDN media | Pertahankan User-Agent/header media yang diperlukan; jangan kirim cookie akun dan Authorization login |
| Host lain, port tidak diizinkan, userinfo pada URL, skema selain HTTPS | Jangan tambahkan kredensial akun |
| Redirect | Hitung ulang kebijakan untuk tujuan setiap hop; jangan meneruskan kredensial hanya karena URL awal diizinkan |

### Perubahan yang direncanakan

1. Parse URL dengan parser URL yang digunakan transport, kemudian cocokkan host secara utuh atau akhiran domain dengan batas titik.
2. Pisahkan peran request publik, request akun, dan request media. Buat kebijakan header untuk masing-masing peran.
3. Sanitasi header sensitif yang sudah ada pada request asal, termasuk variasi huruf besar/kecil dan duplikasi Cookie.
4. Terapkan ulang policy pada redirect lintas host dan penurunan HTTPS ke HTTP. Gunakan pemeriksaan per network hop atau hentikan redirect otomatis pada jalur akun lalu validasi tujuan berikutnya.
5. Pertahankan pemilihan User-Agent per client media yang sudah ada; perketatan akun tidak boleh mengubah signature context playback secara tidak sengaja.
6. Catat hanya kategori tujuan, kode error, dan ID operasi lokal. Jangan menulis nilai header atau URL bertanda tangan.

### Uji yang diperlukan

Gunakan server uji lokal dan kredensial sintetis. Kasus negatif wajib mencakup domain lain yang memuat `youtube.com` pada query/path, `notyoutube.com`, `youtube.com.example.invalid`, URL dengan userinfo, HTTP, port tak sesuai, header Cookie bawaan, dan redirect dari host yang diizinkan ke host lain. Kasus positif memeriksa request YouTube yang diperlukan serta media CDN tanpa kredensial akun.

### Kriteria selesai dan rollback

- [ ] Semua jalur transport akun memakai policy yang sama atau policy khusus yang terdokumentasi.
- [ ] Semua kasus negatif tidak membawa header sensitif.
- [ ] Ekstraksi anonim, login, membership yang sah, dan pemutaran media tetap dapat diverifikasi.
- [ ] Redirect tidak dapat melewati pembatasan awal.

Jika ada endpoint sah yang terblokir, tambahkan aturan sempit berdasarkan bukti request endpoint itu. Jangan mengembalikan pencocokan substring seluruh URL.

## 8. M04 — Satukan status suka, simpan, dan subscribe

### Masalah dan lokasi saat ini

State awal rating/subscription berupa map kosong pada [MainActivity.kt:524](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:524). Shorts memiliki state rating tersendiri di [ShortsScreen.kt:170](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt:170). Dialog Save baru membaca pilihan server saat dibuka.

### Model dan perubahan yang direncanakan

1. Buat `EngagementRepository` dengan kunci sesi + video untuk rating/membership, dan sesi + channel untuk subscription.
2. Bedakan `Unknown`, `Loading`, nilai server yang diketahui, `Updating`, dan `Error/Uncertain`. Boolean default `false` tidak mewakili status akun yang belum dibaca.
3. Temukan status dan command dari respons akun yang benar-benar tersedia. Perluas parser metadata player/next/Shorts sesuai fixture; jangan menyimpulkan status suka dari `likeCount` atau keberadaan video pada feed.
4. Bagikan state yang sama ke Watch, Shorts, fullscreen, dan tindakan online pada player offline.
5. Pisahkan Saved-to-any-playlist, membership Watch later, dan pilihan playlist tertentu. Gunakan identitas item playlist yang benar untuk operasi remove.
6. Untuk status awal yang belum diketahui, tampilkan keadaan memuat/tidak tersedia. Jika tindakan eksplisit masih memungkinkan, kirim tujuan jelas seperti Like atau Subscribe; jangan mengirim toggle berdasarkan asumsi Off.
7. Izinkan satu operasi berjalan per target; nonaktifkan tombol yang sama selama pending. Terapkan hasil hanya bila scope dan operation ID masih sesuai.
8. Klasifikasikan hasil mutasi sebagai confirmed, rejected, atau uncertain. Respons HTTP sukses saja tidak selalu menjadi bukti perubahan; parser harus membaca konfirmasi yang tersedia dan melakukan read-back bila perlu.
9. Pada timeout setelah request mungkin terkirim, baca ulang state. Jangan otomatis mengirim mutasi non-idempoten sekali lagi.
10. Perbarui jumlah suka berdasarkan perubahan dari status awal yang diketahui. Hindari menambah satu bila akun sebenarnya sudah menyukai video.
11. Invalidate/refetch state setelah perubahan dari dialog playlist, subscription, atau kembalinya aplikasi dengan data yang patut diperbarui.

### Uji, kriteria selesai, dan rollback

Uji fixture status sudah suka/sudah subscribe/tidak diketahui, beberapa playlist, HTTP 200 dengan error aplikasi, timeout sesudah server menerima tindakan, ketukan cepat, pergantian layar, dan pergantian akun.

- [ ] Status awal sesuai server ketika datanya tersedia.
- [ ] Watch/Shorts/fullscreen/player offline menampilkan nilai yang konsisten untuk target yang sama.
- [ ] Tidak ada hasil request lama yang menimpa operasi baru.
- [ ] Kegagalan/ketidakpastian tidak ditampilkan sebagai sukses yang sudah dikonfirmasi.

Rollback memakai pembatasan kemampuan mutasi dan pembacaan state yang sudah teruji. Jangan kembali menjadikan state tidak diketahui sebagai Off yang dianggap pasti.

## 9. M05 — Satukan kebijakan pemulihan player

### Masalah dan lokasi saat ini

403 otomatis memicu resolve ulang untuk live, sedangkan VOD biasa terutama menghapus cache. Retry memiliki jeda maksimum tetapi belum memiliki batas total percobaan. Lihat [MainActivity.kt:1761](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1761) dan [PlaybackRecovery.kt:20](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackRecovery.kt:20).

### Kebijakan error yang direncanakan

| Keadaan | Tindakan otomatis | Kondisi berhenti/tindakan pengguna |
| --- | --- | --- |
| Timeout/putus jaringan sementara | Tunggu jaringan atau retry dalam anggaran | Tampilkan status menunggu; Retry tersedia setelah anggaran habis |
| 403 pada URL media | Evict source, resolve ulang, pertahankan posisi | Jika hasil baru menyatakan akses/login tidak tersedia, hentikan retry |
| 401/403 pada endpoint akun | Tandai kebutuhan autentikasi sesuai respons | Minta login melalui UI; tidak mengulang tanpa akhir |
| 429 | Hormati `Retry-After` yang valid dalam batas kebijakan | Tampilkan waktu tunggu/tindakan coba lagi |
| Behind live window | Kembali ke posisi default/live edge yang valid | Batasi frekuensi; jangan gunakan posisi DVR absolut yang sudah keluar window |
| Privat/dihapus/geo-restricted/tidak memiliki akses | Tampilkan alasan dari resolver yang sudah diklasifikasikan | Tidak melakukan loop resolve otomatis |
| Decoder/format tidak didukung | Gunakan fallback format hanya jika kompatibel dengan pilihan pengguna | Jika tidak ada, tampilkan error; jangan menyamarkan sebagai error jaringan |
| Subtitle eksternal gagal | Nonaktifkan/ulang pemuatan subtitle secara terpisah | Pertahankan video/audio yang masih valid |
| URI offline hilang/tidak dapat dibaca | Cek izin dan keberadaan sumber lokal | Jangan mengganti playback lokal menjadi streaming diam-diam |

### Perubahan yang direncanakan

1. Buat klasifikasi error dan `PlaybackRecoveryPolicy` yang digunakan VOD, live, dan Shorts, dengan tindakan khusus untuk masing-masing jenis media.
2. Gunakan `playbackRequestId`/generation, bukan hanya video ID, agar hasil sesi pemutaran lama untuk video yang sama tidak diterapkan ke sesi baru.
3. Ambil snapshot posisi dan preferensi saat recovery dimulai. Selama retry, perubahan pilihan manual pengguna membatalkan atau memperbarui target recovery secara eksplisit.
4. Terapkan batas awal: maksimal 3 percobaan otomatis atau 60 detik sejak recovery dimulai, mana yang lebih dahulu. Waktu menunggu resolver/readiness dihitung ke batas total. Nilai ini adalah keputusan awal produk untuk diuji, bukan kemampuan bawaan library.
5. Gunakan jeda awal 1 dan 2 detik untuk percobaan berikutnya, dibatasi sisa anggaran. Jangan menambah loop di atas loop resolver tanpa menghitung totalnya.
6. Keadaan offline menunggu callback jaringan dan niat play terbaru; tidak memutar ulang polling jaringan cepat.
7. Setelah source diganti, tunggu `STATE_READY` dan periksa error/media generation sebelum menyatakan recovery sukses.
8. Pertahankan play/pause, speed, subtitle, pilihan kualitas, serta audio manual. Recovery tidak boleh menjalankan play bila pengguna sudah menekan Pause.
9. Pertahankan aturan Auto HLS yang telah ada; unknown language tidak memaksa progresif. Gunakan helper pilihan audio yang tersedia.
10. Pertahankan posisi VOD dalam rentang durasi baru. Untuk live, gunakan live offset/window yang masih valid, lalu fallback ke live edge bila dibutuhkan.
11. Catat attempt, kategori error, durasi recovery, dan outcome tanpa URL/kredensial. Tampilkan pesan pengguna yang singkat dengan tindakan yang benar-benar tersedia.

### Uji, kriteria selesai, dan rollback

Gunakan resolver palsu dengan clock yang dapat dikendalikan serta player/integrasi yang menghasilkan error terarah. Uji perubahan video, video yang sama dibuka ulang, Pause saat recovery, kualitas/audio berubah, internet mati/menyala, 403 berulang, error permanen, dan penutupan player.

- [ ] VOD 403 dapat pulih otomatis dalam anggaran bila source baru valid.
- [ ] Error permanen tidak mengulang tanpa akhir.
- [ ] Recovery berhenti saat pengguna menutup/mengganti sesi.
- [ ] Posisi dan preferensi yang masih berlaku tetap dipertahankan.
- [ ] HLS Auto, live DVR, serta recovery Shorts yang sudah bekerja tidak mengalami regresi.

Rollback per jenis media memungkinkan menonaktifkan recovery otomatis yang baru sambil mempertahankan tombol retry dan kebijakan error yang jelas.

## 10. M06 — Pulihkan konteks player setelah proses mati

### Data snapshot yang direncanakan

Gunakan `PlaybackSessionStore` berupa JSON berversi dengan penulisan atomik di penyimpanan privat aplikasi. `rememberSaveable`/saved state tetap digunakan untuk state UI kecil; snapshot disk menangani pemulihan konteks yang diperlukan setelah proses dibuat ulang. Jangan menyimpan objek ExoPlayer, StreamInfo utuh, atau URL media hasil resolve.

| Field | Isi dan aturan |
| --- | --- |
| `schemaVersion`, `updatedAt` | Mendukung migrasi dan penilaian snapshot lama |
| `sourceKind` | Online video/Shorts atau file offline |
| `canonicalUrl` / `localUri` | Identitas sumber asli; URI lokal harus dicek izin/bacanya |
| `positionMs`, `durationMs` | Posisi VOD; diverifikasi ulang terhadap source saat restore |
| `wasLive`, `liveOffsetMs` opsional | Preferensi kembali ke live/window yang masih valid; bukan posisi DVR lama yang dianggap abadi |
| `queue`, `currentIndex` | ID/URL kanonis dan metadata minimum; batas awal 100 item untuk snapshot |
| `audioPreference`, `qualityMode`, `maxHeight`, `speed` | Preferensi semantik, bukan track-group index dari manifest lama |
| `subtitleLanguage`, `subtitleEnabled` | Diselesaikan ulang pada daftar subtitle baru |
| `minimized`, `navigationContext` | Konteks UI yang aman dipulihkan |
| `accountScopeId` | Membatasi pemulihan konten/konteks akun ke scope yang sesuai |

### Perubahan yang direncanakan

1. Ekstrak snapshot/state session dari `MainActivity.kt`; gunakan satu jalur simpan untuk perpindahan video, seek selesai, perubahan antrean, dan perubahan preferensi.
2. Simpan posisi berkala dengan throttle, misalnya 5 detik seperti alur yang sudah ada. Jalankan serialisasi dan I/O di luar main thread.
3. Simpan best-effort ketika lifecycle berhenti; jangan menggantungkan pemulihan hanya pada `onDestroy()`.
4. Intent pembukaan eksplisit dari pengguna mengalahkan snapshot lama. Membuka tautan baru tidak boleh dipotong restore video sebelumnya.
5. Saat restore online, resolve ulang URL kanonis dan pilih ulang trek. Saat restore offline, cek SavedDownloads/URI tanpa memerlukan internet.
6. Jika scope akun berubah, jangan memulihkan konteks privat atau mengirim tracking akun lama. History perangkat tetap dapat digunakan sebagai data lokal.
7. Setelah proses mati, pulihkan konteks dalam keadaan Pause sampai pengguna melanjutkan. Keputusan ini menghindari audio tiba-tiba; background playback yang masih hidup adalah skenario berbeda pada M09.
8. Jika snapshot rusak/versi tidak dikenali, abaikan snapshot itu dan tampilkan aplikasi yang tetap dapat dipakai. Jangan menghapus History atau daftar unduhan.
9. Bedakan menutup player secara eksplisit dari proses yang dihentikan sistem. Close membersihkan snapshot sesi aktif; force-stop tidak menjanjikan callback penyimpanan terakhir.
10. Pertahankan jalur `PlaybackTracker.stop()` dan final position saat pengguna benar-benar mengakhiri sesi; restore tidak mengirim tontonan historis ulang.

### Uji, kriteria selesai, dan rollback

Uji rekreasi Activity, proses mati saat aplikasi berada di background, pembukaan ulang sesudah force-stop, snapshot rusak, URI hilang, URL media lama yang sudah kedaluwarsa, sesi akun berubah, dan incoming link baru. `ActivityScenario.recreate()` saja tidak membuktikan pemulihan proses mati.

- [ ] Video/antrean/konteks terakhir dapat dipulihkan tanpa memakai URL stream tersimpan.
- [ ] Posisi yang pulih berada dalam toleransi checkpoint 5 detik pada skenario penghentian normal yang diuji.
- [ ] Proses baru tidak menyalakan audio otomatis.
- [ ] File offline dapat dipulihkan tanpa jaringan.
- [ ] Snapshot gagal tidak merusak data yang sudah ada.

Rollback menonaktifkan pembacaan snapshot baru dan mempertahankan data berversi untuk diperiksa. Jangan menghapus file snapshot/data lain secara massal.

## 11. M07 — Jadikan unduhan pekerjaan persisten yang dapat dipulihkan

### 11.1. Dasar masalah dan keputusan implementasi

`DownloadStatus` sekarang berada di memori dan service menjalankan satu unduhan tanpa antrean persisten. Proses copy/verifikasi sudah tersedia dan harus dipertahankan. Lihat [DownloadService.kt:25](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:25), [DownloadDestination.kt:14](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadDestination.kt:14), dan [SavedDownloads.kt:25](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/SavedDownloads.kt:25).

Keputusan awal: gunakan database pekerjaan lokal lewat `SQLiteOpenHelper` agar perubahan tidak memerlukan penambahan Room/KSP sekaligus. Akses database berada di belakang `DownloadJobStore`; seluruh transaksi/I/O berjalan di dispatcher yang sesuai. Daftar hasil unduhan lama tetap dibaca melalui `SavedDownloads` pada tahap awal.

Mulai dengan **satu runner aktif** untuk transfer dan pemrosesan. Banyak video dapat masuk antrean, tetapi belum diunduh paralel. Ini menjaga penggunaan CPU, storage sementara, dan FFmpeg tetap terkendali.

### 11.2. Data pekerjaan yang harus disimpan

| Data | Aturan |
| --- | --- |
| `jobId`, `batchId`, `queueOrder` | Identitas tetap; batch untuk playlist atau beberapa video |
| `sourceId`, `canonicalUrl`, metadata video | Sumber YouTube asli; bukan URL CDN sementara |
| `requestedHeight`, `codecPolicy`, `audioOnly` | Tujuan kualitas dan kompatibilitas yang diminta |
| `audioPreference`, `selectedFormatIds` | Preferensi semantik dan format yang telah dipilih untuk melanjutkan transfer |
| `accountScopeId` atau anonymous | Kepemilikan akses akun; tidak berisi cookie |
| `state`, `phase`, `stateVersion` | Status durable dan pencegahan update dari runner lama |
| `attemptCount`, `errorCode`, `nextRetryAt` | Retry terkendali dengan alasan yang dapat ditampilkan |
| `transferredBytes`, `totalBytesEstimate`, `expectedOutputBytes` | Bedakan ukuran transfer, perkiraan, dan hasil final |
| `workDirectory`, `reservedOutputUri`, `pendingOutputName` | Jejak sumber sementara dan output yang menjadi milik pekerjaan |
| `outputPublished`, `libraryRecorded`, `finalUri` | Rekonsiliasi jika proses mati di antara publish dan pencatatan daftar |
| `runnerId`, `leaseUntil` | Kepemilikan eksekusi agar pekerjaan tidak dijalankan dua runner |
| `networkPolicy`, `createdAt`, `updatedAt` | Batas jaringan serta pemantauan pekerjaan |

Simpan path relatif di bawah direktori kerja privat yang ditentukan aplikasi. Validasi path ketika dibaca; database tidak boleh menjadi izin untuk menghapus path arbitrer. Header akun dan cookie sementara tetap terpisah dari tabel pekerjaan.

### 11.3. State dan fase eksekusi

Gunakan state pekerjaan untuk kontrol, dan phase untuk progres. Pemrosesan/penyalinan bukan sekadar angka transfer yang mencapai 100%.

| State | Makna dan transisi utama |
| --- | --- |
| `QUEUED` | Menunggu; dapat dibatalkan atau diambil runner |
| `PREPARING` | Memvalidasi input, akun, storage, dan format |
| `RUNNING` | Runner aktif; phase berupa TRANSFER, PROCESSING, COPYING, VERIFYING, atau PUBLISHING |
| `PAUSE_REQUESTED` | Permintaan berhenti yang sedang mencapai titik aman |
| `PAUSED` | Berhenti atas permintaan pengguna; tidak otomatis dilanjutkan |
| `WAITING_NETWORK` | Menunggu jaringan yang memenuhi policy |
| `WAITING_AUTH` | Akun pemilik akses perlu tersedia kembali |
| `RETRY_WAIT` | Kegagalan sementara; menunggu jadwal dalam anggaran retry |
| `INTERRUPTED` | Eksekusi sebelumnya tidak lagi hidup; perlu rekonsiliasi |
| `COMPLETED` | Output diverifikasi, dipublikasikan, dan tercatat dalam daftar |
| `FAILED` | Memerlukan tindakan pengguna atau tidak dapat dipulihkan otomatis |
| `CANCELLED` | Pengguna membatalkan; pekerjaan tidak dijalankan ulang |

Perpindahan state memakai transaksi compare-and-set berdasarkan stateVersion/runnerId. Callback progres dari proses yang telah dibatalkan tidak boleh menghidupkan kembali pekerjaan atau menulis sukses.

### 11.4. Urutan implementasi runner dan rekonsiliasi

1. Pisahkan `DownloadService` menjadi adapter lifecycle dan `DownloadRunner` yang dapat diuji dengan proses serta penyimpanan palsu.
2. Simpan job sebelum menjadwalkan runner. Tombol Unduh membuat/enqueue job; UI membaca database/Flow, bukan object global sebagai sumber kebenaran.
3. Gunakan direktori kerja stabil per job, misalnya direktori privat `download-work/<jobId>`. Jangan memakai UUID direktori baru pada setiap retry untuk job yang sama.
4. Ambil kepemilikan job secara transaksional. Pastikan scheduler, Activity, dan notifikasi yang masuk bersamaan tidak memulai proses kedua.
5. Validasi kapasitas ruang untuk sumber sementara, hasil merge, dan copy final. Jika ukuran belum diketahui, tampilkan estimasi dan tangani kekurangan ruang pada setiap fase.
6. Pilih format dan audio sebelum transfer. Simpan format ID yang diperlukan untuk melanjutkan job; URL media diselesaikan ulang ketika dibutuhkan.
7. Checkpoint state/phase dan ukuran secara berkala. Jangan mengandalkan blok finally atau callback stop untuk mencatat seluruh kemajuan.
8. Setelah output yt-dlp/FFmpeg valid, simpan expectedOutputBytes sebelum membuat tujuan publik.
9. Rekam niat reservasi output dan nama sementara yang memuat penanda job. Setelah MediaStore insert berhasil, simpan URI miliknya. Saat publish, ubah ke nama final yang ramah pengguna.
10. Jika proses mati setelah insert tetapi sebelum URI tersimpan, rekonsiliasi hanya baris pending milik aplikasi yang memiliki penanda job yang dapat dibuktikan. Baris yang kepemilikannya tidak pasti tidak dihapus otomatis.
11. Salin hasil, tutup stream, verifikasi ukuran, lalu publish. Jika provider tidak melaporkan ukuran, gunakan strategi baca/verifikasi yang benar-benar dapat membuktikan hasil, bukan menandainya valid hanya karena ukuran tidak diketahui.
12. Catat hasil ke `SavedDownloads` secara idempoten berdasarkan URI/job, kemudian tandai COMPLETED. Simpan progres penutupan transaksi agar crash setelah publish tidak memicu unduhan duplikat.
13. Saat startup/runner dimulai ulang, rekonsiliasi job nonterminal: cek proses lama, lease, work files, URI pending/final, ukuran, dan status daftar. Jangan langsung mengulang seluruh transfer.
14. Untuk file yang sudah terpublikasi dan valid tetapi belum tercatat, selesaikan pencatatan daftar. Untuk copy yang belum valid, ulangi copy dari hasil lokal yang masih valid.
15. Hapus hanya output parsial dan temp yang dapat dibuktikan milik job yang dibatalkan/discard atau selesai. Pertahankan file `.part` job yang masih dapat dilanjutkan.
16. Pertahankan pembacaan daftar unduhan lama. Migrasi penambahan job store tidak mengharuskan impor ulang atau pemindahan file pengguna.

### 11.5. Pause, resume, cancel, retry, dan duplikasi

- **Pause saat transfer:** hentikan proses secara terkendali, simpan state, dan pertahankan fragmen/`.part`. Resume memulai ulang proses dengan direktori serta pilihan format yang sama dan kemampuan continuation yt-dlp yang telah diuji.
- **Pause saat merge/copy:** jangan menjanjikan kelanjutan dari byte terakhir jika backend tidak mendukungnya. Tampilkan fase yang sedang diselesaikan atau ulangi fase dari sumber lokal yang aman saat resume. Kontrol Pause dinonaktifkan sementara bila belum ada titik aman yang teruji.
- **Resume setelah proses mati:** cek integritas fragmen dan kesesuaian format. Jika server/source berubah sehingga transfer lama tidak kompatibel, mulai ulang hanya bagian yang perlu dan jelaskan kepada pengguna.
- **Cancel:** batalkan hanya job yang dipilih, hentikan proses miliknya, dan bersihkan output parsial miliknya. Cancel bukan perintah menghapus file selesai yang sudah dimiliki pengguna.
- **Retry otomatis:** maksimal 3 percobaan untuk kegagalan sementara pada satu rangkaian eksekusi. Auth, format tidak tersedia, storage penuh, dan pembatalan pengguna menunggu tindakan yang sesuai.
- **Duplikasi:** job aktif dengan video, konfigurasi, dan scope sama mengembalikan job yang sudah ada. Unduh ulang file selesai hanya terjadi melalui tindakan pengguna, dengan nama baru atau kebijakan eksplisit.

### 11.6. Scheduler dan pembatasan platform

Pilihan scheduler mempertimbangkan pemicu pengguna dan lama transfer; memindahkan semuanya ke worker generik saja belum menetapkan perilaku unduhan panjang. [Android: data transfer options](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options).

Rancangan adapter: UIDT `JobScheduler` untuk Android 14/API 34 ke atas, dan foreground service yang ada sebagai fallback Android 8–13 dengan titik mulai yang diizinkan. UIDT memerlukan deklarasi `RUN_USER_INITIATED_JOBS`, JobService, notifikasi, dan penjadwalan dalam kondisi yang diperbolehkan. Penghentian melalui Task Manager dapat mematikan proses tanpa `onStopJob()`, sehingga checkpoint tetap wajib. [Android: user-initiated data transfer](https://developer.android.com/develop/background-work/background-tasks/uidt).

Jadwalkan batch yang dimulai pengguna ketika aplikasi memenuhi syarat. Jangan merancang antrean dengan asumsi setiap video berikutnya dapat selalu menjadwalkan UIDT baru dari background. Jika scheduler menolak, job tetap tersimpan dan UI menawarkan melanjutkan dari aplikasi.

Pada jalur `dataSync` yang masih digunakan, tangani batas runtime platform dan callback timeout. Android 15 untuk aplikasi target 35 ke atas memberlakukan anggaran total 6 jam dalam periode 24 jam bagi tipe layanan yang dibatasi ketika berjalan di background, dengan aturan reset saat aplikasi kembali ke foreground. Tangani `Service.onTimeout(int, int)`, catat checkpoint, dan segera panggil `stopSelf()`; jangan memulai loop service pengganti. [Android: foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

Berhenti karena sistem dan berhenti karena tindakan pengguna dibedakan. Tidak ada janji kelanjutan otomatis setelah pengguna melakukan force-stop; pekerjaan harus tetap dapat dikenali ketika aplikasi dibuka kembali.

### 11.7. Preferensi audio dan pengaturan unduhan

1. Pakai model `AudioPreference`/metadata bahasa yang sudah ada untuk pemilihan format unduhan. Periksa format asli yt-dlp agar bahasa, original/dub, serta format audio gabungan tetap dapat dibedakan.
2. Kebijakan awal: utamakan bahasa yang diminta; jika tidak tersedia, gunakan original yang teridentifikasi lalu default yang tersedia dengan keterangan bahasa hasil. Pengaturan download dapat menyediakan pilihan ketat yang gagal bila bahasa yang diminta tidak tersedia.
3. Pertahankan keluaran H.264 + AAC/MP4 dan MP3 yang dipilih saat ini. Jangan mengganti ke AV1/VP9 tanpa mengubah pilihan kompatibilitas pengguna secara eksplisit.
4. Jika format bahasa yang diminta tidak dapat dipasangkan dengan batas kualitas/codec, jelaskan pilihan yang tersedia sebelum mengunduh atau laporkan alasan yang spesifik.
5. Tambahkan pengaturan nyata untuk kualitas default, pilihan jaringan, lokasi tujuan, dan pengelolaan ruang. Pilihan Wi-Fi only harus memeriksa jaringan Wi-Fi; unmetered tidak selalu sama dengan Wi-Fi.
6. Folder kustom memakai izin URI yang dapat dipertahankan melalui mekanisme pemilih folder yang sesuai. `DownloadDestination` menjadi adapter MediaStore, legacy storage, atau folder yang dipilih.
7. Jika izin folder hilang, job menunggu pemilihan ulang; jangan menyalin ke lokasi lain secara diam-diam.
8. Pisahkan Remove from list dan Delete file dengan penjelasan akibat serta konfirmasi untuk penghapusan file. Gunakan alur izin sistem ketika diperlukan.

### 11.8. Uji, kriteria selesai, dan rollback

Uji penghentian proses pada PREPARING, TRANSFER, PROCESSING, setelah reservasi URI, saat COPY, setelah VERIFY, setelah PUBLISH sebelum pencatatan daftar, dan setelah daftar tercatat. Uji juga dua scheduler bersamaan, callback progres terlambat, file nama sama, storage penuh, izin dicabut, jaringan berubah, akun berganti, serta job lama dari schema sebelumnya.

- [ ] Antrean beberapa video bertahan setelah proses dibuat ulang.
- [ ] Hanya satu runner/yt-dlp aktif pada konfigurasi awal.
- [ ] Pause/resume diuji terhadap file parsial yang nyata, dengan batas per fase yang jelas.
- [ ] Job yang sudah publish tidak menghasilkan duplikasi setelah crash.
- [ ] COMPLETED hanya muncul setelah copy, verifikasi, publish, dan pencatatan daftar berhasil.
- [ ] Bahasa hasil sesuai pilihan atau fallback yang ditampilkan.
- [ ] File lama dan file tidak terkait tidak terhapus oleh cleanup.
- [ ] Progres/notifikasi menunjukkan fase yang benar dan membuka job/file yang tepat.

Rollback menghentikan penerimaan job melalui runner baru lalu mempertahankan job store, fragmen, dan URI untuk rekonsiliasi. Reader `SavedDownloads` lama tetap dapat membuka hasil yang sudah selesai. Jangan melakukan downgrade schema yang menghapus pekerjaan atau file; gunakan migrasi maju dan hentikan fitur baru bila perlu.

## 12. M08 — Lengkapi komentar biasa dan tindakan playlist

### 12.1. Pisahkan pembuktian kontrak dari pembuatan UI

`WatchPanels.kt` saat ini mendukung baca komentar/balasan, sementara tombol penilaian komentar dinonaktifkan. `AccountPlaylistScreens.kt` memiliki tombol unduh dan pengurutan yang hanya menampilkan pesan. Bukti: [WatchPanels.kt:344](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/WatchPanels.kt:344) dan [AccountPlaylistScreens.kt:205](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountPlaylistScreens.kt:205).

Untuk antarmuka internal, ambil endpoint/command dan parameter dari respons yang diamati serta fixture yang sudah disanitasi. Nama endpoint atau parameter dari ingatan tidak dijadikan kontrak implementasi. Jika sebuah akun/video tidak memperoleh kemampuan tertentu, UI mengikuti keadaan itu. Investigasi baca-saja tidak mengirim komentar atau mutasi percobaan ke akun nyata.

### 12.2. Komentar dan balasan

1. Buat repository komentar yang mempertahankan jalur baca NewPipe yang sudah bekerja, dengan adapter akun untuk kemampuan menulis bila respons server mendukungnya.
2. Perluas model dengan ID komentar, parent/thread, identitas pembuat, kemampuan balas/rating, dan command akun yang relevan. Jangan memakai teks komentar/nama pembuat sebagai identitas mutasi.
3. Tambahkan composer komentar dan composer balasan yang terikat ke video, parent, dan scope akun. Periksa komentar dinonaktifkan, login, serta kemampuan akun sebelum mengaktifkan Send.
4. Simpan draft pendek per target/scope bila composer ditutup sementara. Jangan membawa draft akun A ke akun B.
5. Saat Send, kirim satu operasi dan tampilkan status pending. Jangan mengosongkan draft sampai ada konfirmasi hasil yang memadai.
6. Pada respons berhasil, gunakan ID dan isi yang dikembalikan server, lalu masukkan ke daftar tanpa duplikasi. Jika respons tidak memberi item lengkap, refresh thread dengan identitas yang sama.
7. Pada timeout/hasil uncertain, jangan otomatis mengirim ulang komentar. Sediakan pengecekan ulang atau tindakan pengguna agar komentar ganda tidak tercipta.
8. Aktifkan rating komentar hanya bila command serta konfirmasinya sudah teruji. Gunakan state dan serialisasi operasi seperti M04.
9. Pertahankan paginasi, pin, creator heart, timestamp, dan navigasi thread yang sudah ada.
10. Live chat tetap memakai model/alurnya sendiri; komentar biasa tidak memakai endpoint pengiriman live chat.

Uji komentar disabled, logged-out, draft, parent yang salah/terhapus, response tidak dikenal, submit ganda, timeout sesudah diterima, dan logout saat pending. Test akun nyata memerlukan tindakan kirim yang memang diminta pengguna dengan isi yang disetujui; fixture dan test lokal menjadi jalur awal.

### 12.3. Pengurutan dan penambahan video playlist

1. Perluas parser filter agar menyimpan opsi sort/endpoint yang benar-benar diberikan server beserta scope dan playlist pemiliknya.
2. Saat sort diganti, batalkan pemuatan lama, kosongkan continuation milik sort lama, dan muat halaman pertama dari opsi yang dipilih.
3. Dedup video menggunakan identitas yang sesuai kebutuhan playlist. Perhatikan bahwa video yang sama dapat muncul sebagai item playlist yang berbeda; identitas untuk display dan remove tidak selalu video ID saja.
4. Jika sort hanya diterapkan pada item yang sudah dimuat, labeli sebagai pengurutan lokal dan jangan menyebutnya urutan seluruh playlist akun.
5. Tambahkan alur Add videos yang memanfaatkan pencarian dan dialog Save yang sudah ada, dengan pemilihan playlist tujuan yang jelas.
6. Perbarui header/count setelah mutasi yang dikonfirmasi. Jangan mengubah metadata akun hanya berdasarkan perkiraan lokal.
7. Pertahankan Play all sebagai antrean yang dapat diperpanjang dengan continuation; jangan menjanjikan seluruh playlist dimainkan bila hanya halaman pertama yang tersedia.

### 12.4. Unduh playlist

1. Muat daftar playlist secara bertahap menggunakan scope, filter, dan urutan yang dipilih.
2. Tampilkan jumlah item yang benar-benar ditemukan, item yang tidak dapat diunduh, pilihan kualitas/audio, serta perkiraan ruang jika tersedia. Jangan menyamakan jumlah header dengan jumlah yang sudah berhasil diambil.
3. Setelah pengguna memulai unduhan playlist, buat batch M07. Simpan per item; batalkan pemuatan halaman berikutnya jika pengguna membatalkan batch.
4. Hilangkan loop continuation yang mengulang token; dedup pekerjaan berdasarkan kebijakan video/konfigurasi, dengan catatan bila playlist memiliki entri video yang sama.
5. Setiap job memiliki hasil mandiri; satu video privat/tidak tersedia tidak menggagalkan file lain yang sudah valid.
6. Tampilkan sukses/gagal/dilewati dan tindakan Retry failed. Jangan menandai batch selesai penuh jika sebagian item gagal atau belum diketahui.
7. Jika daftar berubah selama unduh, gunakan snapshot item yang sudah diterima untuk batch; perubahan server berikutnya bukan perintah menghapus file lokal.

### 12.5. Kriteria selesai dan rollback

- [ ] Setiap tombol aktif mempunyai aksi yang tersedia serta penanganan loading/error/sukses.
- [ ] Komentar/balasan memakai target dan sesi yang benar, dengan pencegahan submit ulang yang ambigu.
- [ ] Sort tidak mencampur continuation dari urutan yang berbeda.
- [ ] Unduh playlist memakai antrean persisten dengan hasil per item.
- [ ] Akun/video yang tidak mendukung aksi mendapat status tidak tersedia yang jelas sebelum pengguna menekan kontrol.

Rollback per kemampuan menjaga pembacaan komentar dan playlist tetap tersedia. Mutasi yang sudah diterima server tidak dianggap dapat dibatalkan hanya dengan membalik kode; jangan menjalankan penghapusan komentar/playlist otomatis sebagai rollback.

## 13. M09 — Background audio, tautan masuk, dan Cast

### 13.1. Background audio dan kontrol sistem

Target: pemutaran tetap dapat dikendalikan melalui notifikasi/layar kunci saat pengguna mengaktifkan background playback. Media3 menempatkan Player dan MediaSession dalam `MediaSessionService`; manifest memerlukan tipe foreground `mediaPlayback` serta permission terkait. Dokumentasi ini menjadi dasar integrasi layanan, bukan alasan mempertahankan player milik Activity kedua. [Media3: background playback](https://developer.android.com/media/media3/session/background-playback).

Perubahan yang direncanakan:

1. Tambahkan `media3-session` dengan versi yang sama dengan artifact Media3 proyek; jangan mencampur versi antarartifact. Daftarkan layanan bertipe `mediaPlayback` beserta permission `FOREGROUND_SERVICE_MEDIA_PLAYBACK` dan `FOREGROUND_SERVICE` yang diperlukan.
2. Buat `PlaybackService` sebagai pemilik player/coordinator aktif. Pindahkan resolve, pembentukan source, dan recovery yang memerlukan ExoPlayer ke sisi pemilik itu.
3. Ubah ketergantungan UI yang hanya perlu play/pause/seek menjadi interface `Player`/controller. Operasi khusus seperti pemilihan format source memakai command coordinator yang divalidasi, bukan pemanggilan `setMediaSource()` pada MediaController.
4. Satukan jalur online/offline secara bertahap; lepaskan handoff berupa weak reference dan `OfflinePlaybackBridge` hanya setelah perilaku pindah Activity teruji pada jalur baru.
5. Tetapkan aturan: background nonaktif mengikuti lifecycle jeda yang jelas; background aktif melanjutkan niat play; Pause/Stop dari kontrol sistem selalu dihormati.
6. Kelola audio focus, perubahan output/headset, serta pergantian aplikasi audio. Putus headset memberi perilaku jeda yang konsisten.
7. Kirim metadata judul/channel/cover dan kemampuan previous/next sesuai antrean nyata. Jangan menampilkan Next bila target tidak ada.
8. PiP menampilkan player yang sama. Activity keluar/masuk PiP tidak membuat sesi dan audio baru.
9. Atur resource: pelepasan player, callback, notifikasi, dan layanan setelah Stop/sesi tidak diperlukan; tidak ada job retry atau tracking yang tertinggal.
10. Audit pengiriman final position History saat Pause/Stop dari layanan, bukan hanya tombol tutup UI. Pertahankan opt-out sinkronisasi dan pemisahan akun.
11. Validasi controller yang boleh mengirim command khusus; jangan menerima payload source/aksi akun arbitrer dari controller luar.

Uji layar terkunci, headphone/Bluetooth, audio focus, notifikasi Pause/Play/Stop, antrean habis, PiP ditutup, Activity direkreasi, perpindahan online/offline, serta background dimatikan ketika sedang bermain.

Kriteria selesai:

- [ ] Satu player aktif melayani UI, PiP, kontrol sistem, dan perpindahan layar.
- [ ] Pengaturan background menentukan perilaku secara konsisten.
- [ ] Kontrol layar kunci bekerja dan menampilkan metadata yang benar.
- [ ] Stop menghentikan sesi dan pekerjaan terkait tanpa suara tersembunyi.
- [ ] History/final position tetap benar pada skenario Pause/Stop yang diuji.

Rollback menonaktifkan background playback dan mengembalikan adapter pemilik sebelumnya melalui patch terbatas setelah menghentikan sesi layanan secara bersih. Jangan membiarkan player lama dan layanan baru hidup bersama.

### 13.2. Penerimaan tautan dan share

Manifest saat ini hanya menyediakan launcher. Tambahkan penerimaan VIEW/BROWSABLE untuk pola yang didukung dan SEND `text/plain` sebagai alur share-in. Penerimaan intent dari sistem berbeda dengan verifikasi kepemilikan domain: FlufPlay tidak memiliki domain YouTube, sehingga jangan mengklaim semua tautan YouTube akan terbuka otomatis sebagai verified App Link. [Android: About App Links](https://developer.android.com/training/app-links/about).

Parser dan routing yang direncanakan:

| Bentuk input | Tujuan |
| --- | --- |
| `youtube.com/watch?v=<id>` / `youtu.be/<id>` | Watch video |
| `youtube.com/shorts/<id>` | Shorts yang diminta, lalu antrean yang tersedia |
| `youtube.com/live/<id>` | Resolve jenis media aktual; live yang sudah berakhir menjadi replay bila teridentifikasi |
| Playlist `list=<id>` | Halaman playlist; parameter video opsional menentukan item pembuka bila cocok |
| Channel `/channel/<id>` atau handle `/@...` | Halaman channel |
| Parameter `t`/`start` yang valid | Posisi awal dalam batas media; prioritas di atas resume lokal untuk intent tersebut |
| Teks share dengan satu URL yang didukung | Ekstrak URL dan tampilkan tujuan yang benar |
| Banyak URL, malformed URL, atau host tidak didukung | Pilihan yang jelas atau pesan input tidak didukung; tidak membuka source arbitrer |

Langkah implementasi:

1. Buat parser murni yang memeriksa skema/host/path, ID, parameter waktu, dan batas ukuran input. Normalisasi ke model `OpenRequest`.
2. Jangan menerima `file://`, `content://`, URL CDN, atau skema internal `flufplay-tab:` dari sumber luar melalui parser tautan YouTube. URI unduhan tetap memakai jalur internal yang sudah divalidasi.
3. Bedakan extra internal dan external Intent secara eksplisit; Activity exported tidak boleh menganggap semua extra berasal dari aplikasi sendiri.
4. Gunakan satu dispatcher permintaan pada `onCreate()` dan `onNewIntent()`; beri request ID agar recomposition tidak membuka video berulang.
5. Prioritaskan intent eksplisit atas restore M06. Jika target sama dengan sesi aktif, terapkan seek/navigasi yang diminta tanpa membuat player kedua.
6. Tangani login/akses konten setelah parsing; input URL tidak mengubah akun aktif atau pengaturan autoplay secara diam-diam.
7. Uji cold start, warm start, aplikasi di background, sedang fullscreen, sedang memainkan file offline, dan input share dengan teks tambahan.

Kriteria selesai: seluruh pola yang dinyatakan didukung memiliki parser test dan pengujian routing; intent duplikat tidak memulai sesi ganda; host berbahaya dan source lokal dari luar tidak diteruskan ke player. Rollback dapat melepas filter manifest baru sambil mempertahankan routing internal.

### 13.3. Cast penuh

Saat ini tombol Cast membuka pengaturan pencerminan layar. Integrasi Cast media memerlukan sesi sender/receiver dan kontrol media receiver, sebagaimana dijelaskan oleh Google. Ini adalah kemampuan tambahan yang kelayakannya harus dibuktikan pada source FlufPlay. [Google Cast: Android sender integration](https://developers.google.com/cast/docs/android_sender/integrate).

Tahap awal yang wajib menghasilkan keputusan konkret:

1. Gunakan receiver uji dan media yang boleh diuji; buktikan codec, URL media, batas kedaluwarsa, kebutuhan header, dan konektivitas receiver.
2. Jangan mengirim cookie akun atau header login ke receiver. Keberhasilan local ExoPlayer tidak membuktikan receiver dapat mengambil source yang sama.
3. File `content://` lokal tidak dapat dianggap URL yang bisa diambil receiver. Dukungan file offline memerlukan rancangan penyajian lokal tersendiri dan berada di luar penerapan Cast streaming awal.
4. Catat source yang didukung/tidak didukung. Jangan menambahkan kontrol Cast penuh yang tampak tersedia sebelum sesi media receiver benar-benar bekerja.

Jika kelayakan terbukti, tambahkan pengelolaan sesi, pemilihan perangkat, load/play/pause/seek, sinkronisasi posisi, reconnect, disconnect, dan pengembalian playback ke perangkat lokal. Pemilihan source jarak jauh berada di adapter terpisah dari source ExoPlayer.

Kriteria selesai: receiver memutar konten yang dinyatakan didukung, kontrol sinkron, tidak ada audio ganda, serta kembali ke lokal dengan posisi yang benar. Jika kontrak sumber tidak memungkinkan, catat status **ditangguhkan karena batas dukungan sumber**, dan labeli kemampuan yang ada sebagai pencerminan layar. Ini bukan status Cast media selesai.

## 14. M10 — Perkuat pengujian dan pecah tanggung jawab secara bertahap

### 14.1. Struktur kode dan urutan ekstraksi

Ukuran file tidak dijadikan bukti performa. Tujuan pemisahan adalah membatasi lifecycle, kepemilikan state, dan efek samping agar perubahan dapat diuji.

| Urutan | Ekstraksi | Yang tetap berada di UI |
| --- | --- | --- |
| 1 | Policy header, model sesi, dan klasifikasi error murni | Rendering status serta intent tindakan |
| 2 | `PlaybackSourceBuilder` dan model pilihan media | Pilihan kualitas/subtitle dan label yang tampil |
| 3 | Repository akun/engagement dan pemilik job akun | State tampilan yang berasal dari repository |
| 4 | Recovery dan snapshot lewat coordinator | Gesture, animasi, posisi panel, dialog |
| 5 | Job store, runner, scheduler unduhan | Daftar, pencarian, kontrol job, progres |
| 6 | Service/controller playback pada M09 | Surface video dan kontrol yang mengendalikan Player |
| 7 | Bagian watch/search/home/subscriptions yang masih besar | Composable screen dengan input dan callback yang jelas |

Migrasi per alur dilakukan dengan test perilaku yang relevan. Jangan memindahkan seluruh `MainActivity.kt` dan menambah fitur baru dalam satu patch. `LiveChat.kt` dapat dipecah menjadi transport/session, parser, state, dan UI setelah tindakan akun utamanya dipastikan mengikuti scope.

### 14.2. Lapisan pengujian

| Lapisan | Apa yang dibuktikan | Peralatan yang direncanakan |
| --- | --- | --- |
| Unit JVM | Parser, policy, transisi state, ordering request, anggaran retry, migrasi snapshot | JUnit yang ada, coroutine test clock bila diperlukan |
| Integrasi transport | Header, redirect, pembatalan, urutan respons, error aplikasi | MockWebServer atau server lokal uji dengan identitas sintetis |
| Integrasi penyimpanan | Transaksi, lease, migrasi SQLite, rekonsiliasi URI/file | Test database dan instrumentation penyimpanan |
| Compose/instrumentation | Tombol pending/disabled, state akun, navigasi, semantics | `app/src/androidTest`, runner dan dependency test yang cocok dengan proyek |
| Integrasi Media3 | Source/track yang benar-benar dipakai, subtitle, recovery, lifecycle | Asset media lokal atau HTTP lokal agar hasil deterministik |
| Perangkat manual | Audio terdengar, PiP, lock screen, proses mati, OEM, receiver Cast | Perangkat yang identitas OS dan APK-nya dicatat |
| Akun manual | Mutasi dan History terlihat pada akun/persona yang sama | Skenario terbatas yang memang diizinkan pengguna |

Test otomatis di CI menggunakan data sintetis dan server lokal. Jangan menjadikan endpoint YouTube live sebagai syarat unit test lulus, atau menyimpan sesi login nyata sebagai secret test rutin.

### 14.3. Tambahan test yang bermakna

- M01: subtitle muncul pada source HLS/DASH/progresif dan Off benar-benar diterapkan.
- M02/M03: request tertahan saat sesi berubah, respons terlambat, redirect lintas host, dan header sensitif yang sudah ada pada request awal.
- M04: tiga layar membaca status yang sama, mutasi uncertain direkonsiliasi, dan respons lama tidak mengembalikan state sebelumnya.
- M05: waktu total recovery, pergantian media generation, user Pause, jaringan pulih, serta error permanen.
- M06: snapshot corrupt/migrasi dan host-driven test proses mati. Rekreasi Activity diuji tersendiri.
- M07: crash di batas publish/pencatatan, double runner, cancel saat copy, resume fragmen asli, dan pembacaan daftar lama.
- M08: composer tidak mengirim ulang saat timeout, salah parent ditolak, sort membuang continuation lama, dan batch parsial tetap akurat.
- M09: satu sumber audio aktif, controller reconnect, intent berulang, serta Cast yang benar-benar memutar di receiver.
- M11: semantics seek dan label status; layout pada font besar dan bahasa berbeda.

Pertahankan test yang sudah ada untuk audio, manifest, request headers, tracking URL/final update, History preview, unduhan, dan minimization. Ubah test lama hanya ketika kontrak perilakunya sengaja diperbarui, dengan alasan yang dicatat.

### 14.4. CI dan kriteria selesai

Tambahkan workflow build/test ketika dependency test dan perintah lokal sudah terbukti bekerja. Awali dengan unit test, lint, dan debug assemble. Instrumentation dapat berjalan terpisah agar keterbatasan emulator tidak menyamarkan hasil unit/build.

- [ ] CI tidak membutuhkan akun YouTube atau file pengguna.
- [ ] Test yang baru membuktikan hasil perilaku, bukan hanya memeriksa nilai konstanta yang sama dengan implementasi.
- [ ] Coroutine, player, server uji, dan file sementara dilepas ketika test selesai.
- [ ] Satu kegagalan memiliki log kategori/target test yang cukup untuk ditelusuri.
- [ ] Refactor mempertahankan perilaku yang dinyatakan dalam test dan skenario perangkat yang relevan.

Rollback dilakukan pada ekstraksi atau workflow yang bermasalah secara terpisah. Jangan menonaktifkan test yang gagal untuk menyatakan fitur selesai; perbaiki implementasi atau catat kontrak yang memang berubah.

## 15. M11 — Aksesibilitas, bahasa, dan dokumentasi

### 15.1. Seek bar dan kontrol aksesibel

Seek utama dan Shorts digambar dengan Canvas/gesture. Tambahkan informasi rentang progres dan aksi pengubahan progres pada node semantics yang sesuai. Compose menyediakan semantics untuk menyampaikan nilai/rentang komponen kustom kepada layanan aksesibilitas. [Compose: semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

Perubahan yang direncanakan:

1. Beri seek bar label, posisi, rentang, serta aksi `setProgress` ketika media dapat di-seek. Clamp nilai dan tolak nilai tidak valid.
2. Untuk live DVR, petakan aksi ke window yang sedang valid. Jangan mengubah mekanisme pembekuan batas selama gesture jari yang sudah dipakai player.
3. Tambahkan aksi maju/mundur melalui kontrol yang dapat difokuskan. Pilihan interval mengikuti kebijakan seek yang berlaku.
4. Beri state description untuk play/pause, subtitle, suka, saved, subscription, serta status pending/disabled. State unknown tidak dibacakan sebagai Off yang pasti.
5. Gunakan area sentuh yang memadai tanpa mengubah ukuran ikon yang telah disamakan dengan referensi. Cegah label dekoratif dibaca berulang.
6. Batasi pembaruan pengumuman progres agar pembaca layar tidak membacakan setiap frame. Prioritaskan perubahan yang dipicu pengguna dan status penting.
7. Uji urutan fokus ketika panel komentar, playlist, miniplayer, atau dialog dibuka/ditutup; fokus kembali ke pemicu yang relevan.

Uji TalkBack nyata, navigasi keyboard yang relevan, font besar, serta seek VOD/live/Shorts. Test semantics tidak menggantikan pengamatan apakah kontrol dapat digunakan pada perangkat.

### 15.2. Bahasa dan format teks

1. Jadikan bahasa Inggris sebagai default resource agar mengikuti bahasa UI utama yang saat ini dipakai. Sediakan terjemahan Indonesia melalui resource locale terpisah, misalnya `values-b+id/strings.xml` pada minimum API proyek.
2. Pindahkan teks user-facing ke `values/strings.xml`, termasuk content description, error, dialog, dan notifikasi. Gunakan plural dan format string untuk jumlah/durasi.
3. Sesuaikan pembatasan `resourceConfigurations += "en"` agar resource Indonesia tidak terbuang dari APK. Verifikasi isi resource hasil build.
4. Gunakan locale yang sesuai untuk tanggal, angka, dan label relatif. Jangan menyatukan angka dengan potongan kalimat yang hanya cocok pada satu bahasa.
5. Pisahkan bahasa antarmuka dari preferensi audio. Pengguna yang memilih UI Inggris tetap dapat memakai audio Indonesia dan sebaliknya.
6. Periksa panjang teks, ellipsis, tinggi kontrol, serta font besar pada kedua bahasa. Perbaiki layout berdasarkan bukti tampilan.

### 15.3. Dokumentasi dan status fitur

1. Perbarui README setelah perubahan perilaku benar-benar diterapkan, termasuk feed akun/fallback, status rating, kemampuan komentar, unduhan, background, dan Cast.
2. Pisahkan kemampuan yang telah diimplementasikan dari yang telah diverifikasi pada perangkat/akun. Catat skenario serta tanggal bukti yang relevan.
3. Hubungkan rencana ini dengan dokumen History dan analisis unduhan lama sebagai konteks. Beri catatan jika status di dokumen lama telah dilampaui; jangan memperlakukan status lama sebagai kondisi aplikasi sekarang.
4. Tambahkan catatan migrasi job/snapshot, cara menemukan log yang disanitasi, dan cara menonaktifkan kemampuan baru untuk diagnosis.

### Kriteria selesai dan rollback

- [ ] Seek dapat dioperasikan melalui TalkBack pada jenis media yang mendukung seek.
- [ ] Status penting memiliki label dan state yang benar.
- [ ] UI/notifikasi memakai resource bahasa secara konsisten.
- [ ] Resource Indonesia tersedia di build dan tidak mengubah pilihan audio.
- [ ] README mencerminkan perilaku serta batas verifikasi yang sebenarnya.

Rollback layout/terjemahan dilakukan per komponen. Pertahankan ID resource yang sudah digunakan dan hindari menghapus resource secara massal ketika hanya satu terjemahan yang perlu dikoreksi.

## 16. Urutan pengerjaan yang dapat dijalankan

Setiap tahap menghasilkan patch terbatas, bukti verifikasi, dan pembaruan status. Tidak ada jadwal kalender yang dipaksakan karena kontrak server dan ketersediaan perangkat dapat mengubah ukuran pekerjaan.

| Tahap | Paket | Perubahan utama | Syarat memulai | Bukti sebelum lanjut |
| --- | --- | --- | --- | --- |
| T00 | Dasar M10 | Snapshot checkout, inventaris perubahan lokal, harness test yang diperlukan | Kode kerja saat ini diketahui | Baseline test/build hanya saat implementasi telah dimulai; hasil dan kegagalan awal dicatat |
| T01 | M03 | Policy host/header dan redirect | T00 | Test transport negatif/positif; ekstraksi/media yang relevan tetap bekerja |
| T02 | M02 | Snapshot sesi, pembatasan hasil/cache, pembatalan | T01 | Respons A tidak menulis scope B; mutasi lama tidak mengambil kredensial baru |
| T03 | M01 | Source builder dan subtitle lintas format | T00; integrasi header mengikuti T01 | Subtitle tampil/Off pada HLS/DASH/progresif; pilihan audio tetap benar |
| T04 | M04 | State engagement bersama dan rekonsiliasi | T02 | State awal server, beberapa layar, pending, dan uncertain teruji |
| T05 | M05 | Kebijakan error dan recovery terbatas | T02, T03 | VOD/live/Shorts, niat Pause, dan batas retry teruji |
| T06 | M06 | Snapshot sesi dan restore | T05 | Proses mati dan pembukaan intent baru tidak menciptakan sesi salah |
| T07 | M07 inti | Job store, runner, antrean, checkpoint, publish idempoten | T02; test storage siap | Crash per fase, double runner, file lama tetap aman |
| T08 | M07 lanjutan | Pause/resume, bahasa audio, folder/network, scheduler platform | T07 | Resume nyata, batas platform, izin folder, dan bahasa hasil teruji |
| T09 | M08 | Komentar, sort/add playlist, batch unduh | T04; unduh playlist sesudah T08 | Kontrak akun terbukti; pending/timeout/batch parsial benar |
| T10 | M09 link | Parser dan routing intent | T06 | Cold/warm start, share, waktu mulai, input berbahaya, request ganda |
| T11 | M09 background | Pemilik player di layanan, controller, kontrol sistem | T05, T06; coordinator stabil | Satu player, lock screen, PiP, audio focus, History final |
| T12 | M09 Cast | Bukti receiver, lalu integrasi jika layak | Source adapter stabil dan receiver tersedia | Media benar-benar diputar receiver; batas dukungan dicatat |
| T13 | M10/M11 | CI, refactor sisa, aksesibilitas, bahasa, README | Mengikuti hasil tahap terkait | Test yang relevan, TalkBack, resource, serta dokumentasi diverifikasi |

T03 dapat dikerjakan paralel secara konseptual dengan sebagian pekerjaan sesi, tetapi edit pada `MainActivity.kt` dan `PlaybackNet.kt` perlu dikoordinasikan. M10/M11 tidak menunggu semua fitur selesai: test, semantics, dan resource untuk kontrol yang sedang disentuh ikut dikerjakan pada tahapnya.

## 17. Matriks pengujian penerimaan

Semua status di bawah **belum dijalankan sebagai bagian pembuatan dokumen**. Isi kolom hasil pada implementasi, sertakan jenis test dan lokasi bukti.

| ID | Paket | Skenario | Hasil yang diterima |
| --- | --- | --- | --- |
| Q01 | M01 | HLS + subtitle eksternal, ganti bahasa/Off | Teks tampil sesuai bahasa/waktu; Off menghilangkannya |
| Q02 | M01 | DASH single-period dan kasus multi-period | Dukungan nyata dan batasnya dilaporkan; playback tidak rusak |
| Q03 | M01 | Progresif serta video-only + audio | Subtitle tidak hilang/tergandakan; audio dan posisi tetap benar |
| Q04 | M02 | Feed A terlambat setelah logout/login B | UI, cache, continuation B tidak tercampur A |
| Q05 | M02 | Mutasi menunggu versi client saat sesi berubah | Request tidak dikirim menggunakan kredensial B |
| Q06 | M02 | Respons mutasi A tiba setelah B aktif | Tidak memperbarui engagement B dan tidak dikirim ulang |
| Q07 | M02 | Cache resolve/prefetch dipakai pada scope berbeda | Hak akses/metadata lama tidak dianggap valid untuk scope baru |
| Q08 | M03 | Host palsu/query/path/userinfo/port/HTTP | Header sensitif tidak terkirim |
| Q09 | M03 | Redirect lintas host atau downgrade | Policy dinilai ulang dan header akun tidak ikut |
| Q10 | M03 | Media CDN dengan User-Agent yang tepat | Playback tetap valid tanpa cookie akun ke CDN |
| Q11 | M04 | Video sudah disukai/channel sudah dilanggani | State awal mengikuti server, bukan map kosong |
| Q12 | M04 | Watch, Shorts, fullscreen, player offline | Status target yang sama konsisten di seluruh tampilan |
| Q13 | M04 | Ketukan cepat/timeout/HTTP sukses berisi error | Tidak ada sukses palsu atau hasil lama yang menimpa |
| Q14 | M05 | VOD 403 lalu source baru valid | Pulih otomatis dalam anggaran, posisi dan pilihan tetap |
| Q15 | M05 | Error permanen/offline/Pause saat retry | Tidak loop tanpa akhir; niat Pause dihormati |
| Q16 | M05 | Live DVR bergeser dan Auto HLS | Recovery sesuai timeline; tidak memaksa progresif akibat bahasa unknown |
| Q17 | M06 | Rekreasi Activity | State UI/player sesuai lifecycle yang dirancang |
| Q18 | M06 | Proses mati di background, lalu buka ulang | Konteks pulih dengan source baru, keadaan Pause, posisi checkpoint |
| Q19 | M06 | Snapshot rusak/URI hilang/intent baru | Aplikasi tetap dapat dipakai; intent baru diprioritaskan |
| Q20 | M07 | Antrean beberapa video dan dua pemicu runner | Antrean durable dan hanya satu eksekusi aktif |
| Q21 | M07 | Pause/resume transfer dengan fragmen nyata | Transfer yang kompatibel dilanjutkan; batas merge/copy dijelaskan |
| Q22 | M07 | Crash setelah reservasi/saat copy | Output pending direkonsiliasi hanya jika kepemilikannya terbukti |
| Q23 | M07 | Crash setelah publish sebelum pencatatan daftar | Satu file valid, satu entri, tanpa download duplikat |
| Q24 | M07 | Cancel/storage penuh/izin folder hilang | State akurat; file lama/tidak terkait tetap tersedia |
| Q25 | M07 | Audio Indonesia tersedia/tidak tersedia | Format hasil sesuai pilihan atau fallback yang diketahui pengguna |
| Q26 | M07 | Job dihentikan sistem/pengguna/timeout platform | Checkpoint tetap terbaca; berhenti pengguna tidak dilawan dengan restart loop |
| Q27 | M08 | Komentar disabled, reply, timeout Send | Tidak salah parent, tidak submit ganda, draft dipertahankan sesuai hasil |
| Q28 | M08 | Sort playlist berganti saat continuation berjalan | Respons urutan lama tidak digabung ke urutan baru |
| Q29 | M08 | Playlist multipage dengan item gagal/duplikat | Batch memberi hasil per item dan tidak menyatakan sukses penuh yang palsu |
| Q30 | M09 | Link/share cold start, warm start, input ganda | Target/waktu benar dan tidak membuat player kedua |
| Q31 | M09 | Lock screen, PiP, headphone, audio focus | Satu sumber audio, kontrol sistem sesuai niat pengguna |
| Q32 | M09 | Cast receiver/reconnect/kembali ke lokal | Remote playback terbukti dan posisi tetap konsisten |
| Q33 | M10 | Histori final, audio pilihan, unduhan lama | Perilaku yang sudah ada tetap lolos test relevan |
| Q34 | M11 | TalkBack seek VOD/live/Shorts | Kontrol dapat dipakai dan nilainya dibacakan sesuai keadaan |
| Q35 | M11 | Inggris/Indonesia/font besar | Teks tidak terpotong secara merusak dan preferensi audio tetap independen |

Tambahkan pengujian Android API 26–28 untuk penyimpanan legacy, API 29+ untuk MediaStore, API 34 untuk UIDT, serta API 35/36 untuk batas lifecycle layanan. Emulator dapat dipakai untuk cakupan platform, tetapi PiP, audio, OEM, dan Cast tetap memerlukan bukti perangkat yang relevan.

## 18. Prosedur verifikasi saat implementasi nanti

### 18.1. Baseline dan perubahan lokal

1. Baca ulang instruksi proyek dan status checkout terbaru.
2. Catat HEAD, daftar file berubah/untracked yang relevan, serta versi dependency. Jangan menganggap untracked file tidak penting; banyak fitur saat ini berada di file yang belum dilacak Git.
3. Jika memakai worktree lain, pastikan snapshot kerja yang diperlukan termasuk file baru dibawa secara sengaja. Worktree dari HEAD saja dapat kehilangan implementasi yang sedang dipakai.
4. Catat kegagalan baseline sebelum menyentuh paket terkait. Jangan memperbaiki perubahan pengguna yang tidak terkait hanya agar baseline terlihat bersih.

### 18.2. Perintah pemeriksaan yang disarankan

Perintah berikut adalah panduan untuk tahap implementasi. **Perintah ini tidak dijalankan saat penyusunan rencana.** Java 21 mengikuti konfigurasi `gradle.properties` saat ini; satu worker dan prioritas rendah menjaga pemeriksaan tetap terfokus.

```bash
cd /home/iqbal/Documents/flufclip/flufplay
git diff --check
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 nice -n 10 ./gradlew \
  --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Gunakan test terarah dahulu untuk paket yang sedang diubah; jalankan rangkaian relevan yang lebih luas setelah lolos. Opsi `--offline` hanya dipakai bila seluruh dependency yang diperlukan sudah tersedia. Penambahan dependency baru dapat memerlukan satu tahap pengambilan artifact yang dicatat.

Setelah source `androidTest` dan perangkat/emulator uji disiapkan, jalankan instrumentation untuk paket yang memerlukannya:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 nice -n 10 ./gradlew \
  --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:connectedDebugAndroidTest
```

Catat serial perangkat dan versi APK sebelum pengujian. Instalasi APK dan pengujian akun/perangkat adalah kegiatan implementasi/verifikasi tersendiri; keberhasilannya tidak diasumsikan dari build.

### 18.3. Bukti dan observabilitas

Gunakan log terstruktur yang cukup untuk diagnosis:

| Bagian | Field yang dapat dicatat | Field yang tidak dicatat |
| --- | --- | --- |
| Request akun | ID operasi lokal, generation, kategori endpoint, kode hasil, waktu | Cookie, Authorization, token akun/continuation, identitas lengkap |
| Playback | Media generation, jenis source, state, kategori error, attempt, durasi recovery | URL CDN/manifes bertanda tangan, query autentikasi |
| Unduhan | Job ID lokal, state, phase, ukuran, retry, alasan berhenti | Cookie file, header login, respons akun mentah |
| UI | Nama skenario, state, aksi, perangkat/OS, APK checksum bila relevan | Data pribadi yang tidak diperlukan skenario |

Simpan hasil test dan catatan manual di direktori evidence khusus per tahap. Media uji serta fixture memakai data sintetis atau yang sudah disanitasi. Jangan memasukkan log mentah ke Git tanpa pemeriksaan.

### 18.4. Format pencatatan hasil per tahap

```text
Tahap / paket:
Tanggal / HEAD / patch:
File yang diubah:
Perubahan perilaku:
Test otomatis yang dijalankan dan hasil:
Build/lint dan hasil:
Perangkat / OS / identitas APK:
Skenario perangkat/akun yang benar-benar dilakukan:
Hasil yang diamati:
Batas bukti / hal yang belum diuji:
Risiko tersisa:
Cara rollback yang telah disiapkan:
Lokasi evidence:
Status: belum mulai / berjalan / menunggu bukti / selesai / ditangguhkan
```

Gunakan label bukti yang tepat: source diperiksa, unit test lulus, integrasi lokal lulus, build lulus, APK dipasang, perangkat lulus, dan akun terverifikasi adalah hasil yang berbeda. Tidak ada satu label yang menggantikan seluruh label lain.

## 19. Risiko lintas paket dan penanganannya

| Risiko nyata dalam lingkup perubahan | Penanganan yang harus tersedia |
| --- | --- |
| Pergantian source memperburuk Auto HLS/audio | Pertahankan helper pilihan, snapshot niat pengguna, dan kasus regresi HLS tanpa DASH |
| Refactor menghentikan final position History | Satu jalur akhir sesi, test tracking final, lalu cek akun pada skenario yang sesuai |
| Request A selesai ketika akun B aktif | Snapshot sesi, header tetap, pembatalan best-effort, pemeriksaan sebelum commit state |
| Caption tersedia di UI tetapi tidak dirender | Integrasi source nyata serta pengamatan teks pada waktunya |
| Proses mati setelah MediaStore publish | Job journal, rekonsiliasi, pencatatan daftar idempoten |
| Pause menghasilkan file final setengah jadi | Batas fase yang jelas; partial tetap pending dan belum masuk daftar selesai |
| Mengulang mutasi setelah timeout membuat duplikasi | Outcome uncertain, read-back, tanpa replay otomatis untuk tindakan non-idempoten |
| Kontrol background menciptakan dua player | Pemilik tunggal, pemindahan adapter bertahap, pemeriksaan sesi/audio saat handoff |
| Upgrade dependency untuk test mengubah runtime | Tambah dependency minimal yang cocok dengan proyek; pisahkan upgrade runtime bila diperlukan |
| Parser internal berubah | Fixture berversi, capability yang bisa tidak tersedia, error yang dapat dibedakan dari daftar kosong |
| Cast tidak dapat mengambil source FlufPlay | Bukti receiver sebelum integrasi UI penuh; dokumentasikan batas dukungan |
| Rollback menghilangkan data versi baru | Schema maju yang kompatibel, reader lama tetap tersedia, tidak ada cleanup massal |

## 20. Checklist penyelesaian perbaikan

Checklist ini tetap kosong sampai kriteria paket dan bukti relevannya terpenuhi. Jika ada item ditangguhkan, catat alasan dan akibat pada pengguna; jangan mengubahnya menjadi selesai hanya karena tombol disembunyikan.

- [ ] M01: subtitle pada progresif/HLS/DASH yang didukung terverifikasi.
- [ ] M02: request, continuation, cache, dan state akun mengikuti scope yang benar.
- [ ] M03: header autentikasi dan redirect memenuhi policy tujuan.
- [ ] M04: state suka/simpan/subscribe bersama, mengikuti status server, dan tahan request beruntun.
- [ ] M05: recovery VOD/live/Shorts terklasifikasi dan mempunyai batas.
- [ ] M06: konteks sesi pulih setelah proses mati dengan source baru dan niat Pause.
- [ ] M07: antrean/pause/resume/recovery/publish unduhan serta audio/pengaturannya teruji.
- [ ] M08: komentar/balasan/rating dan fungsi playlist yang dinyatakan didukung benar-benar bekerja.
- [ ] M09 link: intent masuk tervalidasi dan routing cold/warm start teruji.
- [ ] M09 background: layanan, kontrol sistem, PiP, serta perpindahan online/offline memakai satu sesi aktif.
- [ ] M09 Cast: receiver terverifikasi, atau status ditangguhkan beserta batasnya dicatat secara terpisah.
- [ ] M10: test integrasi yang relevan dan pemisahan tanggung jawab mengikuti perubahan yang selesai.
- [ ] M11: aksesibilitas, resource bahasa, README, dan catatan verifikasi telah diperbarui.
- [ ] Migrasi mempertahankan History, preferensi, URI, serta file unduhan pengguna.
- [ ] Setiap tahap memiliki patch terbatas, hasil verifikasi, batas bukti, dan cara rollback.

Definisi selesai keseluruhan: semua kemampuan dalam lingkup rilis yang dipilih memenuhi kriteria paketnya, pengujian penerimaan relevan memiliki hasil tercatat, dan kemampuan yang belum terbukti mempunyai status tersendiri yang jelas. Tidak ada klaim runtime/akun/performa hanya berdasarkan pemeriksaan source atau keberhasilan build.
