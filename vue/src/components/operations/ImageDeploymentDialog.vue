<template>
  <el-dialog title="推送镜像版本" :visible="visible" width="570px" @close="$emit('update:visible', false)">
    <el-form label-width="108px">
      <el-form-item label="目标组件"><strong>{{ componentLabel }}</strong><div class="digest">{{ release && release.registryDigest }}</div></el-form-item>
      <el-form-item label="处理服务器"><el-select v-model="form.agentId" filterable placeholder="选择 Agent" style="width:100%"><el-option v-for="agent in agents" :key="agent.agentId" :label="agent.displayName || agent.hostname || agent.agentId" :value="agent.agentId" :disabled="!agent.online || !agent.enabled" /></el-select></el-form-item>
      <el-form-item label="更新策略"><el-radio-group v-model="form.updatePolicy"><el-radio label="UPDATE_CONTAINERS">更新镜像并更新容器</el-radio><el-radio label="IMAGE_ONLY">仅更新镜像</el-radio></el-radio-group></el-form-item>
      <el-alert v-if="form.updatePolicy === 'UPDATE_CONTAINERS'" title="将更新运行中容器" description="Agent 拉取并校验镜像后会重建受影响容器，仅在健康检查通过后完成；请先确认当前任务可中断。" type="warning" :closable="false" show-icon />
      <el-alert v-else title="不会改动当前容器" description="新镜像仅缓存到处理服务器，后续新建或还原环境时生效。" type="info" :closable="false" show-icon />
    </el-form>
    <span slot="footer"><el-button @click="$emit('update:visible', false)">取消</el-button><el-button type="primary" :loading="submitting" :disabled="!form.agentId" @click="submit">确认推送</el-button></span>
  </el-dialog>
</template>

<script>
import { listProcessingAgents } from '@/api/ProcessingAgents'
import { deployImageRelease } from '@/services/imageRegistry'

function valueOf (result) { return result && result.data !== undefined ? result.data : result }
export default {
  name: 'ImageDeploymentDialog',
  props: { visible: Boolean, release: Object },
  data: () => ({ agents: [], submitting: false, form: { agentId: '', updatePolicy: 'UPDATE_CONTAINERS' } }),
  computed: { componentLabel () { return this.release && this.release.componentType === 'ANNOTATION' ? '图像标注' : '代码编辑' } },
  watch: { visible (shown) { if (shown) this.loadAgents() } },
  methods: {
    async loadAgents () { const result = valueOf(await listProcessingAgents()); this.agents = Array.isArray(result) ? result : (result.records || []) },
    async submit () {
      if (!this.release || !this.form.agentId) return
      this.submitting = true
      try {
        const result = valueOf(await deployImageRelease(this.release.releaseId, {
          agentId: this.form.agentId,
          componentType: this.release.componentType,
          updatePolicy: this.form.updatePolicy,
          idempotencyKey: `ui-${Date.now()}-${Math.random().toString(16).slice(2)}`,
          confirm: true
        }))
        this.$message.success('推送任务已提交')
        this.$emit('deployed', result)
        this.$emit('update:visible', false)
      } finally { this.submitting = false }
    }
  }
}
</script>

<style scoped>
.digest { margin-top: 4px; overflow-wrap: anywhere; color: #687986; font: 12px/1.5 monospace; }
.el-alert { margin: 8px 0 4px; }
</style>
