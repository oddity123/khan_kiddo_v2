#!/usr/bin/env bash
# 下载 sherpa-onnx 原生库 jar 到 backend/lib/native/（不入库）。
# 存在时 Maven profile sherpa-native-* 自动激活并打进后端 jar；缺失时跟读打分接口返回 503。
#
# 用法：
#   ./scripts/setup-sherpa-onnx.sh           # 下载 linux-x64 + osx-aarch64
#   ./scripts/setup-sherpa-onnx.sh --model   # 额外下载英文识别模型到 ~/sherpa-models（本机开发用）

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="1.13.8"
BASE_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download"
NATIVE_DIR="$ROOT/backend/lib/native"
MODEL_NAME="sherpa-onnx-zipformer-gigaspeech-2023-12-12"
MODEL_ROOT="${SHERPA_MODEL_ROOT:-$HOME/sherpa-models}"

WITH_MODEL=0
if [[ "${1:-}" == "--model" ]]; then
  WITH_MODEL=1
fi

download() {
  local url="$1" dest="$2"
  if [[ -s "$dest" ]]; then
    echo "✓ 已存在 ${dest#"$ROOT/"}"
    return 0
  fi
  echo "==> 下载 $url"
  curl -fSL --retry 3 -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
}

mkdir -p "$NATIVE_DIR"
for platform in linux-x64 osx-aarch64; do
  jar="sherpa-onnx-native-lib-$platform-$VERSION.jar"
  download "$BASE_URL/v$VERSION/$jar" "$NATIVE_DIR/$jar"
done

if [[ "$WITH_MODEL" -eq 1 ]]; then
  mkdir -p "$MODEL_ROOT"
  if [[ -f "$MODEL_ROOT/$MODEL_NAME/tokens.txt" ]]; then
    echo "✓ 模型已存在 $MODEL_ROOT/$MODEL_NAME"
  else
    archive="$MODEL_ROOT/$MODEL_NAME.tar.bz2"
    download "$BASE_URL/asr-models/$MODEL_NAME.tar.bz2" "$archive"
    tar xjf "$archive" -C "$MODEL_ROOT"
    rm -f "$archive"
  fi
  echo "模型目录（SHADOWING_MODEL_DIR）：$MODEL_ROOT/$MODEL_NAME"
fi
