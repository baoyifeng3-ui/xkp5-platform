<template>
  <div class="competition-shell">
    <header class="competition-topbar">
      <div class="competition-brand"><span class="competition-mark">竞</span><strong>{{ brandName }}</strong></div>
      <div class="competition-status" aria-live="polite"><small>剩余时间</small><strong class="competition-clock">{{ remainingText }}</strong><span>{{ paperLabel }}</span></div>
      <div class="competition-user">{{ userName }}<button type="button" aria-label="退出登录" @click="$emit('logout')"><i class="el-icon-switch-button" aria-hidden="true" /></button></div>
    </header>
    <nav class="competition-tabs" aria-label="比赛功能">
      <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeKey === tab.key }" @click="$emit('select', tab)">{{ tab.label }}</button>
    </nav>
    <main class="competition-content"><slot /></main>
  </div>
</template>

<script>
export default {
  name: 'CompetitionShell',
  props: {
    brandName: { type: String, default: '人工智能教学实训比赛平台' },
    paperLabel: { type: String, default: '当前赛卷未选择' },
    remainingText: { type: String, default: '比赛时间未设置' },
    userName: { type: String, default: '参赛用户' },
    activeKey: { type: String, default: '' },
    tabs: { type: Array, default: () => [] }
  }
}
</script>

<style>
@import url(../assets/style/platform-theme.css);
.competition-shell { min-height: 100vh; color: var(--ui-text); background: var(--ui-page); }
.competition-topbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; min-height: 64px; padding: 0 28px; color: #eef3ff; background: #172a4b; }
.competition-brand, .competition-user { display: flex; align-items: center; gap: 10px; min-width: 0; font-size: 13px; }
.competition-brand strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.competition-mark { display: grid; place-items: center; flex: 0 0 32px; width: 32px; height: 32px; color: #17376e; background: #dce8ff; border-radius: 8px; font-weight: 800; }
.competition-status { display: flex; align-items: center; gap: 12px; }
.competition-status small { color: #aebbd1; font-size: 11px; }
.competition-clock { font-family: Consolas, Monaco, monospace; font-size: 21px; font-variant-numeric: tabular-nums; letter-spacing: .02em; }
.competition-status > span { padding: 6px 9px; color: #d7e4ff; background: #29446e; border-radius: 6px; font-size: 11px; }
.competition-user { color: #d5deed; font-size: 12px; }
.competition-user button { display: inline-grid; place-items: center; width: 36px; height: 36px; color: inherit; background: transparent; border: 0; border-radius: 50%; cursor: pointer; }
.competition-user button:hover, .competition-user button:focus-visible { color: #fff; background: rgba(255,255,255,.12); outline: 2px solid #a9c4ff; outline-offset: 2px; }
.competition-tabs { display: flex; align-items: center; gap: 4px; min-height: 48px; padding: 0 28px; background: var(--ui-surface); border-bottom: 1px solid var(--ui-border); }
.competition-tabs button { min-height: 44px; padding: 0 16px; color: var(--ui-muted); background: transparent; border: 0; border-bottom: 2px solid transparent; cursor: pointer; }
.competition-tabs button:hover, .competition-tabs button:focus-visible { color: var(--ui-primary-strong); outline: none; }
.competition-tabs button.active { color: var(--ui-primary); border-bottom-color: var(--ui-primary); font-weight: 650; }
.competition-content { min-width: 0; padding: 20px 28px 36px; }
@media (max-width: 720px) { .competition-topbar { align-items: flex-start; flex-wrap: wrap; gap: 10px; padding: 12px 16px; } .competition-status { order: 3; width: 100%; justify-content: space-between; } .competition-clock { font-size: 18px; } .competition-tabs { overflow-x: auto; padding: 0 12px; } .competition-tabs button { flex: 0 0 auto; padding: 0 12px; white-space: nowrap; } .competition-content { padding: 12px; } }
@media (prefers-reduced-motion: reduce) { .competition-user button, .competition-tabs button { transition: none; } }
</style>
