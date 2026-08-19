<template>
  <el-container class="platform-shell">
    <el-aside width="220px" class="shell-aside">
      <div class="shell-brand"><span class="shell-brand-mark">X</span><div class="shell-brand-copy"><strong>XKP5.0平台</strong><small>学习工作台</small></div></div>
      <el-menu :default-active="$route.path" router class="shell-menu"><el-menu-item v-for="item in items" :key="item.key" :index="item.route"><i :class="item.icon" /><span class="menu-text">{{ item.label }}</span></el-menu-item></el-menu>
    </el-aside>
    <el-container><el-header class="shell-header"><div class="shell-header-copy"><strong>学习工作台</strong><small>课程学习、资源使用与实训环境</small></div><div class="shell-account"><span>{{ userName }}</span><el-button type="text" @click="logout">退出</el-button></div></el-header><el-main class="shell-main"><router-view /></el-main></el-container>
  </el-container>
</template>
<script>
const { userItems } = require('@/navigation/roleNavigation')
import { clearSession, getUserName } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'
export default { data: () => ({ items: userItems }), computed: { userName: () => getUserName() || '用户' }, mounted () { startUserActivity() }, beforeDestroy () { stopUserActivity() }, methods: { async logout () { try { await logoutUser() } finally { clearSession(); this.$router.replace('/login') } } } }
</script>
<style>@import url(../assets/style/platform-shell.css);</style>
