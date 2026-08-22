<template>
  <section class="competition-practical module-page">
    <header class="practical-heading module-heading">
      <div>
        <h1>比赛实操</h1>
        <p>当前账号的图像标注与代码编辑环境</p>
      </div>
      <el-tooltip content="刷新环境状态" placement="bottom">
        <el-button icon="el-icon-refresh" circle :loading="loading" aria-label="刷新环境状态" @click="load" />
      </el-tooltip>
    </header>
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
    <div v-else-if="environment.readiness === 'STARTING'" class="practical-state">
      <i class="el-icon-loading" /><strong>比赛环境正在启动</strong>
      <span>容器就绪后入口将自动开放。</span>
    </div>
    <div v-else-if="environment.readiness === 'DEGRADED'" class="practical-state is-error">
      <i class="el-icon-warning-outline" /><strong>比赛环境暂不可用</strong>
      <span>环境状态异常，请联系管理员</span>
    </div>
    <div v-else-if="environment.readiness === 'RUNNING'" class="practical-ready">
      <div class="ready-copy"><span class="ready-dot" /><div><strong>比赛环境已就绪</strong><small v-if="!previewOnly">槽位 {{ environment.slotNumber || '-' }}</small></div></div>
      <div class="ready-actions">
        <el-button type="primary" icon="el-icon-picture-outline" :disabled="previewOnly || !safeUrl(environment.annotationUrl)" @click="open(environment.annotationUrl)">打开图像标注</el-button>
        <el-button icon="el-icon-edit-outline" :disabled="previewOnly || !safeUrl(environment.editorUrl)" @click="open(environment.editorUrl)">打开代码编辑器</el-button>
      </div>
    </div>
    <div v-else class="practical-state is-error">
      <i class="el-icon-warning-outline" /><strong>无法确认比赛环境状态</strong>
    </div>
  </section>
</template>

<script>
import request from '@/utils/request'
import { adminParticipantPreviewCompetitionEnvironmentApi } from '@/api/Match'
import ParticipantPreviewNotice from '@/components/ParticipantPreviewNotice.vue'
import { getRole } from '@/utils/auth'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
  components: { ParticipantPreviewNotice },
  data: () => ({ loading: false, environment: null }),
  computed: {
    previewOnly () { return isPreviewRoute(this.$route, getRole()) }
  },
  mounted () { this.load() },
  methods: {
    async load () {
      if (this.loading) return
      this.loading = true
      try {
        const result = this.previewOnly
          ? await adminParticipantPreviewCompetitionEnvironmentApi()
          : await request({ url: 'user/competition-environment', method: 'get' })
        if (result.code === 200) this.environment = result.data || { readiness: 'UNBOUND' }
      } catch (error) {
        this.environment = this.previewOnly
          ? { readiness: 'DEGRADED' }
          : { readiness: 'DEGRADED', readinessCode: 'COMPETITION_ENVIRONMENT_UNAVAILABLE' }
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
    open (value) {
      if (this.previewOnly) return
      const target = this.safeUrl(value)
      if (this.environment && this.environment.readiness === 'RUNNING' && target) {
        window.open(target, '_blank', 'noopener,noreferrer')
      }
    }
  }
}
</script>

<style scoped>
.competition-practical { min-height: calc(100vh - 116px); padding: 28px; color: #24384a; background: #f4f7f9; box-sizing: border-box; }
.practical-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; max-width: 980px; margin: 0 auto 20px; }
.practical-preview-notice { max-width: 980px; margin-right: auto; margin-left: auto; box-sizing: border-box; }
.practical-heading h1 { margin: 0; font-size: 24px; letter-spacing: 0; }
.practical-heading p { margin: 7px 0 0; color: #72808d; font-size: 13px; }
.practical-state, .practical-ready { max-width: 980px; min-height: 190px; margin: 0 auto; padding: 34px 8px; border-top: 1px solid #dce4ea; border-bottom: 1px solid #dce4ea; box-sizing: border-box; }
.practical-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; text-align: center; }
.practical-state i { color: #56768f; font-size: 28px; }
.practical-state strong { font-size: 18px; }
.practical-state span { max-width: 580px; color: #71808d; font-size: 13px; line-height: 1.7; }
.practical-state.is-error i { color: #c45656; }
.practical-ready { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.ready-copy { display: flex; align-items: center; gap: 13px; }
.ready-copy strong, .ready-copy small { display: block; }
.ready-copy strong { font-size: 18px; }
.ready-copy small { margin-top: 6px; color: #71808d; }
.ready-dot { width: 10px; height: 10px; background: #2f9d68; border-radius: 50%; box-shadow: 0 0 0 5px #dcf1e7; }
.ready-actions { display: flex; gap: 10px; }
@media (max-width: 720px) { .competition-practical { padding: 18px 14px; } .practical-ready { align-items: stretch; flex-direction: column; } .ready-actions { flex-direction: column; } .ready-actions .el-button { width: 100%; margin-left: 0; } }
</style>
