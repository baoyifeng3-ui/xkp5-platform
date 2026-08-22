<template>
  <PlatformShell
    :items="items"
    :active-route="$route.path"
    brand-caption="管理服务器"
    workspace-title="管理工作台"
    workspace-caption="课程、资源、实训与竞赛业务管理"
    :user-name="userName"
    :platform-name="platformName"
    @navigate="navigate"
    @logout="logout"
  >
    <router-view />
  </PlatformShell>
</template>

<script>
import PlatformShell from '@/components/PlatformShell.vue'
const { managementItems } = require('@/navigation/roleNavigation')
import { clearSession, getUserName } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'

export default {
  components: { PlatformShell },
  data: () => ({ items: managementItems }),
  computed: { userName: () => getUserName() || '管理员', platformName: vm => vm.$store.state.Match.platformName },
  mounted () { startUserActivity() },
  beforeDestroy () { stopUserActivity() },
  methods: {
    navigate (item) { this.$router.push(item.route).catch(() => {}) },
    async logout () {
      try { await logoutUser() } finally { clearSession(); this.$router.replace('/login') }
    }
  }
}
</script>
