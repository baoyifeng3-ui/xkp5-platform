<template>
  <div class="platform-shell">
    <button
      ref="drawerTrigger"
      type="button"
      class="shell-drawer-trigger"
      aria-label="打开导航"
      :aria-expanded="drawerOpen ? 'true' : 'false'"
      @click="openDrawer"
    >
      <i class="el-icon-menu" aria-hidden="true" />
    </button>

    <button
      v-if="drawerOpen"
      type="button"
      class="shell-drawer-backdrop"
      aria-label="关闭导航"
      @click="closeDrawer"
    />

    <aside
      :class="['shell-sidebar', 'shell-drawer', { 'is-open': drawerOpen }]"
      :aria-hidden="drawerHidden ? 'true' : 'false'"
      :inert="drawerHidden ? '' : null"
    >
      <div class="shell-brand">
        <span class="shell-brand-mark" aria-hidden="true">X</span>
        <div class="shell-brand-copy">
          <strong>XKP5.0平台</strong>
          <small>{{ brandCaption }}</small>
        </div>
        <button type="button" class="shell-drawer-close" aria-label="关闭导航" @click="closeDrawer">
          <i class="el-icon-close" aria-hidden="true" />
        </button>
      </div>

      <el-menu :default-active="activeRoute" class="shell-menu" @select="selectItem">
        <el-tooltip
          v-for="(item, index) in items"
          :key="item.key"
          :content="item.label"
          placement="right"
          :open-delay="500"
        >
          <el-menu-item :index="item.activeKey || item.route || item.key">
            <span :class="['shell-menu-icon', `shell-menu-icon--${index % 4}`]">
              <i :class="item.icon || 'el-icon-menu'" aria-hidden="true" />
            </span>
            <span class="menu-text">{{ item.label }}</span>
          </el-menu-item>
        </el-tooltip>
      </el-menu>
    </aside>

    <section class="shell-workspace">
      <header class="shell-header">
        <div class="shell-heading">
          <strong>{{ workspaceTitle }}</strong>
          <small>{{ workspaceCaption }}</small>
        </div>

        <div class="shell-header-tools">
          <slot name="status" />
          <div class="shell-search">
            <i class="el-icon-search" aria-hidden="true" />
            <input
              v-model.trim="searchQuery"
              type="search"
              aria-label="搜索导航"
              placeholder="搜索功能"
              @keydown.esc="searchQuery = ''"
            >
            <section v-if="searchQuery" class="shell-search-results" aria-label="导航搜索建议">
              <button
                v-for="item in searchResults"
                :key="item.key"
                type="button"
                @click="navigate(item)"
              >
                <i :class="item.icon || 'el-icon-menu'" aria-hidden="true" />
                <span>{{ item.label }}</span>
              </button>
              <span v-if="searchResults.length === 0" class="shell-search-empty">未找到功能</span>
            </section>
          </div>

          <div class="shell-account">
            <span class="shell-avatar" aria-hidden="true">{{ userInitial }}</span>
            <span class="shell-user-name" :title="userName">{{ userName }}</span>
            <slot name="account-actions">
              <el-tooltip content="退出登录" placement="bottom">
                <button type="button" class="shell-icon-button" aria-label="退出登录" @click="$emit('logout')">
                  <i class="el-icon-switch-button" aria-hidden="true" />
                </button>
              </el-tooltip>
            </slot>
          </div>
        </div>
      </header>

      <main class="shell-main">
        <slot />
      </main>
    </section>
  </div>
</template>

<script>
export default {
  name: 'PlatformShell',
  props: {
    items: { type: Array, default: () => [] },
    brandCaption: { type: String, default: '' },
    workspaceTitle: { type: String, required: true },
    workspaceCaption: { type: String, default: '' },
    userName: { type: String, default: '用户' },
    activeRoute: { type: String, default: '' }
  },
  data () {
    return { drawerOpen: false, mobileViewport: false, drawerMedia: null, searchQuery: '' }
  },
  computed: {
    searchResults () {
      const query = this.searchQuery.toLowerCase()
      if (!query) return []
      return this.items.filter(item => item.label.toLowerCase().includes(query)).slice(0, 6)
    },
    drawerHidden () {
      return this.mobileViewport && !this.drawerOpen
    },
    userInitial () {
      return String(this.userName || '用户').trim().slice(0, 1).toUpperCase()
    }
  },
  watch: {
    '$route.fullPath' () {
      this.drawerOpen = false
      this.searchQuery = ''
    }
  },
  mounted () {
    this.drawerMedia = window.matchMedia('(max-width: 720px)')
    this.updateMobileViewport(this.drawerMedia)
    if (this.drawerMedia.addEventListener) this.drawerMedia.addEventListener('change', this.updateMobileViewport)
    else this.drawerMedia.addListener(this.updateMobileViewport)
    window.addEventListener('keydown', this.handleEscape)
  },
  beforeDestroy () {
    window.removeEventListener('keydown', this.handleEscape)
    if (!this.drawerMedia) return
    if (this.drawerMedia.removeEventListener) this.drawerMedia.removeEventListener('change', this.updateMobileViewport)
    else this.drawerMedia.removeListener(this.updateMobileViewport)
  },
  methods: {
    openDrawer () {
      this.drawerOpen = true
    },
    closeDrawer (restoreFocus = true) {
      this.drawerOpen = false
      if (restoreFocus && this.mobileViewport) {
        this.$nextTick(() => {
          if (this.$refs.drawerTrigger) this.$refs.drawerTrigger.focus()
        })
      }
    },
    updateMobileViewport (event) {
      this.mobileViewport = Boolean(event.matches)
      if (!this.mobileViewport) this.drawerOpen = false
    },
    handleEscape (event) {
      if (event.key === 'Escape' && this.drawerOpen) {
        event.preventDefault()
        this.closeDrawer()
      }
    },
    selectItem (index) {
      const item = this.items.find(candidate => (candidate.activeKey || candidate.route || candidate.key) === index)
      if (item) this.navigate(item, true)
    },
    navigate (item, restoreDrawerFocus = false) {
      this.drawerOpen = false
      this.searchQuery = ''
      this.$emit('navigate', item)
      if (restoreDrawerFocus && this.mobileViewport) {
        this.$nextTick(() => {
          if (this.$refs.drawerTrigger) this.$refs.drawerTrigger.focus()
        })
      }
    }
  }
}
</script>

<style>
@import url(../assets/style/platform-theme.css);
@import url(../assets/style/platform-shell.css);
</style>
