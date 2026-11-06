# Analisis awal Profile, History, Downloads, dan akun

Referensi: `referensi/youtube-21-43-523.apk`, package `com.google.android.youtube`, versi `21.43.523`. Analisis ini berdasarkan inventaris statis APK dan pembacaan source FlufPlay, tanpa menjalankan kedua aplikasi atau mengubah kode aplikasi. Nama aset menunjukkan komponen yang tersedia, bukan bukti bahwa komponen selalu ditampilkan atau susunan UI aktual. Ukuran, warna, transisi, dan seluruh state referensi masih perlu pembandingan perangkat/screenshot.

## Profile / tab You

- `flufplay/app/src/main/java/id/flufclip/player/LibraryTab.kt:47`: header profil memakai avatar 64 dp, nama, dan teks View account / Sign in to YouTube. Header membuka alur akun; dua chip Account dan Downloads mengikuti di bawahnya.
- `LibraryTab.kt:62`: History menampilkan maksimal 10 item secara horizontal dengan kartu selebar 165 dp, judul, dan uploader; tidak ada durasi atau progress di kartu preview. Berikutnya ada baris Watch history dan Downloads.
- `MainActivity.kt:1695`: saat login, preview menggunakan history akun; saat logout, history perangkat. Ketika data kosong, teks loading/error/empty bergantung pada status pemuatan akun.
- Arah perubahan: cocokkan hierarki header, konten dan posisi tombol, kartu history, menu dan susunan halaman berdasarkan tangkapan referensi untuk login dan logout, termasuk ukuran layar tablet. Jangan menyimpulkan fitur ada hanya dari label umum.

## History

- `WatchHistory.kt:84`: halaman perangkat memiliki pencarian lokal judul/uploader, grouping berdasarkan tanggal, menu hapus semua, hapus per item dan empty/no-results. Baris menggunakan thumbnail 152 dp, durasi, dan progress jika tersedia.
- `WatchHistory.kt:165`: halaman akun berbeda: judul YouTube History, Refresh, pencarian hanya pada item yang sudah dimuat, tombol Device history dan Manage on YouTube, bagian berdasarkan respons server, dan Load more manual. Tidak ada aksi hapus per item dalam layar ini. Status error dan loading ditampilkan.
- `AccountHistory.kt:61`: respons kosong atau tidak dikenali dianggap tidak dapat diverifikasi, bukan otomatis history kosong. Perlu membedakan respons kosong valid, gagal jaringan, sesi kedaluwarsa, loading awal dan loading lanjutan jika sumber data memberi bukti cukup.
- Arah perubahan: satukan bahasa visual kedua halaman tanpa menyamakan operasi datanya; tampilkan cakupan pencarian akun secara jujur, pertahankan navigasi ke history perangkat dan pengelolaan akun. Perilaku hapus, paginasi, dan penelusuran riwayat akun harus diverifikasi sebelum diklaim setara YouTube.

## Downloads

- `DownloadScreens.kt:51`: ada halaman internal dengan pencarian lokal, settings, item aktif, daftar unduhan, rekomendasi online jika tersedia, dan bottom navigation. Ukuran thumbnail responsif 128–292 dp tergantung perangkat/orientasi.
- `DownloadScreens.kt:108`: settings saat ini dialog informasi; opsi hapus menghapus entri daftar tetapi file tetap ada. Ini harus dibedakan secara eksplisit dari menghapus file. `DownloadingRow` menampilkan fase, persentase/ETA bila tersedia, dan pembatalan saat berjalan.
- APK referensi memuat aset bernama `downloads_page_zero_state`, `downloads_page_loading_spinner`, `downloads_page_download_recommendations_header`, `downloads_page_smart_downloads_header`, `downloads_page_smart_downloads_opt_in_banner`, `downloads_page_smart_downloads_zero_state`, serta `downloaded_video_with_context`. Ini bukti adanya template/komponen, bukan bukti bahwa fitur Smart Downloads atau rekomendasi aktif di semua akun.
- Arah perubahan: bandingkan state kosong, sedang mengunduh, selesai, gagal, offline, pencarian dan menu item. Jangan menampilkan fitur otomatis atau tindakan hapus file seolah sudah berfungsi jika belum tersedia.

## Akun

- `YouTubeAuth.kt:260`: login menggunakan dialog WebView; deteksi cookie memicu penyimpanan sesi. `YouTubeAuth.kt:358`: akun terhubung ditampilkan dalam AlertDialog dengan avatar/nama/handle dan aksi logout. Tab You memakai `YouTubeAuth.profile` dan `isLoggedIn`.
- APK memuat resource `account_switcher_name_text_color_light` dan `account_switcher_byline_text_color_light`; ini tidak membuktikan isi, urutan menu, dukungan multi-akun, atau perilaku login pada perangkat.
- Arah perubahan: ukur header, tampilan detail akun dan state signed out, loading identitas, gagal memuat profil, dan logout. Tidak menjanjikan pergantian multi-akun tanpa implementasi dan validasi.

## Tahapan verifikasi sebelum implementasi

1. Amati referensi di perangkat untuk setiap layar, pada kondisi login/logout yang tersedia; catat bahasa, ukuran/orientasi, isi data, konektivitas, dan varian akun. Ambil screenshot state kosong dan berisi serta buka menu masing-masing.
2. Bandingkan screenshot FlufPlay pada resolusi dan density yang sama; ukur padding, typography, ikon, ukuran thumbnail, warna, pembatas, scroll dan back navigation.
3. Susun tabel state dan aksi yang didukung sumber data masing-masing, lalu implementasikan bertahap dimulai dari tab You dan History, dilanjut Downloads dan akun.
4. Uji state awal/berhasil/kosong/error, pencarian dan navigasi, perbedaan akun/perangkat, serta file unduhan nyata. Hasil build saja tidak membuktikan kesamaan visual atau perilaku.

## Pendalaman statis DEX dan alur state (pemeriksaan lanjutan)

Pemeriksaan dibatasi dengan `nice -n 15`, `taskset -c 0`, dan `prlimit --as=1073741824` untuk proses pemindaian DEX secara streaming. Proses melaporkan affinity `{0}`, prioritas `15`, dan batas ruang alamat 1.073.741.824 byte. Ini membatasi proses analisis tersebut, bukan penggunaan RAM total seluruh sistem. Pemeriksaan dilakukan memakai pembacaan string DEX, bukan dekompilasi seluruh APK; tidak menjalankan aplikasi referensi.

### Bukti langsung pada DEX referensi

| Bagian | Bukti string dan lokasi APK | Makna yang didukung | Batas bukti |
|---|---|---|---|
| History | `classes.dex`, `classes2.dex`, `classes4.dex`: `FEhistory`; `classes3.dex`: `WATCH_HISTORY`, `SEARCH_HISTORY`, `REMOVE_FROM_HISTORY`, `CLEAR_WATCH_HISTORY`, `WATCH_HISTORY_PAUSED` | Ada identifier riwayat, pencarian/penghapusan dan status pause pada kode atau resource yang dikompilasi. | Belum membuktikan layar mana yang memuatnya, letak tombol, bahasa, availability, atau alur aksi. |
| Kontrol history | `classes7.dex`: `history/clear_watch_history`, `history/get_history_paused_state`, `history/pause_watch_history`, `history/resume_watch_history` | Terdapat nama path terkait operasi/status history. | String saja tidak membuktikan HTTP request, implementasi lengkap, atau state pada akun tertentu. |
| Downloads | `classes4.dex`: `DownloadsPageBrowseController.java`, `DownloadsPageController.java`, `DownloadsPageSectionListController.java`, `DownloadsPageHorizontalVideoCardShelfProvider.java`; `classes.dex`: `downloads_page_downloads_item_section_identifier`, `downloads_page_recommendations_item_section_identifier`, `downloads_page_smart_downloads_item_section_identifier` | Kode merujuk controller halaman, daftar section dan kartu horizontal; ada identifier bagian unduhan, rekomendasi dan smart downloads. | Nama source/controller adalah string di DEX, belum merupakan pseudocode dekompilasi atau bukti susunan yang tampil. |
| State offline | `classes4.dex`: `MainOfflineVideoDownloadCompleteEvent`, `MainOfflineVideoStatusChangedEvent`, `MainOfflineVideoDeleteEvent`; `classes6.dex`: `OFFLINE_VIDEO_POLICY_ACTION_DOWNLOAD_FAILED`, `OFFLINE_VIDEO_POLICY_ACTION_OK`; `classes3.dex`: `DOWNLOADS_PAGE_EMPTY` | Ada identifier untuk complete, status, delete, gagal/OK dan halaman kosong. | Tidak membuktikan semua state punya UI tertentu atau valid pada semua akun. |
| Akun | `classes2.dex`: `OfflineAccountSwitcherStoreImpl.java`, `lite_account_switcher`; `classes5.dex`: `LiteAccountSwitcherLauncherImpl.kt`, `launchLiteAccountSwitcher`, `supportAccountSwitching`; `classes7.dex`: strategi `ACTIVE_ACCOUNT_KIDS`, `BY_IDENTITY`, `SIGNED_OUT` | APK memiliki kode/identifier account switcher dan strategi identitas/log out. | Belum terbukti susunan halaman, jumlah akun yang tersedia, atau bahwa fitur selalu aktif. |

### Perbandingan alur yang dapat dibuktikan dari source FlufPlay

1. **Tab You:** `LibraryTab.kt:47-83` menggunakan daftar video dari pemanggil, membatasi preview ke 10 item, serta menampilkan header profil, chip, History dan dua baris navigasi. `MainActivity.kt:1695-1700` memilih riwayat akun ketika `isLoggedIn` dan lokal jika tidak. Ini tidak membuktikan susunan referensi identik.
2. **History lokal:** `WatchHistory.kt:47-69` menyimpan maksimal 100 entri lokal, memperbarui urutan berdasar URL dan mengembalikan posisi ke nol jika mendekati akhir. `WatchHistory.kt:76-80` mencampur label bahasa `Today`, `Kemarin`, dan tanggal Locale.US. Ini risiko konsistensi teks, bukan hasil observasi perangkat.
3. **History akun:** `HomeFeed.kt:119-123` memakai `FEhistory` atau continuation; `AccountHistory.kt:23-73` mengenali section, video, lockup dan Shorts, tetapi respons tanpa entri dan token diperlakukan sebagai error yang tidak terverifikasi. `MainActivity.kt:331-361` memuat ulang berdasarkan session/tab/history, lalu memuat halaman berikutnya melalui permintaan terpisah. UI hanya memfilter entri yang sudah dimuat dan tombol Load more dipicu manual. `WatchHistory.kt:165-207` tidak menyediakan operasi hapus per item akun; pengelolaan dialihkan melalui callback ke YouTube.
4. **Downloads:** `SavedDownloads.kt:25-51` menyimpan metadata URI, nama, MIME, ukuran, dan data video dalam SharedPreferences; `forget` hanya menghapus metadata. `DownloadService.kt:25-26,126-130` menyimpan state aktif, completed, cancelled atau failed. `DownloadScreens.kt:79-90` memperlihatkan status aktif selama `video != null` dan phase bukan COMPLETED; pesan kosong hanya ditampilkan jika `state.video == null`, sehingga status gagal/batal yang masih punya video dapat menghalangi pesan empty. Ini konsekuensi kondisional source, belum hasil uji perangkat.
5. **Profil akun:** `YouTubeAuth.kt:74-90` menentukan status login dari keberadaan nama cookie, bukan validasi server saat itu. `YouTubeAuth.kt:205-253` mengambil profil dari `account/account_menu` dan mengabaikan kegagalan sync; maka state loading/gagal identitas belum diekspos secara eksplisit. `YouTubeAuth.kt:428-463` punya switch sinkronisasi rekomendasi sendiri. Identifier account switcher di APK tidak membuktikan FlufPlay sudah punya fungsi switching yang setara.

### Rencana berbasis bukti kode

- Rancang model state terpisah untuk history: inisialisasi, loading, data, empty terverifikasi, tidak terautentikasi, error, dan paginasi; pastikan pencarian menjelaskan cakupan data yang dimuat. Tambah pause/clear akun hanya jika operasi dan respons yang dipakai sudah diimplementasikan dan diverifikasi, bukan sekadar karena string referensi ada.
- Untuk Downloads, rancang state queue/progress, complete, failed/cancelled, file tidak tersedia dan empty; pisahkan aksi menghapus entri dari menghapus file fisik. Pertimbangkan section rekomendasi hanya saat data tersedia dan fungsi smart download hanya jika benar-benar dibangun.
- Untuk akun, sediakan state identitas memuat/gagal dan penanganan sesi yang ditolak server; jangan mengklaim multi-account switcher hanya karena referensi memilikinya.
- Rincian posisi, warna, ukuran, tipografi, animasi dan parity visual referensi **tidak bisa disimpulkan dari pemindaian string ini**. Analisis lebih lanjut dapat menarget kelas atau resource tertentu dengan dekompilasi terbatas jika identitas kelas dapat ditemukan; tidak perlu menjalankan UI untuk melanjutkan analisis kode.

## Pendalaman template UI Profile/You — batas analisis statis

Pemeriksaan APK ini menelusuri isi bytes printable pada file EML tertentu, bukan sekadar nama file. Semua path aset di bawah relatif terhadap `assets/mainapp_filegroup/_srs_resources_eml_bundle/` dalam APK referensi.

| Bukti langsung | Isi yang ditemukan | Kesimpulan yang aman |
|---|---|---|
| `horizontal_shelf.eml-js_d92d818ae941595d` | Referensi ke `horizontal_shelf_content.eml-js` | Ada relasi wrapper dan konten shelf horizontal. |
| `horizontal_shelf_content.eml-js_4eb893672a22ede6` | `horizontal_shelf_content.shelf_header`, `horizontal_shelf.lockup_$0`, `horizontal_shelf.cell_video_attribute_$0`, `horizontal_shelf_content.flexible_actions`, `$0_scroll_offset`, `horizontal-video-shelf.view` | Template memuat konsep header, item video/lockup, aksi dan offset scroll horizontal. String ini belum menetapkan bahwa History Profile menggunakan template ini atau menunjukkan tata letak hasil render. |
| `shelf_header_content.eml-js_816933d8734b1ec1` | Referensi `avatar.eml`, `thumbnail.eml`, `shelf_header_badge.eml`, `flexible_actions.eml-js`, `overflow_button.eml`, `shelf_header_icon_button.eml` | Header shelf mendukung komponen-komponen tersebut; tidak berarti semua selalu muncul bersamaan. |
| `lockup.eml-js_4765059d6a7edc91` | `imp_lockup_root`, `lockup_thumbnail.eml`, `imp_metadata_container`, `lockup_metadata_ue.eml-js`, `lockup_attachments.eml` | Template kartu memiliki struktur thumbnail, container metadata, dan attachment. Bukan bukti semua kartu Profile memakai varian ini. |
| `lockup_metadata.eml_76ae4ef3c3d82c9c` | Referensi avatar, decorated avatar, badge, download badge, `progress.eml` | Komponen metadata dapat melibatkan elemen tersebut; penggunaan kondisi masing-masing belum diketahui. |
| `page_header.eml-js_86b7a480124cd9b2` dan `page_header_inner.eml-js_23f8ce17701839e7` | Wrapper memanggil inner; inner merujuk image banner, decorated avatar, title, content metadata, badge, flexible actions, dan channel description preview | APK memiliki template header halaman yang tersusun dari komponen tersebut. Mengingat ada referensi channel, **belum terbukti ini header Profile/You**. |
| `downloads_page_zero_state.eml_286b9c42e7239aaa` | `ic_downloads_page_empty_light`, `ic_downloads_page_empty_dark`, `downloads_page_empty_light`, `downloads_page_empty_dark` | Ada identifier aset zero-state unduhan untuk tema terang dan gelap. Belum diketahui ukuran/isi teks atau syarat tampil. |

### Dampak pada usulan UI FlufPlay

- Struktur kartu FlufPlay (`LibraryTab.kt:69-75`) baru menyediakan gambar, judul dan uploader; template umum referensi menunjukkan adanya kemungkinan metadata tambahan, badge dan progress. Perlu menelusuri binding History secara spesifik sebelum menyatakan elemen itu wajib pada preview Profile.
- FlufPlay memakai satu susunan tetap header akun + dua chip + preview History + baris Watch history/Downloads (`LibraryTab.kt:47-83`). Bukti APK saat ini belum mengungkap susunan, jumlah section, menu atau teks Profile YouTube, sehingga perbedaan posisi tidak dapat diukur atau ditetapkan dari temuan ini.
- Templates referensi mengarah pada UI yang tersusun dari header, aksi fleksibel, shelf, dan kartu; mengembangkan FlufPlay dengan komponen terpisah dapat memudahkan state yang berubah, tetapi ini rekomendasi desain, bukan pembuktian bahwa halaman Profile referensi selalu tersusun demikian.

### REA dan batas resource

Mencoba `rea inspect-android-package` dengan JADX MCP JAR lokal, `REA_JADX_HEAP_MIB=1024`, scope systemd `CPUQuota=50%`, `MemoryMax=1500M`; proses kemudian dibatasi ke CPU 0. Scope terverifikasi dengan `CPUQuotaPerSecUSec=500ms` dan `MemoryMax=1572864000`; penggunaan mendekati batas dan `memory.events` menunjukkan tekanan pada `max`. Proses dihentikan sebelum selesai untuk menjaga batas resource, sehingga **tidak ada hasil dekompilasi REA atau Evidence ID yang dapat diklaim**. Penelusuran EML yang dilaporkan di atas dilakukan terpisah dengan satu CPU, prioritas 15 dan batas ruang alamat 1 GiB.

Status pertanyaan: struktur komponen umum **sebagian terjawab**; susunan halaman Profile/You, binding History, interaksi akun dan state persis referensi **belum terjawab**. APK belum dijalankan dan tidak ada source aplikasi yang diubah.

## Bukti tambahan dari tabel resource dan DEX

Pemeriksaan `aapt dump resources` dan pemindaian DEX dilakukan secara statis dengan `nice 15`, affinity satu CPU `{0}`, dan batas ruang alamat 1 GiB. Tabel resource mengonfirmasi deklarasi dan nilai, tetapi tidak dengan sendirinya membuktikan halaman yang menggunakannya.

- **State logout dan History dijeda:** ada `drawable/ic_logged_out_library_dark`, `ic_logged_out_library_light`, dan `ic_watch_history_paused_dark` / `light` (juga varian inset). Ini bukti APK membawa aset untuk state tersebut, bukan bukti bahwa state ini selalu muncul pada tab You.
- **History:** `dimen/item_section_list_header_history_top_padding = 4dp`, `...bottom_padding = 4dp`, dan `...min_height = 0dp`. Angka-angka ini berlaku untuk resource bernama tersebut; belum terbukti digunakan oleh preview History di Profile.
- **Akun:** resource `account_switcher_drawable_internal_padding = 10dp`, `account_switcher_drawable_padding = 8dp`; `account_switcher_thumbnail_size` mengikuti `yt_sys_measurement_size_hero` → `yt_ref_dimension_size40 = 40dp`. Resource teks mencakup `account_switcher_add_account`, `...manage_accounts_on_device`, `...other_accounts`, `...offline_account`, `...switch_account`, dan error credentials/general. Ini mendukung adanya rancangan untuk pemilihan akun, pengelolaan, offline, dan error, tetapi bukan bukti seluruh opsi tersedia bagi semua akun.
- **Profil umum:** `profile_avatar_size` mengikuti `yt_sys_measurement_avatar_size_expanded` → `yt_ref_dimension_size48 = 48dp`; `profile_avatar_padding_end = 12dp` dan `profile_item_row_gap = 34dp`. Jangan gunakan sebagai ukuran header tab You tanpa bukti hubungan pemakai; nama profil bisa dipakai konteks lain. FlufPlay menggunakan avatar 64dp di `LibraryTab.kt:51` sehingga angka tersebut belum layak dijadikan klaim selisih spesifik Profile.
- **Filter Downloads:** ada string resource `downloads_page_all_menu_item`, `downloads_page_videos_menu_item`, `downloads_page_playlists_menu_item`, dan label aksesibilitas filter. Ini menunjukkan dukungan label filter dalam APK, bukan kepastian bahwa filter tampil untuk setiap konfigurasi.
- **Rute dan state:** `FElibrary` ditemukan di `classes.dex`, `classes2.dex`, dan `classes4.dex`; `FEhistory` juga ditemukan. Ada `TAB_LIBRARY`, `TAB_ACCOUNT`, `TAB_HISTORY`, `SCREEN_HISTORY_PAGE`, `you_tab_avatars_storage`, dan `YouTabAvatarsPromo(thumbnails=`. String-string ini belum menetapkan hubungan pemanggilan atau urutan elemen.
- **Koreksi pembacaan EML:** `account_header` muncul dalam blok nama komponen yang sama pada banyak template berbeda; nama itu sendiri kemungkinan bagian dari daftar/skema umum. Kemunculan tersebut tidak cukup untuk menyimpulkan bahwa tiap template merender header akun atau bahwa page header tertentu adalah halaman You.

Dibandingkan FlufPlay, pemeriksaan ini mengungkap kemungkinan state dan opsi yang layak dianalisis lebih lanjut (logout, history paused, account switcher, filter unduhan). Susunan layar You yang persis dan logika kondisi tampil masih belum terpetakan; resource tambahan tidak mengubah batas kesimpulan tersebut.
