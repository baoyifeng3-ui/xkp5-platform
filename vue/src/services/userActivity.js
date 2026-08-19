import request from '@/utils/request.js'

const TOUCH_INTERVAL = 60000
let timer = null
let lastTouch = 0

function touch () {
  if (document.hidden || Date.now() - lastTouch < TOUCH_INTERVAL) return
  lastTouch = Date.now()
  request({ url: 'user/activity', method: 'post' }).catch(() => {})
}

function visibilityChanged () { if (!document.hidden) touch() }

export function startUserActivity () {
  if (timer) return
  touch()
  timer = setInterval(touch, TOUCH_INTERVAL)
  document.addEventListener('visibilitychange', visibilityChanged)
}

export function stopUserActivity () {
  if (timer) clearInterval(timer)
  timer = null
  lastTouch = 0
  document.removeEventListener('visibilitychange', visibilityChanged)
}

export async function logoutUser () {
  try {
    await request({ url: 'user/logout', method: 'post' })
  } finally {
    stopUserActivity()
  }
}
