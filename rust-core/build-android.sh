#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUST_DIR="$ROOT_DIR/rust-core"
JNI_DIR="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
TARGET_DIR="${CARGO_TARGET_DIR:-$HOME/.cache/prismplayer-rust-target}"

mkdir -p "$JNI_DIR" "$TARGET_DIR"

CARGO_TARGET_DIR="$TARGET_DIR" cargo build \
    --manifest-path "$RUST_DIR/Cargo.toml" \
    --release \
    --target aarch64-linux-android

cp "$TARGET_DIR/aarch64-linux-android/release/libfluf_native.so" "$JNI_DIR/libfluf_native.so"
ls -lh "$JNI_DIR/libfluf_native.so"
