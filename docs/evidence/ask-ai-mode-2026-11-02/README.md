# Bukti Ask / AI Mode YouTube untuk FlufPlay (2026-11-02)

Hasil probe baca-saja dan **satu** pertanyaan sungguhan dari akun yang login di FlufPlay (izin pengguna). Semua berasal dari
klien **WEB** (`clientName: WEB`, versi `2.20261029.11.00`), bukan dari app Android resmi.

| Berkas | Isi |
|---|---|
| `ask_probe.py` | Skrip probe (tanpa rahasia). Membaca cookie FlufPlay dari ponsel via `adb run-as` **hanya ke memori**, tidak dicetak/disimpan. |
| `next_ask_panel_redacted.json` | Panel `PAyouchat` dan `sendUserQueryCommand` dari respons `next`, token/ID disensor. |
| `get_panel_response_redacted.json` | Respons penuh satu pertanyaan, disensor. |
| `probe_summary.json` | Ringkasan hasil dan nama header yang dikirim (nama saja). |

Sensor: nilai pada kunci `token/params/tracking/entityKey/identifier/clientMessageId/datasyncId/responseId/visitorData`
diganti `<redacted>`, string opak panjang diganti `<opaque N chars>`. Dipindai: tidak ada cookie, email, atau ID panjang.

## Langkah 1: `next` gaya FlufPlay sudah memuat Ask (seen)

Panggilan meniru `InnerTubePlayer.related()`: `POST /youtubei/v1/next?prettyPrint=false`, konteks ringkas
`{clientName, clientVersion, userAgent, hl, gl}`, **tanpa** API key dan `visitorData`, header sama dengan `execute()` plus
`YouTubeAuth.buildAuthHeaders()`. Locale ponsel: `en-US`.

- HTTP 200, 665 KB, `loggedOut: false`.
- `sendUserQueryCommand` ×4, `inputComposerViewModel` ×8, panel `PAyouchat` ×4, ikon `SPARK` ×4.
- Tombol bintang: `contents/twoColumnWatchNextResults/results/results/contents[1]/videoPrimaryInfoRenderer/videoActions/menuRenderer/flexibleItems[0]/menuFlexibleItemRenderer/{menuItem/menuServiceItemRenderer, topLevelButton/buttonViewModel}` (label "Ask questions").
- Juga ada di layar penuh: `playerOverlays/playerOverlayRenderer/fullscreenQuickActionsBar/quickActionsViewModel/quickActionButtons[4]/buttonViewModel`.
- `sendUserQueryCommand.innertubeCommand` = `continuationCommand{token, request: CONTINUATION_REQUEST_TYPE_GET_PANEL}` tanpa
  `commandMetadata.apiUrl`, sehingga route-nya `/youtubei/v1/get_panel` (non-streaming).

## Langkah 2: kirim pertanyaan (seen)

```
POST https://www.youtube.com/youtubei/v1/get_panel?prettyPrint=false
{ "context": {"client": {clientName WEB, clientVersion, userAgent, hl, gl}},
  "continuation": "<token sendUserQueryCommand dari respons next>",
  "formData": {"inputComposerFormData": {
      "clientMessageId": "youchat-<ms>", "playerOffsetMs": "0", "userInputText": "<pertanyaan, maks 500 karakter>"}}}
```

Percobaan pertama (varian gaya FlufPlay, tanpa API key) langsung **HTTP 200**, 19 KB, satu JSON utuh (sekitar 2-3 detik).
Tidak perlu attestation/PO token pada permintaan ini. Pertanyaan: "Summarize this video in one sentence." (video publik).

## Peta respons untuk parser (seen)

Akar: `onResponseReceivedCommand.listMutationCommand.operations.operations[...]`

| Data | Jalur |
|---|---|
| Teks jawaban | `operations[0].insertItemSectionContent.contents[0].youChatItemViewModel.text.content` (+ `styleRuns[{startIndex,length,weightLabel}]`). Teks memuat tanda `*kata*` literal. |
| Tombol di bawah jawaban | `contents[0]...responseActionsViewModel.responseActionsViewModel.actionsBar.flexibleActionsViewModel.actionsRows`: `COPY`, `LIKE`, `DISLIKE`. Salin menampilkan snackbar "Text copied to clipboard". |
| Disclaimer | `contents[1].chatItemViewModel.content.disclaimerItemViewModel.linkfiedText.content` |
| Pertanyaan lanjutan (chip) | `contents[2].youChatItemViewModel.chipsData.chipData[i]`: `text.content`, `continuation` (token milik chip), `onClick` (menyisipkan giliran pengguna), plus `pendingStateEntityKey` dan `lastMessageIdEntityKey` |
| Hapus pesan "loading" | `operations[1].removeItem` |
| Status multi-giliran | `frameworkUpdates.entityBatchUpdate.mutations`: `DELETE` (entity pending) dan `REPLACE stringEntity` dengan `value = "youchat-<clientMessageId>"` |

## Pertanyaan lanjutan dan tombol saran (dari kode web, belum dikirim)

Fungsi kirim `chat input` di bundel web (`ytmainappweb`) dipakai bersama oleh pertanyaan ketik dan tombol saran: memakai
`sendUserQueryCommand` **yang sama** (token dari `next`, tidak diganti per giliran) dengan
`inputComposerFormData{clientMessageId:"youchat-<ms>", userInputText, playerOffsetMs, pageContext, previousClientMessageId, currentUtcTimeMillis, pendingSuggestedQueryIdentifier}`.
Untuk tombol saran: `userInputText` = teks chip dan `pendingSuggestedQueryIdentifier` = `id` chip.
`previousClientMessageId` = `lastMessageId` = nilai `stringEntity` terakhir (`youchat-<clientMessageId>` giliran sebelumnya).
FlufPlay (`AskSession`) mengikuti ini; **belum diuji di perangkat**.

## Belum terbukti (unknown)

- Pertanyaan kedua dan tombol saran di server (bentuknya mengikuti kode web di atas; belum ada yang dikirim).
- Perilaku LIKE/DISLIKE, video tanpa transkrip/tidak didukung, Shorts, error, batas laju, masa berlaku token.
- Stabilitas: endpoint tidak resmi; format bisa berubah.

## Catatan

- Satu pertanyaan ini tercatat di aktivitas Ask akun; bisa dihapus dari riwayat akun.
- Jangan simpan atau cetak `datasyncId` dari `responseContext` (pengenal akun).
