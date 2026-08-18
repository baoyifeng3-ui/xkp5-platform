import Vue from 'vue'
import VueRouter from 'vue-router'

import Home from '@/views/Home.vue'
import Publicity from '@/views/Publicity.vue'
import Login from '@/views/Login.vue'
import Layout from '@/views/Layout.vue'
import Match from '@/views/Match.vue'
import Matchs from '@/views/Matchs.vue'
import Rank from '@/views/Rank.vue'
import Detect from '@/views/Detect.vue'
import QuestionA from '@/views/QuestionA.vue'
import QuestionB from '@/views/QuestionB.vue'
import Question from '@/components/Question.vue'
import dthj from '@/components/dthj.vue'
import Admin from '@/views/Admin.vue'
import ManagementShell from '@/layouts/ManagementShell.vue'
import NormalUserShell from '@/layouts/NormalUserShell.vue'
import OperationsShell from '@/layouts/OperationsShell.vue'
import ManagementHome from '@/views/management/ManagementHome.vue'
import CourseManagement from '@/views/management/CourseManagement.vue'
import ResourceManagement from '@/views/management/ResourceManagement.vue'
import TrainingManagement from '@/views/management/TrainingManagement.vue'
import UserManagement from '@/views/management/UserManagement.vue'
import DeviceManagement from '@/views/management/DeviceManagement.vue'
import PlatformLicense from '@/views/management/PlatformLicense.vue'
import CompetitionPreview from '@/views/management/CompetitionPreview.vue'
import CoursePlatform from '@/views/user/CoursePlatform.vue'
import ResourceCenter from '@/views/user/ResourceCenter.vue'
import TrainingEnvironment from '@/views/user/TrainingEnvironment.vue'
import OperationsHome from '@/views/operations/OperationsHome.vue'
import AdministratorManagement from '@/views/operations/AdministratorManagement.vue'
import LicenseDiagnostics from '@/views/operations/LicenseDiagnostics.vue'
import ChangePassword from '@/views/ChangePassword.vue'
import { getToken, mustChangePassword, getRole, landingRoute, getCompetitionAccessPhase } from '@/utils/auth'

Vue.use(VueRouter)

const routes = [
  { path: '/login', name: 'Login', component: Login },
  { path: '/change-password', name: 'ChangePassword', component: ChangePassword },
  {
    path: '/operations', component: OperationsShell, meta: { roles: ['SUPER_ADMIN'] },
    children: [
      { path: '', name: 'OperationsHome', component: OperationsHome },
      { path: 'administrators', name: 'AdministratorManagement', component: AdministratorManagement },
      { path: 'license', name: 'LicenseDiagnostics', component: LicenseDiagnostics }
    ]
  },
  {
    path: '/management', component: ManagementShell, meta: { roles: ['ADMIN'] },
    children: [
      { path: '', name: 'ManagementHome', component: ManagementHome },
      { path: 'courses', name: 'CourseManagement', component: CourseManagement },
      { path: 'resources', name: 'ResourceManagement', component: ResourceManagement },
      { path: 'training', name: 'TrainingManagement', component: TrainingManagement },
      { path: 'users', name: 'UserManagement', component: UserManagement },
      { path: 'devices', name: 'DeviceManagement', component: DeviceManagement },
      { path: 'license', name: 'PlatformLicense', component: PlatformLicense },
      { path: '/competition-preview', name: 'CompetitionPreview', component: CompetitionPreview },
      { path: '/Admin', name: 'Admin', component: Admin }
    ]
  },
  {
    path: '/course-platform', component: NormalUserShell, meta: { roles: ['USER'] },
    children: [
      { path: '', name: 'CoursePlatform', component: CoursePlatform },
      { path: '/resource-center', name: 'ResourceCenter', component: ResourceCenter },
      { path: '/training-environment', name: 'TrainingEnvironment', component: TrainingEnvironment }
    ]
  },
  {
    path: '/Layout',
    name: 'Layout',
    component: Layout,
    children: [
      { path: '/Home', name: 'Home', component: Home },
      { path: '/Publicity', name: 'Publicity', component: Publicity },
      { path: '/Match', name: 'Match', component: Match },
      { path: '/Matchs', name: 'Matchs', component: Matchs },
      { path: '/Rank', name: 'Rank', component: Rank },
      { path: '/Detect', name: 'Detect', component: Detect },
      { path: '/QuestionA', name: 'QuestionA', component: QuestionA },
      { path: '/QuestionB', name: 'QuestionB', component: QuestionB },
      { path: '/Question', name: 'Question', component: Question },
      { path: '/dthj', name: 'dthj', component: dthj }
    ]
  }
]

const router = new VueRouter({ mode: 'hash', base: process.env.BASE_URL, routes })

router.beforeEach((to, from, next) => {
  const loggedIn = Boolean(getToken())
  if (!loggedIn && to.path !== '/login') {
    return next({ path: '/login' })
  }
  if (loggedIn && to.path === '/login') {
    return next(landingRoute())
  }
  if (loggedIn && mustChangePassword() && to.path !== '/change-password') {
    return next({ path: '/change-password' })
  }
  if (loggedIn && !mustChangePassword() && to.path === '/change-password') {
    return next(landingRoute())
  }
  const requiredRoles = to.matched.reduce((roles, record) => record.meta && record.meta.roles ? record.meta.roles : roles, null)
  if (requiredRoles && !requiredRoles.includes(getRole())) {
    return next(landingRoute())
  }
  if (loggedIn && getRole() === 'USER' && getCompetitionAccessPhase() === 'PRE_START' && ['/Question', '/Detect'].includes(to.path)) {
    return next({ path: '/Publicity' })
  }
  if (to.path === '/') {
    return next(loggedIn ? landingRoute() : { path: '/login' })
  }
  next()
})

export default router
