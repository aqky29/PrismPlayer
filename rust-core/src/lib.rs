mod downloader;
mod html_extract;
mod live_chat;
mod mp4_muxer;

use jni::objects::{JClass, JObject, JObjectArray, JString, JValue};
use jni::sys::{jboolean, jbyteArray, jint, jlong, JNI_TRUE};
use jni::JNIEnv;
use std::fs::File;
use std::mem::ManuallyDrop;
use std::os::unix::io::FromRawFd;
use std::path::Path;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

fn throw_runtime(env: &mut JNIEnv, msg: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", msg);
}

fn read_jstring(env: &mut JNIEnv, s: &JString) -> Result<String, String> {
    env.get_string(s)
        .map(|js| js.into())
        .map_err(|e| format!("Invalid JNI string: {e}"))
}

fn clone_cancel_token(token: jlong) -> Arc<AtomicBool> {
    if token == 0 {
        return Arc::new(AtomicBool::new(false));
    }
    let raw = token as *const AtomicBool;
    let arc = unsafe { Arc::from_raw(raw) };
    let cloned = Arc::clone(&arc);
    let _ = Arc::into_raw(arc);
    cloned
}

fn read_header_pairs(env: &mut JNIEnv, arr: &JObjectArray) -> Result<Vec<(String, String)>, String> {
    if arr.is_null() {
        return Ok(Vec::new());
    }
    let len = env
        .get_array_length(arr)
        .map_err(|e| format!("Header array length failed: {e}"))? as usize;
    let mut out = Vec::with_capacity(len / 2);
    let mut i = 0;
    while i + 1 < len {
        let k_obj = env
            .get_object_array_element(arr, i as i32)
            .map_err(|e| format!("Header key read failed: {e}"))?;
        let v_obj = env
            .get_object_array_element(arr, (i + 1) as i32)
            .map_err(|e| format!("Header value read failed: {e}"))?;
        let k_jstr = JString::from(k_obj);
        let v_jstr = JString::from(v_obj);
        let k = read_jstring(env, &k_jstr)?;
        let v = read_jstring(env, &v_jstr)?;
        out.push((k, v));
        i += 2;
    }
    Ok(out)
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeVersion<'local>(
    env: JNIEnv<'local>,
    _cls: JClass<'local>,
) -> JString<'local> {
    env.new_string("fluf_native/0.1.0")
        .expect("Failed to create version string")
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeCreateCancelToken(
    _env: JNIEnv,
    _cls: JClass,
) -> jlong {
    let token = Arc::new(AtomicBool::new(false));
    Arc::into_raw(token) as jlong
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeCancel(
    _env: JNIEnv,
    _cls: JClass,
    token: jlong,
) {
    if token != 0 {
        let raw = token as *const AtomicBool;
        let arc = unsafe { Arc::from_raw(raw) };
        arc.store(true, Ordering::Relaxed);
        let _ = Arc::into_raw(arc);
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeDestroyCancelToken(
    _env: JNIEnv,
    _cls: JClass,
    token: jlong,
) {
    if token != 0 {
        let raw = token as *const AtomicBool;
        unsafe {
            drop(Arc::from_raw(raw));
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeDownloadFile<'local>(
    mut env: JNIEnv<'local>,
    _cls: JClass<'local>,
    url: JString<'local>,
    header_pairs: JObjectArray<'local>,
    dest_path: JString<'local>,
    cancel_token: jlong,
    listener: JObject<'local>,
) -> jlong {
    let url_str = match read_jstring(&mut env, &url) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };
    let dest_str = match read_jstring(&mut env, &dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };
    let headers = match read_header_pairs(&mut env, &header_pairs) {
        Ok(h) => h,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };

    let vm = if !listener.is_null() {
        env.get_java_vm().ok()
    } else {
        None
    };
    let global_listener = if !listener.is_null() {
        env.new_global_ref(&listener).ok()
    } else {
        None
    };

    let cancel = clone_cancel_token(cancel_token);
    let result = downloader::download_file(&url_str, &headers, Path::new(&dest_str), &cancel, |p| {
        if let (Some(ref jvm), Some(ref cb)) = (&vm, &global_listener) {
            if let Ok(mut guard) = jvm.attach_current_thread() {
                let _ = guard.call_method(
                    cb,
                    "onProgress",
                    "(JJJ)V",
                    &[
                        JValue::Long(p.downloaded_bytes as jlong),
                        JValue::Long(p.total_bytes.map(|v| v as jlong).unwrap_or(-1)),
                        JValue::Long(p.eta_seconds.unwrap_or(-1) as jlong),
                    ],
                );
                if guard.exception_check().unwrap_or(false) {
                    let _ = guard.exception_clear();
                }
            }
        }
    });

    match result {
        Ok(bytes) => bytes as jlong,
        Err(e) => {
            throw_runtime(&mut env, &e);
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeMuxMp4<'local>(
    mut env: JNIEnv<'local>,
    _cls: JClass<'local>,
    video_path: JString<'local>,
    audio_path: JString<'local>,
    dest_path: JString<'local>,
    cancel_token: jlong,
) -> jlong {
    let v_str = match read_jstring(&mut env, &video_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };
    let a_str = if audio_path.is_null() {
        None
    } else {
        match read_jstring(&mut env, &audio_path) {
            Ok(s) if !s.is_empty() => Some(s),
            Ok(_) => None,
            Err(e) => {
                throw_runtime(&mut env, &e);
                return -1;
            }
        }
    };
    let d_str = match read_jstring(&mut env, &dest_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };

    let cancel = clone_cancel_token(cancel_token);
    let res = (|| -> Result<u64, String> {
        let mut vf = File::open(&v_str).map_err(|e| format!("Open video failed: {e}"))?;
        let mut af = match a_str {
            Some(ref p) => Some(File::open(p).map_err(|e| format!("Open audio failed: {e}"))?),
            None => None,
        };
        let out = File::create(&d_str).map_err(|e| format!("Create output failed: {e}"))?;
        mp4_muxer::mux_mp4_to_writer(&mut vf, af.as_mut(), out, &cancel)
    })();

    match res {
        Ok(bytes) => bytes as jlong,
        Err(e) => {
            throw_runtime(&mut env, &e);
            -1
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeDownloader_nativeMuxMp4ToFd<'local>(
    mut env: JNIEnv<'local>,
    _cls: JClass<'local>,
    video_path: JString<'local>,
    audio_path: JString<'local>,
    dest_fd: jint,
    cancel_token: jlong,
) -> jlong {
    if dest_fd < 0 {
        throw_runtime(&mut env, "Invalid destination file descriptor");
        return -1;
    }
    let v_str = match read_jstring(&mut env, &video_path) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return -1;
        }
    };
    let a_str = if audio_path.is_null() {
        None
    } else {
        match read_jstring(&mut env, &audio_path) {
            Ok(s) if !s.is_empty() => Some(s),
            Ok(_) => None,
            Err(e) => {
                throw_runtime(&mut env, &e);
                return -1;
            }
        }
    };

    let cancel = clone_cancel_token(cancel_token);
    let res = (|| -> Result<u64, String> {
        let mut vf = File::open(&v_str).map_err(|e| format!("Open video failed: {e}"))?;
        let mut af = match a_str {
            Some(ref p) => Some(File::open(p).map_err(|e| format!("Open audio failed: {e}"))?),
            None => None,
        };
        // Kotlin owns the ParcelFileDescriptor lifecycle; ManuallyDrop prevents closing it twice.
        let out = ManuallyDrop::new(unsafe { File::from_raw_fd(dest_fd) });
        mp4_muxer::mux_mp4_to_writer(&mut vf, af.as_mut(), &*out, &cancel)
    })();

    match res {
        Ok(bytes) => bytes as jlong,
        Err(e) => {
            throw_runtime(&mut env, &e);
            -1
        }
    }
}

fn read_opt_jstring(env: &mut JNIEnv, s: &JString) -> Result<Option<String>, String> {
    if s.is_null() {
        Ok(None)
    } else {
        read_jstring(env, s).map(Some)
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeParsers_nativeExtractYtInitialData<'local>(
    mut env: JNIEnv<'local>,
    _cls: JClass<'local>,
    html: JString<'local>,
) -> JString<'local> {
    let html_str = match read_jstring(&mut env, &html) {
        Ok(s) => s,
        Err(_) => return JObject::null().into(),
    };
    match html_extract::extract_yt_initial_data_json(&html_str) {
        Some(json) => env.new_string(&json).unwrap_or_else(|_| JObject::null().into()),
        None => JObject::null().into(),
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeParsers_nativeParseLiveChatHtmlBootstrap<'local>(
    mut env: JNIEnv<'local>,
    _cls: JClass<'local>,
    html: JString<'local>,
    is_live: jboolean,
    top_chat: jboolean,
    fallback_author: JString<'local>,
    fallback_photo: JString<'local>,
) -> jbyteArray {
    let html_str = match read_jstring(&mut env, &html) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JObject::null().into_raw();
        }
    };
    let author = read_opt_jstring(&mut env, &fallback_author)
        .ok()
        .flatten()
        .unwrap_or_default();
    let photo = read_opt_jstring(&mut env, &fallback_photo).ok().flatten();

    let boot = live_chat::parse_html_bootstrap(
        &html_str,
        is_live == JNI_TRUE,
        top_chat == JNI_TRUE,
        &author,
        photo.as_deref(),
    );
    let encoded = live_chat::encode_bootstrap(&boot);
    match env.byte_array_from_slice(&encoded) {
        Ok(arr) => arr.into_raw(),
        Err(e) => {
            throw_runtime(&mut env, &format!("JNI byte array allocation failed: {e}"));
            JObject::null().into_raw()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_id_flufclip_player_NativeParsers_nativeParseLiveChatResponse<'local>(
    mut env: JNIEnv<'local>,
    _cls: JClass<'local>,
    json: JString<'local>,
    top_chat: jboolean,
    fallback_author: JString<'local>,
    fallback_photo: JString<'local>,
) -> jbyteArray {
    let json_str = match read_jstring(&mut env, &json) {
        Ok(s) => s,
        Err(e) => {
            throw_runtime(&mut env, &e);
            return JObject::null().into_raw();
        }
    };
    let author = read_opt_jstring(&mut env, &fallback_author)
        .ok()
        .flatten()
        .unwrap_or_default();
    let photo = read_opt_jstring(&mut env, &fallback_photo).ok().flatten();

    match live_chat::parse_live_chat_response(
        &json_str,
        &author,
        photo.as_deref(),
        top_chat == JNI_TRUE,
    ) {
        Ok(batch) => {
            let encoded = live_chat::encode_batch(&batch);
            match env.byte_array_from_slice(&encoded) {
                Ok(arr) => arr.into_raw(),
                Err(e) => {
                    throw_runtime(&mut env, &format!("JNI byte array allocation failed: {e}"));
                    JObject::null().into_raw()
                }
            }
        }
        Err(e) => {
            throw_runtime(&mut env, &e);
            JObject::null().into_raw()
        }
    }
}
