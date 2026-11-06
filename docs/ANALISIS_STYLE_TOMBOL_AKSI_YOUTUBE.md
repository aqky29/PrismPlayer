# Style dan kesan volume tombol aksi YouTube

Lanjutan analisis statis `referensi/youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`. Pada tahap analisis awal, kode aplikasi tidak diubah.

## Maksud visual

Pengguna menjelaskan tombol dark mode referensi terasa "bervolume": permukaannya lebih terpisah dari latar, bukan sekadar kapsul abu-abu dengan satu warna. Istilah yang sesuai untuk target visual ini adalah kesan kedalaman atau tonal elevation. Deskripsi tersebut adalah persepsi/target pengguna, bukan bukti bahwa APK selalu memakai shadow atau elevation Android pada baris aksi.

## Yang terkonfirmasi pada FlufPlay saat ini

Sumber: `MainActivity.kt`, `WatchActionSurface`, `WatchAction`, `WatchChip`, dan `LikeDislikePill`.

- Background halaman `#0F0F0F`.
- Background tombol `#343434`, satu warna solid.
- Tinggi 32 dp, radius 16 dp.
- Ikon 18 dp, teks 12 sp dengan weight Medium.
- Padding horizontal tombol 10 dp; jarak ikon-teks 6 dp; jarak antar tombol 8 dp.
- Modifier tombol memakai `clip`, `background`, `clickable`, dan `padding`. Tidak ada lapisan highlight tepi, gradien permukaan, atau shadow yang ditambahkan oleh fungsi-fungsi ini.
- Pemisah Like/Dislike memakai `#4A4A4A`, lebar 1 dp dan tinggi 18 dp.

Ini membuktikan bahwa permukaan tombol aplikasi saat ini dibuat datar secara eksplisit. Ripple dari clickable adalah state interaksi tersendiri dan tidak membuktikan volume saat tombol diam.

## Yang terlihat dalam resource APK referensi

APK menyediakan keluarga token berikut:

- `yt_sys_color_baseline_tonal_background`
- `yt_sys_color_baseline_tonal_rim`
- `yt_sys_color_baseline_tonal_wash`
- `yt_sys_color_baseline_tonal_touch_light`
- varian `overlay`, inverse, hover, serta state layer.

Contoh nilai palet dark yang ditelusuri lewat alias resource:

| Token | Nilai yang terkonfirmasi |
|---|---|
| `dark_default_tonal_background` | Referensi ke putih alpha 10%, ARGB `#1AFFFFFF`. |
| `dark_default_overlay_tonal_rim` | Referensi ke putih alpha 15%, ARGB `#26FFFFFF`. |
| `dark_default_tonal_touch_light` | Referensi ke grey95 alpha 25%, ARGB `#40F2F2F5`. |
| Konstanta putih alpha 20% | ARGB `#33FFFFFF`; tersedia di keluarga token terkait. |

Nilai alpha ini bukan warna RGB final sebuah tombol: hasil komposit bergantung pada latar dan lapisan yang benar-benar dipakai. Jangan menyalinnya sebagai satu warna opaque atau mencampur token overlay dengan token normal tanpa pembuktian jalur pemakaiannya.

Cuplikan resource dan alias disimpan di [BUKTI_STYLE.txt](referensi-actionbar-youtube/BUKTI_STYLE.txt).

## Batas pemastian

Keberadaan token tersebut menunjukkan dukungan palet untuk permukaan tonal, rim, wash, dan interaksi. Belum terbukti bahwa seluruh token itu dipakai bersama pada pill baris aksi di setiap akun/video/config referensi. Tidak ada klaim bahwa semua pill memiliki shadow, bevel, gradien tertentu, atau elevation dengan nilai dp tertentu.

Layout `slim_metadata_button` yang ditelusuri dari controller lama justru berupa ikon di atas teks, bukan pill horizontal modern. Layout ini memakai background dari atribut tema. Karena itu dimensi dan background layout legacy tersebut tidak diperlakukan sebagai spesifikasi pasti pill yang dimaksud pengguna. Kelas `YouTubeButton` yang diperiksa sendiri terutama menambahkan perilaku aksesibilitas, bukan bukti renderer efek volume.

Pencarian terarah literal token pada DEX tidak menghasilkan pengikat yang cukup untuk menyimpulkan renderer modern pill. Kemungkinan jalur model/renderer lain belum dituntaskan; ini bukan bukti bahwa efek tersebut tidak ada.

## Implikasi untuk penyamaan UI berikutnya

Arah visual yang perlu diperiksa adalah pemisahan tone permukaan dari latar, rim/highlight halus, perubahan pencahayaan permukaan, serta respons pressed/toggle. Sekadar membuat `#343434` lebih terang tidak otomatis menghasilkan kesan volume. Ikon, label, padding, dan siluet tetap harus dibandingkan secara terpisah.

Implementasi highlight/shadow/gradien tertentu masih merupakan keputusan rekonstruksi yang harus divalidasi terhadap tampilan YouTube sebenarnya. Analisis ini belum menetapkan efek tersebut sebagai fakta APK maupun mengubah UI FlufPlay.

Analisis tetap dibatasi core 0, prioritas rendah, kuota CPU 50% per scope, satu worker decompiler, heap 768 MiB, dan total RAM scope maksimal 1100 MiB.

## Implementasi lanjutan setelah diminta pengguna

`WatchActionSurface.kt` menerapkan interpretasi tonal depth yang ringan: tinggi pill 36 dp, radius setengah tinggi, gradasi default dari `#3D3D42` ke `#29292D`, rim putih halus di bagian atas yang melemah lalu menjadi gelap di bawah, dan shadow 2 dp. State pressed memakai permukaan lebih terang dan shadow 1 dp; state terpilih memakai tone tersendiri. Ikon menjadi 20 dp, teks 14 sp, padding horizontal 12 dp.

Brush disimpan melalui drawWithCache, transisi warna singkat 100 ms, tanpa blur/backdrop atau shader offscreen. Angka dan warna tersebut adalah keputusan rekonstruksi untuk target visual pengguna, bukan nilai yang diklaim telah diekstrak sebagai spesifikasi pasti pill modern YouTube. Pemeriksaan visual langsung di Android masih diperlukan setelah APK dipasang.

### Revisi penghalusan gradasi dan rim

Setelah umpan balik pengguna, fill menggunakan LinearGradient native dengan Paint anti-alias dan dithering, enam stop warna dengan perubahan tone yang lebih lembut. Default top/bottom menjadi `#3A3A3F` / `#2D2D32`. Rim putih memudar ke transparan tanpa garis hitam bawah; stroke utama 0,6 dp dengan lapisan feather 1,5 dp beropasitas rendah. Radius rim disesuaikan dengan inset sehingga sejajar dengan kurva kapsul. Warna, kekuatan rim, dan elevasi bertransisi 160 ms. Shadow normal 1,75 dp, pressed 1 dp. Paint dan shader tetap dibuat di drawWithCache.

Dithering dimaksudkan untuk membantu mengurangi banding; hasil visual akhirnya bergantung pada jalur render dan layar perangkat. Pemeriksaan visual langsung belum dilakukan pada revisi ini.

### Koreksi berdasarkan pasangan screenshot YouTube dan FlufPlay

Pengguna memberikan `IMG_20261102_065059.jpg` (YouTube, kotak merah) dan `IMG_20261102_065142.jpg` (FlufPlay, kotak hijau), keduanya 2560 × 1600. Sampel area interior tombol Share/Bagikan menunjukkan mode warna YouTube sekitar RGB 50/50/50 di atas, 46/46/46 di tengah, dan 44/44/44 di bawah. FlufPlay pada foto sekitar 58/57/62, 53/52/57, dan 47/46/51. Tepi atas YouTube pada satu profil vertikal memuncak sekitar 65/65/65; FlufPlay sekitar 78/77/82. Ini pengukuran screenshot JPEG, bukan jaminan nilai token tema asli.

Revisi berikutnya menghilangkan bias biru/ungu di seluruh state: fill default `#343434` ke `#2B2B2B`, dengan profil blend yang ditujukan untuk mendekati bagian atas/tengah/bawah referensi. Kekuatan rim normal diturunkan menjadi 0,065, stroke 0,5 dp, feather 1 dp pada alpha 24/255, dan inset rim 0,45 dp. Shadow normal menjadi 0,5 dp. Dithering dan transisi 160 ms dipertahankan. Ukuran tombol dan daftar aksi tidak diubah pada revisi warna ini. Validasi visual di perangkat setelah dipasang tetap diperlukan.
