use std::fs::File;
use std::io::{BufWriter, Read, Seek, SeekFrom, Write};
use std::sync::atomic::{AtomicBool, Ordering};

const COPY_BUF_BYTES: usize = 64 * 1024;
const MOVIE_TIMESCALE: u64 = 1000;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum TrackKind {
    Video,
    Audio,
    Any,
}

#[derive(Clone, Copy, Debug)]
struct Sample {
    offset: u64,
    size: u32,
    duration: u32,
    cts_offset: i32,
    is_sync: bool,
}

#[derive(Clone, Copy, Debug, Default)]
struct TrackExtDefaults {
    track_id: u32,
    default_sample_duration: u32,
    default_sample_size: u32,
    default_sample_flags: u32,
}

#[derive(Debug)]
struct ParsedTrack {
    kind: TrackKind,
    timescale: u32,
    width_fixed: u32,
    height_fixed: u32,
    stsd_payload: Vec<u8>,
    samples: Vec<Sample>,
}

#[derive(Clone, Copy)]
struct BoxHeader {
    start: u64,
    header_size: u64,
    end: u64,
    fourcc: [u8; 4],
}

fn read_u16_be(b: &[u8]) -> u16 {
    u16::from_be_bytes([b[0], b[1]])
}

fn read_u32_be(b: &[u8]) -> u32 {
    u32::from_be_bytes([b[0], b[1], b[2], b[3]])
}

fn read_i32_be(b: &[u8]) -> i32 {
    i32::from_be_bytes([b[0], b[1], b[2], b[3]])
}

fn read_u64_be(b: &[u8]) -> u64 {
    u64::from_be_bytes([b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]])
}

fn read_box_header_from_file(file: &mut File, pos: u64, limit: u64) -> Result<Option<BoxHeader>, String> {
    if pos + 8 > limit {
        return Ok(None);
    }
    file.seek(SeekFrom::Start(pos))
        .map_err(|e| format!("Seek failed: {e}"))?;
    let mut buf = [0u8; 8];
    file.read_exact(&mut buf)
        .map_err(|e| format!("Box header read failed: {e}"))?;
    let size32 = read_u32_be(&buf[0..4]) as u64;
    let fourcc = [buf[4], buf[5], buf[6], buf[7]];
    let (header_size, total_size) = if size32 == 1 {
        if pos + 16 > limit {
            return Err("Truncated 64-bit box header".to_string());
        }
        let mut ext = [0u8; 8];
        file.read_exact(&mut ext)
            .map_err(|e| format!("64-bit box size read failed: {e}"))?;
        let s = read_u64_be(&ext);
        if s < 16 {
            return Err("Invalid 64-bit box size".to_string());
        }
        (16u64, s)
    } else if size32 == 0 {
        (8u64, limit - pos)
    } else {
        if size32 < 8 {
            return Err("Invalid 32-bit box size".to_string());
        }
        (8u64, size32)
    };
    let end = pos
        .checked_add(total_size)
        .ok_or_else(|| "Box size overflow".to_string())?;
    if end > limit {
        return Err(format!(
            "Box {:?} exceeds container bounds ({end} > {limit})",
            std::str::from_utf8(&fourcc).unwrap_or("????")
        ));
    }
    Ok(Some(BoxHeader {
        start: pos,
        header_size,
        end,
        fourcc,
    }))
}

struct SliceBox<'a> {
    fourcc: [u8; 4],
    payload: &'a [u8],
}

fn iter_slice_boxes<'a>(data: &'a [u8]) -> Result<Vec<SliceBox<'a>>, String> {
    let mut out = Vec::new();
    let mut pos = 0usize;
    while pos + 8 <= data.len() {
        let size32 = read_u32_be(&data[pos..pos + 4]) as usize;
        let fourcc = [data[pos + 4], data[pos + 5], data[pos + 6], data[pos + 7]];
        let (hdr, end) = if size32 == 1 {
            if pos + 16 > data.len() {
                return Err("Truncated 64-bit sub-box".to_string());
            }
            let s = read_u64_be(&data[pos + 8..pos + 16]) as usize;
            if s < 16 || pos + s > data.len() {
                return Err("Invalid 64-bit sub-box bounds".to_string());
            }
            (16usize, pos + s)
        } else if size32 == 0 {
            (8usize, data.len())
        } else {
            if size32 < 8 || pos + size32 > data.len() {
                return Err("Invalid sub-box bounds".to_string());
            }
            (8usize, pos + size32)
        };
        out.push(SliceBox {
            fourcc,
            payload: &data[pos + hdr..end],
        });
        pos = end;
    }
    Ok(out)
}

fn find_box<'a>(boxes: &'a [SliceBox<'a>], fourcc: &[u8; 4]) -> Option<&'a [u8]> {
    boxes.iter().find(|b| &b.fourcc == fourcc).map(|b| b.payload)
}

#[derive(Default)]
struct StblTables {
    stsd_payload: Option<Vec<u8>>,
    stts: Vec<(u32, u32)>,
    ctts: Vec<(u32, i32)>,
    stss: Option<Vec<u32>>,
    stsc: Vec<(u32, u32)>,
    stsz_const: u32,
    stsz_count: u32,
    stsz_entries: Vec<u32>,
    chunk_offsets: Vec<u64>,
}

fn parse_stbl(data: &[u8]) -> Result<StblTables, String> {
    let mut tables = StblTables::default();
    for b in iter_slice_boxes(data)? {
        match &b.fourcc {
            b"stsd" => {
                tables.stsd_payload = Some(b.payload.to_vec());
            }
            b"stts" if b.payload.len() >= 8 => {
                let count = read_u32_be(&b.payload[4..8]) as usize;
                let mut p = 8;
                for _ in 0..count {
                    if p + 8 > b.payload.len() {
                        break;
                    }
                    tables
                        .stts
                        .push((read_u32_be(&b.payload[p..p + 4]), read_u32_be(&b.payload[p + 4..p + 8])));
                    p += 8;
                }
            }
            b"ctts" if b.payload.len() >= 8 => {
                let version = b.payload[0];
                let count = read_u32_be(&b.payload[4..8]) as usize;
                let mut p = 8;
                for _ in 0..count {
                    if p + 8 > b.payload.len() {
                        break;
                    }
                    let sc = read_u32_be(&b.payload[p..p + 4]);
                    let offset = if version == 0 {
                        read_u32_be(&b.payload[p + 4..p + 8]) as i32
                    } else {
                        read_i32_be(&b.payload[p + 4..p + 8])
                    };
                    tables.ctts.push((sc, offset));
                    p += 8;
                }
            }
            b"stss" if b.payload.len() >= 8 => {
                let count = read_u32_be(&b.payload[4..8]) as usize;
                let mut vec = Vec::with_capacity(count.min(1_000_000));
                let mut p = 8;
                for _ in 0..count {
                    if p + 4 > b.payload.len() {
                        break;
                    }
                    vec.push(read_u32_be(&b.payload[p..p + 4]));
                    p += 4;
                }
                tables.stss = Some(vec);
            }
            b"stsc" if b.payload.len() >= 8 => {
                let count = read_u32_be(&b.payload[4..8]) as usize;
                let mut p = 8;
                for _ in 0..count {
                    if p + 12 > b.payload.len() {
                        break;
                    }
                    let first_chunk = read_u32_be(&b.payload[p..p + 4]);
                    let samples_per_chunk = read_u32_be(&b.payload[p + 4..p + 8]);
                    tables.stsc.push((first_chunk, samples_per_chunk));
                    p += 12;
                }
            }
            b"stsz" if b.payload.len() >= 12 => {
                tables.stsz_const = read_u32_be(&b.payload[4..8]);
                tables.stsz_count = read_u32_be(&b.payload[8..12]);
                if tables.stsz_const == 0 {
                    let count = tables.stsz_count as usize;
                    tables.stsz_entries.reserve(count.min(2_000_000));
                    let mut p = 12;
                    for _ in 0..count {
                        if p + 4 > b.payload.len() {
                            break;
                        }
                        tables.stsz_entries.push(read_u32_be(&b.payload[p..p + 4]));
                        p += 4;
                    }
                }
            }
            b"stco" if b.payload.len() >= 8 => {
                let count = read_u32_be(&b.payload[4..8]) as usize;
                tables.chunk_offsets.reserve(count.min(1_000_000));
                let mut p = 8;
                for _ in 0..count {
                    if p + 4 > b.payload.len() {
                        break;
                    }
                    tables.chunk_offsets.push(read_u32_be(&b.payload[p..p + 4]) as u64);
                    p += 4;
                }
            }
            b"co64" if b.payload.len() >= 8 => {
                let count = read_u32_be(&b.payload[4..8]) as usize;
                tables.chunk_offsets.reserve(count.min(1_000_000));
                let mut p = 8;
                for _ in 0..count {
                    if p + 8 > b.payload.len() {
                        break;
                    }
                    tables.chunk_offsets.push(read_u64_be(&b.payload[p..p + 8]));
                    p += 8;
                }
            }
            _ => {}
        }
    }
    Ok(tables)
}

fn expand_stbl_samples(tables: &StblTables, kind: TrackKind) -> Result<Vec<Sample>, String> {
    let total = tables.stsz_count as usize;
    if total == 0 {
        return Ok(Vec::new());
    }
    let mut durations = Vec::with_capacity(total);
    for &(cnt, dur) in &tables.stts {
        for _ in 0..cnt {
            if durations.len() >= total {
                break;
            }
            durations.push(dur);
        }
    }
    while durations.len() < total {
        durations.push(1);
    }

    let mut cts_offsets = vec![0i32; total];
    if !tables.ctts.is_empty() {
        let mut idx = 0usize;
        for &(cnt, off) in &tables.ctts {
            for _ in 0..cnt {
                if idx >= total {
                    break;
                }
                cts_offsets[idx] = off;
                idx += 1;
            }
        }
    }

    let mut sync_flags = vec![kind == TrackKind::Audio || tables.stss.is_none(); total];
    if let Some(stss) = &tables.stss {
        for &one_based in stss {
            if one_based >= 1 && (one_based as usize) <= total {
                sync_flags[(one_based - 1) as usize] = true;
            }
        }
    }

    let mut offsets = Vec::with_capacity(total);
    let mut sizes = Vec::with_capacity(total);
    let num_chunks = tables.chunk_offsets.len();
    for c_idx in 0..num_chunks {
        let chunk_num = (c_idx + 1) as u32;
        let samples_in_chunk = tables
            .stsc
            .iter()
            .rfind(|&&(first, _)| first <= chunk_num)
            .map(|&(_, spc)| spc)
            .unwrap_or(1);
        let mut cursor = tables.chunk_offsets[c_idx];
        for _ in 0..samples_in_chunk {
            let s_idx = sizes.len();
            if s_idx >= total {
                break;
            }
            let sz = if tables.stsz_const > 0 {
                tables.stsz_const
            } else {
                *tables
                    .stsz_entries
                    .get(s_idx)
                    .ok_or_else(|| "Missing stsz sample entry".to_string())?
            };
            offsets.push(cursor);
            sizes.push(sz);
            cursor += sz as u64;
        }
    }
    if sizes.len() != total {
        return Err(format!(
            "Sample table mismatch: stsz={total}, chunk-mapped={}",
            sizes.len()
        ));
    }

    let mut samples = Vec::with_capacity(total);
    for i in 0..total {
        samples.push(Sample {
            offset: offsets[i],
            size: sizes[i],
            duration: durations[i],
            cts_offset: cts_offsets[i],
            is_sync: sync_flags[i],
        });
    }
    Ok(samples)
}

fn parse_trex_list(mvex: &[u8]) -> Result<Vec<TrackExtDefaults>, String> {
    let mut out = Vec::new();
    for b in iter_slice_boxes(mvex)? {
        if &b.fourcc == b"trex" && b.payload.len() >= 24 {
            out.push(TrackExtDefaults {
                track_id: read_u32_be(&b.payload[4..8]),
                default_sample_duration: read_u32_be(&b.payload[12..16]),
                default_sample_size: read_u32_be(&b.payload[16..20]),
                default_sample_flags: read_u32_be(&b.payload[20..24]),
            });
        }
    }
    Ok(out)
}

fn parse_moof_samples(
    moof_bytes: &[u8],
    moof_start: u64,
    following_mdat_payload: Option<u64>,
    target_track_id: u32,
    trex: TrackExtDefaults,
    kind: TrackKind,
    out: &mut Vec<Sample>,
) -> Result<(), String> {
    for b in iter_slice_boxes(moof_bytes)? {
        if &b.fourcc != b"traf" {
            continue;
        }
        let sub = iter_slice_boxes(b.payload)?;
        let tfhd = match find_box(&sub, b"tfhd") {
            Some(v) if v.len() >= 8 => v,
            _ => continue,
        };
        let tfhd_flags = read_u32_be(&tfhd[0..4]) & 0x00FF_FFFF;
        let track_id = read_u32_be(&tfhd[4..8]);
        if track_id != target_track_id {
            continue;
        }
        let mut p = 8usize;
        let mut base_data_offset = if (tfhd_flags & 0x000001) != 0 && p + 8 <= tfhd.len() {
            let v = read_u64_be(&tfhd[p..p + 8]);
            p += 8;
            Some(v)
        } else {
            None
        };
        if (tfhd_flags & 0x000002) != 0 && p + 4 <= tfhd.len() {
            p += 4; // sample_description_index
        }
        let def_dur = if (tfhd_flags & 0x000008) != 0 && p + 4 <= tfhd.len() {
            let v = read_u32_be(&tfhd[p..p + 4]);
            p += 4;
            v
        } else {
            trex.default_sample_duration
        };
        let def_size = if (tfhd_flags & 0x000010) != 0 && p + 4 <= tfhd.len() {
            let v = read_u32_be(&tfhd[p..p + 4]);
            p += 4;
            v
        } else {
            trex.default_sample_size
        };
        let def_flags = if (tfhd_flags & 0x000020) != 0 && p + 4 <= tfhd.len() {
            read_u32_be(&tfhd[p..p + 4])
        } else {
            trex.default_sample_flags
        };
        if base_data_offset.is_none() && (tfhd_flags & 0x020000) != 0 {
            base_data_offset = Some(moof_start);
        }

        let mut cursor = base_data_offset.or(following_mdat_payload).unwrap_or(moof_start);
        for sb in &sub {
            if &sb.fourcc != b"trun" || sb.payload.len() < 8 {
                continue;
            }
            let version = sb.payload[0];
            let trun_flags = read_u32_be(&sb.payload[0..4]) & 0x00FF_FFFF;
            let sample_count = read_u32_be(&sb.payload[4..8]) as usize;
            let mut tp = 8usize;
            if (trun_flags & 0x0001) != 0 && tp + 4 <= sb.payload.len() {
                let data_offset = read_i32_be(&sb.payload[tp..tp + 4]) as i64;
                tp += 4;
                let base = base_data_offset.unwrap_or(moof_start) as i64;
                let resolved = base + data_offset;
                if resolved < 0 {
                    return Err("Negative trun data offset".to_string());
                }
                cursor = resolved as u64;
            }
            let first_flags = if (trun_flags & 0x0004) != 0 && tp + 4 <= sb.payload.len() {
                let v = read_u32_be(&sb.payload[tp..tp + 4]);
                tp += 4;
                Some(v)
            } else {
                None
            };

            out.reserve(sample_count.min(100_000));
            for i in 0..sample_count {
                let dur = if (trun_flags & 0x0100) != 0 {
                    if tp + 4 > sb.payload.len() {
                        return Err("Truncated trun sample_duration".to_string());
                    }
                    let v = read_u32_be(&sb.payload[tp..tp + 4]);
                    tp += 4;
                    v
                } else {
                    def_dur
                };
                let size = if (trun_flags & 0x0200) != 0 {
                    if tp + 4 > sb.payload.len() {
                        return Err("Truncated trun sample_size".to_string());
                    }
                    let v = read_u32_be(&sb.payload[tp..tp + 4]);
                    tp += 4;
                    v
                } else {
                    def_size
                };
                let flags = if (trun_flags & 0x0400) != 0 {
                    if tp + 4 > sb.payload.len() {
                        return Err("Truncated trun sample_flags".to_string());
                    }
                    let v = read_u32_be(&sb.payload[tp..tp + 4]);
                    tp += 4;
                    v
                } else if i == 0 {
                    first_flags.unwrap_or(def_flags)
                } else {
                    def_flags
                };
                let cts_offset = if (trun_flags & 0x0800) != 0 {
                    if tp + 4 > sb.payload.len() {
                        return Err("Truncated trun cts_offset".to_string());
                    }
                    let v = if version == 0 {
                        read_u32_be(&sb.payload[tp..tp + 4]) as i32
                    } else {
                        read_i32_be(&sb.payload[tp..tp + 4])
                    };
                    tp += 4;
                    v
                } else {
                    0
                };
                let is_sync = if kind == TrackKind::Audio {
                    true
                } else {
                    (flags & 0x0001_0000) == 0
                };
                out.push(Sample {
                    offset: cursor,
                    size,
                    duration: dur.max(1),
                    cts_offset,
                    is_sync,
                });
                cursor = cursor
                    .checked_add(size as u64)
                    .ok_or_else(|| "Sample offset overflow".to_string())?;
            }
        }
    }
    Ok(())
}

fn extract_track(file: &mut File, expected: TrackKind) -> Result<ParsedTrack, String> {
    let file_len = file
        .metadata()
        .map_err(|e| format!("Metadata failed: {e}"))?
        .len();
    let mut top_boxes = Vec::new();
    let mut pos = 0u64;
    while let Some(hdr) = read_box_header_from_file(file, pos, file_len)? {
        top_boxes.push(hdr);
        pos = hdr.end;
    }

    let moov_hdr = top_boxes
        .iter()
        .find(|b| &b.fourcc == b"moov")
        .copied()
        .ok_or_else(|| "Missing moov box in MP4/M4A stream".to_string())?;

    let mut moov_bytes = vec![0u8; (moov_hdr.end - moov_hdr.start - moov_hdr.header_size) as usize];
    file.seek(SeekFrom::Start(moov_hdr.start + moov_hdr.header_size))
        .map_err(|e| format!("Seek moov failed: {e}"))?;
    file.read_exact(&mut moov_bytes)
        .map_err(|e| format!("Read moov failed: {e}"))?;

    let moov_children = iter_slice_boxes(&moov_bytes)?;
    let trex_list = find_box(&moov_children, b"mvex")
        .map(parse_trex_list)
        .transpose()?
        .unwrap_or_default();

    let mut chosen: Option<(TrackKind, u32, u32, u32, u32, StblTables)> = None;

    for b in &moov_children {
        if &b.fourcc != b"trak" {
            continue;
        }
        let trak_sub = iter_slice_boxes(b.payload)?;
        let tkhd = match find_box(&trak_sub, b"tkhd") {
            Some(v) if v.len() >= 84 => v,
            _ => continue,
        };
        let tkhd_ver = tkhd[0];
        let (track_id, width_fixed, height_fixed) = if tkhd_ver == 1 && tkhd.len() >= 96 {
            (
                read_u32_be(&tkhd[20..24]),
                read_u32_be(&tkhd[88..92]),
                read_u32_be(&tkhd[92..96]),
            )
        } else {
            (
                read_u32_be(&tkhd[12..16]),
                read_u32_be(&tkhd[76..80]),
                read_u32_be(&tkhd[80..84]),
            )
        };

        let mdia = match find_box(&trak_sub, b"mdia") {
            Some(v) => iter_slice_boxes(v)?,
            None => continue,
        };
        let mdhd = match find_box(&mdia, b"mdhd") {
            Some(v) if v.len() >= 20 => v,
            _ => continue,
        };
        let timescale = if mdhd[0] == 1 && mdhd.len() >= 28 {
            read_u32_be(&mdhd[20..24])
        } else {
            read_u32_be(&mdhd[12..16])
        }
        .max(1);

        let hdlr = match find_box(&mdia, b"hdlr") {
            Some(v) if v.len() >= 12 => v,
            _ => continue,
        };
        let handler = &hdlr[8..12];
        let kind = match handler {
            b"vide" => TrackKind::Video,
            b"soun" => TrackKind::Audio,
            _ => continue,
        };
        if expected != TrackKind::Any && expected != kind {
            continue;
        }
        let minf = match find_box(&mdia, b"minf") {
            Some(v) => iter_slice_boxes(v)?,
            None => continue,
        };
        let stbl_bytes = match find_box(&minf, b"stbl") {
            Some(v) => v,
            None => continue,
        };
        let tables = parse_stbl(stbl_bytes)?;
        chosen = Some((kind, track_id, timescale, width_fixed, height_fixed, tables));
        break;
    }

    let (kind, track_id, timescale, mut width_fixed, mut height_fixed, tables) =
        chosen.ok_or_else(|| "No compatible video/audio track found in input".to_string())?;
    let stsd_payload = tables
        .stsd_payload
        .clone()
        .ok_or_else(|| "Missing stsd box in track".to_string())?;

    if kind == TrackKind::Video && (width_fixed == 0 || height_fixed == 0) && stsd_payload.len() >= 48 {
        let w = read_u16_be(&stsd_payload[40..42]) as u32;
        let h = read_u16_be(&stsd_payload[42..44]) as u32;
        if w > 0 && h > 0 {
            width_fixed = w << 16;
            height_fixed = h << 16;
        }
    }

    let mut samples = expand_stbl_samples(&tables, kind)?;
    if samples.is_empty() {
        let trex = trex_list
            .iter()
            .find(|t| t.track_id == track_id)
            .copied()
            .unwrap_or(TrackExtDefaults {
                track_id,
                ..Default::default()
            });

        for idx in 0..top_boxes.len() {
            let hdr = top_boxes[idx];
            if &hdr.fourcc != b"moof" {
                continue;
            }
            let next_mdat_payload = top_boxes
                .get(idx + 1)
                .filter(|b| &b.fourcc == b"mdat")
                .map(|b| b.start + b.header_size);
            let mut moof_bytes = vec![0u8; (hdr.end - hdr.start - hdr.header_size) as usize];
            file.seek(SeekFrom::Start(hdr.start + hdr.header_size))
                .map_err(|e| format!("Seek moof failed: {e}"))?;
            file.read_exact(&mut moof_bytes)
                .map_err(|e| format!("Read moof failed: {e}"))?;
            parse_moof_samples(
                &moof_bytes,
                hdr.start,
                next_mdat_payload,
                track_id,
                trex,
                kind,
                &mut samples,
            )?;
        }
    }

    if samples.is_empty() {
        return Err("Input track contains zero media samples".to_string());
    }
    for s in &samples {
        let end = s
            .offset
            .checked_add(s.size as u64)
            .ok_or_else(|| "Sample offset overflow".to_string())?;
        if end > file_len {
            return Err(format!("Sample extends beyond EOF ({end} > {file_len})"));
        }
    }

    Ok(ParsedTrack {
        kind,
        timescale,
        width_fixed,
        height_fixed,
        stsd_payload,
        samples,
    })
}

fn push_box(out: &mut Vec<u8>, fourcc: &[u8; 4], content: &[u8]) {
    let total = (content.len() + 8) as u32;
    out.extend_from_slice(&total.to_be_bytes());
    out.extend_from_slice(fourcc);
    out.extend_from_slice(content);
}

fn push_full_box(out: &mut Vec<u8>, fourcc: &[u8; 4], version: u8, flags: u32, body: &[u8]) {
    let total = (body.len() + 12) as u32;
    out.extend_from_slice(&total.to_be_bytes());
    out.extend_from_slice(fourcc);
    let vf = ((version as u32) << 24) | (flags & 0x00FF_FFFF);
    out.extend_from_slice(&vf.to_be_bytes());
    out.extend_from_slice(body);
}

fn build_ftyp() -> Vec<u8> {
    let mut body = Vec::with_capacity(24);
    body.extend_from_slice(b"isom");
    body.extend_from_slice(&0x0000_0200u32.to_be_bytes());
    body.extend_from_slice(b"isom");
    body.extend_from_slice(b"iso2");
    body.extend_from_slice(b"avc1");
    body.extend_from_slice(b"mp41");
    let mut out = Vec::with_capacity(32);
    push_box(&mut out, b"ftyp", &body);
    out
}

fn identity_matrix() -> [u8; 36] {
    let mut m = [0u8; 36];
    m[0..4].copy_from_slice(&0x0001_0000u32.to_be_bytes());
    m[16..20].copy_from_slice(&0x0001_0000u32.to_be_bytes());
    m[32..36].copy_from_slice(&0x4000_0000u32.to_be_bytes());
    m
}

#[derive(Clone, Copy)]
struct ChunkPlan {
    track_idx: usize,
    first_sample: usize,
    sample_count: usize,
    start_us: u64,
    mdat_offset: u64,
}

fn build_chunks_for_track(track_idx: usize, track: &ParsedTrack) -> Vec<ChunkPlan> {
    let target_ticks = (track.timescale as u64 / 2).max(1);
    let mut chunks = Vec::new();
    let mut i = 0usize;
    let mut dts_ticks = 0u64;
    while i < track.samples.len() {
        let first_sample = i;
        let start_us = dts_ticks.saturating_mul(1_000_000) / track.timescale as u64;
        let mut chunk_ticks = 0u64;
        while i < track.samples.len() {
            chunk_ticks += track.samples[i].duration as u64;
            dts_ticks += track.samples[i].duration as u64;
            i += 1;
            if chunk_ticks >= target_ticks {
                break;
            }
        }
        chunks.push(ChunkPlan {
            track_idx,
            first_sample,
            sample_count: i - first_sample,
            start_us,
            mdat_offset: 0,
        });
    }
    chunks
}

fn build_stts_box(samples: &[Sample]) -> Vec<u8> {
    let mut runs: Vec<(u32, u32)> = Vec::new();
    for s in samples {
        if let Some(last) = runs.last_mut() {
            if last.1 == s.duration {
                last.0 += 1;
                continue;
            }
        }
        runs.push((1, s.duration));
    }
    let mut body = Vec::with_capacity(4 + runs.len() * 8);
    body.extend_from_slice(&(runs.len() as u32).to_be_bytes());
    for (cnt, dur) in runs {
        body.extend_from_slice(&cnt.to_be_bytes());
        body.extend_from_slice(&dur.to_be_bytes());
    }
    let mut box_buf = Vec::with_capacity(body.len() + 12);
    push_full_box(&mut box_buf, b"stts", 0, 0, &body);
    box_buf
}

fn build_ctts_box(samples: &[Sample]) -> Option<Vec<u8>> {
    if samples.iter().all(|s| s.cts_offset == 0) {
        return None;
    }
    let has_negative = samples.iter().any(|s| s.cts_offset < 0);
    let mut runs: Vec<(u32, i32)> = Vec::new();
    for s in samples {
        if let Some(last) = runs.last_mut() {
            if last.1 == s.cts_offset {
                last.0 += 1;
                continue;
            }
        }
        runs.push((1, s.cts_offset));
    }
    let mut body = Vec::with_capacity(4 + runs.len() * 8);
    body.extend_from_slice(&(runs.len() as u32).to_be_bytes());
    for (cnt, off) in runs {
        body.extend_from_slice(&cnt.to_be_bytes());
        body.extend_from_slice(&off.to_be_bytes());
    }
    let mut box_buf = Vec::with_capacity(body.len() + 12);
    push_full_box(&mut box_buf, b"ctts", if has_negative { 1 } else { 0 }, 0, &body);
    Some(box_buf)
}

fn build_stss_box(samples: &[Sample]) -> Option<Vec<u8>> {
    let mut syncs = Vec::new();
    for (idx, s) in samples.iter().enumerate() {
        if s.is_sync {
            syncs.push((idx + 1) as u32);
        }
    }
    if syncs.is_empty() || syncs.len() == samples.len() {
        return None;
    }
    let mut body = Vec::with_capacity(4 + syncs.len() * 4);
    body.extend_from_slice(&(syncs.len() as u32).to_be_bytes());
    for idx in syncs {
        body.extend_from_slice(&idx.to_be_bytes());
    }
    let mut box_buf = Vec::with_capacity(body.len() + 12);
    push_full_box(&mut box_buf, b"stss", 0, 0, &body);
    Some(box_buf)
}

fn build_stsc_box(track_chunks: &[&ChunkPlan]) -> Vec<u8> {
    let mut runs: Vec<(u32, u32)> = Vec::new();
    for (i, ch) in track_chunks.iter().enumerate() {
        let chunk_num = (i + 1) as u32;
        let spc = ch.sample_count as u32;
        if let Some(&(_, prev_spc)) = runs.last() {
            if prev_spc == spc {
                continue;
            }
        }
        runs.push((chunk_num, spc));
    }
    let mut body = Vec::with_capacity(4 + runs.len() * 12);
    body.extend_from_slice(&(runs.len() as u32).to_be_bytes());
    for (first, spc) in runs {
        body.extend_from_slice(&first.to_be_bytes());
        body.extend_from_slice(&spc.to_be_bytes());
        body.extend_from_slice(&1u32.to_be_bytes());
    }
    let mut box_buf = Vec::with_capacity(body.len() + 12);
    push_full_box(&mut box_buf, b"stsc", 0, 0, &body);
    box_buf
}

fn build_stsz_box(samples: &[Sample]) -> Vec<u8> {
    let first_sz = samples.first().map(|s| s.size).unwrap_or(0);
    let uniform = first_sz > 0 && samples.iter().all(|s| s.size == first_sz);
    let mut body = if uniform {
        Vec::with_capacity(8)
    } else {
        Vec::with_capacity(8 + samples.len() * 4)
    };
    body.extend_from_slice(&(if uniform { first_sz } else { 0 }).to_be_bytes());
    body.extend_from_slice(&(samples.len() as u32).to_be_bytes());
    if !uniform {
        for s in samples {
            body.extend_from_slice(&s.size.to_be_bytes());
        }
    }
    let mut box_buf = Vec::with_capacity(body.len() + 12);
    push_full_box(&mut box_buf, b"stsz", 0, 0, &body);
    box_buf
}

fn build_moov(
    tracks: &[&ParsedTrack],
    interleaved_chunks: &[ChunkPlan],
    mdat_data_start: u64,
    use_co64: bool,
) -> Vec<u8> {
    let track_durations_ticks: Vec<u64> = tracks
        .iter()
        .map(|t| t.samples.iter().map(|s| s.duration as u64).sum::<u64>())
        .collect();
    let track_durations_movie: Vec<u64> = tracks
        .iter()
        .enumerate()
        .map(|(i, t)| track_durations_ticks[i].saturating_mul(MOVIE_TIMESCALE) / t.timescale as u64)
        .collect();
    let movie_duration = track_durations_movie.iter().copied().max().unwrap_or(0);

    let mut moov_body = Vec::new();

    // mvhd
    let mvhd_ver = if movie_duration > u32::MAX as u64 { 1u8 } else { 0u8 };
    let mut mvhd_body = Vec::with_capacity(112);
    if mvhd_ver == 1 {
        mvhd_body.extend_from_slice(&0u64.to_be_bytes());
        mvhd_body.extend_from_slice(&0u64.to_be_bytes());
        mvhd_body.extend_from_slice(&(MOVIE_TIMESCALE as u32).to_be_bytes());
        mvhd_body.extend_from_slice(&movie_duration.to_be_bytes());
    } else {
        mvhd_body.extend_from_slice(&0u32.to_be_bytes());
        mvhd_body.extend_from_slice(&0u32.to_be_bytes());
        mvhd_body.extend_from_slice(&(MOVIE_TIMESCALE as u32).to_be_bytes());
        mvhd_body.extend_from_slice(&(movie_duration as u32).to_be_bytes());
    }
    mvhd_body.extend_from_slice(&0x0001_0000u32.to_be_bytes()); // rate 1.0
    mvhd_body.extend_from_slice(&0x0100u16.to_be_bytes()); // volume 1.0
    mvhd_body.extend_from_slice(&[0u8; 10]);
    mvhd_body.extend_from_slice(&identity_matrix());
    mvhd_body.extend_from_slice(&[0u8; 24]);
    mvhd_body.extend_from_slice(&((tracks.len() as u32) + 1).to_be_bytes());
    push_full_box(&mut moov_body, b"mvhd", mvhd_ver, 0, &mvhd_body);

    for (t_idx, track) in tracks.iter().enumerate() {
        let track_id = (t_idx + 1) as u32;
        let t_dur_movie = track_durations_movie[t_idx];
        let t_dur_media = track_durations_ticks[t_idx];

        let mut trak_body = Vec::new();

        // tkhd
        let tkhd_ver = if t_dur_movie > u32::MAX as u64 { 1u8 } else { 0u8 };
        let mut tkhd_body = Vec::with_capacity(96);
        if tkhd_ver == 1 {
            tkhd_body.extend_from_slice(&0u64.to_be_bytes());
            tkhd_body.extend_from_slice(&0u64.to_be_bytes());
            tkhd_body.extend_from_slice(&track_id.to_be_bytes());
            tkhd_body.extend_from_slice(&0u32.to_be_bytes());
            tkhd_body.extend_from_slice(&t_dur_movie.to_be_bytes());
        } else {
            tkhd_body.extend_from_slice(&0u32.to_be_bytes());
            tkhd_body.extend_from_slice(&0u32.to_be_bytes());
            tkhd_body.extend_from_slice(&track_id.to_be_bytes());
            tkhd_body.extend_from_slice(&0u32.to_be_bytes());
            tkhd_body.extend_from_slice(&(t_dur_movie as u32).to_be_bytes());
        }
        tkhd_body.extend_from_slice(&[0u8; 8]); // reserved
        tkhd_body.extend_from_slice(&0u16.to_be_bytes()); // layer
        tkhd_body.extend_from_slice(&0u16.to_be_bytes()); // alternate_group
        let volume = if track.kind == TrackKind::Audio { 0x0100u16 } else { 0u16 };
        tkhd_body.extend_from_slice(&volume.to_be_bytes());
        tkhd_body.extend_from_slice(&0u16.to_be_bytes());
        tkhd_body.extend_from_slice(&identity_matrix());
        let (w, h) = if track.kind == TrackKind::Video {
            (track.width_fixed, track.height_fixed)
        } else {
            (0, 0)
        };
        tkhd_body.extend_from_slice(&w.to_be_bytes());
        tkhd_body.extend_from_slice(&h.to_be_bytes());
        push_full_box(&mut trak_body, b"tkhd", tkhd_ver, 0x07, &tkhd_body);

        // mdia
        let mut mdia_body = Vec::new();

        // mdhd
        let mdhd_ver = if t_dur_media > u32::MAX as u64 { 1u8 } else { 0u8 };
        let mut mdhd_body = Vec::with_capacity(32);
        if mdhd_ver == 1 {
            mdhd_body.extend_from_slice(&0u64.to_be_bytes());
            mdhd_body.extend_from_slice(&0u64.to_be_bytes());
            mdhd_body.extend_from_slice(&track.timescale.to_be_bytes());
            mdhd_body.extend_from_slice(&t_dur_media.to_be_bytes());
        } else {
            mdhd_body.extend_from_slice(&0u32.to_be_bytes());
            mdhd_body.extend_from_slice(&0u32.to_be_bytes());
            mdhd_body.extend_from_slice(&track.timescale.to_be_bytes());
            mdhd_body.extend_from_slice(&(t_dur_media as u32).to_be_bytes());
        }
        mdhd_body.extend_from_slice(&0x55C4u16.to_be_bytes()); // "und"
        mdhd_body.extend_from_slice(&0u16.to_be_bytes());
        push_full_box(&mut mdia_body, b"mdhd", mdhd_ver, 0, &mdhd_body);

        // hdlr
        let mut hdlr_body = Vec::with_capacity(36);
        hdlr_body.extend_from_slice(&0u32.to_be_bytes());
        if track.kind == TrackKind::Video {
            hdlr_body.extend_from_slice(b"vide");
            hdlr_body.extend_from_slice(&[0u8; 12]);
            hdlr_body.extend_from_slice(b"VideoHandler\0");
        } else {
            hdlr_body.extend_from_slice(b"soun");
            hdlr_body.extend_from_slice(&[0u8; 12]);
            hdlr_body.extend_from_slice(b"SoundHandler\0");
        }
        push_full_box(&mut mdia_body, b"hdlr", 0, 0, &hdlr_body);

        // minf
        let mut minf_body = Vec::new();
        if track.kind == TrackKind::Video {
            push_full_box(&mut minf_body, b"vmhd", 0, 1, &[0u8; 8]);
        } else {
            push_full_box(&mut minf_body, b"smhd", 0, 0, &[0u8; 4]);
        }

        // dinf -> dref -> url
        let mut url_box = Vec::new();
        push_full_box(&mut url_box, b"url ", 0, 1, &[]);
        let mut dref_body = Vec::with_capacity(4 + url_box.len());
        dref_body.extend_from_slice(&1u32.to_be_bytes());
        dref_body.extend_from_slice(&url_box);
        let mut dref_box = Vec::new();
        push_full_box(&mut dref_box, b"dref", 0, 0, &dref_body);
        push_box(&mut minf_body, b"dinf", &dref_box);

        // stbl
        let mut stbl_body = Vec::new();
        push_box(&mut stbl_body, b"stsd", &track.stsd_payload);
        stbl_body.extend_from_slice(&build_stts_box(&track.samples));
        if let Some(ctts) = build_ctts_box(&track.samples) {
            stbl_body.extend_from_slice(&ctts);
        }
        if track.kind == TrackKind::Video {
            if let Some(stss) = build_stss_box(&track.samples) {
                stbl_body.extend_from_slice(&stss);
            }
        }
        let track_chunks: Vec<&ChunkPlan> = interleaved_chunks
            .iter()
            .filter(|c| c.track_idx == t_idx)
            .collect();
        stbl_body.extend_from_slice(&build_stsc_box(&track_chunks));
        stbl_body.extend_from_slice(&build_stsz_box(&track.samples));

        if use_co64 {
            let mut body = Vec::with_capacity(4 + track_chunks.len() * 8);
            body.extend_from_slice(&(track_chunks.len() as u32).to_be_bytes());
            for ch in track_chunks {
                body.extend_from_slice(&(mdat_data_start + ch.mdat_offset).to_be_bytes());
            }
            push_full_box(&mut stbl_body, b"co64", 0, 0, &body);
        } else {
            let mut body = Vec::with_capacity(4 + track_chunks.len() * 4);
            body.extend_from_slice(&(track_chunks.len() as u32).to_be_bytes());
            for ch in track_chunks {
                body.extend_from_slice(&((mdat_data_start + ch.mdat_offset) as u32).to_be_bytes());
            }
            push_full_box(&mut stbl_body, b"stco", 0, 0, &body);
        }

        push_box(&mut minf_body, b"stbl", &stbl_body);
        push_box(&mut mdia_body, b"minf", &minf_body);
        push_box(&mut trak_body, b"mdia", &mdia_body);
        push_box(&mut moov_body, b"trak", &trak_body);
    }

    let mut moov = Vec::with_capacity(moov_body.len() + 8);
    push_box(&mut moov, b"moov", &moov_body);
    moov
}

pub fn mux_mp4_to_writer<W: Write>(
    video_file: &mut File,
    mut audio_file: Option<&mut File>,
    output: W,
    cancel: &AtomicBool,
) -> Result<u64, String> {
    if cancel.load(Ordering::Relaxed) {
        return Err("Download canceled".to_string());
    }

    let primary_kind = if audio_file.is_some() {
        TrackKind::Video
    } else {
        TrackKind::Any
    };
    let track0 = extract_track(video_file, primary_kind)?;
    let track1 = if let Some(ref mut af) = audio_file {
        Some(extract_track(af, TrackKind::Audio)?)
    } else {
        None
    };

    let mut tracks: Vec<&ParsedTrack> = Vec::with_capacity(2);
    tracks.push(&track0);
    if let Some(ref t1) = track1 {
        tracks.push(t1);
    }

    let mut chunks = build_chunks_for_track(0, &track0);
    if let Some(ref t1) = track1 {
        chunks.extend(build_chunks_for_track(1, t1));
    }
    chunks.sort_by_key(|c| (c.start_us, c.track_idx));

    let mut mdat_payload_len = 0u64;
    for ch in &mut chunks {
        ch.mdat_offset = mdat_payload_len;
        let samples = &tracks[ch.track_idx].samples;
        for s in &samples[ch.first_sample..ch.first_sample + ch.sample_count] {
            mdat_payload_len += s.size as u64;
        }
    }

    let ftyp = build_ftyp();
    let mdat_large = mdat_payload_len + 8 > u32::MAX as u64;
    let mdat_hdr_len = if mdat_large { 16u64 } else { 8u64 };

    // First build moov with 32-bit stco to check exact size & whether co64 is required.
    let probe_moov = build_moov(&tracks, &chunks, 0, false);
    let est_mdat_data_start = ftyp.len() as u64 + probe_moov.len() as u64 + mdat_hdr_len;
    let use_co64 = est_mdat_data_start + mdat_payload_len > u32::MAX as u64;
    let moov = if use_co64 {
        let probe_64 = build_moov(&tracks, &chunks, 0, true);
        let start = ftyp.len() as u64 + probe_64.len() as u64 + mdat_hdr_len;
        build_moov(&tracks, &chunks, start, true)
    } else {
        build_moov(&tracks, &chunks, est_mdat_data_start, false)
    };

    let mut writer = BufWriter::with_capacity(COPY_BUF_BYTES, output);
    writer
        .write_all(&ftyp)
        .map_err(|e| format!("Write ftyp failed: {e}"))?;
    writer
        .write_all(&moov)
        .map_err(|e| format!("Write moov failed: {e}"))?;
    if mdat_large {
        writer
            .write_all(&1u32.to_be_bytes())
            .map_err(|e| format!("Write mdat header failed: {e}"))?;
        writer
            .write_all(b"mdat")
            .map_err(|e| format!("Write mdat header failed: {e}"))?;
        writer
            .write_all(&(mdat_payload_len + 16).to_be_bytes())
            .map_err(|e| format!("Write mdat size failed: {e}"))?;
    } else {
        writer
            .write_all(&((mdat_payload_len + 8) as u32).to_be_bytes())
            .map_err(|e| format!("Write mdat header failed: {e}"))?;
        writer
            .write_all(b"mdat")
            .map_err(|e| format!("Write mdat header failed: {e}"))?;
    }

    let mut buf = vec![0u8; COPY_BUF_BYTES];
    for ch in &chunks {
        if cancel.load(Ordering::Relaxed) {
            return Err("Download canceled".to_string());
        }
        let src_file: &mut File = if ch.track_idx == 0 {
            video_file
        } else {
            audio_file.as_mut().unwrap()
        };
        let samples = &tracks[ch.track_idx].samples;
        for s in &samples[ch.first_sample..ch.first_sample + ch.sample_count] {
            src_file
                .seek(SeekFrom::Start(s.offset))
                .map_err(|e| format!("Seek sample failed: {e}"))?;
            let mut rem = s.size as usize;
            while rem > 0 {
                if cancel.load(Ordering::Relaxed) {
                    return Err("Download canceled".to_string());
                }
                let n = rem.min(buf.len());
                src_file
                    .read_exact(&mut buf[..n])
                    .map_err(|e| format!("Read sample failed: {e}"))?;
                writer
                    .write_all(&buf[..n])
                    .map_err(|e| format!("Write sample failed: {e}"))?;
                rem -= n;
            }
        }
    }

    writer.flush().map_err(|e| format!("Mux flush failed: {e}"))?;
    Ok(ftyp.len() as u64 + moov.len() as u64 + mdat_hdr_len + mdat_payload_len)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    fn make_box(fourcc: &[u8; 4], body: &[u8]) -> Vec<u8> {
        let mut v = Vec::new();
        push_box(&mut v, fourcc, body);
        v
    }

    fn make_full_box(fourcc: &[u8; 4], ver: u8, flags: u32, body: &[u8]) -> Vec<u8> {
        let mut v = Vec::new();
        push_full_box(&mut v, fourcc, ver, flags, body);
        v
    }

    fn synthetic_fmp4(kind: TrackKind, timescale: u32, samples_data: &[&[u8]]) -> Vec<u8> {
        let ftyp = build_ftyp();

        // minimal stsd
        let mut stsd_body = Vec::new();
        stsd_body.extend_from_slice(&1u32.to_be_bytes());
        let entry_fourcc = if kind == TrackKind::Video { b"avc1" } else { b"mp4a" };
        stsd_body.extend_from_slice(&make_box(entry_fourcc, &[0u8; 40]));
        let stsd = make_full_box(b"stsd", 0, 0, &stsd_body);

        let empty_tbl = [0u8; 4];
        let empty_stsz = [0u8; 8];
        let mut stbl_body = Vec::new();
        stbl_body.extend_from_slice(&stsd);
        stbl_body.extend_from_slice(&make_full_box(b"stts", 0, 0, &empty_tbl));
        stbl_body.extend_from_slice(&make_full_box(b"stsc", 0, 0, &empty_tbl));
        stbl_body.extend_from_slice(&make_full_box(b"stsz", 0, 0, &empty_stsz));
        stbl_body.extend_from_slice(&make_full_box(b"stco", 0, 0, &empty_tbl));
        let stbl = make_box(b"stbl", &stbl_body);

        let minf = make_box(b"minf", &stbl);
        let mut mdhd_body = Vec::new();
        mdhd_body.extend_from_slice(&0u32.to_be_bytes());
        mdhd_body.extend_from_slice(&0u32.to_be_bytes());
        mdhd_body.extend_from_slice(&timescale.to_be_bytes());
        mdhd_body.extend_from_slice(&0u32.to_be_bytes());
        mdhd_body.extend_from_slice(&[0u8; 4]);
        let mut hdlr_body = Vec::new();
        hdlr_body.extend_from_slice(&0u32.to_be_bytes());
        hdlr_body.extend_from_slice(if kind == TrackKind::Video { b"vide" } else { b"soun" });
        hdlr_body.extend_from_slice(&[0u8; 12]);
        hdlr_body.extend_from_slice(b"Test\0");
        let mut mdia_body = Vec::new();
        mdia_body.extend_from_slice(&make_full_box(b"mdhd", 0, 0, &mdhd_body));
        mdia_body.extend_from_slice(&make_full_box(b"hdlr", 0, 0, &hdlr_body));
        mdia_body.extend_from_slice(&minf);
        let mdia = make_box(b"mdia", &mdia_body);

        let mut tkhd_body = vec![0u8; 80];
        tkhd_body[8..12].copy_from_slice(&1u32.to_be_bytes()); // track_id = 1
        if kind == TrackKind::Video {
            tkhd_body[72..76].copy_from_slice(&(1280u32 << 16).to_be_bytes());
            tkhd_body[76..80].copy_from_slice(&(720u32 << 16).to_be_bytes());
        }
        let mut trak_body = Vec::new();
        trak_body.extend_from_slice(&make_full_box(b"tkhd", 0, 7, &tkhd_body));
        trak_body.extend_from_slice(&mdia);
        let trak = make_box(b"trak", &trak_body);

        let mut trex_body = Vec::new();
        trex_body.extend_from_slice(&1u32.to_be_bytes()); // track_id
        trex_body.extend_from_slice(&1u32.to_be_bytes());
        trex_body.extend_from_slice(&100u32.to_be_bytes()); // default duration
        trex_body.extend_from_slice(&0u32.to_be_bytes());
        trex_body.extend_from_slice(&0x0001_0000u32.to_be_bytes()); // non-sync default
        let mvex = make_box(b"mvex", &make_full_box(b"trex", 0, 0, &trex_body));

        let mut moov_body = Vec::new();
        moov_body.extend_from_slice(&trak);
        moov_body.extend_from_slice(&mvex);
        let moov = make_box(b"moov", &moov_body);

        // Build moof + mdat
        let mut tfhd_body = Vec::new();
        tfhd_body.extend_from_slice(&1u32.to_be_bytes());
        let tfhd = make_full_box(b"tfhd", 0, 0x020000, &tfhd_body); // default-base-is-moof

        let mut trun_body = Vec::new();
        trun_body.extend_from_slice(&(samples_data.len() as u32).to_be_bytes());
        trun_body.extend_from_slice(&0i32.to_be_bytes()); // placeholder data_offset
        for (i, s) in samples_data.iter().enumerate() {
            trun_body.extend_from_slice(&(s.len() as u32).to_be_bytes());
            let flags = if i == 0 { 0x0200_0000u32 } else { 0x0101_0000u32 };
            trun_body.extend_from_slice(&flags.to_be_bytes());
        }
        // flags: data_offset(0x1) | sample_size(0x200) | sample_flags(0x400)
        let trun_placeholder = make_full_box(b"trun", 0, 0x0601, &trun_body);
        let traf = make_box(b"traf", &[tfhd.clone(), trun_placeholder].concat());
        let moof_len = 8 + traf.len();
        let data_offset = (moof_len + 8) as i32;
        trun_body[4..8].copy_from_slice(&data_offset.to_be_bytes());
        let trun = make_full_box(b"trun", 0, 0x0601, &trun_body);
        let traf = make_box(b"traf", &[tfhd, trun].concat());
        let moof = make_box(b"moof", &traf);

        let mut mdat_payload = Vec::new();
        for s in samples_data {
            mdat_payload.extend_from_slice(s);
        }
        let mdat = make_box(b"mdat", &mdat_payload);

        [ftyp, moov, moof, mdat].concat()
    }

    #[test]
    fn muxes_fmp4_video_and_audio_into_faststart_mp4() {
        let dir = std::env::temp_dir();
        let v_path = dir.join("fluf_test_v.mp4");
        let a_path = dir.join("fluf_test_a.m4a");
        let v_bytes = synthetic_fmp4(TrackKind::Video, 1000, &[b"VID0", b"VID1", b"VID2"]);
        let a_bytes = synthetic_fmp4(TrackKind::Audio, 1000, &[b"AUD0", b"AUD1"]);
        std::fs::write(&v_path, &v_bytes).unwrap();
        std::fs::write(&a_path, &a_bytes).unwrap();

        let mut vf = File::open(&v_path).unwrap();
        let mut af = File::open(&a_path).unwrap();
        let mut out = Cursor::new(Vec::new());
        let cancel = AtomicBool::new(false);

        let written = mux_mp4_to_writer(&mut vf, Some(&mut af), &mut out, &cancel).unwrap();
        let bytes = out.into_inner();
        assert_eq!(written as usize, bytes.len());

        let top = iter_slice_boxes(&bytes).unwrap();
        assert_eq!(top.len(), 3);
        assert_eq!(&top[0].fourcc, b"ftyp");
        assert_eq!(&top[1].fourcc, b"moov");
        assert_eq!(&top[2].fourcc, b"mdat");

        // Verify remuxed file parses cleanly as a standard non-fragmented MP4 with both tracks!
        let out_path = dir.join("fluf_test_out.mp4");
        std::fs::write(&out_path, &bytes).unwrap();
        let mut of = File::open(&out_path).unwrap();
        let vt = extract_track(&mut of, TrackKind::Video).unwrap();
        let at = extract_track(&mut of, TrackKind::Audio).unwrap();
        assert_eq!(vt.samples.len(), 3);
        assert_eq!(at.samples.len(), 2);
        assert!(vt.samples[0].is_sync);
        assert!(!vt.samples[1].is_sync);

        let mut check_buf = Vec::new();
        for s in &vt.samples {
            of.seek(SeekFrom::Start(s.offset)).unwrap();
            let mut b = vec![0u8; s.size as usize];
            of.read_exact(&mut b).unwrap();
            check_buf.extend_from_slice(&b);
        }
        assert_eq!(&check_buf, b"VID0VID1VID2");

        let _ = std::fs::remove_file(v_path);
        let _ = std::fs::remove_file(a_path);
        let _ = std::fs::remove_file(out_path);
    }
}
