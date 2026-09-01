import request from '@/utils/request.js'

export const createTerminalSession = agentId => request.post(
  `/operations/processing-agents/${agentId}/ssh-sessions`,
  {},
  { headers: { 'Content-Type': 'application/json' } }
)

export const getTerminalSession = sessionId => request.get(
  `/operations/terminal-sessions/${sessionId}`
)

export const issueBrowserTicket = sessionId => request.post(
  `/operations/terminal-sessions/${sessionId}/browser-ticket`,
  {},
  { headers: { 'Content-Type': 'application/json' } }
)

export const closeTerminalSession = sessionId => request.delete(
  `/operations/ssh-sessions/${sessionId}`,
  { silentError: true }
)

export const pollTerminalOutput = (sessionId, cursor = 0) => request.get(
  `/operations/ssh-sessions/${sessionId}/output`, { params: { cursor } }
)

export const sendTerminalInput = (sessionId, data) => request.post(
  `/operations/ssh-sessions/${sessionId}/input`, { data },
  { headers: { 'Content-Type': 'application/json' } }
)
