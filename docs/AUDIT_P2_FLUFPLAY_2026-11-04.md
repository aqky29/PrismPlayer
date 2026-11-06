# Audit perubahan P2 FlufPlay — 4 November 2026

Dokumen acuan: [Rencana perbaikan FlufPlay](RENCANA_PERBAIKAN_FLUFPLAY_LENGKAP.md), paket M04 sampai M10. Bukti manual yang dibaca: [Testing.md](Testing.md).

**Status terbaru sesudah build 20:53:** P2-A01–P2-A04 sudah ditindaklanjuti pada source. Laporan otomatis mencatat 103 unit test lulus; pengguna telah menguji rekonsiliasi otomatis dan regresi M04 pada Xiaomi. Detail ada pada bagian 7 dan [Testing.md](Testing.md). M04 tetap terbuka untuk skenario khusus yang belum dilaporkan teruji, bukan karena empat perbaikan source tersebut belum dibuat. M05–M09 belum mendapat penerapan paket lanjutan sesuai rencana.

Bagian temuan, bukti 99 test, dan urutan perbaikan awal di bawah adalah catatan sebelum tindak lanjut. Bagian 7 menjadi acuan status source dan verifikasi yang lebih baru; temuan historis tidak dianggap otomatis masih berlaku sesudah patch.

Audit membaca working tree, source test, XML hasil test/lint yang sudah ada, serta catatan uji pengguna. Tidak dilakukan perubahan kode aplikasi, build, eksekusi ulang test, instalasi, pengujian akun, atau pengujian perangkat. Dokumen audit dan ringkasan pada rencana adalah satu-satunya keluaran perubahan tugas ini.

HEAD masih `ec47f85`; banyak perubahan lokal dan file baru belum menjadi commit terpisah. Karena itu, seluruh diff terhadap HEAD tidak diasumsikan sebagai perubahan P2. Snapshot source dari audit P1 dipakai sebagai pembanding terbatas; selisihnya juga mencakup tindak lanjut P1 yang dijelaskan dalam rencana.

## 1. Status paket P2

| Paket | Status audit | Yang sudah ada | Yang belum menutup rencana |
| --- | --- | --- | --- |
| M04 — engagement akun | Perbaikan P2-A01–A04 sudah diterapkan; verifikasi sebagian skenario masih terbuka | Repository bersama, refresh otoritatif/generation, abandon pending, fetch Short aktif, membership playlist/Watch later, rekonsiliasi otomatis; uji Xiaomi build 20:53 | Request tertahan lalu layar ditutup, respons putus sesudah mutasi diterima, perubahan dari aplikasi YouTube, Shorts ↔ Watch, dan kombinasi multi-playlist |
| M05 — recovery | Belum penerapan paket lanjutan | Recovery live, retry manual, helper penantian READY, perlindungan Auto HLS | Anggaran total retry, klasifikasi error menyeluruh, recovery otomatis VOD dan generation sesi |
| M06 — pemulihan sesi | Belum penerapan paket lanjutan | History/posisi lokal; beberapa state UI Downloads memakai `rememberSaveable` | Snapshot sesi/antrean online berversi dan pemulihan setelah proses mati |
| M07 — pekerjaan unduh | Belum penerapan paket lanjutan | Foreground download, copy/verifikasi/publish, daftar hasil dan player offline | Job store, antrean persisten, pause/resume, rekonsiliasi crash, bahasa audio dan pengaturan nyata |
| M08 — komentar/playlist | Belum penerapan paket lanjutan | Baca komentar/balasan, Library dan sejumlah tindakan playlist | Composer/rating komentar, pengurutan yang masih placeholder, batch unduh playlist |
| M09 — integrasi playback | Belum penerapan paket lanjutan | PiP, miniplayer, pembukaan internal, pencerminan layar lewat pengaturan sistem | MediaSessionService, kontrol background, intent link/share masuk, Cast media penuh |
| M10 — test/struktur | Sebagian | 103 hasil unit test tersedia sesudah tindak lanjut; ekstraksi engagement; satu test redirect HTTP lokal | Test integrasi Media3/penyimpanan/lifecycle/Compose, CI, dan pemisahan tanggung jawab berikutnya |

Label belum penerapan paket lanjutan tidak berarti semua fitur dasarnya tidak ada. Kekurangan M05–M09 pada bagian 4 merupakan pekerjaan rencana yang tersisa, bukan seluruhnya regresi akibat perubahan M04.

## 2. Kemajuan M04 yang dapat dikonfirmasi

1. **Satu sumber state untuk beberapa layar sudah dibuat.** `EngagementRepository` dipakai Watch, fullscreen, Shorts, dan player offline; kuncinya membawa revisi akun. Bukti: [EngagementRepository.kt:80](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:80), [MainActivity.kt:2256](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2256), [ShortsScreen.kt:234](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt:234), dan [DownloadedWatch.kt:83](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadedWatch.kt:83).
2. **Parser status awal server sudah berkembang.** `EngagementSource` berada dalam file `EngagementRepository.kt`, bukan file produksi terpisah. Parser mendukung renderer lama, toggle view model, entity mutation, dan `buttonState`. Bukti: [EngagementRepository.kt:249](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:249) dan [EngagementSourceTest.kt](/home/iqbal/Documents/flufclip/flufplay/app/src/test/java/id/flufclip/player/EngagementSourceTest.kt).
3. **Koalesensi ketukan sudah menangani alur sukses.** Ketukan saat pending mengubah niat tampilan; satu request berjalan, lalu follow-up mengirim niat terakhir. Bukti: [EngagementRepository.kt:134](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:134) dan [EngagementRepositoryTest.kt:56](/home/iqbal/Documents/flufclip/flufplay/app/src/test/java/id/flufclip/player/EngagementRepositoryTest.kt:56). Ini menggantikan pendekatan awal tombol dinonaktifkan saat pending, sesuai tindak lanjut pengguna.
4. **Pemeriksaan hasil operasi memakai revision dan operation ID.** Respons sesi lama tidak langsung diterapkan ke state sesi baru. Delta suka dihitung dari baseline yang diketahui. Bukti: [EngagementRepository.kt:156](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:156).
5. **Dialog Save sudah memberikan respons visual segera.** Pilihan dibalik secara optimistis, dikembalikan ketika exception ditangani, dan tidak di-reload setelah setiap klik. Bukti: [AccountLibraryUi.kt:137](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountLibraryUi.kt:137).
6. **Ada catatan pengujian perangkat nyata.** [Testing.md:16](/home/iqbal/Documents/flufclip/docs/Testing.md:16) mencatat perbaikan status awal, Watch/fullscreen setelah restart, ketukan cepat, Save, serta skenario offline dan pergantian akun pada player offline. Catatan tersebut dihargai sebagai bukti skenario yang dicoba; audit ini tidak mengulang atau memperluas cakupannya ke semua jaringan/lifecycle/perangkat.

## 3. Temuan audit awal pada implementasi M04

Status tindak lanjut keempat temuan ini ada di bagian 7. Uraian berikut mempertahankan kondisi kode ketika temuan pertama kali ditulis.

### P2-A01 — Operasi dapat tertinggal pending ketika composable pemiliknya ditutup

**Prioritas: P2, perlu didahulukan sebelum menambah fitur engagement berikutnya.** Ini adalah celah lifecycle yang ditunjukkan oleh alur kode, belum direproduksi pada perangkat dalam audit ini.

**Bukti:** [DownloadedWatch.kt:55](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadedWatch.kt:55), [DownloadedWatch.kt:119](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadedWatch.kt:119), [DownloadedWatch.kt:135](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadedWatch.kt:135), [DownloadsActivity.kt:173](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadsActivity.kt:173), dan [ShortsFeed.kt:110](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:110).

Repository menandai pending sebelum coroutine request dijalankan. Pada player offline, coroutine memakai `rememberCoroutineScope()` milik `DownloadedWatch`. Meminimalkan player membuat composable tersebut keluar dari cabang UI. Scope itu dibatalkan ketika meninggalkan komposisi, sesuai [kontrak Compose](https://developer.android.com/develop/ui/compose/side-effects).

`sendRating()`/`sendSubscription()` dapat meneruskan `CancellationException`, sedangkan loop pemanggil tidak mempunyai jalur pembatalan/finalisasi yang melepaskan pending di repository. Instance `Engagement` tetap hidup selama proses aplikasi.

Urutan yang masih terbuka:

1. Mulai Like/Subscribe pada player offline; state menjadi pending.
2. Tahan request lalu minimalkan/tutup layar sebelum hasil diterapkan.
3. Coroutine dibatalkan sebelum `completeRating()`/`completeSubscription()`.
4. Buka lagi target pada sesi yang sama. Pembacaan awal ditolak oleh `seed()` selama pending; ketukan berikutnya hanya mengubah niat dan tidak memulai request baru karena repository menganggap request lama masih berjalan.

Alur Watch pada [MainActivity.kt:541](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:541) juga perlu diaudit terhadap pembatalan scope induknya. Pengujian ketukan cepat yang sukses tidak mencakup kasus ini.

**Perbaikan yang diperlukan:** beri pemilik operasi lifetime yang sesuai atau finalisasi pembatalan berdasarkan operation ID. Bila request mungkin sudah dikirim, statusnya menjadi belum pasti dan perlu read-back; jangan menyatakan penolakan yang belum diketahui atau membiarkan pending selamanya.

**Uji penutup:** tahan request dengan test transport, minimalkan player offline/keluar layar, lalu buka kembali. Pastikan pending tidak terkunci, state direkonsiliasi, tidak ada request ganda, dan audio/file lokal tetap berjalan sesuai niat pengguna.

### P2-A02 — Pembacaan ulang server tidak dapat memperbaiki nilai known/uncertain yang tersimpan

**Prioritas: P2.** Ini melampaui catatan lama bahwa read-back otomatis belum ada: read-back yang dilakukan ketika video dibuka ulang juga belum mempunyai jalur untuk mengganti nilai yang sudah diketahui.

**Bukti:** [EngagementRepository.kt:100](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:100), [EngagementRepository.kt:156](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:156), [EngagementRepository.kt:201](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:201), dan [MainActivity.kt:2228](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2228).

Saat hasil Uncertain, repository mengembalikan nilai tampilan ke `confirmed` dan menyetel `uncertain = true`. Namun `seed()` hanya menerima nilai rating jika entri belum ada atau nilai saat ini null dan tidak pending. Subscription memakai pembatasan serupa. Nilai lama yang non-null tetap menolak snapshot server berikutnya, bahkan ketika uncertain sudah true. Save awal juga hanya di-seed ketika belum diketahui.

Contoh urutan dari kode: server awal 0, pengguna Like, server menerima perubahan menjadi 1 tetapi respons terputus, lalu repository kembali menampilkan 0/uncertain. Saat video dibuka ulang pada proses dan sesi yang sama, fetch dapat mendapat 1, tetapi `seed()` mengabaikannya karena nilai lokal 0 sudah diketahui. Perubahan dari aplikasi YouTube pada sesi yang sama juga tidak mempunyai pembaruan otoritatif untuk nilai known tersebut.

Test [EngagementRepositoryTest.kt:118](/home/iqbal/Documents/flufclip/flufplay/app/src/test/java/id/flufclip/player/EngagementRepositoryTest.kt:118) justru mempertahankan aturan bahwa seed baru tidak menimpa nilai yang sudah diketahui. Perlindungan terhadap respons awal yang terlambat memang diperlukan, tetapi perlu dibedakan dari pembacaan ulang yang lebih baru dan otoritatif.

Transport rating/subscription juga langsung mengembalikan Confirmed setelah `accountRequest()` selesai tanpa root error; belum ada pembacaan ulang atau parser konfirmasi khusus tindakannya. Bukti: [ShortsFeed.kt:105](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:105) dan [HomeFeed.kt:140](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt:140). Ini tidak menyangkal keberhasilan mutasi pada uji pengguna; kontrak respons yang tidak dikenal masih perlu dicakup agar label Confirmed mempunyai dasar yang jelas.

**Perbaikan yang diperlukan:** pisahkan initial seed dari refresh/read-back otoritatif, dengan revision dan generation agar hasil lama tidak menimpa intent baru. Jadwalkan rekonsiliasi setelah uncertain dan ketika data dinyatakan stale. Tampilkan status belum pasti secara nyata; properti uncertain saat ini belum diteruskan ke model kontrol UI.

**Uji penutup:** server menerapkan tindakan tetapi koneksi diputus sebelum respons; read-back berikutnya harus diterima dan membersihkan uncertain. Ulangi untuk subscription, perubahan dari luar aplikasi, dan refresh yang tiba setelah operasi baru.

Catatan [Testing.md:59](/home/iqbal/Documents/flufclip/docs/Testing.md:59) bahwa pengguna perlu membuka ulang video belum dapat dianggap solusi umum untuk kasus nilai known dalam proses/sesi yang sama. Restart yang membunuh proses dan mengosongkan singleton adalah kasus berbeda.

### P2-A03 — Status awal Shorts belum dimuat; unknown masih ditampilkan seperti Off

**Prioritas: P2; sebagian sudah dicatat sebagai pekerjaan terbuka pada Testing.md.**

**Bukti:** [MainActivity.kt:2216](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2216), [MainActivity.kt:2228](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2228), [ShortsScreen.kt:234](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt:234), dan [MainActivity.kt:2261](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2261).

Fetch status awal engagement berada pada cabang Watch `!shortsMode`; player offline memiliki fetch tersendiri. Shorts membaca repository bersama tetapi belum melakukan fetch status awal untuk target yang belum pernah dibaca lewat layar lain.

Model repository sudah mengizinkan null sebagai unknown. Namun Shorts mengubah null menjadi rating 0, dan `WatchEngagement` menerima `rating ?: 0`, `saved == true`, serta `subscribed == true`. Karena model UI itu hanya membawa Int/Boolean, keadaan unknown tidak dibedakan secara lengkap dari belum suka/belum simpan/belum subscribe.

**Perbaikan yang diperlukan:** muat status untuk Short aktif dengan pembatasan request/prefetch yang sesuai dan scope yang benar. Teruskan loading/unknown/applied/uncertain ke kontrol UI. Jangan menandai status akun sebagai Off yang sudah dipastikan ketika respons belum tersedia.

**Uji penutup:** buka langsung Short yang sudah disukai di akun tanpa pernah membukanya di Watch; bandingkan Watch, Shorts, fullscreen, dan player offline untuk target yang sama. Uji juga respons lambat, gagal, dan akun berubah selama pemuatan.

### P2-A04 — Save masih berupa ringkasan Boolean dan belum merekonsiliasi hasil ambigu

**Prioritas: P2; pemisahan Watch later memang masih terbuka dalam rencana.**

**Bukti:** [EngagementRepository.kt:37](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/EngagementRepository.kt:37), [AccountLibraryUi.kt:137](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountLibraryUi.kt:137), [DownloadedWatch.kt:162](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadedWatch.kt:162), dan [MainActivity.kt:1560](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1560).

Ringkasan `SaveState.saved` berarti ada membership pada playlist mana pun. Ia belum menyimpan membership Watch later maupun per-playlist sebagai state bersama yang eksplisit. Watch membuka pilihan playlist, sedangkan Save dari player offline menambahkan langsung ke Watch later, walaupun chip membaca ringkasan any-playlist yang sama.

Perubahan optimistis dialog sudah menyelesaikan keluhan visual yang dicatat pengguna. Tetapi ketika request Save mungkin sudah diterima lalu koneksi terputus, catch umum mengembalikan centang dan ringkasan ke nilai sebelumnya. Tidak ada klasifikasi uncertain maupun read-back terjadwal untuk kasus tersebut. Reload pasca-klik sengaja dihilangkan; penggantinya perlu rekonsiliasi yang tidak menimpa intent baru dan tidak bergantung pada respons baca yang belum konsisten secara langsung.

**Perbaikan yang diperlukan:** bedakan membership per-playlist/Watch later/ringkasan, simpan status pending/uncertain untuk operasi terkait, dan lakukan pembacaan ulang terkendali setelah hasil ambigu. Pertahankan respons visual segera yang sudah diuji pengguna.

**Uji penutup:** video hanya tersimpan di playlist biasa, hanya di Watch later, di beberapa playlist, serta server sudah menerapkan Save/remove sebelum respons terputus. Pastikan kontrol online/offline mempunyai arti yang jelas dan centang akhirnya sesuai server tanpa mengirim ulang tindakan secara buta.

## 4. Pekerjaan P2 lanjutan yang belum diterapkan

### M05 — Recovery belum memiliki batas total dan cakupan VOD sesuai rencana

`retryPlaybackRecovery()` masih mengulang selama `stillCurrent()` benar. Nilai 30 detik adalah batas jeda, bukan batas durasi total atau jumlah percobaan. Handler otomatis 403 masih dibatasi live; VOD biasa terutama menghapus cache dan mengandalkan retry yang tersedia.

Bukti: [PlaybackRecovery.kt:20](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackRecovery.kt:20) dan [MainActivity.kt:1829](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1829).

Belum ada penutupan kriteria tiga percobaan/60 detik, klasifikasi error lengkap, dan identitas generation playback yang lebih kuat daripada video ID. Helper recovery READY dan perlindungan Auto HLS yang sudah ada tetap menjadi fondasi yang perlu dipertahankan.

### M06 — Belum ada snapshot konteks sesi/antrean online yang persisten

`selected` dan `queue` masih memakai `remember`; `onCreate()` membaca extra pembukaan internal, bukan snapshot sesi berversi. Posisi History lokal dan beberapa `rememberSaveable` di Downloads sudah ada; keduanya tidak membuktikan seluruh konteks/antrean online pulih setelah proses mati.

Bukti: [MainActivity.kt:240](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:240), [MainActivity.kt:314](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:314), dan [MainActivity.kt:476](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:476).

Belum ditemukan store snapshot, migrasi schema sesi, atau test proses mati yang menutup M06. Riwayat dan posisi yang sudah tersimpan tidak dinyatakan hilang oleh temuan ini.

### M07 — Unduhan masih satu tugas di memori

Service masih menolak unduhan baru ketika running, memakai `DownloadStatus` di memori, direktori sementara acak, dan `START_NOT_STICKY`. Tidak ditemukan job store, antrean persisten, kontrol pause/resume, atau rekonsiliasi setelah crash. Selector audio masih `bestaudio`, belum memakai `AudioPreference`. Pengaturan Downloads masih menampilkan informasi folder.

Bukti: [DownloadService.kt:25](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:25), [DownloadService.kt:38](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:38), [DownloadService.kt:77](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:77), dan [DownloadScreens.kt:108](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadScreens.kt:108).

Copy, verifikasi, publish, metadata hasil, serta player offline sudah ada. Laporan pengguna bahwa download aman pada skenario yang dicoba tidak membuktikan antrean/resume/crash recovery yang belum dibuat.

### M08 — Penulisan komentar dan sejumlah kontrol playlist masih terbuka

Komentar biasa masih untuk membaca dan membuka balasan; rating komentar dinonaktifkan. Unduh playlist serta fallback pengurutan Watch later masih menampilkan pesan belum tersedia. Filter server tertentu dan sebagian tindakan Library yang sudah ada tidak dihitung sebagai fitur yang hilang.

Bukti: [WatchPanels.kt:344](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/WatchPanels.kt:344), [AccountPlaylistScreens.kt:208](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountPlaylistScreens.kt:208), dan [AccountPlaylistScreens.kt:225](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountPlaylistScreens.kt:225).

Belum ditemukan composer/mutasi komentar maupun batch playlist yang memakai pekerjaan persisten. Pengiriman live chat merupakan jalur berbeda dan sudah tersedia sebelumnya.

### M09 — Background session, intent masuk, dan Cast media belum dibuat

Manifest masih hanya mendaftarkan service unduhan dan intent filter launcher. Belum ada layanan playback/MediaSession atau penerimaan VIEW/BROWSABLE/SEND dari luar. Pembukaan melalui extra internal serta share keluar tidak menggantikan parser/routing share masuk. Tombol Cast masih membuka pengaturan sistem untuk pencerminan layar.

Bukti: [AndroidManifest.xml:14](/home/iqbal/Documents/flufclip/flufplay/app/src/main/AndroidManifest.xml:14), [MainActivity.kt:231](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:231), dan [MainActivity.kt:2410](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2410).

PiP dan miniplayer yang sudah berfungsi tetap diakui. Tidak ada bukti receiver Cast media atau kontrol layar kunci yang menutup paket M09.

### M10 — Unit test bertambah; integrasi dan kepemilikan operasi belum lengkap

Ada 22 file unit test dengan laporan 99 kasus lulus, termasuk 11 test repository engagement dan 6 test parser engagement. Satu test jaringan HTTP lokal untuk interceptor juga sudah ada pada [AccountHeaderRedirectTest.kt:13](/home/iqbal/Documents/flufclip/flufplay/app/src/test/java/id/flufclip/player/AccountHeaderRedirectTest.kt:13); jangan mengulang penilaian lama bahwa tidak ada test jaringan sama sekali.

Belum ditemukan `app/src/androidTest` atau workflow CI di checkout. Test lifecycle pembatalan, read-back yang otoritatif, subtitle/player Media3, dan crash job store belum tersedia. `MainActivity.kt` sekarang sekitar 4.271 baris dan masih menjalankan loop mutasi UI sendiri; sebagian loop yang sama muncul pada player offline. Ukuran file tidak membuktikan masalah performa, tetapi pembagian kepemilikan operasi ini berkaitan langsung dengan P2-A01.

## 5. Penilaian bukti pada audit awal

Bagian ini mencatat artifact sebelum patch tindak lanjut. Hasil 103 test serta lint/build 20:53 pada bagian 7 menggantikan status verifikasi lama; catatan umur XML lint di tabel ini bukan lagi kesimpulan untuk patch terbaru.

| Bukti | Hasil yang terbaca | Batas pembuktian |
| --- | --- | --- |
| XML unit test | 99 kasus, 0 failure, 0 error, 0 skipped; 22 suite | Dibaca, tidak dijalankan ulang; tidak membuktikan seluruh skenario perangkat/akun |
| Suite engagement | Repository 11 kasus; parser 6 kasus | Belum mencakup pembatalan composable dan penerapan refresh known/uncertain |
| `Testing.md` A–G | Mencatat beberapa skenario Xiaomi lulus setelah perbaikan berurutan | Bukan semua format/jaringan/lifecycle; Shorts terhadap Watch untuk target sama masih terbuka |
| XML lint | 85 Warning, tidak ada issue Error/Fatal; mtime sekitar 16:41 WIB | Lebih tua daripada perubahan Save di `AccountLibraryUi.kt` sekitar 20:10 WIB; belum menjadi bukti lint untuk patch final itu |
| APK debug | Artifact tersedia dengan mtime sekitar 20:12 WIB; catatan menyebut build berhasil | Keberadaan/waktu APK tidak mengikat seluruh source ke perangkat atau membuktikan alur baru |
| Snapshot source audit | 353 file aplikasi/konfigurasi yang relevan difingerprint | Digunakan untuk memastikan audit tidak mengubah implementasi, bukan hasil test fungsi |

Timestamp laporan unit berada pada 4 November 2026 sekitar 13:11 UTC pada atribut XML. Catatan build/test yang sudah ada tetap dicatat sebagai riwayat; audit tidak mengklaim menjalankan ulang Gradle. Untuk menyebut lint patch Save final terverifikasi, perlukan hasil yang dapat dikaitkan ke source setelah perubahan tersebut.

Catatan manual A/B/C/E yang dahulu gagal telah ditindaklanjuti dan dicatat lulus. Temuan P2-A01–A04 tidak membatalkan hasil itu; ia mencakup urutan kejadian tambahan yang belum ditutup oleh skenario tersebut.

## 6. Urutan tindak lanjut dan uji penutup

Urutan berikut berasal dari audit awal. Pekerjaan source pada butir 1–4 sudah ditindaklanjuti sebagaimana dicatat di bagian 7; uji penutupnya tetap dinilai sesuai bukti perangkat yang tersedia.

| Urutan | Pekerjaan | Uji minimum untuk menutup |
| --- | --- | --- |
| 1 | Selesaikan kepemilikan/finalisasi operasi M04 | Request tertahan, minimize/keluar player offline, kembali dan ketuk lagi; pending tidak terkunci |
| 2 | Pisahkan seed dan refresh otoritatif | Server menerima tindakan lalu respons putus; read-back diterima, uncertain dibersihkan, respons lama tidak menimpa intent baru |
| 3 | Lengkapi status awal Shorts dan model UI | Short sudah disukai, belum pernah dibuka di Watch; loading/unknown dibedakan dan nilai akhirnya konsisten |
| 4 | Pisahkan membership serta rekonsiliasi Save | Any-playlist, Watch later, multi-playlist, timeout setelah accepted, perubahan dari luar aplikasi |
| 5 | Verifikasi M04 setelah patch final | Unit test baru, lint yang sesuai patch, integrasi lifecycle, lalu uji perangkat terbatas sesuai skenario |
| 6 | Kerjakan M05 lalu M06 | Budget retry, VOD/live/Shorts, Pause saat recovery, proses mati dan intent baru |
| 7 | Kerjakan M07 dan lanjutkan M08 | Durable job/crash/resume sebelum batch unduh playlist; komentar memakai kontrak yang terbukti |
| 8 | Kerjakan M09 dan lanjutkan M10 | Pemilik player tunggal, kontrol sistem/intent/receiver; test integrasi mengikuti tiap tahap |

Checklist M04–M10 pada rencana tetap terbuka. Bukti uji yang sudah ada dipertahankan; penyelesaian berikutnya dinilai berdasarkan kriteria paket dan uji penutup di atas.

## 7. Tindak lanjut implementasi setelah audit — 4 November 2026

Temuan P2-A01 sampai P2-A04 sudah ditindaklanjuti pada source. Dokumen ini tetap menjadi catatan audit titik-waktu; bagian berikut mencatat perubahan sesudahnya.

- **P2-A01 — operasi tertinggal pending:** repository kini menyediakan `abandonRating`/`abandonSubscription`. Loop pemanggil memakai `try/finally`; bila coroutine dibatalkan (misalnya player offline diminimalkan), pending dilepas, nilai kembali ke `confirmed`, dan status ditandai `uncertain` karena request mungkin sudah terkirim. Ketukan berikutnya tidak lagi terkunci, dan pembacaan server berikutnya dapat merekonsiliasi.
- **P2-A02 — refresh tidak dapat memperbaiki nilai known/uncertain:** ditambahkan `EngagementGeneration` dan `refresh()`. Generasi diambil sebelum pembacaan server; hasil hanya diterapkan bila tidak ada operasi lokal yang lebih baru dan tidak sedang pending. Berbeda dari `seed()`, `refresh()` boleh mengganti nilai yang sudah diketahui dan menghapus `uncertain`. Walk, player offline, dan Short memakai jalur ini. `uncertain` kini tampil sebagai penanda `?`/`Subscribed?` pada kontrol. Setelah hasil `uncertain`, pembacaan ulang server dijadwalkan otomatis (1,5 detik lalu 4 detik) tanpa mengirim ulang tindakan; bila berhasil, tanda `?` hilang dan nilai mengikuti server.
- **P2-A03 — status awal Shorts:** Short aktif memuat status akunnya sendiri dengan jeda 300 ms, hanya bila statusnya belum diketahui atau `uncertain`. Watch dan fullscreen tetap memakai repository yang sama.
- **P2-A04 — Save masih Boolean dan hasil ambigu:** `SaveState` kini menyimpan `playlistIds` dan `watchLater` terpisah dari ringkasan `saved`. Dialog Save melaporkan ID playlist yang dipilih; jalur Watch later menandai `WL` secara eksplisit. Pada kegagalan mutasi playlist, centang dikembalikan lalu dialog membaca ulang server sebagai rekonsiliasi, tanpa mengirim ulang tindakan secara buta.

**Verifikasi otomatis:** 103 unit test lulus (0 failure/error), termasuk test baru untuk refresh otoritatif, penolakan respons basi, pelepasan pending yang dibatalkan, serta pemisahan playlist/Watch later. `:app:lintDebug` dan `:app:assembleDebug` lulus pada 4 November 2026 sekitar 20:53 WIB. APK build 20:53 sudah disalin dan diuji pada perangkat Xiaomi: status `Subscribed?` hilang setelah pembacaan ulang, like saat sinyal hilang tidak menghasilkan sukses palsu, dan regresi ketukan cepat/restart/Save dinyatakan aman oleh pengguna. Skenario P2-A01 yang menahan request lalu menutup layar secara khusus belum dilaporkan terpisah.

**Batas bukti:** rekonsiliasi otomatis `Subscribed?` dan regresi like/ketukan cepat/restart/Save sudah dilaporkan lulus pada build 20:53. Yang belum dilaporkan terpisah adalah lifecycle P2-A01 (request sengaja ditahan lalu layar ditutup), server menerima mutasi lalu respons diputus secara terkontrol, perubahan dari aplikasi YouTube, status awal Short/Shorts ↔ Watch untuk target sama, dan kombinasi Save multi-playlist. Checklist M04 tetap terbuka untuk bukti khusus tersebut.
