# Analisis kekurangan FlufPlay — 4 November 2026

Kesimpulan: FlufPlay sudah memiliki cakupan fitur yang luas. Kekurangan yang perlu didahulukan adalah kelengkapan jalur subtitle, konsistensi sesi akun, pemulihan player, dan ketahanan pekerjaan unduh. Penambahan tampilan baru sebaiknya mengikuti pembenahan alur tersebut.

Analisis ini berdasarkan kode working tree di `/home/iqbal/Documents/flufclip/flufplay`, bukan hanya commit terakhir `ec47f85`. Checkout sudah memiliki banyak perubahan lokal sebelum pemeriksaan. Tidak dilakukan perubahan kode aplikasi, build, eksekusi test, instalasi APK, interaksi akun, atau pengujian perangkat. Temuan alur kode dibedakan dari dampak yang masih perlu direproduksi saat aplikasi berjalan.

Inventaris saat pemeriksaan: 49 file Kotlin produksi, 15.642 baris Kotlin produksi, dan 18 file unit test dengan 79 anotasi `@Test`. Jumlah ini adalah inventaris source, bukan hasil test lulus. Tidak ditemukan source `app/src/androidTest` atau workflow `.github/workflows` di checkout ini.

Beberapa kemampuan yang sudah terlihat di kode adalah Beranda akun melalui `HomeFeed.start()`, urutan Shorts dari YouTube dengan fallback pencarian, History akun, pelaporan posisi tontonan, playlist akun dan dialog simpan/buat playlist, pengiriman live chat, unduhan dengan pemeriksaan ukuran, serta player offline dan miniplayer. Kemampuan tersebut tidak tepat dicatat sebagai fitur yang sama sekali belum dibuat. Keberhasilan semua skenarionya tidak diuji ulang pada analisis ini.

Prioritas berikut adalah urutan perbaikan yang disarankan, bukan pernyataan bahwa semua dampaknya sudah terjadi pada perangkat.

| Prioritas | Kekurangan | Kepastian dari pemeriksaan |
| --- | --- | --- |
| P1 | Subtitle eksternal tidak diteruskan ke sumber HLS/DASH | Terlihat langsung pada konstruksi sumber media |
| P1 | Pengikatan request dan state terhadap sesi akun belum konsisten | Terlihat langsung; dampak saat ganti akun perlu reproduksi |
| P1 | Pemeriksaan tujuan header autentikasi di downloader terlalu longgar | Terlihat langsung; tidak membuktikan telah terjadi kebocoran |
| P2 | Status suka, simpan, dan subscribe belum selalu mencerminkan akun | Terlihat dari state awal dan sumber pembaruannya |
| P2 | Pemulihan error otomatis belum merata untuk video biasa | Cabang penanganan error dapat dipetakan dari kode |
| P2 | Pemulihan konteks player setelah proses mati belum tersedia | State utama masih hanya berada dalam komposisi |
| P2 | Pengelolaan unduhan belum memiliki antrean dan pemulihan pekerjaan | State aktif hanya di memori, layanan tidak meminta restart otomatis |
| P2 | Komentar biasa dan sebagian fungsi playlist masih belum lengkap | UI dan callback memperlihatkan batas fiturnya |
| P2 | Pemutaran background dan integrasi dari/ke luar aplikasi terbatas | Terlihat pada manifest, lifecycle, dan callback Cast |
| P2 | Pengujian integrasi dan pembagian tanggung jawab kode masih kurang | Inventaris test dan struktur aplikasi |
| P3 | Aksesibilitas seek, bahasa UI, dan dokumentasi perlu dirapikan | Terlihat pada composable dan README |

1. **P1 — Subtitle eksternal belum terhubung ke jalur HLS/DASH.**

   `playQuality()` membuat fungsi `media()` yang memasang `selectedSubtitle` lewat `setSubtitleConfigurations()`. Namun cabang HLS dan DASH memanggil `youTubeHlsSource()` atau `youTubeDashSource()` secara langsung. Kedua helper membuat `MediaItem` baru tanpa konfigurasi subtitle itu. Akibat yang didukung oleh kode: pilihan subtitle eksternal tidak masuk ke source pada jalur tersebut, walaupun state pilihan di UI berubah. Subtitle bawaan manifest adalah kasus terpisah; temuan ini tidak menyatakan semua jenis caption gagal.

   Bukti: [MainActivity.kt:777](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:777), [MainActivity.kt:787](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:787), [PlaybackNet.kt:65](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:65), dan callback CC di [MainActivity.kt:2347](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2347).

   Perbaikan yang disarankan: kirim konfigurasi subtitle yang sama ke semua pembentuk source. Verifikasi dengan VOD HLS, VOD DASH, progresif, serta pergantian bahasa dan Off; pastikan teks benar-benar tampil dan hilang.

2. **P1 — Penjagaan sesi akun belum diterapkan merata.**

   History akun dan `HomeFeed.accountRequest()` sudah memakai `sessionRevision`. Beranda utama masih dipicu oleh `LaunchedEffect(YouTubeAuth.isLoggedIn)`, sedangkan `loadHome()` meluncurkan pekerjaan melalui scope komposisi dan menulis hasil/cache tanpa memeriksa revisi sesi pemilik request. Logout memang membersihkan cache dan daftar langganan; masalahnya, respons lama yang masih berjalan tidak dijaga dengan pola revisi yang sama. Pergantian sesi yang tetap berstatus login juga tidak mengubah key boolean efek tersebut.

   Aksi suka dan subscribe memeriksa login, lalu mengambil header autentikasi global ketika request dieksekusi. Keduanya belum menerima revisi sesi yang diharapkan seperti alur Watch later. State penilaian dan langganan juga tidak di-reset berdasarkan revisi akun. Ini membuka kemungkinan hasil lama muncul setelah sesi berubah atau aksi tertunda berjalan menggunakan sesi yang berbeda. Skenario tersebut belum dicoba pada akun nyata.

   Bukti: [MainActivity.kt:802](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:802), [MainActivity.kt:867](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:867), [MainActivity.kt:883](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:883), [ShortsFeed.kt:102](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:102), dan [ShortsFeed.kt:179](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt:179). Pola penjagaan yang sudah tersedia: [HomeFeed.kt:127](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt:127).

   Perbaikan yang disarankan: ikat pekerjaan dan state akun ke revisi sesi; batalkan atau abaikan hasil sesi lama; tangkap identitas sesi sebelum operasi mutasi. Verifikasi logout ketika feed sedang dimuat dan perpindahan akun ketika aksi masih tertunda.

3. **P1 — Validasi tujuan pengiriman header autentikasi perlu diperketat.**

   Downloader NewPipe di `VideoRepository` memakai `reqUrl.contains("youtube.com")` dan pemeriksaan substring lain terhadap seluruh URL untuk menentukan apakah header akun boleh ditambahkan. Teks domain dapat muncul pada path, query, atau nama host lain. Pemeriksaan ini belum memastikan host tujuan yang sebenarnya. Tidak ditemukan bukti pengiriman ke pihak lain pada analisis ini; yang terbukti adalah pembatasan tujuan pada kode belum cukup ketat.

   Bukti: [VideoRepository.kt:70](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt:70). Jalur playback sudah memiliki pola pemeriksaan host yang lebih ketat di [PlaybackNet.kt:22](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackNet.kt:22).

   Perbaikan yang disarankan: parse URL, periksa skema dan host yang diizinkan, lalu batasi jenis header per tujuan. Uji URL yang hanya memuat teks nama domain di query/path dan pastikan header akun tidak dipasang.

4. **P2 — Status tindakan video belum selalu sesuai keadaan akun.**

   `videoRatings` dan `subscribedChannels` dimulai dari map kosong; kode bahkan menyebut status awal akun tidak diketahui. Tampilan memakai nilai default belum suka/belum subscribe. Shorts memiliki map rating tersendiri, sehingga perubahan di Shorts dan layar tonton belum menggunakan satu sumber state. Status Saved pada layar tonton berasal dari map lokal; informasi pilihan playlist baru dibaca ketika dialog Save dibuka.

   Dampaknya: video yang sudah disukai atau channel yang sudah dilanggani dapat tampak belum dipilih. Saat aplikasi dibuka ulang, state tindakan lokal tersebut juga kembali kosong. Pengiriman aksi sudah ada; kekurangannya adalah pembacaan, konfirmasi hasil, dan konsistensi state. `rate()` dan `setSubscribed()` juga belum mem-parsing konfirmasi mutasi dari isi respons sebagaimana `saveToWatchLater()` memeriksa status hasil.

   Bukti: [MainActivity.kt:524](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:524), [MainActivity.kt:2171](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2171), [ShortsScreen.kt:170](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt:170), dan [AccountLibraryUi.kt:103](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountLibraryUi.kt:103).

   Perbaikan yang disarankan: satu sumber state per akun/video/channel, baca status yang diberikan server, tampilkan status belum diketahui secara jujur, dan kendalikan request beruntun agar hasilnya tidak saling menimpa.

5. **P2 — Pemulihan error player belum merata.**

   Penanganan 403 yang otomatis menjalankan `refreshPlayback()` dibatasi pada live di luar Shorts. Untuk VOD biasa, handler membuang cache tetapi tidak langsung memulai resolve ulang; Shorts memakai alur recovery tersendiri. Tombol retry player memang sudah tersedia. Kekurangannya adalah kebijakan recovery yang berbeda-beda antarjenis video, bukan ketiadaan retry sama sekali.

   Helper retry membatasi jeda maksimum sampai 30 detik, tetapi mengulang selama media masih dianggap aktif tanpa batas jumlah percobaan. Untuk error permanen, perlu keputusan kapan menawarkan tindakan kepada pengguna dan kapan mencoba kembali setelah jaringan pulih.

   Bukti: [MainActivity.kt:1761](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:1761), [MainActivity.kt:2317](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2317), dan [PlaybackRecovery.kt:20](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlaybackRecovery.kt:20).

   Perbaikan yang disarankan: kebijakan recovery yang mencakup VOD, live, dan Shorts sesuai jenis error; pertahankan posisi serta pilihan audio/kualitas; berhenti atau tunggu jaringan untuk kegagalan permanen. Reproduksi perlu memakai sumber yang benar-benar gagal atau URL yang kedaluwarsa.

6. **P2 — Konteks sesi player belum dipulihkan setelah proses aplikasi mati.**

   Video terpilih, antrean, tab, dan status miniplayer disimpan dengan `remember`; player dibuat dari komposisi dan dilepas saat komposisi dibuang. `onCreate()` membaca extra `open_video`, tetapi belum memulihkan konteks sesi tersebut dari saved state atau snapshot sesi persisten. Penanganan `configChanges` dan PiP membantu kasus tertentu, tetapi tidak menyediakan pemulihan setelah proses mati.

   Posisi tontonan lokal sudah disimpan dan dipakai kembali saat video dibuka. Jadi kekurangannya adalah pemulihan konteks video/antrean terakhir, bukan hilangnya seluruh History.

   Bukti: [MainActivity.kt:240](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:240), [MainActivity.kt:312](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:312), [MainActivity.kt:465](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:465), [MainActivity.kt:580](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:580), dan [WatchHistory.kt:70](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/WatchHistory.kt:70).

   Perbaikan yang disarankan: simpan identitas video, posisi, antrean, dan konteks navigasi yang diperlukan. Saat dipulihkan, resolve ulang URL media; jangan menganggap URL stream lama tetap berlaku.

7. **P2 — Unduhan belum menjadi sistem pekerjaan yang dapat dilanjutkan.**

   Satu unduhan aktif diperbolehkan; permintaan berikutnya ditolak sampai selesai. `DownloadStatus` hanya berupa `MutableStateFlow` di memori. Layanan mengembalikan `START_NOT_STICKY`, menggunakan direktori sementara acak, lalu menghapus direktori itu pada penyelesaian normal/gagal. Tidak terlihat penyimpanan pekerjaan aktif untuk melanjutkan tugas setelah proses mati, fitur pause/resume, atau antrean beberapa video. Penghentian proses paksa juga tidak menjamin blok `finally` berjalan, sehingga pembersihan sisa tugas perlu dirancang untuk startup berikutnya.

   Penyalinan dan pemeriksaan ukuran output sudah ada. Temuan ini tidak mengulang anggapan bahwa hasil unduhan selalu 0 byte atau player offline belum tersedia.

   Preferensi audio Bahasa Indonesia belum dipakai untuk unduhan: selector masih `bestaudio` atau pasangan H.264 dengan `bestaudio`. Pengaturan Downloads hanya menampilkan informasi folder; belum ada pilihan folder, pengaturan jaringan, atau pengaturan pengelolaan ruang. Penghapusan dari daftar dengan jelas membiarkan file tetap di perangkat; pengelolaan file sebenarnya bisa menjadi tambahan berikutnya.

   Bukti: [MainActivity.kt:558](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:558), [DownloadService.kt:25](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:25), [DownloadService.kt:69](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:69), [DownloadService.kt:149](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt:149), dan [DownloadScreens.kt:108](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/DownloadScreens.kt:108).

   Perbaikan yang disarankan: dahulukan pencatatan pekerjaan, retry, antrean, dan pemulihan yang dapat diuji. Setelah itu terapkan preferensi audio dan opsi pengelolaan unduhan.

8. **P2 — Komentar biasa dan fungsi playlist masih sebagian.**

   Komentar biasa mendukung membaca, paginasi, dan membuka balasan. Belum ada composer untuk menulis komentar/balasan; tombol suka/tidak suka komentar secara eksplisit dinonaktifkan. Ini berbeda dari live chat yang sudah mempunyai jalur pengiriman pesan.

   Playlist akun sudah mendukung pembacaan dan sejumlah tindakan. Namun tombol unduh playlist hanya menampilkan pesan belum tersedia. Kontrol pengurutan Watch later juga hanya menampilkan pesan. Tombol Add videos mengarahkan pengguna memakai menu Save pada video.

   Bukti: [WatchPanels.kt:252](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/WatchPanels.kt:252), [WatchPanels.kt:344](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/WatchPanels.kt:344), [LiveChat.kt:679](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt:679), dan [AccountPlaylistScreens.kt:205](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/AccountPlaylistScreens.kt:205).

   Perbaikan yang disarankan: lengkapi tindakan yang memang akan didukung. Selama belum tersedia, gunakan status disabled atau penjelasan yang terlihat sebelum pengguna menekan tombol.

9. **P2 — Integrasi pemutaran di luar layar aplikasi masih terbatas.**

   Manifest hanya mendaftarkan layanan unduhan; belum ada layanan playback dengan sesi media untuk kontrol background/layar kunci. PiP sudah ada, tetapi tidak menggantikan layanan background. Intent filter launcher belum menerima tautan YouTube lewat VIEW/BROWSABLE atau teks share lewat SEND; pembukaan internal memakai extra `open_video`. Tombol Cast membuka pengaturan pencerminan layar sistem, belum membentuk sesi pengiriman video ke receiver Cast.

   Bukti: [AndroidManifest.xml:14](/home/iqbal/Documents/flufclip/flufplay/app/src/main/AndroidManifest.xml:14), [AndroidManifest.xml:22](/home/iqbal/Documents/flufclip/flufplay/app/src/main/AndroidManifest.xml:22), [MainActivity.kt:231](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:231), dan [MainActivity.kt:2319](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:2319).

   Ini adalah pengembangan produk lanjutan. Prioritaskan background audio dan penerimaan link jika keduanya sering dibutuhkan; integrasi Cast penuh bergantung pada tujuan produk.

10. **P2 — Pengujian integrasi dan struktur state belum mengikuti luas fitur.**

    Unit test sudah ada, termasuk parsing Library/History, pilihan audio, URL tracking, format unduhan, dan logika retry. Namun belum ditemukan test perangkat/Compose dalam checkout. Test helper tidak membuktikan subtitle tampil di ExoPlayer, akun yang benar menerima mutasi, unduhan pulih setelah proses mati, atau perpindahan online/offline tidak memutus sesi.

    `MainActivity.kt` mencapai 4.187 baris dan masih menampung state navigasi, akun, feed, antrean, playback, serta UI. Live chat sekitar 1.810 baris. Ukuran file bukan bukti aplikasi lambat, tetapi banyak tanggung jawab dalam satu scope membuat regresi lintas fitur lebih sulit dibatasi. Tidak dilakukan benchmark pada analisis ini.

    Bukti: [MainActivity.kt:257](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt:257), [PlaybackRecoveryTest.kt:8](/home/iqbal/Documents/flufclip/flufplay/app/src/test/java/id/flufclip/player/PlaybackRecoveryTest.kt:8), dan direktori [test](/home/iqbal/Documents/flufclip/flufplay/app/src/test/java/id/flufclip/player).

    Perbaikan yang disarankan: tambah pengujian yang membuktikan alur berisiko di atas; pisahkan state akun, feed, sesi playback, dan pekerjaan unduh secara bertahap sambil menjaga perilaku yang sudah bekerja. Tidak perlu melakukan penulisan ulang besar hanya untuk mengecilkan file.

11. **P3 — Aksesibilitas, bahasa, dan README belum konsisten.**

    Seek bar utama dan Shorts mengandalkan gambar Canvas serta gesture pointer. Belum terlihat semantics rentang progres dan aksi `setProgress` pada kontrol tersebut. Beberapa tombol lain sudah memiliki label semantics, sehingga masalahnya bukan semua kontrol tanpa label. Pengoperasian seek melalui TalkBack perlu diuji.

    Tidak ditemukan `strings.xml`; banyak teks langsung ditulis dalam Kotlin dan bercampur Indonesia/Inggris. Konfigurasi build membatasi resource bahasa ke `en`. Belum ada dasar lokalisasi UI yang konsisten.

    README masih menyatakan feed tidak dipersonalisasi dan tombol suka/tidak suka Shorts belum terhubung ke akun. Kode sekarang sudah mencoba feed akun dan mengirim aksi rating. Dokumentasi lama dapat menyesatkan penilaian kelengkapan aplikasi, meski keberhasilan seluruh aksi server tetap perlu pengujian tersendiri.

    Bukti: [PlayerControls.kt:517](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/PlayerControls.kt:517), [ShortsScreen.kt:582](/home/iqbal/Documents/flufclip/flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt:582), [app/build.gradle.kts:5](/home/iqbal/Documents/flufclip/flufplay/app/build.gradle.kts:5), [README.md:3](/home/iqbal/Documents/flufclip/flufplay/README.md:3), dan [README.md:7](/home/iqbal/Documents/flufclip/flufplay/README.md:7).

Urutan kerja yang disarankan: tuntaskan subtitle dan penjagaan sesi/header akun, benahi pembacaan status akun serta recovery VOD, tambahkan pemulihan sesi/pekerjaan unduh, lalu lengkapi komentar dan playlist. Background audio, penerimaan tautan, dan Cast dapat diprioritaskan menurut kebutuhan pemakaian. Pengujian integrasi mengikuti tiap perubahan, sedangkan aksesibilitas dan bahasa sebaiknya dibenahi pada kontrol yang disentuh.

Kinerja scroll, lama startup/buffering, suhu perangkat, kesamaan visual dengan YouTube, dan keberhasilan sinkronisasi akun tidak disimpulkan dari ukuran kode atau analisis statis ini. Hal-hal tersebut memerlukan pengamatan pada perangkat dan skenario yang ditentukan.
