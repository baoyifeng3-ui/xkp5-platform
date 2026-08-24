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
const RESUME_KEY = 'xkp:image-upload:'

function valueOf (result) { return result && result.data !== undefined ? result.data : result }
function hex (buffer) { return Array.from(new Uint8Array(buffer)).map(value => value.toString(16).padStart(2, '0')).join('') }

export default {
  name: 'ImageUploadDialog',
  props: { visible: Boolean },
  data: () => ({ file: null, uploadId: '', receivedBytes: 0, progress: 0, uploading: false, hashing: false, hashComputed: false, form: { groupId: '', componentType: 'ANNOTATION', version: '', imageRepository: '', expectedSha256: '' } }),
  computed: {
    hashStatus () {
      if (this.hashing) return '正在计算 SHA-256...'
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
        if (!window.crypto || !window.crypto.subtle) throw new Error('Web Crypto unavailable')
        const digest = await window.crypto.subtle.digest('SHA-256', await this.file.arrayBuffer())
        this.form.expectedSha256 = hex(digest)
        this.hashComputed = true
      } catch (error) {
        this.hashComputed = false
        this.$message.error('无法自动计算 SHA-256，请使用 HTTPS 或 localhost 访问平台')
      } finally { this.hashing = false }
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
          const checksum = hex(await crypto.subtle.digest('SHA-256', await chunk.arrayBuffer()))
          const chunkResult = valueOf(await uploadImageChunk(this.uploadId, index, chunk, checksum, event => {
            const sent = event.total ? event.loaded : 0
            this.progress = Math.min(99, Math.round((start + sent) / this.file.size * 100))
          }))
          this.receivedBytes = Number(chunkResult.receivedBytes || start + chunk.size)
          this.progress = Math.min(99, Math.round(this.receivedBytes / this.file.size * 100))
        }
        await completeImageUpload(this.uploadId)
        this.progress = 100
        localStorage.removeItem(this.resumeKey())
        this.$message.success('镜像归档已上传，等待超级管理员审批')
        this.$emit('uploaded')
        this.close(true)
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
    async discardResume () { if (this.uploadId) { await cancelImageUpload(this.uploadId); localStorage.removeItem(this.resumeKey()) } this.uploadId = ''; this.receivedBytes = 0; this.progress = 0 },
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
