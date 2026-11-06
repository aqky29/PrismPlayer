# Analisis Membership DEANKT dan Feed Kategori — FlufPlay

Dokumen ini merangkum bukti sebelum perubahan perilaku. Sumber: kode FlufPlay saat ini, probe InnerTube dengan cookie akun perangkat (`Iqbalhs`), dan eksekusi NewPipeExtractor v0.26.3 / yt-dlp yang dipakai aplikasi. Nilai cookie tidak dicetak.

## 1. Membership DEANKT

### Status akun

Channel yang dimaksud: **DEANKT** (`UC0KlWYEQIlR2KHS_dHfKJhA`). Playlist membership `UUMO0KlWYEQIlR2KHS_dHfKJhA` berisi **99 video** saat diperiksa.

Probe `/youtubei/v1/player` dengan cookie akun:

| Video | Status | Keterangan |
| --- | --- | --- |
| `MY4CaoaRoSY` — KITA BER 4 BERBAGI OKSIGEN… | `OK`, `streamingData` ada | Replay level membership biasa; akun punya akses |
| `Zi3ZMs9ts7c` — KITA PILIH KAKAK YANG MANA… | `OK`, `streamingData` ada | Replay level biasa |
| `sAvYEdcGclA` — SCRIM DOTA 2… | `UNPLAYABLE` | “tersedia untuk pelanggan pada level: NEXT LVL #WEAREMEMBERSHIP” |

Jadi membership akun aktif. Kegagalan di aplikasi bukan karena akun tidak menjadi member.

### Akar masalah pemutaran

1. **Cookie akun tidak pernah dipakai NewPipe.** `VideoRepository.kt:56-61` hanya menambahkan header auth jika request belum punya `Cookie`. NewPipe selalu mengirim `Cookie: SOCS=…`, sehingga header cookie akun dilewati. Filter host juga hanya `youtube.com`/`googlevideo.com`; `youtubei.googleapis.com` (tempat preflight `reel_item_watch`) tidak cocok. Hasil probe: request preflight tetap anonim → `PaidContentException`.
2. **Mengirim cookie WEB ke client Android NewPipe tetap tidak cukup.** Probe dengan cookie + `Authorization` + `Origin` pada `reel_item_watch` masih menghasilkan `MEMBER-GATE`. Client Android memakai identitas auth native, bukan cookie WEB.
3. **Fallback yt-dlp bawaan gagal untuk replay ini.** Biner yt-dlp 2025.11.12 yang diekstrak aplikasi dijalankan dengan file cookie yang sama: `--dump-json` → `ERROR: Requested format is not available`. Setelah binary diganti 2026.10.26 + EJS/QuickJS dengan client `web_safari`, format berhasil didapat dan `n` diselesaikan.
4. Karena versi lama mengembalikan `null`, `VideoRepository.kt` melempar error asli NewPipe. Itulah pesan “This video is only available for members…” yang terlihat di UI.

### Jalur player yang tersedia

NewPipe tetap tidak bisa memutar replay ini karena client Android-nya memakai identitas auth native, bukan cookie WEB. InnerTube WEB/MWEB dengan cookie memberi status `OK`, tetapi format direct-nya memakai `n`; `YoutubeJavaScriptPlayerManager` v0.26.3/v0.26.5 gagal men-deobfuscate player JS saat ini.

Jalur yang berhasil: **yt-dlp terbaru (2026.10.26) dengan client `web_safari` + EJS/QuickJS**. Dengan cookie yang sama, yt-dlp menyelesaikan `n` dan menghasilkan 11 format: storyboard, 6 playlist HLS (`m3u8_native`, 144p–1080p), dan 1 format direct `https` itag 18 (360p). Playlist HLS yt-dlp dapat diputar (segmen 200) setelah data source ExoPlayer memakai UA Safari + Referer + cookie. Client `visionos` sendirian tidak mengembalikan format; `visionos,web_safari` sama dengan web_safari.

Kesimpulan: aplikasi membundel binary yt-dlp terbaru di `assets/yt-dlp-latest`, menyalinnya setelah `YoutubeDL.init`, memakai `web_safari`, dan menambahkan varian `m3u8_native` sebagai pilihan kualitas HLS (240p–1080p). Ini juga memberi kualitas lebih tinggi daripada itag 18 360p.

## 2. Feed kategori

### Yang dilakukan aplikasi sekarang

- Chip hardcoded: `MainActivity.kt:1321` — `Semua, Musik, Game, Berita, Film, Olahraga, Live`.
- Klik chip non-Live memanggil `VideoRepository.searchPage(value)` (`MainActivity.kt:646`). Artinya “Musik”/“Game” adalah pencarian kata, bukan feed kategori akun.
- `HomeFeed.start()` hanya mengirim `browseId=FEwhat_to_watch` tanpa `params` (`HomeFeed.kt:56`) dan parser tidak membaca chip cloud (`HomeFeed.kt:70-109`).

### Yang dikembalikan server dengan cookie akun

`FEwhat_to_watch` mengembalikan **11 chip dinamis**:

`Semua` (selected), `Game`, `Live`, `Musik`, `Mix`, `Podcast`, `ASMR`, `Game petualangan aksi`, `Baru diupload`, `Ditonton`, `Baru untuk Anda`.

Setiap chip membawa `navigationEndpoint.continuationCommand.token` (atau `browseEndpoint.params`). Contoh hasil:

| Chip | Jumlah item | Contoh isi |
| --- | ---: | --- |
| Semua | 18 | `bobo - DAY 3 PT.1`, ASMR Seowon UNIS, Qwen 4 |
| Musik | 17 | Antent – homesick, About You – The 1975, a coldcore ambient playlist |
| Game | 89 | Revisiting Valorant 2 Years Later, DEANKT live, Roblox Break In |

Hasil yang ditampilkan aplikasi saat chip “Musik”/“Game” ditekan: “Lagu Santai Viral Tiktok…”, “SQUID GAME RUN…”, “HUMAN TETRIS…”. Itu hasil pencarian kata, bukan feed personal akun.

### Akar masalah

Aplikasi tidak pernah mengirim `continuation`/`params` milik chip ke `browse`, sehingga server tidak pernah diminta menyusun feed kategori. Login hanya memengaruhi feed “Semua”; chip tetap memakai pencarian publik yang sama untuk semua akun.

### Perbaikan

- Parse chip cloud dari respons `FEwhat_to_watch` (judul, `continuationCommand.token`, `browseEndpoint.params`, status selected).
- Render chip dari server; fallback label lama hanya saat belum login.
- Saat chip diklik, kirim `continuation` (atau `browseId=FEwhat_to_watch` + `params`) ke `browse` dengan cookie yang sama.
- Parse `onResponseReceivedActions[0].reloadContinuationItemsCommand.continuationItems` / `appendContinuationItemsAction`, termasuk item di `richSectionRenderer.content.richShelfRenderer.contents`.
- Pagination kategori memakai token lanjutan dari respons chip.

## 3. Bukti probe (ringkas)

- `HomeFeed.start()` = 18 lockup + 11 chip; `Musik` = 17 lockup; `Game` = 89 lockup.
- `player` WEB `MY4CaoaRoSY` = `OK`; `player` MWEB = `OK`, 35 format adaptive ber-URL.
- `sAvYEdcGclA` = `UNPLAYABLE`, alasan level `NEXT LVL #WEAREMEMBERSHIP`.
- NewPipe v0.26.3 `StreamInfo.getInfo(MY4CaoaRoSY)` = `PaidContentException`; yt-dlp 2025.11.12 `--dump-json` = `Requested format is not available`.
- Aplikasi menampilkan `Video gagal dibuka: This video is only available for members of the channel of this video` saat replay DEANKT level biasa dibuka.
