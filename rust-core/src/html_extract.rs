use memchr::memmem;
use serde_json::Value;

const INITIAL_DATA_MARKERS: &[&[u8]] = &[
    b"window[\"ytInitialData\"] = ",
    b"window['ytInitialData'] = ",
    b"var ytInitialData = ",
    b"ytInitialData = ",
];

fn skip_ws(bytes: &[u8], mut idx: usize) -> usize {
    while idx < bytes.len() && bytes[idx].is_ascii_whitespace() {
        idx += 1;
    }
    idx
}

/// Scans a balanced JSON object `{ ... }` starting at `start` (which must point to `{`),
/// respecting JSON string literals and backslash escapes.
pub fn scan_balanced_json_object(bytes: &[u8], start: usize) -> Option<&str> {
    if start >= bytes.len() || bytes[start] != b'{' {
        return None;
    }
    let mut depth = 0usize;
    let mut in_string = false;
    let mut escaped = false;
    let mut i = start;

    while i < bytes.len() {
        let b = bytes[i];
        if in_string {
            if escaped {
                escaped = false;
            } else if b == b'\\' {
                escaped = true;
            } else if b == b'"' {
                in_string = false;
            }
        } else {
            match b {
                b'"' => in_string = true,
                b'{' => depth += 1,
                b'}' => {
                    depth -= 1;
                    if depth == 0 {
                        return std::str::from_utf8(&bytes[start..=i]).ok();
                    }
                }
                _ => {}
            }
        }
        i += 1;
    }
    None
}

fn hex_val(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

/// Scans a JS single- or double-quoted string literal starting at `start` (`'` or `"`)
/// and unescapes `\xXX` hex bytes + standard JS escapes in a single pass.
fn scan_and_unescape_js_string(bytes: &[u8], start: usize) -> Option<String> {
    if start >= bytes.len() {
        return None;
    }
    let quote = bytes[start];
    if quote != b'\'' && quote != b'"' {
        return None;
    }

    let mut raw_out: Vec<u8> = Vec::with_capacity(4096);
    let mut i = start + 1;

    while i < bytes.len() {
        let b = bytes[i];
        if b == quote {
            return String::from_utf8(raw_out).ok();
        }
        if b == b'\\' && i + 1 < bytes.len() {
            let next = bytes[i + 1];
            match next {
                b'x' if i + 3 < bytes.len() => {
                    if let (Some(hi), Some(lo)) = (hex_val(bytes[i + 2]), hex_val(bytes[i + 3])) {
                        raw_out.push((hi << 4) | lo);
                        i += 4;
                        continue;
                    }
                    raw_out.push(b);
                    i += 1;
                }
                b'\\' | b'"' | b'\'' | b'/' => {
                    raw_out.push(next);
                    i += 2;
                }
                b'n' => {
                    raw_out.push(b'\n');
                    i += 2;
                }
                b'r' => {
                    raw_out.push(b'\r');
                    i += 2;
                }
                b't' => {
                    raw_out.push(b'\t');
                    i += 2;
                }
                b'u' if i + 5 < bytes.len() => {
                    if let (Some(a), Some(b_h), Some(c), Some(d)) = (
                        hex_val(bytes[i + 2]),
                        hex_val(bytes[i + 3]),
                        hex_val(bytes[i + 4]),
                        hex_val(bytes[i + 5]),
                    ) {
                        let code = ((a as u32) << 12) | ((b_h as u32) << 8) | ((c as u32) << 4) | (d as u32);
                        if let Some(ch) = char::from_u32(code) {
                            let mut utf8 = [0u8; 4];
                            raw_out.extend_from_slice(ch.encode_utf8(&mut utf8).as_bytes());
                            i += 6;
                            continue;
                        }
                    }
                    raw_out.push(b);
                    i += 1;
                }
                _ => {
                    raw_out.push(next);
                    i += 2;
                }
            }
        } else {
            raw_out.push(b);
            i += 1;
        }
    }
    None
}

pub fn extract_yt_initial_data_json(html: &str) -> Option<String> {
    let bytes = html.as_bytes();
    for marker in INITIAL_DATA_MARKERS {
        let mut search_from = 0usize;
        while let Some(pos) = memmem::find(&bytes[search_from..], marker) {
            let abs = search_from + pos + marker.len();
            let val_start = skip_ws(bytes, abs);
            if val_start < bytes.len() {
                if bytes[val_start] == b'{' {
                    if let Some(slice) = scan_balanced_json_object(bytes, val_start) {
                        return Some(slice.to_string());
                    }
                } else if bytes[val_start] == b'\'' || bytes[val_start] == b'"' {
                    if let Some(unescaped) = scan_and_unescape_js_string(bytes, val_start) {
                        if unescaped.trim_start().starts_with('{') {
                            return Some(unescaped);
                        }
                    }
                }
            }
            search_from = abs;
        }
    }
    None
}

pub struct ExtractedYtCfg {
    pub api_key: String,
    pub context_json: String,
}

pub fn extract_ytcfg(html: &str) -> Option<ExtractedYtCfg> {
    let bytes = html.as_bytes();
    let finder = memmem::Finder::new(b"ytcfg.set(");
    let mut search_from = 0usize;

    while let Some(pos) = finder.find(&bytes[search_from..]) {
        let after_paren = search_from + pos + b"ytcfg.set(".len();
        let obj_start = skip_ws(bytes, after_paren);
        if obj_start < bytes.len() && bytes[obj_start] == b'{' {
            if let Some(json_slice) = scan_balanced_json_object(bytes, obj_start) {
                if let Ok(Value::Object(map)) = serde_json::from_str::<Value>(json_slice) {
                    if let (Some(ctx), Some(Value::String(api_key))) =
                        (map.get("INNERTUBE_CONTEXT"), map.get("INNERTUBE_API_KEY"))
                    {
                        if let Ok(context_json) = serde_json::to_string(ctx) {
                            return Some(ExtractedYtCfg {
                                api_key: api_key.clone(),
                                context_json,
                            });
                        }
                    }
                }
            }
        }
        search_from = after_paren;
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn extracts_raw_and_escaped_yt_initial_data_and_ytcfg() {
        let html = r#"
        <html><head><script>
        ytcfg.set({"OTHER": 1});
        ytcfg.set( {"INNERTUBE_API_KEY":"AIzaTestKey","INNERTUBE_CONTEXT":{"client":{"clientName":"WEB"}}} );
        var ytInitialData = '\x7b\x22contents\x22:\x7b\x22text\x22:\x22a}b\x22\x7d\x7d';
        </script></html>
        "#;
        let cfg = extract_ytcfg(html).unwrap();
        assert_eq!(cfg.api_key, "AIzaTestKey");
        assert!(cfg.context_json.contains("\"clientName\":\"WEB\""));

        let data = extract_yt_initial_data_json(html).unwrap();
        let parsed: Value = serde_json::from_str(&data).unwrap();
        assert_eq!(parsed["contents"]["text"], "a}b");
    }

    #[test]
    fn extracts_unquoted_json_yt_initial_data_ignoring_braces_in_strings() {
        let html = r#"<script>window["ytInitialData"] = {"title":"hello };</script> world","ok":true};</script>"#;
        let data = extract_yt_initial_data_json(html).unwrap();
        let parsed: Value = serde_json::from_str(&data).unwrap();
        assert_eq!(parsed["title"], "hello };</script> world");
        assert_eq!(parsed["ok"], true);
    }
}
