<template>
  <PlatformShell
    :items="items"
    :active-route="$route.path"
    brand-caption="超级管理"
    workspace-title="平台运维"
    workspace-caption="系统级配置与管理员账号维护"
    :user-name="userName"
    :platform-name="platformName"
    :platform-logo-url="platformLogoUrl"
    @navigate="navigate"
    @logout="logout"
  >
    <router-view />
  </PlatformShell>
</template>

<script>
import PlatformShell from '@/components/PlatformShell.vue'
const { operationsItems, SUPER_ADMIN } = require('@/navigation/roleNavigation')
import { clearSession, getUserName, getRole } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'

export default {
  components: { PlatformShell },
  data: () => ({
    items: getRole() === SUPER_ADMIN
      ? operationsItems
      : operationsItems.filter(item => item.roles && item.roles.includes(getRole()))
  }),
  computed: { userName: () => getUserName() || '管理员', platformName: vm => vm.$store.state.Match.platformName, platformLogoUrl: vm => vm.$store.state.Match.platformLogoUrl },
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
