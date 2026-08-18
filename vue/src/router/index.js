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
import { getToken, mustChangePassword, isAdmin, getCompetitionAccessPhase } from '@/utils/auth'

Vue.use(VueRouter)

const routes = [
  { path: '/login', name: 'Login', component: Login },
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
      { path: '/dthj', name: 'dthj', component: dthj },
      { path: '/Admin', name: 'Admin', component: Admin, meta: { admin: true } }
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
    return next({ path: '/Publicity' })
  }
  if (to.meta && to.meta.admin && !isAdmin()) {
    return next({ path: '/Publicity' })
  }
  if (loggedIn && mustChangePassword() && to.path !== '/Admin') {
    return next({ path: '/Admin' })
  }
  if (loggedIn && !isAdmin() && getCompetitionAccessPhase() === 'PRE_START' && !['/Publicity', '/Home'].includes(to.path)) {
    return next({ path: '/Publicity' })
  }
  if (to.path === '/') {
    return next({ path: loggedIn ? '/Publicity' : '/login' })
  }
  next()
})

export default router
