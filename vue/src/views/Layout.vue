<template>
  <div :class="['console-layout', { 'admin-layout': isAdminRoute }]">
    <el-container>
      <el-header class="header">
        <div class="lay-brand" :aria-label="platformName">
          <span class="lay-brand-mark" />
          <div>
            <strong :title="platformName">{{ platformName }}</strong>
            <small>Competition Console</small>
          </div>
        </div>
        <div class="lay-time">比赛计时：{{ clearTimeText }}</div>
      </el-header>
      <el-container class="container2">
        <el-aside class="console-sidebar" width="220px">
          <el-menu :default-active="sideNav" class="console-menu">
            <el-menu-item index="home" title="首页" @click="doRouter(1)">
              <i class="el-icon-house" /><span class="menu-label">首页</span>
            </el-menu-item>
            <el-menu-item index="schedule" title="赛规赛程" @click="doRouter(2)">
              <i class="el-icon-date" /><span class="menu-label">赛规赛程</span>
            </el-menu-item>
            <el-menu-item index="paper" title="当前赛卷" @click="doRouter(3)">
              <i class="el-icon-document" /><span class="menu-label">当前赛卷</span>
            </el-menu-item>
            <el-menu-item index="verification" title="成果验证" @click="doRouter(4)">
              <i class="el-icon-circle-check" /><span class="menu-label">成果验证</span>
            </el-menu-item>
            <el-menu-item v-if="!isAdmin" index="practical" title="比赛实操" @click="doRouter(5)">
              <i class="el-icon-monitor" /><span class="menu-label">比赛实操</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-timer" class="menu-admin-start" title="比赛控制" @click="openAdmin('timer')">
              <i class="el-icon-odometer" /><span class="menu-label">比赛控制</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-rules" title="赛规赛程编辑" @click="openAdmin('rules')">
              <i class="el-icon-edit-outline" /><span class="menu-label">赛规赛程编辑</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-subjects" title="试卷题目" @click="openAdmin('subjects')">
              <i class="el-icon-reading" /><span class="menu-label">试卷题目</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-grading" title="试卷判分" @click="openAdmin('grading')">
              <i class="el-icon-finished" /><span class="menu-label">试卷判分</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-users" title="比赛账号" @click="openAdmin('users')">
              <i class="el-icon-user" /><span class="menu-label">比赛账号</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-training" title="训练环境" @click="openAdmin('training')">
              <i class="el-icon-monitor" /><span class="menu-label">训练环境</span>
            </el-menu-item>
            <el-menu-item v-if="showAdminNavigation" index="admin-settings" title="平台设置" @click="openAdmin('settings')">
              <i class="el-icon-setting" /><span class="menu-label">平台设置</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        <el-main><router-view /></el-main>
      </el-container>
    </el-container>
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
import { clearSession, setCompetitionAccessPhase } from '@/utils/auth'
import { startUserActivity, stopUserActivity } from '@/services/userActivity'

export default {
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
      return this.isAdmin && this.$route.query.preview !== '1'
    },
    preStartLocked () {
      return !this.isAdmin && this.countdownSnapshot && this.countdownSnapshot.accessPhase === 'PRE_START'
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
    startUserActivity()
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
        if (result.activePaper) this.$router.push({ name: 'Question' }).catch(() => {})
        else this.$message.warning('管理员尚未选择赛卷')
      } catch (error) {
        // The request interceptor already explains why the current paper could not be synced.
      }
    },
    async doRouter (type) {
      if (type === 1) {
        this.$router.push({ path: '/Publicity' }).catch(() => {})
      } else if (type === 2) {
        this.$router.push({ path: '/Home' }).catch(() => {})
      } else if (type === 3) {
        await this.openActivePaper()
      } else if (type === 4) {
        if (!this.ensureCompetitionAccess()) return
        try {
          await this.$store.dispatch('Match/syncActivePaper')
        } catch (error) {
          // Keep the last known paper when the status endpoint is temporarily unavailable.
        }
        this.$router.push({ path: '/Detect' }).catch(() => {})
      } else if (type === 5) {
        if (!this.ensureCompetitionAccess()) return
        this.$router.push({ path: '/competition-practical' }).catch(() => {})
      }
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
    }
  }
}
</script>

<style scoped>
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
