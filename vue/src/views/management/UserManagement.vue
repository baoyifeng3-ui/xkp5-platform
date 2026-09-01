<template>
  <section class="module-page user-management">
    <header class="module-heading">
      <div>
        <h1>账号管理</h1>
        <p>账号绑定服务器槽位后才能创建实训环境。</p>
      </div>
      <div>
        <el-button icon="el-icon-document" @click="$refs.templates.open()"
          >账号模板</el-button
        ><el-button icon="el-icon-upload2" @click="$refs.importer.open()"
          >表格导入</el-button
        ><el-button icon="el-icon-monitor" @click="openDemoPlacement"
          >教学槽位</el-button
        ><el-button @click="openBatch">批量创建账号</el-button
        ><el-button type="primary" @click="openCreate">创建账号</el-button>
      </div>
    </header>
    <div class="summary">
      <div>
        总账号<strong>{{ users.length }}</strong>
      </div>
      <div>
        已启用<strong>{{ enabledCount }}</strong>
      </div>
      <div>
        已绑定<strong>{{ boundCount }}</strong>
      </div>
      <div>
        可创建环境<strong>{{ readyCount }}</strong>
      </div>
    </div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索账号、备注或自定义字段"
      /><el-select v-model="agentFilter" clearable placeholder="全部服务器"
        ><el-option
          v-for="a in agents"
          :key="a.agentId"
          :label="a.displayName || a.primaryIp"
          :value="a.agentId" /></el-select
      ><el-button :disabled="!selected.length" @click="bulk(true)"
        >批量启用</el-button
      ><el-button :disabled="!selected.length" @click="bulk(false)"
        >批量禁用</el-button
      ><el-button
        type="danger"
        plain
        :disabled="!selected.length"
        @click="removeSelected"
        >批量删除</el-button
      >
    </div>
    <el-table
      v-loading="loading"
      :data="filtered"
      @selection-change="selected = $event"
      ><el-table-column type="selection" width="45" /><el-table-column
        prop="userName"
        label="账号"
      /><el-table-column prop="remark" label="备注" /><el-table-column
        label="服务器槽位"
        min-width="200"
        ><template slot-scope="s"
          ><span v-if="s.row.slotId"
            >{{ s.row.agentName || s.row.agentId }} / 槽位
            {{ s.row.slotNumber }}</span
          ><el-tag v-else type="danger" size="mini">未绑定</el-tag></template
        ></el-table-column
      ><el-table-column
        v-for="name in customFieldNames"
        :key="`field:${name}`"
        :label="name"
        min-width="120"
        ><template slot-scope="s">{{
          customFieldValue(s.row.customFields, name)
        }}</template></el-table-column
      ><el-table-column label="状态" width="90"
        ><template slot-scope="s"
          ><el-tag :type="s.row.enabled ? 'success' : 'info'">{{
            s.row.enabled ? "启用" : "禁用"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column v-if="competitionMode" label="比赛密码" width="140"
        ><template slot-scope="s"
          ><code>{{
            credential(s.row.userId).password || "--"
          }}</code></template
        ></el-table-column
      ><el-table-column label="操作" width="260"
        ><template slot-scope="s"
          ><el-button type="text" @click="openEdit(s.row)">编辑</el-button
          ><el-button
            v-if="s.row.slotId"
            type="text"
            @click="openMigration(s.row)"
            >更换槽位</el-button
          ><el-button
            v-if="competitionMode"
            type="text"
            @click="regenerate(s.row)"
            >重新生成比赛密码</el-button
          ><el-button type="text" class="danger" @click="removeOne(s.row)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    >

    <el-dialog
      :title="editing ? '编辑账号' : '创建账号'"
      :visible.sync="dialog"
      width="620px"
      ><el-form label-width="105px"
        ><el-form-item label="用户名"
          ><el-input v-model.trim="form.userName" /></el-form-item
        ><el-form-item label="密码"
          ><el-input
            v-model="form.password"
            show-password
            :placeholder="editing ? '留空表示不修改' : ''" /></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model.trim="form.remark" /></el-form-item
        ><el-form-item label="服务器槽位（可选）"
          ><el-select v-model="form.slotId" filterable style="width: 100%"
            ><el-option
              v-for="slot in availableSlots(form.userId)"
              :key="slot.slotId"
              :value="slot.slotId"
              :label="slotLabel(slot)" /></el-select></el-form-item
        ><el-form-item label="自定义字段"
          ><div v-for="(f, i) in form.customFields" :key="i" class="field-row">
            <el-input v-model.trim="f.key" placeholder="字段名" /><el-input
              v-model.trim="f.value"
              placeholder="字段值"
            /><el-button
              icon="el-icon-delete"
              circle
              @click="form.customFields.splice(i, 1)"
            />
          </div>
          <el-button
            type="text"
            @click="form.customFields.push({ key: '', value: '' })"
            >添加自定义字段</el-button
          ></el-form-item
        ><el-form-item v-if="!editing" label="首次完善"
          ><el-checkbox v-model="form.requireProfile"
            >首次登录必须完善账号资料</el-checkbox
          ></el-form-item
        ><el-form-item label="状态"
          ><el-switch v-model="form.enabled" /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></span
      ></el-dialog
    >
    <el-dialog title="批量创建账号" :visible.sync="batchDialog" width="560px"
      ><el-form label-width="100px"
        ><el-form-item label="创建数量"
          ><el-input-number
            v-model="batch.count"
            :min="1"
            :max="500" /></el-form-item
        ><el-form-item label="处理服务器（可选）"
          ><el-select v-model="batch.agentIds" multiple style="width: 100%"
            ><el-option
              v-for="a in agents"
              :key="a.agentId"
              :value="a.agentId"
              :label="a.displayName || a.primaryIp" /></el-select></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model="batch.remark" /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="batchDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveBatch"
          >创建</el-button
        ></span
      ></el-dialog
    >
    <el-dialog title="批量创建结果" :visible.sync="resultDialog" width="680px"
      ><el-table :data="batchResults"
        ><el-table-column prop="userName" label="账号" /><el-table-column
          prop="initialPassword"
          label="6 位初始密码" /><el-table-column
          prop="agentId"
          label="服务器" /><el-table-column
          prop="slotNumber"
          label="槽位" /></el-table
    ></el-dialog>
    <el-dialog
      title="环境迁移预检"
      :visible.sync="migrationDialog"
      width="520px"
      ><el-form label-width="110px"
        ><el-form-item label="账号">{{
          migration.user && migration.user.userName
        }}</el-form-item
        ><el-form-item label="目标槽位"
          ><el-select v-model="migration.targetSlotId" style="width: 100%"
            ><el-option
              v-for="slot in availableSlots(
                migration.user && migration.user.userId
              )"
              :key="slot.slotId"
              :value="slot.slotId"
              :label="slotLabel(slot)" /></el-select></el-form-item
        ><el-form-item label="迁移用户数据"
          ><el-checkbox v-model="migration.migrateData"
            >迁移用户数据</el-checkbox
          ><small>默认不迁移，勾选后复制用户工作目录。</small></el-form-item
        ></el-form
      ><span slot="footer"
        ><el-button @click="migrationDialog = false">取消</el-button
        ><el-button type="primary" @click="preflight">开始预检</el-button></span
      ></el-dialog
    >
    <AccountTemplateDialog ref="templates" /><AccountImportDialog
      ref="importer"
      :slots="slots"
      @done="load"
    />
  </section>
</template>
<script>
import {
  listAccounts,
  createAccount,
  updateAccount,
  deleteAccount,
  createAccountBatch,
  bulkEnableAccounts,
  bulkDisableAccounts,
  bulkDeleteAccounts,
  preflightAccountMigration,
  listCompetitionCredentials,
  regenerateCompetitionPassword,
  getAdminDemoPlacement,
  bindAdminDemoPlacement,
  listAccountTemplates,
  saveAccountTemplate,
} from "@/api/AccountManagement";
import { listProcessingAgents } from "@/api/ProcessingAgents";
import { listAdminTrainingSlots } from "@/api/TrainingEnvironments";
import { getPlatformMode } from "@/api/PlatformMode";
const emptyForm = () => ({
  userId: null,
  userName: "",
  password: "",
  remark: "",
  slotId: "",
  enabled: true,
  requireProfile: false,
  customFields: [],
});
export default {
  data: () => ({
    loading: false,
    saving: false,
    users: [],
    agents: [],
    slots: [],
    credentials: [],
    selected: [],
    keyword: "",
    agentFilter: "",
    dialog: false,
    editing: false,
    form: emptyForm(),
    batchDialog: false,
    batch: { count: 1, agentIds: [], remark: "" },
    resultDialog: false,
    batchResults: [],
    migrationDialog: false,
    migration: { user: null, targetSlotId: "", migrateData: false },
    competitionMode: false,
    demoPlacementDialog: false,
    demoPlacementCurrent: null,
    demoPlacementSlotId: "",
    demoPlacementSlots: [],
  }),
  computed: {
    enabledCount() {
      return this.users.filter((x) => x.enabled).length;
    },
    boundCount() {
      return this.users.filter((x) => x.slotId).length;
    },
    readyCount() {
      return this.users.filter((x) => x.placementReady).length;
    },
    customFieldNames() {
      return [
        ...new Set(
          this.users.flatMap((x) => Object.keys(x.customFields || {}))
        ),
      ].sort((a, b) => a.localeCompare(b, "zh-CN"));
    },
    filtered() {
      const k = this.keyword.toLowerCase();
      return this.users.filter(
        (x) =>
          (!this.agentFilter || x.agentId === this.agentFilter) &&
          (!k ||
            `${x.userName} ${x.remark || ""} ${this.fieldText(x.customFields)}`
              .toLowerCase()
              .includes(k))
      );
    },
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const m = await getPlatformMode();
        this.competitionMode = m.data && m.data.mode === "COMPETITION";
        const [u, a, s] = await Promise.all([
          listAccounts(),
          listProcessingAgents(),
          this.competitionMode ? Promise.resolve({ data: [] }) : listAdminTrainingSlots(),
        ]);
        this.users = u.data || [];
        this.agents = (a.data || []).filter(
          (x) => x.enabled !== false && x.online !== false
        );
        this.slots = s.data || [];
        this.credentials = this.competitionMode
          ? (await listCompetitionCredentials()).data || []
          : [];
      } finally {
        this.loading = false;
      }
    },
    fieldText(v) {
      return (
        Object.entries(v || {})
          .map(([k, x]) => `${k}:${x}`)
          .join("；") || "--"
      );
    },
    customFieldValue(fields, name) {
      return fields && fields[name] != null ? fields[name] : "";
    },
    credential(id) {
      return (
        this.credentials.find((x) => Number(x.userId) === Number(id)) || {}
      );
    },
    slotLabel(s) {
      const a = this.agents.find((x) => x.agentId === s.agentId);
      return `${(a && (a.displayName || a.primaryIp)) || s.agentId} / 槽位 ${
        s.slotNumber
      }`;
    },
    availableSlots(id) {
      const active = new Set(this.agents.map((x) => x.agentId));
      const current = this.users.find((x) => Number(x.userId) === Number(id));
      return this.slots.filter(
        (s) =>
          active.has(s.agentId) &&
          (!s.userId ||
            Number(s.userId) === Number(id) ||
            (current && current.slotId === s.slotId))
      );
    },
    openCreate() {
      this.editing = false;
      this.form = emptyForm();
      this.dialog = true;
    },
    openEdit(u) {
      this.editing = true;
      this.form = {
        ...emptyForm(),
        ...u,
        customFields: Object.entries(u.customFields || {}).map(
          ([key, value]) => ({ key, value })
        ),
      };
      this.dialog = true;
    },
    fields() {
      const v = {};
      this.form.customFields.forEach((x) => {
        if (x.key) v[x.key] = x.value;
      });
      return v;
    },
    async save() {
      if (!this.form.userName || (!this.editing && !this.form.password))
        return this.$message.warning("用户名和密码不能为空");
      this.saving = true;
      try {
        const p = { ...this.form, customFields: this.fields() };
        if (this.editing) await updateAccount(this.form.userId, p);
        else await createAccount(p);
        this.dialog = false;
        await this.load();
      } finally {
        this.saving = false;
      }
    },
    openBatch() {
      this.batch = { count: 1, agentIds: [], remark: "" };
      this.batchDialog = true;
    },
    async saveBatch() {
      this.saving = true;
      try {
        const r = await createAccountBatch(this.batch);
        this.batchResults = r.data || [];
        this.batchDialog = false;
        this.resultDialog = true;
        await this.load();
      } finally {
        this.saving = false;
      }
    },
    async bulk(v) {
      const ids = this.selected.map((x) => x.userId);
      await (v ? bulkEnableAccounts(ids) : bulkDisableAccounts(ids));
      await this.load();
    },
    async removeSelected() {
      await this.$confirm("存在实训环境的账号不会被删除。", "批量删除", {
        type: "warning",
      });
      await bulkDeleteAccounts(this.selected.map((x) => x.userId));
      await this.load();
    },
    async removeOne(u) {
      await this.$confirm(`确认删除 ${u.userName}？`, "删除", {
        type: "warning",
      });
      await deleteAccount(u.userId);
      await this.load();
    },
    openMigration(u) {
      this.migration = { user: u, targetSlotId: "", migrateData: false };
      this.migrationDialog = true;
    },
    async preflight() {
      await preflightAccountMigration({
        userId: this.migration.user.userId,
        targetSlotId: this.migration.targetSlotId,
        migrateData: this.migration.migrateData,
      });
      this.$message.success("迁移预检通过");
      this.migrationDialog = false;
    },
    async regenerate(u) {
      const r = await regenerateCompetitionPassword(u.userId);
      const item = this.credential(u.userId);
      if (item) Object.assign(item, r.data);
      else this.credentials.push(r.data);
    },
    async openDemoPlacement() {
      const r = await getAdminDemoPlacement();
      const data = r.data || {};
      this.demoPlacementCurrent = (data.current || [])[0] || null;
      this.demoPlacementSlotId = this.demoPlacementCurrent
        ? this.demoPlacementCurrent.slotId
        : "";
      this.demoPlacementSlots = (data.slots || []).filter(
        (s) =>
          !s.userId ||
          (this.demoPlacementCurrent &&
            s.slotId === this.demoPlacementCurrent.slotId)
      );
      this.demoPlacementDialog = true;
    },
    async saveDemoPlacement() {
      if (!this.demoPlacementSlotId)
        return this.$message.warning("请选择教学槽位");
      this.saving = true;
      try {
        await bindAdminDemoPlacement(this.demoPlacementSlotId);
        this.demoPlacementDialog = false;
        this.$message.success("教学槽位已绑定");
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.summary > div {
  padding: 16px;
  background: #fff;
  border: 1px solid var(--ui-border);
  border-radius: 6px;
}
.summary strong {
  display: block;
  font-size: 24px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.toolbar > .el-input {
  max-width: 320px;
}
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 36px;
  gap: 8px;
  margin-bottom: 8px;
}
.danger {
  color: #f56c6c;
}
small {
  display: block;
  color: #8492a6;
}
</style>
