# Plan sinkronisasi History akun YouTube di FlufPlay

Status: **implementasi awal; belum diverifikasi dengan akun/perangkat**. Tujuannya menampilkan riwayat asli akun YouTube yang aktif dan menyelidiki pencatatan tontonan FlufPlay ke akun tersebut. Keberhasilan satu arah tidak membuktikan arah lainnya.

### Hasil sementara 2026-11-02

- `AccountHistory.kt` meminta `FEhistory` dan continuation, dan memetakan beberapa renderer ke UI History akun dan pratinjau You. Data akun disimpan di memori per sesi; History perangkat tetap terpisah. Pencarian hanya pada item yang dimuat. Penghapusan akun diarahkan ke YouTube.
- `PlaybackTracker` tidak lagi mengirim ping berkala `playing` ketika player tidak bermain, serta menghentikan job saat switch dimatikan atau logout. Mekanisme pencatatan ke server dan perilaku saat akun YouTube menjeda History **belum terbukti**.
- Build `:app:assembleDebug` berhasil dengan Java 21 dan satu worker. Belum ada probe akun, pengujian parser dengan fixture, atau uji visual/runtime perangkat untuk implementasi ini. Format renderer, section, halaman kosong, identitas persona dan pencatatan dua arah masih perlu divalidasi. Kondisi kosong yang tidak bisa dibedakan dari sesi bermasalah saat ini ditampilkan sebagai error, bukan History kosong yang dikonfirmasi.
- Belum ada cache offline akun, kategorisasi error HTTP rinci, verifikasi pencarian akun penuh, dan penghapusan akun langsung. Kriteria penerimaan di bawah masih terbuka.

## 1. Hasil yang diinginkan

- Saat login, halaman History dan pratinjau di tab You menampilkan urutan riwayat yang dikembalikan server untuk akun/channel yang aktif, termasuk video yang ditonton di perangkat lain.
- Pemutaran di FlufPlay dapat tercatat pada akun jika mekanisme yang dipakai terbukti diterima server. Statusnya tidak disebut berhasil hanya karena request dikirim.
- Saat logout, berganti akun, offline, sesi kedaluwarsa, atau History YouTube dijeda, aplikasi menampilkan keadaan yang sesuai tanpa mencampur data antar akun.
- Riwayat perangkat tetap ada sebagai data lokal. Tidak mengunggah riwayat lama, tidak menggabungkannya diam-diam dengan daftar akun, dan tidak menimpa data lokal.
- Penghapusan akun dua arah bukan bagian rilis awal sampai endpoint, identitas item, dan akibatnya terverifikasi; kontrol hapus yang tersedia harus jelas cakupannya.

## 2. Kondisi saat ini dan dasar teknis

- `flufplay/app/src/main/java/id/flufclip/player/WatchHistory.kt`: JSON `watch_history`, maksimal 100 URL, menyimpan posisi dan metadata lokal. `MainActivity.kt` memperbaruinya setiap lima detik dan saat beberapa transisi player. Tab You dan History membaca daftar ini.
- `PlaybackTracker.kt`: saat login dan pengaturan `sync_history_to_google` aktif, mengirim ping playback dan watchtime. Respons dan status HTTP tidak diperiksa; ping berkala `playing` hanya dikirim saat player benar-benar bermain, dan job dihentikan saat switch dimatikan atau logout. Kode ini **belum membuktikan** bahwa tontonan tercatat pada akun; kelengkapan parameter URL, perilaku buffering/seek/akhir, dan penerimaan server masih perlu diverifikasi.
- `YouTubeAuth.kt`: login WebView dengan cookie, `SAPISIDHASH` jika tersedia, dan `X-Goog-AuthUser: 0`. Validitas status login lokal bukan bukti sesi masih diterima server; identitas akun/channel dan brand belum terjamin.
- `HomeFeed.kt`: sudah memiliki request InnerTube WEB, header auth, context, penanganan HTTP dan continuation; komponen yang cocok dapat dipakai ulang tanpa mencampur parser History dengan parser Home.
- [Analisis akun dan riwayat terdahulu](ANALISIS_AKUN_DAN_RIWAYAT_YOUTUBE_REA.md) mengidentifikasi `FEhistory` pada APK referensi dan mencatat probe anonim. Bagian lain juga mencatat probe ber-cookie yang menghasilkan item dan continuation, tetapi bagian batasan dokumen menyatakan bentuk respons ber-cookie belum diambil. Karena catatan tersebut tidak konsisten, jadikan bukti awal, **bukan kontrak renderer atau jaminan bahwa sesi sekarang bekerja**. Ulangi verifikasi dengan data yang disanitasi.
- Google menyatakan daftar playlist watch history tidak dapat diambil melalui YouTube Data API: [revision history YouTube Data API](https://developers.google.com/youtube/v3/revision_history). Oleh karena itu OAuth Data API/`playlistItems.list(HL)` tidak menjadi solusi baca History. `FEhistory` adalah antarmuka internal yang tidak didokumentasikan sebagai API publik; bisa berubah atau ditolak, dan perlu dinilai kesesuaiannya dengan aturan layanan dan distribusi aplikasi.

## 3. Lingkup dan keputusan produk

**Tahap awal: baca akun dari server.** Pada login, History akun menjadi sumber utama daftar akun, termasuk urutan dan kelompok waktu dari server. Daftar perangkat tetap tersedia terpisah dengan label jelas, termasuk saat offline jika cache akun tidak tersedia. Jangan menganggap daftar lokal sebagai hasil sinkronisasi akun.

**Pencatatan tontonan baru:** investigasi dan uji secara terpisah sebelum menyebutnya berfungsi. Jika belum terbukti, tampilkan bahwa pencatatan ke akun belum terverifikasi; jangan mengisi server history dengan entri lokal atau mengklaim sinkronisasi dua arah.

**Hapus:** tahap awal nonaktifkan/sembunyikan operasi hapus akun di daftar akun dan sediakan jalur ke pengelolaan riwayat YouTube/My Activity, dengan label yang jelas. Hapus lokal tetap berlaku hanya pada daftar perangkat. Hapus akun langsung merupakan tahap lanjutan setelah tersedia mekanisme yang terverifikasi; jangan mengirim request destruktif saat eksplorasi.

## 4. Tahap 0 — buktikan sumber dan identitas

1. Pelajari alur `HomeFeed` dan analisis sebelumnya. Lakukan pengujian baca-saja terhadap `browseId=FEhistory` menggunakan sesi aplikasi yang sudah login, dengan context WEB dan header yang ada. Jangan mencetak atau menyimpan cookie, `Authorization`, token, identitas lengkap, atau respons akun mentah ke repo/log.
2. Bandingkan halaman pertama dan continuation dengan tampilan History YouTube pada akun/channel yang sama. Catat secara lokal hanya struktur field yang disanitasi, jenis renderer, jumlah item, urutan, kelompok waktu, continuation dan status yang ditemui. Periksa video biasa, Shorts, video privat/dihapus, serta daftar kosong bila tersedia.
3. Bedakan 401/403 atau halaman sign-in, offline/timeout, respons kosong yang sah, serta bentuk respons tidak dikenal. HTTP 200 saja bukan bukti login atau daftar kosong.
4. Temukan identitas stabil yang benar-benar tersedia untuk akun **dan channel/persona aktif**, termasuk brand. Nama tampilan/email bukan identifier unik. Validasi apakah cookie WEB saja memilih channel tepat dan apakah header page ID diperlukan. Jika identitas tak dapat dipastikan, **jangan membuat cache akun persisten atau menampilkan cache akun lama**; gunakan data sementara sesi dan nyatakan keterbatasannya.
5. Simpan fixture sintetis/anonymized yang tidak memuat riwayat, token, cookie, maupun data pribadi pengguna. Jika endpoint tidak dapat dipakai, hentikan integrasi server dengan status terbatas dan sediakan pilihan membuka History YouTube; jangan membuat klaim sukses.

## 5. Tahap 1 — pembacaan History akun

### Data dan jaringan

- Tambahkan `AccountHistory.kt` atau pemisahan setara: `AccountHistoryRepository` mengirim request awal `FEhistory` dan lanjutan menggunakan continuation yang benar-benar teramati. Pakai `Dispatchers.IO`, timeout, pembatalan coroutine, dan penanganan status HTTP. Gunakan abstraksi HTTP/context yang layak dibagi dengan `HomeFeed` jika tidak mengubah perilaku feed lain.
- Modelkan respons sebagai halaman berurutan berisi section/label server, item, continuation, dan status. Pertahankan key item atau occurrence dari server bila ada; jangan deduplikasi otomatis berdasarkan video ID jika server mengembalikan beberapa peristiwa. Simpan video ID, judul, channel, thumbnail, durasi/progress, dan penanda Shorts **hanya bila tersedia**; nilai tidak tersedia tetap unknown.
- Jangan mengubah label tanggal seperti Today/Yesterday menjadi timestamp rekaan atau mengurutkan ulang berdasarkan timestamp lokal. Pertahankan section dan urutan server. Jika renderer tidak dikenal, tampilkan error/parsial secara jelas, bukan empty state palsu.
- Implementasikan pagination per continuation dengan perlindungan request ganda, token berulang, respons basi, dan item dengan key yang sama. Hasil continuation yang datang setelah refresh atau pergantian akun dibuang.
- Error bertipe `Unauthenticated`, `Network`, `RateLimited`, `UnexpectedResponse`, `Empty` dan `Success` sesuai bukti respons; jangan menganggap 200 otomatis sukses. Retry manual dan backoff terbatas untuk kegagalan sementara; jangan polling agresif.

### Lifecycle, cache dan UI

- Buat state holder yang mengamati login, logout dan perubahan identitas. Gunakan generation/session ID serta pembatalan job untuk mencegah respons akun A ditampilkan setelah berpindah ke B. Refresh saat History dibuka, setelah login dan lewat aksi refresh; jangan merefetch setiap recomposition.
- Jika identitas stabil tersedia dan terbukti, cache halaman sukses secara terpisah per akun/channel dengan batas ukuran dan waktu. Tandai cache offline sebagai data tersimpan dan sertakan waktu pembaruan. Jika gagal refresh, jangan menghapus cache lama atau menyebutnya terkini. Saat logout/ganti akun, bersihkan state yang terlihat dan token continuation; kebijakan cache persisten harus mencegah akses lintas akun.
- Ubah `MainActivity.kt`, `LibraryTab.kt`, dan `WatchHistory.kt` agar pratinjau dan layar lengkap menggunakan state serta sumber akun yang sama saat login. Berikan state loading, empty, login ulang, offline/cache, error + retry, dan penanda sumber. Guest memakai riwayat perangkat. Daftar perangkat tetap dapat dipilih secara eksplisit bila dibutuhkan.
- Pencarian: bila server menyediakan endpoint/parameter pencarian History yang telah diverifikasi, gunakan dengan continuation yang benar; jika belum, beri label pencarian hanya pada item yang sudah dimuat. Jangan menjanjikan pencarian seluruh akun berdasarkan satu halaman.
- Buka video dengan ID/URL server yang valid; Shorts buka di layar Shorts bila tipenya benar-benar teridentifikasi. Posisi dari riwayat lokal hanya digunakan jika cocok dengan identitas dan video; jika sumber/progress akun tidak tersedia, jangan mengklaim resume sinkron. Pastikan akun lain tidak memakai posisi privat akun sebelumnya.
- Pertahankan `WatchHistory` lokal dan fungsi rekomendasi lokal yang bergantung padanya; jangan menghapus data pengguna saat migrasi. Cache dan progress akun merupakan data terpisah, bukan pengganti JSON lokal.

## 6. Tahap 2 — investigasi pencatatan tontonan FlufPlay ke akun

1. Audit `PlaybackTracker` dan bukti runtime yang tersedia untuk menentukan apakah sinyal dibuat berdasarkan URL/parameter tracking yang benar untuk player/client yang sedang digunakan. Jangan mengasumsikan ping buatan atau respons 204 berarti aktivitas masuk History.
2. Rancang state machine yang mengikuti event player nyata: mulai setelah media benar-benar dimainkan, jeda/buffering/seek/akhir/pergantian/penutupan dan logout. Pastikan durasi/posisi milidetik versus detik tepat, CPN dan data session terikat ke video yang benar, dan job lama dibatalkan. Perubahan switch harus menghentikan job aktif segera; mengaktifkan kembali harus mengikuti state player dan kebijakan privasi.
3. Jangan mengirim state playing selama pause, buffering, atau setelah sync dimatikan. Pengiriman final tidak boleh memakai nilai duration/akun sesi baru akibat race. Hormati keadaan History YouTube yang dijeda dan jangan mencoba mengakalinya.
4. Tampilkan status pengiriman yang jujur: kegagalan transport/auth, belum diverifikasi, atau terkonfirmasi hanya jika memang ada bukti memadai. Jangan log informasi sensitif. Tidak ada retry tanpa batas dan tidak ada antrean offline yang mengunggah tontonan lama diam-diam.
5. Dengan akun uji yang disetujui pengguna, tonton video biasa dan Short yang mudah dikenali; cek apakah keduanya muncul di History YouTube resmi, lalu refresh FlufPlay dan bandingkan. Uji pause, seek, pergantian video, pengaturan mati, History akun dijeda dan offline. Catat waktu/sumber serta kemungkinan jeda server; jangan menyimpulkan endpoint andal dari satu percobaan.

## 7. Tahap lanjutan — penghapusan akun

- Secara default tetap gunakan pengelolaan History YouTube resmi. Google menjelaskan bahwa penghapusan rentang di My Activity dapat juga menghapus riwayat pencarian: [YouTube Help](https://support.google.com/youtube/answer/95725?hl=en).
- Jika fitur hapus langsung direncanakan kemudian, identifikasi action/token dari respons untuk item/occurrence yang tepat dan uji menggunakan akun serta data uji yang dapat dihapus. Pastikan batas item versus seluruh akun, respons, undo/retry, konfirmasi pengguna, pembaruan cache dan hasil di YouTube. Jangan lakukan bulk deletion sebagai bagian probe.
- Jangan memberi label “hapus dari YouTube” pada operasi `WatchHistory.remove/clear` lokal. Penghapusan dari perangkat tidak otomatis menghapus akun, dan sebaliknya.

## 8. Pengujian dan kriteria penerimaan

### Otomatis

- Fixture parser sintetis: halaman awal, continuation, beberapa section, berbagai renderer yang sudah diamati, Shorts bila terbukti, video tak tersedia, item berulang, field kosong, bentuk tidak dikenal dan respons login.
- Repository dengan HTTP fake: 401/403/429/500, timeout, offline, 200 tak valid, empty valid, pembatalan, continuation ganda/berulang, refresh paralel, dan respons lama setelah logout/ganti akun.
- State/cache: isolasi akun A/B dan persona, tanpa identifier stabil, cache kedaluwarsa, mode offline, logout, data lokal tetap utuh, dan progress lokal tidak bocor ke akun lain.
- Tracker jika diubah: play/pause/resume, buffering, seek, ganti video, toggle sync, logout, penghentian saat aplikasi ditutup dan keterikatan data ke sesi; uji efek yang terlihat, bukan sekadar detail implementasi.

### Perangkat dan akun

- Bandingkan YouTube dan FlufPlay untuk akun/channel yang sama: urutan beberapa item pertama, section, Shorts, halaman lanjutan, refresh setelah ada tontonan baru di aplikasi YouTube, dan kondisi kosong/jeda.
- Uji dua akun/channel jika tersedia, logout/login, sesi kedaluwarsa, offline/online, pencarian, rotasi/navigasi, serta riwayat lokal tidak hilang.
- Uji tontonan dari FlufPlay muncul di YouTube secara terpisah dari uji baca. Uji hapus akun hanya pada tahap yang memang diimplementasikan dan menggunakan data yang boleh dihapus.
- Build `:app:assembleDebug` dengan Java 21, offline jika dependensi tersedia, satu worker dan prioritas rendah. Verifikasi resource laptop sebelum build; instalasi serta uji perangkat dicatat terpisah dari hasil build.

### Definisi selesai

- [ ] Daftar akun yang terbukti dari server tampil pada History dan tab You, dengan urutan/section dan continuation yang sesuai.
- [ ] Error login, offline, daftar kosong dan respons tidak dikenal dibedakan; data lokal tetap aman dan sumbernya jelas.
- [ ] Akun/channel tidak saling membocorkan state, cache, request lanjutan atau posisi.
- [ ] Penghapusan lokal tidak diklaim menghapus akun; tidak ada penghapusan akun tersembunyi.
- [ ] Pencatatan tontonan FlufPlay ke akun dinyatakan berhasil hanya setelah pengujian terpisah pada YouTube resmi; jika belum, keterbatasan ditampilkan dan dicatat.
- [ ] Pengujian relevan dan build lulus; hasil uji perangkat dicatat dengan batasannya.

## 9. Urutan pengerjaan

1. Verifikasi baca-saja FEhistory + identitas dan buat fixture sintetis.
2. Implementasikan repository/parser serta tes pagination dan error.
3. Integrasikan state akun, UI, cache aman dan mode lokal; uji akun/perangkat.
4. Audit dan perbaiki tracker berdasarkan bukti, lalu validasi pencatatan baru di YouTube resmi.
5. Pertimbangkan penghapusan akun langsung sebagai pekerjaan terpisah bila benar-benar tersedia dan dibutuhkan.
