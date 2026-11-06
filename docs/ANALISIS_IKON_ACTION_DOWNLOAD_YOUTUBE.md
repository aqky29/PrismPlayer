# Analisis ikon Download dan Downloaded pada watch

Tanggal: 2026-11-02. Analisis statis APK `referensi/youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`, dibanding kode FlufPlay dan screenshot referensi yang sudah ada. Tidak ada interaksi perangkat atau perubahan aplikasi. APK di screenshot belum diverifikasi versinya secara terpisah.

## Temuan

- **Downloaded pada screenshot** memperlihatkan lingkaran putih dengan panah bawah gelap yang ujungnya membulat. Lihat [screenshot penuh](evidence/download-watch-implementation/reference-watch-downloaded.png) dan [potongan yang diperbesar](evidence/download-action-icon-analysis/screenshot-crop.png). Ini bukti bentuk pada layar yang diamati; belum membuktikan pemetaan resource yang dipakai renderer atau seluruh variasi YouTube.
- **Downloaded FlufPlay** memakai `yt_w_downloaded` di `VideoDetails` dan `OfflineVideoDetails`. Berkas PNG pada lima density identik byte demi byte dengan APK `yt_fill_downloaded_black_24` (`0x7f080dd3`), yang bergambar lingkaran berisi **centang**. Asetnya asli APK, tetapi berbeda bentuk dari screenshot tersebut. Ini menjelaskan perbedaan yang pengguna lihat.
- Kandidat APK yang paling mendekati screenshot adalah `yt_fill_experimental_arrow_down_circle_black_24` (`0x7f080de8`): lingkaran dengan panah dan ujung membulat. Kandidat lain `yt_fill_arrow_down_circle_black_24` (`0x7f080d90`) memiliki ujung lebih bersudut. Bandingkan [experimental](evidence/download-action-icon-analysis/experimental_circle-4.png), [non-experimental](evidence/download-action-icon-analysis/arrow_circle-4.png), dan [centang saat ini](evidence/download-action-icon-analysis/downloaded-4.png). Kecocokan experimental adalah **inferensi visual**, bukan bukti resource tersebut dirender pada screenshot; resolusi dan antialiasing hasil screenshot membatasi pengukuran.
- **Download FlufPlay** menggunakan `drawable/yt_w_download.xml`, sebuah vector 24dp buatan proyek dengan ujung membulat. APK mempunyai `yt_outline_download_black_24` (`0x7f0810cb`, panah dan garis lurus tipis) serta `yt_outline_experimental_download_black_24` (`0x7f0811e9`, bentuk tebal membulat). Lihat [perbandingan kandidat](evidence/download-action-icon-analysis/compare.png). `OfflineArrowView` memakai `yt_outline_download_black_24` sebagai default melalui `TypedArray.getResourceId`, tetapi atribut dapat menimpanya. Belum ada screenshot tombol Download dalam keadaan belum diunduh yang memastikan pilihan renderer watch terbaru, sehingga jangan menyimpulkan salah satu sebagai yang pasti.
- Keduanya di FlufPlay ditampilkan melalui `WatchChip` dengan kotak gambar 18dp, dalam chip tinggi 32dp, padding horizontal 10dp, dan jarak ikon–teks 6dp (`MainActivity.kt`). Nama resource APK 24 mengacu pada ukuran nominal resource, bukan bukti bahwa semua watch action merendernya pada 24dp. Screenshot tidak cukup untuk menyimpulkan ukuran dp tanpa density dan pengukuran runtime yang sesuai.

## Bukti tambahan

- `OfflineArrowView.l` pada [DEX](evidence/download-parity-v2/OfflineArrowView-dex.txt) memuat default `0x7f0810cb` untuk field `h` dan `0x7f080dd3` untuk field `i`; konfigurasi atribut masih dapat memilih resource lain. Ini bukti kelas legacy tersebut, bukan bukti bahwa setiap watch action memakai kelas atau default yang sama.
- [Cuplikan tabel resource APK](evidence/download-action-icon-analysis/arrow-resources.txt), [inventaris kandidat](evidence/download-action-icon-analysis/arrows.json), dan [inventaris download lama/baru](evidence/download-action-icon-analysis/inventory.json). Field `local` mengacu ke file sementara analisis; path `res/...` dan SHA-256 mengidentifikasi entri di APK.
- Kandidat panah bulat tersedia pada 24, 36, 48, 72 dan 96 px, sesuai density mdpi sampai xxxhdpi. Contoh experimental: `res/d1d.png` untuk 24px; lengkapnya dalam inventaris. Downloaded centang saat ini diverifikasi pada kelima density dengan SHA-256.

## Rekomendasi bila diminta implementasi

1. Gunakan varian `yt_fill_experimental_arrow_down_circle_black_24` untuk status Downloaded watch sebagai kandidat paling dekat dengan screenshot, dengan tint yang sama. Pertahankan seluruh density dan jangan mengganti status download itu sendiri.
2. Pilih varian Download setelah screenshot referensi keadaan belum diunduh atau bukti renderer watch yang mengikat resource. `OfflineArrowView` default dapat menjadi kandidat awal, namun tidak membuktikan varian screenshot modern.
3. Uji ukuran ikon, warna, padding, dan state aktif/selesai dengan screenshot di kondisi dan density yang sama sebelum mengklaim identik. Analisis ini tidak mengubah kode, aset aplikasi, atau APK.

## Batas resource

Pemindaian tabel resource menggunakan `aapt` dengan `taskset -c 0`, `nice -n 19`, dan batas virtual memory `ulimit -v 524288` (512 MiB). Ekstraksi kandidat dan penyusunan gambar menggunakan Python dengan batas yang sama. Tidak dilakukan dekompilasi seluruh APK atau build.
