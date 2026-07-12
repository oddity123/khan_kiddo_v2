#!/usr/bin/env bash
# 一键打包并上传到服务器（仅上传，不自动重启；请在宝塔面板手动重启）
#
# 首次：
#   cp deploy.env.example deploy.env   # 填写主机与远端路径
#   ssh-copy-id root@你的公网IP
#
# 之后：
#   ./deploy.sh                 # 打包 + 上传前端 zip / 后端 jar
#   ./deploy.sh --skip-build    # 只上传已有产物
#   ./deploy.sh --frontend-only
#   ./deploy.sh --backend-only

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

DO_FRONTEND=1
DO_BACKEND=1
SKIP_BUILD=0

usage() {
  cat <<'EOF'
用法: ./deploy.sh [选项]

选项:
  --skip-build      不跑 ./package.sh，直接上传已有产物
  --frontend-only   只上传前端 dist.zip
  --backend-only    只上传后端 jar
  -h, --help        显示帮助

说明: 本脚本只负责上传；后端请在宝塔「Java项目」里手动重启。
配置文件: ./deploy.env（从 deploy.env.example 复制）
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1 ;;
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
die() { printf '✗ %s\n' "$*" >&2; exit 1; }

ENV_FILE="$ROOT/deploy.env"
[[ -f "$ENV_FILE" ]] || die "缺少 deploy.env。请先: cp deploy.env.example deploy.env 并填写"

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

: "${DEPLOY_HOST:?请在 deploy.env 中设置 DEPLOY_HOST}"
: "${DEPLOY_USER:?请在 deploy.env 中设置 DEPLOY_USER}"
: "${DEPLOY_FRONTEND_DIR:?请在 deploy.env 中设置 DEPLOY_FRONTEND_DIR}"
: "${DEPLOY_BACKEND_DIR:?请在 deploy.env 中设置 DEPLOY_BACKEND_DIR}"

DEPLOY_PORT="${DEPLOY_PORT:-22}"
DEPLOY_BUILD_FIRST="${DEPLOY_BUILD_FIRST:-1}"
DEPLOY_BACKEND_JAR="${DEPLOY_BACKEND_JAR:-}"

if [[ "$SKIP_BUILD" -eq 1 ]]; then
  DEPLOY_BUILD_FIRST=0
fi

SSH_OPTS=(-p "$DEPLOY_PORT" -o StrictHostKeyChecking=accept-new)
SCP_OPTS=(-P "$DEPLOY_PORT" -o StrictHostKeyChecking=accept-new)
if [[ -n "${DEPLOY_SSH_KEY:-}" ]]; then
  SSH_OPTS+=(-i "$DEPLOY_SSH_KEY")
  SCP_OPTS+=(-i "$DEPLOY_SSH_KEY")
fi

REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"

ssh_run() {
  ssh "${SSH_OPTS[@]}" "$REMOTE" "$@"
}

if [[ "$DEPLOY_BUILD_FIRST" -eq 1 ]]; then
  log "本地打包…"
  PACKAGE_ARGS=()
  if [[ "$DO_FRONTEND" -eq 1 && "$DO_BACKEND" -eq 0 ]]; then
    PACKAGE_ARGS+=(--frontend-only)
  elif [[ "$DO_FRONTEND" -eq 0 && "$DO_BACKEND" -eq 1 ]]; then
    PACKAGE_ARGS+=(--backend-only)
  fi
  "$ROOT/package.sh" "${PACKAGE_ARGS[@]}"
fi

ZIP_PATH="$ROOT/frontend/dist.zip"
JAR_PATH=""
if [[ "$DO_BACKEND" -eq 1 ]]; then
  JAR_PATH="$(ls -1 "$ROOT/backend/target"/khankiddo-v2-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
  [[ -n "$JAR_PATH" ]] || die "未找到后端 jar，请先 ./package.sh"
fi
if [[ "$DO_FRONTEND" -eq 1 ]]; then
  [[ -f "$ZIP_PATH" ]] || die "未找到 frontend/dist.zip，请先 ./package.sh"
fi

log "创建远端目录…"
mkdir_args=("$DEPLOY_FRONTEND_DIR" "$DEPLOY_BACKEND_DIR")
[[ -n "$DEPLOY_BACKEND_JAR" ]] && mkdir_args+=("$(dirname "$DEPLOY_BACKEND_JAR")")
ssh_run "mkdir -p $(printf '%q ' "${mkdir_args[@]}")"

if [[ "$DO_FRONTEND" -eq 1 ]]; then
  log "上传前端 dist.zip → ${REMOTE}:${DEPLOY_FRONTEND_DIR}/"
  scp "${SCP_OPTS[@]}" "$ZIP_PATH" "${REMOTE}:${DEPLOY_FRONTEND_DIR}/dist.zip"
  log "远端解压前端（覆盖静态资源）…"
  ssh_run "set -euo pipefail
    cd $(printf '%q' "$DEPLOY_FRONTEND_DIR")
    if [[ -f index.html ]]; then
      rm -rf .dist_bak_prev
      mkdir -p .dist_bak_prev
      find . -maxdepth 1 -mindepth 1 ! -name dist.zip ! -name .dist_bak_prev -exec mv {} .dist_bak_prev/ \\;
    fi
    unzip -o -q dist.zip
    rm -f dist.zip
    echo 'frontend deployed'
  "
  ok "前端已部署到 ${DEPLOY_FRONTEND_DIR}"
fi

if [[ "$DO_BACKEND" -eq 1 ]]; then
  if [[ -n "$DEPLOY_BACKEND_JAR" ]]; then
    REMOTE_JAR="$DEPLOY_BACKEND_JAR"
    log "上传后端 jar → ${REMOTE}:${REMOTE_JAR}"
    ssh_run "mkdir -p $(printf '%q' "$(dirname "$REMOTE_JAR")")"
    scp "${SCP_OPTS[@]}" "$JAR_PATH" "${REMOTE}:${REMOTE_JAR}"
    ok "后端 jar 已覆盖: ${REMOTE_JAR}"
  else
    JAR_NAME="$(basename "$JAR_PATH")"
    log "上传后端 ${JAR_NAME} → ${REMOTE}:${DEPLOY_BACKEND_DIR}/"
    scp "${SCP_OPTS[@]}" "$JAR_PATH" "${REMOTE}:${DEPLOY_BACKEND_DIR}/${JAR_NAME}"
    ok "后端 jar 已上传: ${DEPLOY_BACKEND_DIR}/${JAR_NAME}"
  fi
fi

echo
ok "上传完成 → ${REMOTE}"
log "请到宝塔「Java项目 → khankiddo-v2」手动重启后端"
exit 0
