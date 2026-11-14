# Khan Kiddo 浏览器扩展（P0）

从 **ChatGPT 公开分享页**（含 Voice 字幕）一键导入对话到 v2「对话分析」页。

## 构建

本地调试（默认站点 `http://localhost:5173`，弹窗可改地址）：

```bash
cd extension
npm install
npm run build -- --mode development
# 或监听：npm run dev
```

正式发布（站点写死为 `https://khankiddo.top`，**不向用户暴露地址配置**）：

```bash
npm run build
# 等价于 --mode production
```

Chrome → 扩展程序 → 开发者模式 → **加载已解压的扩展程序** → 选择 `extension/dist`。

正式构建会自动收窄权限：仅保留固定生产站、ChatGPT 分享页、`storage`（隐私同意状态）和
`scripting`（向分析页传递导入内容）。localhost 与动态站点权限只会进入 development 构建。

## 使用

1. 打开任意 `https://chatgpt.com/share/...`，点击右下角 **导入到 Khan Kiddo**。
2. 首次使用时阅读数据说明并确认；也可以随时在 popup 取消同意。
3. 或在扩展 popup 粘贴分享链接 → **导入并打开**。
4. 分析页输入框自动填入字幕后，点「开始分析」。登录后可保存历史，未登录可使用访客额度。

开发版弹窗以「自动读取 ChatGPT」为主入口：打开 ChatGPT 历史对话后点击自动读取，页面会刷新，请先保存草稿。保持弹窗打开即可自动更新进度、预览并复制对话；关闭后重新打开也可继续查看；读取内容仅在浏览器内查看和复制，不会自动导入分析页。请核对对话开头，读取结果不保证完整。

分享链接导入位于下方折叠入口；隐私政策与数据使用说明通过链接查看，导入前需勾选同意。开发版的站点地址可在该入口内的「开发设置」修改。

开发版还提供「注入示例句子」：在 ChatGPT 新对话或已有对话页面打开扩展，点击按钮将弹窗内展示的固定英文指令填入当前输入框，由用户检查后手动发送。已有文本草稿不会覆盖；正在生成回复或找不到可用输入框时会提示失败。本阶段不创建新对话、不接入练习计划。

正式版仅提供分享链接导入，目标固定为生产站。

## 说明

- 拉取：`GET https://chatgpt.com/backend-api/share/{id}`（走用户本机网络）。
- Voice 字幕取自 `parts[].content_type === "audio_transcription"`。
- 本阶段不在扩展内跑分析 SSE；只负责导入文本。
- 生产域名通过 `extension/.env.production` 的 `VITE_KK_WEB_ORIGIN` 配置。
- 扩展隐私政策：`https://khankiddo.top/privacy/extension`。
