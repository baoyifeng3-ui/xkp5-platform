const USER = 'USER'
const ADMIN = 'ADMIN'
const SUPER_ADMIN = 'SUPER_ADMIN'
const { previewDestinations } = require('../services/participantPreview')

const userItems = [
  { key: 'course', label: '课程平台', icon: 'el-icon-reading', route: '/course-platform' },
  { key: 'resources', label: '资源中心', icon: 'el-icon-folder-opened', route: '/resource-center' },
  { key: 'training', label: '实训环境', icon: 'el-icon-monitor', route: '/training-environment' }
]

const competitionItems = [
  { key: 'mode', label: '模式与环境', route: '/management/competition-mode' },
  { key: 'preview', label: '参赛端预览', route: '/competition-preview' },
  { key: 'control', label: '比赛控制', route: '/Admin?tab=timer' },
  { key: 'rules', label: '赛程赛规', route: '/Admin?tab=rules' },
  { key: 'subjects', label: '试卷题目', route: '/Admin?tab=subjects' },
  { key: 'grading', label: '试卷评分', route: '/Admin?tab=grading' },
  { key: 'accounts', label: '比赛账号', route: '/Admin?tab=users' }
]

const managementItems = [
  { key: 'home', label: '主页', icon: 'el-icon-house', route: '/management' },
  { key: 'competition', label: '竞赛管理', icon: 'el-icon-trophy', route: '/management/competition' },
  { key: 'courses', label: '课程管理', icon: 'el-icon-reading', route: '/management/courses' },
  { key: 'resources', label: '资源管理', icon: 'el-icon-folder-opened', route: '/management/resources' },
  { key: 'training', label: '实训管理', icon: 'el-icon-monitor', route: '/management/training' },
  { key: 'users', label: '用户管理', icon: 'el-icon-user', route: '/management/users' },
  { key: 'devices', label: '设备管理', icon: 'el-icon-cpu', route: '/management/devices' },
  { key: 'settings', label: '平台设置', icon: 'el-icon-setting', route: '/management/platform-settings' },
  { key: 'image-registry', label: '镜像仓库', icon: 'el-icon-coin', route: '/operations/image-registry' }
]

const operationsItems = [
  { key: 'home', label: '运维主页', icon: 'el-icon-odometer', route: '/operations' },
  { key: 'processing-agents', label: '处理服务器', icon: 'el-icon-cpu', route: '/operations/processing-agents' },
  { key: 'container-templates', label: '容器模板', icon: 'el-icon-box', route: '/operations/container-templates' },
  { key: 'image-registry', label: '镜像仓库', icon: 'el-icon-coin', route: '/operations/image-registry', roles: [ADMIN, SUPER_ADMIN] },
  { key: 'competition-environments', label: '比赛容器', icon: 'el-icon-trophy', route: '/operations/competition-environments' },
  { key: 'administrators', label: '管理员账号', icon: 'el-icon-user', route: '/operations/administrators' },
  { key: 'license', label: '授权诊断', icon: 'el-icon-key', route: '/operations/license' }
]

const routeRoles = {
  '/operations': SUPER_ADMIN,
  '/management': ADMIN,
  '/course-platform': USER
}

function landingRoute (role, mode) {
  if (role === SUPER_ADMIN) return { path: '/operations' }
  if (role === ADMIN) return { path: '/management' }
  return mode === 'COMPETITION' ? { path: '/Publicity' } : { path: '/course-platform' }
}

function legacyAdminRoute (tab) {
  if (tab === 'training') return { path: '/management/devices', replace: true }
  if (tab === 'settings') return { path: '/management/platform-settings', replace: true }
  return null
}

module.exports = {
  USER,
  ADMIN,
  SUPER_ADMIN,
  userItems,
  competitionItems,
  managementItems,
  operationsItems,
  previewDestinations,
  routeRoles,
  landingRoute,
  legacyAdminRoute
}
