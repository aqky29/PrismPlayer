# Ikon Shorts dan unduhan FlufPlay — 2 November 2026

## Bukti referensi dan batas kesimpulan

Referensi: `referensi/youtube-21-43-523.apk`. Analisis memakai skill leanrea: inventaris `aapt2`, ekstraksi ZIP terbatas pada aset terkait, pemeriksaan gambar, dan layout offline. Tidak melakukan dekompilasi seluruh APK.

**Terlihat langsung:** APK memuat keluarga lama `ic_right_*_shadowed` dan keluarga `yt_fill_experimental_*`. Keluarga lama memiliki jempol dengan manset terpisah dan state aktif biru dengan garis putih. Keluarga experimental memiliki jempol dan gelembung komentar membulat serta panah bagikan membulat. Pemetaan path, density, dan SHA-256 ada di [resources.json](evidence/shorts-download-2026-11-02/resources.json). Empat ikon experimental diekstrak utuh pada mdpi sampai xxxhdpi; tint putih diterapkan saat ditampilkan.

**Pilihan implementasi, diperbarui 2026-11-02:** kolom aksi memakai vector experimental heart outline putih, heart terisi merah `#FF0033` saat liked, text bubble outline dan share outline; slot dislike Shorts dihapus. Ukuran tetap 32dp dan label 12sp. Header search/more dan avatar channel tetap. Pemetaan berasal dari analisis statis APK dan belum diverifikasi di perangkat.

**Belum terbukti:** APK mengandung beberapa keluarga ikon dan overlay Shorts online dapat ditentukan server. Kehadiran aset tidak membuktikan keluarga yang sedang dipilih server pada semua akun. Realme mengonfirmasi `versionName=21.43.523`, tetapi terputus saat mencoba melihat Shorts. Karena itu kecocokan visual penuh, warna state aktif, dan geometri overlay online belum diverifikasi pada layar referensi. Geometri overlay Shorts offline tidak dijadikan patokan overlay online.

**Terlihat langsung:** `layout/offline_video_item` (`res/Bki.xml`, ID `0x7f0e052b`) memiliki thumbnail berasio tetap, judul, metadata, ikon offline, dan slot progress. Lebar thumbnail default `dimen/offline_video_item_thumbnail_width` adalah 128dp dengan varian tablet/lanskap. Dump layout ada di [offline-video-item.txt](evidence/shorts-download-2026-11-02/offline-video-item.txt). String `offline_videos_title`, `offline_video_not_playable`, dan pilihan kualitas offline tersedia. Layout ini mendukung daftar offline internal, tetapi tidak membuktikan keseluruhan urutan navigasi atau implementasi penyimpanan YouTube.

## Masalah pada sumber FlufPlay sebelum perubahan

`SavedDownloadsDialog` menampilkan daftar di AlertDialog. `SavedDownloads.openIntent` memakai ACTION_VIEW sehingga pemutaran diserahkan ke aplikasi lain. `DownloadService` sudah mengunduh dan menggabungkan video/audio, menyalin ke URI SAF pilihan pengguna, memverifikasi ukuran, lalu mencatat file. Jadi file unduhannya sudah ada; yang kurang adalah layar daftar dan pemutaran lokal internal.

## Perubahan

- Layar Unduhan terpisah di aplikasi, diakses dari tab Anda, sesudah memilih lokasi simpan, banner hasil, dan notifikasi.
- Menampilkan progres proses tunggal, pembatalan, daftar persisten, nama, jenis media, ukuran, dan area thumbnail 128dp.
- Pemutar internal Media3 menggunakan URI file tersimpan dengan sumber progressive, bukan resolusi ulang URL YouTube. Video MP4 dan audio MP3 memakai alur yang sama; seek, play/pause, dan pesan gagal tersedia.
- File yang selesai ditambahkan ke daftar setelah penyalinan dan verifikasi; notifikasi selesai membuka file itu di aplikasi.
- Pemutar online dijeda saat berpindah dari layar aplikasi menuju Unduhan. Pemutar lokal dijeda saat activity berhenti dan dilepas saat halaman putar ditutup.
- Thumbnail dibaca dari file lokal pada dispatcher IO, satu retriever sekaligus, maksimum 256 × 144 piksel, dengan cache 12 gambar. Android 8.0 memakai ikon fallback agar tidak mengekstrak frame ukuran penuh. Posisi putar disimpan ketika halaman ditutup atau activity berhenti.
- URI pemutaran dibatasi ke dokumen content dan file lokal; URL HTTP/HTTPS dan file dengan host remote ditolak.
- Daftar unduhan lama yang sudah tercatat dipertahankan. File lama yang belum pernah tercatat tidak diimpor otomatis dari seluruh storage. Lokasi SAF pilihan pengguna tetap digunakan.

## Verifikasi

Build/test/lint dibatasi dengan `taskset -c 0`, nice 15, Java 21, satu Gradle worker, compiler Kotlin in-process, `-Xmx1536m`, dan `-XX:ActiveProcessorCount=1`; offline dan tanpa daemon persisten. Hasil final: `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` BUILD SUCCESSFUL dalam 4m54s; 21 tes lolos, nol kegagalan/error tes; lint nol error dan 74 warning (termasuk saran gaya UseKtx). Tiga tes baru mencakup URI SAF/file yang sah, penolakan URL HTTP/HTTPS/file host remote, dan URI rusak/kosong. `git diff --check` untuk file tracked yang disentuh bersih. Log: `flufplay/.verification/shorts-download-final-build.log`. Proses Gradle single-use sudah berhenti setelah build. Keberhasilan build tidak membuktikan pemutaran unduhan pada perangkat atau kesamaan visual runtime.

Pemeriksaan aset: 20 PNG baru cocok byte per byte dengan APK referensi. Header `yt_reel_search` dan `yt_reel_more` sudah mempunyai pathData yang identik dengan `reel_search_bold_24dp` dan `reel_more_vertical_bold_24dp`, sehingga bentuk keduanya dipertahankan.

## APK

- Output: `flufplay/app/build/outputs/apk/debug/app-debug.apk`.
- Dikirim ke Xiaomi `25040RP0AG`, `/sdcard/Download/FlufPlay-shorts-unduhan.apk`, tanpa instalasi otomatis.
- Ukuran: 225.967.499 byte.
- SHA-256 lokal dan perangkat sama: `276af5064b42eccf6447d6eb59fc793479f8908d4fec380670c373b16dc350bd`.
- Pemutaran offline dan tampilan setelah instalasi belum diuji pada perangkat. Pengguna perlu memasang APK ini untuk menjalankan alur baru. File yang tidak lagi tersedia di URI penyimpanannya menampilkan pesan gagal, bukan diam-diam melakukan streaming ulang.
