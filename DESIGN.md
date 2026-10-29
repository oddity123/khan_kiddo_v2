---
name: Khan Kiddo
description: Glass Study — 雾面玻璃自习室里的编辑向口语诊断界面
colors:
  ink-navy: "#0b1a7d"
  ink-navy-soft: "#223194"
  restrained-brass: "#b8941f"
  restrained-brass-soft: "#d4bc6a"
  brass-wash: "#f3ecd4"
  brass-ink: "#5c4a00"
  fog-paper: "#eef1f6"
  fog-mid: "#e8ebf2"
  fog-deep: "#e3e7ef"
  surface-solid: "#ffffff"
  surface-glass: "rgba(255, 255, 255, 0.88)"
  text-ink: "#141824"
  text-secondary: "#3d4460"
  text-muted: "#5a6175"
  text-subtle: "#7a8094"
  success: "#2d6a4f"
  danger: "#a01818"
  warn: "#7a6200"
  link: "#1f4da9"
typography:
  display:
    fontFamily: "Fraunces, Georgia, Songti SC, serif"
    fontSize: "clamp(2.1rem, 4.2vw, 3.75rem)"
    fontWeight: 800
    lineHeight: 1.06
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Fraunces, Georgia, Songti SC, serif"
    fontSize: "clamp(1.35rem, 2.2vw, 1.85rem)"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  title:
    fontFamily: "DM Sans, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "1.14rem"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "normal"
  body:
    fontFamily: "DM Sans, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.7
    letterSpacing: "normal"
  label:
    fontFamily: "DM Sans, PingFang SC, Microsoft YaHei, sans-serif"
    fontSize: "0.78rem"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "0.02em"
  mono:
    fontFamily: "IBM Plex Mono, Menlo, monospace"
    fontSize: "0.88rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
rounded:
  sm: "6px"
  md: "14px"
  lg: "20px"
  xl: "28px"
  pill: "999px"
  nav: "14px"
  dropdown: "12px"
spacing:
  shell-desktop: "80%"
  shell-mobile: "94%"
  navbar-offset: "76px"
  page-gap: "1.25rem"
  btn-padding: "0.85rem 1.75rem"
components:
  button-primary:
    backgroundColor: "{colors.ink-navy}"
    textColor: "#ffffff"
    rounded: "{rounded.pill}"
    padding: "{spacing.btn-padding}"
    typography: "{typography.label}"
  button-primary-hover:
    backgroundColor: "{colors.ink-navy-soft}"
    textColor: "#ffffff"
    rounded: "{rounded.pill}"
    padding: "{spacing.btn-padding}"
  chip-brass:
    backgroundColor: "{colors.brass-wash}"
    textColor: "{colors.brass-ink}"
    rounded: "{rounded.pill}"
    padding: "0.35rem 0.85rem"
    typography: "{typography.label}"
  glass-panel:
    backgroundColor: "rgba(255, 255, 255, 0.72)"
    textColor: "{colors.text-ink}"
    rounded: "{rounded.xl}"
  glass-nav:
    backgroundColor: "rgba(255, 255, 255, 0.38)"
    textColor: "{colors.ink-navy}"
    rounded: "{rounded.nav}"
  card-surface:
    backgroundColor: "{colors.surface-glass}"
    textColor: "{colors.text-ink}"
    rounded: "{rounded.lg}"
---

# Design System: Khan Kiddo

## Overview

**Creative North Star: "Glass Study"**

Khan Kiddo 的界面是一间玻璃自习室：雾面灰蓝底、半透明浮层、海军蓝骨架，暖金只作点缀。气质是沉静专业的学习向杂志版面——玻璃质感服务聚焦，编辑排版服务可读，不抢诊断内容的戏。

密度中等偏疏：大字标题与得分数字用展示衬线压住层级，正文与导航用干净无衬线走完任务流。深度优先靠 Liquid Glass 与雾面底色分层，阴影是同色相低饱和环境光，不是硬黑投影。

**Key Characteristics:**
- Glass Study：毛玻璃浮层 + 雾面纸底
- Editorial Calm：Fraunces 标题 / DM Sans 正文 / 克制暖金点缀
- Token-first：一切色、圆角、玻璃、阴影走 `--kk-*`，禁止平行色板
- Refined and restrained：主按钮有分量但不夸张；组件圆角克制
- Anti-references：黄绿高饱和渐变、Inter/Roboto、紫色 SaaS 风

## Colors

调色板是冷静海军蓝主声部，配一支克制黄铜点缀；中性色全是偏蓝的雾纸，让玻璃浮层「坐」得住。

### Primary
- **Ink Navy** (`#0b1a7d` / `--kk-color-primary`): 品牌骨架——标题、主按钮、导航强调、得分数字。同色相柔化版 **Ink Navy Soft** (`#223194`) 用于渐变终点与 hover。
- **Link Blue** (`#1f4da9` / `--kk-color-link`): 可点击正文链，略亮于主色以区分交互。

### Secondary
- **Restrained Brass** (`#b8941f` / `--kk-color-accent`): 稀缺强调——hero 斜体副标题、关键标签、需要「被看见但不喧哗」的时刻。配套 **Brass Soft** (`#d4bc6a`)、**Brass Wash** (`#f3ecd4`)、**Brass Ink** (`#5c4a00`) 做徽章底与字色。

### Neutral
- **Fog Paper / Mid / Deep** (`#eef1f6` → `#e8ebf2` → `#e3e7ef`): 全页渐变底，带极轻噪声纹理。
- **Text Ink / Secondary / Muted / Subtle** (`#141824` → `#7a8094`): 四级正文层级。
- **Surface Solid / Glass**: 不透明白与半透明白，分别用于实底卡片与玻璃上的内层。

### Semantic
- Success / Danger / Warn 及其浅底：仅用于对错与告警态（改写批注、表单错误），不进入品牌装饰。

### Named Rules
**The One Brass Rule.** Restrained Brass 在任一屏占比应明显少于 Ink Navy；它是高光，不是第二主色。

**The Token-Only Rule.** 颜色只从 `--kk-*` 消费；禁止在组件里硬编码 `#0b1a7d`、`#3498db` 或平行色板。

## Typography

**Display Font:** Fraunces (Georgia / Songti SC)
**Body Font:** DM Sans (PingFang SC / Microsoft YaHei)
**Mono Font:** IBM Plex Mono (Menlo)

**Character:** 展示衬线带一点编辑杂志的温度；正文无衬线冷静清晰；等宽专留给英文例句与代码态诊断文本。

### Hierarchy
- **Display** (800, `clamp(2.1rem, 4.2vw, 3.75rem)`, lh 1.06): Hero 与品牌级标题；主色，斜体副行可用 Brass。
- **Headline** (700, ~1.35–1.85rem): 分区标题、详情页大标题。
- **Title** (600, ~1.14rem): 卡片头、支柱栏目标题。
- **Body** (400, 1rem, lh 1.7): 说明、诊断段落；宜保持约 34rem 内宽（首页导语已用）。
- **Label** (700, ~0.78rem, tracking 0.02em): 徽章、维度名、小标签。
- **Mono** (400, ~0.88rem): 英文原句 / 建议句 / 知识点代码感文本。

### Named Rules
**The Three-Voice Rule.** Display 只扛标题与关键数据；Body 扛任务文案；Mono 只进「语言材料」本身，不进导航。

## Layout

空间模型是「浮动壳 + 居中内容轨」：`AppLayout` 提供全屏 `.kk-page-bg`，内容走 `.kk-page-shell`（桌面 **80%**，≤768px **94%**），与浮动导航对齐。桌面导航 `fixed` 悬浮（非全宽贴边白条），内容顶距约 **76px**（`--kk-navbar-offset`）；≤992px 改底部玻璃 Tab 栏，顶距收紧。

密度：营销首页可更疏（hero + 透视 demo）；Operate 页（分析、复盘）保持同一壳宽与玻璃面板，避免另起一套栅格。子页面禁止再铺全屏背景色。

### Named Rules
**The One Shell Rule.** 新页面必须进 `AppLayout` + `.kk-page-shell`；不要发明第二套内容宽度。

## Elevation & Depth

**Glass-first hybrid：** 默认深度来自雾面底色分层 + Liquid Glass（半透明白、blur、saturate、内高光），不是扁平卡片堆叠，也不是重阴影拟物。阴影一律染成 Ink Navy 低透明环境光，作「玻璃压在雾纸上」的环境接触，而非黑色 drop shadow。

### Shadow Vocabulary
- **Glass ambient** (`0 8px 32px rgba(11, 26, 125, 0.12)`): 默认 `.kk-glass`
- **Panel soft** (`0 24px 48px rgba(11, 26, 125, 0.1)`): `.kk-glass--panel` / 大块落地
- **Card / card-hover** (`0 16px 32px …` / `0 22px 40px …`): 可交互卡片抬升
- **Button / button-hover** (`0 12px 28px …0.28` / `0 16px 32px …0.32`): 主 CTA 重量感
- **Nav** (`0 12px 40px rgba(11, 26, 125, 0.14)` + 内外高光): `.kk-glass--nav`

### Named Rules
**The Navy Shadow Rule.** 阴影色相跟主色，禁止中性黑灰大投影。

**The No Scoped Glass Rule.** 禁止在组件 scoped CSS 里手写 `backdrop-filter` 或复制半透明玻璃底；只用 `.kk-glass*` 与 `--kk-glass-*`。

## Shapes

圆角阶梯偏「柔和编辑」而非全圆玩具感：控件常用 **6 / 14 / 20 / 28px**；主 CTA 与徽章用 **pill (999px)**；导航圆角 **14px**，下拉 **12px**。玻璃面板外轮廓偏大圆角（xl），内层嵌套略收。边框多为低透明海军蓝或玻璃白描边，避免粗灰线框。

### Named Rules
**The Pill-For-CTA Rule.** 全圆胶囊留给主行动与徽章；内容面板不要一律 pill。

## Components

整体手感：**refined and restrained**——主按钮有重量与轻抬，其余浮层安静。

### Buttons
- **Shape:** 胶囊 (`999px`)
- **Primary:** Ink Navy → Soft 135° 渐变，白字，padding `0.85rem 1.75rem`，字重 700；阴影 `--kk-shadow-btn`
- **Hover:** `translateY(-2px)` + `--kk-shadow-btn-hover`
- **Elsewhere:** 任务页优先 Element Plus 主色桥接（`--el-color-primary` → Ink Navy），勿另起一套品牌按钮色

### Chips
- **Brass badge:** Brass Wash 底 + Brass Ink 字 + 淡黄铜描边，pill；用于 hero 标签等稀缺强调

### Cards / Containers
- **Liquid Glass default** (`.kk-glass`): 演示窗、小浮层
- **Panel** (`.kk-glass--panel`): 落地主内容块，xl 圆角
- **Solid / muted surface:** 需要不透明可读区时用 surface token，仍消费 `--kk-*`

### Inputs / Fields
- 以 Element Plus 为主，圆角桥接 `--kk-radius-md/sm`；焦点与错误态走语义色，不引入第三品牌色

### Navigation
- **Desktop:** `.kk-glass.kk-glass--nav` 浮动条；字色 Ink Navy；激活态玻璃 hover 底 + 内描边或主色渐变强调
- **Mobile:** 底部同系玻璃 Tab；「更多」用玻璃 sheet
- **Dropdown:** `kk-nav-dropdown` 全局样式（挂 body），与 nav 同系 blur，避免动画打断毛玻璃

### Signature: Diagnosis demo
- Hero 透视玻璃窗：原句 / AI 建议分栏；批注用 semantic 色阶（fatal / warn / soft），不是装饰彩虹

### Signature: Score strip
- 综合自然度大数字用 Display；四维条主色填充，表达自然维可偏 accent 强调；分数可读优先于图表装饰

### Signature: Growth flashcards
- 闪卡是交互容器：玻璃或实面均可，但字阶仍服从 Display（短语）/ Mono 或 Body（例句）；勿做成高饱和游戏卡

## Do's and Don'ts

### Do:
- **Do** 新 UI 只引用 `--kk-*` 与 `.kk-glass*` / `.kk-page-*`。
- **Do** 保持 Glass Study：雾底 + 玻璃浮层 + Ink Navy 骨架 + 稀缺 Brass。
- **Do** 标题/关键数据用 Fraunces，导航与正文用 DM Sans，语言材料用 IBM Plex Mono。
- **Do** 页面进 `AppLayout`，内容宽用 `.kk-page-shell`。
- **Do** 阴影保持海军蓝环境光；主 CTA 可用轻 `translateY` 抬升。

### Don't:
- **Don't** 使用黄绿高饱和渐变、紫色 SaaS 风、Inter/Roboto/Arial 作为品牌字体。
- **Don't** 在组件内重定义 `:root` token 或硬编码品牌色。
- **Don't** 在 scoped 样式里复制 `backdrop-filter` / 半透明玻璃配方。
- **Don't** 把 Restrained Brass 铺成大面积主色或第二主题。
- **Don't** 用中性黑大阴影或全屏贴边实心顶栏替代浮动玻璃导航。
