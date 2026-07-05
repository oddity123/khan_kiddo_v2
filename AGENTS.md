# Khan Kiddo v2

前后端分离项目：`backend/`（Java 21 + Spring Boot 3 + MyBatis-Plus + MySQL + JWT + LangChain4j），`frontend/`（Vue 3 + Vite + TypeScript）。
标准命令与部署说明见 `DEPLOY.md`、`.env.example`，以及 `.cursor/rules/` 下的构建/前端规则，本文件不重复。

## Cursor Cloud specific instructions

服务概览与端口：
- 后端 Spring Boot：`:8080`（`PORT` 可改），REST + SSE，健康检查 `GET /api/health`。
- 前端 Vite dev：`:5173`，`/api/*` 代理到 `http://localhost:8080`（见 `frontend/vite.config.ts`）。
- MySQL 8：库 `khan_kiddo_dev`，本机 `127.0.0.1:3306`，账号 `root` / `root`。

启动/运行注意（非显而易见）：
- **MySQL 不会开机自启**，每个会话先运行 `sudo service mysql start`。数据目录随快照保留；若表缺失，重跑
  `mysql -h 127.0.0.1 -u root -proot < backend/src/main/resources/sql/DDL.sql`（DDL 全部 `IF NOT EXISTS`，幂等）。
- **不要用仓库里的 `mvn.sh`**：它把 `JAVA_HOME` 写死为 macOS 路径，在此 Linux 环境无效。直接用系统 `mvn`
  （默认已是 Java 21），例如 `mvn -f backend/pom.xml ...`。
- **Spring Boot 不会自动读 `.env`**：启动后端前先 `set -a && source .env && set +a`。本环境已放置一个（被 `.gitignore` 忽略、不会提交）的 dev `.env`，含 `DB_PASSWORD=root`、`SPRING_PROFILES_ACTIVE=dev`，且 `DOUBAO_API_KEY=${DOUBAO_API_KEY}`、`QWEN_API_KEY=${QWEN_API_KEY}`（即从进程环境继承注入的密钥）。
- **AI 密钥由 Cloud 作为环境变量注入**：由于 `.env` 里写的是 `DOUBAO_API_KEY=${DOUBAO_API_KEY}`、`QWEN_API_KEY=${QWEN_API_KEY}`，务必**从已带这些变量的 shell 启动后端**（新的 Shell 工具会话或新建的 tmux 会话会继承）。避免在密钥注入之前创建、之后复用的旧 tmux 会话里启动，否则展开为空、AI 分析会失败。验证：`curl -s localhost:8080/api/conversation/llm-models -H "Authorization: Bearer <token>"` 应列出 `doubao-seed`（配了 `QWEN_API_KEY` 还会有 `qwen-plus`），而非 `[]`。
- 运行后端（开发）：`set -a && source .env && set +a && mvn -f backend/pom.xml spring-boot:run`，或跑已构建的 jar
  `java -jar backend/target/khankiddo-v2-3.0.0-SNAPSHOT.jar`。
- dev profile 会自动创建管理员账号 **`admin` / `admin123`**（`DefaultUserInitializer`，`test`/`prod` 下禁用）。
- 前端开发：`cd frontend && npm run dev`；类型检查+构建：`npm run build`（`vue-tsc -b && vite build`）。前端无 ESLint。

AI 相关（非显而易见）：
- 登录/注册、留言反馈、查看历史等**无需 AI Key** 即可跑通（仅需 MySQL）。
- **对话分析（`/api/conversation/analyze/stream`、`/api/ai/*`）需要 `DOUBAO_API_KEY`**（Stage1 分离硬绑定豆包 Flash，见 `AI_SEPARATION_MODEL`）；
  `QWEN_API_KEY` 为 Stage2/3 可选模型（前端选 `Qwen Plus` / `modelId=qwen-plus`），千问不能单独完成分析、仍依赖豆包做 Stage1。
  模型条目定义在 `application.yml` 的 `app.llm.models`，`LlmModelCatalog.listEnabled()` 只返回 enabled 且 api-key 非空的模型。
  注意 `QWEN_MODEL_NAME` 默认值在 `application.yml`（`qwen3.6-plus`）与 `.env.example`（`qwen-plus`）不一致，建议显式设为 DashScope 账号可用的模型名。

其它：
- `backend/pom.xml` 含一个 macOS-only 依赖 `netty-resolver-dns-native-macos`（classifier `osx-aarch_64`），在 Linux 上仅是未使用的产物，不影响构建/运行。
- 后端测试用 H2（`test` profile），无需 MySQL：`mvn -f backend/pom.xml test`。
