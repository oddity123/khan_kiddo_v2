#!/usr/bin/env bash
# 同时打包前端（Vite）与后端（Spring Boot jar）
# 用法：
#   ./package.sh              # 并行打包（默认跳过后端测试）
#   ./package.sh --sequential # 先后端再前端
#   ./package.sh --with-tests # 后端跑测试
#   ./package.sh --frontend-only | --backend-only

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

SKIP_TESTS=1
PARALLEL=1
DO_FRONTEND=1
DO_BACKEND=1

usage() {
  cat <<'EOF'
用法: ./package.sh [选项]

选项:
  --sequential      串行打包（先前端后后端）；默认并行
  --with-tests      后端执行测试（默认 -DskipTests）
  --frontend-only   只打包前端
  --backend-only    只打包后端
  -h, --help        显示帮助

产物:
  前端  frontend/dist/  与  frontend/dist.zip（可直接上传服务器解压）
  后端  backend/target/khankiddo-v2-*.jar
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --sequential) PARALLEL=0 ;;
    --with-tests) SKIP_TESTS=0 ;;
    --frontend-only) DO_BACKEND=0; DO_FRONTEND=1 ;;
    --backend-only) DO_FRONTEND=0; DO_BACKEND=1 ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "未知参数: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

log() { printf '\n==> %s\n' "$*"; }
ok() { printf '✓ %s\n' "$*"; }
fail() { printf '✗ %s\n' "$*" >&2; }

run_mvn() {
  local mac_jdk="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
  if [[ -x "$ROOT/mvn.sh" && -d "$mac_jdk" ]]; then
    "$ROOT/mvn.sh" "$@"
  else
    # Linux / 非 mac Temurin 路径：直接用系统 mvn + 当前 JAVA_HOME
    mvn -f "$ROOT/backend/pom.xml" "$@"
  fi
}

build_frontend() {
  log "打包前端 (frontend/)"
  if [[ ! -d "$ROOT/frontend/node_modules" ]]; then
    log "安装前端依赖…"
    (cd "$ROOT/frontend" && npm install)
  fi
  (cd "$ROOT/frontend" && npm run build)

  local dist_dir="$ROOT/frontend/dist"
  local zip_path="$ROOT/frontend/dist.zip"
  if [[ ! -d "$dist_dir" ]]; then
    fail "未找到前端目录 frontend/dist/"
    return 1
  fi
  log "压缩前端产物 → frontend/dist.zip"
  rm -f "$zip_path"
  # 压缩 dist 内文件（解压后直接是静态资源，无多余 dist/ 一层）
  (cd "$dist_dir" && zip -r -q "$zip_path" .)
  ok "前端产物: frontend/dist/"
  ok "前端压缩包: frontend/dist.zip ($(du -h "$zip_path" | awk '{print $1}'))"
}

build_backend() {
  log "打包后端 (backend/)"
  local args=(-q package)
  if [[ "$SKIP_TESTS" -eq 1 ]]; then
    args+=(-DskipTests)
  fi
  run_mvn "${args[@]}"
  local jar
  jar="$(ls -1 "$ROOT/backend/target"/khankiddo-v2-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
  if [[ -z "$jar" ]]; then
    fail "未找到后端 jar"
    return 1
  fi
  ok "后端产物: ${jar#"$ROOT/"}"
}

FRONTEND_STATUS=0
BACKEND_STATUS=0
FRONTEND_LOG=""
BACKEND_LOG=""

cleanup_logs() {
  [[ -n "$FRONTEND_LOG" && -f "$FRONTEND_LOG" ]] && rm -f "$FRONTEND_LOG"
  [[ -n "$BACKEND_LOG" && -f "$BACKEND_LOG" ]] && rm -f "$BACKEND_LOG"
}
trap cleanup_logs EXIT

if [[ "$PARALLEL" -eq 1 && "$DO_FRONTEND" -eq 1 && "$DO_BACKEND" -eq 1 ]]; then
  log "并行打包前后端…"
  FRONTEND_LOG="$(mktemp "${TMPDIR:-/tmp}/kk-fe.XXXXXX.log")"
  BACKEND_LOG="$(mktemp "${TMPDIR:-/tmp}/kk-be.XXXXXX.log")"

  (build_frontend >"$FRONTEND_LOG" 2>&1) &
  FE_PID=$!
  (build_backend >"$BACKEND_LOG" 2>&1) &
  BE_PID=$!

  wait "$FE_PID" || FRONTEND_STATUS=$?
  wait "$BE_PID" || BACKEND_STATUS=$?

  echo
  echo "----- 前端日志 -----"
  cat "$FRONTEND_LOG"
  echo "----- 后端日志 -----"
  cat "$BACKEND_LOG"
else
  if [[ "$DO_FRONTEND" -eq 1 ]]; then
    build_frontend || FRONTEND_STATUS=$?
  fi
  if [[ "$DO_BACKEND" -eq 1 ]]; then
    build_backend || BACKEND_STATUS=$?
  fi
fi

echo
echo "======== 打包结果 ========"
[[ "$DO_FRONTEND" -eq 1 ]] && {
  if [[ "$FRONTEND_STATUS" -eq 0 ]]; then ok "前端成功"; else fail "前端失败 (exit $FRONTEND_STATUS)"; fi
}
[[ "$DO_BACKEND" -eq 1 ]] && {
  if [[ "$BACKEND_STATUS" -eq 0 ]]; then ok "后端成功"; else fail "后端失败 (exit $BACKEND_STATUS)"; fi
}

if [[ "$FRONTEND_STATUS" -ne 0 || "$BACKEND_STATUS" -ne 0 ]]; then
  exit 1
fi

log "全部完成"
exit 0
