# Laporan uji manual M04 — status suka, simpan, dan subscribe

Tanggal: **4 November 2026**.
Perangkat: Xiaomi `25040RP0AG` (taiko), wireless ADB.
APK uji:
- `16:02` — build M04 awal.
- `16:41` — build setelah perbaikan parser status awal, koalesensi ketukan, dan seed status Save.
- `20:12` — build setelah perbaikan dialog Save (centang optimistis, tanpa reload pasca-klik).

Lingkungan: akun A (sudah login) dan akun B (login setelah logout dari A). Video uji utama: `zSLoE1N4LLA` (sudah di-like dan channel-nya sudah di-subscribe di YouTube), `r--bhiBs4b8` (belum di-like dan belum di-subscribe).

> Uji cepat tanpa perangkat fisik: lihat [Panduan test emulator Pixel 10](PANDUAN_TEST_EMULATOR.md) (`pixel10 app`).

**Status terbaru — build 20:53:** tindak lanjut P2-A01–P2-A04 sudah diterapkan pada source; laporan otomatis terbaru mencatat 103 unit test lulus. Pengguna telah menguji rekonsiliasi otomatis `Subscribed?`, like saat sinyal hilang, serta regresi ketukan cepat/restart/Save. Bagian A–G dan hasil 99 test di bawah adalah riwayat build sebelumnya; bagian 3.1 dan batas bukti pada bagian 4 menjelaskan keadaan sesudah tindak lanjut.

## 1. Ringkasan hasil

| Tes | Skenario | Build | Hasil |
| --- | --- | --- | --- |
| A | Status awal like/subscribe dari server | 16:02 → 16:41 | **Gagal pada 16:02, lulus setelah perbaikan.** Subscribe terbaca; ikon Like tidak aktif walau video sudah di-like. Pada 16:41 status awal terbaca benar. |
| B | Konsistensi Watch dan fullscreen setelah restart | 16:02 → 16:41 | **Gagal pada 16:02, lulus setelah perbaikan.** Sebelum perbaikan, ikon Like aktif lalu menjadi nonaktif setelah aplikasi dibuka ulang. |
| C | Ketukan cepat berulang | 16:02 → 16:41 | **Gagal pada 16:02, lulus setelah perbaikan.** Paritas ketukan sebelumnya meleset (5 kali masih aktif, 6 kali baru nonaktif). Subscribe cepat sudah aman sejak awal. |
| D | Like saat offline | 16:02 | **Lulus.** Muncul pesan kegagalan `unable to resolve host "www.youtube.com"`; setelah jaringan kembali, status mengikuti server. |
| E | Dialog Save dan chip Save | 16:02 → 20:12 | **Gagal pada 16:02, lulus setelah perbaikan.** Centang tidak tampil setelah diklik dan dialog terasa lambat; chip Save sempat nonaktif lalu aktif. Pada 20:12 centang berubah segera dan tetap sesuai server. |
| F | Pergantian akun pada player offline | 16:02 | **Lulus.** Like pada akun A terlihat pada file unduhan; setelah logout ke akun B, file yang sama tidak menampilkan Like akun A. |
| G | Player offline tanpa jaringan pada akun B | 16:02 | **Lulus.** Like dan subscribe gagal dengan pesan yang jelas, video tetap diputar; akun A juga aman. |

## 2. Temuan dan perbaikan

### 2.1 Status like awal tidak terbaca (A/B)

Gejala: subscribe berhasil dipetakan dari server, tetapi like tidak, sehingga video yang sudah di-like tampil seolah belum di-like. Setelah aplikasi dibuka ulang, status lokal hilang dan ikon kembali nonaktif.

Penyebab: parser `next` hanya membaca `entityKey` di dalam `toggleButtonMutation`, sedangkan respons nyata menaruhnya pada node mutasi `frameworkUpdates`.

Perbaikan: parser kini membaca `entityKey` pada node mutasi maupun di dalam mutation, mendukung `buttonState` `TOGGLED`/`UNTOGGLED`, dan mempertahankan nilai pertama untuk entity key tiap tombol. Log kategori `FlufEngagement` mencatat `like=known|unknown` dan `subscribe=known|unknown` tanpa nilai header/token.

### 2.2 Paritas ketukan cepat salah (C)

Gejala: ketukan berulang tidak menghasilkan status sesuai jumlah ketukan; sebagian ketukan hilang saat request sebelumnya belum selesai.

Penyebab: tombol dinonaktifkan selama operasi berjalan sehingga ketukan berikutnya dibuang.

Perbaikan: setiap ketukan langsung mengubah niat yang tampil, satu request berjalan per target, dan niat terakhir dikirim setelah request sebelumnya selesai (`followUpRating`/`followUpSubscription`). Tombol tidak lagi dinonaktifkan selama menunggu.

### 2.3 Dialog Save lambat dan centang tidak sinkron (E)

Gejala: setelah memilih playlist, centang tidak langsung tampil; dialog terasa kaku. Chip Save sempat nonaktif lalu aktif saat video dibuka dari History.

Penyebab: dialog memuat ulang daftar dari server setelah setiap klik, dan respons baca ulang yang belum konsisten menimpa centang. Status tersimpan belum diambil saat video dibuka.

Perbaikan: status tersimpan diambil dari server saat Watch/player offline dibuka; centang berubah segera saat diketuk dan dikembalikan bila server menolak; reload pasca-klik dihapus. Daftar dimuat ulang hanya saat dialog dibuka berikutnya.

## 3. Verifikasi otomatis

- Sebelum tindak lanjut audit: 99 unit test lulus, 0 gagal. Cakupan: isolasi sesi, operasi ganda, hasil terlambat, penolakan/ketidakpastian, delta jumlah suka, koalesensi ketukan, parser legacy/toggle view model/entity key/`buttonState`.
- Sesudah tindak lanjut audit, build 20:53: 103 unit test lulus, 0 failure/error, termasuk refresh otoritatif, penolakan respons basi, pelepasan pending yang dibatalkan, dan pemisahan playlist/Watch later.
- `:app:lintDebug` dan `:app:assembleDebug` lulus (Gradle offline, Java 21, satu worker).
- Pemetaan status awal menghasilkan kategori `known` pada video uji setelah perbaikan; dapat diperiksa dengan `adb logcat -s FlufEngagement`.

## 3.1. Uji lanjutan setelah tindak lanjut audit P2

- Like saat sinyal hilang menampilkan pesan kegagalan dan tidak meninggalkan status sukses palsu.
- Subscribe yang diubah saat jaringan dimatikan/dihidupkan cepat sempat menampilkan `Subscribed?` sebagai penanda hasil belum pasti. Pembacaan ulang otomatis kini dijadwalkan agar penanda itu hilang dan nilai mengikuti server.
- Skenario lain pada A–G dinyatakan aman oleh pengguna.
- Build 20:53 (rekonsiliasi otomatis) diuji pada perangkat: tanda `Subscribed?` hilang sendiri dan nilai akhirnya sesuai server; like saat sinyal hilang tetap aman; ketukan cepat, status setelah restart, dan Save per playlist tetap aman.

## 4. Batas bukti yang tersisa

- Status awal Short kini dimuat pada source. Konsistensi Shorts ↔ Watch untuk target yang sama dan Short yang belum pernah dibuka di Watch belum dilaporkan diuji ulang pada perangkat sesudah perubahan ini.
- Pembacaan ulang otomatis setelah `uncertain` sudah dibuat dan skenario `Subscribed?` yang hilang sendiri telah diuji pada build 20:53. Skenario terkontrol ketika server menerima mutasi lalu respons terputus, serta perubahan status dari aplikasi YouTube, belum dilaporkan terpisah.
- Pemisahan `playlistIds` dan `watchLater` sudah dibuat serta dicakup unit test. Kombinasi beberapa playlist dan perpindahan membership Watch later versus playlist lain masih memerlukan bukti perangkat yang spesifik.
- Pelepasan pending lewat `abandonRating`/`abandonSubscription` dan `try/finally` sudah dibuat. Skenario request sengaja ditahan lalu player offline diminimalkan/ditutup belum dilaporkan terpisah pada perangkat.
- Perangkat/akun lain, jaringan lambat, dan video live belum dicakup laporan ini.
- Checklist M04 di rencana utama tetap terbuka sampai butir-butir di atas memiliki bukti.

## 5. Catatan asli pengguna

> A. saya udah buka di app yt dan like video https://youtu.be/zSLoE1N4LLA?si=thbB36NTLwUaSvSM dan subscribe, hasil di app flufplay sudah ke subscribed, dan like video itu masuk ke library tapi anehnya icon like video nya ga aktif saya like lagi di flufplay lalu keluar dan lihat library like Masih ada, untuk video yang ga di like aman dan belum di subscribe aman juga https://youtu.be/r--bhiBs4b8?si=K6O8StY7253AzFp0
>
> B. Saya cek di bagian watch aktif likenya dan buka di Fullscreen aktif juga https://youtu.be/zSLoE1N4LLA?si=thbB36NTLwUaSvSM pas saya buka lagi Dari refresh app nya icon like aktif jadi ga aktif dan di Fullscreen juga tapi di library like Masih ada
>
> C. Saya ketuk di Fullscreen berkali Kali like nya ketukan di ke enam Masih aktif seharusnya itu off likenya dan di watch saya ketuk Dari like on diketukan ke Lima Masih on baru diketukan ke 6 off saya cek saat on masuk ke library dan saat off hilang Dari library itu semua di ketuk cepat, dan button subscribe saya ketuk cepat aman dan cek di screen subscription aman juga di klik untuk unsubscribe hilang discreen subscription
>
> D. Gagal like dan muncul notif dengan logo app unable to resolve host "www.youtube.com" No address...
>
> Saat di no aktifkan mode pesawat aman lagi dan masuk ke library
>
> E. Menekan save muncul panel playlist dan klik Salah satu namun UI nya ga menampilkan playlist itu teecentang namun ada button done aktif, dan klik done cek playlist masuk, saya close videonya dan buka lagi Dari history icon savenya aktif tapi sempat ga aktif lalu aktif
>
> F. Aman semua udah pake akun A iqbalhs787 saya like satu video yang udah didownload lalu cek dengan mode pesawat aktif dan off sama ada notif nya dan itu Masih like on lalu logout ganti ke akun B aqky cek di download video yang sama aman ga ada like icon aktif
>
> G. Pake akun B di download on mode pesawat like video gagal dan subscribe gagal Masih aman videonya terputar cek akun A juga aman
