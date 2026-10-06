# edge-tts 小服务

影子跟读的“原声”来源。基于 [edge-tts](https://github.com/rany2/edge-tts)（LGPLv3，调用微软 Edge 在线朗读接口，非官方、无需 Key），
包一层 FastAPI 供后端 `EdgeTtsClient` 调用。后端会把合成结果按 `sha256(voice + 文本)` 缓存到磁盘，同一句只合成一次。

## 接口

- `GET /health` → `{"status":"ok"}`
- `POST /v1/tts`，JSON `{"text": "...", "voice": "en-US-AriaNeural"}` → `audio/mpeg`
  - `voice` 可省略，默认取环境变量 `EDGE_TTS_DEFAULT_VOICE`（`en-US-AriaNeural`）
  - 文本超过 `EDGE_TTS_MAX_TEXT_CHARS`（默认 500）返回 400；微软接口失败返回 502

## 本地运行

```bash
cd services/edge-tts
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 127.0.0.1 --port 8001
```

## Docker（服务器）

只绑定 `127.0.0.1`，不要在安全组放行 8001：

```bash
cd services/edge-tts
docker build -t khankiddo-edge-tts .
docker run -d --name edge-tts --restart unless-stopped -p 127.0.0.1:8001:8001 khankiddo-edge-tts
```

## 验证

```bash
curl -s -X POST http://127.0.0.1:8001/v1/tts \
  -H 'Content-Type: application/json' \
  -d '{"text":"Yesterday I went to school."}' -o /tmp/tts.mp3 && file /tmp/tts.mp3
```

后端开启：`SHADOWING_TTS_ENABLED=true`、`SHADOWING_TTS_BASE_URL=http://127.0.0.1:8001`。
