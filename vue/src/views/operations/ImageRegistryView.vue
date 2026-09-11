<template>
  <section class="module-page registry-page">
    <header class="module-heading heading">
      <div><h1>镜像仓库</h1><p>上传 TAR 镜像归档，启用后直接下发到处理服务器。</p></div>
      <div><el-button icon="el-icon-refresh" :loading="loading" @click="loadAll">刷新</el-button><el-button v-if="canWrite" type="primary" icon="el-icon-upload2" @click="uploadVisible=true">上传镜像</el-button></div>
    </header>
    <el-tabs v-model="tab">
      <el-tab-pane label="镜像文件" name="files">
        <el-table :data="files" v-loading="loading" empty-text="暂无已启用镜像文件">
          <el-table-column prop="originalFilename" label="镜像文件" min-width="190" />
          <el-table-column label="大小" width="110"><template slot-scope="s">{{ formatBytes(s.row.sizeBytes) }}</template></el-table-column>
          <el-table-column label="状态" width="100"><template><el-tag size="small" type="success">已启用</el-tag></template></el-table-column>
          <el-table-column label="更新时间" min-width="165"><template slot-scope="s">{{ formatTime(s.row.updatedAt) }}</template></el-table-column>
          <el-table-column v-if="canWrite" label="操作" width="150" align="right"><template slot-scope="s"><el-button type="text" icon="el-icon-position" @click="openDeploy(s.row)">推送</el-button><el-button type="text" class="danger" icon="el-icon-delete" @click="removeFile(s.row)">删除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="推送进度" name="deployments">
        <el-table :data="deployments" v-loading="loading" empty-text="暂无推送任务">
          <el-table-column prop="requestedAt" label="发起时间" min-width="165" />
          <el-table-column prop="agentId" label="处理服务器" min-width="210" />
          <el-table-column label="镜像" min-width="210"><template slot-scope="s">{{ deploymentLabel(s.row) }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template slot-scope="s"><el-tag size="small" :type="stateTag(s.row.state)">{{ stateLabel(s.row.state) }}</el-tag></template></el-table-column>
          <el-table-column label="下发进度" min-width="270"><template slot-scope="s"><div class="deployment-progress"><el-progress :percentage="deploymentPercent(s.row)" :status="s.row.state==='FAILED'?'exception':s.row.state==='SUCCEEDED'?'success':undefined" :stroke-width="8" /><small>{{ deploymentProgressText(s.row) }}</small></div></template></el-table-column>
          <el-table-column label="结果" min-width="260"><template slot-scope="s"><span :class="{ failure:s.row.failureMessage }">{{ s.row.failureMessage || '-' }}</span></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="服务器清单" name="servers">
        <el-table :data="agents" v-loading="loading" row-key="agentId" empty-text="暂无处理服务器">
          <el-table-column type="expand"><template slot-scope="s"><div class="inventory"><h3>镜像（{{ images(s.row).length }}）</h3><el-table :data="images(s.row)" size="mini" empty-text="暂无镜像"><el-table-column label="镜像文件" min-width="190"><template slot-scope="i">{{ imageLabel(i.row) }}<div class="image-tags">{{ (i.row.repoTags || []).join(" · ") }}</div></template></el-table-column><el-table-column prop="id" label="镜像 ID" min-width="190" show-overflow-tooltip /><el-table-column label="大小" width="110"><template slot-scope="i">{{ formatBytes(i.row.sizeBytes) }}</template></el-table-column><el-table-column v-if="canWrite" label="操作"><template slot-scope="i"><el-button type="text" class="danger" @click="removeDockerImage(s.row,i.row)">删除</el-button></template></el-table-column></el-table><h3>容器（{{ containers(s.row).length }}）</h3><el-table :data="containers(s.row)" size="mini" empty-text="暂无容器"><el-table-column prop="name" label="容器" /><el-table-column prop="image" label="镜像" /><el-table-column prop="state" label="运行状态" width="100" /><el-table-column prop="status" label="状态详情" /><el-table-column v-if="canWrite" label="操作"><template slot-scope="c"><el-button type="text" class="danger" @click="removeDockerContainer(s.row,c.row)">删除</el-button></template></el-table-column></el-table></div></template></el-table-column>
          <el-table-column prop="displayName" label="服务器" min-width="150" />
          <el-table-column prop="primaryIp" label="IP" min-width="130" />
          <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag size="small" :type="s.row.online?'success':'info'">{{ s.row.online?'在线':'离线' }}</el-tag></template></el-table-column>
          <el-table-column label="镜像" width="90"><template slot-scope="s">{{ images(s.row).length }}</template></el-table-column>
          <el-table-column label="容器" width="90"><template slot-scope="s">{{ containers(s.row).length }}</template></el-table-column>
          <el-table-column prop="agentVersion" label="Agent 版本" width="120" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <ImageUploadDialog :visible.sync="uploadVisible" @uploaded="uploaded" />
    <ImageDeploymentDialog :visible.sync="deploymentVisible" :file="selectedFile" @deployed="deployed" />
  </section>
</template>

<script>
import ImageUploadDialog from '@/components/operations/ImageUploadDialog.vue'
import ImageDeploymentDialog from '@/components/operations/ImageDeploymentDialog.vue'
import { listImageFiles, listImageDeployments, deleteImageFile, getImageUploadStatus, deleteDockerImage, deleteDockerContainer, getDockerCommand } from '@/services/imageRegistry'
import { listProcessingAgents } from '@/api/ProcessingAgents'
import { getRole } from '@/utils/auth'
import { SUPER_ADMIN } from '@/navigation/roleNavigation'
const valueOf = result => result && result.data !== undefined ? result.data : result
const records = result => { const value = valueOf(result); return Array.isArray(value) ? value : ((value && value.records) || []) }
export default {
  name: 'ImageRegistryView', components: { ImageUploadDialog, ImageDeploymentDialog },
  data: () => ({ tab: 'files', files: [], deployments: [], agents: [], loading: false, uploadVisible: false, deploymentVisible: false, selectedFile: null, poller: null, completionPollers: [] }),
  computed: { canWrite () { return getRole() === SUPER_ADMIN } },
  created () { this.loadAll(); this.poller = setInterval(() => this.loadDeployments().catch(() => {}), 5000) },
  beforeDestroy () { clearInterval(this.poller); this.completionPollers.forEach(clearInterval) },
  methods: {
    async loadAll () { this.loading = true; try { const [files, deployments, agents] = await Promise.all([listImageFiles({ limit: 500 }), listImageDeployments({ limit: 200 }), listProcessingAgents()]); this.files = records(files); this.deployments = records(deployments); this.agents = records(agents) } finally { this.loading = false } },
    async loadDeployments () { if (!this.deployments.some(row => ['PENDING', 'PULLED', 'RUNNING'].includes(row.state))) return; this.deployments = records(await listImageDeployments({ limit: 200 })) },
    openDeploy (file) { this.selectedFile = file; this.deploymentVisible = true },
    async uploaded (upload) {
      this.uploadVisible = false; await this.loadAll()
      if (!upload || !upload.uploadId) return
      let attempts = 0
      const timer = setInterval(async () => {
        attempts += 1
        try {
          const status = valueOf(await getImageUploadStatus(upload.uploadId))
          if (status && status.state === 'PENDING_REVIEW') { clearInterval(timer); this.completionPollers = this.completionPollers.filter(item => item !== timer); await this.loadAll(); this.$message.success('镜像归档校验完成，已启用') }
          else if (status && status.failureCode) { clearInterval(timer); this.completionPollers = this.completionPollers.filter(item => item !== timer); this.$message.error(status.failureMessage || '镜像归档合并校验失败') }
        } catch (error) { if (attempts >= 60) { clearInterval(timer); this.completionPollers = this.completionPollers.filter(item => item !== timer); this.$message.error('镜像归档后台处理超时，请查看上传状态') } }
        if (attempts >= 60) { clearInterval(timer); this.completionPollers = this.completionPollers.filter(item => item !== timer) }
      }, 5000)
      this.completionPollers.push(timer)
    },
    deployed (items) { this.deployments.unshift(...items); this.tab = 'deployments' },
    async removeFile (file) { await this.$confirm('确认删除这个镜像归档文件？', '删除镜像', { type: 'warning' }); await deleteImageFile(file.fileId); await this.loadAll() },
    async waitDockerDelete (agentId, response) {
      const command = valueOf(response)
      if (!command || !command.commandId) throw new Error('服务器未返回删除任务')
      this.$message.info('删除任务已提交，正在等待服务器执行')
      for (let attempt = 0; attempt < 90; attempt++) {
        const state = valueOf(await getDockerCommand(agentId, command.commandId))
        if (state.state === 'SUCCEEDED') { this.$message.success('删除完成'); await this.loadAll(); return }
        if (['FAILED', 'CANCELLED'].includes(state.state)) throw new Error(state.resultMessage || '删除失败')
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      throw new Error('等待删除结果超时，任务可能仍在执行，请稍后刷新服务器清单')
    },
    async removeDockerImage (agent, image) {
      try {
        await this.$confirm('确认删除服务器镜像？有容器（包括已停止的容器）或模板引用时不能删除，请先删除相关容器及模板。', '删除服务器镜像', { type: 'warning' })
        const target = image.repository && image.repository !== '<none>' && image.tag && image.tag !== '<none>' ? image.repository + ':' + image.tag : image.id
        await this.waitDockerDelete(agent.agentId, await deleteDockerImage(agent.agentId, target, image.id))
      } catch (error) { if (error !== 'cancel' && error !== 'close') this.$message.error(error.message || '删除失败') }
    },
    async removeDockerContainer (agent, container) {
      try {
        await this.$confirm('删除容器将同步删除关联实训环境及其全部组件容器，挂载的数据保留。确认继续？', '删除容器', { type: 'warning' })
        await this.waitDockerDelete(agent.agentId, await deleteDockerContainer(agent.agentId, container.name, container.id))
      } catch (error) { if (error !== 'cancel' && error !== 'close') this.$message.error(error.message || '删除失败') }
    },
    deploymentLabel (row) { const file = this.files.find(item => item.fileId === row.fileId); return file ? file.originalFilename : (row.targetImage || row.targetDigest) },
    imageLabel (image) { const file = this.files.find(item => (image.repoTags || []).includes(item.imageRepository + ':' + item.imageTag) || item.imageRepository === image.repository && item.imageTag === image.tag); return file ? file.originalFilename : (image.repository && image.repository !== '<none>' ? image.repository + ':' + image.tag : image.id) },
    images (agent) {
      const groups = new Map()
      for (const image of (agent.latestMetrics && agent.latestMetrics.images) || []) {
        const reference = image.repository && image.repository !== '<none>' ? image.repository + ':' + image.tag : image.id
        const key = image.id || reference
        const row = groups.get(key) || { ...image, repoTags: [] }
        row.repoTags = [...new Set([...row.repoTags, ...(image.repoTags || []), reference].filter(Boolean))]
        groups.set(key, row)
      }
      return [...groups.values()]
    },
    containers (agent) { return (agent.latestMetrics && agent.latestMetrics.containers) || [] },
    stateLabel (state) { return ({ PENDING:'等待 Agent', PULLED:'已接收', RUNNING:'下发中', SUCCEEDED:'完成', FAILED:'失败' })[state] || state },
    stateTag (state) { return ({ SUCCEEDED:'success', FAILED:'danger', RUNNING:'warning' })[state] || 'info' },
    deploymentPercent (row) { if (row.state === 'SUCCEEDED') return 100; const value = Number(row.progressPercent || 0); return Math.max(0, Math.min(row.state === 'RUNNING' ? 99 : 100, Math.round(value))) },
    deploymentProgressText (row) { if (row.state === 'PENDING') return '等待目标服务器接收'; if (row.progressStage === 'IMPORTING') return 'Docker 正在加载镜像'; if (Number(row.totalBytes) > 0) return this.formatBytes(row.transferredBytes) + ' / ' + this.formatBytes(row.totalBytes); return row.state === 'SUCCEEDED' ? '下发完成' : row.state === 'FAILED' ? '下发失败' : '准备下发' },
    formatBytes (bytes) { const units=['B','KiB','MiB','GiB','TiB']; let value=Number(bytes||0),i=0; while(value>=1024&&i<units.length-1){value/=1024;i++} return value.toFixed(i?1:0)+' '+units[i] },
    formatTime (value) { return value ? String(value).replace('T',' ').slice(0,19) : '-' }
  }
}
</script>

<style scoped>
.image-tags { color:#687986; font-size:12px; overflow-wrap:anywhere; }
.heading { display:flex; align-items:center; justify-content:space-between; gap:16px; }.heading p { margin:4px 0 0; color:#687986; }.danger,.failure { color:#c75454; }.deployment-progress { width:100%; min-width:230px; }.deployment-progress small { display:block; margin-top:4px; color:#687986; }.inventory { padding:8px 20px 18px; }.inventory h3 { margin:14px 0 8px; font-size:14px; } code { font:12px/1.5 monospace; overflow-wrap:anywhere; }
@media(max-width:760px){.heading{align-items:flex-start;flex-direction:column}}
</style>
