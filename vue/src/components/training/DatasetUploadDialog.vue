<template><el-dialog title="上传数据集到实训环境" :visible.sync="visible" width="620px"><p>文件将临时中转到 <code>/home/student/data/datasets</code>，成功后平台立即删除临时副本。</p><input type="file" multiple @change="choose"><div v-for="item in queue" :key="item.id" class="row"><span>{{item.file.name}}</span><el-progress :percentage="item.progress" :status="item.status"/></div><span slot="footer"><el-button @click="visible=false">关闭</el-button><el-button type="primary" :loading="uploading" @click="upload">开始上传</el-button></span></el-dialog></template>
<script>
import { uploadTrainingDataset } from '@/api/TrainingTransfers'
export default {
  data: () => ({ visible: false, environment: null, queue: [], uploading: false }),
  methods: {
    open(environment) {
      if (this.uploading) return
      this.environment = environment
      this.queue = []
      this.visible = true
    },
    choose(e) {
      if (this.uploading) return
      this.queue = Array.from(e.target.files || []).map((file, i) => ({ id: `${file.name}:${i}`, file, progress: 0, status: undefined }))
    },
    async upload() {
      if (this.uploading || !this.environment || !this.queue.length) return
      this.uploading = true
      const environmentId = this.environment.environmentId
      let failed = 0
      try {
        for (const item of this.queue) {
          if (item.status === 'success') continue
          try {
            await uploadTrainingDataset(environmentId, item.file, e => {
              if (e.total) item.progress = Math.min(99, Math.round(e.loaded * 100 / e.total))
            })
            item.progress = 100
            item.status = 'success'
          } catch (error) {
            item.status = 'exception'
            failed += 1
          }
        }
        if (failed) this.$message.error(`${failed} 个文件提交失败，请重试`)
        else this.$message.success('数据集传输任务已提交')
      } finally { this.uploading = false }
    }
  }
}
</script><style scoped>.row{display:grid;grid-template-columns:minmax(0,1fr) 220px;gap:12px;padding:9px 0}</style>
