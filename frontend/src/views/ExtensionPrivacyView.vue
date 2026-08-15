<script setup lang="ts">
import {onMounted} from 'vue'

import {signalPrerenderReady} from '@/utils/prerender'

const updatedAt = '2026 年 8 月 15 日'

onMounted(() => {
  signalPrerenderReady()
})
</script>

<template>
  <article class="privacy-page">
    <header class="privacy-hero kk-glass kk-glass--panel">
      <div class="hero-copy">
        <p class="eyebrow">Browser extension · Privacy</p>
        <h1>Khan Kiddo 浏览器扩展隐私政策</h1>
        <p class="hero-lead">
          我们把数据边界写清楚：扩展只在你主动操作后，读取指定的 ChatGPT 公开分享内容，并将其导入 Khan Kiddo 分析页。
        </p>
        <div class="hero-meta">
          <span>生效及更新日期：{{ updatedAt }}</span>
          <span class="status"><i/> Chrome / Edge 适用</span>
        </div>
      </div>
      <div class="purpose-card" aria-label="扩展单一用途">
        <span class="purpose-number">01</span>
        <p>扩展的单一用途</p>
        <strong>导入用户指定的公开对话，帮助用户发起英语学习分析。</strong>
      </div>
    </header>

    <div class="privacy-layout">
      <aside class="privacy-summary">
        <p class="summary-title">快速摘要</p>
        <ul>
          <li><span>主动触发</span>不会在后台持续扫描网页</li>
          <li><span>公开内容</span>不读取 ChatGPT 密码或登录 Cookie</li>
          <li><span>临时传递</span>导入内容被分析页读取后即从会话缓存移除</li>
          <li><span>用途限定</span>不出售数据，不用于个性化广告</li>
        </ul>
        <router-link to="/feedback" class="contact-link">隐私问题与数据请求 →</router-link>
      </aside>

      <div class="privacy-content">
        <section>
          <span class="section-index">01</span>
          <h2>适用范围与运营者</h2>
          <p>
            本政策适用于 Khan Kiddo 在 Google Chrome 和 Microsoft Edge 上发布的浏览器扩展。扩展由 Khan Kiddo
            项目运营者提供。本政策仅说明扩展导入功能的数据处理；当你继续使用 Khan Kiddo 网站的账号、分析、历史记录或留言功能时，相关页面展示的说明也同时适用。
          </p>
        </section>

        <section>
          <span class="section-index">02</span>
          <h2>我们处理哪些数据</h2>
          <div class="data-grid">
            <div>
              <h3>你主动提供的分享信息</h3>
              <p>ChatGPT 公开分享链接，以及链接中包含的公开分享 ID。</p>
            </div>
            <div>
              <h3>公开页面中的对话内容</h3>
              <p>分享标题、用户与助手的文本消息、Voice 转写字幕、消息数量及导入时间。</p>
            </div>
            <div>
              <h3>本地偏好</h3>
              <p>你是否同意扩展数据说明；开发调试版本还可能保存你主动填写的测试站点地址。</p>
            </div>
            <div>
              <h3>必要的服务日志</h3>
              <p>当你访问 Khan Kiddo 网站时，服务器可能产生时间、请求状态、IP 地址等安全与运行日志。</p>
            </div>
          </div>
          <div class="notice">
            <strong>扩展不会读取</strong>
            <p>你的 ChatGPT 密码、身份验证 Cookie、私密会话列表、浏览器历史记录，也不会持续监控你访问的其他网站。</p>
          </div>
        </section>

        <section>
          <span class="section-index">03</span>
          <h2>数据如何流转</h2>
          <ol class="flow-list">
            <li>
              <span>1</span>
              <div><strong>你主动发起</strong><p>粘贴公开分享链接，或在公开分享页点击“导入到 Khan Kiddo”。</p></div>
            </li>
            <li>
              <span>2</span>
              <div><strong>扩展在本机整理内容</strong><p>扩展从 ChatGPT 公共分享接口获取 JSON，并在浏览器内提取标题和对话文本。</p></div>
            </li>
            <li>
              <span>3</span>
              <div><strong>临时交给分析页</strong><p>内容被写入 khankiddo.top 分析页的会话缓存；页面读取后会立即移除该缓存项。</p></div>
            </li>
            <li>
              <span>4</span>
              <div><strong>由你决定是否分析</strong><p>只有你继续点击“开始分析”后，内容才会提交到 Khan Kiddo 服务端和所选 AI 服务。</p></div>
            </li>
          </ol>
        </section>

        <section>
          <span class="section-index">04</span>
          <h2>使用目的与第三方服务</h2>
          <p>我们仅为完成扩展公开说明的导入与分析功能而处理上述数据，包括解析分享、填充分析输入框、生成分析结果、保障服务安全和排查故障。</p>
          <p>
            获取公开分享内容时，浏览器会连接 ChatGPT（OpenAI）服务。你开始 AI 分析后，对话内容可能根据页面显示的模型选择和处理阶段，发送至
            Khan Kiddo 服务端，以及豆包/火山方舟或通义千问等 AI 服务提供方。我们不会将扩展取得的数据出售给第三方，也不会用于个性化、重定向或兴趣广告。
          </p>
          <p class="limited-use">
            Khan Kiddo 扩展对用户数据的使用遵守 Chrome Web Store User Data Policy 的 Limited Use
            要求：数据仅用于提供或改进扩展面向用户的单一功能，并仅在提供该功能、安全防护、履行法律义务或取得用户明确同意的其他必要情形下传输。
          </p>
        </section>

        <section>
          <span class="section-index">05</span>
          <h2>存储期限与删除</h2>
          <ul class="policy-list">
            <li>扩展不会持久保存导入的对话正文；分析页读取后会从会话缓存移除。</li>
            <li>未登录体验的分析结果不会进入账号历史记录；页面刷新后可能无法恢复。</li>
            <li>登录用户主动保存或产生的分析记录，会保留在账号中，直至用户在历史记录中删除。</li>
            <li>同意状态会保存在浏览器扩展本地存储中；你可以在扩展弹窗取消同意，或卸载扩展以移除。</li>
            <li>安全及运行日志按照保障系统安全和排查故障所需的合理期限保留。</li>
          </ul>
        </section>

        <section>
          <span class="section-index">06</span>
          <h2>安全、你的选择与未成年人</h2>
          <p>
            扩展和生产站使用 HTTPS 传输数据，并通过限定网站访问范围、最小权限和 Manifest V3 限制扩展能力。任何网络服务都无法承诺绝对安全，因此请勿在公开分享中放入密码、证件号码、支付信息或其他不必要的敏感信息。
          </p>
          <p>
            你可以拒绝或撤回扩展中的数据使用同意、停止导入、删除分析历史，或通过留言页面提出访问、更正、删除及其他隐私请求。请只导入你有权处理的内容；未成年人应在监护人指导下使用本服务，不应提交自己或他人的敏感个人信息。
          </p>
        </section>

        <section>
          <span class="section-index">07</span>
          <h2>政策更新与联系我们</h2>
          <p>
            如果扩展的数据种类、用途或共享对象发生实质变化，我们会更新本页面，并在扩展界面中重新提示和取得同意。若你对本政策、扩展权限或数据处理有疑问，请通过
            <router-link to="/feedback">Khan Kiddo 留言页面</router-link>联系我们。
          </p>
        </section>

        <footer class="privacy-footer">
          <img src="/icon.svg" alt=""/>
          <div><strong>Khan Kiddo</strong><span>Learn clearly. Speak freely.</span></div>
          <router-link to="/">返回首页</router-link>
        </footer>
      </div>
    </div>
  </article>
</template>

<style scoped>
.privacy-page {
  width: min(1120px, 100%);
  margin: 1.5rem auto 3rem;
  color: var(--kk-color-text);
}

.privacy-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(240px, 0.7fr);
  gap: clamp(2rem, 6vw, 5rem);
  padding: clamp(2rem, 5vw, 4.5rem);
  position: relative;
  overflow: hidden;
}

.privacy-hero::after {
  content: '';
  position: absolute;
  width: 330px;
  height: 330px;
  right: -150px;
  top: -160px;
  border: 1px solid rgba(184, 148, 31, 0.28);
  border-radius: 50%;
  box-shadow: 0 0 0 46px rgba(184, 148, 31, 0.035), 0 0 0 92px rgba(11, 26, 125, 0.025);
}

.hero-copy { position: relative; z-index: 1; }
.eyebrow,
.section-index {
  color: var(--kk-color-accent-text);
  font-family: var(--kk-font-mono);
  font-size: 0.72rem;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1 {
  max-width: 720px;
  margin: 0.75rem 0 1.25rem;
  color: var(--kk-color-primary);
  font-family: var(--kk-font-display);
  font-size: clamp(2.25rem, 5vw, 4.25rem);
  line-height: 1.02;
  letter-spacing: -0.04em;
}

.hero-lead {
  max-width: 670px;
  margin: 0;
  color: var(--kk-color-text-secondary);
  font-size: clamp(1rem, 1.6vw, 1.18rem);
  line-height: 1.75;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.5rem;
  margin-top: 2rem;
  color: var(--kk-color-text-muted);
  font-size: 0.82rem;
}

.status { display: inline-flex; align-items: center; gap: 0.45rem; }
.status i { width: 7px; height: 7px; border-radius: 50%; background: var(--kk-color-success); box-shadow: 0 0 0 4px rgba(45, 106, 79, 0.12); }

.purpose-card {
  align-self: end;
  min-height: 230px;
  padding: 1.5rem;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: end;
  border-radius: var(--kk-radius-lg);
  color: white;
  background: var(--kk-color-primary);
  box-shadow: var(--kk-shadow-btn);
}

.purpose-number {
  position: absolute;
  top: 1rem;
  right: 1.25rem;
  color: rgba(255, 255, 255, 0.2);
  font-family: var(--kk-font-display);
  font-size: 4.5rem;
  line-height: 1;
}

.purpose-card p { margin: 0 0 0.55rem; color: var(--kk-color-accent-soft); font: 500 0.72rem/1.2 var(--kk-font-mono); letter-spacing: 0.08em; text-transform: uppercase; }
.purpose-card strong { font-family: var(--kk-font-display); font-size: 1.35rem; line-height: 1.45; }

.privacy-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: clamp(2rem, 6vw, 5rem);
  margin-top: 2.5rem;
  padding: 0 clamp(0.5rem, 3vw, 2.5rem);
}

.privacy-summary {
  align-self: start;
  position: sticky;
  top: 90px;
  padding: 1.4rem;
  border-top: 3px solid var(--kk-color-accent);
  border-radius: 0 0 var(--kk-radius-md) var(--kk-radius-md);
  background: rgba(255, 255, 255, 0.62);
  box-shadow: var(--kk-shadow-card);
}

.summary-title { margin: 0 0 1rem; color: var(--kk-color-primary); font-family: var(--kk-font-display); font-size: 1.25rem; font-weight: 700; }
.privacy-summary ul { margin: 0; padding: 0; list-style: none; }
.privacy-summary li { padding: 0.85rem 0; color: var(--kk-color-text-muted); font-size: 0.8rem; line-height: 1.55; border-bottom: 1px solid var(--kk-color-border); }
.privacy-summary li span { display: block; margin-bottom: 0.15rem; color: var(--kk-color-text); font-weight: 700; }
.contact-link { display: inline-block; margin-top: 1.2rem; color: var(--kk-color-primary); font-size: 0.82rem; font-weight: 700; text-decoration: none; }

.privacy-content { min-width: 0; }
.privacy-content > section { position: relative; padding: 0.5rem 0 3.2rem; }
.privacy-content > section + section { padding-top: 3.2rem; border-top: 1px solid rgba(11, 26, 125, 0.13); }
.privacy-content h2 { margin: 0.45rem 0 1.25rem; color: var(--kk-color-primary); font-family: var(--kk-font-display); font-size: clamp(1.65rem, 3vw, 2.35rem); letter-spacing: -0.025em; }
.privacy-content h3 { margin: 0 0 0.5rem; color: var(--kk-color-text); font-size: 0.95rem; }
.privacy-content p,
.policy-list { color: var(--kk-color-text-secondary); font-size: 0.95rem; line-height: 1.85; }
.privacy-content a { color: var(--kk-color-link); font-weight: 700; }

.data-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin: 1.5rem 0; }
.data-grid > div { padding: 1.25rem; border: 1px solid rgba(11, 26, 125, 0.09); border-radius: var(--kk-radius-md); background: rgba(255, 255, 255, 0.68); }
.data-grid p { margin: 0; font-size: 0.85rem; line-height: 1.65; }

.notice,
.limited-use { margin-top: 1.4rem; padding: 1.15rem 1.25rem; border-left: 3px solid var(--kk-color-accent); background: var(--kk-color-accent-bg); }
.notice strong { color: var(--kk-color-accent-text); }
.notice p { margin: 0.35rem 0 0; font-size: 0.86rem; line-height: 1.65; }
.limited-use { color: var(--kk-color-accent-text) !important; font-size: 0.86rem !important; line-height: 1.7 !important; }

.flow-list { margin: 1.5rem 0 0; padding: 0; list-style: none; }
.flow-list li { display: grid; grid-template-columns: 42px 1fr; gap: 1rem; position: relative; padding-bottom: 1.5rem; }
.flow-list li:not(:last-child)::before { content: ''; position: absolute; left: 20px; top: 40px; bottom: 0; width: 1px; background: rgba(11, 26, 125, 0.18); }
.flow-list li > span { width: 42px; height: 42px; display: grid; place-items: center; border-radius: 50%; color: white; background: var(--kk-color-primary); font-family: var(--kk-font-mono); font-size: 0.78rem; }
.flow-list strong { color: var(--kk-color-text); }
.flow-list p { margin: 0.2rem 0 0; font-size: 0.88rem; line-height: 1.65; }
.policy-list { padding-left: 1.2rem; }
.policy-list li { margin: 0.55rem 0; padding-left: 0.35rem; }

.privacy-footer { display: flex; align-items: center; gap: 0.75rem; margin-top: 1rem; padding-top: 2rem; border-top: 3px solid var(--kk-color-primary); }
.privacy-footer img { width: 44px; height: 44px; object-fit: contain; }
.privacy-footer div { display: flex; flex-direction: column; }
.privacy-footer strong { color: var(--kk-color-primary); }
.privacy-footer span { color: var(--kk-color-text-muted); font-size: 0.72rem; }
.privacy-footer a { margin-left: auto; color: var(--kk-color-primary); font-size: 0.85rem; text-decoration: none; }

@media (max-width: 800px) {
  .privacy-hero { grid-template-columns: 1fr; }
  .purpose-card { min-height: 190px; }
  .privacy-layout { grid-template-columns: 1fr; }
  .privacy-summary { position: static; }
}

@media (max-width: 560px) {
  .privacy-page { margin-top: 0.75rem; }
  .privacy-hero { padding: 1.5rem; border-radius: var(--kk-radius-lg); }
  .data-grid { grid-template-columns: 1fr; }
  .privacy-layout { padding: 0 0.2rem; }
  .privacy-content > section { padding-bottom: 2.4rem; }
  .privacy-content > section + section { padding-top: 2.4rem; }
  .privacy-footer { align-items: flex-start; flex-wrap: wrap; }
  .privacy-footer a { width: 100%; margin: 0.5rem 0 0; }
}

@media (prefers-reduced-motion: reduce) {
  * { scroll-behavior: auto !important; }
}
</style>
