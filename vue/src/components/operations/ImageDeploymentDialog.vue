<template>
  <el-dialog title="下发镜像" :visible="visible" width="570px" @close="$emit('update:visible', false)">
    <el-form label-width="96px">
      <el-form-item label="镜像文件"><strong>{{ file && file.originalFilename }}</strong></el-form-item>
      <el-form-item label="目标服务器">
        <el-checkbox v-model="selectAll" @change="toggleAll">选择全部在线服务器（{{ onlineAgents.length }} 台）</el-checkbox>
        <el-select v-model="agentIds" multiple filterable collapse-tags placeholder="选择服务器" style="width:100%;margin-top:8px">
          <el-option v-for="agent in agents" :key="agent.agentId" :label="agent.displayName || agent.hostname" :value="agent.agentId" :disabled="!agent.online || !agent.enabled" />
        </el-select>
      </el-form-item>
      <el-alert v-if="duplicateAgents.length" :title="duplicateAgents.length + ' 台服务器存在同名镜像'" description="提交时会要求确认覆盖；容器不会自动重建。" type="warning" :closable="false" show-icon />
    </el-form>
    <span slot="footer"><el-button @click="$emit('update:visible', false)">取消</el-button><el-button type="primary" :loading="submitting" :disabled="!agentIds.length" @click="submit">下发到 {{ agentIds.length }} 台</el-button></span>
  </el-dialog>
</template>

<script>
import { listProcessingAgents } from '@/api/ProcessingAgents'
import { deployImageFile } from '@/services/imageRegistry'
const valueOf = result => result && result.data !== undefined ? result.data : result
export default {
  name: 'ImageDeploymentDialog',
  props: { visible: Boolean, file: Object },
  data: () => ({ agents: [], agentIds: [], selectAll: false, submitting: false }),
  computed: {
    imageName () { return this.file ? this.file.imageRepository + ':' + this.file.imageTag : '' },
    onlineAgents () { return this.agents.filter(agent => agent.online && agent.enabled !== false) },
    duplicateAgents () { return this.agents.filter(agent => this.agentIds.includes(agent.agentId) && ((agent.latestMetrics && agent.latestMetrics.images) || []).some(image => image.repository + ':' + image.tag === this.imageName)) }
  },
  watch: { visible (shown) { if (shown) this.load() } },
  methods: {
    async load () { const result = valueOf(await listProcessingAgents()); this.agents = Array.isArray(result) ? result : (result.records || []); this.agentIds = []; this.selectAll = false },
    toggleAll (checked) { this.agentIds = checked ? this.onlineAgents.map(agent => agent.agentId) : [] },
    async submit () {
      if (!this.file || !this.agentIds.length) return
      const duplicateIds = new Set(this.duplicateAgents.map(agent => agent.agentId))
      if (duplicateIds.size) await this.$confirm('目标服务器中存在同名镜像，是否覆盖并继续下发？', '覆盖同名镜像', { type: 'warning', confirmButtonText: '确认覆盖' })
      this.submitting = true
      try {
        const batch = Date.now().toString(36); const deployments = []
        for (let i = 0; i < this.agentIds.length; i++) {
          const agentId = this.agentIds[i]
          const result = await deployImageFile(this.file.fileId, { agentId, overwrite: duplicateIds.has(agentId), idempotencyKey: batch + '-' + i })
          deployments.push(valueOf(result))
        }
        this.$message.success('已提交 ' + deployments.length + ' 个下发任务'); this.$emit('deployed', deployments); this.$emit('update:visible', false)
      } finally { this.submitting = false }
    }
  }
}
</script>

<style scoped>.muted { margin-top:4px; color:#687986; font-size:12px; }.el-alert { margin-top:8px; }</style>
