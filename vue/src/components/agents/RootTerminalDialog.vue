<template>
  <el-dialog :visible="visible" custom-class="root-terminal-dialog" fullscreen :show-close="false" :close-on-click-modal="false" @close="close">
    <header class="terminal-header">
      <div class="terminal-title"><i class="el-icon-monitor" /><strong>{{ agentName }}</strong><el-tag size="mini" type="danger">ROOT</el-tag></div>
      <div class="terminal-meta"><span v-if="state">{{ stateLabel }}</span><span v-if="countdown">{{ countdown }}</span><el-button aria-label="关闭终端" title="关闭终端" type="danger" icon="el-icon-close" circle @click="close" /></div>
    </header>
    <div ref="terminal" class="terminal-body" />
    <div v-if="errorMessage" class="terminal-overlay"><i class="el-icon-warning-outline" /><span>{{ errorMessage }}</span><el-button size="small" @click="close">关闭</el-button></div>
  </el-dialog>
</template>

<script>
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import { connectRootTerminal, sendResize, closeRootTerminal, getTerminalSession } from '@/services/rootTerminalSession'

const LABELS = { WAITING_AGENT: '等待 Agent 连接', WAITING_BROWSER: '建立终端连接', ACTIVE: '终端已连接', CLOSING: '正在关闭', CLOSED: '终端已关闭', FAILED: '终端不可用' }

export default {
  name: 'RootTerminalDialog',
  props: { visible: { type: Boolean, default: false }, session: { type: Object, default: null }, agentName: { type: String, default: '处理服务器' } },
  data: () => ({ state: 'WAITING_AGENT', errorMessage: '', countdown: '', terminal: null, fit: null, socket: null, timer: null, closing: false }),
  computed: { stateLabel () { return LABELS[this.state] || this.state } },
  watch: { visible (value) { if (value) this.start(); else this.teardown(false) } },
  mounted () { if (this.visible) this.start() },
  beforeDestroy () { this.teardown(true) },
  methods: {
    async start () {
      if (!this.session || this.terminal) return
      this.state = this.session.state || 'WAITING_AGENT'
      this.errorMessage = ''
      this.terminal = new Terminal({ convertEol: true, cursorBlink: true, scrollback: 2000, fontSize: 14, theme: { background: '#101820', foreground: '#e6edf3' } })
      this.fit = new FitAddon()
      this.terminal.loadAddon(this.fit)
      this.terminal.open(this.$refs.terminal)
      this.fitTerminal()
      this.terminal.onData(data => { if (this.socket && this.socket.readyState === WebSocket.OPEN) this.socket.send(new TextEncoder().encode(data)) })
      window.addEventListener('resize', this.fitTerminal)
      this.startCountdown()
      try {
        const connection = await connectRootTerminal(this.session.sessionId, { onOpen: () => { this.state = 'ACTIVE'; this.fitTerminal() }, onMessage: data => this.terminal && this.terminal.write(typeof data === 'string' ? data : new Uint8Array(data)), onError: () => this.fail('终端连接失败'), onClose: event => { if (!this.closing && event.code !== 1000) this.fail('终端连接已断开'); else if (!this.closing) this.state = 'CLOSED' } })
        if (this.closing) { connection.socket.close(); return }
        this.socket = connection.socket
      } catch (error) { this.fail('终端票据获取失败') }
    },
    fitTerminal () { if (!this.fit || !this.socket) return; this.fit.fit(); sendResize(this.socket, this.terminal.cols, this.terminal.rows) },
    startCountdown () {
      const expiry = this.session && (this.session.absoluteExpiresAt || this.session.agentConnectionDeadline)
      if (!expiry) return
      const tick = () => { const remaining = Math.max(0, new Date(expiry).getTime() - Date.now()); this.countdown = `${Math.ceil(remaining / 1000)} 秒`; if (remaining <= 0) { this.fail('终端会话已过期'); return } this.timer = setTimeout(tick, 1000) }
      tick()
    },
    fail (message) { this.state = 'FAILED'; this.errorMessage = message },
    async close () { if (this.closing) return; this.closing = true; this.state = 'CLOSING'; await this.teardown(false); this.$emit('update:visible', false); this.$emit('closed') },
    async teardown (destroying) {
      if (this.timer) { clearTimeout(this.timer); this.timer = null }
      window.removeEventListener('resize', this.fitTerminal)
      const socket = this.socket; this.socket = null
      if (socket && socket.readyState < WebSocket.CLOSING) socket.close(1000, 'OPERATOR_CLOSED')
      if (this.session && this.session.sessionId) { try { await closeRootTerminal(this.session.sessionId, socket) } catch (error) {} }
      if (this.terminal) { this.terminal.dispose(); this.terminal = null; this.fit = null }
    }
  }
}
</script>

<style>
.root-terminal-dialog .el-dialog__header, .root-terminal-dialog .el-dialog__body { padding: 0; }
.root-terminal-dialog .el-dialog__body { height: 100vh; display: flex; flex-direction: column; background: #101820; }
.terminal-header { min-height: 52px; padding: 0 18px; display: flex; align-items: center; justify-content: space-between; color: #e6edf3; background: #172532; }
.terminal-title, .terminal-meta { display: flex; align-items: center; gap: 10px; }
.terminal-body { flex: 1; min-height: 0; padding: 12px; }
.terminal-overlay { position: absolute; inset: 52px 0 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; color: #e6edf3; background: rgba(16, 24, 32, .94); }
</style>
