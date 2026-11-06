# Audit pengerjaan P1 FlufPlay — 4 November 2026

Dokumen yang diaudit: [Rencana perbaikan FlufPlay](RENCANA_PERBAIKAN_FLUFPLAY_LENGKAP.md), khusus M01, M02, dan M03.

**Hasil: 0 dari 3 paket P1 memenuhi seluruh kriteria selesai. Ketiganya sudah diimplementasikan sebagian.** Ada delapan kelompok pekerjaan terbuka di bawah; beberapa berupa kekurangan kode yang dapat ditunjukkan langsung, sementara sebagian lain merupakan bukti integrasi/perangkat yang belum tersedia.

Audit ini membaca working tree saat ini, test source, laporan test yang sudah tersedia, dan kontrak Media3 yang relevan. HEAD masih `ec47f85`, dengan banyak perubahan lokal dan file baru. Seluruh diff terhadap HEAD tidak diasumsikan berasal dari pengerjaan P1.

Tidak dilakukan perubahan kode aplikasi, build, eksekusi ulang test, instalasi APK, pengujian akun, atau pengujian perangkat. Perubahan pada tugas audit terbatas pada laporan Markdown dan tautan/ringkasan hasil audit dalam rencana.

## 1. Ringkasan status per paket

| Paket | Implementasi yang terlihat sekarang | Penghambat penyelesaian |
| --- | --- | --- |
| M01 — subtitle | HLS/DASH memakai `DefaultMediaSourceFactory`, konfigurasi subtitle, MIME manifest; live configuration bersyarat; audio terpisah tetap dirangkai | Progresif belum memakai factory khusus; Off/CC belum mengendalikan trek manifest; penanganan error dan batas multi-period belum lengkap; belum ada test subtitle/integrasi/perangkat |
| M02 — sesi akun | `loadHome()` menangkap revisi dan memeriksa hasil; job Beranda dibatalkan saat revisi berubah; `accountRequest()` mengambil header sebelum menunggu versi client | Mutasi lain masih mengambil autentikasi global; respons langganan/paginasi belum dijaga; cache/continuation Shorts tidak memiliki scope; pembatalan Call jaringan dan scope persisten belum lengkap |
| M03 — tujuan header | Policy HTTPS/host/port/userinfo sudah ada; header media disaring; HomeFeed dan downloader NewPipe memiliki network interceptor | Transport Shorts/InnerTube/live chat/profile belum memiliki penjagaan hop yang sama; redirect media belum dibuktikan; tidak ada test jaringan/redirect yang ditemukan |

Tidak digunakan persentase penyelesaian karena tiap butir berbeda risiko dan kebutuhan pembuktiannya. Keberadaan helper atau hasil test policy tidak menutup kriteria paket yang bergantung pada perilaku player dan sesi akun.

## 2. Perubahan yang sudah dapat dikonfirmasi dari kode

1. **Perbaikan utama konstruksi HLS/DASH memang sudah masuk.** Cabang HLS/DASH pada `playQuality()` menggunakan factory yang menerima MediaItem berisi subtitle dan MIME. Konfigurasi live hanya dibuat ketika `info.isOngoingLive()` benar. Bukti: [MainActivity.kt:779](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:779), [MainActivity.kt:792](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:792), dan [PlaybackNet.kt:61](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:61).
2. **Beranda halaman pertama sudah mendapat penjagaan revisi.** `loadHome()` menyimpan revision dan memeriksanya setelah permintaan serta sebelum penulisan hasil. Efek pergantian sesi membatalkan `homeLoadJob`. Bukti: [MainActivity.kt:807](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:807) dan [MainActivity.kt:892](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:892).
3. **Header pada `HomeFeed.accountRequest()` sudah ditangkap sebelum pemuatan versi client.** Ada pemeriksaan revision sebelum dan sesudah request. Bukti: [HomeFeed.kt:134](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt:134).
4. **Pencocokan substring tujuan auth pada downloader NewPipe sudah diganti.** `permitsAccountHeaders()` memeriksa HTTPS, port 443, userinfo, serta batas domain YouTube. Bukti: [AuthHeaderPolicy.kt:6](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AuthHeaderPolicy.kt:6) dan [VideoRepository.kt:78](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt:78). Temuan lama mengenai substring tersebut tidak dicatat ulang sebagai defect yang masih ada di downloader ini.
5. **Dua transport sudah memiliki filter per network hop.** HomeFeed dan `extractorClient` NewPipe menghapus header sensitif ketika tujuan tidak diizinkan. Bukti: [HomeFeed.kt:81](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt:81) dan [VideoRepository.kt:51](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt:51).
6. **Header awal request media sudah dibersihkan.** Header sensitif bawaan dibuang, lalu Cookie hanya ditambahkan untuk tujuan yang lolos policy. Bukti: [PlaybackNet.kt:22](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:22). Ini belum membuktikan seluruh redirect di bawah DataSource diperiksa ulang.

## 3. Temuan yang belum selesai

### A01 — M02: mutasi rating/subscription masih dapat mengambil sesi yang berbeda

**Bukti:** [ShortsFeed.kt:102](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:102), [ShortsFeed.kt:138](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:138), [ShortsFeed.kt:179](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:179), dan [MainActivity.kt:530](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:530).

`rate()` dan `setSubscribed()` hanya memeriksa login, dapat menunggu pengambilan versi client, kemudian `execute()` membangun header dari `YouTubeAuth` global. Keduanya belum menerima expected session/revision. Callback hasil Watch juga tidak memeriksa revisi sesi sebelum mengubah state.

Urutan yang memungkinkan dari kode: pengguna memulai tindakan pada akun A, request menunggu versi client, akun berubah ke B, lalu request mengambil header B. Ini merupakan risiko dari urutan operasi yang terbuka; pengiriman salah akun belum direproduksi pada audit ini.

Jalur player offline menggunakan fungsi yang sama pada [DownloadedWatch.kt:90](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadedWatch.kt:90). Live chat juga mengambil auth global saat pengiriman, sementara state chat diingat berdasarkan video/live/postLive tanpa revision: [LiveChat.kt:388](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt:388) dan [LiveChat.kt:703](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt:703).

**Pekerjaan tersisa:** tangkap konteks sesi sebelum operasi dimulai; gunakan snapshot header yang sama; validasi expected session sebelum kirim dan sebelum menerapkan hasil; batalkan/reset state tindakan dan chat sesuai scope. Request yang sudah terkirim tetap harus diperlakukan sebagai operasi yang hasilnya mungkin belum pasti.

**Uji penutup:** tahan permintaan versi client untuk tindakan akun A, ganti sesi, lepaskan request; pastikan tidak ada mutasi memakai kredensial B dan tidak ada callback A yang mengubah state B. Ulangi pada Watch, Shorts, player offline, dan live chat.

### A02 — M02: respons langganan dan paginasi lama masih dapat menulis state sesi baru

**Bukti:** [MainActivity.kt:381](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:381), [MainActivity.kt:400](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:400), [MainActivity.kt:1002](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1002), dan [MainActivity.kt:892](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:892).

`loadSubscriptions()` dan `loadMoreSubscriptions()` meluncurkan coroutine pada scope komposisi lalu menulis daftar/token tanpa memeriksa revision. Efek pergantian sesi hanya membatalkan job Beranda yang disimpan; mengosongkan daftar langganan tidak membatalkan coroutine langganan yang sedang berjalan.

`loadMoreFeed()` juga belum menangkap revision/generation. Pada cabang Beranda, respons continuation langsung memperbarui token dan menambah item. Cabang kategori mempunyai sebagian pemeriksaan kategori/chip, tetapi itu belum mengikat hasil ke sesi akun. `subsLoading`/`feedLoading` juga belum di-reset atau dimiliki per generation secara konsisten.

Skenario yang masih terbuka: respons Subscriptions A ditahan; login berubah ke B dan daftar dikosongkan; respons A datang kemudian dan mengisi daftar lagi. Jika loading lama tetap aktif, pemuatan awal B juga dapat tertunda oleh guard loading tersebut.

**Pekerjaan tersisa:** simpan job dan revision/generation untuk langganan, membership, kategori, dan paginasi; batalkan pekerjaan lama; validasi kepemilikan sebelum setiap penulisan hasil, token, loading, dan error; reset continuation/loading yang relevan saat sesi berubah.

**Uji penutup:** respons lama datang setelah logout/login, kategori berubah ketika halaman berikutnya dimuat, dan request lama gagal setelah request baru dimulai. Daftar/token/error/loading sesi baru harus tetap benar.

### A03 — M02: cache dan urutan Shorts belum dipisahkan berdasarkan sesi

**Bukti:** [ShortsFeed.kt:50](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:50), [MainActivity.kt:462](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:462), [MainActivity.kt:1591](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1591), [VideoRepository.kt:376](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt:376), dan [HomeFeedCache.kt:11](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/HomeFeedCache.kt:11).

Cache `ShortsFeed.warm` hanya menyimpan halaman dan waktu, dengan TTL tiga menit; tidak ada scope/revision. `start()` dapat mengambil halaman tersebut sesudah akun berubah. `shortsResolver`, queue, dan token urutan disimpan tanpa key akun; efek pemuatan tab Shorts bergantung pada tab/request, bukan sesi.

`VideoRepository.resolve()` memakai `videoKey(url)` saja untuk cache dan job resolve. Pekerjaannya berjalan pada scope repository, sehingga cache/job yang hasilnya dapat bergantung pada akses akun belum dipisahkan secara menyeluruh. `ShortsResolver` juga memakai key video dan umur cache.

Cache disk Beranda masih berupa JSON dengan key `items`, tanpa scope/schema kepemilikan akun. Pembersihan pada efek revisi adalah kemajuan, tetapi belum menyediakan pemisahan cache persisten ketika proses dibuat ulang. `YouTubeAuth` belum mempunyai scope ID persisten; revision dimulai dari nol pada proses baru.

State `videoRatings` dan `subscribedChannels` pada [MainActivity.kt:527](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:527) juga belum diikat ke revisi. Pembacaan status server adalah pekerjaan M04, tetapi isolasi nilai lama saat ganti akun tetap bagian P1 M02.

**Pekerjaan tersisa:** partisi atau invalidasi cache yang bergantung akun; ikat warm page, resolver, queue, continuation, dan state mutasi ke scope; tambahkan identitas scope persisten untuk cache disk. Cache publik murni tetap dapat dipakai bersama bila batasnya jelas.

**Uji penutup:** hangatkan Shorts pada A lalu ganti B; buka video yang sama pada scope berbeda; buat ulang proses; pastikan halaman, token, metadata akses, dan status tindakan A tidak dipakai sebagai milik B.

### A04 — M03: penjagaan header/redirect belum mencakup seluruh transport

**Bukti:** [PlaybackNet.kt:39](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:39), [ShortsFeed.kt:38](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:38), [InnerTubePlayer.kt:35](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/InnerTubePlayer.kt:35), [LiveChat.kt:103](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt:103), dan [YouTubeAuth.kt:69](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/YouTubeAuth.kt:69).

| Transport | Pembatasan yang terlihat | Sisa pemeriksaan |
| --- | --- | --- |
| HomeFeed | Network interceptor menyaring header pada tujuan yang ditolak | Test redirect nyata dan pembuktian sesi untuk seluruh pemanggil |
| NewPipe `extractorClient` | Policy pada penambahan auth dan network interceptor | Test header bawaan, redirect, dan ekstraksi yang relevan |
| Media3 custom DataSource | Policy ketika DataSpec disiapkan | Filter per hop redirect di bawah transport belum terlihat/teruji |
| ShortsFeed | Client tersendiri dan penambahan auth global | Belum ada filter hop atau penonaktifan redirect eksplisit |
| InnerTubePlayer, termasuk Ask | Client tersendiri dan penambahan auth global | Belum ada filter hop atau penonaktifan redirect eksplisit |
| Live chat | Client tersendiri dan auth pada GET/POST | Belum ada filter hop atau penonaktifan redirect eksplisit |
| Refresh profil | Client tersendiri, memakai header akun | Belum ada filter hop atau penonaktifan redirect eksplisit |
| PlaybackTracker | Redirect secara eksplisit dimatikan; URL tracking divalidasi | Jangan menghapus perlindungan yang sudah ada; snapshot sesi/header tetap perlu dipertahankan |
| yt-dlp/FFmpeg | Cookie file di luar transport OkHttp/Media3 | Kontrak eksternal ini perlu dicatat tersendiri; helper Kotlin tidak otomatis membuktikan perilakunya |

`ResolvingDataSource` meneruskan DataSpec ke DataSource yang dibungkus; ia bukan network interceptor dari client HTTP di bawahnya. Karena itu, policy pada DataSpec awal belum cukup menjadi bukti semua redirect diperiksa. Lihat [kontrak Resolver](https://developer.android.com/reference/androidx/media3/datasource/ResolvingDataSource.Resolver) dan [implementasi resmi DefaultHttpDataSource](https://github.com/androidx/media/blob/release/libraries/datasource/src/main/java/androidx/media3/datasource/DefaultHttpDataSource.java). Source branch release digunakan sebagai penjelasan umum, bukan pengganti pembuktian versi artifact/platform yang dipakai aplikasi.

Audit ini tidak membuktikan cookie telah terkirim ke pihak lain. Temuannya adalah cakupan pengamanan dan bukti jaringan yang belum menutup persyaratan M03.

**Pekerjaan tersisa:** terapkan policy per hop atau hentikan redirect pada semua request akun; pilih transport media yang dapat menjalankan policy pada hop aktual; dokumentasikan peran host/header; pertahankan User-Agent media; gunakan kredensial sintetis pada pengujian.

**Uji penutup:** server A mengarahkan ke host B yang ditolak, variasi GET/POST dan status redirect, header Cookie bawaan, serta URL yang memuat teks YouTube pada query/path. Periksa header yang benar-benar diterima B, bukan hanya nilai hasil helper.

### A05 — M01/M03: cabang progresif masih memakai factory bawaan player

**Bukti:** [MainActivity.kt:582](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:582), [MainActivity.kt:796](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:796), dan [PlaybackNet.kt:61](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:61).

Player dibuat dengan `ExoPlayer.Builder(context).build()` tanpa pemasangan factory khusus. Pada format progresif dengan audio tertanam, `playQuality()` masih memanggil `player.setMediaItem(media(stream.content))`, sedangkan jalur HLS/DASH dan video-only membuat source melalui factory terkonfigurasi.

Akibat yang dapat dipastikan dari kode: cabang tersebut tidak memakai policy header, pemilihan User-Agent, dan load-error policy milik `youTubeMediaSourceFactory()`. MediaItem-nya tetap membawa konfigurasi subtitle; temuan ini bukan bukti bahwa semua subtitle progresif gagal.

**Pekerjaan tersisa:** bangun source progresif lewat factory yang sama, atau pasang factory terkonfigurasi sebagai factory player. Pastikan sumber audio terpisah serta source subtitle juga melewati DataSource/policy yang dimaksud.

**Uji penutup:** pilih format progresif yang valid dan amati request aktual ke media/subtitle. Pastikan User-Agent/header serta penanganan error konsisten dengan jalur lain, dan subtitle benar-benar dirender.

### A06 — M01: Off dan indikator CC belum mencerminkan trek teks manifest

**Bukti:** [MainActivity.kt:2218](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2218), [MainActivity.kt:2326](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2326), [MainActivity.kt:2359](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2359), dan [PlayerControls.kt:341](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlayerControls.kt:341).

Off saat ini mengosongkan `selectedSubtitle` dan membangun ulang source. Tidak ada perubahan `TRACK_TYPE_TEXT` pada jalur player online untuk memastikan trek teks bawaan manifest dinonaktifkan. Nilai `hasSubtitles` hanya memeriksa daftar subtitle extractor, dan `subtitlesEnabled` hanya mengikuti `selectedSubtitle != null`.

Dengan demikian, trek teks yang hanya ada di manifest belum terwakili oleh kontrol tersebut; Off juga belum mempunyai jaminan untuk kasus teks default/forced yang tetap tersedia dalam manifest. Ini tidak menyatakan teks pasti tetap terlihat pada setiap video.

API Media3 menyediakan penonaktifan tipe trek melalui `setTrackTypeDisabled`; keadaan pilihan UI dan pemilihan trek aktual perlu diselaraskan. [Media3: TrackSelectionParameters.Builder](https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder).

**Pekerjaan tersisa:** gabungkan kemampuan subtitle extractor dan `currentTracks`; pisahkan selected/wanted/applied/error; terapkan Off pada seleksi teks; aktifkan kembali trek yang dipilih secara eksplisit tanpa menggandakan subtitle.

**Uji penutup:** manifest-only subtitle, external-only subtitle, keduanya tersedia, default/forced track, ganti bahasa, dan Off. Periksa teks yang tampil dan state trek, bukan sekadar perubahan ikon CC.

### A07 — M01: kegagalan subtitle dan batas DASH multi-period belum ditangani sampai tuntas

**Bukti:** [MainActivity.kt:784](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:784), [MainActivity.kt:1773](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1773), dan [MainActivity.kt:2359](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2359).

Try/catch pada tombol subtitle hanya menangkap exception sinkron. Kegagalan pemuatan/rendering yang tiba setelah `prepare()` tidak mengembalikan pilihan melalui catch tersebut. Handler error player belum membedakan kegagalan subtitle dari media utama dan belum menunjukkan fallback khusus yang mempertahankan video/audio serta membetulkan state CC.

Tidak ditemukan pembatasan kemampuan untuk sideload subtitle pada DASH multi-period. Batas ini dinyatakan pada [dokumentasi Media3 media items](https://developer.android.com/media/media3/exoplayer/media-items), dan rencana sudah memasukkannya sebagai kriteria yang perlu diuji.

Yang dapat disimpulkan adalah fallback/klasifikasi dan pembuktiannya belum lengkap. Tidak disimpulkan bahwa setiap kegagalan subtitle pasti mematikan video, karena sebagian error dapat ditangani library sesuai jenis source-nya.

**Pekerjaan tersisa:** bedakan error subtitle dan media utama; terapkan fallback yang menjaga posisi/audio/niat Pause; sinkronkan state pilihan setelah error; tandai manifest yang tidak didukung sebelum mengklaim CC berhasil. Ekstraksi `PlaybackSourceBuilder` dapat membantu test, tetapi nama class baru bukan syarat fungsi berjalan.

**Uji penutup:** subtitle 404, timeout, konten rusak/MIME salah, DASH single-period dan multi-period, serta perubahan kualitas/audio ketika subtitle dipilih. Video tetap dapat dimainkan pada kasus yang didukung dan UI menjelaskan hasil sebenarnya.

### A08 — M01–M03: bukti test saat ini baru mencakup helper header

Laporan test yang tersedia mencatat hasil berikut:

| Suite | Test | Failure/error | Bukti |
| --- | --- | --- | --- |
| `AuthHeaderPolicyTest` | 1 | 0 / 0 | [XML hasil](/home/iqbal/Documents/flufclip/flufplay/app/build/test-results/testDebugUnitTest/TEST-id.flufclip.player.AuthHeaderPolicyTest.xml) |
| `PlaybackRequestHeadersTest` | 4 | 0 / 0 | [XML hasil](/home/iqbal/Documents/flufclip/flufplay/app/build/test-results/testDebugUnitTest/TEST-id.flufclip.player.PlaybackRequestHeadersTest.xml) |

Kedua report memiliki mtime 4 November 2026 sekitar 14:35 WIB. Source test memeriksa hasil fungsi policy/header dan konsistensi User-Agent; tidak membuka server redirect atau menjalankan player. Audit hanya membaca report ini dan tidak menjalankannya ulang. Timestamp bukan pengikat hash lengkap antara source, binary, dan hasil test.

Inventaris source test saat audit: 19 file unit test. Tidak ditemukan test subtitle, test `sessionRevision`/scope/request akun, test jaringan redirect, atau source `app/src/androidTest`. Ini adalah hasil pencarian dalam checkout, bukan klaim bahwa tidak pernah ada pengujian manual di luar checkout.

**Pekerjaan tersisa:** tambah test perilaku yang menutup A01–A07; jalankan integrasi Media3 dan transport; catat skenario perangkat/akun yang benar-benar dilakukan. Keluaran kompilasi atau test lima helper tidak memenuhi kriteria rendering, isolasi akun, atau keamanan redirect keseluruhan.

## 4. Pemeriksaan kriteria selesai dari rencana

| Paket | Kriteria | Status audit |
| --- | --- | --- |
| M01 | Subtitle tampil pada progresif/HLS/DASH yang didukung | Sebagian jalur source tersambung; rendering belum terbukti |
| M01 | Off menghilangkan teks tanpa mengubah posisi/audio | Belum lengkap untuk trek manifest dan belum diuji |
| M01 | Error subtitle tidak mematikan playback valid | Fallback khusus/bukti belum tersedia |
| M01 | Batas DASH multi-period dicatat dan diuji | Dicatat dalam rencana; penanganan dan uji belum tersedia |
| M02 | Semua request memakai snapshot kredensial sesi operasi | Baru sebagian, terutama `accountRequest()` |
| M02 | Respons sesi/generation lama tidak menulis UI/cache | Baru sebagian pada halaman pertama Beranda/History |
| M02 | State akun di-reset/terpartisi konsisten | Masih ada cache Shorts, continuation, resolver, dan rating/subscription tanpa scope |
| M02 | Data perangkat dan file lokal bertahan sesudah logout | Tidak tampak perubahan yang sengaja menghapusnya pada bagian audit; skenario perangkat tetap belum dijalankan |
| M03 | Semua transport memakai policy terdokumentasi | Baru sebagian transport |
| M03 | Semua kasus negatif tidak mengirim header sensitif | Helper lolos pada kasus yang diuji; jaringan aktual belum diuji |
| M03 | Anonim/login/membership/media tetap bekerja | Belum dibuktikan ulang sebagai rangkaian penerimaan P1 |
| M03 | Redirect tidak dapat melewati pembatasan | Belum tertutup untuk seluruh transport dan belum ada test hop aktual |

## 5. Urutan penyelesaian yang disarankan

1. **Tutup A01 dan A04:** jaga snapshot kredensial untuk mutasi, lalu lengkapi policy transport/redirect. Tambahkan test dengan header sintetis dan request yang dapat ditahan.
2. **Tutup A02 dan A03:** isolasi coroutine, continuation, cache, dan state per scope; tambah test respons sesi lama dan pembuatan ulang proses.
3. **Tutup A05 dan A06:** satukan jalur factory progresif serta kontrak subtitle native/external/Off.
4. **Tutup A07:** pisahkan error subtitle dan tetapkan perilaku DASH multi-period; uji source lokal terlebih dahulu.
5. **Tutup A08:** jalankan test integrasi serta skenario perangkat/akun yang relevan, simpan identitas source/APK/perangkat dan hasilnya, lalu evaluasi checklist M01–M03.

Prioritas ini membedakan implementasi yang belum lengkap dari verifikasi yang belum dilakukan. Tidak ada perubahan pada paket P2/P3 yang diperlukan hanya untuk memperbaiki label status audit P1.

## 6. Catatan pemeriksaan dan batas audit

- `git diff --check` masih melaporkan tiga trailing whitespace di `ShortsFeed.kt` baris 222, 256, dan 259, sesuai catatan progres yang sudah ada. Bagian tersebut tidak diubah pada audit dan bukan penjelasan untuk kriteria P1 yang belum terpenuhi.
- Source aplikasi dibandingkan dengan snapshot hash selama audit untuk memastikan laporan tidak disertai perubahan implementasi.
- Dokumentasi resmi digunakan untuk mengklarifikasi kontrak subtitle/track dan batas peran resolver. Ia tidak membuktikan perilaku APK di perangkat.
- Klaim kebocoran kredensial, pengiriman aksi ke akun salah, subtitle tetap tampil setelah Off, atau video berhenti akibat subtitle memerlukan reproduksi terarah. Laporan ini menunjukkan jalur kode yang belum menutup risiko/kriteria tersebut tanpa mengklaim kejadian nyata yang tidak diamati.
