import { closeTerminalSession, issueBrowserTicket, getTerminalSession } from '@/api/TerminalSessions'

export function websocketUrl (sessionId) {
  const origin = window.location.origin.replace(/^http:/i, 'ws:').replace(/^https:/i, 'wss:')
  return `${origin}/terminal/v1/browser/${encodeURIComponent(sessionId)}`
}

export async function connectRootTerminal (sessionId, callbacks = {}) {
  const ticketResult = await issueBrowserTicket(sessionId)
  const ticketData = ticketResult && ticketResult.data ? ticketResult.data : ticketResult
  const ticket = ticketData && (ticketData.ticket || ticketData.browserTicket)
  if (!ticket) throw new Error('TERMINAL_TICKET_UNAVAILABLE')
  const socket = new WebSocket(websocketUrl(sessionId), ['xkp-terminal.v1', `ticket.${ticket}`])
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
