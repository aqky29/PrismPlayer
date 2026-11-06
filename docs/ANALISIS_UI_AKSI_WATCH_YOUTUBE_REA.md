# Baris aksi watch (Like/Dislike/Share/Save/Download) — YouTube 21.43.523 vs FlufPlay

APK `youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`. Sumber: `aapt2 dump resources/xmltree`, REA/JADX 0.7.1 (decompile `classes4.dex`), `dexdump`. Analisis statis; tidak ada pengamatan runtime YouTube di perangkat. Nama resource diterjemahkan dari tabel resource APK.

## Ringkasan

- Baris aksi watch di APK memakai `SlimMetadataButtonContainerLayout` di dalam `slim_video_action_bar`, dengan margin kiri/kanan **14 dp**. Container membagi tombol ke 5 kolom (ponsel) atau 6 kolom (lebar), lebar tiap tombol **66–96 dp**.
- Tombol `slim_metadata_button`/`slim_metadata_offline_button` adalah tombol **vertikal** (ikon di atas label), label **12 sp**, ikon **24 × 32 dp**, padding atas/bawah **8 dp**, latar hanya ripple `ytTouchFeedbackCircle` — bukan pill berisi warna.
- Sumber lain di APK menyediakan gaya pill horizontal: `content_pill` (**32 dp**, radius **16 dp**, teks **14 sp**, padding **8/10 dp**, jarak ikon **6 dp**, margin **8 dp**) dan `fullscreen_engagement_action_bar_button` (ikon + teks horizontal, jarak **6 dp**, tinggi baris **48 dp**).
- Token desain yang konsisten: `yt_sys_measurement_action_height_compact` = **32 dp**, `yt_sys_measurement_radius_comfortable` = **16 dp**, `yt_sys_measurement_icon_size_compact` = **18 dp**, `extra_small_font_size` = **12 sp**, `small_font_size` = **14 sp**.
- Warna pill gelap: `ytOverlayButtonSecondary` → `yt_sys_color_baseline_mobile_dark_default_default_overlay_button_secondary` → putih 10% (`transparent_white1_alpha10`). Di atas latar `#0F0F0F`, hasilnya kira-kira **#272727** — sama dengan `Surface` FlufPlay saat ini.

## Bukti resource

| Hal | Nilai di APK | Bukti |
|---|---|---|
| Layout baris aksi | `slim_video_action_bar` = `res/M_J.xml`; `HeightTransitionLayout` + `SlimMetadataButtonContainerLayout` id `button_container`; marginStart/End **14 dp** | `aapt2 dump xmltree --file res/M_J.xml` |
| Container tombol | `SlimMetadataButtonContainerLayout`: maks. 5 kolom (ponsel) / 6 kolom (lebar), lebar tombol min **66 dp**, maks **96 dp** | `classes4.dex` → `SlimMetadataButtonContainerLayout`; `slim_metadata_button_min_width`/`max_width` |
| Tombol ikon+label | `slim_metadata_button` = `res/jKs.xml`: orientasi vertikal, padding atas/bawah **8 dp**, ikon **24 × 32 dp**, teks **12 sp**, background ripple | `aapt2 dump xmltree --file res/jKs.xml` |
| Tombol like/dislike | `slim_metadata_offline_button` = `res/nWV.xml`: `OfflineArrowView` **24 × 32 dp**, teks **12 sp**, padding atas/bawah **8 dp** | `aapt2 dump xmltree --file res/nWV.xml` |
| Gaya pill horizontal generik | `content_pill` = `res/0Oh.xml`: tinggi **32 dp**, radius **16 dp**, teks **14 sp**, paddingStart **8 dp**, paddingEnd **10 dp**, drawablePadding **6 dp**, margin **8 dp** | `aapt2 dump xmltree --file res/0Oh.xml` |
| Tombol fullscreen | `fullscreen_engagement_action_bar_button` = `res/s8K.xml`: ikon + teks horizontal, jarak **6 dp**, tinggi baris **48 dp** | `aapt2 dump xmltree --file res/s8K.xml`; `fullscreen_engagement_action_bar_height` |
| Tombol aksi lama | `action_button` = `res/pWr.xml`: `CardView` **80 × 56 dp**, radius **9 dp**, teks **10 sp** | `aapt2 dump xmltree --file res/pWr.xml` |
| Warna pill | `content_pill_background` memakai `content_pill_color` → `yt_dark_blue`; latar tombol overlay memakai putih 10% | `res/0X7.xml`; `yt_sys_color_baseline_mobile_dark_default_default_overlay_button_secondary` |

Catatan: `content_pill` adalah layout pill generik yang dipakai untuk chip konten; tidak ditemukan bukti langsung bahwa baris aksi watch memakai layout itu. Pill horizontal pada screenshot paling dekat dengan geometri `content_pill` + struktur ikon/teks `fullscreen_engagement_action_bar_button`. Ini **disimpulkan**, bukan dipastikan dari satu layout.

## Perbandingan dengan FlufPlay sebelum perubahan

| Properti | YouTube (token/varian pill) | FlufPlay sebelum |
|---|---|---|
| Tinggi pill | 32 dp (compact) | 36 dp |
| Radius | 16 dp (comfortable) | 50% dari 36 dp |
| Ikon | 18 dp (compact) / 24 dp (slim metadata) | 20 dp |
| Teks label | 12 sp (slim metadata) / 14 sp (content pill) | 14 sp |
| Padding horizontal | 8–10 dp (content pill) / 14 dp margin container | 12 dp |
| Jarak ikon–teks | 6 dp | 6 dp |
| Jarak antar-pill | 8 dp | 8 dp |
| Warna isi | putih 10% di atas `#0F0F0F` ≈ `#272727` | `Surface` = `#272727` |

Kesimpulan: warna sudah benar; yang paling berbeda adalah **tinggi, ukuran ikon, ukuran teks, dan padding** — pill FlufPlay lebih besar/gemuk daripada referensi.

## Perubahan yang diterapkan

Di `MainActivity.kt`:

- `WatchChip` dan `WatchAction`: tinggi **36 → 32 dp**, radius **16 dp**, ikon **20 → 18 dp**, teks **14 → 12 sp**, padding horizontal **12 → 10 dp**, jarak ikon–teks tetap **6 dp**.
- `LikeDislikePill`: tinggi **32 dp**, radius **16 dp**, ikon **18 dp**, teks **12 sp**, padding **10 dp**, pemisah **18 dp**, jarak ikon–teks **6 dp**.
- Warna `Surface` `#272727` dipertahankan karena setara dengan overlay button secondary YouTube di atas latar gelap.

Perbedaan yang masih ada dan belum diubah: susunan/isi tombol (FlufPlay punya Remix, Klip, Salin link; YouTube punya Ask, Hype), margin baris 12 dp vs 14 dp, dan tidak ada elevation 1,5 dp seperti `content_pill`. Ini bukan bagian dari permintaan “style pill”, jadi dibiarkan.

## Yang belum diketahui

- Varian layout persis yang dipakai screenshot (modern/server-driven button row) tidak bisa dipastikan dari resource saja; APK memuat beberapa varian pill dan tombol dapat berasal dari `buttonRenderer`/`buttonViewModel` server.
- Apakah label aksi di perangkat memakai 12 sp (slim metadata) atau 14 sp (content pill); keduanya ada di APK.
- Apakah ada elevation/shadow pada pill di perangkat; `content_pill` memakai 1,5 dp, sedangkan `slim_metadata_button` tidak.
