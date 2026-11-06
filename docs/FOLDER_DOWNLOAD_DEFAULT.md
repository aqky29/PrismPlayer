# Folder download default FlufPlay

Sebelumnya setiap download menjalankan ACTION_CREATE_DOCUMENT di MainActivity. Ini pemilih dokumen dengan nama file, bukan penyimpanan otomatis ke folder Download. Karena alur tersebut diluncurkan untuk setiap pilihan kualitas, pengguna diminta memilih lokasi setiap kali.

## Perubahan

- Tombol **Unduh** langsung memulai DownloadService dan membuka layar Unduhan.
- Lokasi hasil video MP4 dan audio MP3: `Download/video-yt-downloader/` pada penyimpanan utama perangkat.
- Folder dibuat otomatis saat hasil pertama disimpan.
- Android 10/API29 ke atas: MediaStore.Downloads, RELATIVE_PATH `Download/video-yt-downloader/`, IS_PENDING=1 selama penyalinan, baru diterbitkan setelah ukuran diverifikasi. Tidak membutuhkan izin storage tambahan untuk file yang dibuat aplikasi sendiri.
- Android 8–9/API26–28: folder Download publik dengan izin WRITE_EXTERNAL_STORAGE; aplikasi meminta izin saat pertama kali diperlukan. Manifest membatasi izin ini sampai API28. Jika izin ditolak, unduhan tidak dimulai dan pengguna menerima penjelasan.
- Nama file disanitasi dan dibatasi menurut code point Unicode agar tidak melampaui batas nama filesystem. Judul sama tidak menimpa file sebelumnya: MediaStore mengalokasikan nama file unik; jalur legacy mereservasi nama dengan createNewFile atomik (`video.mp4`, `video (1).mp4`, dan seterusnya).
- Output baru dibersihkan bila gagal atau dibatalkan, termasuk saat menyalin hasil. Cleanup hanya menyentuh output yang direservasi download tersebut. File lama tidak dipindah atau dihapus.
- Registry SavedDownloads dan player lokal tetap memakai URI file hasil. Entri lama tetap dapat dibaca; lokasi file lama tidak diubah.

## Cakupan

Perubahan ini khusus lokasi penyimpanan default. Analisis kesenjangan visual progress, layar Unduhan, dan watch screen offline pada laporan sebelumnya tetap merupakan pekerjaan terpisah; perubahan folder ini tidak menyatakan ketiganya telah setara dengan YouTube.

Tidak memakai perangkat, tidak menginstal APK, dan tidak mengirim APK ke perangkat. Verifikasi lokal dilakukan dengan Java21, satu inti CPU, satu Gradle worker, nice15, heap1536MiB, Kotlin in-process, offline, tanpa daemon persisten.

## Verifikasi

Build final `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` berhasil dalam 4m58s. Seluruh 25 tes lolos; lint nol error, 74 warning. Log: `flufplay/.verification/default-download-folder-build.log`. Tes filesystem mencakup penamaan/path, Unicode panjang, nama duplikat tanpa overwrite, traversal path, serta lokasi yang bukan direktori. MediaStore dan dialog izin Android belum diuji pada perangkat.

APK lokal: `flufplay/app/build/outputs/apk/debug/app-debug.apk`. SHA-256: `6fea502fd5ffe7a6aebd6e4e3004d93091736200f04a3b54f559648104bfa9fa`. APK tidak dikirim atau dipasang ke perangkat.
