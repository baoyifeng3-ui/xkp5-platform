const USER = 'USER'
const ADMIN = 'ADMIN'
const SUPER_ADMIN = 'SUPER_ADMIN'
const { previewDestinations } = require('../services/participantPreview')

const userItems = [
  { key: 'course', label: '课程平台', icon: 'el-icon-reading', route: '/course-platform' },
  { key: 'resources', label: '资源中心', icon: 'el-icon-folder-opened', route: '/resource-center' },
  { key: 'training', label: '实训环境', icon: 'el-icon-monitor', route: '/training-environment' },
  { key: 'validation', label: '模型验证', icon: 'el-icon-cpu', route: '/training-validation' }
]

const competitionItems = [
  { key: 'mode', label: '模式与环境', route: '/management/competition-mode' },
  { key: 'preview', label: '参赛端预览', route: '/competition-preview' },
  { key: 'control', label: '比赛控制', route: '/Admin?tab=timer' },
  { key: 'rules', label: '赛程赛规', route: '/Admin?tab=rules' },
  { key: 'subjects', label: '试卷题目', route: '/Admin?tab=subjects' },
  { key: 'grading', label: '试卷评分', route: '/Admin?tab=grading' },
  { key: 'accounts', label: '比赛账号与容器绑定', route: '/management/competition-accounts' }
]

const managementItems = [
  { key: 'home', label: '主页', icon: 'el-icon-house', route: '/management' },
  { key: 'competition-group', label: '竞赛管理', icon: 'el-icon-trophy', children: competitionItems },
  { key: 'learning-group', label: '课程与资源', icon: 'el-icon-reading', children: [
    { key: 'courses', label: '课程管理', route: '/management/courses' },
    { key: 'resources', label: '资源管理', route: '/management/resources' }
  ] },
  { key: 'training-group', label: '实训管理', icon: 'el-icon-monitor', children: [
    { key: 'training', label: '实训环境', route: '/management/training' },
    { key: 'devices', label: '设备状态', route: '/management/devices' }
  ] },
  { key: 'platform-group', label: '平台管理', icon: 'el-icon-setting', children: [
    { key: 'users', label: '用户管理', route: '/management/users' },
    { key: 'settings', label: '平台设置', route: '/management/platform-settings' },
    { key: 'image-registry', label: '镜像仓库', route: '/operations/image-registry' }
  ] }
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
