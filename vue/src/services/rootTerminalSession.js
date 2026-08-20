import { closeTerminalSession, issueBrowserTicket, getTerminalSession } from '@/api/TerminalSessions'

export function websocketUrl (sessionId) {
  const origin = window.location.origin.replace(/^http:/i, 'ws:').replace(/^https:/i, 'wss:')
  return `${origin}/terminal/v1/browser/${encodeURIComponent(sessionId)}`
}

function ensureConnectable (signal, deadline) {
  if (signal && signal.aborted) throw new Error('TERMINAL_CANCELLED')
  if (deadline && Date.now() >= deadline) throw new Error('TERMINAL_CONNECTION_TIMEOUT')
}

function waitForPoll (signal, deadline) {
  ensureConnectable(signal, deadline)
  const remaining = deadline ? Math.max(0, deadline - Date.now()) : 1000
  return new Promise((resolve, reject) => {
    const onAbort = () => { clearTimeout(timer); reject(new Error('TERMINAL_CANCELLED')) }
    const timer = setTimeout(() => { if (signal) signal.removeEventListener('abort', onAbort); resolve() }, Math.min(1000, remaining))
    if (signal) signal.addEventListener('abort', onAbort, { once: true })
  })
}

export async function connectRootTerminal (sessionId, callbacks = {}, options = {}) {
  const signal = options.signal
  const parsedDeadline = options.deadline ? new Date(options.deadline).getTime() : NaN
  const deadline = Number.isFinite(parsedDeadline) ? parsedDeadline : Date.now() + 90000
  ensureConnectable(signal, deadline)
  let status = await getTerminalSession(sessionId)
  ensureConnectable(signal, deadline)
  status = status && status.data ? status.data : status
  while (status && status.state === 'WAITING_AGENT') {
    await waitForPoll(signal, deadline)
    status = await getTerminalSession(sessionId)
    ensureConnectable(signal, deadline)
    status = status && status.data ? status.data : status
    if (callbacks.onState && status && status.state) callbacks.onState(status)
  }
  if (!status || ['FAILED', 'CLOSED', 'EXPIRED'].includes(status.state)) throw new Error('TERMINAL_NOT_READY')
  const ticketResult = await issueBrowserTicket(sessionId)
  ensureConnectable(signal, deadline)
  const ticketData = ticketResult && ticketResult.data ? ticketResult.data : ticketResult
  const ticket = ticketData && (ticketData.ticket || ticketData.browserTicket)
  if (!ticket) throw new Error('TERMINAL_TICKET_UNAVAILABLE')
  const socket = new WebSocket(websocketUrl(sessionId), ['xkp-terminal-v1', `xkp-terminal-ticket.${ticket}`])
  socket.binaryType = 'arraybuffer'
  socket.onopen = event => callbacks.onOpen && callbacks.onOpen(event, socket)
  socket.onmessage = event => callbacks.onMessage && callbacks.onMessage(event.data)
  socket.onerror = event => callbacks.onError && callbacks.onError(event)
  socket.onclose = event => callbacks.onClose && callbacks.onClose(event)
  return { socket, ticket }
}

export function sendResize (socket, columns, rows) {
  if (!socket || socket.readyState !== WebSocket.OPEN) return false
  const boundedColumns = Math.max(20, Math.min(500, Math.floor(Number(columns) || 20)))
  const boundedRows = Math.max(5, Math.min(200, Math.floor(Number(rows) || 5)))
  socket.send(JSON.stringify({ type: 'resize', columns: boundedColumns, rows: boundedRows }))
  return true
}

export function sendTerminalClose (socket, reason = 'OPERATOR_CLOSED') {
  if (!socket || socket.readyState !== WebSocket.OPEN) return false
  socket.send(JSON.stringify({ type: 'close', reason }))
  return true
}

export async function closeRootTerminal (sessionId, socket, reason = 'OPERATOR_CLOSED') {
  sendTerminalClose(socket, reason)
  if (socket && socket.readyState < WebSocket.CLOSING) socket.close(1000, reason)
  if (sessionId) await closeTerminalSession(sessionId)
}

export { getTerminalSession }
