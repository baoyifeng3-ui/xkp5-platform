<template>
  <PlatformShell
    :items="items"
    :active-route="$route.path"
    brand-caption="学习工作台"
    workspace-title="学习工作台"
    workspace-caption="课程学习、资源使用与实训环境"
    :user-name="userName"
    :platform-name="platformName"
    :platform-logo-url="platformLogoUrl"
    @navigate="navigate"
    @logout="logout"
  >
    <template v-if="previewOnly" #account-actions><span /></template>
    <el-alert v-if="policy.active || policy.modeSwitching || policy.stopping" :title="policy.modeSwitching ? '平台正在切换环境，请等待准备完成' : policy.stopping ? '正在结束课堂并停止环境，完成后恢复自由实训' : policy.switching ? '教师已指定课堂环境，正在停止原环境并准备新环境' : '当前使用教师指定的课堂环境'" type="info" :closable="false" show-icon />
    <router-view :key="classRevision" />
    <el-dialog title="课堂签到" :visible.sync="attendanceDialog" :show-close="false" :close-on-press-escape="false" width="420px"><p>管理员已开启本轮签到，完成签到后可继续使用平台。</p><span slot="footer"><el-button type="primary" :loading="checkingIn" @click="checkIn">立即签到</el-button></span></el-dialog>
  </PlatformShell>
</template>

<script>
import PlatformShell from '@/components/PlatformShell.vue'
const { userItems } = require('@/navigation/roleNavigation')
import { clearSession, getClassPolicy, getRole, getUserName } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'
import { getAttendanceStatus, checkInAttendance } from '@/api/Attendance'
import { getUserClassPolicy } from '@/api/ClassPolicy'
import { setClassPolicy } from '@/utils/auth'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
  components: { PlatformShell },
  data: () => ({ items: userItems, attendanceDialog: false, checkingIn: false, policyTimer: null, policy: {}, classRevision: '', syncingPolicy: false }),
  computed: {
    previewOnly () { return isPreviewRoute(this.$route, getRole()) },
    userName () { return this.previewOnly ? '参赛端预览' : (getUserName() || '用户') },
    platformName () { return this.$store.state.Match.platformName },
    platformLogoUrl () { return this.$store.state.Match.platformLogoUrl }
  },
  mounted () { if (!this.previewOnly) { startUserActivity(); this.loadAttendance(); this.refreshClassPolicy(); this.policyTimer = setInterval(() => this.refreshClassPolicy().catch(() => {}), 2000) } },
  beforeDestroy () { if (!this.previewOnly) stopUserActivity(); clearInterval(this.policyTimer) },
  methods: {
    participantLocation (path) {
      return { path, query: this.previewOnly ? { preview: '1' } : undefined }
    },
    async navigate (item) {
      if (this.attendanceDialog) return this.$message.warning('请先完成签到')
      await this.refreshClassPolicy()
      const classPolicy = getClassPolicy()
      if (classPolicy.active === true) {
        if (item.route === '/training-environment') return this.$router.push(this.classDestination(classPolicy)).catch(() => {})
        if (!['/training-validation', '/resource-center'].includes(item.route)) return this.$message.warning('当前上课中，禁止使用该功能')
      }
      this.$router.push(this.participantLocation(item.route)).catch(() => {})
    },
    classDestination (classPolicy) {
      return classPolicy.courseId
        ? { path: '/course-platform', query: { courseId: classPolicy.courseId } }
        : { path: '/training-environment' }
    },
    classRouteAllowed (classPolicy) {
      return ['/training-validation', '/resource-center'].includes(this.$route.path) ||
        (!classPolicy.courseId && this.$route.path === '/training-environment') ||
        (classPolicy.courseId && this.$route.path === '/course-platform' && String(this.$route.query.courseId || '') === String(classPolicy.courseId))
    },
    async refreshClassPolicy () {
      if (this.syncingPolicy) return this.policy
      this.syncingPolicy = true
      try {
      const result = await getUserClassPolicy()
      const classPolicy = result.data || {}
      setClassPolicy(classPolicy)
      const revision = String(classPolicy.revision || '')
      const changed = revision !== this.classRevision
      this.policy = classPolicy
      this.classRevision = revision
      if (classPolicy.active === true && (changed || !this.classRouteAllowed(classPolicy))) this.$router.replace(this.classDestination(classPolicy)).catch(() => {})
      return classPolicy
      } finally { this.syncingPolicy = false }
    },
    async loadAttendance () { const [attendance, policy] = await Promise.all([getAttendanceStatus(), getUserClassPolicy()]);this.attendanceDialog=Boolean(attendance.data&&attendance.data.active&&!attendance.data.checkedIn);setClassPolicy(policy.data||{}) },
    async checkIn () { this.checkingIn=true;try{await checkInAttendance();this.attendanceDialog=false;this.$message.success('签到成功')}finally{this.checkingIn=false} },
    async logout () {
      if (this.previewOnly) return
      try { await logoutUser() } finally { clearSession(); this.$router.replace('/login') }
    }
  }
}
</script>
