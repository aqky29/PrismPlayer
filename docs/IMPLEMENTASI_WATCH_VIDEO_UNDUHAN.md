# Implementasi halaman tonton video unduhan

Target: layar Downloads dan halaman tonton yang diamati pada screenshot referensi. Video yang dipilih dari daftar harus membuka komposisi watch dengan baris channel, aksi **Diunduh**, komentar/rekomendasi bila tersedia, dan antrean Unduhan. Player tetap membaca file lokal.

Screenshot target disimpan di [daftar unduhan](evidence/download-watch-implementation/reference-downloads.png) dan [watch dengan tombol Downloaded](evidence/download-watch-implementation/reference-watch-downloaded.png). Aset status diunduh dan antrean diambil langsung dari APK `youtube-21-43-523.apk`. Versi APK tersebut tidak disimpulkan dari screenshot perangkat.

## Perubahan

- `DownloadsActivity` kini memiliki satu ExoPlayer untuk item aktif, dengan penyiapan sumber progressive dari URI lokal. Pergantian inline/fullscreen memakai instance player yang sama, menyimpan posisi dan durasi, serta melepas view/player/listener pada lifecycle yang sesuai.
- `DownloadedWatch` memakai `PlayerControlsOverlay` yang sama dengan player utama, bukan native Media3 controller. Mendukung tap/double-tap seek, time bar merah, replay, speed, subtitle jika terdapat track lokal, fullscreen, dan previous/next/autoplay dalam antrean Unduhan.
- Tampilan portrait memakai player inline 16:9 dan metadata/daftar di bawahnya. Lebar 840dp ke atas memakai kolom player/metadata di kiri dan rekomendasi/antrean di kanan. Fullscreen mengubah orientasi serta menyembunyikan system bars; Back keluar fullscreen terlebih dahulu.
- Ketika informasi online berhasil tersedia, komponen `VideoDetails`, `CommentsTeaser`, `CommentsPanel`, `DescriptionPanel`, dan `VideoCard` yang sama dengan layar utama digunakan kembali. Video tetap memakai file lokal; info online tidak pernah dipakai untuk mengganti media source. Tanpa internet, metadata persisten dan daftar video tersimpan langsung ditampilkan; komentar tidak dibuat-buat.
- Tombol **Diunduh** memakai aset APK dan membuka informasi file serta akses ke daftar Unduhan. Baris aksi layar utama juga memeriksa registry berdasarkan source video, sehingga video yang sudah tersimpan memiliki status yang sama. Task aktif ditandai **Mengunduh** beserta persentase jika diketahui.
- Tombol aksi utama dan offline berbagi ukuran: chip tinggi 32dp, ikon 18dp, padding horizontal 10dp. Pil suka/tidak suka disamakan; tombol berlangganan juga menjadi 32dp.
- Antrean menampilkan posisi `Unduhan · n/total` dan status akhir antrean. File lokal lain tetap tersedia saat offline. Rekomendasi online dapat membuka video di FlufPlay; video yang sudah ada dalam registry dipilih sebagai file lokal.
- Halaman Unduhan memuat judul bagian, pencarian lokal, pengaturan lokasi, navigasi bawah, baris download aktif, badge tersimpan, durasi, progress tontonan, dan menu menghapus dari daftar tanpa menghapus file. Rekomendasi download berasal dari metadata terkait yang benar-benar tersedia, bukan placeholder. Tombol unduh rekomendasi membuka alur pilihan kualitas aplikasi.

## Data, progress, dan kompatibilitas

`DownloadVideo` menyimpan judul asli, URL sumber/channel, uploader, thumbnail/avatar, durasi, jumlah penayangan/suka/pelanggan, serta deskripsi. Metadata ini diteruskan dari StreamInfo yang sudah ada ketika pengguna mengunduh. Metadata JSON opsional pada registry; row lama tetap dibaca dengan fallback nama file. Satu row rusak tidak lagi mengosongkan seluruh daftar.

Artwork disimpan opsional di direktori internal aplikasi dengan batas ukuran dan timeout. Kegagalan cache artwork tidak menggagalkan file media. Thumbnail video lokal memakai satu MediaMetadataRetriever pada IO, scaled frame dan LRU 12 gambar.

Progress transfer menggunakan baris output yt-dlp untuk membedakan tahap dan unknown; ETA hanya dipakai bila baris saat itu benar-benar menyatakan ETA. Heuristik FFmpeg 99 tidak ditampilkan sebagai total progress. Merge/extract audio menggunakan tahap pemrosesan, copy memiliki persentase byte, kemudian verifikasi. Callback UI/notifikasi dibatasi sekitar 300ms dan state identik tidak diterbitkan ulang.

Folder default tetap `Download/video-yt-downloader`. Penyimpanan, verifikasi ukuran, publish MediaStore, dan cleanup output gagal tetap dipertahankan.

## Batas yang perlu diperhatikan saat review

- Unduhan lama yang hanya menyimpan nama file dan URI tidak dapat dipastikan judul/channel/URL aslinya. File tetap dapat diputar dengan komposisi watch baru, tetapi metadata lengkap tersedia dari unduhan baru. Tidak ada pencarian heuristik yang menebak identitas video lama.
- Komentar, rekomendasi, akun, dan metadata online tetap bergantung jaringan serta layanan. Kegagalannya tidak menghentikan pemutaran file lokal. Tidak ada angka komentar atau rekomendasi palsu saat offline.
- Pengaturan kualitas melaporkan tinggi video aktual bila diketahui; file tersimpan tidak bisa ditingkatkan resolusinya lewat pemilih kualitas online. Subtitle memakai track yang benar-benar tersedia di file.
- Miniplayer offline kemudian ditambahkan pada [perbaikan thumbnail dan miniplayer](PERBAIKAN_THUMBNAIL_DAN_MINIPLAYER_OFFLINE.md). PiP/background media service, pause/resume transfer, dan rekonstruksi task setelah process death belum ditambahkan.
- Kesamaan visual setelah pemasangan dan perilaku sentuh belum diuji pada perangkat. Tidak ada instalasi/pengiriman APK ke perangkat selama implementasi ini.

## Verifikasi

Build, unit test, dan lint dijalankan offline dengan Java21, satu inti CPU (`taskset -c 0`), nice15, satu worker, heap1536MiB, dan compiler Kotlin in-process tanpa daemon persisten. Hasil final: BUILD SUCCESSFUL dalam 4m48s; 30 tes lolos, nol kegagalan; lint 0 error dan 75 warning. Log: `flufplay/.verification/download-watch-parity-final-build.log`. Proses Gradle single-use sudah berhenti.

Tes baru mencakup unknown progress, progress/ETA pada transfer, baris 100% tanpa ETA, merge/FFmpeg tanpa progress palsu, posisi replay, serta fallback model file lama. Ini tidak menggantikan pengujian UI/perangkat.

APK: `flufplay/app/build/outputs/apk/debug/app-debug.apk`, 225968381 byte. SHA-256: `16d4c4bd13a7e07769f0bf11787d43210732077ae7b68384bc33ff974bd86072`. [Hasil verifikasi terstruktur](evidence/download-watch-implementation/verification.json).
