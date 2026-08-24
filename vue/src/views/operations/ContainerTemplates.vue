<template>
  <section class="module-page module-composed-page">
    <header class="module-heading module-toolbar action-heading"><div><h1>容器模板</h1><p>维护图像标注与代码编辑容器的版本化配置。</p></div><el-button type="primary" icon="el-icon-plus" @click="openCreate">发布模板</el-button></header>
    <div class="module-table-wrap"><el-table v-loading="loading" :data="templates" empty-text="暂无容器模板">
      <el-table-column prop="templateName" label="名称" min-width="150" />
      <el-table-column label="组件" width="110"><template slot-scope="scope">{{ scope.row.componentType === 'ANNOTATION' ? '图像标注' : '代码编辑' }}</template></el-table-column>
      <el-table-column prop="imageReference" label="镜像" min-width="190" show-overflow-tooltip />
      <el-table-column prop="templateVersion" label="版本" width="80" />
      <el-table-column label="GPU 算力" width="100"><template slot-scope="scope">{{ scope.row.gpuEnabled ? `${scope.row.gpuComputePercent}%` : '-' }}</template></el-table-column>
      <el-table-column label="状态" width="90"><template slot-scope="scope"><el-tag size="small" :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '可用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="245" align="right"><template slot-scope="scope"><el-button size="small" @click="edit(scope.row)">发布新版本</el-button><el-button v-if="scope.row.enabled" size="small" type="danger" @click="disable(scope.row)">停用</el-button><el-button size="small" type="danger" plain @click="remove(scope.row)">删除</el-button></template></el-table-column>
    </el-table></div>

    <el-dialog title="发布容器模板" :visible.sync="dialog" width="620px">
      <el-form label-width="110px">
        <el-form-item label="模板名称"><el-input v-model.trim="form.templateName" maxlength="80" /></el-form-item>
        <el-form-item label="组件类型"><el-radio-group v-model="form.componentType" @change="applyBoundary"><el-radio-button label="ANNOTATION">图像标注</el-radio-button><el-radio-button label="EDITOR">代码编辑</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="镜像"><el-input v-model.trim="form.imageReference" placeholder="例如 zy-anno:latest" /></el-form-item>
        <el-form-item label="容器端口"><el-input v-model.trim="portsText" placeholder="标注 8081；代码工具 9091,8881,5000" /></el-form-item>
        <el-form-item label="CPU 限制"><el-input-number v-model="form.cpuLimitMillis" :min="100" :max="128000" :step="100" /><span class="unit">毫核</span></el-form-item>
        <el-form-item label="内存限制"><el-input-number v-model="memoryGiB" :min="1" :max="512" /><span class="unit">GiB</span></el-form-item>
        <el-form-item v-if="form.componentType === 'EDITOR'" label="启用 GPU"><el-switch v-model="form.gpuEnabled" /></el-form-item>
        <el-form-item v-if="form.gpuEnabled" label="GPU 算力"><el-slider v-model="form.gpuComputePercent" :min="1" :max="100" show-input /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="publishing" @click="publish">发布</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import { listContainerTemplates, publishContainerTemplate, disableContainerTemplate, deleteContainerTemplate } from '@/api/ContainerTemplates'

const GiB = 1024 * 1024 * 1024
export default {
  data: () => ({ loading: false, publishing: false, dialog: false, templates: [], portsText: '8080', memoryGiB: 2, form: {} }),
  created () { this.load() },
  methods: {
    async load () { this.loading = true; try { const result = await listContainerTemplates(); this.templates = result.data || [] } finally { this.loading = false } },
    openCreate () { this.form = { templateName: '', componentType: 'ANNOTATION', imageReference: '', runtimeName: 'sysbox-runc', restartPolicy: 'always', mountTarget: '/root/data', cpuLimitMillis: 2000, gpuEnabled: false, gpuComputePercent: 50, privileged: false, hostNetwork: false }; this.portsText = '8081'; this.memoryGiB = 2; this.dialog = true },
    edit (row) { this.form = Object.assign({}, row, { templateVersion: null }); this.portsText = (row.ports || []).map(p => p.containerPort).join(',') || '8080'; this.memoryGiB = Math.max(1, Math.round((row.memoryLimitBytes || GiB * 2) / GiB)); this.dialog = true },
    applyBoundary (type) { const editor = type === 'EDITOR'; this.form.runtimeName = editor ? 'nvidia' : 'sysbox-runc'; this.form.mountTarget = editor ? '/home/student/data' : '/root/data'; this.form.gpuEnabled = editor; this.portsText = editor ? '9091,8881,5000' : '8081'; this.memoryGiB = editor ? 6 : 2 },
    async publish () { const ports = this.portsText.split(',').map(value => Number(value.trim())).filter(Boolean).map(containerPort => ({ containerPort, protocol: 'tcp' })); const payload = { ...this.form, ports, memoryLimitBytes: this.memoryGiB * GiB }; if (!payload.gpuEnabled) { payload.gpuComputePercent = null; payload.gpuMemoryLimitBytes = null } this.publishing = true; try { await publishContainerTemplate(payload); this.$message.success('模板版本已发布'); this.dialog = false; await this.load() } finally { this.publishing = false } },
    async disable (row) { await this.$confirm('停用后不能再分配给新环境，已有环境仍使用固定版本。', '确认停用', { type: 'warning' }); await disableContainerTemplate(row.templateId, row.templateVersion); await this.load() },
    async remove (row) { await this.$confirm('仅删除未被任何训练或比赛环境引用的模板版本，确认继续？', '确认删除', { type: 'warning' }); try { await deleteContainerTemplate(row.templateId, row.templateVersion); this.$message.success('模板版本已删除'); await this.load() } catch (error) {} }
  }
}
</script>

<style scoped>
.action-heading { display: flex; align-items: center; justify-content: space-between; }
.unit { margin-left: 10px; color: #6b7c8f; }
</style>
