<template>
  <div :class="['console-layout', { 'admin-layout': isAdminRoute }]">
    <CompetitionShell
      v-if="!showAdminNavigation"
      :brand-name="platformName"
      :paper-label="activePaperLabel"
      :remaining-text="clearTimeText"
      :user-name="userName"
      :active-key="sideNav"
      :tabs="participantItems"
      @select="handleShellNavigation"
      @logout="logout"
    >
      <router-view />
    </CompetitionShell>
    <PlatformShell
      v-else
      :items="shellItems"
      :active-route="sideNav"
      :brand-caption="showAdminNavigation ? '竞赛管理' : '参赛工作台'"
      :workspace-title="showAdminNavigation ? '竞赛管理' : platformName"
      :workspace-caption="showAdminNavigation ? '赛程、赛卷与比赛账号管理' : '比赛答题与实操环境'"
      :platform-name="platformName"
      :user-name="userName"
      @navigate="handleShellNavigation"
      @logout="logout"
    >
      <template #status>
        <span class="competition-clock"><i class="el-icon-time" aria-hidden="true" />比赛计时：{{ clearTimeText }}</span>
      </template>
      <template v-if="previewOnly" #account-actions><span /></template>
      <router-view />
    </PlatformShell>
    <aside v-if="preStartLocked" class="prestart-dialog" role="status" aria-live="polite" aria-label="距离比赛开始时间">
      <div class="prestart-heading"><span class="prestart-icon"><i class="el-icon-time" /></span><small>距离比赛开始还有</small></div>
      <strong>{{ preStartTimeText }}</strong>
      <p>赛前可查看首页和赛规赛程，比赛功能将在正式开始后自动开放。</p>
      <span class="prestart-state"><i />平台已开放登录</span>
    </aside>
  </div>
</template>

<script>
import { getClearTime } from '@/api/Match'
import { mapState } from 'vuex'
import PlatformShell from '@/components/PlatformShell.vue'
import CompetitionShell from '@/components/CompetitionShell.vue'
import { clearSession, getRole, getUserName, setCompetitionAccessPhase } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
  components: { PlatformShell, CompetitionShell },
  data () {
    return {
      clearTimeText: '比赛时间未设置',
      countdownSnapshot: null,
      serverOffset: 0,
      clockNow: Date.now(),
      tickTimer: null,
      syncTimer: null
    }
  },
  computed: {
    ...mapState('Match', ['isAdmin', 'platformName']),
    isAdminRoute () {
      return this.$route.path === '/Admin'
    },
    showAdminNavigation () {
      return this.isAdmin && !this.previewOnly
    },
    previewOnly () {
      return isPreviewRoute(this.$route, getRole())
    },
    userName () {
      return this.previewOnly ? '参赛端预览' : (getUserName() || (this.isAdmin ? '管理员' : '参赛用户'))
    },
    participantItems () {
      return [
        { key: 'home', activeKey: 'home', label: '首页', icon: 'el-icon-house', destination: 1 },
        { key: 'schedule', activeKey: 'schedule', label: '赛规赛程', icon: 'el-icon-date', destination: 2 },
        { key: 'paper', activeKey: 'paper', label: '当前赛卷', icon: 'el-icon-document', destination: 3 },
        { key: 'verification', activeKey: 'verification', label: '成果验证', icon: 'el-icon-circle-check', destination: 4 }
      ]
    },
    activePaperLabel () {
      const paper = this.$store.state.Match.activePaper
      return paper ? `${paper} 卷 · 进行中` : '当前赛卷未选择'
    },
    shellItems () {
      if (!this.showAdminNavigation) return this.participantItems
      return this.participantItems.concat([
        { key: 'admin-timer', activeKey: 'admin-timer', label: '比赛控制', icon: 'el-icon-odometer', adminTab: 'timer' },
        { key: 'admin-rules', activeKey: 'admin-rules', label: '赛规赛程编辑', icon: 'el-icon-edit-outline', adminTab: 'rules' },
        { key: 'admin-subjects', activeKey: 'admin-subjects', label: '试卷题目', icon: 'el-icon-reading', adminTab: 'subjects' },
        { key: 'admin-grading', activeKey: 'admin-grading', label: '试卷判分', icon: 'el-icon-finished', adminTab: 'grading' },
        { key: 'admin-users', activeKey: 'admin-users', label: '比赛账号', icon: 'el-icon-user', adminTab: 'users' },
        { key: 'admin-training', activeKey: 'admin-training', label: '训练环境', icon: 'el-icon-monitor', adminTab: 'training' },
        { key: 'admin-settings', activeKey: 'admin-settings', label: '平台设置', icon: 'el-icon-setting', adminTab: 'settings' }
      ])
    },
    preStartLocked () {
      return !this.isAdmin && this.countdownSnapshot && ['BEFORE_LOGIN', 'PRE_START', 'UNSCHEDULED'].includes(this.countdownSnapshot.accessPhase)
    },
    preStartTimeText () {
      if (!this.countdownSnapshot || !this.countdownSnapshot.scheduledStartTime) return '00:00:00'
      const remaining = Math.max(0, Math.ceil((Number(this.countdownSnapshot.scheduledStartTime) - (this.clockNow + this.serverOffset)) / 1000))
      return this.formatRemaining(remaining)
    },
    sideNav () {
      const path = this.$route.path
      if (path === '/Home') return 'schedule'
      if (['/Question', '/QuestionA', '/QuestionB', '/Match', '/Matchs'].includes(path)) return 'paper'
      if (path === '/Detect') return 'verification'
      if (path === '/competition-practical') return 'practical'
      if (path === '/Admin') {
        const tab = String(this.$route.query.tab || 'timer')
        if (['timer', 'rules', 'subjects', 'grading', 'users', 'training', 'settings'].includes(tab)) return `admin-${tab}`
        return 'admin-timer'
      }
      return 'home'
    }
  },
  async mounted () {
    if (!this.previewOnly) startUserActivity()
    this.syncTimer = setInterval(this.syncState, 10000)
    this.tickTimer = setInterval(this.updateCountdownText, 1000)
    await this.syncState()
    this.updateCountdownText()
  },
  beforeDestroy () {
    stopUserActivity()
    clearInterval(this.syncTimer)
    clearInterval(this.tickTimer)
  },
  methods: {
    handleShellNavigation (item) {
      if (item.adminTab) this.openAdmin(item.adminTab)
      else this.doRouter(item.destination)
    },
    async syncCountdown () {
      try {
        const result = await getClearTime()
        if (result.code === 200) {
          this.countdownSnapshot = result.data
          this.serverOffset = Number(result.data.serverTime || Date.now()) - Date.now()
          setCompetitionAccessPhase(result.data.accessPhase)
          if (!this.isAdmin && result.data.accessPhase === 'FINISHED') {
            clearSession()
            this.$message.info('比赛已结束，请重新登录')
            this.$router.replace({ path: '/login' }).catch(() => {})
            return
          }
          this.updateCountdownText()
        }
      } catch (error) {
        this.clearTimeText = '计时服务不可用'
      }
    },
    async syncCompetition () {
      try {
        await this.$store.dispatch('Match/syncActivePaper')
      } catch (error) {
        // Keep the last known paper when the status endpoint is temporarily unavailable.
      }
    },
    async syncState () {
      await Promise.all([this.syncCountdown(), this.syncCompetition()])
    },
    updateCountdownText () {
      this.clockNow = Date.now()
      if (!this.countdownSnapshot) return
      const snapshot = this.countdownSnapshot
      let remaining = Number(snapshot.remainingSeconds || 0)
      if (snapshot.status === 'RUNNING' && snapshot.endTime) {
        remaining = Math.max(0, Math.ceil((Number(snapshot.endTime) - (this.clockNow + this.serverOffset)) / 1000))
      }
      if (!snapshot.status || snapshot.status === 'NOT_STARTED') {
        this.clearTimeText = '比赛时间未设置'
        return
      }
      if (snapshot.accessPhase === 'PRE_START' || snapshot.accessPhase === 'BEFORE_LOGIN') {
        const preStartRemaining = Math.max(0, Math.ceil((Number(snapshot.scheduledStartTime) - (this.clockNow + this.serverOffset)) / 1000))
        if (snapshot.accessPhase === 'PRE_START' && preStartRemaining === 0) {
          this.$set(this.countdownSnapshot, 'accessPhase', 'ACTIVE')
          this.$set(this.countdownSnapshot, 'status', 'RUNNING')
          setCompetitionAccessPhase('ACTIVE')
        }
        this.clearTimeText = `距开始 ${this.formatRemaining(preStartRemaining)}`
        return
      }
      this.clearTimeText = this.formatRemaining(remaining)
    },
    formatRemaining (seconds) {
      let value = Math.max(0, Number(seconds || 0))
      const hours = Math.floor(value / 3600)
      value %= 3600
      const minutes = Math.floor(value / 60)
      const secs = value % 60
      return [hours, minutes, secs].map(item => String(item).padStart(2, '0')).join(':')
    },
    async openActivePaper () {
      if (!this.ensureCompetitionAccess()) return
      try {
        const result = await this.$store.dispatch('Match/syncActivePaper')
        if (!result.synced) return
        if (result.activePaper) this.$router.push(this.participantLocation('/Question')).catch(() => {})
        else this.$message.warning('管理员尚未选择赛卷')
      } catch (error) {
        // The request interceptor already explains why the current paper could not be synced.
      }
    },
    async doRouter (type) {
      if (type === 1) {
        this.$router.push(this.participantLocation('/Publicity')).catch(() => {})
      } else if (type === 2) {
        this.$router.push(this.participantLocation('/Home')).catch(() => {})
      } else if (type === 3) {
        await this.openActivePaper()
      } else if (type === 4) {
        if (!this.ensureCompetitionAccess()) return
        try {
          await this.$store.dispatch('Match/syncActivePaper')
        } catch (error) {
          // Keep the last known paper when the status endpoint is temporarily unavailable.
        }
        this.$router.push(this.participantLocation('/Detect')).catch(() => {})
      } else if (type === 5) {
        if (!this.ensureCompetitionAccess()) return
        this.$router.push(this.participantLocation('/competition-practical')).catch(() => {})
      }
    },
    participantLocation (path) {
      return { path, query: this.previewOnly ? { preview: '1' } : undefined }
    },
    ensureCompetitionAccess () {
      if (!this.preStartLocked) return true
      this.$message.warning('比赛尚未正式开始，当前功能暂不可用')
      return false
    },
    openAdmin (tab) {
      if (!this.isAdmin) return
      const query = tab ? { tab } : {}
      this.$router.push({ path: '/Admin', query }).catch(() => {})
    },
    async logout () {
      if (this.previewOnly) return
      try { await logoutUser() } finally { clearSession(); this.$router.replace('/login') }
    }
  }
}
</script>

<style>
@import url(../assets/style/layout.css);

.prestart-dialog { position: fixed; z-index: 1200; top: 82px; right: 28px; width: 260px; overflow: hidden; color: #34495e; background: #fff; border: 1px solid #d4dfe6; border-top: 4px solid var(--platform-theme-color, #162d45); border-radius: 7px; box-shadow: 0 16px 36px rgba(22, 42, 60, .18); box-sizing: border-box; pointer-events: none; }
.prestart-heading { display: flex; align-items: center; gap: 10px; padding: 15px 17px 8px; }
.prestart-icon { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 36px; width: 36px; height: 36px; color: #715817; font-size: 18px; background: #fff8df; border: 1px solid #ead37e; border-radius: 6px; }
.prestart-dialog small { color: #6f7d89; font-size: 11px; }
.prestart-dialog strong { display: block; padding: 0 17px 14px; color: #173d5b; font-family: Menlo, Monaco, Consolas, monospace; font-size: 30px; letter-spacing: 0; }
.prestart-dialog p { margin: 0; padding: 12px 17px; color: #637485; font-size: 11px; line-height: 1.7; background: #f7f9fa; border-top: 1px solid #e4eaee; }
.prestart-state { display: flex; align-items: center; gap: 7px; padding: 12px 17px 15px; color: #2f815d; font-size: 10px; }
.prestart-state i { width: 7px; height: 7px; background: #2f9d68; border-radius: 50%; box-shadow: 0 0 0 3px #dff0e7; }

@media (max-width: 1935px) {
  .prestart-dialog { top: auto; right: 18px; bottom: 18px; width: 286px; }
}

@media (max-width: 900px) {
  .prestart-dialog { position: static; width: auto; margin: 14px 14px 18px; }
}
</style>
