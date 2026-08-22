<template>
  <PlatformShell
    :items="items"
    :active-route="$route.path"
    brand-caption="学习工作台"
    workspace-title="学习工作台"
    workspace-caption="课程学习、资源使用与实训环境"
    :user-name="userName"
    @navigate="navigate"
    @logout="logout"
  >
    <template v-if="previewOnly" #account-actions><span /></template>
    <router-view />
  </PlatformShell>
</template>

<script>
import PlatformShell from '@/components/PlatformShell.vue'
const { userItems } = require('@/navigation/roleNavigation')
import { clearSession, getRole, getUserName } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
  components: { PlatformShell },
  data: () => ({ items: userItems }),
  computed: {
    previewOnly () { return isPreviewRoute(this.$route, getRole()) },
    userName () { return this.previewOnly ? '参赛端预览' : (getUserName() || '用户') }
  },
  mounted () { if (!this.previewOnly) startUserActivity() },
  beforeDestroy () { if (!this.previewOnly) stopUserActivity() },
  methods: {
    participantLocation (path) {
      return { path, query: this.previewOnly ? { preview: '1' } : undefined }
    },
    navigate (item) {
      this.$router.push(this.participantLocation(item.route)).catch(() => {})
    },
    async logout () {
      if (this.previewOnly) return
      try { await logoutUser() } finally { clearSession(); this.$router.replace('/login') }
    }
  }
}
</script>
