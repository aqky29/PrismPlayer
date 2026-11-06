# Panel di Shorts YouTube 21.43.523 (komentar, chat, bagikan, menu) dan rekomendasi FlufPlay

> **Status implementasi (2026-10-31, dibuild, belum diuji di perangkat):** scrim di luar panel komentar (dengan kembali dari balasan) dan lembar bawah untuk menu titik tiga beserta Kecepatan putar sudah dikerjakan; lihat "Tahap ketiga" di [`ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md`](ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md). Tabel "FlufPlay sekarang vs YouTube" di bawah menggambarkan keadaan **sebelum** perubahan itu. Urutan komentar, kolom tulis, panel bagikan mengintip, dan chat live Shorts belum dikerjakan.

Dibuat 2026-10-31. Semua temuan **statis** (APK tidak dijalankan). Melengkapi [`ANALISIS_GESTUR_UX_SHORTS_APPBAR_YOUTUBE_REA.md`](ANALISIS_GESTUR_UX_SHORTS_APPBAR_YOUTUBE_REA.md) dan [`ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md`](ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md).

## Kesimpulan

1. Shorts memakai **tiga wadah panel yang berbeda**, bukan satu: (a) *engagement panel* untuk komentar dan balasan, (b) `ReelsBottomSheetDialog` untuk menu titik tiga dan kecepatan putar, (c) panel bagikan "mengintip" (`unified_share_panel`). Chat live di Shorts bukan lembar bawah; ia **overlay di atas video**.
2. **Isi** panel komentar Shorts dikirim server dan dirender ke host kosong `reel_engagement_panel_overlay_root` (`mse.c`). Karena itu geometri persis daftar komentar Shorts **tidak ada** di layout XML APK. Yang terbaca adalah rangka bersama (header, tombol tutup, keadaan snap, perilaku ketuk-di-luar) dan komponen komentar lama berbasis View.
3. Panel punya **empat keadaan snap** (`WRAP_CONTENT`, `FULL_BLEED`, `BELOW_THE_PLAYER`, `BELOW_THE_MINI_PLAYER`) dan tiga perilaku ketuk di luar panel (tidak melakukan apa-apa, tutup, atau tutup satu lapis). Keadaan mana yang dipakai komentar Shorts ditentukan server per panel.
4. Tinggi lembar menu Shorts adalah **pecahan layar yang dikirim server** (tinggi minimum, tinggi bawaan, tinggi maksimum). Nilai angkanya tidak ada di APK.
5. Panel komentar FlufPlay sudah dekat rangka YouTube: sudut atas 12 dp (sama dengan `panel_corner_radius`), pegangan geser, judul 20 sp dengan jumlah komentar, tombol tutup X, garis pemisah, dan panah kembali untuk balasan. Yang belum ada: peredupan di luar panel, urutan komentar, kolom tulis komentar, kecepatan putar, dan menu titik tiga berupa lembar bawah (sekarang `DropdownMenu`).

## Batas analisis

- Alat: REA (JADX 0.7.1) untuk kelas, `aapt2` untuk resource/layout/string, `dexdump` untuk memetakan string dan ID ke kelas ter-obfuscate.
- Nama kelas di luar paket `com.google.android...` sudah di-obfuscate; perannya **disimpulkan dari pemakaian**.
- Layout XML `engagement_panel_*` dan `comments_header` dipakai juga oleh layar tonton biasa. Bahwa Shorts memakai rangka yang sama didukung penanda `ENGAGEMENT_PANEL_SURFACE_SHORTS` dan ID `shorts-comments-panel`, tetapi susunan akhir yang tampil di Shorts tidak terbukti.
- Konstanta penting diperiksa ulang lewat bytecode bila JADX meragukan (lihat catatan di dokumen gestur).

## Peta panel

Kolom "Bukti": **K** = terbaca di kode, **L** = terlihat di layout/resource, **S** = hanya string atau nama enum.

| Panel | Wadah | Yang terbukti | Bukti |
|---|---|---|---|
| **Komentar dan balasan** | *Engagement panel*, surface `SHORTS`, ID panel `shorts-comments-panel`, host `reel_engagement_panel_overlay_root` | `mse.c(id, perintah)` mengambil elemen panel dari perintah server (`bfcw`) dan memasangnya ke host lewat `atom.h(...)`; host diset `clipChildren=false` dan `clipToPadding=false`. Header panel: baris judul tinggi minimum **48 dp**, tombol tutup **X** lebar 48 dp (ikon 24 dp, padding 12 dp), panah kembali opsional (untuk balasan), tombol urutan/filter, info, dan overflow opsional. Balasan memakai panel sendiri (`comment_replies_engagement_panel`). | K, L, S |
| **Menu titik tiga, kecepatan putar, daftar elemen** | `ReelsBottomSheetDialog` (`agsn`, dikonfigurasi `agsg`) | `DialogFragment` berbasis `BottomSheetBehavior`. Parameter dan bawaan ada di tabel berikutnya. Menu dibuka dari `ReelWatchFragmentImpl` ("reels bottom sheet menu"); fragmen menu `ReelMenuBottomSheetFragment` (`mof`); menu kecepatan putar `REEL_PLAYBACK_RATE_MENU_BOTTOM_SHEET_FRAGMENT`; jalur analitik `/youtube/app/shorts_overflow_menu/`. | K, S |
| **Bagikan** | `unified_share_panel_fragment` | Scrim layar penuh (menutup bila diketuk), lalu `AnchorableTopPeekingScrollView` berisi dua daftar (header dan isi). Panel "mengintip" setinggi **200 dp** (potret) atau **36 dp** (lanskap, `share_panel_peek_amount`), lalu ditarik ke atas untuk membuka penuh. Spinner di tengah-atas (margin atas 144 dp) saat memuat. | L |
| **Chat live (Shorts live/imersif)** | Overlay di atas video, bukan lembar | `immersive_live_chat_container`: umpan chat (`chat_feed`) dengan latar gradasi (`live_chat_in_immersive_gradient_background`), padding 4 dp, tepi memudar **28 dp**; papan ticker, spanduk yang bisa digeser, kuis; tombol "komentar lain" (panah bawah) **32 dp**, margin bawah 16 dp; wadah perintah di atas chat; blok galat berlatar gradasi dengan tombol coba lagi tinggi 36. Kolom tulis di bawah: `live_chat_reel_bottom_bar_container` → `live_chat_immersive_input_action_panel_wrapper`, sudut input **18 dp**. Ada event `LiveChatReelWatchInputFocusedEvent` saat kolom tulis difokuskan. Wadah `lightweight_engagement_in_reel_container` tinggi 500 dp ada di overlay yang sama. | L, S |
| **Penulis komentar (dialog)** | `comment_dialog_with_dismiss_button` | Kolom: area tutup di atas, bayangan **4 dp**, lalu pembungkus berisi teks judul, teks keterangan, dan kolom tulis. | L |
| **Header daftar komentar** | `comments_header` | Judul + jumlah komentar (baris tinggi minimum 48 dp, margin kiri 16 dp, jumlah berjarak 8 dp dari judul), tombol urutan (ikon "adjust" 24 dp), dan **kotak tulis** ("simplebox": avatar **40 dp** + teks tinggi minimum 40 dp, dengan pembatas 1 dp). | L |
| **Panel produk / belanja** | `bottom_panel_product`, `ReelVideoWithProductPreviewController`, `REEL_WATCH_ENDPOINT_SOURCE_PAUSED_STATE_SHOPPING` | Ada; isi tidak dibaca (di luar cakupan FlufPlay). | L, S |

## Engagement panel: parameter dari APK

| Parameter | Nilai | Bukti |
|---|---|---|
| Keadaan snap (enum) | `WRAP_CONTENT`, `FULL_BLEED`, `BELOW_THE_PLAYER`, `BELOW_THE_MINI_PLAYER`, `UNKNOWN` | S |
| Ketuk di luar panel (enum) | `NOOP`, `POP`, `POP_STACK`, `UNSPECIFIED` | S |
| Gaya latar (enum) | `DEFAULT`, `DARK`, `RAISED`, `USING_ENTITY_COLOR`, `CUSTOM_TRANSPARENCY` (+ varian lanskap) | S |
| Permukaan (enum) | `SHORTS`, `WATCH`, `LIVE_CHAT`, `BROWSE`, `SEARCH`, `NATIVE_ALL`, `LIVE_CREATION` | S |
| Pemicu tutup | `ON_ENGAGEMENT_PANEL_CLOSE_REQUESTED_TRIGGER`, penutupan otomatis | S |
| Sudut atas | 12 dp (`panel_corner_radius`) | L |
| Pegangan geser | tinggi 12 dp (`panel_header_drag_handle_height`) | L |
| Tinggi header | 62 dp (`engagement_panel_header_height`); garis bawah 1 dp | L |
| Margin vertikal judul | 8 dp | L |
| Tinggi kaki panel | 52 dp | L |
| Margin bawah isi | 24 dp | L |
| Tinggi pemudar mulus | 20 dp | L |
| Tinggi bilah chip filter | 48 dp | L |
| Jarak animasi header | 40 dp | L |
| Perilaku ukuran | `EngagementPanelSizeBehavior` (CoordinatorLayout; menangani gulir bersarang dan lempar) | K |
| Rasio tinggi maksimum bawaan (layar tonton) | 1,25 (`flexy_default_engagement_panel_max_height_aspect_ratio`) | L |
| Lebar pemutar minimum untuk panel lanskap | 500 dp | L |

`mse.c` memasang panel Shorts hanya bila perintah membawa elemen panel (ekstensi `bfdc`). `moz` adalah fragmen yang memuat `fragment_container_view` (susunan yang sama dengan `reel_watch_pane_fragment`) dan memasang pengamat untuk `app_engagement_panel_wrapper` bila sebuah flag konfigurasi (`aT.c().y.d`) aktif dan kondisi `bO()` false; arti `bO()` tidak dibaca, jadi apakah ini jalur panel Shorts atau layar lain belum pasti.

## `ReelsBottomSheetDialog`: kunci konfigurasi

Dibaca `agsn.qJ` dari argumen fragmen; bawaan di kolom kanan.

| Kunci | Bawaan | Arti |
|---|---|---|
| `RoundCorners` | `true` | Sudut atas membulat, radius **16 dp** (`reels_bottom_sheet_rounded_corner_radius`) |
| `DimBackground` | `true` | Latar di belakang diredupkan |
| `initExpanded` | `false` | Mulai dalam keadaan terbuka penuh |
| `DropShadow` | `false` | Bayangan header, tinggi **4 dp** |
| `MinHeight` | `0,0` | Tinggi minimum, pecahan layar |
| `MaxDefaultHeight` | `0,0` | Tinggi awal maksimum, pecahan layar |
| `MaxHeight` | `0,0` | Tinggi maksimum, pecahan layar |
| `Draggable` | `true` | Boleh digeser |
| `TopView` | `false` | Ada tampilan di atas lembar (`bottom_sheet_with_top_view`: slot `top_view_container` di atas `modal_view_container`) |
| `FitToScreen` | `false` | Pas satu layar |
| `UseModernCloseIcon` | `false` | Ikon tutup modern |
| `TextureCloseButton` | (string) | Penanda tombol tutup |

Ukuran terkait: padding lembar **6 dp**, padding item header **10 dp**, judul **20 sp** (spasi baris tambahan 4 sp). Pecahan tinggi diisi dari perintah server (`bmnq.e` dan `bmnq.f`, dibaca `mnr.b`), sehingga **angkanya tidak diketahui dari APK**. Penyusunan tinggi untuk komentar Shorts, bila memakai wadah ini, juga tidak terbukti.

## Hal yang tidak terbukti dari APK

- Apakah **video Shorts mengecil** dan naik ke atas saat panel komentar terbuka, atau panel menutupi video. Penanda snap `BELOW_THE_PLAYER` dan `BELOW_THE_MINI_PLAYER` menunjukkan panel *bisa* berada di bawah pemutar, tetapi enum itu dipakai bersama layar tonton dan keadaan yang dipilih komentar Shorts datang dari server. Kontainer Shorts diberi margin bawah setinggi `bottom_bar_container` oleh `moc.e` (diduga, dari perubahan `MarginLayoutParams`), yaitu tanda bahwa konten Shorts bisa diletakkan di atas bilah bawah, bukan bukti perilaku saat panel dibuka.
- Apakah scroll vertikal antar-Short **dikunci** saat panel terbuka. Mesin sentuh pager (`atiw`) hanya berhenti oleh tahan, cubit, atau multi-jari; hubungannya dengan panel tidak dibaca.
- Tinggi awal panel komentar, apakah ada kolom tulis komentar di dasar panel Shorts, dan urutan item di menu titik tiga: semuanya isi server.
- Durasi dan kurva animasi buka/tutup panel.

Cara menutup semuanya sekaligus: tangkapan layar dan `uiautomator dump` YouTube asli (Realme RMX3630 punya versi yang sama dengan APK; Redmi 21.42.243) pada Short dengan panel komentar terbuka. Itu membutuhkan membuka aplikasi di akun pengguna.

## FlufPlay sekarang vs YouTube

| Aspek | FlufPlay (`WatchPanels.kt`, `ShortsScreen.kt`) | YouTube |
|---|---|---|
| Wadah komentar | `EngagementPanel` di dalam Box, `fillMaxHeight(.7f)`, rata bawah | Engagement panel server-driven; keadaan snap dari server |
| Sudut | 12 dp atas | 12 dp (engagement), 16 dp (lembar menu Shorts) |
| Pegangan geser | 40 × 4 dp, abu `#606060` | Tinggi 12 dp (`panel_header_drag_handle_height`) |
| Tutup | Geser ke bawah (lebih dari 120 dp atau lempar > 1500 px/s), tombol **X**, tombol Back | Geser, tombol **X** 48 dp, ketuk di luar (bergantung perilaku) |
| Header | Judul 20 sp tebal + jumlah 16 sp, garis pemisah | Baris judul tinggi minimum 48 dp, header 62 dp, garis 1 dp |
| Peredupan di luar | **Tidak ada** | Scrim; perilaku ketuk-di-luar dikontrol enum |
| Balasan | Panel pengganti, panah kembali | Panel balasan terpisah, tumpukan (`POP_STACK`) |
| Urutan komentar | **Tidak ada** | Tombol urutan di header |
| Kolom tulis | **Tidak ada** | Kotak tulis (avatar 40 dp) |
| Menu titik tiga | `DropdownMenu` Material (di kanan-atas) | Lembar bawah (`ReelsBottomSheetDialog`) |
| Kecepatan putar | **Tidak ada** | Menu terpisah (`REEL_PLAYBACK_RATE_MENU`) |
| Bagikan | Intent sistem (`onShare`) | Panel mengintip 200 dp |
| Chat live | **Tidak ada** | Overlay imersif |
| Pager saat panel terbuka | `userScrollEnabled = false` | Tidak terbukti |

## Seharusnya seperti apa

Urutan mengikuti dampak pada kesan "mirip YouTube", dengan batas bukti di atas.

**Prioritas 1 — komentar**
1. **Peredupan di luar panel.** Scrim hitam semi-transparan di atas video bagian yang tidak tertutup panel; ketuk scrim menutup panel (perilaku `POP`). Pada balasan, ketuk scrim kembali ke daftar komentar (perilaku `POP_STACK`), bukan menutup semuanya.
2. **Header:** sudah mirip (judul, jumlah, X, panah kembali di balasan). Penyesuaian kecil: tinggi header setara 62 dp (judul tinggi minimum 48 dp ditambah margin vertikal 8 dp di atas dan bawah), area sentuh X selebar 48 dp, jarak jumlah dari judul 8 dp (sudah), dan garis pemisah 1 dp (sudah).
3. **Tombol urutan** (ikon "adjust" 24 dp) membuka lembar kecil berisi "Teratas" dan "Terbaru" (urutan memerlukan dukungan extractor atau permintaan lanjutan; belum diperiksa).
4. **Tinggi panel:** sekarang 70% layar. Pilihan aman: mulai di 70%, bisa ditarik hingga penuh (analog `FULL_BLEED`), dan menyusut kembali saat ditarik turun (`WRAP_CONTENT`/tutup). Mengecilkan video ke atas (analog `BELOW_THE_PLAYER`) menunggu bukti dari tangkapan layar.
5. **Pager dikunci saat panel terbuka** boleh dipertahankan; itu pilihan desain FlufPlay yang tidak bertentangan dengan APK.

**Prioritas 2 — menu dan bagikan**
6. Ganti `DropdownMenu` titik tiga dengan **lembar bawah** bersudut atas 16 dp, padding 6 dp, judul 20 sp, redup latar, bisa digeser, dan area tutup. Isi tetap: Salin tautan, Buka di YouTube, Tidak tertarik; tambah **Kecepatan putar** (0,3x/0,5x/1x/2x/3x mengikuti string `shorts_speed_control_*` milik kamera, hanya sebagai usulan nilai).
7. **Bagikan:** lembar mengintip 200 dp (potret) dengan daftar aplikasi; sementara itu cukup tetap pakai intent sistem.

**Prioritas 3 — nanti**
8. Kolom tulis komentar (butuh login dan permintaan tulis; di luar cakupan).
9. Overlay chat live imersif untuk Shorts live (gradasi bawah, umpan chat, tombol "komentar lain" 32 dp, kolom tulis di dasar). FlufPlay punya `LiveChat` untuk layar tonton; menyambungkannya ke Shorts live belum dikerjakan.

## Daftar bukti

Pemeriksaan REA dan `dexdump` 2026-10-31 (sesi ditutup; hasil bisa diulang).

| Temuan | Kelas / sumber | Bukti |
|---|---|---|
| Kelas "EngagementPanel" | `search_android_classes` | `ev_102686728a74b5052b0416ceff290cfd39ff3c9eb6c54dd060fe87248d77fd87` |
| Perilaku ukuran panel | `EngagementPanelSizeBehavior` | `ev_d504b7d3bdf92e31537d8bb26c29dca232f47184110d16b3a8c3a117fd2daa6d` |
| Konfigurasi lembar Shorts | `agsn.qJ` | `ev_837c9869e5582dbeea759258a7d7981d8b96fff7343a6e4a81a665f054f78b07` |
| Penyetel lembar Shorts | `agsg` | `ev_87b9f817742400aaeff530eaa3a0240a2368ecede5fb1b46dcf6f4288eb1d639` |
| Memasang panel ke host Shorts | `mse.c` | `ev_feb6b974efab156175bc7f1d8bfd38fffdcc3a8ef2b634fd97ac85b557ba2854` |
| Fragmen penampung Shorts | `moz.kz` | `ev_e4a5850568a5df80f81a1d2efa65fe24e4635f675f12d1627b3778a77fc648af` |
| Penyetel margin kontainer | `moc` (kelas), `moc.e`, `moc.a` | `ev_44ae290c24a2cd578b70155d2acd9ad424420fdef387514c069cbbb51b655c8a`, `ev_dba8c2be1ff6f19d5b9c3eca1daba5419d2f341695109b443a10e2b29289e367`, `ev_135ce93c3aa7facf1896c6b4df06ab185a6b913c54b087b9e63fabfd0d161d57` |
| Penanda enum snap, ketuk-luar, permukaan, gaya latar | `bfgh`, `bfgg`, `bfgl` | `dexdump -d` (`<clinit>`); tanpa ID bukti REA |
| Kelas pemanggil lembar (menu, kecepatan, elemen) | `iep.t`, `atez.F`, `mof`, `jbr.b` | `dexdump -d`; tanpa ID bukti REA |

Layout dan resource: `reel_engagement_panel_overlay`, `engagement_panel_layout`, `engagement_panel_root_view`, `engagement_panel_header`, `engagement_panel_title_header`, `engagement_panel_lower_view`, `comments_header`, `comment_dialog_with_dismiss_button`, `comment_replies_engagement_panel`, `live_chat_reel_bottom_bar_container`, `live_chat_in_immersive_overlay`, `bottom_sheet_with_top_view`, `bottom_sheet_modal_container_round_corners`, `unified_share_panel_fragment`. Layout `shorts_engagement_panel_top_bar` adalah milik **kamera pembuatan Shorts** (memuat tombol ganti kamera) dan tidak dipakai di sini.
