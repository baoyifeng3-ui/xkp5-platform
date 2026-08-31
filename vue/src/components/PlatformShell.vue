<template>
  <div :class="['platform-shell', { 'sidebar-collapsed': sidebarCollapsed }]">
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
        <img v-if="platformLogoUrl" class="shell-brand-logo" :src="platformLogoUrl" alt="平台 Logo">
        <span v-else class="shell-brand-mark" aria-hidden="true">X</span>
        <div class="shell-brand-copy">
          <strong>{{ platformName }}</strong>
          <small>{{ brandCaption }}</small>
        </div>
        <button v-if="!mobileViewport" type="button" class="shell-sidebar-toggle" aria-label="切换导航栏" :aria-pressed="sidebarCollapsed ? 'true' : 'false'" @click="toggleSidebar">
          <i :class="sidebarCollapsed ? 'el-icon-arrow-right' : 'el-icon-arrow-left'" aria-hidden="true" />
        </button>
        <button type="button" class="shell-drawer-close" aria-label="关闭导航" @click="closeDrawer">
          <i class="el-icon-close" aria-hidden="true" />
        </button>
      </div>

      <el-menu :default-active="activeRoute" class="shell-menu" unique-opened @select="selectItem">
        <template v-for="(item, index) in items">
          <el-submenu v-if="item.children && item.children.length" :key="item.key" :index="item.key">
            <template slot="title"><span :class="['shell-menu-icon', `shell-menu-icon--${index % 4}`]"><i :class="item.icon || 'el-icon-menu'" /></span><span class="menu-text">{{ item.label }}</span></template>
            <el-menu-item v-for="child in item.children" :key="child.key" :index="child.route" class="shell-submenu-item">{{ child.label }}</el-menu-item>
          </el-submenu>
          <el-menu-item v-else :key="item.key" :index="item.activeKey || item.route || item.key"><span :class="['shell-menu-icon', `shell-menu-icon--${index % 4}`]"><i :class="item.icon || 'el-icon-menu'" /></span><span class="menu-text">{{ item.label }}</span></el-menu-item>
        </template>
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
        <nav class="shell-breadcrumb" aria-label="当前位置">
          <template v-for="(label, index) in currentBreadcrumbs">
            <i v-if="index" :key="`separator-${index}`">/</i>
            <strong v-if="index === currentBreadcrumbs.length - 1" :key="`current-${index}`">{{ label }}</strong>
            <span v-else :key="`parent-${index}`">{{ label }}</span>
          </template>
        </nav>
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
    platformName: { type: String, default: 'XKP5.0平台' },
    platformLogoUrl: { type: String, default: '' },
    activeRoute: { type: String, default: '' }
  },
  data () {
    return { drawerOpen: false, sidebarCollapsed: localStorage.getItem('platform-sidebar-collapsed') === '1', mobileViewport: false, drawerMedia: null, searchQuery: '' }
  },
  computed: {
    searchResults () {
      const query = this.searchQuery.toLowerCase()
      if (!query) return []
      return this.flatItems.filter(item => item.label.toLowerCase().includes(query)).slice(0, 6)
    },
    drawerHidden () {
      return this.mobileViewport && !this.drawerOpen
    },
    userInitial () {
      return String(this.userName || '用户').trim().slice(0, 1).toUpperCase()
    },
    flatItems () { return this.items.reduce((all, item) => all.concat(item.children || [item]), []) },
    currentBreadcrumbs () {
      const path = this.$route.path
      const fullPath = this.$route.fullPath
      const aliases = {
        '/management/competition': ['竞赛管理'],
        '/management/course-platform': ['课程与资源', '课程平台'],
        '/management/demo/courses': ['课程与资源', '课程平台'],
        '/management/demo/resources': ['课程与资源', '资源中心'],
        '/management/demo/training': ['实训管理', '实训环境'],
        '/management/demo/validation': ['实训管理', '模型验证'],
        '/management/license': ['平台管理', '平台授权'],
        '/competition-preview': ['竞赛管理', '参赛端预览']
      }
      if (aliases[path]) return aliases[path]
      for (const item of this.items) {
        const children = item.children || []
        const child = children.find(value => value.route && (fullPath === value.route || (!value.route.includes('?') && path === value.route)))
        if (child) return [item.label, child.label]
        if (!children.length && item.route && path === item.route.split('?')[0]) {
          return item.key === 'home' ? ['首页', this.workspaceTitle] : [item.label]
        }
      }
      return ['首页', this.$route.meta && this.$route.meta.title ? this.$route.meta.title : this.workspaceTitle]
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
    toggleSidebar () {
      if (this.mobileViewport) return this.openDrawer()
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem('platform-sidebar-collapsed', this.sidebarCollapsed ? '1' : '0')
    },
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
      const item = this.flatItems.find(candidate => (candidate.activeKey || candidate.route || candidate.key) === index)
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
.shell-main {
  position: relative;
}
.shell-breadcrumb {
  display: flex;
  min-height: 40px;
  margin: 0;
  padding: 0 148px 0 2px;
  align-items: center;
  gap: 9px;
  color: var(--ui-muted);
  font-size: 13px;
}
.shell-breadcrumb i {
  color: #a7b1ba;
  font-style: normal;
}
.shell-breadcrumb strong {
  color: var(--ui-text);
  font-weight: 500;
}
.shell-main > .module-page > .module-heading,
.shell-main > .module-page > .page-heading,
.shell-main > .module-composed-page > .module-heading,
.shell-main > .module-page > .file-heading,
.shell-main > .module-page > .course-heading:not(.detail-heading) {
  position: absolute;
  z-index: 2;
  top: 14px;
  right: 16px;
  min-height: 40px;
  margin: 0;
  align-items: center;
  background: #f7f8fb;
}
.shell-main > .module-page > .module-heading h1,
.shell-main > .module-page > .page-heading h1,
.shell-main > .module-composed-page > .module-heading h1,
.shell-main > .module-page > .file-heading h1,
.shell-main > .module-page > .course-heading:not(.detail-heading) h1 {
  display: none;
}
.shell-main > .module-page > .module-heading p,
.shell-main > .module-page > .page-heading p,
.shell-main > .module-composed-page > .module-heading p,
.shell-main > .module-page > .file-heading p,
.shell-main > .module-page > .course-heading:not(.detail-heading) p {
  display: none;
}
@media (max-width: 720px) {
  .shell-breadcrumb {
    min-height: 36px;
    padding-right: 2px;
  }
  .shell-main > .module-page > .module-heading,
  .shell-main > .module-page > .page-heading,
  .shell-main > .module-composed-page > .module-heading,
  .shell-main > .module-page > .file-heading,
  .shell-main > .module-page > .course-heading:not(.detail-heading) {
    position: static;
    min-height: 0;
    margin-bottom: 8px;
    background: transparent;
  }
}
</style>
