<template>
  <section class="module-page module-composed-page">
    <header class="module-heading module-toolbar action-heading"><div><h1>容器模板</h1><p>维护图像标注与代码编辑容器的版本化配置。</p></div><el-button type="primary" icon="el-icon-plus" @click="openCreate">发布模板</el-button></header>
    <div class="module-table-wrap"><el-table v-loading="loading" :data="templates" empty-text="暂无容器模板">
      <el-table-column prop="templateName" label="名称" min-width="150" />
      <el-table-column label="组件" width="110"><template slot-scope="scope">{{ scope.row.componentType === 'ANNOTATION' ? '图像标注' : '代码编辑' }}</template></el-table-column>
      <el-table-column prop="imageReference" label="镜像" min-width="190" show-overflow-tooltip />
      <el-table-column prop="templateVersion" label="版本" width="80" />
      <el-table-column label="GPU / MPS" width="170"><template slot-scope="scope">{{ scope.row.gpuEnabled ? `${scope.row.gpuComputePercent || '-'}%${scope.row.mpsEnabled ? '（MPS）' : ''}${scope.row.gpuMemoryLimitBytes ? ` / ${Math.round(scope.row.gpuMemoryLimitBytes / GiB)} GiB` : ''}` : '-' }}</template></el-table-column>
      <el-table-column label="状态" width="90"><template slot-scope="scope"><el-tag size="small" :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '可用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="245" align="right"><template slot-scope="scope"><el-button size="small" @click="edit(scope.row)">发布新版本</el-button><el-button v-if="scope.row.enabled" size="small" type="danger" @click="disable(scope.row)">停用</el-button><el-button size="small" type="danger" plain @click="remove(scope.row)">删除</el-button></template></el-table-column>
    </el-table></div>

    <el-dialog title="发布容器模板" :visible.sync="dialog" width="620px">
      <el-form label-width="110px">
        <el-form-item label="模板名称"><el-input v-model.trim="form.templateName" maxlength="80" /></el-form-item>
        <el-form-item label="组件类型"><el-radio-group v-model="form.componentType" @change="applyBoundary"><el-radio-button label="ANNOTATION">图像标注</el-radio-button><el-radio-button label="EDITOR">代码编辑</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="已发布镜像"><el-select v-model="selectedRelease" value-key="releaseId" filterable placeholder="选择已发布镜像版本" style="width:100%"><el-option v-for="item in compatibleReleases" :key="item.releaseId" :label="releaseLabel(item)" :value="item" :disabled="item.state !== 'PUBLISHED' || item.componentType !== form.componentType" /></el-select><small class="release-digest">{{ selectedRelease ? selectedRelease.registryDigest : '全部已发布镜像均会显示，组件不匹配时不可选择' }}</small></el-form-item>
        <el-form-item label="端口分配"><span class="unlimited">创建实训环境时选择主机端口</span></el-form-item>
        <el-form-item label="CPU 限制"><el-checkbox v-model="form.cpuLimitEnabled">启用限制</el-checkbox><el-input-number v-if="form.cpuLimitEnabled" v-model="form.cpuLimitMillis" :min="100" :max="128000" :step="100" /><span v-if="form.cpuLimitEnabled" class="unit">毫核</span><span v-else class="unlimited">不限制</span></el-form-item>
        <el-form-item label="内存限制"><el-checkbox v-model="form.memoryLimitEnabled">启用限制</el-checkbox><el-input-number v-if="form.memoryLimitEnabled" v-model="memoryGiB" :min="1" :max="512" /><span v-if="form.memoryLimitEnabled" class="unit">GiB</span><span v-else class="unlimited">不限制</span></el-form-item>
        <el-form-item v-if="form.componentType === 'EDITOR'" label="启用 GPU"><el-switch v-model="form.gpuEnabled" /></el-form-item>
        <el-form-item v-if="form.gpuEnabled" label="启用 MPS"><el-switch v-model="form.mpsEnabled" /></el-form-item>
        <el-form-item v-if="form.gpuEnabled && form.mpsEnabled" label="MPS GPU 比例"><el-slider v-model="form.gpuComputePercent" :min="1" :max="100" show-input /></el-form-item>
        <el-form-item v-if="form.gpuEnabled && form.mpsEnabled" label="GPU 显存限制"><el-checkbox v-model="form.gpuMemoryLimitEnabled">启用限制</el-checkbox><el-input-number v-if="form.gpuMemoryLimitEnabled" v-model="gpuMemoryGiB" :min="1" :max="64" /><span v-if="form.gpuMemoryLimitEnabled" class="unit">GiB</span><span v-else class="unlimited">不限制</span></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="publishing" @click="publish">发布</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import { listContainerTemplates, publishContainerTemplate, disableContainerTemplate, deleteContainerTemplate } from '@/api/ContainerTemplates'
import { listImageReleases } from '@/services/imageRegistry'

const GiB = 1024 * 1024 * 1024
export default {
  data: () => ({ GiB, loading: false, publishing: false, dialog: false, templates: [], releases: [], selectedRelease: null, portsText: '', memoryGiB: 2, gpuMemoryGiB: 2, form: {} }),
  computed: { compatibleReleases () { return this.releases.filter(item => item.state === 'PUBLISHED') } },
  created () { this.load() },
  methods: {
    async load () { this.loading = true; try { const [result, releases] = await Promise.all([listContainerTemplates(), listImageReleases({ limit: 200 })]); this.templates = result.data || []; this.releases = releases.data || [] } finally { this.loading = false } },
    releaseLabel (item) { return `${item.componentType === 'ANNOTATION' ? '图像标注' : '代码编辑'} · ${item.groupId} · ${item.version}${item.componentType !== this.form.componentType ? '（组件不匹配）' : ''}` },
    openCreate () { this.form = { templateName: '', componentType: 'ANNOTATION', imageReference: '', releaseId: '', runtimeName: 'sysbox-runc', restartPolicy: 'always', mountTarget: '/root/data', cpuLimitMillis: 2000, cpuLimitEnabled: true, memoryLimitEnabled: true, gpuEnabled: false, mpsEnabled: false, gpuComputePercent: 50, gpuMemoryLimitEnabled: false, privileged: false, hostNetwork: false }; this.selectedRelease = null; this.portsText = '8081'; this.memoryGiB = 2; this.gpuMemoryGiB = 2; this.dialog = true },
    edit (row) { this.form = Object.assign({}, row, { templateVersion: null, mpsEnabled: Boolean(row.mpsEnabled), cpuLimitEnabled: row.cpuLimitMillis != null, memoryLimitEnabled: row.memoryLimitBytes != null, gpuMemoryLimitEnabled: row.gpuMemoryLimitBytes != null }); this.selectedRelease = this.releases.find(item => item.registryDigest === row.imageReference) || null; this.portsText = (row.ports || []).map(p => p.containerPort).join(',') || '8080'; this.memoryGiB = Math.max(1, Math.round((row.memoryLimitBytes || GiB * 2) / GiB)); this.gpuMemoryGiB = Math.max(1, Math.round((row.gpuMemoryLimitBytes || GiB * 2) / GiB)); this.dialog = true },
    applyBoundary (type) { const editor = type === 'EDITOR'; this.selectedRelease = null; this.form.runtimeName = editor ? 'nvidia' : 'sysbox-runc'; this.form.mountTarget = editor ? '/home/student/data' : '/root/data'; this.form.gpuEnabled = editor; this.form.mpsEnabled = false; this.form.gpuMemoryLimitEnabled = false; this.portsText = editor ? '9091,8881,5000' : '8081'; this.memoryGiB = editor ? 6 : 2; this.gpuMemoryGiB = 2 },
    async publish () { if (!this.selectedRelease) { this.$message.warning('请选择已发布镜像版本'); return } const payload = { ...this.form, releaseId: this.selectedRelease.releaseId, imageReference: this.selectedRelease.registryDigest, ports: [], cpuLimitMillis: this.form.cpuLimitEnabled ? this.form.cpuLimitMillis : null, memoryLimitBytes: this.form.memoryLimitEnabled ? this.memoryGiB * GiB : null, gpuMemoryLimitBytes: this.form.gpuMemoryLimitEnabled ? this.gpuMemoryGiB * GiB : null }; if (!payload.mpsEnabled) { payload.gpuComputePercent = null; payload.gpuMemoryLimitBytes = null } if (!payload.gpuEnabled) { payload.mpsEnabled = false; payload.gpuMemoryLimitBytes = null } this.publishing = true; try { await publishContainerTemplate(payload); this.$message.success('模板版本已发布'); this.dialog = false; await this.load() } finally { this.publishing = false } },
    async disable (row) { await this.$confirm('停用后不能再分配给新环境，已有环境仍使用固定版本。', '确认停用', { type: 'warning' }); await disableContainerTemplate(row.templateId, row.templateVersion); await this.load() },
    async remove (row) { await this.$confirm('仅删除未被任何训练或比赛环境引用的模板版本，确认继续？', '确认删除', { type: 'warning' }); try { await deleteContainerTemplate(row.templateId, row.templateVersion); this.$message.success('模板版本已删除'); await this.load() } catch (error) {} }
  }
}
</script>

<style scoped>
.action-heading { display: flex; align-items: center; justify-content: space-between; }
.unit { margin-left: 10px; color: #6b7c8f; }
.unlimited { margin-left: 10px; color: #6b7c8f; }
</style>
