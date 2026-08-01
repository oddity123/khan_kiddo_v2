#!/usr/bin/env python3
"""
离线挖掘：从本地 + 线上 conversation_analysis_item 归纳知识点叶子候选。

用法（在仓库根目录）:
  python3 scripts/mine_knowledge_points.py                 # 导出 + 分批挖掘 + 汇总
  python3 scripts/mine_knowledge_points.py --export-only   # 只导出合并语料
  python3 scripts/mine_knowledge_points.py --mine-only     # 跳过导出，用已有语料继续挖
  python3 scripts/mine_knowledge_points.py --resume        # 跳过已完成的 batch

输出:
  scripts/out/point-mining/
    corpus.jsonl          合并去重后的错误条目
    batches/batch_XX.json 每批模型原始输出
    candidates.json       汇总后的知识点候选（含命中估计与例句）
    run.log               运行日志
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = Path(__file__).resolve().parent / "out" / "point-mining"
BATCH_DIR = OUT_DIR / "batches"
CORPUS_PATH = OUT_DIR / "corpus.jsonl"
CANDIDATES_PATH = OUT_DIR / "candidates.json"
LOG_PATH = OUT_DIR / "run.log"

DEFAULT_MODEL = "doubao-seed-2-0-mini-260428"
DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
DEFAULT_BATCH_SIZE = 60
DEFAULT_USER_ID = 1

# 本地库默认（application.yml 回退值）；线上从 .env 的 DB_* 读
LOCAL_DEFAULTS = {
    "host": "127.0.0.1",
    "port": "3306",
    "user": "root",
    "password": "1134836877",
    "database": "khan_kiddo_dev",
}


def log(msg: str) -> None:
    line = f"[{datetime.now().strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def load_env(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    if not path.exists():
        return env
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip("'").strip('"')
    return env


def parse_jdbc_url(url: str) -> dict[str, str]:
    """jdbc:mysql://host:port/db?... → host/port/database"""
    m = re.match(
        r"jdbc:mysql://([^:/]+)(?::(\d+))?/([^?]+)",
        url or "",
    )
    if not m:
        raise ValueError(f"无法解析 DB_URL: {url!r}")
    return {
        "host": m.group(1),
        "port": m.group(2) or "3306",
        "database": m.group(3),
    }


def mysql_tsv(cfg: dict[str, str], sql: str) -> list[list[str]]:
    env = os.environ.copy()
    env["MYSQL_PWD"] = cfg["password"]
    cmd = [
        "mysql",
        "-h", cfg["host"],
        "-P", cfg["port"],
        "-u", cfg["user"],
        "--default-character-set=utf8mb4",
        "--batch",
        "--raw",
        "-N",
        "-e", sql,
    ]
    proc = subprocess.run(
        cmd,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(
            f"mysql 失败 ({cfg['host']}): {proc.stderr.strip() or proc.stdout.strip()}"
        )
    rows: list[list[str]] = []
    for line in proc.stdout.splitlines():
        # mysql --batch 用 tab 分隔；字段内可能含换行已被 --raw 保留为字面量
        rows.append(line.split("\t"))
    return rows


EXPORT_SQL = """
SELECT
  i.id,
  ca.analysis_id,
  ca.user_id,
  i.sentence_id,
  REPLACE(REPLACE(COALESCE(i.original_sentence,''),'\\n',' '),'\\t',' ') AS original_sentence,
  REPLACE(REPLACE(COALESCE(i.problem_types,''),'\\n',' '),'\\t',' ') AS problem_types,
  REPLACE(REPLACE(COALESCE(i.error_point,''),'\\n',' '),'\\t',' ') AS error_point,
  REPLACE(REPLACE(COALESCE(i.suggestion,''),'\\n',' '),'\\t',' ') AS suggestion
FROM {db}.conversation_analysis_item i
JOIN {db}.conversation_analysis ca ON ca.analysis_id = i.analysis_id
WHERE ca.status = 'success' AND ca.user_id = {user_id}
ORDER BY i.id;
"""


def fetch_items(label: str, cfg: dict[str, str], user_id: int) -> list[dict]:
    db = cfg["database"]
    sql = EXPORT_SQL.format(db=db, user_id=user_id)
    log(f"导出 {label}: {cfg['user']}@{cfg['host']}:{cfg['port']}/{db} user_id={user_id}")
    rows = mysql_tsv(cfg, sql)
    items = []
    for r in rows:
        if len(r) < 8:
            continue
        items.append(
            {
                "source": label,
                "item_id": r[0],
                "analysis_id": r[1],
                "user_id": int(r[2]) if r[2].isdigit() else r[2],
                "sentence_id": r[3],
                "original_sentence": r[4],
                "problem_types": r[5],
                "error_point": r[6],
                "suggestion": r[7],
            }
        )
    log(f"  → {len(items)} 条")
    return items


def item_key(it: dict) -> str:
    raw = "|".join(
        [
            (it.get("original_sentence") or "").strip().lower(),
            (it.get("error_point") or "").strip().lower(),
            (it.get("problem_types") or "").strip().lower(),
        ]
    )
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def export_corpus(env: dict[str, str], user_id: int, sources: str) -> list[dict]:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    all_items: list[dict] = []

    if sources in ("local", "both"):
        local = {
            "host": env.get("LOCAL_DB_HOST", LOCAL_DEFAULTS["host"]),
            "port": env.get("LOCAL_DB_PORT", LOCAL_DEFAULTS["port"]),
            "user": env.get("LOCAL_DB_USERNAME", LOCAL_DEFAULTS["user"]),
            "password": env.get("LOCAL_DB_PASSWORD", LOCAL_DEFAULTS["password"]),
            "database": env.get("LOCAL_DB_NAME", LOCAL_DEFAULTS["database"]),
        }
        all_items.extend(fetch_items("local", local, user_id))

    if sources in ("online", "both"):
        if not env.get("DB_URL"):
            raise RuntimeError(".env 缺少 DB_URL，无法连线上库")
        online = parse_jdbc_url(env["DB_URL"])
        online["user"] = env.get("DB_USERNAME", "")
        online["password"] = env.get("DB_PASSWORD", "")
        if not online["user"] or not online["password"]:
            raise RuntimeError(".env 缺少 DB_USERNAME / DB_PASSWORD")
        all_items.extend(fetch_items("online", online, user_id))

    seen: set[str] = set()
    merged: list[dict] = []
    dup = 0
    for it in all_items:
        k = item_key(it)
        if k in seen:
            dup += 1
            continue
        seen.add(k)
        it["dedupe_key"] = k
        merged.append(it)

    with CORPUS_PATH.open("w", encoding="utf-8") as f:
        for it in merged:
            f.write(json.dumps(it, ensure_ascii=False) + "\n")

    type_dist = Counter(it["problem_types"] for it in merged)
    log(
        f"合并完成: 原始 {len(all_items)} → 去重 {len(merged)}（去掉重复 {dup}）→ {CORPUS_PATH}"
    )
    log("ProblemType 分布: " + ", ".join(f"{t}={c}" for t, c in type_dist.most_common(12)))
    return merged


def load_corpus() -> list[dict]:
    if not CORPUS_PATH.exists():
        raise RuntimeError(f"找不到语料 {CORPUS_PATH}，请先 --export-only 或完整跑一遍")
    items = []
    with CORPUS_PATH.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                items.append(json.loads(line))
    return items


def chunked(seq: list, size: int):
    for i in range(0, len(seq), size):
        yield i // size, seq[i : i + size]


BATCH_SYSTEM = """你是英语教学研究者，专长中文母语者口语错误归因。
任务：从一批真实口语纠错记录中，自底向上归纳「可出规则卡、可反复练习」的知识点叶子。

硬性要求：
1. 只归纳「规则型」知识点——背一条规则能覆盖多条错误。
2. 不要把一次性词汇缺口（说错词 / 想不起词 / 语音识别噪音）收成知识点；把它们归到 bucket=lexical_gap。
3. 不要把口语流利度问题（说到一半断开、口头重复、自我更正）收成规则知识点；归到 bucket=fluency。
4. 每个知识点必须给稳定的 pointId（大写蛇形，如 FEEL_BE_ED_ADJ、ARTICLE_GENERIC_PLURAL）。
5. 每个知识点给：familyId（家族）、titleZh（一句人话规则）、whyZh（母语迁移解释，1-2 句）、exampleIndexes（本批里的证据序号，最多 5 个）。
6. 宁可少而准，不要硬拆。如果本批没有够格的规则型，points 可以为空，只填 buckets。
7. 只输出 JSON，不要 markdown。"""


def build_batch_user_prompt(batch_idx: int, items: list[dict]) -> str:
    lines = [
        f"本批编号 batch_{batch_idx:02d}，共 {len(items)} 条。",
        "每条格式: [#idx] type | original | error_point",
        "",
    ]
    for i, it in enumerate(items):
        lines.append(
            f"[#{i}] {it.get('problem_types','')} | "
            f"{it.get('original_sentence','')} | "
            f"{it.get('error_point','')}"
        )
    lines.extend(
        [
            "",
            "请输出 JSON，schema：",
            "{",
            '  "points": [',
            "    {",
            '      "pointId": "FEEL_BE_ED_ADJ",',
            '      "familyId": "FAM_WORD_FORM",',
            '      "titleZh": "说「我感到…」要用 -ed，别直接用动词原形",',
            '      "whyZh": "中文形容词/动词不分，英文感受用 -ed",',
            '      "exampleIndexes": [0, 3, 7],',
            '      "estimatedCountInBatch": 5',
            "    }",
            "  ],",
            '  "buckets": {',
            '    "lexical_gap": {"count": 0, "note": ""},',
            '    "fluency": {"count": 0, "note": ""},',
            '    "other": {"count": 0, "note": ""}',
            "  }",
            "}",
        ]
    )
    return "\n".join(lines)


MERGE_SYSTEM = """你是英语教学产品的知识点词典主编。
下面是多批离线挖掘结果，请合并成一份 v1 候选字典。

要求：
1. 合并同义 / 重叠的 pointId（统一命名，保留最清晰的 titleZh / whyZh）。
2. 按「历史证据强度」排序：totalEstimatedCount 从高到低。
3. 只保留规则型叶子；lexical_gap / fluency 单独汇总，不要放进 points。
4. 建议 v1Keep=true 的大约 25–35 个高频叶子；其余 v1Keep=false 进完全体候补。
5. 每个叶子给 2–3 条真实例句（从 batches 的 example 里挑，保留 original + error_point）。
6. 只输出 JSON。"""


def chat_completions(
    *,
    base_url: str,
    api_key: str,
    model: str,
    system: str,
    user: str,
    temperature: float = 0.2,
    timeout: int = 90,
    retries: int = 3,
) -> str:
    url = base_url.rstrip("/") + "/chat/completions"
    payload = {
        "model": model,
        "temperature": temperature,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }
    data = json.dumps(payload).encode("utf-8")
    last_err: Exception | None = None
    for attempt in range(1, retries + 1):
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {api_key}",
            },
            method="POST",
        )
        try:
            log(f"  → API 请求 attempt {attempt}/{retries} (timeout={timeout}s)")
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                body = json.loads(resp.read().decode("utf-8"))
            return body["choices"][0]["message"]["content"]
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", errors="replace")
            last_err = RuntimeError(f"HTTP {e.code}: {detail}")
            # 429/5xx 可重试；4xx 其它直接失败
            if e.code not in (408, 429, 500, 502, 503, 504):
                raise last_err from e
            log(f"  ↻ HTTP {e.code}，{2 * attempt}s 后重试")
            time.sleep(2 * attempt)
        except Exception as e:
            last_err = e
            log(f"  ↻ 请求异常: {type(e).__name__}: {e}；{2 * attempt}s 后重试")
            time.sleep(2 * attempt)
    raise RuntimeError(f"API 连续失败 {retries} 次: {last_err}")


def extract_json(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        m = re.search(r"\{[\s\S]*\}", text)
        if not m:
            raise
        return json.loads(m.group(0))


def mine_batches(
    items: list[dict],
    *,
    env: dict[str, str],
    model: str,
    batch_size: int,
    resume: bool,
    max_batches: int | None,
) -> list[Path]:
    BATCH_DIR.mkdir(parents=True, exist_ok=True)
    api_key = env.get("DOUBAO_API_KEY") or env.get("AI_API_KEY")
    if not api_key:
        raise RuntimeError(".env 缺少 DOUBAO_API_KEY / AI_API_KEY")
    base_url = env.get("DOUBAO_BASE_URL") or env.get("AI_BASE_URL") or DEFAULT_BASE_URL

    paths: list[Path] = []
    for batch_idx, batch in chunked(items, batch_size):
        if max_batches is not None and batch_idx >= max_batches:
            log(f"已达 --max-batches={max_batches}，停止")
            break
        out_path = BATCH_DIR / f"batch_{batch_idx:02d}.json"
        paths.append(out_path)
        if resume and out_path.exists():
            log(f"跳过已完成 batch_{batch_idx:02d}")
            continue

        prompt = build_batch_user_prompt(batch_idx, batch)
        log(f"挖掘 batch_{batch_idx:02d}（{len(batch)} 条）→ {model}")
        t0 = time.time()
        raw = chat_completions(
            base_url=base_url,
            api_key=api_key,
            model=model,
            system=BATCH_SYSTEM,
            user=prompt,
        )
        parsed = extract_json(raw)
        # 把证据序号还原成真实句子，方便后续汇总
        for p in parsed.get("points") or []:
            exs = []
            for idx in p.get("exampleIndexes") or []:
                if isinstance(idx, int) and 0 <= idx < len(batch):
                    it = batch[idx]
                    exs.append(
                        {
                            "original_sentence": it.get("original_sentence"),
                            "error_point": it.get("error_point"),
                            "problem_types": it.get("problem_types"),
                            "source": it.get("source"),
                        }
                    )
            p["examples"] = exs
        record = {
            "batch": batch_idx,
            "model": model,
            "item_count": len(batch),
            "elapsed_sec": round(time.time() - t0, 2),
            "created_at": datetime.now(timezone.utc).isoformat(),
            "result": parsed,
            "raw": raw,
        }
        out_path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
        log(f"  ✓ {out_path.name}  ({record['elapsed_sec']}s, points={len(parsed.get('points') or [])})")
        time.sleep(0.4)
    return paths


def synthesize(env: dict[str, str], model: str) -> dict:
    batch_files = sorted(BATCH_DIR.glob("batch_*.json"))
    if not batch_files:
        raise RuntimeError("没有 batch 结果可汇总")

    # 先本地粗聚合，减少喂给模型的体积
    by_id: dict[str, dict] = {}
    bucket_totals = Counter()
    for path in batch_files:
        data = json.loads(path.read_text(encoding="utf-8"))
        result = data.get("result") or {}
        for p in result.get("points") or []:
            pid = (p.get("pointId") or "").strip().upper()
            if not pid:
                continue
            slot = by_id.setdefault(
                pid,
                {
                    "pointId": pid,
                    "familyId": p.get("familyId"),
                    "titleZh_votes": [],
                    "whyZh_votes": [],
                    "totalEstimatedCount": 0,
                    "batchHits": 0,
                    "examples": [],
                },
            )
            slot["batchHits"] += 1
            slot["totalEstimatedCount"] += int(p.get("estimatedCountInBatch") or 0)
            if p.get("titleZh"):
                slot["titleZh_votes"].append(p["titleZh"])
            if p.get("whyZh"):
                slot["whyZh_votes"].append(p["whyZh"])
            for ex in p.get("examples") or []:
                if len(slot["examples"]) < 8:
                    slot["examples"].append(ex)
            if not slot.get("familyId") and p.get("familyId"):
                slot["familyId"] = p["familyId"]
        buckets = result.get("buckets") or {}
        for k, v in buckets.items():
            if isinstance(v, dict):
                bucket_totals[k] += int(v.get("count") or 0)
            elif isinstance(v, int):
                bucket_totals[k] += v

    pre_agg = sorted(
        by_id.values(),
        key=lambda x: (-x["totalEstimatedCount"], -x["batchHits"], x["pointId"]),
    )
    # 喂给模型时大幅瘦身，避免超长 JSON 被截断
    slim = []
    for p in pre_agg:
        titles = p.get("titleZh_votes") or []
        whys = p.get("whyZh_votes") or []
        ex0 = (p.get("examples") or [{}])[0]
        slim.append(
            {
                "pointId": p["pointId"],
                "familyId": p.get("familyId"),
                "titleZh": titles[0] if titles else "",
                "whyZh": whys[0] if whys else "",
                "totalEstimatedCount": p["totalEstimatedCount"],
                "batchHits": p["batchHits"],
                "example": {
                    "original_sentence": (ex0.get("original_sentence") or "")[:160],
                    "error_point": (ex0.get("error_point") or "")[:120],
                },
            }
        )
    compact = {
        "pre_aggregated": slim,
        "bucket_totals": dict(bucket_totals),
        "batch_count": len(batch_files),
    }

    api_key = env.get("DOUBAO_API_KEY") or env.get("AI_API_KEY")
    base_url = env.get("DOUBAO_BASE_URL") or env.get("AI_BASE_URL") or DEFAULT_BASE_URL
    user_prompt = (
        "以下是多批挖掘的预聚合结果，请合并成最终 v1 候选字典。\n"
        "硬性：只输出合法 JSON；examples 每条最多 2 个，句子截断到 120 字；"
        "notes 用字符串数组。\n"
        "输出 schema：\n"
        "{\n"
        '  "families": [{"familyId":"FAM_WORD_FORM","titleZh":"词形与词类","note":""}],\n'
        '  "points": [{\n'
        '    "pointId":"...","familyId":"...","titleZh":"...","whyZh":"...",\n'
        '    "totalEstimatedCount":0,"v1Keep":true,\n'
        '    "examples":[{"original_sentence":"...","error_point":"..."}]\n'
        "  }],\n"
        '  "buckets": {"lexical_gap":{"count":0,"note":""},"fluency":{"count":0,"note":""}},\n'
        '  "notes": ["观察1","观察2"]\n'
        "}\n\n"
        + json.dumps(compact, ensure_ascii=False)
    )

    log(f"汇总 {len(batch_files)} 个 batch → 调模型合并（slim={len(slim)}）")
    t0 = time.time()
    raw = ""
    parsed: dict | None = None
    for attempt in range(1, 3):
        try:
            raw = chat_completions(
                base_url=base_url,
                api_key=api_key,
                model=model,
                system=MERGE_SYSTEM,
                user=user_prompt if attempt == 1 else (
                    "上一次输出不是合法 JSON。请严格只输出一个 JSON 对象，不要 markdown。\n"
                    "保持同样 schema，examples 每条最多 1 个且截断。\n\n"
                    + json.dumps(
                        {
                            "pre_aggregated": slim[:80],
                            "bucket_totals": dict(bucket_totals),
                        },
                        ensure_ascii=False,
                    )
                ),
                temperature=0.1,
                timeout=180,
            )
            (OUT_DIR / "synthesize_raw.txt").write_text(raw, encoding="utf-8")
            parsed = extract_json(raw)
            break
        except Exception as e:
            log(f"  汇总 attempt {attempt} 失败: {e}")
            if attempt == 2:
                log("  回退到本地预聚合（不经模型合并）")
                parsed = local_synthesize_fallback(pre_agg, bucket_totals)

    assert parsed is not None
    # 补全 examples：模型可能只给了瘦身后的例句，用本地预聚合补到 2-3 条
    by_pid = {p["pointId"]: p for p in pre_agg}
    for p in parsed.get("points") or []:
        pid = (p.get("pointId") or "").upper()
        src = by_pid.get(pid)
        if src and (not p.get("examples") or len(p.get("examples") or []) < 2):
            p["examples"] = (src.get("examples") or [])[:3]
        if src and not p.get("totalEstimatedCount"):
            p["totalEstimatedCount"] = src["totalEstimatedCount"]

    out = {
        "model": model,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "elapsed_sec": round(time.time() - t0, 2),
        "batch_count": len(batch_files),
        "pre_aggregated_count": len(by_id),
        "result": parsed,
        "pre_aggregated": pre_agg,
        "raw": raw,
    }
    CANDIDATES_PATH.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    points = parsed.get("points") or []
    v1 = [p for p in points if p.get("v1Keep")]
    log(
        f"✓ 候选字典 → {CANDIDATES_PATH}  "
        f"(points={len(points)}, v1Keep={len(v1)}, {out['elapsed_sec']}s)"
    )
    return out


def local_synthesize_fallback(
    pre_agg: list[dict],
    bucket_totals: Counter,
) -> dict:
    """模型汇总失败时：按预聚合直接产出候选，标题取最高频文案。"""
    points = []
    families: dict[str, str] = {}
    for i, p in enumerate(pre_agg):
        titles = p.get("titleZh_votes") or [""]
        whys = p.get("whyZh_votes") or [""]
        # 取出现最多的标题
        title = Counter(titles).most_common(1)[0][0] if titles else p["pointId"]
        why = Counter(whys).most_common(1)[0][0] if whys else ""
        fid = p.get("familyId") or "FAM_OTHER"
        families.setdefault(fid, fid)
        points.append(
            {
                "pointId": p["pointId"],
                "familyId": fid,
                "titleZh": title,
                "whyZh": why,
                "totalEstimatedCount": p["totalEstimatedCount"],
                "v1Keep": i < 30 and p["totalEstimatedCount"] >= 3,
                "examples": (p.get("examples") or [])[:3],
            }
        )
    return {
        "families": [{"familyId": k, "titleZh": k, "note": "local fallback"} for k in families],
        "points": points,
        "buckets": {
            k: {"count": v, "note": "from batch buckets"}
            for k, v in bucket_totals.items()
        },
        "notes": [
            "模型合并 JSON 解析失败，已回退到本地预聚合。",
            "v1Keep = 前 30 且 estimatedCount>=3，需人工复核合并同义 pointId。",
        ],
    }


def print_summary(candidates: dict) -> None:
    result = candidates.get("result") or {}
    points = result.get("points") or []
    print("\n======== v1 候选（按证据强度）========")
    for i, p in enumerate(points, 1):
        flag = "V1" if p.get("v1Keep") else "  "
        print(
            f"{i:2d}. [{flag}] {p.get('pointId')}  "
            f"n≈{p.get('totalEstimatedCount')}  "
            f"{p.get('titleZh')}"
        )
    buckets = result.get("buckets") or {}
    if buckets:
        print("\n======== 非规则桶 ========")
        for k, v in buckets.items():
            if isinstance(v, dict):
                print(f"  {k}: {v.get('count')}  {v.get('note','')}")
            else:
                print(f"  {k}: {v}")
    notes = result.get("notes")
    if notes:
        print("\n======== 观察 ========")
        if isinstance(notes, list):
            for n in notes:
                print(f"  - {n}")
        else:
            print(f"  {notes}")


def main() -> int:
    parser = argparse.ArgumentParser(description="离线挖掘口语知识点候选")
    parser.add_argument("--export-only", action="store_true")
    parser.add_argument("--mine-only", action="store_true")
    parser.add_argument("--synthesize-only", action="store_true")
    parser.add_argument("--resume", action="store_true", help="跳过已有 batch 文件")
    parser.add_argument("--sources", choices=["local", "online", "both"], default="both")
    parser.add_argument("--user-id", type=int, default=DEFAULT_USER_ID)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument("--max-batches", type=int, default=None, help="调试用：只跑前 N 批")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--skip-synthesize", action="store_true")
    args = parser.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    env = load_env(ROOT / ".env")

    if args.synthesize_only:
        candidates = synthesize(env, args.model)
        print_summary(candidates)
        return 0

    items: list[dict]
    if args.mine_only:
        items = load_corpus()
        log(f"加载已有语料 {len(items)} 条")
    else:
        items = export_corpus(env, args.user_id, args.sources)
        if args.export_only:
            return 0

    mine_batches(
        items,
        env=env,
        model=args.model,
        batch_size=args.batch_size,
        resume=args.resume,
        max_batches=args.max_batches,
    )

    if not args.skip_synthesize:
        candidates = synthesize(env, args.model)
        print_summary(candidates)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        log("中断")
        raise SystemExit(130)
    except Exception as e:
        log(f"失败: {e}")
        raise
