<template>
  <section class="competition-practical module-page">
    <header class="practical-heading module-heading">
      <div>
        <h1>比赛实操</h1>
        <p>当前账号的图像标注与代码编辑环境</p>
      </div>
      <div class="practical-actions">
        <el-button icon="el-icon-back" @click="backToAnswers">返回答题界面</el-button>
        <el-button icon="el-icon-full-screen" @click="toggleFullscreen">全屏</el-button>
        <el-button icon="el-icon-question" @click="openHelp">比赛帮助</el-button>
        <el-button icon="el-icon-document" @click="viewPaper">查看赛题</el-button>
        <el-button v-if="!previewOnly" icon="el-icon-download" @click="downloadRootCa">下载平台根证书</el-button>
      </div>
    </header>
    <el-drawer title="比赛帮助" :visible.sync="helpVisible" size="420px">
      <div class="practical-drawer-copy">{{ helpContent || "暂无比赛帮助信息" }}</div>
    </el-drawer>
    <el-drawer title="当前赛题" :visible.sync="paperVisible" size="520px">
      <div class="practical-drawer-copy">
        <h3>{{ paperTitle || "当前赛题" }}</h3>
        <p v-if="paperAnswering">{{ paperAnswering }}</p>
        <p v-if="paperScreenshot">截图要求：{{ paperScreenshot }}</p>
        <p v-if="!paperAnswering && !paperScreenshot">当前题目未提供补充说明。</p>
      </div>
    </el-drawer>
    <ParticipantPreviewNotice v-if="previewOnly" class="practical-preview-notice" />

    <div v-if="loading && !environment" class="practical-state">
      <i class="el-icon-loading" /><strong>正在查询比赛环境</strong>
    </div>
    <div v-else-if="previewOnly" class="practical-state">
      <i class="el-icon-monitor" /><strong>比赛实操环境预览</strong>
      <span>预览模式不连接实际比赛环境，参赛用户绑定可用槽位后可进入图像标注和代码编辑。</span>
    </div>
    <div v-else-if="!environment || environment.readiness === 'UNBOUND'" class="practical-state">
      <i class="el-icon-info" /><strong>未分配比赛环境</strong>
      <span>您仍可继续浏览试卷并作答，但暂时无法进入实操环境。</span>
    </div>
    <div v-else-if="['STARTING','STOPPED','CREATING','STOPPING','WAITING_DEPENDENCY'].includes(environment.readiness)" class="practical-state">
      <i class="el-icon-loading" /><strong>比赛环境正在启动</strong>
      <span>容器就绪后入口将自动开放。</span>
    </div>
    <div v-else-if="['DEGRADED','ERROR'].includes(environment.readiness)" class="practical-state is-error">
      <i class="el-icon-warning-outline" /><strong>比赛环境暂不可用</strong>
      <span>环境状态异常，请联系管理员</span>
    </div>
    <div v-else-if="environment.readiness === 'RUNNING' && embeddedUrl" class="practical-ready">
      <el-alert v-if="$route.query.tool === 'code'" type="info" :closable="false" title="代码环境需要信任当前平台根证书；若页面空白或提示证书错误，请联系管理员安装当前平台根证书。" />
      <EnvironmentToolFrame :src="embeddedUrl" class="practical-frame" :title="embeddedTitle" />
    </div>
    <div v-else class="practical-state is-error">
      <i class="el-icon-warning-outline" /><strong>无法确认比赛环境状态</strong>
    </div>
  </section>
</template>

<script>
import EnvironmentToolFrame from '@/components/training/EnvironmentToolFrame.vue';
import request from '@/utils/request'
import { adminParticipantPreviewCompetitionEnvironmentApi, competitionApi } from '@/api/Match'
import ParticipantPreviewNotice from '@/components/ParticipantPreviewNotice.vue'
import { getRole } from '@/utils/auth'
import { downloadCodeServerRootCa } from '@/api/TrainingModels'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
  components: { EnvironmentToolFrame, ParticipantPreviewNotice },
  data: () => ({ loading: false, environment: null, openedRequestedTool: false, helpVisible: false, helpContent: '', paperVisible: false, pollTimer: null }),
  computed: {
    previewOnly () { return isPreviewRoute(this.$route, getRole()) }
  },
  mounted () { this.load(); this.pollTimer=setInterval(() => this.load(),2000) },
  beforeDestroy () { clearInterval(this.pollTimer) },
  methods: {
    async downloadRootCa () {
      const response = await downloadCodeServerRootCa()
      const url = URL.createObjectURL(new Blob([response.data], { type: 'application/x-pem-file' }))
      const link = document.createElement('a')
      link.href = url
      link.download = 'rootCA.pem'
      link.click()
      URL.revokeObjectURL(url)
    },
    backToAnswers () { this.$router.push({ path: '/Question', query: { fromPractical: '1' } }) },
    viewPaper () { this.paperVisible = true },
    async openHelp () {
      this.helpVisible = true
      if (this.helpContent) return
      try {
        const result = await competitionApi()
        this.helpContent = result.code === 200 && result.data ? String(result.data.competitionHelpContent || '') : ''
      } catch (error) {
        this.helpContent = ''
      }
    },
    async toggleFullscreen () {
      if (document.fullscreenElement) return document.exitFullscreen()
      if (this.$el && this.$el.requestFullscreen) return this.$el.requestFullscreen()
      return this.$message.warning('当前浏览器不支持全屏')
    },
    async load () {
      if (this.loading) return
      this.loading = true
      try {
        if (this.previewOnly) {
          const result = await adminParticipantPreviewCompetitionEnvironmentApi()
          if (result.code === 200) this.environment = result.data || { readiness: 'UNBOUND' }
        } else {
          const result = await request({ url: 'user/competition-environment', method: 'get' })
          this.environment = result.data
          this.openRequestedTool()
        }
      } catch (error) {
        if (!this.previewOnly) {
          try {
            const result = await request({ url: 'user/competition-environment', method: 'get' })
            if (result.code === 200 && result.data && result.data.readiness === 'RUNNING') {
              this.environment = result.data
              return
            }
          } catch (ignored) {}
        }
        this.environment = this.previewOnly ? { readiness: 'DEGRADED' } : { readiness: 'DEGRADED', readinessCode: 'COMPETITION_ENVIRONMENT_UNAVAILABLE' }
      } finally {
        this.loading = false
      }
    },
    safeUrl (value) {
      if (!value) return ''
      try {
        const parsed = new URL(value, window.location.origin)
        return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : ''
      } catch (error) {
        return ''
      }
    },
    openRequestedTool () {
      this.openedRequestedTool = true
    },
    open (value) {
      if (this.previewOnly) return
      const target = this.safeUrl(value)
      if (this.environment && this.environment.readiness === 'RUNNING' && target) {
        window.open(target, '_blank', 'noopener,noreferrer')
      }
    }
  }
  ,computed: {
    previewOnly () { return isPreviewRoute(this.$route, getRole()) },
    embeddedUrl () {
      if (!this.environment || this.environment.readiness !== 'RUNNING') return ''
      return this.safeUrl({ cvat: this.environment.annotationUrl, code: this.environment.editorUrl, t100: this.environment.t100Url }[this.$route.query.tool])
    },
    embeddedTitle () { return this.$route.query.tool === 'cvat' ? '图像标注环境' : '代码编辑环境' }
    ,paperTitle () { return this.$route.query.title || '' }
    ,paperAnswering () { return this.$route.query.answering || '' }
    ,paperScreenshot () { return this.$route.query.screenshot || '' }
  }
}
</script>

<style scoped>
.competition-practical { min-height: calc(100vh - 116px); padding: 28px; color: var(--ui-text); background: var(--ui-page); box-sizing: border-box; }
.competition-practical:fullscreen { display: flex; width: 100vw; height: 100vh; min-height: 0; padding: 20px 28px; flex-direction: column; overflow: hidden; }
.competition-practical:fullscreen .practical-heading { width: 100%; max-width: none; flex: 0 0 auto; }
.competition-practical:fullscreen .practical-ready { width: 100%; max-width: none; min-height: 0; flex: 1 1 auto; }
.competition-practical:fullscreen .practical-frame { height: 100%; min-height: 0; }
.practical-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; max-width: 980px; margin: 0 auto 20px; }
.practical-preview-notice { max-width: 980px; margin-right: auto; margin-left: auto; box-sizing: border-box; }
.practical-heading h1 { margin: 0; font-size: 24px; letter-spacing: 0; }
.practical-heading p { margin: 7px 0 0; color: var(--ui-muted); font-size: 13px; }
.practical-state, .practical-ready { max-width: 980px; min-height: 190px; margin: 0 auto; padding: 34px 24px; border: 1px solid var(--ui-border); border-radius: var(--ui-card-radius); background: var(--ui-surface); box-shadow: var(--ui-shadow); box-sizing: border-box; }
.practical-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; text-align: center; }
.practical-state i { color: var(--ui-primary); font-size: 28px; }
.practical-state strong { font-size: 18px; }
.practical-state span { max-width: 580px; color: var(--ui-muted); font-size: 13px; line-height: 1.7; }
.practical-state.is-error i { color: #c45656; }
.practical-ready { max-width: 1200px; min-height: calc(100vh - 190px); padding: 0; overflow: hidden; }
.practical-frame { display: block; width: 100%; height: calc(100vh - 190px); min-height: 620px; border: 0; background: #fff; }
.ready-copy { display: flex; align-items: center; gap: 13px; }
.ready-copy strong, .ready-copy small { display: block; }
.ready-copy strong { font-size: 18px; }
.ready-copy small { margin-top: 6px; color: var(--ui-muted); }
.ready-dot { width: 10px; height: 10px; background: #2f9d68; border-radius: 50%; box-shadow: 0 0 0 5px #dcf1e7; }
.ready-actions { display: flex; gap: 10px; }
.practical-drawer-copy { padding: 4px 24px 24px; color: var(--ui-text); line-height: 1.8; white-space: pre-wrap; word-break: break-word; }
.practical-drawer-copy h3 { margin: 0 0 12px; font-size: 18px; }
.practical-drawer-copy p { margin: 8px 0; }
@media (max-width: 720px) { .competition-practical { padding: 18px 14px; } .practical-ready { align-items: stretch; flex-direction: column; } .ready-actions { flex-direction: column; } .ready-actions .el-button { width: 100%; margin-left: 0; } }
</style>
