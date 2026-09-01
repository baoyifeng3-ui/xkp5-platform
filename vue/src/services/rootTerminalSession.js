import { closeTerminalSession, pollTerminalOutput, sendTerminalInput } from '@/api/TerminalSessions'

export async function pollRootTerminal (sessionId, cursor) {
  const result = await pollTerminalOutput(sessionId, cursor)
  return result && result.data ? result.data : result
}

export async function executeRootTerminalInput (sessionId, data) {
  return sendTerminalInput(sessionId, data)
}

export async function closeRootTerminal (sessionId) {
  if (sessionId) await closeTerminalSession(sessionId)
}
