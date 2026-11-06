# Akun, login, dan riwayat — YouTube 21.43.523 vs FlufPlay

APK `youtube-21-43-523.apk`, SHA-256 `4410cf3e9a37db09e8bd0fcc2176d047284ce95192598e9118a5249194c1b84c`, package `com.google.android.youtube`, versi `21.43.523` (`1561307650`), min SDK 29, target SDK 37. Sumber: REA/JADX 0.7.1 (decompile), `dexdump` (bytecode), `aapt2` (manifest), probe InnerTube anonim, dan logcat perangkat. APK tidak dijalankan. Nama kelas aplikasi sebagian terobfuscasi; nama asli diambil dari string log `SourceFile` di dalam dex.

## Ringkasan

1. **YouTube tidak menyalin data lokal ke akun.** Login menghasilkan sebuah *identity* (`accountName`, `pageId`, `dataSyncId`, status incognito/persona, dan tipe delegasi) yang disimpan di tabel SQLite `identity`. Setiap request kemudian membawa header identity tersebut; data akun (termasuk riwayat) baru muncul karena server mengembalikan data untuk identity itu.
2. **Riwayat YouTube adalah feed server `FEhistory`, bukan database lokal.** Tanpa konteks akun, endpoint ini hanya mengembalikan ajakan login. FlufPlay belum pernah memanggil `FEhistory` sama sekali; layar Riwayat saat ini hanya membaca `WatchHistory` lokal (SharedPreferences), sehingga riwayat akun tidak mungkin muncul setelah login.
3. **Force close berasal dari `PlaybackTracker`, bukan dari login itu sendiri.** Setelah login, `PlaybackTracker.start` aktif dan setiap 12 detik memanggil `player.currentPosition` dari `Dispatchers.IO`. ExoPlayer menolak akses dari thread selain main. Dua crash record di perangkat menunjukkan stack yang sama: `PlaybackTracker.kt:73` → `MainActivity.kt:813`.
4. **Beranda akun sudah dikembalikan server, tetapi dibuang parser FlufPlay.** Dengan cookie akun, `FEwhat_to_watch` mengembalikan 28 video dalam `richItemRenderer` + `lockupViewModel`. `HomeFeed.parsePage` versi lama hanya membaca `videoRenderer`, sehingga hasilnya 0 item dan aplikasi diam-diam fallback ke Kiosk/pencarian. Ditambah lagi Beranda hanya dimuat sekali lewat `LaunchedEffect(Unit)`, jadi login tidak memicu request ulang.

## 1. Model login di APK referensi

### 1.1 Login native memakai akun Android + token Google Play Services

Kelas `CachingAuthTokenProvider` (`Laclz`) membuat `new Account(accountName, "com.google")` lalu meminta token lewat jalur Google Play Services. Ia juga memakai `GoogleAuthUtil`-style `axrg` dengan bundle `handle_notification`. Ini **bukan** login cookie WebView.

- Bukti: `defpackage.aclz` metode `a` dan `f` — `ev_...` belum dikutip untuk metode ini; isi metode terlihat di `classes2.dex` (`aclz.a`, `aclz.f`).
- String kunci: `com.google`, `handle_notification`, `AuthTokenProvider: clearToken`.

### 1.2 Identity disimpan di SQLite, dengan `datasync_id`

Tabel `identity` berevolusi lewat migrasi. Migrasi case 7 menambahkan kolom `datasync_id`:

```sql
ALTER TABLE identity ADD COLUMN datasync_id TEXT
```

Bukti: `defpackage.acmp` metode `a`, overload case 7 — `ev_f225878a9a039aa5304d016b2dbae32e161069c6f3036d06bf51d6097cf317da`.

Tabel yang sama juga menyimpan `page_id`, `delegation_type`, `is_persona`, `is_unicorn`, `is_griffin`, `is_teenacorn`, dan `delegation_context` (migrasi case 1, 6, 7, 8, 10). Query di `DefaultIdentityStoreOld` membuang identity dengan `datasync_id` kosong:

- `[Clockwork][Database] getAccountInfos dropping empty datasyncid`
- `[Clockwork][Database]Dropping pref acct w/ empty datasync id`
- `datasync_id = ?`

Bukti: string dan bytecode `classes2.dex`, kelas `Lacmb`/`Lacmn`; log `AbstractIdentityStore getIdentityByDataSyncId() hitting DB on main thread.` ada di `Lacmn`.

### 1.3 Field identity dan artinya

`AccountIdentity` (`aclr`) memiliki field berikut. Nama field diambil dari `toString()` kelas tersebut:

| Field | Arti | Metode |
|---|---|---|
| `a` | id internal identity | `k()` |
| `b` | nama akun Google | — |
| `c` | `pageId` (akun brand/channel) | `l()` |
| `d` | `isIncognito` | `h()` |
| `e` | `wasUnicorn` | — |
| `f` | `isPersona` | — |
| `g` | `dataSyncId` | `i()` |
| `h` | `isUnicorn` | — |
| `i` | `isGriffin` | — |
| `j` | `isTeenacorn` | — |
| `k` | `delegationContext` | `j()` |
| `l` | `gaiaDelegationType` | — |

Bukti: `defpackage.aclr` metode `toString` — `ev_686f6e55b221e1870ad5af8e2a7aee8a8ea0e1b1ca27ae5edd4823d760d844dd`. `aclr.d()` bernilai `pageId != ""`, `aclr.i()` mengembalikan `dataSyncId`, dan `aclr.h()` mengembalikan `isIncognito`.

### 1.4 Sign-in menyimpan identity dan memberi tahu observer

`DefaultSignInController` (`Lacsc`) menerima identity hasil sign-in, menyimpannya lewat `acmtVar.k(identity)`, memperbarui state sign-in (`acmw.v(...)`), lalu mengirim event akun. Pada jalur akun yang sudah aktif, controller juga menangani perpindahan akun dan memanggil `j(false, true)`.

Bukti: `defpackage.acsc` metode `d` — `ev_f467fe185c7735a5430a42d51d20e17c877c0d3ca00708048b51fe2221c1dd52`.
Log yang terlihat: `Only one concurrent post-auth sign-in allowed.` (tag `DefaultSignInController`).

### 1.5 Perubahan akun membersihkan token dan identity lama

`AccountsChangedHandler` (`Laqgp`) menangani dua hal:

- **Rename akun:** membandingkan akun lama/baru lalu memanggil `u(oldName, newName)`.
- **Akun dihapus:** `aclx.h(list)` menghapus token, `aewr.c(...)` memberi tahu observer, dan identity terkait dihapus dari store.

Bukti: `defpackage.aqgp` metode `c`/`d` — `ev_e7b07ed906059c30c8d77b6f5d2fb3f6cd57ea39f82f7477a757ae080909b779`.
Implikasi: state yang di-scope ke akun (token, identity, cache offline) memang di-invalidasi saat akun berubah. YouTube tidak mencoba “memindahkan” riwayat guest ke akun.

## 2. Bagaimana request di-scope ke akun

Header provider `aqda` (`defpackage.aqda`, metode `b`) memiliki tiga mode:

| Mode | Kondisi | Header yang ditambahkan |
|---|---|---|
| `cid = 1` | signed-in dan identity **bukan** incognito | `X-YouTube-DataSync-Id: <dataSyncId>` |
| `cid = 2` | signed-in dan `pageId` tidak kosong (akun brand) | `X-Goog-PageId: <pageId>` |
| `cid = 0` | signed-in dan identity adalah delegated child | `X-YouTube-Delegated-Child: true` |

Bukti: `defpackage.aqda` metode `b` — `ev_b3f370368c2f97f71cdd3b790ee78a5942cc64ce2ab76e15ccf6e9442b7e7e8f`.

Jadi “data akun ikut” di aplikasi native terjadi karena setiap request membawa identity akun yang sedang aktif. Bukan karena ada proses migrasi data lokal ke akun.

## 3. Riwayat adalah feed server `FEhistory`

`FEhistory` adalah browse ID. Ia muncul di kelas `Lauoa` dan dipakai untuk memeriksa apakah respons browse berisi konten:

- `Lauoa.v(kpp)` memeriksa `FEhistory` lalu membaca field protobuf pada respons.
- Bukti: `defpackage.auoa` metode `v` — `ev_bee3025deac9f54390229e9c07749c46015251930b0320f96a8013aef7737adf`.
- `FEhistory` juga muncul di dex `classes.dex`, `classes2.dex`, dan `classes4.dex`.

Probe anonim ke `POST https://www.youtube.com/youtubei/v1/browse?prettyPrint=false` dengan `browseId=FEhistory` (WEB context, tanpa cookie) mengembalikan:

```text
messageRenderer: "Keep track of what you watch"
icon: WATCH_HISTORY
button: "Sign in"  -> signInEndpoint
```

Respons 28.846 byte, tanpa satu pun `videoRenderer`. Artinya server memang **menolak menampilkan riwayat tanpa konteks akun**; riwayat tidak disimpan di APK atau di perangkat sebagai sumber utama.

Catatan: response dengan cookie akun tidak diambil dalam analisis ini. Bentuk renderer terautentikasi (misalnya `videoRenderer`, `lockupViewModel`, atau `compactVideoRenderer`) belum diverifikasi untuk akun ini.

## 4. Perbandingan dengan FlufPlay

| Hal | YouTube 21.43.523 | FlufPlay saat ini |
|---|---|---|
| Login | Akun Android + token Google Play Services | WebView `accounts.google.com`, lalu simpan cookie mentah |
| Identity | Tabel `identity` + `pageId` + `datasyncId` | Tidak ada; hanya `isLoggedIn` dan profil |
| Header akun | `X-YouTube-DataSync-Id` / `X-Goog-PageId` / delegated child | `Cookie`, `Authorization: SAPISIDHASH`, `X-Goog-AuthUser: 0` |
| Riwayat | Browse server `FEhistory` | `WatchHistory` lokal (SharedPreferences, maks. 100 entri) |
| Subscription | Browse `FEsubscriptions` | Belum tersedia (teks di `LibraryTab.kt:101`) |
| Ganti akun | Identity diganti, token/identity lama dibersihkan | Logout menghapus semua cookie dan prefs auth |

Bukti kode FlufPlay:

- `YouTubeAuth.buildAuthHeaders()` menyusun `Cookie`, `Origin`, `X-Origin`, `X-Goog-AuthUser: 0`, dan `Authorization: SAPISIDHASH` (`YouTubeAuth.kt` sekitar baris 150–170).
- `HomeFeed.start()` hanya memanggil `browseId=FEwhat_to_watch` (`HomeFeed.kt:56`). Tidak ada pemanggilan `FEhistory` di seluruh `app/src/main/java`.
- `WatchHistory` membaca/menulis JSON di SharedPreferences `watch_history`, maksimal 100 video (`WatchHistory.kt:46–66`).
- Layar Riwayat di `MainActivity.kt:1334` memakai `watchHistory` lokal, bukan feed akun.
- Cookie juga diinjeksi ke request NewPipe (`VideoRepository.kt:56`) dan ke file cookie yt-dlp (`YouTubeAuth.writeNetscapeCookiesFile`), tetapi NewPipe tidak dipakai untuk mengambil `FEhistory`.

Konsekuensi:

- Login berhasil membuat Beranda dan Live Chat memakai cookie, tetapi **layar Riwayat tetap menampilkan riwayat lokal perangkat**. Ini yang membuat “riwayat akun tidak masuk”.
- Untuk akun brand, FlufPlay tidak mengirim `X-Goog-PageId`. Di aplikasi native header ini wajib untuk akun brand; untuk klien WEB, dampaknya **belum diverifikasi** (disimpulkan dari perilaku native, bukan dari probe web ber-cookie).
- Menyalin riwayat lokal ke akun bukan solusi yang dipakai YouTube. Riwayat lokal sebaiknya tetap menjadi cache offline; riwayat akun harus diambil dari server.

### 4.1 Akar masalah Beranda tidak berubah setelah login

Probe dengan cookie akun yang tersimpan di perangkat (`Iqbalhs`, akun biasa tanpa `pageId`) memberikan hasil:

| Request | Hasil |
|---|---|
| `FEwhat_to_watch` **dengan** cookie + `SAPISIDHASH` | 28 video, `richItemRenderer` + `lockupViewModel`, continuation tersedia |
| `FEwhat_to_watch` **tanpa** cookie | `feedNudgeRenderer` “Coba telusuri untuk memulai”, 0 video |
| `FEhistory` dengan cookie | 7 `lockupViewModel` + continuation |
| `FEsubscriptions` dengan cookie | 19 `richItemRenderer` + 13 `lockupViewModel` |

Artinya autentikasi cookie FlufPlay **bekerja**: server memang mengembalikan feed akun. Masalahnya ada di dua tempat:

1. `HomeFeed.parsePage` lama hanya membaca `contentObj.optJSONObject("videoRenderer")`. Feed akun memakai `richItemRenderer.content.lockupViewModel`, jadi seluruh item dibuang dan `accountHome.items` kosong. `MainActivity` lalu masuk ke fallback Kiosk/pencarian tanpa error yang terlihat.
2. Beranda dimuat oleh `LaunchedEffect(Unit) { loadHome() }` (sekali saat komposisi). `YouTubeAuth.saveAuth()` mengubah `isLoggedIn`, tetapi tidak ada yang memicu `loadHome()` ulang. Setelah login, layar tetap memakai feed lama sampai pull-to-refresh atau aplikasi dibuka ulang.

Perbaikan yang diterapkan:

- `HomeFeed` sekarang mem-parse `lockupViewModel` (`contentType`, `contentId`, title, metadataRows, thumbnail, avatar, badge durasi/LIVE) di samping `videoRenderer` lama.
- `MainActivity` memakai `LaunchedEffect(YouTubeAuth.isLoggedIn)`; login memicu `loadHome()`, logout membersihkan cache Beranda terkait.
- Validasi logika parser terhadap respons akun nyata: 23 video awal + 20 video continuation berhasil dipetakan (judul, channel, views, tanggal, durasi, status live, thumbnail, avatar).

## 5. Penyebab force close yang teramati

Perangkat: `25040RP0AG`, Android 16. FlufPlay terpasang: `id.flufclip.player` versi `0.1.0` (`versionCode 1`).

Dua crash record di `adb logcat -b crash`:

```text
FATAL EXCEPTION: DefaultDispatcher-worker-13
Process: id.flufclip.player, PID: 13763
java.lang.IllegalStateException: Player is accessed on the wrong thread.
Current thread: 'DefaultDispatcher-worker-13'
Expected thread: 'main'
  at androidx.media3.exoplayer.ExoPlayerImpl.getCurrentPosition(ExoPlayerImpl.java:1142)
  at id.flufclip.player.MainActivityKt.App$startInfo$lambda$259(MainActivity.kt:813)
  at id.flufclip.player.PlaybackTracker$start$2.invokeSuspend(PlaybackTracker.kt:73)
```

Crash kedua pada `04:16:56` memiliki stack yang sama (`DefaultDispatcher-worker-9`).

Akar masalah:

- `PlaybackTracker.start()` hanya berjalan bila `YouTubeAuth.isLoggedIn` bernilai true (`PlaybackTracker.kt:55`).
- Di dalam `trackingJob` (`Dispatchers.IO`), setiap 12 detik ia memanggil `positionProvider()` (`PlaybackTracker.kt:73`).
- `MainActivity.kt:813` mengirim `{ player.currentPosition }` sebagai `positionProvider`.
- ExoPlayer hanya boleh diakses dari main thread, sehingga aplikasi crash tepat setelah login dan pemutaran berjalan.

Perbaikan minimum: panggil `positionProvider()` di main thread, misalnya:

```kotlin
val posMs = withContext(Dispatchers.Main.immediate) { positionProvider() }
```

Alternatif: simpan posisi terakhir dari main thread ke `@Volatile var`, lalu loop IO hanya membaca nilai itu. `PlaybackTracker.stop()` yang memakai `player.currentPosition` di `MainActivity.kt:473` juga harus dipastikan selalu dipanggil dari main thread.

## 6. Rekomendasi implementasi

### P0 — hentikan force close

- Jalankan pembacaan `player.currentPosition` di `Dispatchers.Main.immediate` di dalam loop `PlaybackTracker`.
- Tambahkan `try/catch` di sekitar pembacaan posisi agar kegagalan player (misalnya saat player dilepas) tidak menjatuhkan proses.

### P1 — ambil riwayat akun dari `FEhistory`

- Buat klien baru (misalnya `AccountHistory.kt`) yang memakai `YouTubeAuth.buildAuthHeaders()` dan `OkHttp`, lalu POST ke `https://www.youtube.com/youtubei/v1/browse?prettyPrint=false` dengan body `browseId=FEhistory` dan context WEB yang sama seperti `HomeFeed`.
- Parse `sectionListRenderer` → `itemSectionRenderer` → `videoRenderer`/`lockupViewModel`/`compactVideoRenderer`, termasuk `continuationItemRenderer` untuk halaman berikutnya.
- Tampilkan hasil server di layar Riwayat saat login; gunakan `WatchHistory` lokal sebagai fallback offline. Jangan menimpa riwayat lokal dengan data server.
- Simpan cache per akun (kunci: nama akun + page ID) dan hapus cache saat logout, supaya riwayat akun lama tidak muncul setelah ganti akun.

### P2 — akun brand dan subscription

- Ambil `pageId` akun aktif dari respons `account_menu`/sign-in, lalu kirim `X-Goog-PageId` bila akun tersebut akun brand. Ini meniru `aqda` mode 2; untuk klien WEB efeknya perlu diuji.
- Subscription dapat memakai browse `FEsubscriptions` dengan pola yang sama.
- Jangan mencoba memigrasikan riwayat lokal ke akun. Perilaku referensi adalah identity-switch + fetch server, bukan copy data.

## 7. Tabel bukti

| Klaim | Bukti |
|---|---|
| Identity punya `dataSyncId`, `pageId`, `isIncognito`, dll. | `defpackage.aclr.toString` — `ev_686f6e55b221e1870ad5af8e2a7aee8a8ea0e1b1ca27ae5edd4823d760d844dd`; `classes2.dex` |
| Migrasi menambahkan `datasync_id` | `defpackage.acmp.a` case 7 — `ev_f225878a9a039aa5304d016b2dbae32e161069c6f3036d06bf51d6097cf317da` |
| Request native membawa header identity | `defpackage.aqda.b` — `ev_b3f370368c2f97f71cdd3b790ee78a5942cc64ce2ab76e15ccf6e9442b7e7e8f` |
| Sign-in menyimpan identity | `defpackage.acsc.d` — `ev_f467fe185c7735a5430a42d51d20e17c877c0d3ca00708048b51fe2221c1dd52` |
| Perubahan akun menghapus token/identity | `defpackage.aqgp.d` — `ev_e7b07ed906059c30c8d77b6f5d2fb3f6cd57ea39f82f7477a757ae080909b779` |
| `FEhistory` adalah browse feed | `defpackage.auoa.v` — `ev_bee3025deac9f54390229e9c07749c46015251930b0320f96a8013aef7737adf` |
| `FEhistory` anonim hanya menampilkan ajakan login | Probe `youtubei/v1/browse` tanpa cookie: `messageRenderer` “Keep track of what you watch”, tombol “Sign in” |
| FlufPlay tidak memanggil `FEhistory` | `grep -rn 'FEhistory' app/src/main/java` → tidak ada hasil |
| Feed akun memakai `lockupViewModel`, feed anonim memakai `feedNudgeRenderer` | Probe `FEwhat_to_watch` dengan/tanpa cookie perangkat |
| Parser lama hanya membaca `videoRenderer` | `HomeFeed.parsePage` sebelum perubahan; feed akun menghasilkan 0 item |
| Beranda hanya dimuat sekali saat komposisi | `MainActivity.kt` `LaunchedEffect(Unit)` sebelum perubahan |
| FlufPlay memakai riwayat lokal | `WatchHistory.kt:46–66`, `MainActivity.kt:1334` |
| FlufPlay tidak mengirim `X-Goog-PageId`/`X-YouTube-DataSync-Id` | `YouTubeAuth.buildAuthHeaders()` hanya Cookie/SAPISIDHASH/AuthUser |
| Force close karena akses ExoPlayer dari IO | Logcat `11-01 04:14:24` dan `04:16:56`, stack `PlaybackTracker.kt:73` → `MainActivity.kt:813` |

## 8. Yang belum diketahui

- Bentuk persis respons `FEhistory` untuk akun yang login (renderer dan continuation) belum diambil karena cookie akun tidak dipakai dalam probe ini.
- Apakah klien WEB memerlukan `X-Goog-PageId` untuk akun brand, atau cukup cookie + `X-Goog-AuthUser`. Perilaku native terlihat, perilaku web belum diverifikasi.
- Apakah `account_menu` versi sekarang menyediakan `pageId`/`dataSyncId` yang bisa dipakai FlufPlay; perlu probe dengan cookie akun.
- Perilaku runtime aplikasi referensi (urutan layar, cache per akun, kapan refresh) tidak diamati; analisis ini statis kecuali probe anonim dan logcat FlufPlay.
