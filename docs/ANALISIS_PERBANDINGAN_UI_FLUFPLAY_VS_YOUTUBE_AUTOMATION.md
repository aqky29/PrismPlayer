# Analisis Komparasi UI/UX: FlufPlay vs YouTube App (via Virtual Device Automation)

**Tanggal:** 5 November 2026  
**Metode:** Otomasi UI dump (`android layout` + `uiautomator`) & Visual Frame Capture (`screencap`)  
**Target:** Google Pixel 10 AVD (`emulator-5554`, Android 16 / API 36, 1080×2424 @ 420 dpi)  
**Paket Uji:** `id.flufclip.player` vs `com.google.android.youtube`  
**Status Resource:** RAM emulator dibatasi ke 1536 MB (`hw.ramSize=1536`, `vm.heapSize=384`), background daemon dibersihkan, emulator telah dimatikan pasca-inspeksi.

---

## 1. Ringkasan Eksekutif & Temuan Kunci

Berdasarkan inspeksi hierarki pohon UI (*layout tree*) dan ekstraksi bounding box dari automasi virtual:

1. **Desain Feed & Layout Surface:**
   - **YouTube:** Menganut prinsip **Edge-to-Edge** murni (lebar 1080px tanpa margin luar). Pemisah antar item didasarkan pada whitespace tipis atau divider halus.
   - **FlufPlay:** Menggunakan model **Card terisolasi dengan Margin** (`bounds: [42, ...] [1038, ...]`, padding horizontal 42px kiri-kanan) dan sudut melengkung (*rounded corners*).

2. **Top App Bar & Filter Bar:**
   - **YouTube:** Memiliki sistem filter bertingkat dengan `HorizontalScrollView` berisi kategori chip (*Home, Subs, Music, Live, More*), serta icon Cast dan Notifikasi ber-badge di sisi kanan.
   - **FlufPlay:** Bersih dan minimalis; hanya menampilkan teks "FlufPlay" dan tombol Search ("Telusuri"). Belum memiliki chip filter kategori di Home.

3. **Bottom Navigation Bar:**
   - **YouTube:** Menggunakan 4 tab native: `Home`, `Shorts`, `Create (+)` (posisi sentral), dan `You` (avatar dinamis dengan border status). Tinggi bar: **126px** (`[2235, 2361]`).
   - **FlufPlay:** Menggunakan 4 tab: `Home`, `Shorts`, `Subscriptions`, dan `You`. Menghilangkan tombol Create dan mempromosikan Subscriptions ke bottom bar. Tinggi bar: **147px** (`[2214, 2361]`).

---

## 2. Perbandingan Mendalam per Layar

### A. Layar 1: Home Feed

| Atribut / Komponen | YouTube App Native | FlufPlay | Dampak & Rekomendasi |
| :--- | :--- | :--- | :--- |
| **Top App Bar** | • Logo SVG YouTube (`[0,142][320,268]`)<br>• Cast icon<br>• Notifikasi + Badge (`[828,142][954,268]`)<br>• Search icon (`[954,142][1080,268]`) | • Title Text "FlufPlay" (`[139,179][922,253]`)<br>• Search button ("Telusuri") (`[923,154][1049,280]`) | FlufPlay lebih simpel, namun YouTube unggul dalam glanceability notifikasi dan casting. |
| **Category Chips** | • `HorizontalScrollView` (`[42,268][848,393]`) tinggi 125px.<br>• Chip pill: *Home, Subs, Music, Live, More*. | • *Tidak ada di Home screen* (hanya ada di Subscriptions). | Menambahkan chip bar di Home FlufPlay akan meningkatkan eksplorasi konten pengguna. |
| **Feed Card Width** | • Lebar penuh **1080px** (tanpa margin samping). | • Lebar **996px** (margin kiri 42px, kanan 42px). | Desain card FlufPlay memberi kesan modern/card-based, sedangkan YouTube mengoptimalkan ruang tayang visual thumbnail. |
| **Video Thumbnail & Timestamp** | • Timestamp badge di pojok kanan bawah thumbnail.<br>• Sudut thumbnail semi-kotak (border-radius 0 - 4dp). | • Timestamp badge (`[931,775][1009,817]`).<br>• Sudut thumbnail membulat (border-radius ~12dp). | Konsisten dengan desain card FlufPlay. |
| **Interleaved Content** | • Menyisipkan rak Shorts 2-grid (`[0,702][540,1450]`) langsung di feed. | • Feed murni daftar video panjang vertikal. | FlufPlay fokus pada linear feed tanpa gangguan rak Shorts di Home. |

---

### B. Layar 2: Watch / Video Player Screen

| Atribut / Komponen | YouTube App Native | FlufPlay | Dampak & Rekomendasi |
| :--- | :--- | :--- | :--- |
| **Player Viewport** | • Rasio dinamis (standar 16:9 atau mengikuti format video ad/vertikal).<br>• Progress seekbar di `[0,1684][1080,1779]`. | • Viewport teratas `[0,137][1080,745]` (tinggi 608px).<br>• Seekbar di `[0,671][1080,745]`. | FlufPlay membatasi player di area atas dengan layout kontrol yang sangat responsif. |
| **Overlay Controls** | • Tombol play/pause, next, previous, auto-fade.<br>• Mode full screen toggle di kanan bawah player. | • Kontrol lengkap: Minimize (`[0,137]`), Autoplay (`[550,137]`), Cast, Subtitle, Settings (`[949,137]`), Previous, Pause (`[435,336]`), Next, Fullscreen (`[943,606]`). | FlufPlay menyediakan akses cepat satu sentuhan ke Subtitle dan Autoplay langsung di overlay. |
| **Title & Expander** | • Judul video dengan baris deskripsi expandable ("...more"). | • Judul video (`[32,777][1041,840]`) + metadata views/tanggal + `...selengkapnya` (`[773,848][1048,911]`). | Hierarki teks FlufPlay sudah 1:1 setara dengan YouTube. |
| **Channel Row** | • Avatar, Nama Channel, Subscribers count, Subscribe Pill. | • Avatar, Nama Channel ("Hour Hits"), 166K subs, Tombol "Subscribe" (`[801,925][1048,1051]`). | FlufPlay memposisikan Subscribe button sejajar di kanan dengan proporsi tap target nyaman (~247×126px). |
| **Action Bar (Chips)** | • Scroll horizontal tombol pill kapsul:<br>Like/Dislike split pill, Share, Remix, Download, Clip, Save. | • Scroll horizontal (`[32,1073][1048,1173]`):<br>Like (`4 K`), Dislike, Share, Ask (AI), Download. | FlufPlay memiliki fitur unik tombol **Ask** (AI inquiry) yang terintegrasi langsung di action bar. |
| **Comments Teaser** | • Preview komentar teratas dalam card kompak dengan rounded corner dan avatar komentator. | • Card preview komentar (`[32,1215][1048,1436]`): Header "Comments 86", avatar "D", snippet "I Miss my dad". | FlufPlay sudah mengadopsi pola teaser komentar modern YouTube. |

---

### C. Layar 3: Shorts Screen

| Atribut / Komponen | YouTube App Native | FlufPlay | Dampak & Rekomendasi |
| :--- | :--- | :--- | :--- |
| **Reel Viewport** | • Fullscreen reel recycler (`[0,137][1080,2235]`). | • Fullscreen scrollable view (`[0,0][1080,2214]`). | Keduanya menyajikan video vertikal 9:16 imersif. |
| **Top Action Icons** | • Search (`[858,169]`) & More/Camera (`[985,169]`). | • Title "Shorts" (`[42,176]`), Mute (`[682,149]`), Search (`[808,149]`), Opsi (`[934,149]`). | FlufPlay menyertakan tombol **Mute** instan di top bar, sangat berguna bagi pengguna yang sensitif suara saat scrolling. |
| **Right Rail Actions** | • Like (vertical pill + count)<br>• Dislike<br>• Comments<br>• Save<br>• Share<br>• Remix<br>• Sound track thumbnail berputar | • Like (`[894,1490]` + count "143K")<br>• Comments (`[894,1677]` + count "297")<br>• Share (`[894,1864]`)<br>• Audio track pill (`[916,2036]`) | FlufPlay menyederhanakan rail ke aksi esensial (Like, Comment, Share, Sound), mengurangi clutter visual tombol Remix/Save. |
| **Bottom Overlay Info** | • Channel avatar + Handle (`@ojrigs`) + Subscribe button pill hitam/putih.<br>• Judul Short 1-2 baris. | • Channel avatar (`[17,1858]`) + Nama channel (`[127,1858]`).<br>• Judul Short (`[32,1963]`).<br>• Audio tag pill ("Stitches") (`[32,2052]`). | FlufPlay mempertahankan tata letak informasi kreator dan judul lagu yang jelas di kiri bawah. |

---

### D. Layar 4: Subscriptions Screen

| Atribut / Komponen | YouTube App Native | FlufPlay | Dampak & Rekomendasi |
| :--- | :--- | :--- | :--- |
| **Channel Story Avatar Row** | • Carousel horizontal lingkaran avatar channel yang di-subscribe di paling atas. | • *Tidak ada carousel avatar terpisah di atas feed*; langsung menggunakan chips filter. | Rekomendasi: Menambahkan deretan avatar bulat channel di puncak layar Subscriptions untuk akses cepat ke profil kreator. |
| **Filter Chips** | • Chips: *All, Today, Videos, Shorts, Live, Posts*. | • Chips horizontal (`[0,285][1080,411]`): *All, Membership, Today, Video, Shorts, Live*. | FlufPlay memiliki chip khusus **Membership**, memudahkan filter konten eksklusif langganan. |
| **Feed Item Layout** | • Edge-to-edge feed dengan tanggal/waktu rilis real-time. | • Card feed ber-margin dengan badge status (Verified badge, Live/Streamed status, Timestamp). | Detail metadata di FlufPlay lengkap dan rapi. |

---

### E. Layar 5: You / Profile Tab

| Atribut / Komponen | YouTube App Native | FlufPlay | Dampak & Rekomendasi |
| :--- | :--- | :--- | :--- |
| **Profile Header** | • Avatar channel, nama akun, handle, tombol "View channel", icon Settings di top-right. | • Avatar, nama akun ("Iqbalhs"), handle (`@Iqbalhs-m9t · View account`).<br>• Horizontal chip: *Switch account*, *2 accounts*, *Watch history*. | FlufPlay mempermudah multi-akun dengan chip "Switch account" dan penanda jumlah akun yang langsung terlihat. |
| **History Section** | • Horizontal carousel cuplikan video riwayat tontonan dengan tombol "View all". | • Header "History" + subtext "From your YouTube account" + tombol "View all" (`[835,674]`).<br>• Carousel kartu video horizontal dengan badge Shorts / thumbnail. | Sinkronisasi History akun YouTube di FlufPlay berhasil dipetakan ke carousel horizontal dengan presisi tinggi. |
| **Playlists Section** | • Carousel daftar playlist horizontal: *Liked videos, Watch later, Custom playlists*. | • Carousel Playlists: *Liked videos* (Private), *Saved Shorts* (Private), *Watch later* (Private) + tombol "View all". | Pola kartu playlist FlufPlay mengikuti standar YouTube dengan badge privasi yang jelas. |
| **Menu Navigasi Bawah** | • List vertikal: *Your videos, Downloads (0 videos), Movies, Badges*. | • List navigasi: *Watch history*, *Downloads*. | FlufPlay memfokuskan daftar pada fitur yang paling sering dipakai (History dan Downloads offline). |

---

## 3. Rangkuman Bukti Visual & Layout Dump

Seluruh artefak hasil inspeksi otomatis tersimpan di direktori workspace:
- `docs/evidence/ui_comparison_automation/flufplay_home.png` & `flufplay_home_layout.json`
- `docs/evidence/ui_comparison_automation/yt_home.png` & `yt_home_layout.json`
- `docs/evidence/ui_comparison_automation/flufplay_watch.png` & `flufplay_watch_layout.json`
- `docs/evidence/ui_comparison_automation/yt_watch.png` & `yt_watch_layout.json`
- `docs/evidence/ui_comparison_automation/flufplay_shorts.png` & `flufplay_shorts_layout.json`
- `docs/evidence/ui_comparison_automation/yt_shorts.png` & `yt_shorts_layout.json`
- `docs/evidence/ui_comparison_automation/flufplay_subs.png` & `flufplay_subs_layout.json`
- `docs/evidence/ui_comparison_automation/yt_subs.png` & `yt_subs_layout.json`
- `docs/evidence/ui_comparison_automation/flufplay_you.png` & `flufplay_you_layout.json`
- `docs/evidence/ui_comparison_automation/yt_you.png` & `yt_you_layout.json`
