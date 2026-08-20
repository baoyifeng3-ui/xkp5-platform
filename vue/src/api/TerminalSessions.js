import request from '@/utils/request.js'

export const createTerminalSession = agentId => request.post(
  `/operations/processing-agents/${agentId}/terminal-sessions`,
  { confirmation: 'OPEN_ROOT_TERMINAL' }
)

export const getTerminalSession = sessionId => request.get(
  `/operations/terminal-sessions/${sessionId}`
)

export const issueBrowserTicket = sessionId => request.post(
  `/operations/terminal-sessions/${sessionId}/browser-ticket`
)

export const closeTerminalSession = sessionId => request.delete(
  `/operations/terminal-sessions/${sessionId}`
)
