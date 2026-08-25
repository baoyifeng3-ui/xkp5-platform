<template>
  <section v-loading="loading" class="module-page preview-page">
    <header class="module-heading">
      <h1>参赛端预览</h1>
      <p>固定显示比赛模式下普通用户登录后的参赛界面。</p>
    </header>
    <div v-if="loadError" class="preview-error" role="alert">
      <i class="el-icon-warning-outline" />
      <strong>普通用户界面加载失败</strong>
      <span>无法获取平台当前模式，请稍后重试。</span>
      <el-button type="primary" size="small" @click="loadPlatformMode">重新加载</el-button>
    </div>
    <div v-else-if="homePath" class="preview-frame">
      <iframe :src="frameUrl" :title="frameTitle" />
    </div>
  </section>
</template>
<script>
const { previewHome } = require('@/services/participantPreview')

export default {
  name: 'CompetitionPreview',
  data: () => ({ loading: false, loadError: false, mode: 'COMPETITION', homePath: '' }),
  computed: {
    frameUrl () { return `${window.location.origin}${window.location.pathname}#${this.homePath}?preview=1` },
    frameTitle () { return this.mode === 'COMPETITION' ? '比赛模式普通用户界面预览' : '实训模式普通用户界面预览' }
  },
  created () { this.loadPlatformMode() },
  methods: {
    async loadPlatformMode () {
      if (this.loading) return
      this.loading = true
      this.loadError = false
      this.mode = 'COMPETITION'
      this.homePath = previewHome('COMPETITION')
      this.loading = false
    }
  }
}
</script>
<style scoped>
.preview-page{min-height:calc(100vh - 116px)}
.preview-frame{height:calc(100vh - 220px);min-height:560px;border:1px solid #d9e2e8;background:#fff}
.preview-frame iframe{width:100%;height:100%;border:0}
.preview-error{display:flex;min-height:320px;align-items:center;justify-content:center;flex-direction:column;gap:10px;color:#73828e;text-align:center}
.preview-error i{color:#d89b32;font-size:34px}
.preview-error strong{color:#294152;font-size:17px}
.preview-error span{margin-bottom:4px}
@media(max-width:650px){.preview-page{min-height:calc(100vh - 92px)}.preview-frame{height:calc(100vh - 180px);min-height:500px}}
</style>
