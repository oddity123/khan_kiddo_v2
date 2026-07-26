# Khan Kiddo v2 · 简历项目素材（固化版）

> 目标岗位：**Java 后端 / AI 应用工程师（Agent 方向）**  
> 前端：仅保留 1～2 点，用于证明全栈落地能力，不占主篇幅。  
> 固化日期：2026-07-23  
> 依据：仓库实际代码（`backend/` + `frontend/`），不含 v2 未实现功能（文章生成、词典、笔记本等）。

---

## 1. 项目一句话

**Khan Kiddo v2** — AI 英语口语学习助手：对用户与 AI 的对话字幕做多阶段 LLM 分析，输出语法纠错、可解释口语评分与学习诊断；并基于个人错题数据提供 Agentic RAG 语法复盘助手。

---

## 2. 技术栈（简历栏）

**后端 / AI：** Java 21 · Spring Boot 3 · Spring Security · JWT · MyBatis-Plus · MySQL · LangChain4j · SSE · 虚拟线程 · Caffeine · Qdrant（可选）

**前端（补充）：** Vue 3 · TypeScript · Vite · Pinia

---

## 3. 功能说明（精简）


| 模块         | 说明                                |
| ---------- | --------------------------------- |
| 认证         | 注册 / 登录，JWT 无状态鉴权                 |
| 对话分析       | 粘贴字幕 → SSE 多阶段流式分析 → 诊断报告持久化      |
| 分析历史       | 分页列表、详情、删除                        |
| 口语评分       | 自然度 / 准确度 / 流利度 / 词汇四维 + 综合分      |
| 中英分流       | 含汉字句走「表达建议」；纯英文句走语法分析             |
| 语法复盘 Agent | 基于历史错题的 Tool 调用 + 可选向量检索，SSE 流式问答 |
| 学习看板       | 近 7 日练习量、严重错误、高频错误类型等             |


---



## 4. 推荐简历正文（可直接粘贴）

> 职责表述请按真实贡献改为「独立完成 / 负责 / 参与」。

**Khan Kiddo v2｜AI 英语口语学习平台（Agent）**  
技术栈：Java 21、Spring Boot 3、LangChain4j、MySQL、MyBatis-Plus、Spring Security、JWT、SSE、Qdrant；前端 Vue3/TS（全栈落地）

- 设计并落地对话字幕**多阶段分析流水线**（分离 → 语法/表达分析 → 教育诊断），通过 SSE 实时推送进度、阶段失败降级及结果持久化，并采用虚拟线程分批处理长对话、Semaphore 控制并发与有序归并，降低长耗时 AI 任务的等待不确定性并避免单次超长请求。
- 基于 LangChain4j 实现**语法复盘 Agentic RAG**，由模型按需调用错句统计、样例查询等 DB Tools 及可选语义检索 Tool，通过 `@MemoryId` 隔离用户记忆与工具数据，并在事务提交后异步构建向量索引，实现个性化复盘且避免索引任务阻塞分析主链路。
- 构建面向豆包、通义等模型的**结构化输出策略**，按模型能力适配 strict JSON Schema 或 JSON Mode + Prompt Schema，并增加确定性 Sanitizer 过滤 span 不匹配与自我修正类假阳性，提升跨模型兼容性及诊断结果可靠性。
- 搭建 **LLM 漂移回归 Harness**（按需触发真实模型调用），通过分数极差、错误类型 Jaccard 相似度和句级翻转率量化同一输入的多次运行差异，为模型切换、Prompt 调整及参数变更提供可重复的稳定性回归依据。
- 设计**配置驱动的口语四维评分模型**，基于错误类型权重、指数密度衰减和短对话平滑计算自然度、准确度、流利度与词汇得分，使评分摆脱 LLM 随机打分并具备可解释、可调参与可单测能力。
- 完成 Vue3/TypeScript 前端交付，基于 Fetch ReadableStream 实现 SSE 半包解析与多阶段流式预览，并落地 JWT 路由鉴权和分析报告可视化，将后端 Agent 能力封装为可交互的完整产品闭环，体现前后端全栈交付能力。

---



## 5. 亮点拆解（面试可展开）



### 主打（Agent / AI 工程）



#### A. 多阶段流水线 + 长对话并发

- **做什么：** Stage1 字幕分离（固定 Flash）→ CJK 分流 → Stage2 语法流式分析 → Stage3 教育总结。
- **工程点：** SSE 进度；总结失败可降级；句数超阈值分批（默认 15 句阈值 / 5 句一批 / 并发上限 5）；虚拟线程 + Semaphore；多批进度串行化并过滤句子级预览，避免事件交错。
- **勿夸大：** 限流为单机内存滑动窗口；分批失败为 fail-fast，非部分成功。



#### B. 多模型结构化输出 + Sanitizer

- **做什么：** 策略模式按模型选择 `json_schema+strict` 或千问 JSON Mode（省略 max_tokens、Schema 注入 prompt）。
- **工程点：** 解析失败有限重试；流异常结束可回退同步请求；后置规则校验压假阳性。
- **面试点：** 为何千问不用 strict schema；Sanitizer 误杀风险。



#### C. 可解释四维评分（非 LLM 打分）

- **公式要点：** `penalty = multiplier * (1 - exp(-decay * density))`；`effectiveSentences = n + k` 平滑短对话；单句扣分封顶；FATAL 额外惩罚；四维加权合成综合分。
- **面试点：** 为何不让模型直接打分；权重如何调；与文本诊断的职责边界。



#### D. Agentic RAG（岗位主叙事）

- **做什么：** `@AiService` + DB Tools + 可选 Qdrant 语义检索；短窗 Caffeine 记忆按用户隔离。
- **工程点：** Tool 侧用 `@ToolMemoryId` 传 userId（避免异步丢 SecurityContext）；检索动态 filter 按用户隔离；`AFTER_COMMIT` + 虚拟线程异步索引；未配 Qdrant 时条件不装配，仍可用 DB Tools。
- **面试点：** Agentic vs Naive RAG；索引失败是否影响主流程；无向量库时产品行为。



#### E. LLM 漂移评测

- **做什么：** 同对话连跑 N 次，度量分数波动、类型分布 Jaccard、句子标记翻转率；度量内核与 LLM runner 解耦可单测。
- **面试点：** 与准确性 golden set 的区别；阈值如何定；HIGH_DRIFT 如何定位 Stage。



### 辅助产品点（可选一句）



#### F. 中英分流双通道

- Unicode HAN 规则分流：中文表达建议通道 vs 英文语法通道；失败有 fallback；可写进简历作场景理解，不必占主条。



### 前端（全栈证明，最多两点）

1. **SSE 半包解析 + 流式状态机：** `fetch` + `ReadableStream`，半包缓冲、畸形事件容错、AbortSignal；分析页阶段进度与句子级 commit/live 预览解耦。
2. **鉴权与报告可视化落地：** JWT 路由守卫；诊断详情（分项得分、错误分布图等），证明能把 Agent 能力交付成可用产品。

---



## 6. 精简版（版面紧张时）

**Khan Kiddo v2｜AI 口语诊断与语法复盘 Agent**  
Java 21 / Spring Boot 3 / LangChain4j / MySQL / SSE；Vue3 全栈落地  

- 多阶段 LLM 分析流水线 + 虚拟线程分批并发 + SSE 流式进度与降级。  
- 多模型结构化输出策略 + 结果 Sanitizer；配置驱动四维口语评分。  
- Agentic RAG（DB/语义 Tools、用户隔离记忆与检索、事务后异步索引）+ LLM 漂移回归 Harness。  
- 前端自研 SSE 消费与流式预览，完成 JWT 鉴权与诊断报告可视化。

---



## 7. 关键词（ATS）

`Java 21` `Spring Boot 3` `LangChain4j` `Agent` `Agentic RAG` `Tool Calling` `SSE` `JSON Schema` `虚拟线程` `MyBatis-Plus` `JWT` `Qdrant` `LLM Eval` `多模型编排`

---



## 8. 写作红线（避免翻车）


| 不要写                    | 原因                          |
| ---------------------- | --------------------------- |
| 文章生成 / 词典 / 笔记本 / 情感分析 | v2 未实现或仅占位                  |
| 「全面向量知识库」              | Qdrant 需配置才启用，默认可走 DB Tools |
| 「分布式限流 / Reactive 背压」  | 单机滑动窗口 + Semaphore，勿拔高      |
| 「主导/独立」若非事实            | 按真实职责改措辞                    |


---



## 9. 面试 60 秒口述稿（可背）

> 这个项目是一个口语学习 Agent 系统。用户贴上和 AI 练习的英文字幕后，后端跑一条多阶段流水线：先分离对话，再按是否含汉字分流——英文做语法结构化分析，中文做表达建议——最后生成诊断总结。长对话会虚拟线程分批并发，全程 SSE 推进度。  
> 分数不是模型随便打的，而是按错误类型权重和密度公式算的四维分，可配置、可复现。  
> 复盘侧我做了 Agentic RAG：助手按需调 DB 统计和错句检索 Tool，可选接 Qdrant，并按 userId 隔离；索引在事务提交后异步做。  
> 另外有一套漂移回归，同一对话跑多次，看分数和错句标不稳。前端用 Vue3 把 SSE 流式预览和报告页落地，证明整条链路能交付。

