# Bukti analisis statis Unduhan dan player offline

Semua berkas berasal dari kode lokal, APK referensi, atau AAR library 0.18.1 yang dipakai proyek. Tidak ada data hasil pemeriksaan perangkat atau respons jaringan.

- `analysis-scope.json`: versi/hash input dan batas resource.
- `app-source-fingerprints.json`: fingerprint 76 sumber Kotlin/XML untuk memeriksa bahwa aplikasi tidak berubah selama analisis.
- `StreamProcessExtractor-javap.txt`, `YoutubeDL-javap.txt`: disassembly bytecode class library yang relevan.
- `ProgressProbe.java`, `probe-stubs/android/util/Log.java`, `progress-probe-output.txt`: probe parser dengan baris sintetis dan outputnya. Probe memakai kelas asli dari `classes.jar` di library-0.18.1.aar, Kotlin stdlib 2.2.21, serta Java21. Log stub bukan bagian aplikasi dan tidak membuktikan perilaku Android.
- `nra-dex.txt`: presenter item video offline. Nama source `OfflineVideoPresenter.java` ditemukan pada log method-nya; constructor mengikat `offline_video_item`.
- `aqqk-dex.txt`: enum status yang dibaca presenter; disassembly berasal dari classes6.dex.
- `nln-dex.txt`, `nqy-dex.txt`, `nqz-dex.txt`: jalur callback klik dan pembuatan/dispatch command watch offline.
- `click-switch-verification.json`: verifikasi payload packed-switch untuk discriminator 8 pada callback klik, bukan tebakan dari kedekatan instruksi.
- `nqv-dex.txt`: binder status ke OfflineArrowView.
- `nqr-dex.txt`, `resume_playback_overlay.txt`: helper overlay resume dan XML ProgressBar yang dirujuk baris video.
- `resource-dimensions-and-colors.txt`: subset resource dan alias yang benar-benar digunakan dalam perbandingan.
- `offline-class-inventory.json`: inventaris class yang memiliki namespace `/offline/`; ini bukan daftar lengkap semua fitur offline karena class lain terobfuscasi.
- `scan_offline.py`: pemindai awal callsite kandidat dari tabel DEX. Hasil pencarian kandidat tidak digunakan sendiri sebagai bukti; class terpilih kemudian diverifikasi melalui dexdump.

Untuk mengulang probe, ekstrak `classes.jar` dari AAR library 0.18.1 yang ada di cache Gradle, kompilasi kedua file Java dengan JDK21 dan classpath library + Kotlin stdlib 2.2.21, kemudian jalankan ProgressProbe dengan classpath yang sama. Tidak membutuhkan Android SDK runtime atau perangkat. Gunakan taskset satu CPU, nice15, dan heap JVM192MiB seperti analysis-scope.json.
