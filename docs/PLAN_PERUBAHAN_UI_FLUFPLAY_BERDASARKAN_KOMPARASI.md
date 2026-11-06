# Rencana Perubahan UI/UX FlufPlay (Berdasarkan Hasil Analisis Virtual Device)

**Tanggal:** 5 November 2026  
**Referensi Analisis:** [ANALISIS_PERBANDINGAN_UI_FLUFPLAY_VS_YOUTUBE_AUTOMATION.md](ANALISIS_PERBANDINGAN_UI_FLUFPLAY_VS_YOUTUBE_AUTOMATION.md)  
**Tujuan:** Menutup kesenjangan estetika dan kenyamanan interaksi (*ergonomics*) antara FlufPlay dan YouTube native app tanpa mengorbankan performa ringan dan keunggulan fitur unik FlufPlay (seperti tombol *Ask AI*, *Switch Account* cepat, dan *Mute* di Shorts).

---

## Ringkasan Prioritas

```
FASE 1: Quick Wins & Layout Feed (Home & Subscriptions)
  ├── 1.1 Category Chips Bar di Home Feed
  ├── 1.2 Channel Avatar Row di Tab Subscriptions
  └── 1.3 Format Feed Edge-to-Edge (Visual Thumbnail Lebih Luas)

FASE 2: Pemutakhiran Layar Watch / Player
  ├── 2.1 Segmented Pill untuk Like/Dislike di Watch Action Bar
  ├── 2.2 Penataan Ikon Aksi (Like/Dislike, Share, Ask, Save, Download)
  └── 2.3 Perhalus Transisi Sheet Komentar & Rekomendasi

FASE 3: Polish Shorts & You/Profile Screen
  ├── 3.1 Ergonomi Rail Kanan Shorts & Marquee Text Audio Pill
  ├── 3.2 Indikator Storage / Kapasitas Offline pada Section Downloads
  └── 3.3 Indikator Badge Akun Aktif di Bottom Bar Avatar
```

---

## Rincian Perubahan per Fase

### FASE 1: Layout Feed & Eksplorasi (Home & Subscriptions)

#### 1.1 Penambahan Category Chips Bar di Home Feed
- **Masalah:** Di YouTube, pengguna dapat memfilter feed beranda secara instan (*Home, Subs, Music, Live, Gaming*). FlufPlay baru memiliki filter chips di tab Subscriptions.
- **Tindakan:**
  - Tambahkan `CategoryChipsRow` tepat di bawah Top App Bar pada `flufplay/app/src/main/java/id/flufclip/player/HomeFeed.kt`.
  - Hubungkan dengan endpoint InnerTube kategori yang sudah ada (`browseId` / `continuation` kategori).
  - Chip pertama *"All"* aktif secara default.

#### 1.2 Channel Story Avatar Carousel di Tab Subscriptions
- **Masalah:** Di YouTube native, bagian atas layar Subscriptions menampilkan deretan lingkaran foto channel yang di-subscribe pengguna untuk akses cepat ke profil masing-masing kreator.
- **Tindakan:**
  - Tambahkan `LazyRow` berisikan channel avatar melingkar (diameter ~56dp) di bagian atas `AccountLibraryUi.kt`.
  - Berikan border indikator halus atau dot jika channel sedang mengadakan Live streaming atau baru mengunggah video.
  - Sediakan chip terakhir *"All"* untuk melihat seluruh daftar langganan.

#### 1.3 Penyesuaian Margin Feed Video (Edge-to-Edge Thumbnail)
- **Masalah:** Card feed FlufPlay saat ini dibatasi margin horizontal 42px di kiri dan kanan (`bounds: [42, ...] [1038, ...]`), menyebabkan thumbnail video menyempit (996px).
- **Tindakan:**
  - Ubah kontainer thumbnail menjadi lebar penuh (**1080px**) edge-to-edge seperti YouTube native.
  - Terapkan padding horizontal (12-16dp) khusus untuk blok informasi di bawahnya (avatar channel, 2-line title, metadata views/waktu, dan 3-dots menu).
  - Memberikan visual yang lebih lega, sinematik, dan fokus pada konten.

---

### FASE 2: Layar Pemutaran Video (Watch Screen)

#### 2.1 Segmented Pill Kapsul [Like | Dislike]
- **Masalah:** Saat ini tombol Like dan Dislike di `WatchActionSurface.kt` terpisah menjadi dua tombol independen dengan jarak kosong di antaranya.
- **Tindakan:**
  - Satukan Like dan Dislike ke dalam satu komponen kapsul horizontal (`SegmentedPill`).
  - Bagian kiri: Ikon jempol + label counter (misal: "4 K").
  - Garis pemisah vertikal tipis (lebar 1dp, opasitas 20%).
  - Bagian kanan: Ikon jempol ke bawah (Dislike).
  - Menghemat ruang horizontal di action bar sehingga tombol penting lainnya tidak terpotong.

#### 2.2 Penataan Urutan Action Bar & Penambahan Chip Save
- **Masalah:** Fitur Save playlist yang baru diperbaiki di M04 belum muncul berdampingan di action bar utama layar Watch.
- **Tindakan:**
  - Urutkan baris aksi: `[Like | Dislike]` → `Share` → `Save` (Playlist) → `Download` → `Ask` (AI).
  - Pastikan seluruh tombol memiliki feedback ripple dan status aktif (*toggled*) yang konsisten dengan `EngagementRepository`.

#### 2.3 Interaksi Preview Komentar
- **Masalah:** Card preview komentar sudah bagus secara visual, tetapi perlu dipastikan transisi saat card diketuk membuka modal sheet komentar berjalan mulus (60/120fps) tanpa stutter.
- **Tindakan:**
  - Optimalkan lazy loading komentar pada modal bottom sheet agar tidak memicu re-render pemutar video di belakangnya.

---

### FASE 3: Shorts & Tab Profile / You

#### 3.1 Ergonomi Tombol Rail Kanan Shorts
- **Masalah:** Penempatan tombol Like, Comment, Share di sisi kanan bawah kadang terlalu mepet dengan navigation bar atau sulit dijangkau satu tangan.
- **Tindakan:**
  - Atur jarak vertikal antar icon di `ShortsScreen.kt` menjadi standar 16dp.
  - Tambahkan efek marquee (teks berjalan) pada audio pill di kiri bawah jika judul lagu/soundtrack panjang melebihi lebar 200dp.

#### 3.2 Indikator Storage pada Menu Downloads
- **Masalah:** Menu Downloads di tab You hanya menampilkan teks statis tanpa informasi kapasitas.
- **Tindakan:**
  - Tambahkan subtext kapasitas: misalnya `12 video · 2.4 GB digunakan` dan indikator progress bar kapasitas penyimpanan perangkat.

#### 3.3 Pertahankan Keunggulan Unik FlufPlay
- Fitur berikut di FlufPlay terbukti lebih unggul dan praktis dibanding YouTube native saat pengujian, sehingga **wajib dipertahankan**:
  - Tombol **Mute cepat** di top bar Shorts.
  - Pill **Switch Account** langsung di header tab You (tanpa masuk submenu tersembunyi).
  - Tombol **Ask AI** di pemutar video.
  - Tab langsung **Subscriptions** di Bottom Bar (menggantikan tombol Create yang jarang dipakai oleh penonton umum).

---

## Rencana Eksekusi Bertahap

| Tahap | Modul Target | Estimasi Perubahan Kode |
| :--- | :--- | :--- |
| **Langkah 1** | Home Feed (`HomeFeed.kt`, `HomeRecommendations.kt`) | Tambah `CategoryChipsRow` & penyesuaian padding edge-to-edge thumbnail |
| **Langkah 2** | Subscriptions (`AccountLibraryUi.kt`) | Tambah `SubscriptionsChannelAvatarRow` horizontal di atas feed |
| **Langkah 3** | Watch Action Bar (`WatchActionSurface.kt`) | Terapkan segmented pill `[Like | Dislike]` & integrasikan chip `Save` |
| **Langkah 4** | Verifikasi & Pengujian | Build APK debug, run unit tests, dan verifikasi layout dump otomatis via Pixel 10 emulator |
