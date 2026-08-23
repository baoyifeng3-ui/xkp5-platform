<template>
  <section class="module-page module-composed-page dashboard-home">
    <header class="module-heading"><div><h1>主页</h1><p>普通管理员的业务总览与快捷入口。</p></div><span v-if="stale" class="stale-state">数据暂未更新</span></header>
    <div class="overview-grid"><button v-for="action in actions" :key="action.key" type="button" class="overview-action" :disabled="action.key === 'mode' && (modeLoading || !modeInitialized || modeSwitching)" @click="handleAction(action)"><i :class="action.icon" /><span>{{ actionLabel(action) }}</span></button></div>
    <div v-if="modeLoadError" class="mode-error" role="alert"><span>平台模式加载失败，模式切换已禁用。</span><el-button size="small" icon="el-icon-refresh" :loading="modeLoading" @click="loadPlatformMode">重新加载</el-button></div>
    <LicenseStatusPanel />
    <div v-if="firstLoadFailed" class="overview-warning">概览服务暂不可用，请稍后重试。</div>
    <div class="metric-strip"><div v-for="metric in metrics" :key="metric.label"><small>{{ metric.label }}</small><strong>{{ metric.value }}</strong><span>{{ metric.note }}</span></div></div>
    <nav v-if="snapshot" class="alert-links" aria-label="告警和待处理事项">
      <router-link v-for="item in alertLinks" :key="item.label" :to="item.route"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><i class="el-icon-arrow-right" /></router-link>
    </nav>
    <section class="resource-section">
      <header><h2>处理服务器资源</h2><span>在线服务器最近一次有效采样的平均值</span></header>
      <div class="resource-grid"><div v-for="resource in resources" :key="resource.key" class="resource-row"><div class="resource-copy"><strong>{{ resource.label }}</strong><span>{{ resourceText(resource) }}</span></div><el-progress :percentage="resourcePercent(resource)" :show-text="false" :stroke-width="9" :color="resource.color" /><small>{{ resourceSampleText(resource) }}</small></div></div>
    </section>
  </section>
</template>

<script>
import LicenseStatusPanel from '@/components/LicenseStatusPanel.vue'
import { getDashboardOverview } from '@/api/DashboardOverview'
import { getPlatformMode, changePlatformMode } from '@/api/PlatformMode'
const { applyOverviewSuccess, applyOverviewFailure, resourceText } = require('@/services/dashboardOverviewState')

export default {
  components: { LicenseStatusPanel },
  data: () => ({
    mode: {},
    modeLoading: false,
    modeInitialized: false,
    modeLoadError: false,
    modeSwitching: false,
    snapshot: null,
    loading: false,
    stale: false,
    firstLoadFailed: false,
    refreshTimer: null
  }),
  computed: {
    actions () {
      return [{ key: 'training', label: '一键上课', icon: 'el-icon-video-play', route: '/management/training' }, { key: 'mode', icon: 'el-icon-refresh' }, { key: 'courses', label: '进入课程平台', icon: 'el-icon-reading', route: '/management/course-platform' }, { key: 'environment', label: '进入实训环境', icon: 'el-icon-monitor', route: '/management/training' }]
    },
    metrics () {
      const data = this.snapshot || {}
      const agents = data.agents || data.processingAgents || {}
      const environments = data.environments || {}
      const alerts = data.alerts || {}
      return [{ label: '服务器在线数', value: this.value(agents.online), note: agents.enabled == null ? '总数不可用' : `共 ${agents.enabled} 台` }, { label: '运行环境数', value: this.value(environments.running), note: environments.transitional == null ? '状态不可用' : `${environments.transitional} 个处理中` }, { label: '告警和待处理事项', value: this.value(alerts.total), note: alerts.pendingWorkTotal == null ? '状态不可用' : `${alerts.pendingWorkTotal} 项待处理` }, { label: '用户在线数', value: this.value(data.onlineUsers), note: '最近五分钟活跃' }]
    },
    resources () {
      const data = (this.snapshot && this.snapshot.resources) || {}
      return [{ key: 'cpu', label: 'CPU', color: 'var(--ui-success)', summary: data.cpu }, { key: 'gpu', label: 'GPU', color: 'var(--ui-info)', summary: data.gpu }, { key: 'gpuMemory', label: 'GPU 显存', color: 'var(--ui-primary)', summary: data.gpuMemory }, { key: 'memory', label: '内存', color: 'var(--ui-warning)', summary: data.memory }, { key: 'disk', label: '磁盘', color: 'var(--ui-danger)', summary: data.disk }]
    },
    alertLinks () {
      const alerts = (this.snapshot && this.snapshot.alerts) || {}
      return [{ label: '离线服务器', value: alerts.offlineAgents || 0, route: '/management/devices' }, { label: '异常实训环境', value: (alerts.degradedEnvironments || 0) + (alerts.failedEnvironments || 0), route: '/management/training' }, { label: '待处理环境操作', value: (alerts.pendingOperations || 0) + (alerts.failedOperations || 0), route: '/management/training' }, { label: '待处理服务器命令', value: (alerts.pendingCommands || 0) + (alerts.failedCommands || 0), route: '/management/devices' }, { label: '授权提醒', value: (alerts.licenseUnusable || 0) + (alerts.licenseExpiring || 0), route: '/management/license' }]
    }
  },
  mounted () { this.loadPlatformMode(); this.loadOverview(); this.refreshTimer = setInterval(this.loadOverview, 15000) },
  beforeDestroy () { clearInterval(this.refreshTimer) },
  methods: {
    async loadPlatformMode () {
      if (this.modeLoading) return
      this.modeLoading = true
      this.modeInitialized = false
      this.modeLoadError = false
      try {
        const response = await getPlatformMode()
        const value = response && response.data
        if (!value || !['TRAINING', 'COMPETITION'].includes(value.mode)) throw new Error('Platform mode response is unavailable')
        this.mode = Object.assign({}, value)
        this.modeInitialized = true
      } catch (error) {
        this.modeLoadError = true
        this.$message.error('平台模式加载失败，请稍后重试。')
      } finally {
        this.modeLoading = false
      }
    },
    async togglePlatformMode () {
      if (!this.modeInitialized || this.modeSwitching) return
      const target = this.mode.mode === 'COMPETITION' ? 'TRAINING' : 'COMPETITION'
      const phrase = target === 'COMPETITION' ? 'ENTER COMPETITION' : 'EXIT COMPETITION'
      try {
        await this.$confirm('切换后普通用户下次登录将进入对应模式，是否继续？', target === 'COMPETITION' ? '进入比赛模式' : '退出比赛模式', { confirmButtonText: '确认切换', cancelButtonText: '取消', type: 'warning' })
      } catch (error) {
        return
      }
      this.modeSwitching = true
      try {
        const response = await changePlatformMode(target)
        const value = response && response.data
        this.mode = Object.assign({}, this.mode, value, { mode: value && value.mode ? value.mode : target })
        this.$message.success(target === 'COMPETITION' ? '已进入比赛模式' : '已退出比赛模式')
      } catch (error) {
        this.$message.error('平台模式切换失败，请稍后重试。')
      } finally {
        this.modeSwitching = false
      }
    },
    handleAction (action) {
      if (action.key === 'mode') return this.togglePlatformMode()
      if (action.route) return this.$router.push(action.route)
    },
    actionLabel (action) {
      if (action.key === 'mode') {
        if (!this.modeInitialized) return this.modeLoadError ? '平台模式不可用' : '正在加载平台模式'
        return this.mode.mode === 'COMPETITION' ? '退出比赛模式' : '进入比赛模式'
      }
      return action.label
    },
    async loadOverview () {
      if (this.loading) return
      this.loading = true
      try {
        const response = await getDashboardOverview()
        applyOverviewSuccess(this, response.data)
      } catch (error) {
        applyOverviewFailure(this)
      } finally { this.loading = false }
    },
    value (value) { return value == null ? '--' : value },
    resourcePercent (resource) { const value = resource.summary && resource.summary.averagePercent; return value == null ? 0 : Math.max(0, Math.min(100, Number(value))) },
    resourceText (resource) { return resourceText(resource.summary) },
    resourceSampleText (resource) { const count = resource.summary && resource.summary.sampleCount; return count ? `${count} 台服务器` : '无有效采样' }
  }
}
</script>

<style scoped>
.module-heading{display:flex;align-items:flex-start;justify-content:space-between}.stale-state,.overview-warning{color:var(--ui-text);background:#fff8df;border:1px solid var(--ui-warning);border-radius:var(--ui-radius)}.stale-state{padding:6px 10px;font-size:12px}.overview-warning{margin-bottom:14px;padding:10px 12px}.overview-grid{display:grid;grid-template-columns:repeat(4,minmax(150px,1fr));gap:12px;margin-bottom:18px}.overview-action{height:88px;padding:16px;border:1px solid var(--ui-border);border-radius:var(--ui-radius);background:var(--ui-surface);color:var(--ui-text);text-align:left;cursor:pointer}.overview-action:hover{border-color:var(--ui-primary)}.overview-action:disabled{color:var(--ui-muted);cursor:not-allowed;opacity:.7}.overview-action i{display:block;margin-bottom:9px;color:var(--ui-primary);font-size:24px}.overview-action span{font-size:15px}.mode-error{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:-6px 0 18px;padding:10px 12px;color:var(--ui-danger);background:#fff7f7;border:1px solid #ead5d5;border-radius:var(--ui-radius)}.metric-strip{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;margin-bottom:12px;background:var(--ui-border);border:1px solid var(--ui-border)}.metric-strip>div{padding:18px;background:var(--ui-surface)}.metric-strip small,.metric-strip span{display:block;color:var(--ui-muted)}.metric-strip strong{display:block;margin:8px 0;font-size:26px}.alert-links{display:grid;grid-template-columns:repeat(5,1fr);margin-bottom:18px;border-top:1px solid var(--ui-border);border-bottom:1px solid var(--ui-border)}.alert-links a{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:8px;padding:11px 12px;color:var(--ui-muted);text-decoration:none;border-right:1px solid var(--ui-border)}.alert-links a:last-child{border-right:0}.alert-links a:hover{color:var(--ui-primary-strong);background:#f5f6fb}.alert-links strong{color:var(--ui-text)}.resource-section{padding:18px 0;border-top:1px solid var(--ui-border)}.resource-section>header{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:15px}.resource-section h2{margin:0;color:var(--ui-text);font-size:17px}.resource-section header span{color:var(--ui-muted);font-size:12px}.resource-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 24px}.resource-row{display:grid;grid-template-columns:100px minmax(120px,1fr) 86px;align-items:center;gap:12px;min-height:48px}.resource-copy strong,.resource-copy span{display:block}.resource-copy span,.resource-row small{color:var(--ui-muted);font-size:12px}.resource-row small{text-align:right}@media(max-width:1100px){.alert-links{grid-template-columns:repeat(2,1fr)}.alert-links a{border-bottom:1px solid var(--ui-border)}}@media(max-width:900px){.overview-grid,.metric-strip{grid-template-columns:repeat(2,1fr)}.resource-grid{grid-template-columns:1fr}}@media(max-width:520px){.overview-grid,.metric-strip,.alert-links{grid-template-columns:1fr}.mode-error{align-items:stretch;flex-direction:column}.resource-section>header{display:block}.resource-section header span{display:block;margin-top:5px}.resource-row{grid-template-columns:76px minmax(80px,1fr) 72px}}
</style>
