<template>
  <section class="module-page preview-page">
    <header class="module-heading"><h1>比赛预览</h1><p>以普通参赛用户视角只读查看现有比赛页面。</p></header>
    <div class="preview-toolbar"><el-radio-group v-model="activePath" size="small"><el-radio-button v-for="item in destinations" :key="item.path" :label="item.path">{{ item.label }}</el-radio-button></el-radio-group><el-button icon="el-icon-refresh" circle title="刷新预览" @click="reload" /></div>
    <div class="preview-frame"><iframe :key="frameKey" :src="frameUrl" :title="`比赛预览-${activeLabel}`" /></div>
  </section>
</template>
<script>
export default {
  data: () => ({ activePath: '/Publicity', frameKey: 0, destinations: [{ label: '首页', path: '/Publicity' }, { label: '赛程赛规', path: '/Home' }, { label: '当前赛卷', path: '/Question' }, { label: '成果验证', path: '/Detect' }] }),
  computed: {
    frameUrl () { return `${window.location.origin}${window.location.pathname}#${this.activePath}?preview=1` },
    activeLabel () { const item = this.destinations.find(item => item.path === this.activePath); return item ? item.label : '比赛页面' }
  },
  methods: { reload () { this.frameKey += 1 } }
}
</script>
<style scoped>.preview-toolbar{display:flex;justify-content:space-between;gap:16px;margin-bottom:12px}.preview-frame{height:calc(100vh - 220px);min-height:560px;border:1px solid #d9e2e8;background:#fff}.preview-frame iframe{width:100%;height:100%;border:0}@media(max-width:650px){.preview-toolbar{align-items:flex-start}.preview-toolbar .el-radio-group{display:grid;grid-template-columns:repeat(2,1fr)}.preview-frame{min-height:500px}}</style>
