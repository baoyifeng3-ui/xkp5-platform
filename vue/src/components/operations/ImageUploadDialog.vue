<template>
  <el-dialog title="上传镜像文件" :visible="visible" width="600px" :close-on-click-modal="!uploading" @close="close">
    <el-form class="upload-form" label-width="100px">
      <el-form-item label="归档文件">
        <input ref="file" class="native-file" type="file" accept=".tar" :disabled="uploading" @change="selectFile">
        <el-button icon="el-icon-folder-opened" :disabled="uploading" @click="$refs.file.click()">选择文件</el-button>
        <span class="file-name">{{ file ? file.name + ' · ' + formatBytes(file.size) : '尚未选择' }}</span>
      </el-form-item>
      <el-form-item v-if="uploading" label="上传进度"><el-progress :percentage="progress" /><small>{{ formatBytes(sent) }} / {{ formatBytes(file.size) }}</small></el-form-item>
    </el-form>
    <span slot="footer"><el-button :disabled="uploading" @click="close">取消</el-button><el-button type="primary" :loading="uploading" :disabled="!file" @click="start(false)">上传并启用</el-button></span>
  </el-dialog>
</template>

<script>
import { uploadImageFile } from '@/services/imageRegistry'
const valueOf = result => result && result.data !== undefined ? result.data : result

export default {
  name: 'ImageUploadDialog',
  props: { visible: Boolean },
  data: () => ({ file: null, uploading: false, progress: 0, sent: 0 }),
  watch: { visible (shown) { if (shown) { this.file = null; this.progress = 0; this.sent = 0 } } },
  methods: {
    selectFile (event) {
      const file = event.target.files[0] || null
      if (file && !/\.tar$/.test(file.name)) { this.$message.error('请选择 .tar 镜像归档'); event.target.value = ''; return }
      this.file = file
    },
    async start (overwrite) {
      if (!this.file) { this.$message.warning('请选择 .tar 文件'); return }
      this.uploading = true
      try {
        const data = new FormData(); data.append('overwrite', overwrite); data.append('file', this.file)
        let upload
        try {
          upload = valueOf(await uploadImageFile(data, event => { this.sent = event.loaded || 0; this.progress = Math.min(99, Math.round((event.loaded || 0) / this.file.size * 100)) }))
        } catch (error) {
          const reason = error.response && error.response.data && error.response.data.data && error.response.data.data.reasonCode
          if (reason === 'FILE_EXISTS') {
            await this.$confirm('已存在同名文件，确认覆盖原文件？', '确认覆盖', { type: 'warning', confirmButtonText: '覆盖文件' })
            this.uploading = false
            return this.start(true)
          }
          throw error
        }
        this.progress = 100; this.$message.success('镜像上传成功，已启用'); this.$emit('uploaded', upload); this.close(true)
      } catch (error) {
        if (error === 'cancel' || error === 'close') return
        const response = error && error.response && error.response.data
        const payload = response && response.data
        const message = payload && (payload.msg || payload.message)
        this.$message.error(message || (error && error.message) || '镜像上传失败，请重试')
        throw error
      } finally { this.uploading = false }
    },
    close (force) { if (!this.uploading || force) this.$emit('update:visible', false) },
    formatBytes (bytes) { const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']; let value = Number(bytes || 0); let i = 0; while (value >= 1024 && i < units.length - 1) { value /= 1024; i++ } return value.toFixed(i ? 1 : 0) + ' ' + units[i] }
  }
}
</script>

<style scoped>
.upload-form { margin-top: 20px; }
.native-file { display: none; }
.file-name { margin-left: 12px; color: #687986; }
.el-progress { display: inline-block; width: calc(100% - 90px); vertical-align: middle; }
small { margin-left: 8px; color: #687986; }
</style>
