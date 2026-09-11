<template>
  <section class="module-page">
    <header class="module-heading">
      <div>
        <h1>实训管理</h1>
        <p>选择账号创建环境，服务器、槽位和端口由平台自动分配。</p>
        <p class="port-policy">
          图像标注端口由系统自动分配；VS Code 端口使用容器内 9090，<span
            >Jupyter 端口</span
          >
          使用容器内 8888，T100 端口使用容器内 5000，外部端口从端口池自动分配。
        </p>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate"
        >创建实训环境</el-button
      >
    </header>
    <el-table
      :data="environmentGroups"
      v-loading="loading"
      empty-text="暂无实训环境"
      row-key="groupKey"
      ><el-table-column type="expand"
        ><template slot-scope="s"
          ><el-table :data="s.row.items" size="mini" border
            ><el-table-column
              prop="userId"
              label="账号"
              width="90"
            /><el-table-column prop="courseId" label="课程" /><el-table-column
              label="服务器槽位"
              ><template slot-scope="i"
                >{{ i.row.agentId }} / 槽位 {{ i.row.slotNumber }}</template
              ></el-table-column
            ><el-table-column
              prop="actualState"
              label="状态"
              width="100"
            /><el-table-column prop="resultMessage" label="最近操作结果" min-width="220" /><el-table-column label="操作" width="150"
              ><template slot-scope="i"
                ><el-button type="text" @click="restore(i.row)">重置</el-button
                ><el-button
                  type="text"
                  class="danger"
                  @click="remove(i.row)"
                  >删除</el-button
                ></template
              ></el-table-column
            ></el-table
          ></template
        ></el-table-column
      ><el-table-column
        prop="environmentName"
        label="环境名称"
      /><el-table-column label="类型"
        ><template slot-scope="s">{{
          s.row.environmentType === "COMPETITION" ? "比赛环境" : "课程环境"
        }}</template></el-table-column
      ><el-table-column label="用户数"
        ><template slot-scope="s">{{
          s.row.items.length
        }}</template></el-table-column
      ><el-table-column label="运行中"
        ><template slot-scope="s">{{
          s.row.items.filter((i) => i.actualState === "RUNNING").length
        }}</template></el-table-column
      ><el-table-column label="停止"
        ><template slot-scope="s">{{
          s.row.items.filter((i) => i.actualState === "STOPPED").length
        }}</template></el-table-column
      ><el-table-column label="操作" width="130"
        ><template slot-scope="s"
          ><el-button
            type="text"
            class="danger"
            :loading="deletingGroupKey === s.row.groupKey"
            @click="removeGroup(s.row)"
            >全部删除</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-dialog
      title="创建实训环境"
      :visible.sync="dialog"
      width="680px"
      :close-on-click-modal="false"
      ><el-form label-width="120px"
        ><el-form-item label="环境类型"
          ><el-radio-group v-model="form.environmentType"
            ><el-radio-button label="COURSE">课程环境</el-radio-button
            ><el-radio-button label="COMPETITION"
              >比赛环境</el-radio-button
            ></el-radio-group
          ></el-form-item
        ><el-form-item label="环境名称"
          ><el-input v-model.trim="form.environmentName" /></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model.trim="form.remark" /></el-form-item
        ><el-form-item label="关联账号"
          ><div class="account-actions">
            <el-checkbox v-model="selectAll" @change="toggleAll"
              >全选当前可用账号</el-checkbox
            ><span
              >已选择 {{ form.userIds.length }} /
              {{ eligibleAccounts.length }}</span
            >
          </div>
          <el-select
            v-model="form.userIds"
            multiple
            filterable
            collapse-tags
            style="width: 100%"
            @change="selectionChanged"
            ><el-option
              v-for="u in eligibleAccounts"
              :key="u.userId"
              :value="u.userId"
              :label="`${u.userName} · ${u.agentName || u.primaryIp} / 槽位 ${
                u.slotNumber
              }`"
          /></el-select>
          <div class="placement-list">
            <span v-for="u in selectedAccounts" :key="u.userId"
              >{{ u.userName }} → {{ u.agentName || u.primaryIp }} / 槽位
              {{ u.slotNumber }}</span
            >
          </div></el-form-item
        ><el-form-item label="关联课程"
          ><el-select
            v-model="form.courseId"
            clearable
            filterable
            style="width: 100%"
            ><el-option
              v-for="item in courses"
              :key="course(item).courseId"
              :value="course(item).courseId"
              :label="course(item).name" /></el-select></el-form-item
        ><el-form-item label="图像标注模板"
          ><el-select
            v-model="annotationKey"
            clearable
            style="width: 100%"
            @change="templateChanged('ANNOTATION')"
            ><el-option
              v-for="t in annotationTemplates"
              :key="key(t)"
              :value="key(t)"
              :label="label(t)" /></el-select></el-form-item
        ><el-form-item label="代码编辑模板"
          ><el-select
            v-model="editorKey"
            clearable
            style="width: 100%"
            @change="templateChanged('EDITOR')"
            ><el-option
              v-for="t in editorTemplates"
              :key="key(t)"
              :value="key(t)"
              :label="label(t)" /></el-select></el-form-item
        ><el-alert
          type="info"
          :closable="false"
          show-icon
          title="平台会按账号绑定的服务器，从对应环境端口池分配空闲端口。" /></el-form
      ><span slot="footer"
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="creating" @click="create"
          >创建</el-button
        ></span
      ></el-dialog
    >
    <el-dialog title="创建结果" :visible.sync="resultDialog" width="620px"
      ><el-table :data="results"
        ><el-table-column prop="userId" label="账号 ID" /><el-table-column
          label="结果"
          ><template slot-scope="s"
            ><el-tag :type="s.row.state === 'SUCCEEDED' ? 'success' : s.row.state === 'FAILED' ? 'danger' : 'info'">{{
              s.row.state === 'SUCCEEDED' ? "创建完成" : s.row.state === 'FAILED' ? "创建失败" : "创建中"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="message" label="说明" /></el-table
    ></el-dialog>
  </section>
</template>
<script>
import {
  listAdminTrainingEnvironments,
  createAdminTrainingEnvironment,
  restoreAdminTrainingEnvironment,
  deleteAdminTrainingEnvironment,
  listAdminEnvironmentTemplates,
  listEligibleEnvironmentAccounts,
} from "@/api/TrainingEnvironments";
import { listAdminCourses } from "@/api/Courses";
const empty = () => ({
  environmentType: "COURSE",
  environmentName: "",
  remark: "",
  userIds: [],
  courseId: null,
  annotationTemplateId: null,
  annotationTemplateVersion: null,
  editorTemplateId: null,
  editorTemplateVersion: null,
});
export default {
  data: () => ({
    loading: false,
    creating: false,
    dialog: false,
    resultDialog: false,
    environments: [],
    deletingGroupKey: "",
    eligibleAccounts: [],
    courses: [],
    templates: [],
    form: empty(),
    annotationKey: "",
    editorKey: "",
    selectAll: false,
    results: [],
    refreshTimer: null,
  }),
  computed: {
    environmentGroups() {
      const groups = {};
      this.environments.forEach((item) => {
        const key = `${item.environmentType || "COURSE"}:${
          item.environmentName || "默认"
        }`;
        if (!groups[key])
          groups[key] = {
            groupKey: key,
            environmentName: item.environmentName || "默认",
            environmentType: item.environmentType || "COURSE",
            items: [],
          };
        groups[key].items.push(item);
      });
      return Object.values(groups);
    },
    annotationTemplates() {
      return this.templates.filter((x) => x.componentType === "ANNOTATION");
    },
    editorTemplates() {
      return this.templates.filter((x) => x.componentType === "EDITOR");
    },
    selectedAccounts() {
      return this.eligibleAccounts.filter((x) =>
        this.form.userIds.includes(x.userId)
      );
    },
  },
  created() {
    this.load();
    this.refreshTimer = window.setInterval(() => { if (!this.loading) this.refreshEnvironments().catch(() => {}); }, 3000);
  },
  beforeDestroy() { window.clearInterval(this.refreshTimer); },
  methods: {
    syncCreationResults() {
      this.results = this.results.map(result => {
        if (!result.success) return { ...result, state: 'FAILED' };
        const environment = this.environments.find(row => result.operation && row.environmentId === result.operation.environmentId);
        if (!environment || environment.operationId !== result.operation.operationId) return { ...result, state: result.state || 'PENDING' };
        return { ...result, state: environment.operationState, message: environment.resultMessage || '' };
      });
    },
    async refreshEnvironments() {
      const response = await listAdminTrainingEnvironments();
      this.environments = response.data || [];
      this.syncCreationResults();
    },
    course(v) {
      return v.course || v;
    },
    key(v) {
      return `${v.templateId}:${v.templateVersion}`;
    },
    label(v) {
      return `${v.templateName || v.templateId} / v${v.templateVersion}`;
    },
    async load() {
      this.loading = true;
      try {
        const [e, u, c, t] = await Promise.all([
          listAdminTrainingEnvironments(),
          listEligibleEnvironmentAccounts(),
          listAdminCourses(),
          listAdminEnvironmentTemplates(),
        ]);
        this.environments = e.data || [];
        this.syncCreationResults();
        this.eligibleAccounts = u.data || [];
        this.courses = c.data || [];
        this.templates = t.data || [];
      } finally {
        this.loading = false;
      }
    },
    async openCreate() {
      this.form = empty();
      this.annotationKey = "";
      this.editorKey = "";
      this.selectAll = false;
      const [accounts, courses, templates] = await Promise.all([
        listEligibleEnvironmentAccounts(),
        listAdminCourses(),
        listAdminEnvironmentTemplates(),
      ]);
      this.eligibleAccounts = accounts.data || [];
      this.courses = courses.data || [];
      this.templates = templates.data || [];
      this.dialog = true;
    },
    toggleAll(v) {
      this.form.userIds = v ? this.eligibleAccounts.map((x) => x.userId) : [];
    },
    selectionChanged() {
      this.selectAll =
        this.form.userIds.length === this.eligibleAccounts.length &&
        this.eligibleAccounts.length > 0;
    },
    templateChanged(type) {
      const value = type === "ANNOTATION" ? this.annotationKey : this.editorKey;
      const t = this.templates.find((x) => this.key(x) === value);
      if (type === "ANNOTATION") {
        this.form.annotationTemplateId = (t && t.templateId) || null;
        this.form.annotationTemplateVersion = (t && t.templateVersion) || null;
      } else {
        this.form.editorTemplateId = (t && t.templateId) || null;
        this.form.editorTemplateVersion = (t && t.templateVersion) || null;
      }
    },
    async create() {
      if (!this.form.environmentName || !this.form.userIds.length)
        return this.$message.warning("请填写环境名称并选择账号");
      if (!this.form.annotationTemplateId && !this.form.editorTemplateId)
        return this.$message.warning("至少选择一个模板");
      this.creating = true;
      try {
        const r = await createAdminTrainingEnvironment(this.form);
        this.results = r.data || [];
        this.dialog = false;
        this.resultDialog = true;
        await this.load();
      } finally {
        this.creating = false;
      }
    },
    async restore(v) {
      await this.$confirm("重置会删除并重建容器，容器内未保存在挂载目录的数据将丢失。完成后环境保持停止。", "重置环境", { type: "warning" });
      await restoreAdminTrainingEnvironment(v.environmentId);
      await this.load();
    },
    async remove(v) {
      await this.$confirm("确认强制删除该环境及服务器容器？", "删除", { type: "warning" });
      await deleteAdminTrainingEnvironment(v.environmentId);
      for (let attempt = 0; attempt < 30; attempt += 1) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        await this.load();
        if (!this.environments.some(item => item.environmentId === v.environmentId)) {
          this.$message.success("环境及服务器容器已删除");
          return;
        }
      }
      this.$message.warning("删除命令已提交，服务器尚未确认容器删除");
    },
    async removeGroup(group) {
      await this.$confirm(
        `确认删除“${group.environmentName}”及其 ${group.items.length} 个用户容器？`,
        "全部删除",
        { type: "warning" }
      );
      this.deletingGroupKey = group.groupKey;
      try {
        for (const item of group.items)
          await deleteAdminTrainingEnvironment(item.environmentId);
        this.$message.success("已提交整个实训环境的删除任务");
        await this.load();
      } finally {
        this.deletingGroupKey = "";
      }
    },
  },
};
</script>
<style scoped>
.account-actions {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.placement-list {
  display: grid;
  gap: 3px;
  margin-top: 8px;
  color: #718096;
  font-size: 12px;
}
.danger {
  color: #f56c6c;
}
</style>
