<template>
  <el-dialog :visible="visible" custom-class="root-terminal-dialog" fullscreen :show-close="false" :close-on-click-modal="false" @opened="dialogOpened">
    <header class="terminal-header">
      <div class="terminal-title"><i class="el-icon-monitor"/><strong>{{ agentName }} SSH 终端</strong><el-tag size="mini" type="danger">超级管理员</el-tag><el-tag size="mini">终端仿真</el-tag></div>
      <div class="terminal-meta"><span>{{ errorMessage || (connected ? '已连接' : '正在连接') }}</span><el-button aria-label="关闭终端" type="danger" icon="el-icon-close" circle @click="close"/></div>
    </header>
    <div ref="terminalHost" class="terminal-host" @click="focusTerminal"/>
  </el-dialog>
</template>
<script>
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'
import { pollRootTerminal, executeRootTerminalInput, closeRootTerminal } from '@/services/rootTerminalSession'

export default {
  name: 'RootTerminalDialog',
  props: { visible: { type: Boolean, default: false }, session: { type: Object, default: null }, agentName: { type: String, default: '处理服务器' } },
  data: () => ({ terminal: null, fit: null, observer: null, cursor: 0, pollTimer: null, inputQueue: '', flushTimer: null, sending: false, closing: false, remoteClosed: false, connected: false, errorMessage: '', initialized: false }),
  watch: { visible (value) { if (value) this.$nextTick(() => window.setTimeout(this.initializeTerminal, 120)) } },
  mounted () { if (this.visible) this.$nextTick(() => window.setTimeout(this.initializeTerminal, 120)) },
  beforeDestroy () { this.teardown() },
  methods: {
    dialogOpened () { this.$nextTick(() => window.setTimeout(this.initializeTerminal, 80)) },
    initializeTerminal () {
      if (this.initialized || !this.session || !this.$refs.terminalHost) return
      const host = this.$refs.terminalHost
      if (host.clientWidth < 40 || host.clientHeight < 40) { window.setTimeout(this.initializeTerminal, 80); return }
      this.initialized = true
      this.terminal = new Terminal({ cursorBlink: true, convertEol: false, scrollback: 5000, fontSize: 14, fontFamily: 'Consolas, Monaco, monospace', theme: { background: '#101820', foreground: '#e6edf3', cursor: '#ffffff' } })
      this.fit = new FitAddon()
      this.terminal.loadAddon(this.fit)
      this.terminal.open(host)
      this.fit.fit()
      this.terminal.write('\x1b[32m正在建立处理服务器 SSH 会话...\x1b[0m\r\n')
      this.terminal.focus()
      this.terminal.onData(data => this.queueInput(data))
      if (window.ResizeObserver) { this.observer = new ResizeObserver(() => this.resize()); this.observer.observe(host) }
      window.addEventListener('resize', this.resize)
      this.poll()
    },
    focusTerminal () { if (this.terminal) this.terminal.focus() },
    resize () { if (!this.fit || !this.$refs.terminalHost) return; window.requestAnimationFrame(() => { try { this.fit.fit() } catch (e) {} }) },
    queueInput (data) { if (this.closing) return; this.inputQueue += data; if (!this.flushTimer) this.flushTimer = setTimeout(this.flushInput, 15) },
    async flushInput () {
      this.flushTimer = null
      if (this.sending || !this.inputQueue || this.closing) return
      const data = this.inputQueue.slice(0, 4096)
      this.inputQueue = this.inputQueue.slice(data.length)
      this.sending = true
      try { await executeRootTerminalInput(this.session.sessionId, data) } catch (e) { this.errorMessage = 'SSH 输入发送失败' } finally { this.sending = false; if (this.inputQueue) this.flushTimer = setTimeout(this.flushInput, 15); this.focusTerminal() }
    },
    async poll () {
      if (this.closing || !this.terminal) return
      try {
        const data = await pollRootTerminal(this.session.sessionId, this.cursor)
        if (data.data) { this.terminal.write(data.data); this.cursor = Number(data.nextCursor || this.cursor); this.connected = true; this.errorMessage = ''; this.focusTerminal() }
        if (data.closed) { this.terminal.write('\r\nSSH 会话已关闭\r\n'); return }
      } catch (e) { this.errorMessage = 'SSH 输出连接失败' } finally { if (!this.closing) this.pollTimer = setTimeout(this.poll, 200) }
    },
    async close () { if (this.closing) return; this.closing = true; await this.teardown(); this.$emit('update:visible', false); this.$emit('closed') },
    async teardown () {
      if (this.pollTimer) { clearTimeout(this.pollTimer); this.pollTimer = null }
      if (this.flushTimer) { clearTimeout(this.flushTimer); this.flushTimer = null }
      window.removeEventListener('resize', this.resize)
      if (this.observer) { this.observer.disconnect(); this.observer = null }
      if (!this.remoteClosed && this.session && this.session.sessionId) { this.remoteClosed = true; try { await closeRootTerminal(this.session.sessionId) } catch (e) {} }
      if (this.terminal) { this.terminal.dispose(); this.terminal = null; this.fit = null }
    }
  }
}
</script>
<style>.root-terminal-dialog .el-dialog__header,.root-terminal-dialog .el-dialog__body{padding:0}.root-terminal-dialog .el-dialog__body{height:100vh;display:flex;flex-direction:column;background:#101820}.terminal-header{height:52px;flex:0 0 52px;padding:0 18px;display:flex;align-items:center;justify-content:space-between;color:#e6edf3;background:#172532}.terminal-title,.terminal-meta{display:flex;align-items:center;gap:10px}.terminal-host{position:relative;flex:1 1 auto;width:100%;min-width:0;min-height:0;padding:12px;overflow:hidden;background:#101820;box-sizing:border-box}.terminal-host .xterm{width:100%;height:100%;padding:0}.terminal-host .xterm-screen{width:100%!important}.terminal-host .xterm-viewport{overflow-y:auto!important}</style>
