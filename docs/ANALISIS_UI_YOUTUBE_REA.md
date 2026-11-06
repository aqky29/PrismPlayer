# Analisis awal UI YouTube 21.43.523 dengan REA

## Identitas dan cakupan

- APK: `youtube-21-43-523.apk`
- SHA-256: `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`
- Package: `com.google.android.youtube`; versi `21.43.523` (`1561307650`); min SDK 29, target SDK 37.
- REA menggunakan JADX headless MCP 0.7.1, menemukan 53.653 kelas dan melaporkan 17.022 resource. Bukti: `ev_b2abc6c5b0628ea042312fe682746cd915f3a317ad6d28402f3adbd60d2107ed`.
- Ini analisis statis awal terhadap APK ini. Tidak ada pengamatan tampilan, interaksi, respons jaringan, variasi perangkat, eksperimen, atau verifikasi tanda tangan. Nama kelas yang ditemukan tidak membuktikan kelas tersebut digunakan pada layar tertentu saat aplikasi berjalan.

## Navigasi bawah / PivotBar

`com.google.android.libraries.youtube.rendering.ui.pivotbar.PivotBar` menyediakan operasi untuk membuat atau menambahkan item, mengambil style, menangani sentuhan dan perubahan konfigurasi. Inventaris kelas: `ev_35662fce5d2a73457029dbc7bd107138ae67cc4a7f8f17602cc9b5bc33b7970d`.

- Metode `c(avbz, boolean, int, bkij)` mengonfigurasi item, membaca styled attributes, memasukkan item ke daftar, dan menambahkan view; terdapat pembedaan tipe `PIVOT_BAR_NAVIGATION_TYPE_UNSELECTABLE_TAB`. Bukti: `ev_7c5c9d64c186f8457e4106cb39a1ccc54a728909fd187f958eca3c1100e7f49f`.
- Metode `j(TypedArray, boolean, boolean)` memilih drawable latar dari atribut, dengan kemungkinan membungkusnya memakai warna dan dimensi sebelum `setBackground`. Tidak cukup bukti untuk menyimpulkan efek visual tertentu. Bukti: `ev_0ec06283350a9efd6b1002ef78878e2618618ddce41dae8a779b5d6fa129f652`.
- `aapt dump --values resources` menunjukkan nilai resource default berikut melalui rantai referensi: `pivot_bar_height` → 48 dp; `pivot_bar_image_size` → 24 dp; `pivot_bar_image_mask_size` → 28 dp. Ini nilai resource, bukan pengukuran UI pada perangkat.
- Ada resource `navigation_bar_background_dark` → `#FF0F0F0F` dan `navigation_bar_background_light` → `#FFFFFFFF`; nama resource saja tidak membuktikan bahwa keduanya dipakai sebagai latar PivotBar dalam semua kondisi.

## Player dan time bar

- Kelas `com.google.android.libraries.youtube.player.ui.PlayerView` ada dalam APK; anggota yang terlihat sebagian memakai nama terobfuscasi, sehingga perannya yang lebih rinci belum terpetakan. Bukti: `ev_2c834364d077ffd125148316bf1e82b9c7451cbfdec4864c183f64e82f7c7c60`.
- `com.google.android.libraries.youtube.player.features.overlay.timebar.TimeBar` mempunyai `draw`, `onMeasure`, dan metode accessibility. Bukti inventaris: `ev_de46bbea1067a54501a719dcca589bdd648abe90141c42399b9c16d266d871cf`.
- `draw(Canvas)` menggambar beberapa bidang persegi, sebuah drawable alternatif pada kondisi tertentu, lingkaran pada kondisi tertentu, teks waktu, dan penanda posisi. Kode tidak cukup untuk memberi nama pasti semua lapisan yang dipakai setiap kondisi. Bukti: `ev_f57ea69a6f35d7dac2d5ee8551ec3cc10c39ec537c2f2f3d6b3980efe1d0d81c`.
- `onMeasure` memilih tinggi berdasarkan kondisi dan density; salah satu jalur menggunakan dua kali density. JADX memberi peringatan blok kode terduplikasi. Bukti: `ev_5750fbeda52401952c97086b264af08cb943b5f47c35a76347aa22b38a7d7311`.
- Resource `inline_time_bar_size` bernilai 2 dp; `inline_time_bar_scrubber_default_size` mengarah ke 12 dp, dan `inline_time_bar_scrubber_drag_size` 20 dp. Resource tersebut belum ditelusuri hingga pemakaian pada kelas `TimeBar`, sehingga tidak dapat diasumsikan identik untuk semua tampilan.

## Resource dan kelas lain yang ditemukan

- Resource layout meliputi `modern_miniplayer_controls_overlay`, `watch_while_time_bar`, `watch_while_timebar_overlay`, dan beberapa layout bottom sheet. Ini menunjukkan keberadaan aset, bukan hierarki layar aktual.
- Inventaris nama kelas mencakup `DefaultTabsBar`, `ReelPlayerView`, `ShortsPlayerView`, `YouTubeButton`, dan `YouTubeTextView`; belum ditelusuri alur layar dan penggunaannya.
- APK juga memuat `assets/youtube_sans.xml`, beberapa definisi bobot YouTube Sans, dan `assets/youtube_sans_bold_font.ttf`. Tidak semua teks UI pasti menggunakan font tersebut.

## Langkah lanjutan

Pilih layar untuk analisis lebih dalam: Home dan pivot bar, halaman pemutaran video, Shorts, atau mini player. Telusuri XML dan referensi kelas yang terlibat; untuk memastikan tampilan nyata, bandingkan dengan screenshot dan hierarchy pada perangkat dengan konfigurasi yang diketahui. Konten feed, nilai tema, ketersediaan fitur, dan variasi eksperimen tidak dapat dipastikan hanya dari resource dan metode yang diperiksa di sini.
