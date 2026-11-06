# Rencana UI Profile FlufPlay

## Tujuan dan dasar

Rapikan tab You/Profile dan modal akun dengan mempertahankan alur akun, History dan Downloads yang sudah ada. Acuan teknis: `ANALISIS_PROFILE_HISTORY_DOWNLOAD_YOUTUBE.md` dan source FlufPlay. APK YouTube 21.43.523 menyediakan bukti komponen dan sejumlah state, tetapi susunan serta ukuran Profile yang tepat belum terpetakan. Rancangan di bawah adalah usulan UI FlufPlay, bukan klaim replika pixel-perfect.

## 1. Header Profile dan navigasi

- Rapikan susunan avatar, nama, handle jika tersedia, dan teks aksi akun di `LibraryTab.kt`. Pertahankan header dapat diklik untuk login ketika logout dan membuka modal ketika login.
- Hapus chip **Downloads** di bawah header. Pertahankan satu baris **Downloads** di bawah History, dengan callback yang sekarang membuka halaman unduhan internal.
- Pertahankan chip Account pada tahap awal; evaluasi jarak dan hierarki sehingga tidak bersaing dengan header yang juga dapat diklik.
- Ganti karakter `◷` pada baris Watch history dengan ikon yang konsisten dengan ikon aplikasi. Samakan warna, tipografi, jarak, dan label, serta pastikan target sentuh memadai.
- Buat layout adaptif untuk ponsel dan tablet, serta teks panjang tanpa terpotong secara merusak. Nilai ukuran adalah keputusan desain, bukan ukuran Profile referensi yang telah terverifikasi.

## 2. Preview History

- Pertahankan header History, View all, daftar horizontal, urutan saat ini, dan klik membuka video/Shorts sesuai alur yang ada.
- Tambahkan badge durasi bila `duration > 0` dan progress bila `duration > 0` serta `position > 0`; jangan mengarang progress akun yang tidak disediakan data.
- Tampilkan secara jelas sumber riwayat akun atau perangkat, tanpa mencampur kedua sumber. Pertahankan akses ke History perangkat ketika login.
- Pisahkan state loading awal, daftar berisi, tidak ada data yang terverifikasi, dan gagal memuat. Respons akun yang kosong tetapi belum dapat diverifikasi tetap merupakan state tidak terverifikasi, bukan diklaim kosong. Bila sebelumnya ada data dan refresh gagal, pertahankan data serta tampilkan informasi yang sesuai.
- Penanganan paginasi dan aksi history akun mengikuti kemampuan saat ini; tidak menambahkan klaim hapus atau pencarian server yang belum tersedia.

## 3. Modal akun

- Rapikan dialog `YouTubeAccountDialog` di `YouTubeAuth.kt`: identitas, handle yang tersedia, pengaturan sinkronisasi, dan aksi tertata jelas dalam satu hierarki.
- Pertahankan fungsi switch `PlaybackTracker.setSyncEnabled` beserta status yang tersimpan; tulis deskripsi yang sesuai dengan fungsinya.
- Bedakan aksi **Close** dan **Sign out**, dan pertahankan callback logout yang sekarang. Jangan tampilkan pernyataan bahwa semua konten membership/badge pasti tersedia hanya berdasarkan status login.
- Tambahkan state pemuatan dan gagal mengambil profil melalui state eksplisit pada `YouTubeAuth.refreshProfile`, dengan fallback data tersimpan dan kesempatan coba lagi. Tangani respons yang datang setelah logout/perubahan sesi agar profil lama tidak muncul kembali. Status cookie tidak diperlakukan sebagai bukti bahwa server telah memvalidasi sesi.
- Tidak menampilkan aksi tambah/ganti akun yang belum benar-benar didukung. Resource account switcher APK merupakan bukti fitur referensi, bukan bukti dukungan FlufPlay.
- Pastikan modal muat dan dapat di-scroll pada layar kecil, landscape, dan ukuran font besar; back atau ketuk di luar menutup modal tanpa logout.

## 4. Ruang lingkup dan file

- Utama: `app/src/main/java/id/flufclip/player/LibraryTab.kt`, `YouTubeAuth.kt`, dan `MainActivity.kt` untuk mengalirkan state History dan akun.
- Bila perlu gunakan drawable History yang sesuai dalam `app/src/main/res/drawable*`; pertahankan ikon yang sudah ada bila cocok.
- Perubahan `AccountHistory.kt` hanya bila diperlukan untuk merepresentasikan state yang dapat dibuktikan dari respons. Tidak mengubah mekanisme download, file tersimpan, login, cookie, atau sinkronisasi selain penanganan state yang diperlukan.
- Hormati perubahan yang sudah ada di working tree; jangan menghapus file atau melakukan reset.

## 5. Verifikasi setelah implementasi

1. Build/kompilasi sesuai lingkungan proyek dengan Java 21, offline bila dependensi tersedia, satu worker, dan prioritas rendah.
2. Uji state yang relevan: logout, login dengan profil tersedia, profil memuat/gagal, respons terlambat setelah logout, history lokal/akun sedang memuat/gagal/kosong terverifikasi, serta durasi/progress yang tersedia dan tidak tersedia.
3. Uji satu akses Downloads yang tersisa, buka History/View all dan video/Shorts, toggle sinkronisasi, close dialog dan sign out. Pastikan data lokal dan file unduhan tidak terhapus oleh perubahan UI.
4. Jika pengujian perangkat secara eksplisit diizinkan, verifikasi ukuran ponsel/tablet, orientasi dan ukuran font, serta alur login dan offline. Build atau tes source saja tidak membuktikan parity visual/perilaku di perangkat.

## Kriteria selesai

Profile memiliki tepat satu akses Downloads, modal akun menampilkan informasi dan aksi yang jelas, state History/akun tidak salah menggambarkan data, navigasi yang ada tetap berfungsi, dan pemeriksaan yang dijalankan dilaporkan sesuai bukti. Kesamaan pixel-perfect dengan referensi tidak diklaim tanpa pengukuran langsung yang diizinkan.

## Instruksi pelaksanaan berbasis bukti

Instruksi ini berlaku bagi AI atau pengembang yang mengerjakan rencana:

1. **Baca kondisi terbaru terlebih dahulu.** Periksa isi file dan working tree terkait sebelum mengubahnya. Dokumen analisis adalah petunjuk, bukan pengganti pemeriksaan source saat ini. Pertahankan perubahan pengguna yang sudah ada.
2. **Pisahkan fakta, inferensi, dan keputusan desain.** Kaitkan klaim dengan lokasi kode, resource APK, atau hasil pengujian yang benar-benar diperiksa. Jika bukti tidak cukup, tulis “belum diketahui”; jangan mengisi celah dengan asumsi.
3. **Jangan mengklaim kesamaan dengan YouTube tanpa bukti.** Nama resource dan string DEX membuktikan keberadaan identifier, bukan posisi, ukuran, kondisi tampil, atau interaksi aktual di Profile. Angka UI yang belum terkait dengan halaman tersebut merupakan keputusan desain, bukan hasil pengukuran referensi.
4. **Jangan menciptakan kemampuan data.** Tampilkan handle, durasi, progress, state kosong, atau aksi akun hanya sesuai data dan operasi yang benar-benar tersedia. Respons history akun yang kosong tetapi belum terverifikasi tidak boleh dipresentasikan sebagai riwayat kosong yang pasti. Jangan menambahkan tombol ganti/tambah akun atau penghapusan history akun yang tidak berfungsi.
5. **Lacak alur state sampai sumbernya.** Sebelum menampilkan loading, gagal, retry, atau data lama, periksa sumber state, pembatalan request, dan perubahan sesi. Respons yang terlambat tidak boleh mengembalikan identitas akun setelah logout; bedakan ketiadaan cookie dari kegagalan jaringan dan kegagalan server jika kodenya dapat membedakannya.
6. **Verifikasi perubahan yang dilakukan.** Jalankan pemeriksaan yang sesuai dan laporkan perintah serta hasil sebenarnya. Bedakan source review, build, uji otomatis, instalasi, dan pengujian perangkat; jangan menyebut hasil yang tidak dijalankan sebagai lulus atau parity visual sebagai terverifikasi.
7. **Jaga ruang lingkup.** Kerjakan rencana yang disetujui dan pertahankan perilaku, data history lokal, file unduhan, dan perubahan lain yang sudah ada. Jika terdapat konflik persyaratan atau keterbatasan yang menghalangi implementasi, jelaskan bukti dan dampaknya sebelum mengambil keputusan yang tidak dapat dibalik.
8. **Laporkan keterbatasan secara konkret.** Di akhir, sebutkan apa yang berubah, bagaimana diverifikasi, dan bagian yang belum dapat dibuktikan. Jangan menyembunyikan kegagalan build atau memperkirakan hasil pengujian.

## Catatan hasil implementasi

### Perubahan yang diterapkan

- **`LibraryTab.kt`**: header tab You memakai avatar 64 dp (ponsel) atau 72 dp (layar ≥ 600 dp), nama maksimal dua baris, handle dan aksi akun pada baris terpisah, serta tetap dapat diklik untuk login/modal. Chip Downloads dihapus; hanya chip Account dan satu baris Downloads di bawah History yang tersisa. Baris Watch history memakai `R.drawable.yt_w_history` menggantikan karakter `◷`.
- **`LibraryTab.kt:94-151`**: header History menyertakan label sumber (`From your YouTube account` atau `Watched on this device`). State dipisahkan menjadi loading awal, daftar berisi, tidak ada data terverifikasi, dan error. Jika daftar sudah berisi lalu refresh gagal, daftar dipertahankan dan error ditampilkan di atas kartu. Tombol Try again memicu ulang permintaan History.
- **`LibraryTab.kt:283-317`**: kartu preview history menampilkan badge durasi hanya saat `duration > 0` dan progress hanya saat `duration > 0 && position > 0`. Data akun tidak diisi progress karena `AccountHistory.kt` memang tidak menyediakannya. Lebar kartu 165 dp pada ponsel dan 210 dp pada layar ≥ 600 dp.
- **`YouTubeAuth.kt:80-84`**: state eksplisit `profileLoading` dan `profileError`.
- **`YouTubeAuth.kt:231-337`**: `refreshProfile` mencatat revisi sesi dan ID request, membatalkan request lama, memakai fallback profil tersimpan saat respons tidak lengkap, dan menerapkan hasil hanya jika sesi masih sama. Penulisan profil, prefs, error, dan status loading dilakukan pada main thread setelah pengecekan ulang; respons setelah logout atau login baru tidak dapat mengembalikan profil lama.
- **`YouTubeAuth.kt:449-550`**: modal akun memuat ulang profil saat dibuka, scrollable, menampilkan state loading/gagal dengan Try again, serta tetap menampilkan data tersimpan saat refresh gagal.
- **`YouTubeAuth.kt:567-616`**: label switch menjadi `Sync watch time` dengan deskripsi playback/watch-time ping. Kotak status berubah menjadi `Signed in on this device` dan `Membership access depends on your YouTube account.`; tidak ada klaim bahwa cookie telah divalidasi server atau benefit membership pasti tersedia. Aksi Close dan Sign out tetap terpisah; tidak ada aksi tambah/ganti akun.
- **`MainActivity.kt:321`**: `accountLoading` dimulai sesuai status login agar pemuatan awal tidak ditampilkan sebagai daftar kosong. **`MainActivity.kt:1694-1699`** mengalirkan sumber, loading, error, dan retry History ke LibraryTab. Pesan error History akun diseragamkan ke bahasa Inggris pada baris 343 dan 360.
- **`app/src/main/res/drawable/yt_w_history.xml`**: ikon History vektor 24 dp baru.
- `AccountHistory.kt`, mekanisme download, file tersimpan, login, cookie, dan sinkronisasi tidak diubah selain penggunaan state yang sudah ada.

### Verifikasi yang dijalankan

- Perintah: `taskset -c 0 nice -n 15 ./gradlew :app:compileDebugKotlin --offline --max-workers=1 --console=plain` di direktori `flufplay`. Gradle memakai `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`.
- Hasil kompilasi penuh: `BUILD SUCCESSFUL in 1m 33s`.
- Kompilasi ulang setelah perubahan teks terakhir: `BUILD SUCCESSFUL in 5s`.
- Tidak ada error kompilasi. Warning yang muncul adalah peringatan deprecation dan opt-in yang sudah ada pada source lain, bukan kegagalan perubahan ini.

### Batasan dan yang belum diverifikasi

- Tidak ada pengujian perangkat, emulator, atau visual. Ukuran, jarak, warna, perilaku scroll dialog pada landscape/font besar, dan tampilan tablet belum diamati langsung.
- Belum diuji dengan akun YouTube nyata untuk state login, profil tersedia, profil gagal dimuat, respons terlambat setelah logout, serta History akun yang loading/gagal/kosong.
- Uji unit dan uji otomatis tidak dijalankan; verifikasi otomatis yang dilakukan hanya kompilasi Kotlin.
- Karena itu, paritas visual/pixel-perfect dengan referensi tetap tidak diklaim.
