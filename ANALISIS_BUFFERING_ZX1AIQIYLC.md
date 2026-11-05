# Analisis dan perbaikan buffering video FlufPlay

Tanggal: 3 November 2026  
Video yang diuji: [Ada Dewa Mabuk Disini - Phantom Blade Zero Indonesia Part 4](https://youtu.be/zx1aIqiYL_c)  
Perangkat: Xiaomi/Redmi tablet `25040RP0AG` (`taiko`)  
Aplikasi: FlufPlay, paket `id.flufclip.player`

## 1. Masalah yang dilaporkan

Video di atas mengalami buffering berat di FlufPlay, sementara video lain yang diuji pengguna berjalan normal. Video ini merupakan video biasa, bukan siaran langsung, dengan durasi sekitar 4.161 detik atau 69 menit 21 detik. Pengguna membandingkan video yang sama di aplikasi YouTube asli pada tablet dan jaringan yang sama; 480p di YouTube dilaporkan lancar.

Menurunkan kualitas di FlufPlay ke 480p sempat terasa lancar, tetapi kemudian tetap buffering. Pengujian lanjutan memastikan bahwa masalah muncul pada kedua format video 480p yang dicoba: AVC dan VP9. Mengubah preferensi audio ke **Original audio** juga tidak menghilangkan masalah.

Karena itu, masalah tidak cukup dijelaskan sebagai pilihan resolusi 1440p, codec tertentu, atau preferensi audio Bahasa Indonesia saja. Perbandingan dengan YouTube mengarah pada perbedaan jalur pemutaran FlufPlay, tetapi tidak dengan sendirinya membuktikan kondisi setiap jaringan dan server pada setiap waktu.

## 2. Bukti dari tablet

APK diagnostik dipasang di perangkat dan identitasnya diperiksa dengan SHA-256. Log yang digunakan di bawah tidak menyertakan cookie, token, atau URL stream yang ditandatangani.

### Buffer habis dan pemutaran berhenti

Pada satu percobaan, pemutar mulai siap dengan sekitar **1,33 detik** data di buffer. Sekitar satu detik kemudian buffer tinggal **0,10 detik**, dan player masuk ke kondisi buffering. Player baru kembali ke kondisi siap sekitar **26 detik** kemudian, dengan sekitar **0,18 detik** di buffer.

Pada percobaan lain setelah beralih ke 480p, buffer turun menjadi sekitar **0,37 detik** dan player berada dalam kondisi buffering sekitar **19 detik**. Pengujian berikutnya mengonfirmasi pemilihan `video/avc` 480p, lalu pada percobaan terpisah `video/x-vnd.on2.vp9` 480p. Keduanya tetap mengalami buffering menurut pengguna; log juga merekam buffer 480p AVC turun hingga sekitar **0,26 detik**.

Angka buffer tersebut adalah data pada saat transisi status yang tercatat, bukan pengukuran lengkap kecepatan jaringan sepanjang pemutaran.

### Playlist dan media memakai jalur berbeda

Log diagnostik menunjukkan permintaan playlist ke Googlevideo selesai dalam ratusan milidetik, termasuk playlist berukuran sekitar 0,95 MB. Setelah itu player terlihat memasang ulang sumber dan memulai dua pemuatan media HTTP langsung. Ketika buffer habis, permintaan media tersebut masih berlangsung dan tidak ada error HTTP yang tercatat dalam potongan log yang diperiksa.

Jenis event Media3 dan pemeriksaan kode sumber Media3 menunjukkan event `type=1 track=-1` pada tahap itu konsisten dengan pemuatan progressive, sedangkan percobaan HLS yang kemudian berjalan lancar mencatat pemuatan segmen terpisah. Tidak adanya error HTTP **tidak** membuktikan koneksi sehat atau mengidentifikasi sebab persis perlambatan di jaringan/server.

Saat pengguna mengganti kualitas kembali ke **Auto**, player kembali memakai HLS. Pada pengamatan tersebut, beberapa segmen video berukuran sekitar 1,5–3 MB selesai dalam kira-kira 0,3–1 detik. Posisi pemutaran kemudian bergerak dari sekitar 00:38 ke 01:44 tanpa transisi ke buffering yang tercatat selama interval itu.

## 3. Diagnosis

`VideoRepository` berhasil mendapatkan sumber HLS untuk video yang diuji. Jalur pemilihan audio FlufPlay juga dapat mencari track audio pilihan di sumber progressive dan mengganti media ke aliran HTTP langsung apabila audio yang diinginkan dianggap tidak tersedia dalam manifest adaptif.

Pada kasus ini terlihat perpindahan sumber segera setelah HLS mulai dimuat. Pada sumber langsung itulah buffer berulang kali habis; setelah beralih kembali ke HLS, pengujian berjalan lebih lancar. Hubungan ini didukung oleh log dan percobaan pada tablet, tetapi log tidak memastikan alasan internal mengapa pemuatan progressive lambat untuk video ini. Tidak ada dasar untuk menyimpulkan bahwa semua video, semua server Googlevideo, atau semua kualitas mengalami masalah yang sama.

Pemeriksaan kode juga menemukan dua kelemahan pada keputusan fallback audio:

1. Keputusan dapat diambil sebelum player mencapai status `READY` dan daftar track stabil.
2. Track audio manifest tanpa label bahasa diperlakukan seolah-olah pasti bukan bahasa yang diinginkan, meskipun bahasanya sebenarnya belum diketahui.

## 4. Perubahan yang diterapkan

### Pertahankan HLS pada pemilihan audio otomatis

Pada `AudioTrackSelection.kt`, fungsi `allowAutomaticDirectAudioFallback()` mencegah fallback **otomatis** ke sumber langsung ketika HLS tersedia dan DASH tidak tersedia. Jika DASH menjadi sumber yang dipilih atau HLS tidak tersedia, jalur fallback otomatis yang relevan tetap dapat digunakan.

Di `MainActivity.kt`, efek pemilihan audio otomatis memeriksa keputusan ini sebelum mengganti sumber. Untuk video yang diuji, HLS tersedia dan DASH tidak tersedia, sehingga sumber tidak lagi otomatis diganti ke progressive hanya demi mencari track audio.

Konsekuensinya: jika bahasa yang disukai tidak tersedia atau tidak teridentifikasi pada HLS, pemutaran otomatis menggunakan audio HLS yang tersedia. Pilihan track audio secara manual masih tersedia dan dapat memakai jalur sumber lain bila diperlukan. Perubahan ini mengutamakan kelancaran pemutaran; ia tidak menjamin bahwa setiap pilihan bahasa otomatis tersedia pada semua video.

### Tunggu player siap dan hindari asumsi bahasa

Efek fallback audio otomatis sekarang menunggu `STATE_READY`, lalu memberi waktu 1,5 detik untuk daftar track stabil sebelum mengambil keputusan. Efek ini juga tidak mengganti sumber ketika kualitas atau audio telah dipilih secara manual.

`manifestMissingPreferredAudio()` tidak lagi menyimpulkan bahwa bahasa pilihan tidak ada apabila ada track audio utama tanpa bahasa yang dapat dikenali. Track tanpa label dapat saja merupakan track yang diminta, sehingga kondisi itu diperlakukan sebagai tidak pasti.

### Pemulihan cadangan

Pada `PlaybackRecovery.kt` dan `MainActivity.kt`, tersedia pemulihan terbatas untuk sumber langsung yang sebelumnya dipilih otomatis sebagai fallback audio. Bila sumber tersebut terus dalam `STATE_BUFFERING` selama sekitar delapan detik dan persyaratannya terpenuhi, player mencoba kembali ke HLS pada posisi yang sama.

Pemulihan ini dibatasi pada video biasa dengan HLS tersedia, tanpa DASH, dalam mode kualitas Auto, tanpa pilihan audio manual, dan hanya jika sumber langsung berasal dari fallback audio otomatis. Pilihan kualitas/audio manual, Shorts, dan live tidak diubah oleh mekanisme tersebut. Pada pengujian akhir video ini tetap di HLS, sehingga **cabang pemulihan delapan detik belum terbukti terpicu di perangkat**.

### Diagnostik

`MainActivity.kt` memiliki log diagnostik `FlufPlayback` untuk transisi status player, format yang dipilih, awal/akhir pemuatan, jumlah byte, durasi, dan kelompok server. Log yang ditambahkan tidak mencetak URL lengkap atau cookie. Catatan diagnostik ini membantu membedakan playlist, media, dan status player; log `loadStart` saja tidak berarti permintaan gagal.

## 5. Pengujian

- Build debug dan `:app:testDebugUnitTest` berhasil menggunakan Java 21, mode offline, dan satu worker.
- Pengujian unit mencakup keputusan fallback sumber untuk HLS/DASH, track audio tanpa label, serta batasan pemulihan agar tidak menimpa pilihan manual, live, atau Shorts.
- APK **FlufPlay-buffer-hls-20261103.apk** disalin ke folder Download tablet; checksum hasil salin dan APK lokal cocok.
- APK yang terpasang kemudian diverifikasi cocok dengan APK tersebut: SHA-256 `8c395e221bd75a8cc4a425331bd3ce83d287409a29073c65cd4bd3f09523589b`.
- Pengguna memutar video yang sama dalam mode **Auto** sekitar satu menit dan melaporkan **lancar**. Log menunjukkan player tetap menggunakan HLS, segmen terus dimuat, dan tidak ada transisi buffering baru yang tercatat selama periode pengamatan. Posisi video tercatat mencapai sekitar 58 detik.

Hasil ini memvalidasi pengujian spesifik tersebut, bukan seluruh durasi video atau seluruh kemungkinan video dan jaringan. Kualitas manual 480p setelah perubahan akhir, seluruh pilihan bahasa audio, dan mekanisme pemulihan delapan detik masih memerlukan pengujian langsung terpisah.

## 6. Cara memeriksa ulang

1. Pasang APK yang ingin diuji dan buka video yang sama.
2. Pilih **Quality → Auto**, lalu putar setidaknya satu menit; catat apakah buffering muncul.
3. Jika perlu, uji 480p secara manual dan bandingkan dengan Auto. Uji audio Original dan Bahasa Indonesia secara terpisah apabila tersedia.
4. Periksa log `FlufPlayback` dan `FlufMedia` untuk transisi buffer, format, serta awal/akhir pemuatan. Jangan membagikan cookie, token, atau URL stream yang ditandatangani.
5. Bila pemulihan otomatis terpicu, log akan memuat pesan `automatic audio fallback stalled; retrying HLS`. Pesan ini menunjukkan percobaan pemulihan, bukan bukti bahwa pemutaran kemudian lancar.

Untuk uji terkontrol, hindari mengubah kualitas, audio, dan posisi video secara bersamaan: setiap perubahan tersebut dapat memasang ulang sumber dan menyulitkan perbandingan.
