<script setup lang="ts">
import {DataAnalysis, MagicStick, Promotion, RefreshRight, TrendCharts,} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {computed, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'

import {fetchHomePage} from '@/api/home'
import type {HomePageResponse} from '@/types/home'
import {getErrorMessage} from '@/utils/error'

const router = useRouter()
const loading = ref(true)
const home = ref<HomePageResponse | null>(null)
const revealed = ref(false)

const recentSentences = computed(
    () => home.value?.analysisStats?.recentSentences ?? [],
)

async function loadHome() {
  loading.value = true
  try {
    const {data} = await fetchHomePage()
    home.value = data
  } catch (error) {
    home.value = null
    ElMessage.error(getErrorMessage(error, '加载首页数据失败'))
  } finally {
    loading.value = false
    requestAnimationFrame(() => {
      revealed.value = true
    })
  }
}

function onFeaturePending(name: string) {
  ElMessage.info(`${name}功能迁移中，敬请期待`)
}

onMounted(loadHome)
</script>

<template>
  <div v-loading="loading" class="landing" :class="{ 'landing--revealed': revealed }">
    <section class="hero reveal" style="--reveal-delay: 0ms">
      <div class="hero-glow" aria-hidden="true"/>
      <div class="hero-grid">
        <div class="hero-copy">
          <span class="hero-badge">
            <el-icon><MagicStick/></el-icon>
            AI 纠正助手
          </span>
          <h1 class="hero-title">
            从「会说」到「精通」的
            <span class="hero-title-accent">语境进化之路</span>
          </h1>
          <p class="hero-desc">
            {{ home?.description ?? '粘贴你与 AI 的英文对话，系统会标出可优化表达并给出更地道的改写建议。' }}
          </p>
          <div class="hero-actions">
            <button type="button" class="btn-primary" @click="router.push('/conversation/analyze')">
              <el-icon>
                <Promotion/>
              </el-icon>
              开始深度学习
            </button>
            <button type="button" class="btn-ghost" @click="onFeaturePending('句子笔记本')">
              观看演示 →
            </button>
          </div>
        </div>

        <div class="hero-demo reveal" style="--reveal-delay: 120ms">
          <div class="hero-demo-stage">
            <div class="demo-shadow" aria-hidden="true"/>
            <div class="demo-float-layer">
              <div class="demo-window kk-glass">
                <div class="demo-chrome">
              <span class="demo-dots">
                <i/><i/><i/>
              </span>
                  <span class="demo-label">Doubao · Qwen · GPT</span>
                </div>
                <div class="demo-pane demo-pane--before">
                  <span class="demo-tag">原句</span>
                  <p class="demo-quote">
                    "When I give presentation, I always forget what to say next and feel very embarrassing."
                  </p>
                </div>
                <div class="demo-pane demo-pane--after">
                  <span class="demo-tag demo-tag--ai">AI 建议</span>
                  <p class="demo-improved">
                    When I give presentations, I always lose my train of thought and feel embarrassed.
                  </p>
                  <ul class="correction-list">
                    <li class="correction correction--fatal">可数名词：presentation → presentations</li>
                    <li class="correction correction--warn">词性搭配：embarrassing → embarrassed</li>
                    <li class="correction correction--soft">表达地道：forget what to say → lose my train of thought</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="pillars reveal" style="--reveal-delay: 200ms">
      <article class="pillar">
        <span class="pillar-icon pillar-icon--blue"><el-icon><DataAnalysis/></el-icon></span>
        <h3 class="pillar-heading">智能 AI 语境助手</h3>
        <p class="pillar-body">
          对你与 AI 练习后的对话字幕逐句分析，定位语法与表达可优化点，给出可直接复用的改写建议。
        </p>
        <div class="pillar-tags">
          <span>语义分析</span><span>口语建模</span><span>实时捕捉</span>
        </div>
      </article>

      <article class="pillar pillar--learners">
        <span class="pillar-icon pillar-icon--photo">
          <img
              src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=96&h=96&fit=crop&crop=face"
              alt=""
              loading="lazy"
          />
        </span>
        <h3 class="pillar-heading pillar-heading--gold">致力于深度进阶的学习者</h3>
        <p class="pillar-body">
          适合已有基础、希望说得更准更自然的学习者：备考、面试或跨国协作，都能通过复盘转化表达优势。
        </p>
        <div class="avatar-row">
          <img src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=64&h=64&fit=crop&crop=face" alt=""/>
          <img src="https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=64&h=64&fit=crop&crop=face" alt=""/>
          <img src="https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=64&h=64&fit=crop&crop=face" alt=""/>
          <span class="avatar-badge">10k+</span>
        </div>
      </article>

      <article class="pillar">
        <span class="pillar-icon pillar-icon--olive"><el-icon><RefreshRight/></el-icon></span>
        <h3 class="pillar-heading pillar-heading--green">闭环式学习体验</h3>
        <p class="pillar-body">
          分类复盘、AI 笔记与针对性建议，构建从「发现可优化点」到「肌肉记忆」的完整闭环。
        </p>
        <div class="progress-track">
          <div class="progress-fill"/>
        </div>
        <p class="progress-caption">知识留存效率提升约 75%</p>
      </article>
    </section>

    <section
        v-if="home?.authenticated && home.analysisStats"
        class="dashboard reveal"
        style="--reveal-delay: 280ms"
    >
      <header class="dashboard-head">
        <h2 class="dashboard-title">
          <el-icon>
            <TrendCharts/>
          </el-icon>
          内容分析概览
        </h2>
      </header>

      <div class="stats-grid">
        <div class="stat-card">
          <p class="stat-value">{{ home.analysisStats.seriousIssueCount }}</p>
          <p class="stat-label">历史累计优化点</p>
        </div>
        <div class="stat-card">
          <p class="stat-value stat-value--text">{{ home.analysisStats.mostCommonErrorType }}</p>
          <p class="stat-label">最常见优化类型</p>
        </div>
        <div class="stat-card stat-card--accent">
          <p class="stat-value">{{ home.analysisStats.recent7DaysSentenceCount }}</p>
          <p class="stat-label">最近 7 天分析句子数</p>
        </div>
      </div>

      <template v-if="recentSentences.length">
        <h3 class="recent-head">最近 7 天分析句子</h3>
        <article v-for="(item, idx) in recentSentences" :key="idx" class="recent-item">
          <p class="recent-line">
            <span class="recent-key">原句</span>
            {{ item.originalSentence }}
          </p>
          <p v-if="item.suggestion" class="recent-line">
            <span class="recent-key">AI 修改后</span>
            <span class="recent-suggestion">{{ item.suggestion }}</span>
          </p>
          <div v-if="item.problemTypeTags?.length" class="recent-tags">
            <span v-for="tag in item.problemTypeTags.slice(0, 3)" :key="tag">{{ tag }}</span>
          </div>
        </article>
      </template>
      <p v-else class="dashboard-empty">
        暂无最近 7 天分析句子，
        <button type="button" class="link-btn" @click="router.push('/conversation/analyze')">开始分析</button>
      </p>
    </section>
  </div>
</template>

<style scoped>
.landing {
  font-family: var(--kk-font-body);
  color: var(--kk-color-text);
}

.reveal {
  opacity: 0;
  transform: translateY(18px);
  transition: opacity var(--kk-duration-slow) var(--kk-ease-out),
  transform var(--kk-duration-slow) var(--kk-ease-out);
  transition-delay: var(--reveal-delay, 0ms);
}

.landing--revealed .reveal {
  opacity: 1;
  transform: translateY(0);
}

/* Hero — 单层容器，不再套第二层圆角矩形 */
.hero {
  position: relative;
  padding: 0.5rem 0 1.5rem;
  margin-bottom: 0.5rem;
}

.hero-glow {
  position: absolute;
  top: -20%;
  right: 0;
  width: 48%;
  height: 90%;
  background: radial-gradient(circle, rgba(184, 148, 31, 0.1) 0%, transparent 70%);
  pointer-events: none;
}

.hero-grid {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: clamp(1.5rem, 3vw, 2.5rem);
  align-items: center;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  width: fit-content;
  padding: 0.35rem 0.85rem;
  border-radius: 999px;
  background: var(--kk-color-accent-bg);
  color: var(--kk-color-accent-text);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  border: 1px solid rgba(184, 148, 31, 0.35);
}

.hero-title {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: clamp(2.1rem, 4.2vw, 3.75rem);
  font-weight: 800;
  line-height: 1.06;
  letter-spacing: -0.03em;
  color: var(--kk-color-primary);
}

.hero-title-accent {
  display: block;
  color: var(--kk-color-accent);
  font-style: italic;
}

.hero-desc {
  margin: 0;
  max-width: 34rem;
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--kk-color-text-muted);
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
  margin-top: 0.25rem;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.85rem 1.75rem;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--kk-color-primary) 0%, var(--kk-color-primary-soft) 100%);
  color: #fff;
  font-family: inherit;
  font-size: 0.95rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--kk-shadow-btn);
  transition: transform var(--kk-duration-normal) ease, box-shadow var(--kk-duration-normal) ease;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: var(--kk-shadow-btn-hover);
}

.btn-ghost {
  border: none;
  background: transparent;
  color: var(--kk-color-primary);
  font-family: inherit;
  font-size: 0.92rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0.5rem 0;
  transition: color 0.2s ease;
}

.btn-ghost:hover {
  color: var(--kk-color-accent);
}

/* Demo window — Hero 3D 透视展示 */
.hero-demo {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0.5rem 0 1.5rem;
  perspective: 1400px;
}

.hero-demo-stage {
  position: relative;
  width: 100%;
  max-width: 31.2rem;
  transform-style: preserve-3d;
  transform: rotateY(-14deg) rotateX(7deg);
  transition: transform 1s var(--kk-ease-out);
  will-change: transform;
}

.hero-demo-stage:hover {
  transform: rotateY(-6deg) rotateX(3deg) translateZ(16px);
}

.demo-float-layer {
  transform-style: preserve-3d;
  animation: demo-float-y 7s ease-in-out infinite;
}

.hero-demo-stage:hover .demo-float-layer {
  animation-play-state: paused;
}

@keyframes demo-float-y {
  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-10px);
  }
}

.demo-shadow {
  position: absolute;
  inset: 6% -4% -10% 8%;
  border-radius: var(--kk-radius-lg);
  background: linear-gradient(
      145deg,
      rgba(11, 26, 125, 0.28) 0%,
      rgba(11, 26, 125, 0.12) 55%,
      rgba(184, 148, 31, 0.14) 100%
  );
  filter: blur(22px);
  transform: translateZ(-36px) scale(0.96);
  opacity: 0.85;
  pointer-events: none;
}

.demo-window {
  position: relative;
  z-index: 1;
  border-radius: var(--kk-radius-lg);
  padding: 1.1rem;
  transform: translateZ(24px);
  backface-visibility: hidden;
  box-shadow: var(--kk-glass-shadow),
  inset 0 1px 0 var(--kk-glass-highlight),
  0 28px 56px rgba(11, 26, 125, 0.18);
  transition: box-shadow 1s var(--kk-ease-out);
}

.hero-demo-stage:hover .demo-window {
  box-shadow: var(--kk-glass-shadow),
  inset 0 1px 0 var(--kk-glass-highlight),
  0 36px 64px rgba(11, 26, 125, 0.22);
}

.demo-window::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(
      125deg,
      rgba(255, 255, 255, 0.42) 0%,
      transparent 38%,
      transparent 62%,
      rgba(11, 26, 125, 0.04) 100%
  );
  pointer-events: none;
}

@media (prefers-reduced-motion: reduce) {
  .demo-float-layer {
    animation: none;
  }

  .hero-demo-stage:hover {
    transform: rotateY(-14deg) rotateX(7deg);
  }
}

.demo-chrome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.85rem;
}

.demo-dots {
  display: flex;
  gap: 0.35rem;
}

.demo-dots i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ddd;
  display: block;
}

.demo-dots i:nth-child(1) {
  background: #e8a0a0;
}

.demo-dots i:nth-child(2) {
  background: #e8d44d;
}

.demo-label {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #8b90a0;
}

.demo-pane {
  border-radius: var(--kk-radius-md);
  padding: 1rem 1.05rem;
}

.demo-pane--before {
  background: var(--kk-glass-inner-bg);
  border-left: 3px solid var(--kk-color-accent);
}

.demo-pane--after {
  margin-top: 0.85rem;
  background: var(--kk-glass-inner-bg-muted);
  border: 1px solid var(--kk-glass-inner-border);
}

.demo-tag {
  display: block;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(11, 26, 125, 0.55);
  margin-bottom: 0.4rem;
}

.demo-tag--ai {
  color: var(--kk-color-primary-soft);
}

.demo-quote {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.88rem;
  line-height: 1.65;
  color: #4a5068;
  font-style: italic;
}

.demo-improved {
  margin: 0 0 0.65rem;
  font-family: var(--kk-font-mono);
  font-size: 0.88rem;
  line-height: 1.65;
  color: #1f4da9;
  font-weight: 500;
}

.correction-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.correction {
  display: inline-flex;
  width: fit-content;
  padding: 0.22rem 0.65rem;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
}

.correction--fatal {
  background: #ffecec;
  color: #a01818;
  border: 1px solid rgba(160, 24, 24, 0.2);
}

.correction--warn {
  background: #fff8e0;
  color: #7a6200;
  border: 1px solid rgba(122, 98, 0, 0.22);
}

.correction--soft {
  background: #e8f2ff;
  color: #0e5080;
  border: 1px solid rgba(14, 80, 128, 0.18);
}

/* Pillars */
.pillars {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.pillar {
  background: #fff;
  border-radius: var(--kk-radius-lg);
  padding: 1.5rem 1.35rem;
  border: 1px solid rgba(11, 26, 125, 0.06);
  box-shadow: var(--kk-shadow-card);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.pillar:hover {
  transform: translateY(-3px);
  box-shadow: var(--kk-shadow-card-hover);
}

.pillar-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
  color: #fff;
  font-size: 1.1rem;
  overflow: hidden;
}

.pillar-icon--blue {
  background: linear-gradient(145deg, #1b2b93, #0b1a7d);
}

.pillar-icon--olive {
  background: linear-gradient(145deg, #7a7940, #5c5b2e);
}

.pillar-icon--photo {
  border: 2px solid rgba(201, 162, 39, 0.35);
  padding: 0;
}

.pillar-icon--photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pillar-heading {
  margin: 0 0 0.5rem;
  font-family: var(--kk-font-display);
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.pillar-heading--gold {
  color: #7a6500;
}

.pillar-heading--green {
  color: #2d6a4f;
}

.pillar-body {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.65;
  color: var(--kk-color-text-muted);
}

.pillar-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.85rem;
}

.pillar-tags span {
  padding: 0.2rem 0.5rem;
  border-radius: 6px;
  background: #eef0f5;
  color: #646b7e;
  font-size: 0.68rem;
  font-weight: 700;
}

.avatar-row {
  display: flex;
  align-items: center;
  margin-top: 0.85rem;
}

.avatar-row img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  margin-left: -8px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.avatar-row img:first-child {
  margin-left: 0;
}

.avatar-badge {
  width: 32px;
  height: 32px;
  margin-left: -8px;
  border-radius: 50%;
  background: #e4e6ee;
  border: 2px solid #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.55rem;
  font-weight: 800;
  color: #4a5068;
}

.progress-track {
  margin-top: 0.75rem;
  height: 5px;
  border-radius: 999px;
  background: #e0e2ea;
  overflow: hidden;
}

.progress-fill {
  width: 74%;
  height: 100%;
  background: linear-gradient(90deg, #8a7200, #c9a227);
  border-radius: inherit;
}

.progress-caption {
  margin: 0.45rem 0 0;
  font-size: 0.82rem;
  color: #7a8094;
}

/* Dashboard */
.dashboard {
  background: #fff;
  border-radius: var(--kk-radius-lg);
  padding: clamp(1.25rem, 2vw, 1.75rem);
  border: 1px solid rgba(11, 26, 125, 0.06);
  box-shadow: var(--kk-shadow-card);
}

.dashboard-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 1.25rem;
  font-family: var(--kk-font-display);
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--kk-color-primary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin-bottom: 1.25rem;
}

.stat-card {
  text-align: center;
  padding: 1.1rem 0.75rem;
  border-radius: var(--kk-radius-md);
  background: linear-gradient(160deg, #f5f6fa 0%, #eceef5 100%);
  border: 1px solid rgba(11, 26, 125, 0.05);
}

.stat-card--accent {
  background: linear-gradient(160deg, #edf7f0 0%, #dceee3 100%);
}

.stat-value {
  margin: 0;
  font-family: var(--kk-font-display);
  font-size: 2rem;
  font-weight: 800;
  line-height: 1.1;
  color: var(--kk-color-primary);
}

.stat-value--text {
  font-size: 1.25rem;
}

.stat-card--accent .stat-value {
  color: #2d6a4f;
}

.stat-label {
  margin: 0.35rem 0 0;
  font-size: 0.82rem;
  color: #656b7e;
}

.recent-head {
  margin: 0 0 0.75rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: #656b7e;
  letter-spacing: 0.02em;
}

.recent-item {
  padding: 1rem 1.1rem;
  margin-bottom: 0.65rem;
  border-radius: var(--kk-radius-md);
  background: #f7f8fc;
  border-left: 3px solid var(--kk-color-primary);
}

.recent-line {
  margin: 0 0 0.35rem;
  font-size: 0.9rem;
  line-height: 1.6;
  color: #3d4460;
}

.recent-key {
  display: inline-block;
  min-width: 4.5rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #7a8094;
  margin-right: 0.5rem;
}

.recent-suggestion {
  color: #1f4da9;
  font-weight: 600;
}

.recent-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.5rem;
}

.recent-tags span {
  padding: 0.18rem 0.55rem;
  border-radius: 999px;
  background: #fff3bf;
  color: #6a5700;
  font-size: 0.72rem;
  font-weight: 700;
}

.dashboard-empty {
  margin: 0;
  color: #656b7e;
  font-size: 0.92rem;
}

.link-btn {
  border: none;
  background: none;
  color: var(--kk-color-primary);
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.link-btn:hover {
  color: var(--kk-color-accent);
}

@media (max-width: 992px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .hero-demo {
    perspective: none;
    padding: 0.25rem 0 1rem;
  }

  .hero-demo-stage {
    max-width: none;
    transform: none;
    transition: none;
  }

  .hero-demo-stage:hover {
    transform: none;
  }

  .demo-float-layer {
    animation: none;
  }

  .demo-shadow {
    display: none;
  }

  .demo-window {
    transform: none;
    box-shadow: var(--kk-glass-shadow),
    inset 0 1px 0 var(--kk-glass-highlight);
  }

  .pillars {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
