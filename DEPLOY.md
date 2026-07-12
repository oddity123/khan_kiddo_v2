# Khan Kiddo v2 最小部署说明

> 从 v1（`khan_kiddo`，Java 8 + Thymeleaf 单体，端口 8080）迁移到 v2（Java 21 + Vue3 SPA，端口 8081）。

## 1. 与 v1 的关键差异

| 项目 | v1（老项目） | v2（本项目） |
|------|-------------|-------------|
| Java | 8 | **21**（Temurin） |
| 端口 | 8080 | **8081**（`PORT` 可改） |
| 前端 | Thymeleaf 内嵌在 jar | **独立 Vue SPA**，需 `npm run build` |
| 认证 | 无 / Session | **JWT**，需注册登录 |
| AI Key | `AI_API_KEY` | **`DOUBAO_API_KEY`**（兼容回退 `AI_API_KEY`） |
| 数据库 | `khan_kiddo_dev` | 同名库可用，但需执行 v2 DDL（含 `users` 表） |

**可与 v1 同机共存**：v1 继续占 8080，v2 后端用 8081，Nginx 按路径或子域名分流。

---

## 2. 环境准备

- **JDK 21**（Temurin）
- **MySQL 8**（已有 v1 库可复用实例）
- **Node.js 18+**（仅构建前端时需要）

---

## 3. 数据库

在 MySQL 执行（首次部署）：

```bash
mysql -u root -p < backend/src/main/resources/sql/DDL.sql
```

v2 新增 **`users`** 表；`conversation_analysis` 等表结构与 v1 基本兼容。  
若 v1 已有数据且要保留，建议 **新建库**（如 `khan_kiddo_v2`）并在环境变量里改 `DB_URL`，避免混用。

---

## 4. 环境变量

复制模板并填写：

```bash
cp .env.example .env
# 编辑 .env
```

**相对 v1 必须新增/改名的变量：**

```bash
# 必填（生产，无默认值，缺任一项 prod 启动失败）
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=<至少32字符随机串>
DB_URL=jdbc:mysql://localhost:3306/khan_kiddo_dev?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_PASSWORD=<你的密码>
DOUBAO_API_KEY=<豆包 API Key>

# 端口（默认 8080）
PORT=8080

# 可选：通义千问
QWEN_API_KEY=
```

**v1 → v2 映射：**

| v1 | v2 |
|----|-----|
| `AI_API_KEY` | `DOUBAO_API_KEY`（或保留 `AI_API_KEY` 作 LangChain4j 回退） |
| `AI_BASE_URL` | `DOUBAO_BASE_URL` |
| `AI_MODEL` | `DOUBAO_MODEL_NAME` |
| `SERVER_PORT=8080` | `PORT=8081` |
| — | `JWT_SECRET`（新增，必填） |

启动前加载环境变量（Spring Boot **不会**自动读 `.env`）：

```bash
set -a && source .env && set +a
```

---

## 5. 构建与启动

### 一键打包（推荐）

```bash
./package.sh                 # 并行打前后端；前端另产出 frontend/dist.zip
./package.sh --frontend-only
./package.sh --backend-only
```

### 一键上传到阿里云 ECS（宝塔）

```bash
cp deploy.env.example deploy.env   # 填写公网 IP、SSH 用户、远端目录
ssh-copy-id root@你的公网IP
./deploy.sh                        # 本地打包 + 上传；后端请在宝塔面板手动重启
./deploy.sh --skip-build           # 只上传已有产物
```

配置见 `deploy.env.example`。脚本只负责上传/解压，**不自动重启** Java 进程。

### 后端

```bash
cd /path/to/khan_kiddo_v2
set -a && source .env && set +a
./mvn.sh -q package -DskipTests
java -jar backend/target/khankiddo-v2-3.0.0-SNAPSHOT.jar
```

验证：`curl http://localhost:8081/api/health`

### 前端

```bash
cd frontend
npm install
npm run build    # 产物在 frontend/dist/；./package.sh 会额外生成 dist.zip
```

生产构建时 API 与页面**同源**则 `VITE_API_BASE_URL` 留空；前后端分域则设为后端地址，例如 `https://api.example.com`。

---

## 6. Nginx 推荐配置（同源反代）

与 v1 不同，v2 需要同时托管静态前端 + 反代 API。示例（替换域名与路径）：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /path/to/khan_kiddo_v2/frontend/dist;
    index index.html;

    # API → 后端 8081
    location /api/ {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # SSE 对话分析需要
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 600s;
    }

    # Vue Router history 模式
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

若 v1 仍在同域名根路径运行，常见做法：

- v1：`your-domain.com/`（8080）
- v2：`v2.your-domain.com` 或 `your-domain.com/v2/`（需改 Vite `base` 为 `/v2/`）

---

## 7. 上线前检查清单

- [ ] `SPRING_PROFILES_ACTIVE=prod`（启用 `application-prod.yml`）
- [ ] `JWT_SECRET`、`DB_URL`、`DB_PASSWORD`、`DOUBAO_API_KEY` 已用环境变量注入（prod 无 yml 默认值）
- [ ] LangChain4j 请求日志已在 prod profile 关闭（`log-requests/responses: false`）
- [ ] prod 下不会自动创建 `admin/admin123`（`DefaultUserInitializer` 已禁用）
- [ ] 对话分析限流已按需调整（`app.conversation-analysis.analyze-rate-limit-*`，默认每用户每分钟 5 次）
- [ ] 健康检查：`GET /api/health`

---

## 8. 常用运维命令

```bash
# 编译
./mvn.sh -q compile

# 测试
./mvn.sh -q test

# 查看 Java 版本
./mvn.sh -version   # 应显示 Java 21
```
