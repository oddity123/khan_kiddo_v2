<script setup lang="ts">
defineProps<{
  kind: 'analyze' | 'cards' | 'review' | 'learners'
}>()
</script>

<template>
  <span class="pillar-mark" :class="`pillar-mark--${kind}`" aria-hidden="true">
    <!-- 智能 AI 语境助手：波形 + 评分环（主色海军，暖金更重） -->
    <svg
        v-if="kind === 'analyze'"
        class="pillar-mark-svg pillar-mark-svg--analyze"
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
    >
      <circle
          cx="16"
          cy="16"
          r="11.2"
          stroke="currentColor"
          stroke-width="1.7"
          opacity="0.28"
      />
      <path
          d="M16 4.8a11.2 11.2 0 0 1 9.7 5.6"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
          class="pillar-mark-accent"
      />
      <path
          d="M10.2 18.2V13.8M13.4 20.4V11.6M16.6 21.2V10.8M19.8 19.6V12.4M23 17.4v-2.8"
          stroke="currentColor"
          stroke-width="2.05"
          stroke-linecap="round"
      />
      <circle cx="24.6" cy="8.8" r="2.35" class="pillar-mark-accent" fill="currentColor"/>
    </svg>

    <!-- 知识卡片：叠卡（石板蓝灰，与海军主色区分） -->
    <svg
        v-else-if="kind === 'cards'"
        class="pillar-mark-svg"
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
    >
      <rect
          x="6.2"
          y="9.2"
          width="14.2"
          height="11.2"
          rx="2.1"
          fill="currentColor"
          opacity="0.18"
          transform="rotate(-10 13.3 14.8)"
      />
      <rect
          x="6.2"
          y="9.2"
          width="14.2"
          height="11.2"
          rx="2.1"
          stroke="currentColor"
          stroke-width="1.45"
          opacity="0.55"
          transform="rotate(-10 13.3 14.8)"
      />
      <rect
          x="9.4"
          y="10.4"
          width="15"
          height="12.2"
          rx="2.2"
          fill="currentColor"
          opacity="0.12"
      />
      <rect
          x="9.4"
          y="10.4"
          width="15"
          height="12.2"
          rx="2.2"
          stroke="currentColor"
          stroke-width="1.65"
      />
      <path
          d="M12.8 14.6h8.4M12.8 17.8h5.6"
          stroke="currentColor"
          stroke-width="1.5"
          stroke-linecap="round"
      />
      <path
          d="M21.6 11.2h2.2v2.2"
          stroke="currentColor"
          stroke-width="1.45"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="pillar-mark-accent"
      />
    </svg>

    <!-- 复盘助手：对话气泡 + 洞察折线 -->
    <svg
        v-else-if="kind === 'review'"
        class="pillar-mark-svg"
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
    >
      <path
          d="M8.2 9.4h9.4c1.5 0 2.7 1.2 2.7 2.7v4.1c0 1.5-1.2 2.7-2.7 2.7h-2.1l-2.6 2.3v-2.3H8.2c-1.5 0-2.7-1.2-2.7-2.7v-4.1c0-1.5 1.2-2.7 2.7-2.7Z"
          stroke="currentColor"
          stroke-width="1.55"
          stroke-linejoin="round"
          opacity="0.5"
      />
      <path
          d="M14.6 12.8h9.2c1.45 0 2.6 1.15 2.6 2.6v4.3c0 1.45-1.15 2.6-2.6 2.6h-2.3l-2.5 2.2v-2.2h-4.4c-1.45 0-2.6-1.15-2.6-2.6v-4.3c0-1.45 1.15-2.6 2.6-2.6Z"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linejoin="round"
      />
      <path
          d="M16.4 17.2h5.8M16.4 19.7h3.6"
          stroke="currentColor"
          stroke-width="1.4"
          stroke-linecap="round"
      />
      <circle cx="23.8" cy="11.4" r="1.45" class="pillar-mark-accent" fill="currentColor"/>
    </svg>

    <!-- 深度学习者：上升轨迹 + 目标点 -->
    <svg
        v-else
        class="pillar-mark-svg"
        viewBox="0 0 32 32"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
    >
      <path
          d="M7.5 21.5c2.2-1.4 4.1-4.2 5.8-6.6 1.9-2.7 3.7-5.2 6.4-6.4"
          stroke="currentColor"
          stroke-width="1.65"
          stroke-linecap="round"
      />
      <path
          d="M7.5 21.5H24.2"
          stroke="currentColor"
          stroke-width="1.45"
          stroke-linecap="round"
          opacity="0.4"
      />
      <path
          d="M16.2 9.2h3.9v3.9"
          stroke="currentColor"
          stroke-width="1.55"
          stroke-linecap="round"
          stroke-linejoin="round"
      />
      <circle cx="22.8" cy="8.6" r="2.05" class="pillar-mark-accent" fill="currentColor"/>
      <circle cx="10.2" cy="19.8" r="1.25" fill="currentColor" opacity="0.75"/>
    </svg>
  </span>
</template>

<style scoped>
.pillar-mark {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0;
  color: #f4f6ff;
  position: relative;
  overflow: hidden;
  box-shadow:
      0 8px 18px color-mix(in srgb, var(--kk-color-primary) 22%, transparent),
      inset 0 1px 0 rgba(255, 255, 255, 0.22);
}

.pillar-mark::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
      145deg,
      rgba(255, 255, 255, 0.22) 0%,
      transparent 42%,
      transparent 100%
  );
  pointer-events: none;
}

/* 语境助手：海军主色 + 暖金描边，并排时仍保持辨识度 */
.pillar-mark--analyze {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  background: linear-gradient(
      155deg,
      color-mix(in srgb, var(--kk-color-primary-soft) 90%, white) 0%,
      var(--kk-color-primary) 48%,
      #07124f 100%
  );
  box-shadow:
      0 10px 22px color-mix(in srgb, var(--kk-color-primary) 32%, transparent),
      0 0 0 1px color-mix(in srgb, var(--kk-color-accent) 35%, transparent),
      inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

/* 知识卡片：石板蓝灰，与海军主色拉开 */
.pillar-mark--cards {
  background: linear-gradient(155deg, #4a5a72 0%, #2f3d52 52%, #1c2636 100%);
  box-shadow:
      0 10px 22px rgba(28, 38, 54, 0.28),
      inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.pillar-mark--review {
  background: linear-gradient(155deg, #1f6e6e 0%, #134848 55%, #0c3434 100%);
}

.pillar-mark--learners {
  background: linear-gradient(
      155deg,
      color-mix(in srgb, var(--kk-color-accent) 55%, #5c4a00) 0%,
      #8a7200 48%,
      #5c4a00 100%
  );
  box-shadow:
      0 10px 22px color-mix(in srgb, var(--kk-color-accent) 28%, transparent),
      inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.pillar-mark-svg {
  width: 22px;
  height: 22px;
  position: relative;
  z-index: 1;
}

.pillar-mark-svg--analyze {
  width: 24px;
  height: 24px;
}

.pillar-mark-accent {
  color: var(--kk-color-accent-soft);
}

.pillar-mark--analyze .pillar-mark-accent {
  color: #e8c84a;
}

.pillar-mark--cards .pillar-mark-accent {
  color: #d4b84a;
}

.pillar-mark--learners .pillar-mark-accent {
  color: #fff6d8;
}
</style>
