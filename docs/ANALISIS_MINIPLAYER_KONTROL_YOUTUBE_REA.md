# Mini player & kontrol player — YouTube 21.43.523 vs FlufPlay

APK `youtube-21-43-523.apk`, SHA-256 `4410cf3e…1b84c`. Sumber: REA/JADX 0.7.1 (decompile), `dexdump` (bytecode), `aapt2` (resource). Analisis statis: aplikasi YouTube tidak dijalankan. Nama kelas terobfuscasi (`qxo`, `qxj`, …), jadi arti beberapa field disimpulkan dari cara pemakaiannya. Pemakaian fitur dikunci feature flag server (`aktm.m(…)`), sehingga **tidak bisa dipastikan perangkat mana yang mendapat versi mana**.

## 1. Mini player modern (floating) — kelas `qxo`

### Fakta dari kode
| Hal | Nilai di YouTube | Bukti |
|---|---|---|
| Animasi snap/pindah | `ValueAnimator` 0→1, **500 ms**, `OvershootInterpolator(1.0)` (sedikit memantul), interpolasi linear antar `Rect` | `qxo.<init>`, `qxo.y` — ev_7554580c…, dexdump `y` |
| Rasio | 1.777 (16:9); video vertikal memakai rasio dibalik dengan tinggi minimum | `qxo.<init>`, `qxo.w` — ev_517dfa0d… |
| Posisi | Menempel di **4 sudut**: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT` (+`UNKNOWN`). Default kanan bawah | enum `qxn`, `qxo.w`, `qxo.f` — ev_2e070ad9… |
| Jarak dari tepi | `miniplayer_inset_offset` **8 dp**, **12 dp** bila lebar ≥ 840 dp; juga menghindari status/nav bar dan bottom bar | resource + `qxo.w` |
| Lebar default | `clamp(min(W,H)/2 − inset, 192 dp, max)` | dexdump `ak` |
| Lebar "besar" | `min(min(W,H) − 2·inset, max)` | dexdump `j`, `P` |
| Lebar maksimum | `miniplayer_max_size` **640 dp**, **720 dp** bila lebar ≥ 1024 dp, dan tidak melebihi layar dikurangi inset | resource + dexdump `al` |
| Lebar minimum | **192 dp** | `qxo.<init>` (`R`, `s`) |
| Resize | **Cubit dua jari**: lebar dikali skala tiap frame, berpusat di titik fokus jari (`jph.L(focusX, focusY, rect, scale)`) | `qxj.m` — ev_047d9963… |
| Batas saat cubit | Melewati min/maks diberi **efek karet**: `scale^0.25` (masih bisa sedikit lewat, terasa berat) | `qxj.m` |
| Saat jari dilepas | `J()` mengembalikan lebar ke rentang [192 dp, maks], lalu `M()` menganimasikan ke sudut (500 ms overshoot) | dexdump `qxj.g`, `qxo.J`, `qxo.M` |
| Mode besar | Ketika lebar ≥ **80 %** dari ukuran besar, mode "besar" aktif (`qwh.e(true)`) — tampilan kontrol ikut berubah | `qxj.m` |
| Tombol ubah ukuran | `P()`: berpindah antara ukuran default ↔ besar, ingat sudut sebelumnya | dexdump `P` |
| Stash ke tepi | Bila pusat mini player didorong keluar layar kiri/kanan, ia tersembunyi dan muncul **handle** 108 × 24 dp (32 dp pada layar lebar) yang ikut keluar sesuai jarak; ketuk untuk memunculkan lagi | `qxo.Y`, resource `miniplayer_handle_*`, string "Stash/Unstash miniplayer" — ev_607efa53… |
| Drag | Bebas vertikal, dibatasi dari atas (inset+status bar) sampai di atas bottom bar; dilepas → snap ke sudut terdekat | `qxo.m`, `qxo.aa` — ev_0aa48a8f… |

### Kontrol di dalam mini player (`modern_miniplayer_controls_overlay`)
Tutup (kanan atas), maximize (kiri atas), **play/pause 48 dp di tengah dengan progress melingkar** saat buffering, **mundur 10 dtk** (kiri) & **maju 10 dtk** (kanan) 48 dp, badge iklan kiri bawah, time bar 2 dp (`floaty_inline_time_bar_size`).

### Mini player klasik (bar) masih ada
`floaty_bar_controls`: bar setinggi **56 dp** (48 dp landscape) berisi thumbnail video, judul, nama channel, play/pause, tutup, dan progress 2 dp. Mana yang aktif (bar atau floating) ditentukan flag server; **tidak dapat dipastikan dari APK**.

### Contoh hasil rumus di berbagai layar
| Layar (dp) | Default YT | Besar YT | FlufPlay sekarang (min / default / maks) |
|---|---|---|---|
| HP 411 × 891 | ≈ 198 | 395 | 160 / 220 / 360 |
| Tablet 800 × 1280 | 392 | 640 | 160 / 220 / 360 |
| Tablet landscape 1280 × 800 | 388 | 720 | 160 / 220 / 360 |

## 2. Kontrol player utama

| Elemen | YouTube | Bukti |
|---|---|---|
| Tombol tengah | Play/pause/replay **56 dp** (area sentuh 80 dp); prev/next/transport **40 dp** (sentuh 48 dp); semua dengan **latar lingkaran** `player_button_circle_background` | layout `youtube_controls_button_group_layout` |
| Jarak antar tombol tengah | 24 dp; mode aksesibilitas 16 dp, 32 dp di sw600dp, 40 dp fullscreen | resource |
| Scrim atas | Gradien **180 dp** (`controls_overlay_top_bar_gradient`), bukan warna rata | resource |
| Tombol atas | Tinggi 48 dp, ikon 40 dp, padding 8 dp | `inline_top_ui_controls_*` |
| Time bar | Garis 2 dp; scrubber 12 dp, **20 dp saat diseret**; area sentuh playhead 20 dp; mulai scrub setelah geser 5–10 dp; jarak antar chapter 2 dp (3 dp saat scrub) | `inline_time_bar_*`, `horizontal_touch_offset_to_start_scrubbing` |
| Fullscreen | Margin kiri/kanan 16 dp, tambahan bawah 30 dp, tombol fullscreen 60 × 60 dp | `fullscreen_*`, `time_bar_margin_landscape` |
| Preview saat scrub | Margin 8 dp horizontal, 12 dp vertikal (36 dp versi panjang) | `inline_scrubbed_preview_*` |
| Fine scrubbing | "Pull up for precise seeking": tarik ke atas saat scrub → film strip (item 72 dp) | string + `fine_scrubbing_*` |
| Double tap | Durasi bisa diatur 5/10/15/20/30/60 dtk (default 10). Efek: lingkaran yang membesar dari titik ketukan, dipotong bentuk busur di sisi itu (`CircularClipTapBloomView`) | string `pref_double_tap_skip_*`, kelas `quickseek.overlay` |
| Tahan | "Speedmaster" **2x**; ada juga varian mundur 2x dan kunci 2x (di Shorts: tarik ke bawah) | string `speedmaster_*` |
| Chapter & heatmap | Time bar memisah chapter; `HeatMarkerView` ("paling sering diputar") | kelas & resource |
| Waktu sembunyi otomatis | **Belum ditemukan** di kode yang diperiksa | — |

## 3. Perbedaan dengan FlufPlay saat ini

### Mini player
1. **Ukuran tidak mengikuti layar.** FlufPlay: lebar tetap 160–360 dp (default 220 dp). YouTube: lebar dihitung dari sisi layar terpendek (default ≈ setengahnya) dan maksimum 640–720 dp, jadi di tablet mini player jauh lebih besar.
2. **Cubit kurang halus.** FlufPlay langsung dibatasi keras dan tidak berpusat di jari. YouTube berpusat di titik fokus, ada efek karet di batas, dan saat dilepas memantul halus (overshoot 500 ms) ke ukuran yang sah.
3. **Tidak bisa dipindah.** FlufPlay hanya di kanan bawah. YouTube bisa diseret dan menempel di 4 sudut dengan animasi overshoot.
4. **Tidak ada stash ke tepi** dengan handle.
5. **Tombol mini player kurang.** FlufPlay tidak punya mundur/maju 10 dtk, progress bar, atau indikator buffering melingkar, dan ikon ⤢ ⤧ masih berupa teks.
6. **Tombol ubah ukuran** FlufPlay min ↔ maks; YouTube default ↔ besar.
7. **Inset** FlufPlay 8 dp kanan dan 64 dp bawah, sama di semua ukuran layar; YouTube 8/12 dp dan mengikuti system bar serta bottom bar.

### Kontrol player
1. Ukuran tombol tengah: FlufPlay 60 / 44 dp → YouTube 56 / 40 dp (area sentuh 80 / 48 dp).
2. Jarak tombol tengah: FlufPlay 36 / 64 dp → YouTube 24 dp (dengan area sentuh lebih besar).
3. Scrim: FlufPlay hitam rata 45 % → YouTube gradien atas 180 dp.
4. Time bar: FlufPlay scrubber 12 → 18 dp → YouTube 12 → 20 dp, dan scrub baru mulai setelah geser beberapa dp.
5. Double tap: FlufPlay memakai setengah lingkaran putih statis → YouTube memakai lingkaran yang membesar dari titik ketuk. Durasi seek belum bisa diatur.
6. Belum ada fine scrubbing, chapter di time bar, heatmap, atau mundur 2x.
7. Fullscreen: margin FlufPlay 16 dp dan bawah 40 dp, sudah mendekati YouTube (16 dp + 30 dp).

## Yang belum diketahui
- Feature flag mana yang aktif di perangkat tertentu (bar klasik atau floating; mode besar).
- Waktu sembunyi otomatis kontrol, durasi fade, dan warna persis latar lingkaran tombol (atribut tema).
- Arti pasti beberapa field terobfuscasi (`g()` diduga tinggi bottom bar, `X.h` diduga layar lebar).

## 4. Koreksi (pemeriksaan ulang 2026-11-01): kontrol & gesture mini player modern

Sumber: `aapt dump xmltree` layout `modern_miniplayer_controls_overlay` (`res/7l9.xml`), `dexdump`, dan JADX 1.5.6 per kelas (`oix`, `qxj`, `afsz`, `afti`, `aftb`, `afta`, `qxa`, `qxd`, `qxg`, `rog`, `qyv`). Statis, tidak dijalankan.

### Kontrol (`oix`, id `modern_miniplayer_*`)
| Elemen | Fakta | Bukti |
|---|---|---|
| Latar tombol | Tidak ada lingkaran gelap. Style `YouTubePlayerButton` → `touch_feedback_overlay` (ripple oval saja) | style 0x7f150e6a, `res/v5i.xml` |
| Scrim | `scrim_overlay` di layout `gone`; tidak ada kode di `oix` yang menampilkannya | layout + pencarian id 0x7f0b12cc (hanya di `olx`, overlay player utama) |
| Shadow ikon | alpha 166, offset y 1 dp, blur 4 dp | `shadow_icon_*`, `qxa.b`, `oix.e/i` |
| Tutup | kanan atas, padding 8 dp, ikon registry `bpfw.oW` tint putih | `oix.p` |
| Expand | ikon `yt_outline_maximize_cairo_black_24`, tapi `oix.x()` selalu `afro.s(q(), false)` → **tidak tampil** | `oix.q`, `oix.x` |
| Tombol play/pause | **Kiri atas** (layout memusatkannya, tapi `oix.n()` menghapus constraint end/bottom → tersisa top/start). 48 dp, padding 12 dp (ikon 24 dp). Ikon pause/play/replay (`accessibility_pause/play/replay`). Cocok dengan pengamatan pengguna di aplikasi | `oix.n`, `qxa.c` |
| Cincin progres | Kiri atas, margin kiri/atas 6 dp (konsentris dengan tombol 48 dp), 36 dp, tebal 2 dp, putih di atas `yt_grey3`, ikon jadi "Stop": **countdown autoplay** (`floatybar_progress_circle_autonav`, `autonav_a11y`), bukan buffering | `qxd.d`, `res/VmA.xml` |
| ±10 dtk | Ada di layout dengan ikon `yt_fill_skip_*_10_white_24`, tapi kode hanya menyembunyikan (`qxg.c`); tidak ditemukan pemanggil yang menampilkan | `qxg`, `qxb` case 3 |
| Auto-hide | Tidak ditemukan untuk tombol; animator 3 dtk di `oix` hanya mengembalikan subtitle bar | `oix.a`, `oiv` |
| Subtitle bar | kiri bawah, margin 8 dp, teks 12 sp + badge iklan | layout, `oix.u/r`, `qxc.m` |

### Gesture (`qxj` + detektor)
| Gesture | Perilaku | Bukti |
|---|---|---|
| Tap sekali | Ditunda `doubleTapTimeout`; bila tidak ada tap kedua → `rog.h(true)` = buka player penuh | `afsz.d/b`, `qxj.lV/e`, `rsx.q` |
| Tap dua kali | `qxo.P()`: ganti ukuran default ↔ besar | `qxj.lT` |
| Tap dekat tombol | Sentuhan ≤ 4 dp di luar tombol tengah/tutup diteruskan ke tombol | `qxj.s/p` |
| Tap pada kontrol | Detektor tap dimatikan bila jari di atas kontrol | `qxj.p`, `qxj.i` |
| Seret | Mulai setelah touch slop; mini player ikut jari; dilepas → `qxo.S()` snap ke sudut | `aftb.d` |
| Fling | ≥ 1500 px/s dan jarak ≥ (5·slop)²; arah menentukan sudut (sektor ±12,5°) atau stash ke tepi | `afta.onFling` |
| Cubit | lihat bagian 1; mulai cubit menahan overlay | `qxj.n/m/g` |
| Long press | dimatikan (`setIsLongpressEnabled(false)`) | `aftb.<init>` |
