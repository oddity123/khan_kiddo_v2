#!/usr/bin/env bash
# 本机一键部署到宝塔：打包 → 上传 → java-service stop + start
#
# 用法：
#   cp deploy.env.example deploy.env   # 填写必填项
#   ./deploy.sh                        # 完整流程
#   ./deploy.sh --check-deps           # 只检测本机依赖，不安装、不部署
#   ./deploy.sh --skip-build           # 跳过打包，复用已有产物
#   ./deploy.sh --dry-run              # 打印将执行的命令，不实际上传/重启
#
# 依赖缺失时仅报错退出，不会自动安装任何软件。

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

SKIP_BUILD=0
DRY_RUN=0
CHECK_DEPS_ONLY=0

usage() {
  cat <<'EOF'
用法: ./deploy.sh [选项]

选项:
  --check-deps    只检测本机命令依赖（不安装、不部署）
  --skip-build    跳过 ./package.sh，使用已有 jar / dist.zip
  --dry-run       只打印远程命令，不实际上传与重启
  -h, --help      显示帮助

配置:
  复制 deploy.env.example → deploy.env，把 CHANGEME 改成真实值。
  应用运行环境变量（JWT/DB/API Key）在宝塔面板配置，不在本脚本。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-deps) CHECK_DEPS_ONLY=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --dry-run) DRY_RUN=1 ;;
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
warn() { printf '! %s\n' "$*" >&2; }
fail() { printf '✗ %s\n' "$*" >&2; exit 1; }

# ---------- 依赖检测（只检测，不安装）----------

# 必选：上传与远程；打包相关在未 --skip-build 时检查
REQUIRED_ALWAYS=(ssh scp curl)
REQUIRED_RSYNC=(rsync)
REQUIRED_BUILD=(npm node zip)
# java / mvn 由 package.sh / mvn.sh 处理，此处仅作提示
OPTIONAL=(sshpass)

check_cmd() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    printf 'OK   %s  (%s)\n' "$name" "$(command -v "$name")"
    return 0
  fi
  printf 'MISS %s\n' "$name"
  return 1
}

check_deps() {
  local missing=0
  local name

  log "检测本机依赖（不安装）"

  echo "--- 必选（SSH / 上传 / 探测）---"
  for name in "${REQUIRED_ALWAYS[@]}"; do
    check_cmd "$name" || missing=1
  done

  echo "--- 上传（rsync 推荐；也可用 UPLOAD_METHOD=scp）---"
  if ! check_cmd rsync; then
    warn "未找到 rsync：请改用 UPLOAD_METHOD=scp，或自行安装 rsync"
  fi
  check_cmd scp || missing=1

  if [[ "$SKIP_BUILD" -eq 0 && "$CHECK_DEPS_ONLY" -eq 0 ]] || [[ "$CHECK_DEPS_ONLY" -eq 1 ]]; then
    echo "--- 打包（package.sh；--skip-build 时可缺）---"
    for name in "${REQUIRED_BUILD[@]}"; do
      check_cmd "$name" || missing=1
    done
    if check_cmd java; then
      java -version 2>&1 | sed 's/^/     /' | head -n 1 || true
      warn "PATH 上的 java 可能是 8；正式打包请用仓库 ./mvn.sh（Java 21）"
    else
      missing=1
    fi
    check_cmd mvn || warn "未找到系统 mvn：若存在 ./mvn.sh 仍可打包"
  fi

  echo "--- 可选 ---"
  for name in "${OPTIONAL[@]}"; do
    if command -v "$name" >/dev/null 2>&1; then
      printf 'OK   %s  (%s)\n' "$name" "$(command -v "$name")"
      ok "sshpass 可用（仍更推荐 SSH 密钥）"
    else
      printf 'SKIP %s  （密码登录才需要；推荐密钥）\n' "$name"
    fi
  done

  if [[ -f "$HOME/.ssh/id_ed25519.pub" || -f "$HOME/.ssh/id_rsa.pub" ]]; then
    ok "检测到本机 SSH 公钥"
  else
    warn "未检测到 ~/.ssh/id_ed25519.pub 或 id_rsa.pub — 请先配置免密或确保 ssh-agent/config 可用"
  fi

  if [[ "$missing" -ne 0 ]]; then
    fail "存在缺失依赖（上方 MISS）。请自行安装后重试；本脚本不会自动安装。"
  fi
  ok "依赖检测通过"
}

# ---------- 配置加载 ----------

load_config() {
  local example="$ROOT/deploy.env.example"
  local conf="$ROOT/deploy.env"

  if [[ ! -f "$conf" ]]; then
    fail "缺少 deploy.env。请执行: cp deploy.env.example deploy.env 并填写 CHANGEME"
  fi
  # shellcheck disable=SC1090
  set -a
  # shellcheck disable=SC1091
  source "$conf"
  set +a

  local key
  for key in SSH_HOST SSH_USER BT_JAVA_PROJECT REMOTE_JAR_DIR REMOTE_JAR_NAME REMOTE_WEB_DIR; do
    if [[ -z "${!key:-}" ]]; then
      fail "配置项 $key 为空，请编辑 deploy.env"
    fi
    if [[ "${!key}" == *CHANGEME* ]]; then
      fail "配置项 $key 仍为 CHANGEME，请编辑 deploy.env 填写真实值"
    fi
  done

  SSH_PORT="${SSH_PORT:-22}"
  # 宝塔机常无 rsync，默认 scp（两端只需 ssh/scp）
  UPLOAD_METHOD="${UPLOAD_METHOD:-scp}"
  HEALTH_URL="${HEALTH_URL:-}"
  SSH_KEY="${SSH_KEY:-}"
}

ssh_base_opts() {
  local opts=(-p "$SSH_PORT" -o StrictHostKeyChecking=accept-new)
  if [[ -n "$SSH_KEY" ]]; then
    # 展开 ~
    local key_path="${SSH_KEY/#\~/$HOME}"
    opts+=(-i "$key_path")
  fi
  printf '%s\n' "${opts[@]}"
}

remote_ssh() {
  local -a opts
  # shellcheck disable=SC2207
  opts=($(ssh_base_opts))
  if [[ "$DRY_RUN" -eq 1 ]]; then
    printf '[dry-run] ssh %s %s@%s %q\n' "${opts[*]}" "$SSH_USER" "$SSH_HOST" "$*"
    return 0
  fi
  ssh "${opts[@]}" "$SSH_USER@$SSH_HOST" "$@"
}

# ---------- 产物 ----------

find_local_jar() {
  local jar
  jar="$(ls -1 "$ROOT/backend/target"/khankiddo-v2-*.jar 2>/dev/null | grep -v '\.original$' | head -n1 || true)"
  [[ -n "$jar" ]] || fail "未找到 backend/target/khankiddo-v2-*.jar，请先打包或去掉 --skip-build"
  echo "$jar"
}

# ---------- 上传 ----------

resolve_upload_method() {
  if [[ "$UPLOAD_METHOD" != "rsync" ]]; then
    UPLOAD_METHOD=scp
    return 0
  fi
  if ! command -v rsync >/dev/null 2>&1; then
    warn "本机无 rsync，改用 scp"
    UPLOAD_METHOD=scp
    return 0
  fi
  # rsync 需要远端也有 rsync；宝塔精简机常缺失
  if [[ "$DRY_RUN" -eq 1 ]]; then
    return 0
  fi
  if ! remote_ssh "command -v rsync >/dev/null 2>&1"; then
    warn "服务器无 rsync，改用 scp"
    UPLOAD_METHOD=scp
  fi
}

upload_file() {
  local local_path="$1"
  local remote_path="$2"
  local -a ssh_opts scp_opts
  # shellcheck disable=SC2207
  ssh_opts=($(ssh_base_opts))
  scp_opts=(-P "$SSH_PORT")
  if [[ -n "$SSH_KEY" ]]; then
    local key_path="${SSH_KEY/#\~/$HOME}"
    scp_opts+=(-i "$key_path")
  fi

  if [[ "$UPLOAD_METHOD" == "rsync" ]]; then
    rsync -az --progress -e "ssh ${ssh_opts[*]}" \
      "$local_path" "$SSH_USER@$SSH_HOST:$remote_path"
  else
    scp "${scp_opts[@]}" "$local_path" "$SSH_USER@$SSH_HOST:$remote_path"
  fi
}

upload_artifacts() {
  local jar="$1"
  local dist_zip="$ROOT/frontend/dist.zip"
  [[ -f "$dist_zip" ]] || fail "未找到 frontend/dist.zip，请先打包或去掉 --skip-build"

  resolve_upload_method
  ok "上传方式: $UPLOAD_METHOD"

  log "确保远程目录存在"
  remote_ssh "mkdir -p $(printf %q "$REMOTE_JAR_DIR") $(printf %q "$REMOTE_WEB_DIR")"

  log "上传后端 jar → ${SSH_USER}@${SSH_HOST}:${REMOTE_JAR_DIR}/${REMOTE_JAR_NAME}"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "[dry-run] upload $jar → $REMOTE_JAR_DIR/$REMOTE_JAR_NAME"
  else
    upload_file "$jar" "$REMOTE_JAR_DIR/$REMOTE_JAR_NAME"
  fi
  ok "后端已上传"

  log "上传并解压前端 → ${REMOTE_WEB_DIR}"
  local remote_zip="$REMOTE_WEB_DIR/.kk-frontend-dist.zip"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "[dry-run] upload $dist_zip → $remote_zip && unzip -o"
  else
    upload_file "$dist_zip" "$remote_zip"
    remote_ssh "command -v unzip >/dev/null 2>&1 || { echo '服务器未找到 unzip' >&2; exit 1; }; cd $(printf %q "$REMOTE_WEB_DIR") && unzip -o $(printf %q "$remote_zip") && rm -f $(printf %q "$remote_zip")"
  fi
  ok "前端已更新"
}

restart_remote() {
  # restart 偶发「未获取到Pid」；拆成 stop → 短暂等待 → start 更稳
  local project_q
  project_q="$(printf %q "$BT_JAVA_PROJECT")"
  log "远程停止并启动宝塔 Java 项目: $BT_JAVA_PROJECT"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "[dry-run] java-service $BT_JAVA_PROJECT stop && sleep 2 && java-service $BT_JAVA_PROJECT start"
    return 0
  fi
  remote_ssh "command -v java-service >/dev/null || { echo '未找到 java-service，请检查宝塔 Java 项目管理 / 系统加固' >&2; exit 1; }; java-service ${project_q} stop; sleep 2; java-service ${project_q} start"
  ok "已请求 stop + start"
}

health_check() {
  [[ -n "$HEALTH_URL" ]] || { warn "未配置 HEALTH_URL，跳过健康检查"; return 0; }
  log "健康检查: $HEALTH_URL"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "[dry-run] curl -fsS $HEALTH_URL"
    return 0
  fi
  local i
  for i in 1 2 3 4 5 6; do
    if curl -fsS --max-time 5 "$HEALTH_URL" >/dev/null; then
      ok "健康检查通过"
      return 0
    fi
    sleep 2
  done
  fail "健康检查失败: $HEALTH_URL"
}

# ---------- main ----------

check_deps
if [[ "$CHECK_DEPS_ONLY" -eq 1 ]]; then
  exit 0
fi

load_config

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  log "本地打包"
  "$ROOT/package.sh"
else
  log "跳过打包（--skip-build）"
fi

LOCAL_JAR="$(find_local_jar)"
ok "本地 jar: ${LOCAL_JAR#"$ROOT/"}"

upload_artifacts "$LOCAL_JAR"
restart_remote
health_check

log "部署完成"
ok "后端: $REMOTE_JAR_DIR/$REMOTE_JAR_NAME"
ok "前端: $REMOTE_WEB_DIR"
ok "项目: java-service $BT_JAVA_PROJECT stop + start"
exit 0
