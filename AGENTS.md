# Khan Kiddo v2

前后端分离项目：`backend/`（Java 21 + Spring Boot 3 + MyBatis-Plus + MySQL + JWT + LangChain4j），`frontend/`（Vue 3 + Vite + TypeScript），`extension/`（Chrome MV3，ChatGPT 分享页导入字幕）。
标准命令与部署说明见 `README.md`、`.env.example`，构建规则见下文；前端约定见 `frontend/AGENTS.md`；LLM 提示词约定见 `backend/src/main/resources/AGENTS.md`。

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
- **Spring Boot 不会自动读 `.env`**：启动后端前先 `set -a && source .env && set +a`。本环境已放置一个（被 `.gitignore` 忽略、不会提交）的 dev `.env`，含 `DB_PASSWORD=root`、空 AI Key、`SPRING_PROFILES_ACTIVE=dev`。
- 运行后端（开发）：`set -a && source .env && set +a && mvn -f backend/pom.xml spring-boot:run`，或跑已构建的 jar
  `java -jar backend/target/khankiddo-v2-3.0.0-SNAPSHOT.jar`。
- dev profile 会自动创建管理员账号 **`admin` / `admin123`**（`DefaultUserInitializer`，`test`/`prod` 下禁用）。
- 前端开发：`cd frontend && npm run dev`；类型检查+构建：`npm run build`（`vue-tsc -b && vite build`）。前端无 ESLint。
- 浏览器扩展：`cd extension && npm run build`，Chrome 加载 `extension/dist`（见 `extension/README.md`）。

AI 相关（非显而易见）：
- 登录/注册、留言反馈、查看历史等**无需 AI Key** 即可跑通（仅需 MySQL）。
- **对话分析（`/api/conversation/analyze/stream`、`/api/ai/*`）需要 `DOUBAO_API_KEY`**（Stage1 分离与默认 Bean 共用 `DOUBAO_MODEL_NAME`）；
 `QWEN_API_KEY` 仅为 Stage2/3 可选模型。未配置 Key 时 `/api/conversation/llm-models` 返回空、分析会失败。
- **ERRANT 操作批注（可选）**：`ERRANT_ENABLED=true` + `ERRANT_BASE_URL` 时，Stage2 sanitize 后软依赖调用
  `POST /v1/annotate`，落库 `conversation_analysis.edit_annotations`，前端句子卡按 R/M/U 高亮。默认关闭；
  服务不可用时分析仍成功，仅无高亮。已有库需执行 DDL 注释中的
  `ALTER TABLE ... ADD COLUMN edit_annotations ...`。

其它：
- `backend/pom.xml` 含一个 macOS-only 依赖 `netty-resolver-dns-native-macos`（classifier `osx-aarch_64`），在 Linux 上仅是未使用的产物，不影响构建/运行。
- 后端测试用 H2（`test` profile），无需 MySQL：`mvn -f backend/pom.xml test`。

# 架构守门（Khan Kiddo v2）

改业务代码前对照本规则。依据全量说明见 Project Context 中的 `architecture-guardrails.md`（与本文一致；以 **本文件（AGENTS.md）为生效版**）。

## 何时必须对照

- 新增/修改 `backend` 的 Service、Controller、Pipeline、Gateway、Support、LLM Factory/Policy
- 新增/修改大型 Vue SFC、composable，或新 LLM 阶段 / 铸卡 type / `sourceRef` 形态
- 讨论「放哪」「分层」「工具类」「配置放哪」

---

## R1 — 禁止继续加厚上帝类

**禁止**向下列单元堆无关职责；新能力旁路到**新类 / Support / 组件 / composable**，旧类最多委托：

| 后端 | 前端 |
|------|------|
| `ConversationAnalysisServiceImpl` | `AnalysisDetailView.vue` |
| `ConversationAnalysisStreamingHelper` | `ChineseExpressionFan.vue` |
| `GrowthCardMintGateway` | 勿在 Fan/Detail 上继续扩 prop 矩阵 |

**检查：** diff 是否在上述文件新增另一职责的大块逻辑 → 驳回，要求旁路。

---

## R2 — OCP：扩展走策略 / 配置 / 新 Step

| 场景 | ✅ | ❌ |
|------|----|----|
| 新 LLM 厂商 quirk | 新 `*StructuredOutputPolicy` 等 | Factory / `LlmProviderSupport` 无限 `contains` |
| 新自动铸卡 type | 新 Mint Step/Persister | `GrowthCardMintGateway` 再加循环分支 |
| pointId 走 LLM 切分 | yml / Properties | 改 `FocusPhraseCutSelector.LLM_POINT_IDS` 常量 |
| 新 `sourceRef` 形态 | 共享 codec | Hydrator 再开 if/else 表 |

---

## R3 — 公共逻辑单点（Support / Utils / constants）

改前先搜 `*Support`、`*Utils`、`*Helper`、`frontend/src/utils/`、`frontend/src/constants/`。

**禁止：**

- 复制已有职责的私有 `static` / 模块级函数（如 `trimToNull`、`sourceRef` 编解码、`HabitScore` 映射、前端 `formatTime` / `typeLabel`）
- **同一职责两处实现**（例：JWT `access_token` 在 Filter 与 WebSocket Handshake 各写一套 → 收到 `SecurityUtils` / `JwtAccessTokenSupport`）
- **Composable / Handler 内堆可复用纯函数**（PCM/base64、错误帧解析、ASR 合并等 → `utils/*`，composable 只编排）

空判断：`CollectionUtils` / `StringUtils` / `ObjectUtils`。登录用户：`SecurityUtils`。

**检查：** 新增私有工具与已有 Support 同职责 → 合并；跨层重复常量字符串 → `constants/` 单点。

---

## R4 — 配置单源（Properties / yml / env）

**禁止配置散落：**

- Controller、composable、前端常量里写**魔法数**（采样率、chunk、协议 model 版本等）而与 `*Properties` / yml 双源
- 前端 `config || '1.2.6.1'` 类**协议兜底字面量**；缺配置应明确失败，默认值只在 **Properties + `application.yml`（+ `.env.example` 注释）** 一处定义
- 密钥、上游 URL、model/voice：**后端 Properties**，经 REST `/config` 暴露非敏感项；**禁止**前端硬编码 openspeech 等上游地址

**检查：** 新集成是否具备 `*Properties` + yml 段 + `.env.example`；前后端是否共用同一 config API，而非各写一套数字。

---

## R5 — 依赖方向与落位

- `controller → application/service → domain/support`；Support 不依赖 Web/SSE 细节
- Controller 保持薄（对照现有 ≤80 行门面）
- 改前自答：「谁拥有此职责？」— 答案不是「最近那个大文件」

---

## R6 — 变更预算（绞杀者）

- 一次 PR **只加深一个 seam**；行为默认等价，除非任务明确要求改产品
- 禁止借重构引入未请求的新框架/新层
- **碰到上帝类再拆**：本任务必须改该职责时才抽；不为「好看」单独开重构 PR

---

## R7 — 前端体量：先拆再加

SFC / composable 已 ~400–500 行且混编排+交互+数学时：**先**抽 composable / 子组件 / `utils`，**再**加功能。禁止只在 `AnalysisDetailView` / `ChineseExpressionFan` 叠 prop 或入口分支。

新功能优先新页面 + 新 composable（如 `voice/` 包 + `useVoiceRealtime`），勿回流上帝 SFC。

---

## 合并前自检（短清单）

1. 上帝类 diff 是否只委托、无新职责块？
2. 扩展是否走配置/新 Step，而非改常量名单或巨型 if？
3. 有无重复工具 / 重复常量 / 重复 token 解析？
4. 可调参数是否只在 Properties+yml，前端是否读 config？
5. PR 是否只做一件事、可测、无未请求的框架？

# Maven 构建（khan_kiddo_v2 / backend）

前后端分离：Java 代码与 `pom.xml` 在 **`backend/`**。使用 **Java 21**（Temurin）。

## 必须遵守

1. 在仓库根目录使用根级包装脚本（推荐）：

```bash
cd /Users/oddity/workspace/khan_kiddo_v2
./mvn.sh -q compile
./mvn.sh -q test
./mvn.sh -q package -DskipTests
```

2. 或在 `backend/` 下：

```bash
cd /Users/oddity/workspace/khan_kiddo_v2/backend
./mvn.sh -q compile
```

3. **不要**在仓库根对不存在的根 `pom.xml` 执行 `mvn`，也不要不设置 `JAVA_HOME` 直接调用系统 `mvn`。

## 验证

`./mvn.sh -version` 中 `Java version` 应为 **21.x**。
