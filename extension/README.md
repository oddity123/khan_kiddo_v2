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

## 使用

1. 打开站点并**先登录**。
2. 打开任意 `https://chatgpt.com/share/...`，点击右下角 **导入到 Khan Kiddo**。
3. 或在扩展 popup 粘贴分享链接 → **导入并打开**。
4. 分析页输入框自动填入字幕后，点「开始分析」。

开发版 popup 会显示「站点地址」；正式版只显示导入，目标固定为生产站。

## 说明

- 拉取：`GET https://chatgpt.com/backend-api/share/{id}`（走用户本机网络）。
- Voice 字幕取自 `parts[].content_type === "audio_transcription"`。
- 本阶段不在扩展内跑分析 SSE；只负责导入文本。
- 生产域名通过 `extension/.env.production` 的 `VITE_KK_WEB_ORIGIN` 配置。
