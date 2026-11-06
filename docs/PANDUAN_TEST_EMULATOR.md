# Panduan uji FlufPlay di emulator Pixel 10

**Status:** 5 November 2026 · **Lingkup:** mesin pengembang `iqbalh` (Ubuntu 26.04, AMD Cezanne, 14 GB RAM)
**Terkait:** [Testing.md](Testing.md) untuk hasil uji manual per build, [README FlufPlay](../README.md) untuk gambaran fitur.

Dokumen ini menjelaskan cara menyalakan emulator Android 16 profil **Pixel 10** dan memasang build FlufPlay untuk pengujian cepat tanpa perangkat fisik. Semua dijalankan lewat satu script: [`tools/pixel10`](../tools/pixel10).

## Ringkasan cepat

```bash
cd ~/Documents/flufclip
pixel10 app                 # nyalakan emulator (±8 dtk dari snapshot) + buka FlufPlay
pixel10 shot bukti.png      # screenshot layar saat ini
pixel10 stop                # matikan; snapshot disimpan agar start berikutnya cepat
```

## 1. Perangkat maya yang dipakai

| Komponen | Nilai |
| --- | --- |
| AVD | `Pixel_10` (profil resmi `pixel_10`) |
| Android | 16 (API 36), tag `google_apis_playstore` |
| Arsitektur | x86_64, dengan Play Store dan GMS |
| Layar | 1080 × 2424, 420 dpi |
| RAM guest | 3 GB, heap 512 MB, 4 vCPU |
| Akselerasi | KVM (`/dev/kvm`, ACL untuk user `iqbal` — tanpa sudo) |
| Render | `-gpu host` → AMD Radeon (Mesa radeonsi/RADV) via Xwayland |

Emulator ini **bukan pengganti uji perangkat fisik**; lihat bagian 9.

## 2. Pemasangan ulang (mesin baru / AVD hilang)

SDK yang dipakai: `~/Android/Sdk` (`ANDROID_HOME` sudah di-set di shell).

```bash
SDK="$HOME/Android/Sdk"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDM="$SDK/cmdline-tools/latest/bin/avdmanager"

# 1) paket emulator + image Android 16 Play Store
yes | "$SDKM" --sdk_root="$SDK" emulator \
  "system-images;android-36;google_apis_playstore;x86_64"

# 2) AVD dengan profil Pixel 10
echo no | "$AVDM" create avd -n Pixel_10 -d pixel_10 \
  -k "system-images;android-36;google_apis_playstore;x86_64"
```

Setelah AVD dibuat, samakan setelan berikut di `~/.android/avd/Pixel_10.avd/config.ini`
(sudah diterapkan di mesin ini):

```ini
hw.ramSize=3072
vm.heapSize=512
hw.keyboard=yes
hw.gpu.enabled=yes
PlayStore.enabled=yes
showDeviceFrame=no
```

Cek akselerasi:

```bash
ls -l /dev/kvm          # harus ada, group kvm, ACL user
sudo usermod -aG kvm "$USER"   # hanya jika user belum punya akses; perlu login ulang
```

## 3. Memasang script

Script sumber ada di repo: [`tools/pixel10`](../tools/pixel10). Pasang ke `PATH`:

```bash
ln -sf ~/Documents/flufclip/tools/pixel10 ~/.local/bin/pixel10
# atau salin: cp tools/pixel10 ~/.local/bin/pixel10
```

`~/.local/bin` sudah ada di `PATH` pada mesin ini, jadi cukup ketik `pixel10`.
Kalau script dijalankan dari terminal desktop biasa, `DISPLAY`/`XAUTHORITY` sudah benar;
script juga mendeteksi cookie Xwayland sendiri bila dipanggil dari shell non-desktop.

### Perintah

| Perintah | Fungsi | Waktu terukur |
| --- | --- | --- |
| `pixel10` | nyalakan (quickboot) + tunggu siap + fokus jendela | **~8 dtk** |
| `pixel10 --cold` | boot bersih, abaikan snapshot | ~18 dtk |
| `pixel10 app` | nyalakan + buka FlufPlay | ~1 dtk bila sudah boot |
| `pixel10 app <pkg/.Activity>` | buka komponen lain | — |
| `pixel10 stop` | matikan + simpan snapshot | ~5 dtk |
| `pixel10 restart` | stop lalu start | ~20 dtk |
| `pixel10 status` | boot, Android, layar, CPU, GPU | — |
| `pixel10 logs [-f]` | log emulator (`-f` streaming) | — |
| `pixel10 shot [file]` | screenshot PNG (default `~/Pictures/pixel10/`) | — |
| `pixel10 shell [cmd]` | `adb shell` | — |

Opsi: `--cold`, `--wipe` (hapus data AVD), `--headless` (tanpa jendela), `--gpu host|swiftshader`, `--timeout <detik>`.

Variabel lingkungan: `AVD_NAME`, `PIXEL10_GPU`, `PIXEL10_HEADLESS`, `PIXEL10_TIMEOUT`, `PIXEL10_LOG`, `PIXEL10_SHOTS`.

## 4. Alur uji harian

```bash
cd ~/Documents/flufclip/flufplay

# 1) build APK debug (atau dari Android Studio)
./gradlew :app:assembleDebug

# 2) nyalakan emulator + buka FlufPlay
pixel10 app

# 3) pasang/replace APK tanpa menghapus data aplikasi
adb -s emulator-5554 install -r -g \
  app/build/outputs/apk/debug/app-debug.apk

# 4) buka ulang setelah replace
pixel10 app

# 5) amati log kategori penting
adb -s emulator-5554 logcat -s FlufEngagement FlufPlayDownload
```

Praktik yang membantu:

- **Reset state** sebelum skenario: `adb -s emulator-5554 shell pm clear id.flufclip.player`
  (menghapus sesi akun dan riwayat lokal).
- **Screenshot bukti**: `pixel10 shot ~/Pictures/pixel10/<skenario>.png`.
- **Uji dengan jaringan lambat/putus**: `adb -s emulator-5554 shell cmd connectivity airplane-mode enable`
  (matikan dengan `disable`), atau lewat Quick Settings emulator.
- **Ukuran & versi terpasang**:
  `adb -s emulator-5554 shell dumpsys package id.flufclip.player | grep -E 'versionName|versionCode'`.

## 5. Uji otomatis

Unit test dan lint berjalan di host (tidak butuh emulator):

```bash
cd ~/Documents/flufclip/flufplay
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Catatan per 5 November 2026: modul `app` **belum punya source set `androidTest`**, jadi
belum ada instrumented test dan `:app:connectedDebugAndroidTest` belum bisa dipakai.
Setelah `app/src/androidTest` ditambahkan, task itu otomatis memakai emulator yang sedang
jalan (`emulator-5554`) sebagai target, sehingga alurnya cukup:

```bash
pixel10                       # pastikan emulator siap
cd flufplay
./gradlew :app:connectedDebugAndroidTest
```

Sampai saat itu, verifikasi UI masih manual memakai alur bagian 4; catat hasilnya di
[Testing.md](Testing.md) seperti biasa.

## 6. Diagnostik

```bash
pixel10 status                 # boot / Android / layar / CPU / GPU
pixel10 logs | tail -40        # log emulator
adb -s emulator-5554 logcat -s FlufEngagement FlufPlayDownload
adb -s emulator-5554 shell dumpsys activity activities | grep -m1 topResumedActivity
```

CPU normal saat idle di mesin ini sekitar 30–130% (bergantung animasi dan Play Store sync).
Kalau `status` menunjukkan CPU terus >300%, biasanya render jatuh ke software — cek `gpu`
dan pastikan nilainya `host`.

## 7. Troubleshooting

| Gejala | Penyebab | Tindakan |
| --- | --- | --- |
| Dialog **“Emulator Is Not Responding”** | CPU kelaparan saat boot pertama / RAM host penuh | Klik **Wait**; pastikan `pixel10 status` → `gpu host`; tutup tab Chrome berat |
| `Your GPU cannot be used for hardware rendering` | driver/GL host ditolak | `pixel10 --gpu swiftshader start` (lebih lambat, tapi jalan) |
| Boot menggantung / snapshot rusak | snapshot lama tidak kompatibel | `pixel10 --cold start` |
| Aplikasi ingin state bersih | data lama | `pixel10 --wipe start` (data AVD hilang) |
| `adb: device offline` | server adb desync | `adb kill-server && adb start-server` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | APK beda signature | `adb uninstall id.flufclip.player` lalu install lagi |
| Jendela tidak muncul | `DISPLAY`/`XAUTHORITY` tidak ada | jalankan dari terminal desktop, atau `pixel10 --headless start` |
| Emulator sangat lambat | RAM host habis (14 GB) | tutup aplikasi berat; pertimbangkan turunkan `hw.ramSize` ke 2048 |

## 8. Lokasi file penting

| Isi | Lokasi |
| --- | --- |
| Script | `tools/pixel10` (repo), symlink `~/.local/bin/pixel10` |
| AVD & config | `~/.android/avd/Pixel_10.avd/` (`config.ini`) |
| Snapshot quickboot | `~/.android/avd/Pixel_10.avd/snapshots/default_boot/` (±2,9 GB) |
| Log emulator | `/tmp/pixel10-emu.log` |
| Screenshot | `~/Pictures/pixel10/` |
| APK debug | `flufplay/app/build/outputs/apk/debug/app-debug.apk` |
| Shortcut menu | `~/.local/share/applications/pixel-10.desktop`, `flufplay.desktop` |

## 9. Batas pengujian di emulator

- Emulator memakai image Google Play x86_64: perilaku YouTube, Feed akun, dan pembatasan
  region bisa **berbeda** dari perangkat Xiaomi yang dipakai di [Testing.md](Testing.md).
- Cast, Picture-in-Picture, MediaStore/penyimpanan, dan performa unduhan tetap perlu
  verifikasi perangkat fisik.
- Video live dan Shorts bergantung jaringan; emulator memakai NAT host, jadi hasil bisa
  berbeda saat jaringan host lambat.
- Snapshot quickboot mempercepat start, tetapi **bukan** jaminan state bersih — gunakan
  `--cold` atau `pm clear` untuk skenario yang membutuhkan kondisi awal.

## 10. Checklist uji cepat

- [ ] `pixel10 status` → `boot siap`, `gpu host`
- [ ] APK terpasang adalah build terbaru (`dumpsys package` → `versionName`)
- [ ] Beranda memuat feed/kiosk
- [ ] Buka video → putar → fullscreen → kembali ke miniplayer
- [ ] Shorts: geser beberapa video
- [ ] Unduhan: MP4 dan MP3, cek folder `Download/video-yt-downloader`
- [ ] Engagement: like/save/subscribe, termasuk saat mode pesawat
- [ ] Screenshot bukti tersimpan dan hasil dicatat di `docs/Testing.md`
