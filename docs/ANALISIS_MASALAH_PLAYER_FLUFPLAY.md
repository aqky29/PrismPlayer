# Analisis masalah pemutar FlufPlay

**Tanggal:** 2026-11-01  
**Cakupan:** pemeriksaan statis source Kotlin/Compose dan inventaris aset youtube-21-43-523.apk. Tidak ada kode yang diubah dan aplikasi tidak dijalankan di perangkat pada analisis awal.  
**Pembaruan:** bagian "Verifikasi ulang dan perbaikan" di bawah mencatat koreksi referensi, temuan tambahan, dan perbaikan yang sudah diterapkan.

## Ringkasan

Temuan utama dari source:

1. Loading setelah mengetuk video mencakup resolusi metadata dan URL stream. Pemutar baru dipersiapkan setelah proses itu selesai. Jalur video biasa tidak memakai cache resolusi.
2. Sebagian ikon Shorts memakai ikon Material atau vektor hati buatan, meskipun aset ikon YouTube yang berbeda tersedia. Bar aksi juga menetapkan empat aksi dan satu tint secara seragam.
3. Avatar Shorts mengambil URL dari hasil resolusi NewPipe. Jika URL kosong atau gambar gagal dimuat, komponen sengaja menampilkan huruf awal nama.
4. Progress live dihitung seperti progress video dengan durasi tetap. Selain itu, jalur fallback tertentu selalu memberi tipe stream VIDEO_STREAM; hal ini dapat membuat tampilan waktu dan Live Chat memperlakukan siaran langsung sebagai video biasa.

## 1. Loading awal setelah video diketuk

Pada video biasa, open(item) memanggil openUrl(). Halaman sementara dan spinner tampil, lalu aplikasi menunggu VideoRepository.resolve() sebelum memanggil startInfo() dan player.prepare(). Urutannya ada di [MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L1093) dan [VideoRepository.kt](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt#L274).

VideoRepository.resolve() terlebih dahulu menjalankan StreamInfo.getInfo(url). Source tidak menunjukkan cache atau prefetch resolusi untuk video biasa. Shorts mempunyai ShortsResolver dengan cache dan prefetch, tetapi jalur itu terpisah ([ShortsResolver.kt](../flufplay/app/src/main/java/id/flufclip/player/ShortsResolver.kt#L16)).

Jika resolusi utama gagal atau tidak memberikan stream, fallback hanya dapat berjalan saat akun login. Fallback yt-dlp dapat menginisialisasi binary, memeriksa pembaruan mingguan, lalu meminta info video; jalur InnerTube dapat membuat permintaan tambahan. Semua terjadi sebelum pemutar dimulai ([VideoRepository.kt](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt#L286)).

**Kesimpulan statis:** spinner tepat setelah ketukan bisa menandakan aplikasi masih mencari URL stream, belum tentu ExoPlayer sedang mengisi buffer. Setelah prepare() dipanggil, buffering media menjadi tahap terpisah. Source memakai ExoPlayer dengan builder standar dan tidak mengatur LoadControl khusus ([MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L606)).

## 2. Ikon Shorts berbeda dari APK referensi

### Temuan source

- Header Shorts memakai ikon Material Search dan MoreVert, bukan aset khusus Shorts ([ShortsScreen.kt](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt#L257)).
- Tombol Suka memakai vektor yt_short_heart dan yt_short_heart_filled. File yt_short_like.webp memang tersedia di resource project, tetapi tidak dipakai oleh tombol tersebut ([ShortsScreen.kt](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt#L402), [yt_short_like.webp](../flufplay/app/src/main/res/drawable-mdpi/yt_short_like.webp)).
- Bar aksi saat ini menetapkan empat tombol—Suka, Tidak suka, Komentar, dan Bagikan. Ikon digambar berukuran 32 dp dan menerima tint dari pemanggil ([ShortsScreen.kt](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt#L402), [ShortsAction](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt#L605)).

### Perbandingan inventaris APK

Tabel resource APK youtube-21-43-523.apk memuat reel_search_bold_24dp, reel_more_vertical_bold_24dp, ic_right_like_off_shadowed, ic_right_like_on_shadowed, ic_right_dislike_off_shadowed, ic_right_dislike_on_shadowed, ic_right_comment_shadowed, dan ic_right_share_shadowed. Tabel itu juga memuat ic_remix_filled_white_shadowed.

**Kesimpulan statis:** pemilihan ikon, aset status aktif/nonaktif, dan jumlah tombol di FlufPlay tidak identik dengan keluarga resource yang ada di APK referensi. Inventaris resource saja tidak membuktikan bahwa setiap tombol APK tampil pada semua siaran atau video.

## 3. Avatar channel Shorts kadang menjadi huruf

ShortsFeed.item() membentuk item awal dari ID video dan thumbnail saja; item itu tidak mengisi nama channel atau avatar ([ShortsFeed.kt](../flufplay/app/src/main/java/id/flufclip/player/ShortsFeed.kt#L185)). Setelah resolusi, ShortPage mengambil avatar dari info.uploaderAvatars.lastOrNull() ([ShortsScreen.kt](../flufplay/app/src/main/java/id/flufclip/player/ShortsScreen.kt#L386)).

Komponen Avatar menampilkan huruf pertama nama bila URL avatar kosong atau permintaan gambarnya gagal. Shorts tidak memanggil helper VideoRepository.channelAvatar() sebagai fallback, walaupun helper itu tersedia untuk mengambil avatar dari halaman channel ([WatchPanels.kt](../flufplay/app/src/main/java/id/flufclip/player/WatchPanels.kt#L498), [VideoRepository.kt](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt#L173)).

APK referensi memiliki resource ic_default_channel_placeholder; source Shorts memakai huruf sebagai placeholder-nya.

**Yang belum dapat dibedakan dari source saja:** pada kejadian tertentu, URL bisa saja tidak diberikan extractor atau permintaan gambar bisa gagal. Keduanya menghasilkan fallback huruf yang sama.

## 4. Progress live dan Live Chat

### Progress bar

Kontrol player membaca player.currentPosition dan player.duration tiap 200 ms. Nilai progress kemudian digambar sebagai posisi dibagi durasi. Untuk live dengan durasi player yang berubah atau pendek, rasio itu dapat meloncat atau terlihat seperti durasi hanya beberapa detik ([PlayerControls.kt](../flufplay/app/src/main/java/id/flufclip/player/PlayerControls.kt#L155), [TimeBar](../flufplay/app/src/main/java/id/flufclip/player/PlayerControls.kt#L450)).

Jika stream sudah dikenali sebagai live tetapi durasinya tidak tersedia, source mengganti posisi dan durasi bar menjadi 1 ms. Ini menghasilkan bar penuh. currentLiveOffset hanya dipakai untuk menentukan indikator tertinggal dari live; nilai itu tidak dipakai untuk menggambar progress merah ([PlayerControls.kt](../flufplay/app/src/main/java/id/flufclip/player/PlayerControls.kt#L241), [indikator LIVE](../flufplay/app/src/main/java/id/flufclip/player/PlayerControls.kt#L291)).

### Klasifikasi live dan chat

UI menentukan stream live dari tipe LIVE_STREAM atau AUDIO_LIVE_STREAM ([MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L1602)). Namun fallback yt-dlp dan InnerTube membentuk StreamInfo dengan VIDEO_STREAM tanpa mempertahankan tipe live. Fallback tersebut digunakan bila ekstraksi utama gagal atau tidak mendapatkan stream, dan hanya dijalankan saat akun login ([VideoRepository.kt](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt#L291), [VideoRepository.kt](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt#L352), [InnerTubePlayer.kt](../flufplay/app/src/main/java/id/flufclip/player/InnerTubePlayer.kt#L90)).

LiveChatState menerima nilai klasifikasi itu. Jika live bernilai false, ChatSession memanggil jalur replay chat (startReplay) alih-alih endpoint live chat. Tombol replay hanya ditampilkan setelah chat berhasil ditemukan atau video memang bertipe post-live; karena itu salah klasifikasi dapat sekaligus membuat label waktu menjadi normal dan Live Chat tidak muncul ([LiveChat.kt](../flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt#L154), [MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2138)).

Pada tampilan inline, chat berada di tombol/kartu “Live chat” pada halaman tontonan, bukan panel otomatis di sisi pemutar. Panel docked ditampilkan dalam fullscreen ketika stream terdeteksi live dan syarat tata letaknya terpenuhi ([MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2150), [MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2018)).

Jika klasifikasi live sudah benar tetapi pesan tetap gagal dimuat, source mengandalkan parsing HTML/JSON YouTube untuk ytInitialData, ytcfg, renderer chat, dan continuation. Kegagalan dicatat pada state dan ditampilkan ketika panel chat dibuka ([LiveChat.kt](../flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt#L175), [LiveChat.kt](../flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt#L812)).

## 5. Replay di channel diberi label “sedang menonton”

Saat membuka channel, source mengambil daftar video dari tab Video melalui NewPipe. Kartu channel tidak menerima metadata live khusus dari parser subscriptions; fungsi **VideoCard** menentukan status live dari **item.isOngoingLive()** ([MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L898), [VideoRepository.kt](../flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt#L241), [MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L1566)).

Classifier **isOngoingLive()** menolak tipe POST_LIVE_STREAM, menerima LIVE_STREAM, dan juga menganggap item berdurasi nol sebagai live jika tanggalnya mengandung kata seperti “live”, “watching”, atau “menonton” ([MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2335)). Jadi replay bisa salah dianggap masih live jika extractor memberi tipe LIVE_STREAM, atau jika durasinya tidak diketahui lalu teks tanggal cocok dengan heuristik tersebut.

Setelah isLive menjadi true, VideoCard menampilkan item.viewCount dengan label “sedang menonton”. Pada jalur normal, angka yang sama diberi label “ditonton” ([MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2531), [MainActivity.kt](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2612)). Artinya angka “88 rb sedang menonton” pada kartu channel berasal dari viewCount yang dirender ulang sebagai jumlah penonton live; source tidak mengambil angka penonton live terpisah di kartu tersebut.

Ada jalur parser subscriptions lain: **HomeFeed.parseLockup()** mengisi watchingText ketika baris metadata server memuat kata “watching” atau “menonton”, lalu menetapkan isLive jika lencana LIVE ada **atau** watchingText tidak kosong. Ini dapat mengklasifikasikan item berdasarkan teks metadata saja ([HomeFeed.kt](../flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt#L534), [HomeFeed.kt](../flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt#L577)). Jalur ini dipakai pada kartu feed Langganan yang membawa VideoMeta; halaman channel memakai classifier NewPipe di atas.

**Kesimpulan statis:** label yang membingungkan muncul ketika item replay diklasifikasikan sebagai live; kemudian viewCount totalnya dipakai sebagai jumlah penonton. Untuk memastikan cabang mana pada replay tertentu, perlu nilai streamType, duration, textualUploadDate, dan viewCount yang benar-benar dikembalikan extractor—nilai runtime itu tidak diperiksa dalam sesi ini.

## Batas kesimpulan

Ini analisis source dan inventaris resource lokal. Source mendukung mekanisme di atas, tetapi tidak menunjukkan jalur resolusi yang dipakai pada video tertentu, field metadata sebuah replay, waktu jaringan tiap tahap, keberhasilan permintaan avatar, atau respons endpoint chat saat kejadian. Untuk memastikan penyebab satu video/live tertentu dibutuhkan data runtime; pemeriksaan tersebut tidak dilakukan dalam sesi ini.

## Verifikasi ulang dan perbaikan (kode saja)

Nomor baris pada bagian koreksi dan temuan merujuk pada source sebelum perbaikan; setelah perubahan, gunakan nama fungsi sebagai acuan.

**Koreksi referensi**

- Builder ExoPlayer ada di [MainActivity.kt:483](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L483), bukan 606. Baris 606 adalah `playQuality()` dan `prepare()` dipanggil di 629.
- `VideoCard` dimulai di [MainActivity.kt:2512](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2512), bukan 898. Baris 898 adalah `openChannel()`.
- Label live tanpa `VideoMeta` ada di [MainActivity.kt:2615-2618](../flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt#L2615), bukan 2612.
- `isOngoingLive()` dipakai di semua kartu (beranda, pencarian, playlist, channel), bukan hanya halaman channel.

**Temuan tambahan**

- [InnerTubePlayer.kt:103](../flufplay/app/src/main/java/id/flufclip/player/InnerTubePlayer.kt#L103) menulis `lengthSeconds * 1000` (milidetik), padahal `StreamInfo.duration` dan `PlaybackTracker.start(durationSec)` memakai detik; jalur yt-dlp sudah benar.
- `rememberLiveChat` dipanggil untuk setiap video terpilih dan selalu memulai `ChatSession`, sehingga video biasa memicu fetch watch page + percobaan replay chat di latar belakang.
- Baik jalur yt-dlp maupun `InnerTubePlayer.resolve` tidak mengisi `uploaderAvatars`, sehingga avatar layar tonton juga menjadi huruf saat fallback dipakai.
- `parseCompactVideoRenderer` selalu memberi `VIDEO_STREAM`, termasuk item live di daftar rekomendasi fallback.
- Action bar Shorts tidak memiliki aksi Remix. Resource `ic_remix_filled_white_shadowed` ada di APK, tetapi penempatannya di layar Shorts tidak terbukti dari inventaris resource.

**Perbaikan yang diterapkan**

- `InnerTubePlayer`: unit durasi dalam detik, klasifikasi `LIVE_STREAM`/`POST_LIVE_STREAM` dari `isLive`/`isLiveContent`/`liveBroadcastDetails`, dan badge LIVE pada item `related()`.
- Status live pemutar diikat ke `mediaId` melalui `rememberPlayerLive`; progress live memakai `currentLiveOffset`, bukan rasio `position/duration`.
- Chat hanya dimuat untuk live atau post-live; video biasa tidak lagi memulai sesi chat.
- Heuristik teks di `isOngoingLive()` dihapus; label "sedang menonton" hanya untuk tipe live eksplisit. Jalur `HomeFeed.parseLockup()` juga tidak lagi menjadikan `watchingText` sebagai penanda live; hanya badge `LIVE`/`liveData` eksplisit.
- Avatar Shorts dan layar tonton memakai `VideoRepository.channelAvatar()` saat `uploaderAvatars` kosong.
- Ikon aksi Shorts memakai aset APK `ic_right_*_shadowed` (off/on); header memakai `reel_search_bold_24dp`/`reel_more_vertical_bold_24dp`.
- Resolve video biasa memakai cache 10 menit + prefetch item berikutnya; `startInfo()` dipanggil sebelum pembaruan rekomendasi.
- `PlayerControls`: status `liveDvr`/`canSeek` membedakan live DVR dari non-DVR; time bar, double-tap, long-press, dan tombol LIVE memakai flag itu. Bar live dipetakan ke rentang `minOffsetMs`/`maxOffsetMs` dari `liveConfiguration` bila tersedia; non-DVR ditampilkan sebagai bar live statis penuh.
- Live memakai **DASH** (`StreamInfo.dashMpdUrl`) lebih dulu bila tersedia; manifest DASH YouTube menyertakan `timeShiftBufferDepth` (contoh live uji: 7200 detik), sehingga rewind DVR bisa bekerja. HLS tetap menjadi fallback.
- `MainActivity`: error `BehindLiveWindowException` dari HLS live dipulihkan dengan `seekToDefaultPosition()` + `prepare()`, dibatasi sekali per 30 detik agar tidak loop.
- `openUrl`: resolusi dibatasi 45 detik; saat timeout, job/cache resolve di-evict dan halaman sementara menampilkan error yang bisa dicoba lagi.
- Prefetch dipisahkan dari job resolve user, dan status login dibaca ulang saat `onResume` setelah proses dibekukan sistem.

## Referensi YouTube 21.43.523: pipeline media live

**Ditambahkan:** 2026-11-01  
**Metode:** analisis statis `referensi/youtube-21-43-523.apk` memakai dump `classes.dex`/`classes2.dex` yang sudah ada dan `strings` per-DEX. Seluruh 8 DEX tidak di-dump ulang. Klaim ditandai **(seen)** bila berasal dari simbol/bytecode dan **(inferred)** bila merupakan tafsiran.

### Alur umum

`PlayerResponse -> media engine YouTube (SABR/Platypus) -> Media3 ExoPlayer -> MediaCodec/audio -> TimeBar + LiveChat`

### 1. Media engine dan jaringan

- ExoPlayer Media3 terlihat dari `Landroidx/media3/exoplayer/ExoPlayer;`, `ExoPlayerImpl`, dan `MediaCodecRenderer` di `classes.dex` **(seen)**.
- Lapisan media YouTube berada di `com.google.android.libraries.youtube.media.player.exo2.platypus`: `PlaybackNetFetchFactory`, `EnvoyNetFetchProvider`, `ExecutorImpl`, `MetadataStoreImpl`, `SharedBandwidthSampleTrackerWrapper`, dan `BufferManagerImpl` **(seen)**.
- Jaringan memakai Cronet (`libcronet.156.0.8078.47.so`) dan `libgoogle3.so`; ada bandwidth sampling/ABR **(seen)**.
- Jalur live yang paling jelas adalah **SABR**. String enum seek di `classes3.dex`:
  - `SEEK_SOURCE_SABR_LIVE_DVR_USER_SEEK`
  - `SEEK_SOURCE_SABR_SEEK_TO_DVR_LOWER_BOUND`
  - `SEEK_SOURCE_SABR_SEEK_TO_DVR_UPPER_BOUND`
  - `SEEK_SOURCE_SABR_ACCURATE_SEEK`
  - `SEEK_SOURCE_SABR_SEEK_TO_CLOSEST_KEYFRAME`
  - `SEEK_SOURCE_SABR_INGESTION_WALL_TIME_SEEK`
  - `SEEK_SOURCE_SABR_SEEK_TO_HEAD`
  - `SEEK_SOURCE_SABR_PARTIAL_CHUNK`
  - `SEEK_SOURCE_SABR_RELOAD_PLAYER_RESPONSE_TOKEN_SEEK` **(seen)**
- Dukungan DASH/HLS juga ada: `androidx.media3.exoplayer.dash.DashMediaSource$Factory` dan `androidx.media3.exoplayer.hls.HlsMediaSource$Factory` di `classes3.dex` **(seen)**. Pemetaan MIME `application/dash+xml`, `application/x-mpegURL`, serta parameter `format=mpd-time-csf` dan `format=m3u8-aapl` terlihat di `classes.dex` **(seen)**.
- Pemilihan SABR vs DASH/HLS per video tidak bisa dipastikan tanpa runtime/player response. Untuk live, bukti terkuat mengarah ke SABR **(inferred)**.

### 2. DVR dan seek live

- Enum live di `classes.dex`:
  - `NOT_LIVE`
  - `LIVE_DVR`
  - `LIVE_NON_DVR`
  - `LIVE_DVR` dibuat pada `3da5e0`; `LIVE_NON_DVR` pada `3da5f4` **(seen)**.
- Enum disimpan di field `Lohl.x`. Method `Lohl.d()` (offset `5069c8`) mengembalikan true hanya jika state bukan `NOT_LIVE` dan flag `y` aktif **(seen)**.
- Method `Lohl.b()` (offset `5a9bec`) menghitung `x != LIVE_NON_DVR` lalu mengirim boolean itu ke player/time bar (`jrf.mq(z)`, `Lohn.c(...)`) dan memperbarui `aruc.w` **(seen)**.
- Artinya DVR dan non-DVR diperlakukan berbeda. Indikasi kuat: pada `LIVE_NON_DVR` time bar/seek DVR tidak aktif, sedangkan `LIVE_DVR` memakai jalur DVR **(inferred)**.
- Seek live bersifat server-aware: clamp ke batas bawah/atas DVR, bisa memilih keyframe terdekat atau accurate seek, memakai ingestion wall time, dan dapat memicu `ReloadPlayerResponse` untuk token baru. Proto `ReloadPlayerResponseOuterClass$ReloadPlayerResponse` terlihat di `classes4.dex` **(seen)**.
- String `REMOTE_LIVE_DVR` juga ada, kemungkinan untuk playback remote/cast **(seen; peran detail inferred)**.
- Panjang window DVR tidak ditemukan sebagai konstanta; kemungkinan ditentukan server/player response **(inferred)**.

### 3. Buffering

- Media engine membedakan state `MEDIA_ENGINE_PLAYER_STATE_BUFFERING`, `PLAYER_PLAYBACK_STATE_BUFFERING`, dan `PAUSED_BUFFERING` **(seen)**.
- Ada watchdog/log `Player stuck buffering and not loading for ...`, `Player stuck buffering with no progress for ...`, `BUFFERING_TIME`, dan `AD_BUFFERING` **(seen)**.
- Saat seek live, pemutar masuk buffering, menunggu chunk SABR/media, lalu melanjutkan. Tidak ada indikasi aplikasi mengunduh seluruh DVR lebih dulu; buffer dikelola media engine **(inferred)**.

### 4. Live chat

Live chat adalah subsistem tersendiri, bukan sekadar daftar pesan:

- Class/UI: `LiveChatController`, `LiveChatRecyclerView`, `LiveChatSettingFragment`, `LiveChatOverlayModel`, `LiveChatAccountNameProvider` **(seen)**.
- Endpoint yang terlihat:
  - `live_chat/get_live_chat`
  - `live_chat/get_live_chat_replay`
  - `live_chat/send_message`
  - `live_chat/moderate`
  - `live_chat/manage_user`
  - `live_chat/get_item_context_menu`
  - `live_chat/get_live_chat_message_buy_flow`
  - `live_chat/get_live_chat_super_sticker_buy_flow`
  - `live_chat/get_live_interactivity`
  - `live_chat/create_poll`
  - `live_chat/send_live_chat_vote` **(seen)**
- String `Falling back from streaming to unary GetLiveChat` menunjukkan transport streaming dengan fallback unary **(seen)**.
- Replay chat terhubung ke posisi video: `LiveChatController: VideoTimeEvent subscription failed; chat replay will no longer sync to the video position.` **(seen)**.
- Ada `getIsLiveChatParticipationRestricted`, entry point overlay/fullscreen, dan toggle chat **(seen)**.
- Kemampuan yang tercakup: baca chat, kirim pesan, moderasi/context menu, Super Chat/paid message, Super Sticker, poll/vote, replay chat, dan pengaturan chat **(inferred dari endpoint/class)**.

### 5. Format, codec, dan latensi

- Dukungan codec terlihat dari library native:
  - `libdav1dJNI.so` untuk AV1
  - `libvpxV2JNI.so`/`libvpx.so` untuk VP9
  - `libopusV2JNI.so` untuk Opus
  - `libmedia3EffectJNI.so` untuk audio effect Media3 **(seen)**
- Ada `enableLowLatencyAudio`, `isLowLatencyInputSupported`, dan `isLowLatencyOutputSupported`; ini low-latency audio, bukan bukti LL-HLS **(seen)**.
- Tidak ditemukan bukti kuat LL-HLS/low-latency video pada string yang diperiksa; statusnya **unknown**, bukan “tidak ada”.
- Caption live ikut diparsing di jalur SABR: `SABR Captions parsing error in LiveFmt3Parser` **(seen)**.

### 6. Batas kesimpulan referensi live

Yang belum dapat dipastikan dari APK saja:

- Panjang window DVR untuk stream tertentu.
- Kapan live memakai SABR vs DASH/HLS.
- Dukungan low-latency video/LL-HLS.
- Detail negosiasi server untuk DVR seek dan reload token.
- Perilaku runtime ketika jaringan lambat, token kedaluwarsa, atau chat members-only.

### 7. Implikasi singkat untuk FlufPlay

- FlufPlay memakai DASH untuk live bila `dashMpdUrl` tersedia, dengan HLS sebagai fallback; seek memakai `player.seekTo()` di dalam window DVR. Referensi memakai jalur SABR dengan clamp batas DVR dan reload player-response token.
- Live chat FlufPlay sudah mencakup baca, kirim, filter, Super Chat, emoji, dan replay. Fitur referensi seperti moderasi, poll/vote, dan alur pembelian Super Sticker/paid message belum diterapkan karena membutuhkan kontrak endpoint/izin terautentikasi yang belum diverifikasi.
- Perbedaan ini adalah hasil analisis statis; belum diverifikasi dengan pengujian runtime pada APK referensi.
