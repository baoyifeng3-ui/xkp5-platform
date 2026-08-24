<template>
  <el-dialog title="上传镜像归档" :visible="visible" width="640px" :close-on-click-modal="!uploading" @close="close">
    <el-alert title="仅支持 Docker save 生成的 .tar 或 .tar.gz；上传中断后可选择同一文件继续。" type="info" :closable="false" show-icon />
    <el-form class="upload-form" label-width="112px">
      <el-form-item label="镜像组 ID"><el-input v-model.trim="form.groupId" placeholder="已有镜像组标识" :disabled="uploading" /></el-form-item>
      <el-form-item label="组件"><el-radio-group v-model="form.componentType" :disabled="uploading"><el-radio-button label="ANNOTATION">图像标注</el-radio-button><el-radio-button label="EDITOR">代码编辑</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="版本"><el-input v-model.trim="form.version" placeholder="例如 2026.08.1" :disabled="uploading" /></el-form-item>
      <el-form-item label="仓库名称"><el-input v-model.trim="form.imageRepository" placeholder="例如 xkp/annotation" :disabled="uploading" /></el-form-item>
      <el-form-item label="归档 SHA-256"><el-input v-model.trim="form.expectedSha256" maxlength="64" placeholder="选择文件后自动生成" :disabled="uploading || hashing" /><small class="hash-status" :class="{ 'is-ready': hashComputed }">{{ hashStatus }}</small></el-form-item>
      <el-form-item label="归档文件">
        <input ref="file" class="native-file" type="file" accept=".tar,.gz,.tgz,application/x-tar,application/gzip" :disabled="uploading" @change="selectFile">
        <el-button icon="el-icon-folder-opened" :disabled="uploading" @click="$refs.file.click()">选择文件</el-button><span class="file-name">{{ file ? `${file.name} · ${formatBytes(file.size)}` : '尚未选择' }}</span>
      </el-form-item>
      <el-form-item v-if="uploadId" label="断点状态"><span>{{ uploadId }}</span><el-button type="text" :disabled="uploading" @click="discardResume">放弃并重建</el-button></el-form-item>
      <el-form-item v-if="uploading || progress" label="上传进度"><el-progress :percentage="progress" :status="progress === 100 ? 'success' : undefined" /><small>{{ formatBytes(receivedBytes) }} / {{ file ? formatBytes(file.size) : '--' }}</small></el-form-item>
    </el-form>
    <span slot="footer"><el-button :disabled="uploading || hashing" @click="close">取消</el-button><el-button type="primary" :loading="uploading" :disabled="hashing || !hashComputed" @click="start">{{ uploadId ? '继续上传' : '开始上传' }}</el-button></span>
  </el-dialog>
</template>

<script>
import { createImageUpload, getImageUploadStatus, uploadImageChunk, completeImageUpload, cancelImageUpload } from '@/services/imageRegistry'

const CHUNK_SIZE = 8 * 1024 * 1024
const MAX_CHUNK_RETRIES = 3
const RESUME_KEY = 'xkp:image-upload:'

function valueOf (result) { return result && result.data !== undefined ? result.data : result }
function hex (buffer) { return Array.from(new Uint8Array(buffer)).map(value => value.toString(16).padStart(2, '0')).join('') }
function readBuffer (blob) {
  if (blob && typeof blob.arrayBuffer === 'function') {
    return blob.arrayBuffer().catch(() => readWithFileReader(blob))
  }
  return readWithFileReader(blob)
}
function readWithFileReader (blob) {
  return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(reader.error || new Error('文件读取失败')); reader.readAsArrayBuffer(blob) })
}
const SHA256_K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
]
function rotr (value, bits) { return (value >>> bits) | (value << (32 - bits)) }
class Sha256 {
  constructor () { this.h = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]; this.buffer = []; this.length = 0 }
  update (input) {
    const bytes = input instanceof Uint8Array ? input : new Uint8Array(input)
    this.length += bytes.length
    let offset = 0
    if (this.buffer.length) {
      while (this.buffer.length < 64 && offset < bytes.length) this.buffer.push(bytes[offset++])
      if (this.buffer.length === 64) { this.block(this.buffer); this.buffer = [] }
    }
    while (offset + 64 <= bytes.length) { this.block(bytes.subarray(offset, offset + 64)); offset += 64 }
    while (offset < bytes.length) this.buffer.push(bytes[offset++])
  }
  block (bytes) {
    const w = new Array(64).fill(0)
    for (let i = 0; i < 16; i++) w[i] = (bytes[i * 4] << 24) | (bytes[i * 4 + 1] << 16) | (bytes[i * 4 + 2] << 8) | bytes[i * 4 + 3]
    for (let i = 16; i < 64; i++) { const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3); const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10); w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0 }
    let [a, b, c, d, e, f, g, h] = this.h
    for (let i = 0; i < 64; i++) { const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25); const ch = (e & f) ^ (~e & g); const temp1 = (h + S1 + ch + SHA256_K[i] + w[i]) | 0; const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22); const maj = (a & b) ^ (a & c) ^ (b & c); const temp2 = (S0 + maj) | 0; h = g; g = f; f = e; e = (d + temp1) | 0; d = c; c = b; b = a; a = (temp1 + temp2) | 0 }
    this.h = this.h.map((value, i) => (value + [a, b, c, d, e, f, g, h][i]) | 0)
  }
  digest () {
    const bitLength = this.length * 8
    const padded = this.buffer.slice()
    padded.push(0x80)
    while (padded.length % 64 !== 56) padded.push(0)
    for (let i = 7; i >= 0; i--) padded.push(Math.floor(bitLength / Math.pow(2, i * 8)) & 255)
    for (let offset = 0; offset < padded.length; offset += 64) this.block(padded.slice(offset, offset + 64))
    const output = new Uint8Array(32)
    this.h.forEach((value, i) => { output[i * 4] = value >>> 24; output[i * 4 + 1] = value >>> 16; output[i * 4 + 2] = value >>> 8; output[i * 4 + 3] = value })
    return output
  }
}

export default {
  name: 'ImageUploadDialog',
  props: { visible: Boolean },
  data: () => ({ file: null, uploadId: '', receivedBytes: 0, progress: 0, uploading: false, hashing: false, hashComputed: false, retrying: false, retryAttempt: 0, form: { groupId: '', componentType: 'ANNOTATION', version: '', imageRepository: '', expectedSha256: '' } }),
  computed: {
    hashStatus () {
      if (this.retrying) return `网络波动，正在重试第 ${this.retryAttempt} 次`
      if (this.hashing) return this.file && this.file.size > 64 * 1024 * 1024 ? '正在分块计算 SHA-256...' : '正在计算 SHA-256...'
      if (this.hashComputed) return '已自动计算，可直接上传'
      return this.file ? '等待计算' : '请选择归档文件'
    }
  },
  methods: {
    async selectFile (event) {
      this.file = event.target.files[0] || null
      this.uploadId = ''
      this.receivedBytes = 0
      this.progress = 0
      this.form.expectedSha256 = ''
      this.hashComputed = false
      if (!this.file) return
      const stored = localStorage.getItem(this.resumeKey())
      if (stored) this.uploadId = stored
      await this.computeFileHash()
    },
    async computeFileHash () {
      if (!this.file) return
      this.hashing = true
      try {
        if (this.file.size <= 64 * 1024 * 1024 && window.crypto && window.crypto.subtle) {
          this.form.expectedSha256 = hex(await window.crypto.subtle.digest('SHA-256', await readBuffer(this.file)))
        } else {
          const sha = new Sha256()
          for (let offset = 0; offset < this.file.size; offset += CHUNK_SIZE) sha.update(await readBuffer(this.file.slice(offset, Math.min(offset + CHUNK_SIZE, this.file.size))))
          this.form.expectedSha256 = hex(sha.digest())
        }
        this.hashComputed = true
      } catch (error) {
        this.hashComputed = false
        this.$message.error(`无法自动计算 SHA-256：${error.message || '文件读取失败'}`)
      } finally { this.hashing = false }
    },
    async chunkHash (chunk) {
      if (window.crypto && window.crypto.subtle) return hex(await window.crypto.subtle.digest('SHA-256', await readBuffer(chunk)))
      const sha = new Sha256(); sha.update(await readBuffer(chunk)); return hex(sha.digest())
    },
    resumeKey () { return `${RESUME_KEY}${this.file ? `${this.file.name}:${this.file.size}:${this.file.lastModified}` : ''}` },
    async start () {
      if (!this.validate()) return
      this.uploading = true
      try {
        let status
        if (this.uploadId) {
          try { status = valueOf(await getImageUploadStatus(this.uploadId)) } catch (error) { this.uploadId = ''; localStorage.removeItem(this.resumeKey()) }
        }
        if (status && status.state === 'PENDING_REVIEW') { this.finishUploaded(); return }
        if (!this.uploadId) {
          status = valueOf(await createImageUpload({
            groupId: this.form.groupId,
            componentType: this.form.componentType,
            originalFilename: this.file.name,
            totalSize: this.file.size,
            chunkSize: CHUNK_SIZE,
            expectedSha256: this.form.expectedSha256,
            version: this.form.version,
            imageRepository: this.form.imageRepository,
            imageTag: this.form.version
          }))
          this.uploadId = status.uploadId
          localStorage.setItem(this.resumeKey(), this.uploadId)
        }
        this.receivedBytes = Number(status.receivedBytes || 0)
        const firstChunk = Math.floor(this.receivedBytes / CHUNK_SIZE)
        const totalChunks = Math.ceil(this.file.size / CHUNK_SIZE)
        for (let index = firstChunk; index < totalChunks; index++) {
          const start = index * CHUNK_SIZE
          const chunk = this.file.slice(start, Math.min(start + CHUNK_SIZE, this.file.size))
          const checksum = await this.chunkHash(chunk)
          let chunkResult
          try {
            chunkResult = await this.uploadChunkWithRetry(index, chunk, checksum, start)
          } catch (error) {
            const recovered = valueOf(await getImageUploadStatus(this.uploadId).catch(() => null))
            if (recovered && recovered.state === 'PENDING_REVIEW') { this.finishUploaded(); return }
            throw error
          }
          this.receivedBytes = Number(chunkResult.receivedBytes || start + chunk.size)
          this.progress = Math.min(99, Math.round(this.receivedBytes / this.file.size * 100))
        }
        await completeImageUpload(this.uploadId)
        this.finishUploaded()
      } finally { this.uploading = false }
    },
    validate () {
      const validFile = this.file && /\.(tar|tar\.gz|tgz)$/i.test(this.file.name)
      if (!this.form.groupId || !this.form.version || !this.form.imageRepository || !this.hashComputed || !/^[0-9a-f]{64}$/.test(this.form.expectedSha256) || !validFile) {
        this.$message.warning('请完整填写信息并选择归档文件，SHA-256 会自动生成')
        return false
      }
      return true
    },
    async uploadChunkWithRetry (index, chunk, checksum, start) {
      let lastError
      for (let attempt = 0; attempt <= MAX_CHUNK_RETRIES; attempt++) {
        this.retrying = attempt > 0
        this.retryAttempt = attempt
        try {
          const result = valueOf(await uploadImageChunk(this.uploadId, index, chunk, checksum, event => {
            const sent = event.total ? event.loaded : 0
            this.progress = Math.min(99, Math.round((start + sent) / this.file.size * 100))
          }))
          this.retrying = false
          this.retryAttempt = 0
          return result
        } catch (error) {
          lastError = error
          if (attempt === MAX_CHUNK_RETRIES) break
          await new Promise(resolve => window.setTimeout(resolve, 1000 * Math.pow(2, attempt)))
        }
      }
      this.retrying = false
      this.retryAttempt = 0
      throw lastError
    },
    finishUploaded () {
      this.progress = 100
      localStorage.removeItem(this.resumeKey())
      this.$message.success('镜像归档已上传，等待超级管理员审批')
      this.$emit('uploaded')
      this.close(true)
    },
    async discardResume () { if (this.uploadId) { await cancelImageUpload(this.uploadId); localStorage.removeItem(this.resumeKey()) } this.uploadId = ''; this.receivedBytes = 0; this.progress = 0; this.retrying = false; this.retryAttempt = 0 },
    close (force = false) { if (this.uploading && !force) return; this.$emit('update:visible', false) },
    formatBytes (bytes) { if (!Number.isFinite(bytes)) return '--'; if (bytes < 1024) return `${bytes} B`; const units = ['KiB', 'MiB', 'GiB', 'TiB']; let value = bytes / 1024; let unit = units[0]; for (let i = 1; value >= 1024 && i < units.length; i++) { value /= 1024; unit = units[i] } return `${value.toFixed(value >= 10 ? 1 : 2)} ${unit}` }
  }
}
</script>

<style scoped>
.upload-form { margin-top: 20px; }
.native-file { display: none; }
.file-name { margin-left: 12px; color: #687986; }
.el-progress { width: calc(100% - 90px); display: inline-block; vertical-align: middle; }
.el-form-item small { margin-left: 10px; color: #7a8995; }
.hash-status { display: block; margin: 5px 0 0 !important; }
.hash-status.is-ready { color: #35a87c; }
</style>
