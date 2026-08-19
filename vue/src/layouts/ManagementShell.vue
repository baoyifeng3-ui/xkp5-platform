<template>
  <el-container class="platform-shell">
    <el-aside width="238px" class="shell-aside">
      <div class="shell-brand"><span class="shell-brand-mark">X</span><div class="shell-brand-copy"><strong>XKP5.0平台</strong><small>管理服务器</small></div></div>
      <el-menu :default-active="$route.path" :default-openeds="['competition']" router class="shell-menu">
        <template v-for="item in items">
          <el-submenu v-if="item.children" :key="item.key" :index="item.key">
            <template slot="title"><i :class="item.icon" /><span class="menu-text">{{ item.label }}</span></template>
            <el-menu-item v-for="child in item.children" :key="child.key" :index="child.route">{{ child.label }}</el-menu-item>
          </el-submenu>
          <el-menu-item v-else :key="item.key" :index="item.route"><i :class="item.icon" /><span class="menu-text">{{ item.label }}</span></el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="shell-header"><div class="shell-header-copy"><strong>管理工作台</strong><small>课程、资源、实训与竞赛业务管理</small></div><div class="shell-account"><span>{{ userName }}</span><el-button type="text" @click="logout">退出</el-button></div></el-header>
      <el-main class="shell-main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script>
const { managementItems } = require('@/navigation/roleNavigation')
import { clearSession, getUserName } from '@/utils/auth'
import { logoutUser, startUserActivity, stopUserActivity } from '@/services/userActivity'
export default {
  data: () => ({ items: managementItems }),
  computed: { userName: () => getUserName() || '管理员' },
  mounted () { startUserActivity() },
  beforeDestroy () { stopUserActivity() },
  methods: { async logout () { try { await logoutUser() } finally { clearSession(); this.$router.replace('/login') } } }
}
</script>
<style>@import url(../assets/style/platform-shell.css);</style>
