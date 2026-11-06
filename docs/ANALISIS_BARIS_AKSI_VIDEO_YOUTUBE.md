# Analisis baris aksi video YouTube 21.43.523

Analisis statis pada APK lokal `referensi/youtube-21-43-523.apk`. SHA-256: `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`.
Tidak mengubah kode aplikasi FlufPlay, memasang APK, atau menjalankan YouTube. Cuplikan hasil dekompilasi disimpan di [BUKTI_KODE.txt](referensi-actionbar-youtube/BUKTI_KODE.txt), dengan nama kelas dan nomor baris asal.

## Kesimpulan

Baris tombol referensi tidak dibentuk dari satu susunan tetap seperti implementasi FlufPlay saat ini. Presenter menerima daftar model tombol, memilih controller berdasarkan tipe model dan kondisi, kemudian menambahkan view menurut urutan daftar tersebut. Jumlah dan jenis tombol yang dirender dapat berbeda; label, ikon, endpoint, dan status tombol juga dapat berubah.

Yang terlihat langsung adalah model dan kode render di client. Pemilihan daftar awal oleh backend YouTube, aturan eksperimen, dan syarat lengkap setiap tombol belum dapat dibuktikan hanya dari jalur APK yang diperiksa. Karena itu tidak tepat mengklaim bahwa suatu tombol selalu tersedia untuk semua video, atau menyimpulkan seluruh aturan Premium, wilayah, usia, hak cipta, Remix, atau Klip dari keberadaan resource saja.

## Bukti langsung di APK

1. `qmm.b()` mengambil `((bmqt) this.k).c`, lalu meneruskannya ke `qly.e(...)` dan `qly.g()`. `qmu.b()` melakukan pola sama dengan `bmqy.c`. Ini daftar model, bukan pemanggilan tombol dengan urutan teks yang ditulis langsung di presenter.
2. `qly.e(Iterable, String, auhp)` mengiterasi daftar model `bmqi`. Field discriminator memilih `bmqh`, `bmqk`, `bmqg`, `bbxh`, atau `bfcw`. `qly.h(...)` membuat controller yang sesuai dan menambahkannya ke daftar internal.
3. `qly.g()` menambahkan view dari daftar controller secara berurutan. Container ditampilkan berdasarkan apakah ada child. Tidak ada penyisipan otomatis semua tombol yang dikenal di metode ini.
4. Tombol umum `qll.c(...)` mendapatkan ikon, teks, warna, accessibility, dan informasi endpoint dari model `bdci`. Label bukan sekadar teks tetap yang sama untuk semua tombol atau video.
5. `qmi`/`qlm` menangani status toggle beserta ikon/teks/endpoint yang berbeda. Pada `qma.k(...)`, dua cabang model diberi resource ID `like_button` dan `dislike_button`, dan statusnya terhubung ke pembaruan rating.
6. Tombol offline/Unduh memiliki controller `qmd`, `OfflineArrowView`, data offline lokal, dan cabang untuk model promosi/upsell. `qmd.e(...)` mengubah kondisi interaksi, sementara `qmd.g(...)` memilih teks berdasarkan status, termasuk resource label unduhan dijeda. Ini bukan bukti bahwa semua akun boleh mengunduh atau bahwa tombol selalu muncul.
7. `qlq.d(boolean)` memilih satu dari dua model tombol account-link. Jika keduanya tidak tersedia, controller mencatat bahwa tidak ada tombol untuk ditampilkan. Ini contoh konkret variasi tombol berdasarkan state, tanpa mengasumsikan arti bisnis semua field obfuscated.

## Resource yang diverifikasi dengan aapt

| ID | Nama resource |
|---|---|
| `0x7f0b02f7` | `id/button_container` |
| `0x7f0b0ab9` | `id/like_button` |
| `0x7f0b0674` | `id/dislike_button` |
| `0x7f0e0780` | `layout/slim_video_action_bar` |
| `0x7f0e0784` | `layout/slim_video_scrollable_action_bar` |
| `0x7f0e0785` | `layout/slim_video_scrollable_action_bar_reduced_margins` |
| `0x7f140934` | `string/offline_button_label_paused` |

`qmu` memilih dua varian scrollable berdasarkan `jph.aJ(...)`. `qmm` memiliki cabang konfigurasi container dengan nilai 5 atau 6 berdasarkan `afvg.t(context)`. Arti lengkap kondisi flag ini belum ditelusuri; tidak dinyatakan sebagai aturan pasti ponsel/tablet. Kelas `SlimMetadataScrollableButtonContainerLayout` juga menghitung ukuran child berdasarkan lebar dan jumlah child, serta mendukung arah layout RTL.

Adanya varian layout dan resource tidak membuktikan bahwa semua varian dipakai pada setiap layar atau akun, termasuk screenshot yang diberikan.

## Perbandingan dengan FlufPlay saat diperiksa

Sumber: `flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt`, fungsi `VideoDetails`, bagian `Row(...horizontalScroll(...))`.

| Bagian | Implementasi FlufPlay |
|---|---|
| Suka/tidak suka | Selalu ditambahkan; jumlah/label dan rating dapat berubah. |
| Live chat / Replay chat | Kondisional berdasarkan `isLive` dan `showReplayChat`. |
| Bagikan | Selalu ditambahkan. |
| Remix | Selalu ditambahkan; callback saat ini menutup player dan membuka tab Shorts. Ini belum merupakan alur remix video sumber YouTube. |
| Unduh | Selalu ditambahkan; memakai fitur unduh lokal aplikasi. |
| Klip | Selalu ditambahkan; callback memakai fungsi pembuat tautan klip aplikasi, bukan bukti dukungan Clip resmi pada semua video. |
| Simpan | Selalu ditambahkan; label dan ikon mengikuti `engagement.saved`. |
| Salin link | Selalu ditambahkan sebagai tombol mandiri. |

Jadi pernyataan bahwa seluruh bar sama persis untuk semua video perlu diperinci: chat memang sudah kondisional dan state Suka/Simpan berubah, tetapi sebagian besar keanggotaan daftar dan urutannya tetap. Komentar kode yang menyebut urutan persis YouTube asli terlalu kuat dibanding bukti yang ada.

Perlu dibedakan antara tombol yang tidak dirender, tombol yang dinonaktifkan/berubah status, dan tombol yang sekadar berada di luar viewport sehingga perlu digeser. Ketiganya bukan kondisi yang sama.

## Arah implementasi dari analisis awal

- Bentuk daftar model aksi untuk video aktif, lalu render daftar tersebut dalam row scrollable.
- Pisahkan ketersediaan aksi, status aktif/toggle, label/ikon, urutan, dan callback.
- Tampilkan chat menurut kemampuan live/replay yang tersedia.
- Jangan menganggap semua video mendukung Remix atau Clip resmi. Jika data ketersediaannya belum ada, tandai sebagai belum diketahui; jangan menyimpulkan dukungan hanya dari URL video biasa.
- Bedakan fitur lokal seperti unduhan atau salin tautan bertimestamp dari aksi resmi YouTube. Fitur lokal tetap dapat ditawarkan dengan penamaan/perilaku yang jelas.
- Untuk kesamaan persis dengan referensi pada video tertentu, masih diperlukan data respons watch/next dan pemeriksaan tampilan aktual untuk video, akun, serta konfigurasi yang sama.

## Batas resource dan ketelitian

Dekompilasi hanya pada kelas terpilih dari salinan DEX yang hash APK-nya sesuai. JADX memakai satu worker, core 0 (`taskset`), prioritas nice 15, heap 768 MiB, dan cgroup `CPUQuota=50%`, `MemoryMax=1100M`, `TasksMax=48`. Batas cgroup diverifikasi saat proses berjalan. Salah satu sampel RAM proses/cgroup sekitar 368 MiB; ini sampel, bukan klaim puncak seluruh analisis.

Hasil dekompilasi satu DEX memiliki warning hierarchy/type inference karena kelas dependensi di DEX lain tidak semuanya dimuat. Kesimpulan di atas dibatasi pada struktur yang terbaca jelas, resource yang cocok, dan jalur bind/render yang diperiksa. Tidak ada klaim hasil runtime atau aturan backend yang tidak terlihat.

## Implementasi lanjutan di FlufPlay

Setelah pengguna meminta penerapan, baris aksi di `VideoDetails` memakai daftar dari `WatchActionModel.kt`. Kebijakan ini mengikuti kapabilitas yang tersedia di client, bukan mengklaim sudah membaca daftar aksi resmi Android YouTube dari backend.

- Video live: rating, chat bila tersedia, bagikan, simpan, dan menu tambahan. Unduhan file biasa tidak ditawarkan untuk siaran yang masih berlangsung.
- Video/replay: chat replay ditawarkan setelah tersedia; Unduh ditawarkan bila ada sumber media. Simpan berubah menjadi Tersimpan mengikuti state akun.
- Salin link dipindah ke menu tambahan. Video berdurasi diketahui juga menawarkan Salin link dari waktu ini.
- Remix dan Klip resmi tidak dipaksakan tampil karena aplikasi belum memiliki alur resmi yang sesuai. Callback pembuat tautan bertimestamp dipertahankan sebagai aksi salin waktu dengan nama yang sesuai.
- Scroll/menu di-reset ketika video berganti. Model aksi memiliki pengujian untuk live, replay, dan sumber yang tidak menyediakan kemampuan terkait.

Kesamaan daftar/urutan persis dengan respons backend YouTube tetap di luar klaim implementasi ini.
