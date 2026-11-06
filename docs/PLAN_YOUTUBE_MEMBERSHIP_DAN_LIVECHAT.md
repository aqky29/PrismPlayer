# Rencana Implementasi: Konten YouTube Membership & Live Chat Berautentikasi (Badge + Emoji Member)

Dokumen ini berisi rencana teknis lengkap untuk menambahkan dukungan **Autentikasi Akun YouTube (Login)**, **Tab & Pemutaran Konten Membership (`Members-only`)**, serta **Pengiriman Pesan Live Chat dengan Badge & Custom Emoji Member** pada aplikasi **FlufPlay**.

---

## 1. Ringkasan Kebutuhan & Cara Kerja di Server YouTube

| Fitur | Butuh Login Akun? | Butuh Langganan (Member Aktif)? | Cara Kerja di Server YouTube (`InnerTube`) |
| :--- | :---: | :---: | :--- |
| **Melihat daftar video Membership di Channel** | Tidak wajib (Publik) | Tidak wajib | Setiap channel dengan fitur Membership memiliki playlist otomatis dengan awalan `UUMO` (mengganti prefix `UC` dari Channel ID menjadi `UUMO`, misal `UCxxxx` $\rightarrow$ `UUMOxxxx`). |
| **Memutar (Streaming) & Mengunduh Video Membership** | **Wajib** | **Wajib (Tier sesuai)** | Endpoint `/youtubei/v1/player` dan `yt-dlp` memvalidasi `Cookie` + `SAPISIDHASH` di sisi server. Tanpa hak akses member aktif, server hanya mengembalikan status `UNPLAYABLE` (*"Join this channel to get access to members-only content"*). |
| **Mengirim Live Chat + Tampil Badge & Warna Hijau Member** | **Wajib** | **Wajib** | Saat pesan dikirim ke `/youtubei/v1/live_chat/send_message` dengan kredensial akun member, server YouTube otomatis menempelkan `authorBadges` (gambar badge sesuai durasi langganan), warna nama hijau member, dan mengizinkan penggunaan `emojiId` khusus channel tersebut. |

---

## 2. Arsitektur & Alur Sistem yang Akan Dibangun

```
+-----------------------------------------------------------------------+
|                         1. YouTubeAuthManager                         |
|  - Login via Android WebView (accounts.google.com -> youtube.com)     |
|  - Ekstrak CookieManager: SAPISID, __Secure-3PAPISID, SID, HSID, dll. |
|  - Generator Header: Authorization: SAPISIDHASH <ts>_<sha1>           |
|  - Generator Netscape cookies.txt (untuk fallback yt-dlp & Download)  |
+-----------------------------------+-----------------------------------+
                                    |
        +---------------------------+---------------------------+
        |                           |                           |
        v                           v                           v
+-----------------------+ +-----------------------+ +-----------------------+
|  2. VideoRepository   | | 3.Download / Stream   | |     4. LiveChat       |
|  & Channel Screen     | |    Fallback (yt-dlp)  | |   (ChatSession & UI)  |
|-----------------------| |-----------------------| |-----------------------|
| - Suntik Cookie &     | | - Jika NewPipe gagal  | | - Suntik Cookie &     |
|   SAPISIDHASH di      | |   buka video Member   | |   SAPISIDHASH ke      |
|   NewPipe Downloader  | |   (ContentNotAvail),  | |   get_live_chat       |
| - Deteksi Playlist    | |   fallback ke yt-dlp  | | - Ekstrak sendParams  |
|   UUMO... untuk Tab   | |   dengan --cookies    | |   & daftar Custom     |
|   "Membership"        | | - DownloadService     | |   Emoji Member        |
| - Badge "Khusus       | |   menyertakan         | | - Endpoint POST       |
|   Member" di kartu    | |   --cookies           | |   send_message        |
+-----------------------+ +-----------------------+ +-----------------------+
```

---

## 3. Rincian Perubahan per File

### Tahap 1: Modul Autentikasi & Manajemen Sesi (`YouTubeAuth.kt` — *File Baru*)
Lokasi: `flufplay/app/src/main/java/id/flufclip/player/YouTubeAuth.kt`

1. **Penyimpanan Kredensial (`SharedPreferences` `"youtube_auth"`):**
   * Menyimpan string `cookie` utuh dari domain `https://www.youtube.com`.
   * Menyimpan informasi profil singkat akun yang sedang login (Nama Akun, Email/Handle, Avatar URL) agar mudah dikenali di UI.
2. **Perhitungan Header `SAPISIDHASH`:**
   * Mengambil nilai cookie `SAPISID` atau `__Secure-3PAPISID`.
   * Membuat fungsi `buildAuthHeaders(origin: String = "https://www.youtube.com"): Map<String, String>`:
     * `Cookie`: `<raw_cookies>`
     * `Authorization`: `SAPISIDHASH <epoch_sec>_<SHA1(epoch_sec + " " + sapisid + " " + origin)>`
     * `Origin`: `https://www.youtube.com`
     * `X-Origin`: `https://www.youtube.com`
     * `X-Goog-AuthUser`: `0`
3. **Konversi Cookie ke Format Netscape (`cookies.txt`):**
   * Membuat fungsi `writeNetscapeCookiesFile(cacheDir: File): File?` untuk mengubah string cookie menjadi format standar Netscape HTTP Cookie File.
   * File ini digunakan oleh `youtubedl-android` (`yt-dlp`) saat mengekstrak stream video *Members-only* maupun saat mengunduh video/audio di `DownloadService`.
4. **Komponen UI Dialog / Layar Login (`YouTubeLoginDialog`):**
   * Menggunakan `AndroidView` dengan `WebView` (`JavaScript` & `DomStorage` aktif) yang mengarah ke `https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com`.
   * Memantau `onPageFinished`: ketika URL sudah kembali ke `youtube.com` dan `CookieManager.getInstance().getCookie("https://www.youtube.com")` memiliki `SAPISID` + `LOGIN_INFO`, otomatis menyimpan cookie dan menutup dialog login.
   * Menyediakan tombol **Logout** yang menghapus `SharedPreferences` dan membersihkan `CookieManager`.

---

### Tahap 2: Integrasi Tab "Membership" & Pemutaran Video Member (`VideoRepository.kt` & `DownloadService.kt`)
Lokasi:
* `flufplay/app/src/main/java/id/flufclip/player/VideoRepository.kt`
* `flufplay/app/src/main/java/id/flufclip/player/DownloadService.kt`

1. **Injeksi Header Autentikasi pada `NewPipe.init(Downloader)` (`VideoRepository.kt` baris 38–48):**
   * Jika `YouTubeAuth.isLoggedIn()` bernilai `true` dan URL request mengarah ke `youtube.com` / `google.com`, tambahkan header `Cookie` dan `Authorization: SAPISIDHASH ...` (kecuali jika request secara eksplisit meminta tanpa cookie).
2. **Deteksi & Pemuatan Tab "Membership" (`UUMO` Playlist) di Channel (`VideoRepository.kt` baris 111–151):**
   * Menambahkan konstanta tab `const val TAB_MEMBERSHIP = "membership"`.
   * Membuat helper `membershipPlaylistUrl(channelId: String): String?`:
     * Jika `channelId` diawali `"UC"` (misal `UCxxxxxxxx`), ubah menjadi `"https://www.youtube.com/playlist?list=UUMO" + channelId.removePrefix("UC")`.
   * Saat membuka channel (`VideoRepository.channel(url)`), cek secara paralel (asynchronous) apakah playlist `UUMO...` memiliki item video (`PlaylistInfo.getInfo(uumoUrl)`).
   * Jika ada, tambahkan tab **"Membership"** ke daftar tab di `ChannelPage` dan dukung paginasinya lewat `PlaylistInfo.getMoreItems(...)`.
3. **Fallback Ekstraksi Stream Video Membership dengan `yt-dlp` (`VideoRepository.kt` baris 158):**
   * Kendala umum `NewPipeExtractor`: Saat memanggil `StreamInfo.getInfo(url)`, `NewPipeExtractor` sering menggunakan klien `IOS` atau `ANDROID` tanpa dukungan cookie member sehingga melempar `ContentNotAvailableException` (*"Join this channel..."*).
   * Solusi: Jika `StreamInfo.getInfo(url)` gagal pada video khusus member dan user sudah login, aplikasi otomatis melakukan fallback menggunakan `YoutubeDL.getInstance().getInfo(request)` dengan opsi `--cookies <cookies.txt>` untuk mendapatkan URL stream HLS / DASH / Progressive dan memasukkannya ke `ExoPlayer`.
4. **Dukungan Unduhan Video Membership (`DownloadService.kt` baris 60–74):**
   * Jika `YouTubeAuth.isLoggedIn()` aktif, buat file sementara `cookies.txt` di folder `temp` unduhan dan tambahkan `addOption("--cookies", cookieFile.absolutePath)` pada `YoutubeDLRequest`.
   * Hapus file `cookies.txt` di blok `finally` bersama folder `temp`.

---

### Tahap 3: Pengiriman Live Chat, Badge Member, & Custom Emoji (`LiveChat.kt`)
Lokasi: `flufplay/app/src/main/java/id/flufclip/player/LiveChat.kt`

1. **Model Data Baru untuk Input & Emoji Member:**
   ```kotlin
   internal data class CustomChatEmoji(
       val emojiId: String,
       val shortcut: String,
       val imageUrl: String,
       val isCustomEmoji: Boolean = true
   )

   internal data class ChatInputConfig(
       val sendParams: String,
       val authorName: String,
       val authorPhotoUrl: String?,
       val badges: List<ChatBadge>,
       val customEmojis: List<CustomChatEmoji>
   )
   ```
2. **Menyertakan Cookie & `SAPISIDHASH` di `ChatSession` (`LiveChat.kt` baris 71–133):**
   * Pada `get(url)` dan `next()`, sertakan header `YouTubeAuth.buildAuthHeaders()` serta field `context.user` bila login aktif.
   * Di `ChatSession.start()` dan `parse()`, ekstrak `actionPanel.liveChatMessageInputRenderer`:
     * Ambil `sendParams` dari `sendButton.buttonRenderer.serviceEndpoint.sendLiveChatMessageEndpoint.params`.
     * Ambil profil & `authorBadges` milik user sendiri agar user bisa melihat preview badge member-nya di bar input chat.
     * Ambil daftar emoji khusus member dari `pickers[].liveChatEmojiPickerRenderer.categories[]` (memetakan `emojiId`, `shortcuts[0]`, dan `image.thumbnails`).
3. **Fungsi Kirim Pesan `ChatSession.sendMessage(segments)`:**
   * Mengirim request HTTP `POST` ke:
     `https://www.youtube.com/youtubei/v1/live_chat/send_message?key=$apiKey`
   * Header: `Cookie`, `Authorization: SAPISIDHASH ...`, `Origin: https://www.youtube.com`, `Content-Type: application/json`.
   * Payload JSON:
     ```json
     {
       "context": { ... },
       "params": "<sendParams>",
       "clientMessageId": "<UUID>",
       "richMessage": {
         "textSegments": [
           { "text": "Halo semua! " },
           { "emojiId": "UCxxxx/..." }
         ]
       }
     }
     ```
   * Mem-parsing `actions` dari respons JSON `send_message` menggunakan `parseAction(...)` yang sudah ada sehingga pesan yang baru dikirim **langsung muncul seketika di daftar chat lengkap dengan Badge Member & warna nama hijau**.
4. **UI Kolom Input Chat & Emoji Picker di `LiveChatPanel` (`LiveChat.kt` baris 952–1042):**
   * Menambahkan bar input di bagian bawah `LiveChatPanel` (hanya saat `state.live == true`):
     * **Jika belum login:** Menampilkan tombol *"Login untuk ikut Live Chat"* yang membuka `YouTubeLoginDialog`.
     * **Jika sudah login & `sendParams` tersedia:**
       * Menampilkan avatar + badge member user di sisi kiri.
       * Tombol **Emoji Member** untuk membuka panel grid stiker/emoji custom channel (`CustomChatEmoji`).
       * `TextField` input pesan (mendukung penyisipan shortcut `:emoji:` atau klik langsung dari grid emoji).
       * Tombol **Kirim** dengan indikator loading saat proses pengiriman.
     * **Jika chat dibatasi (misal *Members-only chat* dan akun belum member di channel tersebut):** Menampilkan pesan status dari `actionPanel` (misal *"Mode khusus Member"*).

---

### Tahap 4: Integrasi UI di `MainActivity.kt`
Lokasi: `flufplay/app/src/main/java/id/flufclip/player/MainActivity.kt`

1. **Tombol Akun / Login di Top Bar atau Pengaturan:**
   * Menambahkan ikon/avatar akun di pojok kanan atas bar utama (`MainActivity.kt`):
     * Jika belum login: Ikon akun untuk membuka `YouTubeLoginDialog`.
     * Jika sudah login: Menampilkan foto profil akun beserta menu status akun & opsi *Logout*.
2. **Render Tab "Membership" di `ChannelHeader` (`MainActivity.kt` baris 1658–1736):**
   * Menambahkan `"membership" to "Membership"` ke `tabNames`.
   * Menampilkan indikator ikon bintang (`⭐`) pada tab Membership dan memilih sumber data dari playlist `UUMO...` saat tab tersebut diklik.

---

## 4. Urutan Eksekusi Pengerjaan (Checklist)

- [x] **Langkah 1:** Buat `YouTubeAuth.kt` (WebView Login, penyimpanan Cookie, generator `SAPISIDHASH`, dan generator `cookies.txt`).
- [x] **Langkah 2:** Perbarui `VideoRepository.kt` & `DownloadService.kt` (injeksi header autentikasi, deteksi playlist `UUMO` untuk tab Membership, dan fallback `yt-dlp` untuk stream video khusus member).
- [x] **Langkah 3:** Perbarui `LiveChat.kt` (autentikasi `ChatSession`, ekstraksi `sendParams` + custom emoji member, endpoint `send_message`, serta UI Input Bar & Emoji Picker di `LiveChatPanel`).
- [x] **Langkah 4:** Perbarui `MainActivity.kt` (tombol Login/Akun di Header, tab `"Membership"` di halaman Channel, dan penanganan klik tab Membership).
- [x] **Langkah 5:** Build & verifikasi (`./gradlew assembleDebug`).
