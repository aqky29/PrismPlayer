# Analisis preview History profil — YouTube 21.43.523

## Temuan dan batas bukti

APK `referensi/youtube-21-43-523.apk` memiliki identifier `FElibrary` dan `FEhistory` pada classes.dex, classes2.dex, dan classes4.dex. Keberadaan string ini tidak membuktikan endpoint yang dipakai layar profil, isi responsnya, atau aturan memilih Shorts. Pemeriksaan versi paket pada tablet menunjukkan YouTube 21.43.523 terpasang. UI hierarchy tidak berhasil dibaca (`null root node`); belum ada perbandingan runtime profil dan History dari keadaan yang sama dalam pemeriksaan ini. Observasi pengguna bahwa urutan aplikasi YouTube berbeda menjadi acuan masalah, bukan bukti bahwa Shorts selalu dikecualikan.

Analisis yang sudah ada di `ANALISIS_LIBRARY_PLAYLIST_HISTORY_YOUTUBE_REA.md` mencatat bahwa respons WEB `FElibrary` pada akun yang diperiksa memuat shelf playlist. Analisis itu tidak membuktikan apakah respons tersebut memiliki shelf History, dan tidak membuktikan bahwa APK Android memakai payload atau endpoint yang identik.

## Penyebab pada FlufPlay

- `MainActivity.kt` memasok `accountHistoryPreview(accountEntries)` ke `LibraryTab` ketika login; ketika logout memakai `watchHistory` lokal.
- `HomeFeed.historyResponse()` meminta `browseId=FEhistory` dengan client WEB. `AccountHistory.parse()` membaca isi rak `reelShelfRenderer` dan meratakannya menjadi entri individual bersama video lain; setiap rak memiliki `shortsShelfId`.
- `accountHistoryPreview()` hanya membatasi dua item per rak, mempertahankan urutan masuk, kemudian mengambil 10 item. Ini bukan algoritme yang menaikkan prioritas Shorts, tetapi bila rak Shorts muncul pertama, dua item rak tersebut menjadi kartu pertama. Tidak tersedia waktu tonton masing-masing item akun: `watchedAt` default 0, sehingga urutan kronologis sejati tidak dapat disusun dari data ini.
- `LibraryTab` menampilkan daftar berurutan dalam `LazyRow`, dengan thumbnail 16:9 untuk semua kartu dan perlakuan visual khusus Shorts. Sumber riwayat lokal menyimpan item terbaru di depan tanpa penyaringan Shorts.
- `AccountLibrary.playlists()` sudah mengambil `FElibrary`, tetapi `parsePlaylists()` mengambil playlist dan membuang informasi shelf History yang mungkin ada; keberadaan shelf itu belum diverifikasi.

## Arah menuju kesamaan referensi

1. Bandingkan profil YouTube dan FlufPlay pada akun dan orientasi sama, catat urutan video/Shorts dan bandingkan dengan halaman History penuh. Uji keadaan tontonan terakhir video, Shorts, beberapa Shorts berurutan, serta tidak ada video reguler; jangan menganggap Shorts pasti tidak pernah muncul.
2. Secara baca saja, periksa struktur respons `FElibrary` dari sesi yang sama: identitas shelf, endpoint navigasi dan tipe/urutan item. Bandingkan dengan `FEhistory`. Jangan menyimpan atau mengungkap cookie, token, ID akun atau judul tontonan pribadi tanpa kebutuhan. Client WEB yang dipakai FlufPlay dapat berbeda dari client Android referensi.
3. Jika terbukti `FElibrary` memiliki shelf History yang cocok dengan profil referensi, parse shelf itu secara spesifik dengan mempertahankan urutan dan metadata server. Gunakan state preview tersendiri dan tangani pergantian akun, refresh, error dan respons terlambat. Gunakan `FEhistory` untuk layar History penuh; jangan menggabungkan sumber akun dan lokal.
4. Jika shelf profil tidak tersedia atau tidak cocok, perlukan bukti tambahan respons/client Android sebelum menentukan aturan. Filter Shorts hanya sebagai fallback yang dinyatakan sebagai perkiraan dan hanya jika cocok dengan pengamatan; jangan memprioritaskan video secara acak atau mengklaim urutan kronologis yang tidak tersedia. Hindari otomatis memuat semua halaman tanpa batas untuk mengisi preview.
5. Setelah implementasi yang disetujui, uji parser memakai contoh respons yang disanitasi: shelf pertama Shorts, video campuran, shelf kosong/tidak dikenali, data tanpa metadata, continuation dan perubahan sesi. Verifikasi hasil di perangkat pada state yang sama, termasuk navigasi History penuh dan pembukaan kartu.

Tidak ada perubahan kode, build, instalasi, atau pengujian interaksi perangkat dalam analisis ini.
