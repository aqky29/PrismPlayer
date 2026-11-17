use std::fs::{File, OpenOptions};
use std::io::{BufWriter, Read, Write};
use std::os::unix::fs::FileExt;
use std::path::Path;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, Instant};

const MIN_PARALLEL_BYTES: u64 = 4 * 1024 * 1024;
const MIN_CHUNK_BYTES: u64 = 1024 * 1024;
const MAX_CHUNK_BYTES: u64 = 4 * 1024 * 1024;
const WORKER_COUNT: usize = 4;
const IO_BUF_BYTES: usize = 64 * 1024;
const MAX_RETRIES: usize = 3;

#[derive(Clone, Copy, Debug)]
pub struct DownloadProgress {
    pub downloaded_bytes: u64,
    pub total_bytes: Option<u64>,
    pub eta_seconds: Option<i64>,
}

fn build_agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .timeout_connect(Duration::from_secs(15))
        .timeout_read(Duration::from_secs(30))
        .redirects(10)
        .build()
}

fn apply_headers(mut req: ureq::Request, headers: &[(String, String)]) -> ureq::Request {
    for (k, v) in headers {
        if !k.eq_ignore_ascii_case("range") {
            req = req.set(k, v);
        }
    }
    req
}

fn parse_content_range_total(header: &str) -> Option<u64> {
    let (_, total_part) = header.split_once('/')?;
    let trimmed = total_part.trim();
    if trimmed == "*" {
        return None;
    }
    trimmed.parse::<u64>().ok()
}

fn compute_chunk_size(total: u64) -> u64 {
    let target = (total / 24).max(MIN_CHUNK_BYTES).min(MAX_CHUNK_BYTES);
    target
}

struct ProgressReporter<'a, F: Fn(DownloadProgress) + Send + Sync> {
    downloaded: AtomicU64,
    total: Option<u64>,
    start: Instant,
    last_emit: Mutex<Instant>,
    callback: &'a F,
}

impl<'a, F: Fn(DownloadProgress) + Send + Sync> ProgressReporter<'a, F> {
    fn new(total: Option<u64>, callback: &'a F) -> Self {
        let now = Instant::now();
        Self {
            downloaded: AtomicU64::new(0),
            total,
            start: now,
            last_emit: Mutex::new(now - Duration::from_secs(1)),
            callback,
        }
    }

    fn add(&self, bytes: u64, force: bool) {
        let current = self.downloaded.fetch_add(bytes, Ordering::Relaxed) + bytes;
        let should_emit = if force {
            true
        } else if let Ok(mut guard) = self.last_emit.try_lock() {
            if guard.elapsed() >= Duration::from_millis(250) {
                *guard = Instant::now();
                true
            } else {
                false
            }
        } else {
            false
        };

        if should_emit {
            let eta = self.estimate_eta(current);
            (self.callback)(DownloadProgress {
                downloaded_bytes: current,
                total_bytes: self.total,
                eta_seconds: eta,
            });
        }
    }

    fn estimate_eta(&self, current: u64) -> Option<i64> {
        let total = self.total?;
        if current >= total {
            return Some(0);
        }
        let elapsed = self.start.elapsed().as_secs_f64();
        if elapsed < 0.2 || current == 0 {
            return None;
        }
        let rate = (current as f64) / elapsed;
        if rate <= 1.0 {
            return None;
        }
        let remaining = (total - current) as f64;
        Some((remaining / rate).round() as i64)
    }
}

fn check_cancelled(cancel: &AtomicBool) -> Result<(), String> {
    if cancel.load(Ordering::Relaxed) {
        Err("Download canceled".to_string())
    } else {
        Ok(())
    }
}

fn is_retryable_status(code: u16) -> bool {
    code == 429 || code == 408 || (500..600).contains(&code)
}

fn stream_reader_sequential<R: Read, F: Fn(DownloadProgress) + Send + Sync>(
    mut reader: R,
    file: File,
    reporter: &ProgressReporter<F>,
    cancel: &AtomicBool,
) -> Result<u64, String> {
    let mut writer = BufWriter::with_capacity(IO_BUF_BYTES, file);
    let mut buf = vec![0u8; IO_BUF_BYTES];
    let mut written = 0u64;
    loop {
        check_cancelled(cancel)?;
        let n = reader
            .read(&mut buf)
            .map_err(|e| format!("Stream read failed: {e}"))?;
        if n == 0 {
            break;
        }
        writer
            .write_all(&buf[..n])
            .map_err(|e| format!("Disk write failed: {e}"))?;
        written += n as u64;
        reporter.add(n as u64, false);
    }
    writer.flush().map_err(|e| format!("Disk flush failed: {e}"))?;
    reporter.add(0, true);
    Ok(written)
}

fn download_chunk_with_retry<F: Fn(DownloadProgress) + Send + Sync>(
    agent: &ureq::Agent,
    url: &str,
    headers: &[(String, String)],
    file: &File,
    start: u64,
    end_inclusive: u64,
    reporter: &ProgressReporter<F>,
    cancel: &AtomicBool,
) -> Result<(), String> {
    let mut offset = start;
    let mut attempt = 0usize;
    let mut buf = vec![0u8; IO_BUF_BYTES];

    loop {
        check_cancelled(cancel)?;
        let range_val = format!("bytes={}-{}", offset, end_inclusive);
        let req = apply_headers(agent.get(url), headers).set("Range", &range_val);

        match req.call() {
            Ok(resp) => {
                let status = resp.status();
                if status != 206 && !(status == 200 && offset == 0) {
                    return Err(format!("Unexpected HTTP status {status} for range {range_val}"));
                }
                let mut reader = resp.into_reader();
                let mut chunk_err: Option<String> = None;
                while offset <= end_inclusive {
                    if cancel.load(Ordering::Relaxed) {
                        return Err("Download canceled".to_string());
                    }
                    let remaining = (end_inclusive - offset + 1) as usize;
                    let to_read = remaining.min(buf.len());
                    match reader.read(&mut buf[..to_read]) {
                        Ok(0) => {
                            chunk_err = Some(format!(
                                "Premature EOF at offset {offset} (expected up to {end_inclusive})"
                            ));
                            break;
                        }
                        Ok(n) => {
                            file.write_all_at(&buf[..n], offset)
                                .map_err(|e| format!("Disk write failed at {offset}: {e}"))?;
                            offset += n as u64;
                            reporter.add(n as u64, false);
                        }
                        Err(e) => {
                            chunk_err = Some(format!("Network read failed: {e}"));
                            break;
                        }
                    }
                }
                if chunk_err.is_none() {
                    return Ok(());
                }
                if attempt >= MAX_RETRIES {
                    return Err(chunk_err.unwrap());
                }
            }
            Err(ureq::Error::Status(code, _)) => {
                if !is_retryable_status(code) || attempt >= MAX_RETRIES {
                    return Err(format!("HTTP {code}"));
                }
            }
            Err(ureq::Error::Transport(t)) => {
                if attempt >= MAX_RETRIES {
                    return Err(format!("Network transport error: {t}"));
                }
            }
        }

        attempt += 1;
        let backoff_ms = 300 * (1u64 << (attempt - 1));
        for _ in 0..(backoff_ms / 50) {
            check_cancelled(cancel)?;
            thread::sleep(Duration::from_millis(50));
        }
    }
}

pub fn download_file<F>(
    url: &str,
    headers: &[(String, String)],
    dest_path: &Path,
    cancel: &AtomicBool,
    on_progress: F,
) -> Result<u64, String>
where
    F: Fn(DownloadProgress) + Send + Sync,
{
    check_cancelled(cancel)?;
    let agent = build_agent();

    let probe_req = apply_headers(agent.get(url), headers).set("Range", "bytes=0-0");
    let probe_resp = match probe_req.call() {
        Ok(resp) => resp,
        Err(ureq::Error::Status(416, _)) => {
            // Some servers reject Range 0-0 on empty/non-rangeable objects; fall back to plain GET.
            apply_headers(agent.get(url), headers)
                .call()
                .map_err(|e| format!("Download request failed: {e}"))?
        }
        Err(ureq::Error::Status(code, _)) => return Err(format!("HTTP {code}")),
        Err(ureq::Error::Transport(t)) => return Err(format!("Network error: {t}")),
    };

    let status = probe_resp.status();
    if status == 206 {
        let total = probe_resp
            .header("content-range")
            .and_then(parse_content_range_total);
        if let Some(total_len) = total {
            if total_len == 0 {
                File::create(dest_path).map_err(|e| format!("Cannot create file: {e}"))?;
                return Ok(0);
            }
            let mut first_byte = [0u8; 1];
            let got = probe_resp
                .into_reader()
                .read(&mut first_byte)
                .map_err(|e| format!("Probe read failed: {e}"))?;
            if got == 0 {
                return Err("Empty 206 probe response".to_string());
            }

            let file = OpenOptions::new()
                .create(true)
                .write(true)
                .read(true)
                .truncate(true)
                .open(dest_path)
                .map_err(|e| format!("Cannot open destination file: {e}"))?;
            file.set_len(total_len)
                .map_err(|e| format!("Cannot allocate destination file: {e}"))?;
            file.write_all_at(&first_byte[..1], 0)
                .map_err(|e| format!("Cannot write initial byte: {e}"))?;

            let reporter = ProgressReporter::new(Some(total_len), &on_progress);
            reporter.add(1, true);

            if total_len == 1 {
                file.sync_data()
                    .map_err(|e| format!("Disk sync failed: {e}"))?;
                return Ok(1);
            }

            if total_len >= MIN_PARALLEL_BYTES {
                let chunk_size = compute_chunk_size(total_len);
                let mut chunks = Vec::new();
                let mut cursor = 1u64;
                while cursor < total_len {
                    let end = (cursor + chunk_size - 1).min(total_len - 1);
                    chunks.push((cursor, end));
                    cursor = end + 1;
                }

                let next_idx = AtomicUsize::new(0);
                let first_error: Mutex<Option<String>> = Mutex::new(None);
                let workers = WORKER_COUNT.min(chunks.len());

                thread::scope(|s| {
                    for _ in 0..workers {
                        s.spawn(|| loop {
                            if cancel.load(Ordering::Relaxed) {
                                break;
                            }
                            if first_error.lock().map(|g| g.is_some()).unwrap_or(true) {
                                break;
                            }
                            let idx = next_idx.fetch_add(1, Ordering::Relaxed);
                            if idx >= chunks.len() {
                                break;
                            }
                            let (c_start, c_end) = chunks[idx];
                            if let Err(err) = download_chunk_with_retry(
                                &agent,
                                url,
                                headers,
                                &file,
                                c_start,
                                c_end,
                                &reporter,
                                cancel,
                            ) {
                                if let Ok(mut guard) = first_error.lock() {
                                    if guard.is_none() {
                                        *guard = Some(err);
                                    }
                                }
                                break;
                            }
                        });
                    }
                });

                if let Some(err) = first_error.into_inner().unwrap_or(None) {
                    return Err(err);
                }
                check_cancelled(cancel)?;
            } else {
                download_chunk_with_retry(
                    &agent,
                    url,
                    headers,
                    &file,
                    1,
                    total_len - 1,
                    &reporter,
                    cancel,
                )?;
            }

            reporter.add(0, true);
            file.sync_data()
                .map_err(|e| format!("Disk sync failed: {e}"))?;
            return Ok(total_len);
        }
    }

    // Fallback: 200 OK (no Range support or unknown total from Content-Range).
    let total = probe_resp
        .header("content-length")
        .and_then(|v| v.trim().parse::<u64>().ok());
    let file = File::create(dest_path).map_err(|e| format!("Cannot create file: {e}"))?;
    let reporter = ProgressReporter::new(total, &on_progress);
    let written = if status == 200 {
        stream_reader_sequential(probe_resp.into_reader(), file, &reporter, cancel)?
    } else {
        let resp = apply_headers(agent.get(url), headers)
            .call()
            .map_err(|e| format!("Download request failed: {e}"))?;
        stream_reader_sequential(resp.into_reader(), file, &reporter, cancel)?
    };
    Ok(written)
}
