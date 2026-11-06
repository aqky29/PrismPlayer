# Analisis Screen Subscriptions YouTube 21.43.523 vs FlufPlay

Status: analisis statis + bukti runtime, lalu diimplementasikan (lihat bagian Implementasi di akhir).

## Identitas dan metode

- APK acuan: `referensi/youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`, versi `21.43.523`.
- Versi YouTube yang terpasang di perangkat sama dengan APK (`21.43.523`), sehingga hierarchy runtime dan isi APK dapat dibandingkan langsung.
- Perangkat: tablet 2560x1600, `wm density` 360 (2,25 px/dp), locale `en-US`. Lokasi FlufPlay: `flufplay/app`.
- Bukti runtime (observed):
  - `uiautomator dump` layar Subscriptions YouTube (tab Langganan aktif), 01-11-2026 18:25.
  - `screen_yt.png` (17:43) dan `screen_flufplay.png` (17:46) di root repo.
  - `uiautomator dump` layar Langganan FlufPlay setelah perbaikan navigation bar (18:11).
- Bukti statis (seen):
  - `aapt2 dump resources referensi/youtube-21-43-523.apk` (337.402 baris): `layout/feed_filter_bar`, `layout/feed_filter_bar_with_overlay`, `layout/feed_channel_filter_header`, `drawable/chip_cloud_chip_*`, `id/chip_cloud_chip_avatar`, `drawable/img_no_subscriptions`.
  - Probe InnerTube memakai cookie login app sendiri (WEB client, `FEsubscriptions`): 2,25 MB JSON. Hanya struktur yang dikutip di sini; berkas respons sementara sudah dihapus.
  - Probe `FEchannels` dengan autentikasi yang sama: 132 KB JSON, berisi `channelRenderer`.
- Catatan: label chip ("All", "Memberships", "Today", ...), teks "Most relevant", dan status "has new content"/"is live" **tidak ditemukan** di string DEX maupun resource APK. Artinya label tersebut berasal dari respons server (client native), bukan konstanta client.

## Data yang ditampilkan layar Subscriptions YouTube (observed)

### Top bar
- Logo YouTube di kiri.
- Ikon notifikasi dengan badge `9+` (`content-desc="Notifications, 9+"`).
- Ikon search di kanan.
- Tidak ada avatar profil di top bar pada keadaan ini.

### Rail kiri (khusus layar lebar/tablet)
Lebar rail 504 px (224 dp); konten utama mulai di x = 576 px (256 dp).

| Elemen | Ukuran (px) | Data |
|---|---|---|
| "Most relevant" | 266x72 (32 dp) | dropdown urutan feed |
| "All channels" | 468x117 (52 dp) | tombol filter semua channel |
| Baris channel | 468x117 (52 dp) | avatar 81 px (36 dp), nama channel |
| Status channel | — | `has new content` (dot biru) atau `is live` (dot merah) |

Contoh baris hasil dump: `DEANKT`, `Crispy Cendy, has new content`, `GUAVAAA, is live`, `set1awanade, has new content`, `RIXPLAY ID`, `nabbsky`, `GAGITUSIL, has new content`, `Bubble, has new content`, `Windah Basudara, has new content`.

### Chip filter di atas feed
Tinggi 72 px (32 dp), jarak antar chip ±18 px (8 dp), bentuk pill, bisa digeser horizontal.

`All` (terpilih, latar putih) · `Memberships` · `Today` · `Videos` · `Shorts` · `Live` · `Continue watching` · `Unwatched`

### Kartu feed
- Dua kolom pada lebar 2560 px; lebar kartu 960 px (427 dp), thumbnail 16:9.
- Overlay thumbnail: durasi (contoh `3:40:44`, `42:18:02`), lencana `LIVE`, atau progress bar tontonan.
- Baris 1: avatar channel, judul (maks. 2 baris), menu titik tiga.
- Baris 2: nama channel + centang Verified, jumlah penonton/tayangan, waktu.
  - VOD: `APAANSIH` · `Crispy Cendy` · `27K views` · `Streamed 21 hours ago`.
  - Live: `Tidak bisa berbicara, whatsapp saja . - DAY 3 PT.2` · `GUAVAAA` · `1.5K watching` · `LIVE`.
- Lencana tambahan yang teramati di data: `Members only`, `Verified`, `Now playing` (progress).
- Kartu iklan (`Sponsored · Google Gemini` + tombol `Daftar`) muncul di feed YouTube; tidak relevan untuk FlufPlay.

### Shelf Shorts
- Header `Shorts` + ikon play merah + chevron (`screen_yt.png`).
- Deretan horizontal kartu 9:16 (6 item pada respons yang diperiksa).
- Setiap item: thumbnail vertikal, judul singkat, jumlah tayangan, menu titik tiga.

### Navigation bar bawah
- Lima item, grup di tengah layar: `Home`, `Shorts`, `Create (+)`, `Subscriptions` (aktif, ikon filled), `You`.
- Tinggi bar 108 px (48 dp) dari y = 1492 sampai 1600.
- FlufPlay saat ini: 4 item selebar layar (`Beranda`, `Shorts`, `Langganan`, `Anda`), tanpa tombol Create.

## Struktur respons API yang dipakai app kita (observed, authenticated)

Permintaan: `POST /youtubei/v1/browse` `browseId=FEsubscriptions`, WEB client, cookie login app.

- `header.feedTabbedHeaderRenderer.title` = `Subscriptions`.
- `tabRenderer.content.richGridRenderer.contents` berisi campuran:
  - `richSectionRenderer` #0 → `shelfRenderer` berjudul `Latest`, dengan tombol `All subscriptions` menuju `browseId=FEchannels`.
  - `richSectionRenderer` #1 → `richShelfRenderer` berjudul `Most relevant` (video).
  - 118 `richItemRenderer`: 112 `lockupViewModel` video + 6 `shortsLockupViewModel`.
  - `richSectionRenderer` #10 → `richShelfRenderer` berjudul `Shorts`, berisi 6 item `shortsLockupViewModel` (`entityId`, `reelWatchEndpoint.videoId`, thumbnail `frame0.jpg`, `accessibilityText` mis. `2.4 thousand views - play Short`).
  - `continuationItemRenderer` untuk halaman berikutnya.
- Metadata video dibawa di `lockupMetadataViewModel.metadata.contentMetadataViewModel.metadataRows`:
  - Baris 0: nama channel.
  - Baris 1: `40K views` + `Streamed 2 days ago` (dipisah ` • ` dan `badges`).
  - Live: baris metadata `1.5K watching`; avatar punya `liveData.liveBadgeText = "LIVE"`.
  - Badge `Members only` ada di `badgeViewModel` (`BADGE_MEMBERS_ONLY`).
  - Durasi/LIVE ada di `thumbnailBadgeViewModel`; progress tontonan di `thumbnailOverlayProgressBarViewModel`.
- **Tidak ada** `chipCloudRenderer` / `feedFilterChipBarRenderer` dan **tidak ada** daftar channel rail di respons WEB `FEsubscriptions`. Chip dan rail adalah UI client native / endpoint lain.
- `FEchannels` mengembalikan daftar `channelRenderer`: `channelId`, `title`, `thumbnail`, `subscriberCountText`, `videoCountText`, `subscriptionButton`, `ownerBadges`. Ini cukup untuk mengisi rail "All channels", tetapi bukan status "has new content"/"is live".

## Perbandingan dengan FlufPlay

| Data / elemen | YouTube (observed) | FlufPlay sekarang | Keterangan |
|---|---|---|---|
| Rail sort + semua channel | Ada (`Most relevant`, `All channels`, daftar channel + status) | Tidak ada | `FEsubscriptions` tidak mengirim rail; perlu `FEchannels` + logika status |
| Chip filter | 8 chip | Tidak ada | Label chip tidak ada di respons WEB |
| Judul section | `Latest`, `Most relevant`, `Shorts` | Hilang | `HomeFeed.collect()` meratakan seluruh `lockupViewModel` menjadi satu list |
| Shelf Shorts | Kartu 9:16 horizontal + header | Tidak tampil | Respons sudah berisi 6 `shortsLockupViewModel`; `parseLockupViewModel()` hanya menerima `LOCKUP_CONTENT_TYPE_VIDEO` sehingga item Shorts dibuang |
| Views + waktu | `40K views · Streamed 2 days ago` | Tidak tampil | Parser mencari kata `ditonton`/`menonton`/`yang lalu`; locale perangkat `en-US`, server mengirim `views`/`Streamed ... ago` (`HomeFeed.kt:309-310`) |
| Penonton live | `1.5K watching` | Tidak tampil | Baris metadata live yang sama tidak diparse |
| Verified / Members only | Centang + badge | Tidak ada | `ownerBadges`/`badgeViewModel` belum diparse |
| Progress tontonan | Bar progress di thumbnail | Tidak ada | `thumbnailOverlayProgressBarViewModel` belum dipakai |
| Grid | 2 kolom, kartu 427 dp | 3 kolom adaptif min 300 dp | `GridCells.Adaptive(300.dp)` di `SubscriptionsScreen` |
| Menu kartu | Titik tiga | Ada (`Opsi rekomendasi`) | Sudah berfungsi |
| Navigation bar | 5 item di tengah + Create | 4 item selebar layar | Perbedaan global, bukan khusus Subscriptions |
| Top bar | Logo + notifikasi + search | Logo FlufPlay + search | Notifikasi belum ada |

Referensi kode FlufPlay:
- `MainActivity.kt:276` `loadSubscriptions()`; `MainActivity.kt:295` `loadMoreSubscriptions()`.
- `MainActivity.kt:1378-1401` pemanggilan `LibraryTab(subscriptionsContent = ...)`.
- `MainActivity.kt:2514` `SubscriptionsScreen`; `MainActivity.kt:2438` `VideoCard`.
- `HomeFeed.kt:77-82` `subscriptions()`; `HomeFeed.kt:104-112` `parsePage()`; `HomeFeed.kt:150-171` `collect()`; `HomeFeed.kt:272-357` `parseLockupViewModel()`.

## Harus apa

### P0 — perbaiki data yang sudah tersedia (tanpa endpoint baru)
1. `HomeFeed.parseLockupViewModel`: parse `metadataRows` berdasarkan posisi/struktur, bukan kata bahasa Indonesia.
   - baris 0 → `uploaderName`;
   - baris 1 `metadataParts[0]` → views atau `N watching` (live);
   - `metadataParts[1]` / `accessibilityLabel` → waktu (`Streamed ... ago`);
   - `badgeViewModel.badgeText` → `Members only`; `liveData.liveBadgeText` → live.
2. Tambah field model untuk: teks penonton live, badge verified/members, progress tontonan.
3. `VideoCard`: tampilkan metadata gaya YouTube (`channel · 40K views · Streamed 2 days ago`, live `1.5K watching`), centang verified, badge Members only, dan progress bar.
4. Ganti grid `Adaptive(300.dp)` menjadi 2 kolom besar pada layar lebar (mis. `Adaptive(minSize = 400.dp)` atau `Fixed(2)` saat lebar ≥ 840 dp), gap 12 dp, margin 16 dp.

### P1 — struktur layar seperti YouTube
1. Ubah hasil parsing Langganan menjadi daftar section, bukan list video datar:
   `sealed interface SubsEntry { Video(item); ShortsShelf(title, items); Header(title) }`.
   Pertahankan urutan `richGridRenderer.contents` (shelf `Latest`, shelf `Most relevant`, shelf `Shorts`).
2. Render shelf Shorts: header `Shorts` + chevron, `LazyRow` kartu 9:16 (lebar ±160 dp), buka pemutar Shorts yang sudah ada.
3. Rail kiri untuk layar lebar (≥ 840 dp): `Most relevant` (dropdown; minimal label, sort server belum tersedia), `All channels`, dan daftar channel dari `FEchannels` (avatar 36 dp, tinggi baris 52 dp). Status dot dapat diturunkan sementara dari item feed (channel yang punya upload terbaru/live), sampai endpoint native tersedia.
4. Chip bar: data chip tidak tersedia di respons WEB. Implementasi client-side dulu untuk `All`, `Videos`, `Shorts`, `Live`, `Today`, `Continue watching`, `Unwatched` berdasarkan item yang sudah dimuat; jangan mengklaim filter server.

### P2 — penyelesaian visual
1. Sesuaikan navigation bar tablet menjadi grup 5 item di tengah (termasuk tombol `Create` hanya bila fitur unggah tersedia).
2. Tambah ikon notifikasi + badge di top bar bila data notifikasi tersedia.
3. Samakan ukuran/typography chip (tinggi 32 dp, pill) dan kartu (16:9, radius 12 dp).

### Kriteria penerimaan
- Pada 2560x1600 (1138x711 dp): rail 224 dp, konten mulai 256 dp, 2 kolom kartu 427 dp, chip 32 dp, nav 48 dp.
- Langganan menampilkan: rail/chip (sesuai ketersediaan data), section `Latest`/`Most relevant`, shelf `Shorts`, metadata views/waktu/live, badge verified/members.
- Tidak ada regresi pada Home, Shorts, dan pemutar.

### Risiko / batasan
- Chip filter dan status rail (new content/live) adalah UI client native; respons WEB `FEsubscriptions` yang dipakai FlufPlay tidak memuatnya. Jangan menjanjikan paritas server-side tanpa menambah endpoint/client native.
- `FEchannels` hanya memberi daftar channel, bukan status unread/live.
- Jangan menyimpan respons autentikasi (berisi data akun) ke repo; probe yang dipakai untuk analisis ini sudah dihapus.

## Implementasi

Diimplementasikan pada 01-11-2026 dan diverifikasi pada perangkat yang sama (2560x1600, density 360).

### Data (`HomeFeed.kt`)
- `subscriptions()` sekarang mengembalikan `SubsPage(entries, continuationToken)` dengan model `SubsEntry.Video(item, meta)` dan `SubsEntry.ShortsShelf(items)`; `moreSubscriptions(token)` memakai parser yang sama.
- `parseLockup()` menggantikan pembacaan metadata berbasis kata Indonesia:
  - baris 0 → channel; baris 1+ diklasifikasi menjadi views, `N watching`, atau waktu (`Streamed ... ago`) berdasarkan penanda bahasa, bukan locale;
  - `attachmentRuns` dengan `CHECK_CIRCLE_FILLED` → verified;
  - `badgeViewModel` `Members only` → membersOnly;
  - `thumbnailBadgeViewModel` LIVE/`liveData.liveBadgeText` → live;
  - `thumbnailOverlayProgressBarViewModel.startPercent` → progress tontonan.
- `shortsLockupViewModel` diparse menjadi `ShortsCard` (videoId, judul, views, thumbnail 9:16).
- `channels()` memanggil `FEchannels` dan membaca `channelRenderer` (id, nama, avatar, URL).
- URL gambar protokol-relatif (`//yt3...`) dinormalisasi menjadi `https://...`; sebelumnya avatar rail gagal dimuat dan hanya tampil huruf awal channel.

### UI (`MainActivity.kt`)
- `SubscriptionsScreen`: rail 224 dp + chip bar + `LazyVerticalGrid` 2 kolom (layar ≥ 840 dp) / adaptif 300 dp.
- Rail: dropdown `Paling relevan`, tombol `Semua channel`, daftar channel dengan avatar 36 dp, baris 52 dp, dot biru (punya item di feed) / merah (live), dan highlight pilihan.
- Chip client-side: `Semua`, `Membership`, `Hari ini`, `Video`, `Shorts`, `Live`, `Lanjutkan menonton`, `Belum ditonton`. Filter memakai metadata hasil parse; pagination hanya berjalan pada tampilan `Semua` tanpa filter channel.
- Shelf `Shorts`: header merah + chevron, kartu 9:16 (160x284 dp) dengan judul/views overlay; klik membuka pemutar Shorts dengan antrean shelf.
- `VideoCard` menerima `VideoMeta`: metadata asli server (`187K views · Streamed 19 hours ago`, `1.9K watching`), ikon verified inline, badge `Khusus Member`, progress bar merah, dan lencana LIVE di kiri bawah.
- `HomeNavigation`: item 96 dp dikelompokkan di tengah pada layar lebar (gaya tablet YouTube); tetap melebar penuh di layar sempit.
- Ikon `Semua channel` memakai aset asli YouTube 21.43.523 (`res/-tD.png`, `yt_fill_experimental_subscriptions_black_24`) yang disalin ke `res/drawable-xxxhdpi/yt_all_channels.png`.

### Verifikasi
- Filter Live hanya menampilkan siaran live (`1.9K watching`, `93 rb sedang menonton`).
- Filter Membership hanya menampilkan item dengan badge `Khusus Member`.
- Filter Hari ini menampilkan item `1 hour ago` / `19 hours ago`.
- Filter Shorts hanya menampilkan shelf; klik kartu membuka Shorts player.
- Klik channel di rail memfilter feed ke channel tersebut; `Semua channel` mengembalikan feed penuh.
- Avatar channel dan ikon `Semua channel` tampil sesuai referensi.

### Perbaikan chip Membership (01-11-2026)
- Masalah: chip Membership semula hanya memfilter 112 item feed yang sudah dimuat. Di halaman pertama hanya 3 item DEANKT memiliki badge `Members only`, sehingga tampil 3.
- Bukti pembanding: chip `Memberships` di aplikasi YouTube (server-side) menampilkan 4 kartu pada layar pertama (termasuk `SCAMMER KELAS KAKAP` dan `PARTY SAKIT KEPALA`), lalu memuat halaman berikutnya saat digulir. Respons WEB `FEsubscriptions` tidak membawa chip/params tersebut.
- Solusi: `HomeFeed.membershipVideos(channelId)` memanggil playlist membership otomatis `VLUUMO<channelId tanpa "UC">` untuk setiap channel yang punya item `Members only` di feed. Playlist ini sumber yang sama dengan tab Membership halaman channel. Hasilnya untuk DEANKT: 99 video membership (termasuk item lama seperti `WHEEL MATES`, `BACK TO BACK NOERDIN`, `HORROR NIGHT`), bukan 3.
- Semua item playlist UUMO ditandai `membersOnly = true` karena isi playlist memang konten member, meskipun metadata badge tidak selalu menyertakannya.
- Verifikasi di perangkat: chip Membership menampilkan daftar panjang dengan badge `Khusus Member` dan metadata `Streamed ... ago`; tidak ada lagi batas 3.

### Batasan implementasi
- Chip lain (Hari ini, Live, Shorts, Lanjutkan menonton, Belum ditonton) masih client-side dari item yang sudah dimuat; hanya Membership yang kini memakai sumber playlist channel.
- Dot rail dihitung dari item yang sudah dimuat, bukan state server.
- Kartu iklan dan community post tidak ditampilkan (tidak ada di respons WEB yang dipakai).
- Tombol notifikasi di top bar belum ditambahkan (belum ada sumber data notifikasi).
