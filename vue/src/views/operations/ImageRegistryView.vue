<template>
  <section class="module-page module-composed-page registry-page">
    <header class="module-heading module-toolbar action-heading">
      <div><h1>镜像仓库</h1><p>审批 Docker 归档，按摘要发布图像标注或代码编辑镜像，并逐台推送到处理服务器。</p></div>
      <div class="heading-actions"><el-button icon="el-icon-refresh" :loading="loading" @click="loadAll">刷新</el-button><el-button v-if="canWrite" type="primary" icon="el-icon-upload2" @click="uploadVisible = true">上传镜像</el-button></div>
    </header>

    <div class="toolbar module-toolbar"><el-input v-model.trim="query" prefix-icon="el-icon-search" clearable placeholder="搜索镜像组、版本或摘要" /><el-select v-model="componentFilter" clearable placeholder="全部组件"><el-option label="图像标注" value="ANNOTATION" /><el-option label="代码编辑" value="EDITOR" /></el-select><span class="read-only" v-if="!canWrite"><i class="el-icon-view" /> 普通管理员只读</span></div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="镜像目录" name="catalog">
        <div v-loading="loading" class="catalog">
          <section v-for="group in filteredGroups" :key="group.groupId" class="group-section">
            <header><div><strong>{{ group.name || group.groupName || group.groupId }}</strong><span>{{ group.groupType === 'PENDING' ? '待审批归档' : group.groupType === 'CUSTOM' ? '自定义组合' : '标准镜像组' }}</span></div><small>{{ artifactsFor(group.groupId).length }} 个版本</small></header>
            <div class="module-table-wrap"><el-table :data="artifactsFor(group.groupId)" size="small" empty-text="该组暂无镜像">
              <el-table-column label="组件" width="105"><template slot-scope="scope"><el-tag size="small" effect="plain">{{ componentLabel(scope.row.componentType) }}</el-tag></template></el-table-column>
              <el-table-column prop="version" label="版本" width="115" />
              <el-table-column prop="originalFilename" label="归档" min-width="150" show-overflow-tooltip />
              <el-table-column label="状态" width="120"><template slot-scope="scope"><el-tag size="small" :type="artifactTag(scope.row)">{{ artifactState(scope.row) }}</el-tag></template></el-table-column>
              <el-table-column label="不可变摘要" min-width="250"><template slot-scope="scope"><code v-if="scope.row.registryDigest">{{ scope.row.registryDigest }}</code><span v-else class="muted">导入完成后生成</span></template></el-table-column>
              <el-table-column v-if="canWrite" label="操作" width="180" align="right"><template slot-scope="scope"><template v-if="scope.row.reviewState === 'PENDING_REVIEW'"><el-button type="text" @click="review(scope.row, 'APPROVED')">批准</el-button><el-button type="text" class="danger-text" @click="review(scope.row, 'REJECTED')">驳回</el-button></template><el-button v-if="scope.row.importState === 'READY' && scope.row.registryDigest" type="text" @click="publish(scope.row)">发布该组件</el-button></template></el-table-column>
            </el-table></div>
          </section>
          <el-empty v-if="!loading && filteredGroups.length === 0" description="暂无匹配的镜像组" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="发布版本" name="releases">
        <div class="module-table-wrap"><el-table v-loading="loading" :data="filteredReleases" empty-text="暂无已发布版本">
          <el-table-column prop="publishedAt" label="发布时间" width="170" />
          <el-table-column label="组件" width="110"><template slot-scope="scope">{{ componentLabel(scope.row.componentType) }}</template></el-table-column>
          <el-table-column prop="version" label="版本" width="120" />
          <el-table-column prop="registryDigest" label="不可变摘要" min-width="310"><template slot-scope="scope"><code>{{ scope.row.registryDigest }}</code></template></el-table-column>
          <el-table-column prop="state" label="状态" width="95"><template slot-scope="scope"><el-tag size="small" type="success">{{ scope.row.state || 'PUBLISHED' }}</el-tag></template></el-table-column>
          <el-table-column v-if="canWrite" label="操作" width="110" align="right"><template slot-scope="scope"><el-button type="text" icon="el-icon-position" @click="openDeployment(scope.row)">推送</el-button></template></el-table-column>
        </el-table></div>
      </el-tab-pane>

      <el-tab-pane label="推送进度" name="deployments">
        <div class="module-table-wrap"><el-table v-loading="loading" :data="filteredDeployments" empty-text="暂无推送任务">
          <el-table-column prop="requestedAt" label="发起时间" width="170" />
          <el-table-column prop="agentName" label="处理服务器" min-width="145"><template slot-scope="scope">{{ scope.row.agentName || scope.row.agentId }}</template></el-table-column>
          <el-table-column label="组件" width="105"><template slot-scope="scope">{{ componentLabel(scope.row.componentType) }}</template></el-table-column>
          <el-table-column label="策略" width="130"><template slot-scope="scope">{{ scope.row.updatePolicy === 'IMAGE_ONLY' ? '仅更新镜像' : '更新容器' }}</template></el-table-column>
          <el-table-column prop="targetDigest" label="目标摘要" min-width="220"><template slot-scope="scope"><code>{{ scope.row.targetDigest }}</code></template></el-table-column>
          <el-table-column label="进度" width="120"><template slot-scope="scope"><el-tag size="small" :type="deploymentTag(scope.row.state)">{{ deploymentState(scope.row.state) }}</el-tag></template></el-table-column>
          <el-table-column label="失败原因" min-width="180"><template slot-scope="scope"><span class="failure">{{ scope.row.failureMessage || scope.row.failureCode || '-' }}</span></template></el-table-column>
          <el-table-column v-if="canWrite" label="操作" width="90" align="right"><template slot-scope="scope"><el-button v-if="scope.row.state === 'FAILED' && scope.row.previousDigest" type="text" @click="rollback(scope.row)">回滚</el-button></template></el-table-column>
        </el-table></div>
      </el-tab-pane>
    </el-tabs>

    <ImageUploadDialog :visible.sync="uploadVisible" @uploaded="loadAll" />
    <ImageDeploymentDialog :visible.sync="deploymentVisible" :release="selectedRelease" @deployed="deploymentCreated" />
  </section>
</template>

<script>
import ImageUploadDialog from '@/components/operations/ImageUploadDialog.vue'
import ImageDeploymentDialog from '@/components/operations/ImageDeploymentDialog.vue'
import { listImageGroups, listImageArtifacts, listImageReleases, listImageDeployments, reviewImageArtifact, publishImageRelease, rollbackImageDeployment } from '@/services/imageRegistry'
import { getRole } from '@/utils/auth'
import { SUPER_ADMIN } from '@/navigation/roleNavigation'

function valueOf (result) { return result && result.data !== undefined ? result.data : result }
function recordsOf (result) { const value = valueOf(result); return Array.isArray(value) ? value : ((value && value.records) || []) }
export default {
  name: 'ImageRegistryView',
  components: { ImageUploadDialog, ImageDeploymentDialog },
  data: () => ({ activeTab: 'catalog', query: '', componentFilter: '', loading: false, groups: [], artifacts: [], releases: [], deployments: [], uploadVisible: false, deploymentVisible: false, selectedRelease: null, poller: null }),
  computed: {
    canWrite () { return getRole() === SUPER_ADMIN },
    displayGroups () {
      const known = new Set(this.groups.map(group => group.groupId))
      const pendingGroups = this.filteredArtifacts.filter(item => !known.has(item.groupId)).map(item => ({ groupId: item.groupId, name: item.groupId, groupType: 'PENDING' }))
      return this.groups.concat(pendingGroups.filter((group, index, list) => list.findIndex(item => item.groupId === group.groupId) === index))
    },
    filteredGroups () { const ids = new Set(this.filteredArtifacts.map(item => item.groupId)); const term = this.query.toLowerCase(); return this.displayGroups.filter(group => ids.has(group.groupId) || !term && !this.componentFilter || `${group.name || ''} ${group.groupName || ''} ${group.groupId || ''}`.toLowerCase().includes(term)) },
    filteredArtifacts () { const term = this.query.toLowerCase(); return this.artifacts.filter(item => (!this.componentFilter || item.componentType === this.componentFilter) && (!term || `${item.version || ''} ${item.registryDigest || ''} ${item.originalFilename || ''} ${item.groupId || ''}`.toLowerCase().includes(term))) },
    filteredReleases () { const term = this.query.toLowerCase(); return this.releases.filter(item => (!this.componentFilter || item.componentType === this.componentFilter) && (!term || `${item.version || ''} ${item.registryDigest || ''} ${item.groupId || ''}`.toLowerCase().includes(term))) },
    filteredDeployments () { const term = this.query.toLowerCase(); return this.deployments.filter(item => (!this.componentFilter || item.componentType === this.componentFilter) && (!term || `${item.agentName || ''} ${item.agentId || ''} ${item.targetDigest || ''}`.toLowerCase().includes(term))) }
  },
  created () { this.loadAll(); this.poller = setInterval(() => this.refreshDeployments().catch(() => {}), 5000) },
  beforeDestroy () { clearInterval(this.poller) },
  methods: {
    async loadAll () {
      this.loading = true
      try {
        const [groups, artifacts, releases, deployments] = await Promise.all([listImageGroups({ limit: 200 }), listImageArtifacts({ limit: 500 }), listImageReleases({ limit: 200 }), listImageDeployments({ limit: 200 })])
        this.groups = recordsOf(groups); this.artifacts = recordsOf(artifacts); this.releases = recordsOf(releases); this.deployments = recordsOf(deployments)
      } finally { this.loading = false }
    },
    async refreshDeployments () { if (!this.deployments.some(item => ['PENDING', 'PULLED', 'RUNNING'].includes(item.state))) return; const result = await listImageDeployments({ limit: 200 }); this.deployments = recordsOf(result) },
    artifactsFor (groupId) { return this.filteredArtifacts.filter(item => item.groupId === groupId) },
    componentLabel (componentType) { return componentType === 'ANNOTATION' ? '图像标注' : componentType === 'EDITOR' ? '代码编辑' : componentType },
    artifactState (artifact) { if (artifact.failureMessage) return artifact.failureMessage; return ({ PENDING_REVIEW: '待审批', APPROVED: '已批准', REJECTED: '已驳回' })[artifact.reviewState] || ({ IMPORTING: '导入中', READY: '可发布', FAILED: '导入失败' })[artifact.importState] || artifact.reviewState || artifact.importState || '上传中' },
    artifactTag (artifact) { if (artifact.failureMessage || artifact.reviewState === 'REJECTED' || artifact.importState === 'FAILED') return 'danger'; if (artifact.importState === 'READY') return 'success'; if (artifact.reviewState === 'PENDING_REVIEW') return 'warning'; return 'info' },
    deploymentState (state) { return ({ PENDING: '等待 Agent', PULLED: '镜像已拉取', RUNNING: '健康检查中', SUCCEEDED: '已完成', FAILED: '失败', ROLLED_BACK: '已回滚' })[state] || state },
    deploymentTag (state) { return ({ PENDING: 'info', PULLED: '', RUNNING: 'warning', SUCCEEDED: 'success', FAILED: 'danger', ROLLED_BACK: 'success' })[state] || 'info' },
    async review (artifact, decision) { let reason = ''; if (decision === 'REJECTED') { const result = await this.$prompt('填写驳回原因，归档将保留到清理策略执行。', '驳回镜像', { inputType: 'textarea', inputValidator: value => Boolean(value && value.trim()) || '请填写驳回原因' }); reason = result.value } else await this.$confirm('批准后导入任务会异步写入内部镜像仓库。是否继续？', '批准镜像', { type: 'warning' }); await reviewImageArtifact(artifact.artifactId, decision, reason); await this.loadAll() },
    async publish (artifact) { await this.$confirm(`发布${this.componentLabel(artifact.componentType)}版本 ${artifact.version}？其他组件版本不会改变。`, '发布镜像', { type: 'warning' }); await publishImageRelease(artifact.artifactId); this.activeTab = 'releases'; await this.loadAll() },
    openDeployment (release) { this.selectedRelease = release; this.deploymentVisible = true },
    deploymentCreated (deployment) { if (deployment) this.deployments.unshift(deployment); this.activeTab = 'deployments' },
    async rollback (deployment) { await this.$confirm(`将 ${this.componentLabel(deployment.componentType)} 恢复到上一可用摘要，并重建受影响容器。确认继续？`, '确认回滚', { type: 'warning', confirmButtonText: '确认回滚' }); await rollbackImageDeployment(deployment.deploymentId); await this.refreshDeployments() }
  }
}
</script>

<style scoped>
.registry-page { max-width: 1400px; }
.action-heading, .toolbar, .group-section > header, .heading-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.toolbar { justify-content: flex-start; margin-bottom: 14px; }
.toolbar .el-input { width: min(440px, 55vw); }
.toolbar .el-select { width: 150px; }
.read-only { margin-left: auto; color: #687986; font-size: 13px; }
.group-section { margin-bottom: 16px; border: 1px solid #dfe7ed; background: #fff; }
.group-section > header { min-height: 58px; padding: 0 16px; border-bottom: 1px solid #e7edf1; }
.group-section header strong, .group-section header span { display: block; }
.group-section header span, .group-section header small, .muted { margin-top: 3px; color: #7a8995; font-size: 12px; }
code { color: var(--ui-text); font: 12px/1.5 monospace; overflow-wrap: anywhere; }
.danger-text, .failure { color: #c75454; }
::v-deep .el-tabs__header { margin: 0 0 18px; background: transparent; border-bottom: 1px solid #dfe7ed; }
::v-deep .el-tabs__nav-wrap::after { height: 1px; background: #dfe7ed; }
::v-deep .el-tabs__item { height: 44px; padding: 0 28px; color: #6d7d91; font-size: 15px; line-height: 44px; }
::v-deep .el-tabs__item.is-active { color: #2f6fed; font-weight: 600; }
::v-deep .el-tabs__active-bar { height: 3px; background: #5b7cf3; }
@media (max-width: 760px) { .action-heading { align-items: flex-start; flex-direction: column; } .toolbar { align-items: stretch; flex-wrap: wrap; } .toolbar .el-input { width: 100%; } .read-only { margin-left: 0; } }
</style>
