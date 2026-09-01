<template>
  <section class="management-home module-page">
    <header class="page-heading module-heading">
      <div class="heading-actions">
        <el-button icon="el-icon-refresh" :loading="loading" @click="refreshAll"
          >刷新数据</el-button
        >
        <el-button
          :type="platformMode === 'COMPETITION' ? 'warning' : 'primary'"
          icon="el-icon-trophy"
          :loading="switchingMode"
          @click="togglePlatformMode"
          >{{
            platformMode === "COMPETITION" ? "退出比赛模式" : "进入比赛模式"
          }}</el-button
        >
        <el-button
          type="primary"
          icon="el-icon-video-play"
          @click="openClassDialog"
          >一键上课</el-button
        >
        <el-button
          type="warning"
          icon="el-icon-video-pause"
          :loading="classStopping"
          @click="stopAllClasses"
          >一键下课</el-button
        >
      </div>
    </header>

    <section class="status-strip">
      <div
        class="mode-status"
        :class="{ competition: platformMode === 'COMPETITION' }"
      >
        <small>当前平台模式</small
        ><strong>{{
          platformMode === "COMPETITION" ? "比赛模式" : "实训模式"
        }}</strong
        ><span>{{
          platformMode === "COMPETITION"
            ? "普通用户登录后进入比赛界面"
            : "普通用户登录后进入实训界面"
        }}</span>
      </div>
      <div>
        <small>平台总用户</small><strong>{{ totalUsers }}</strong
        ><span>全部启用账号</span>
      </div>
      <div>
        <small>当前在线</small
        ><strong class="online-number">{{ onlineUsers }}</strong
        ><span>最近 5 分钟活跃</span>
      </div>
      <div>
        <small>处理服务器</small
        ><strong>{{ onlineAgents }}/{{ totalAgents }}</strong
        ><span>在线 / 已添加</span>
      </div>
    </section>

    <div class="business-layout">
      <section>
        <header class="section-heading">
          <div>
            <h2>课程与资源</h2>
            <p>
              {{ courseCount }} 门课程 · {{ resourceCount }} 项资源 ·
              {{ environmentCount }} 个实训环境
            </p>
          </div>
        </header>
        <div class="category-grid">
          <button
            v-for="item in categories"
            :key="item.key"
            type="button"
            @click="$router.push(item.route)"
          >
            <span :class="item.tone"><i :class="item.icon" /></span>
            <div>
              <strong>{{ item.value }}</strong
              ><small>{{ item.label }}</small
              ><em>{{ item.note }}</em>
            </div>
          </button>
        </div>
      </section>
      <aside>
        <header class="section-heading">
          <div>
            <h2>快捷入口</h2>
            <p>常用业务功能</p>
          </div>
        </header>
        <nav class="quick-actions">
          <button
            v-for="item in quickActions"
            :key="item.route"
            @click="$router.push(item.route)"
          >
            <i :class="item.icon" /><span>{{ item.label }}</span
            ><b class="el-icon-arrow-right" />
          </button>
        </nav>
      </aside>
    </div>

    <section class="monitor-section">
      <header class="section-heading">
        <div>
          <h2>服务器资源监控</h2>
          <p>每 5 秒刷新 Agent 最新上报数据</p>
        </div>
        <span>更新时间 {{ snapshotTime }}</span>
      </header>
      <div class="server-table-wrap">
        <el-table
          :data="agentResources"
          size="small"
          empty-text="暂无已添加的处理服务器"
        >
          <el-table-column label="服务器" min-width="180"
            ><template slot-scope="s"
              ><div class="server-name">
                <span class="state-dot" :class="{ online: s.row.online }" />
                <div>
                  <strong>{{ s.row.displayName || s.row.agentId }}</strong
                  ><small>{{ s.row.primaryIp || s.row.agentId }}</small>
                </div>
              </div></template
            ></el-table-column
          >
          <el-table-column label="状态" width="80"
            ><template slot-scope="s"
              ><el-tag size="mini" :type="s.row.online ? 'success' : 'info'">{{
                s.row.online ? "在线" : "离线"
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="CPU" width="105"
            ><template slot-scope="s"
              ><MetricBar :value="s.row.cpuPercent" /></template
          ></el-table-column>
          <el-table-column label="GPU" width="105"
            ><template slot-scope="s"
              ><MetricBar :value="s.row.gpuPercent" /></template
          ></el-table-column>
          <el-table-column label="内存" width="105"
            ><template slot-scope="s"
              ><MetricBar :value="s.row.memoryPercent" /></template
          ></el-table-column>
          <el-table-column label="磁盘" width="105"
            ><template slot-scope="s"
              ><MetricBar :value="s.row.diskPercent" /></template
          ></el-table-column>
          <el-table-column label="网络下载" width="105"
            ><template slot-scope="s">{{
              formatRate(s.row.networkReceiveBytesPerSecond)
            }}</template></el-table-column
          >
          <el-table-column label="网络上传" width="105"
            ><template slot-scope="s">{{
              formatRate(s.row.networkSendBytesPerSecond)
            }}</template></el-table-column
          >
          <el-table-column label="容器" width="70"
            ><template slot-scope="s">{{
              s.row.runningContainerCount == null
                ? "--"
                : s.row.runningContainerCount
            }}</template></el-table-column
          >
          <el-table-column label="最后上报" width="150"
            ><template slot-scope="s">{{
              formatTime(s.row.lastSeenAt)
            }}</template></el-table-column
          >
        </el-table>
      </div>
    </section>

    <el-dialog title="一键上课" :visible.sync="classDialog" width="460px"
      ><el-form label-width="90px"
        ><el-form-item label="课程/环境"
          ><el-select
            v-model="classForm.target"
            filterable
            placeholder="选择课程或独立实训环境"
            style="width: 100%"
            ><el-option-group label="课程"
              ><el-option
                v-for="item in courses"
                :key="`COURSE:${course(item).courseId}`"
                :label="course(item).name"
                :value="`COURSE:${course(item).courseId}`" /></el-option-group
            ><el-option-group label="独立实训环境"
              ><el-option
                v-for="item in independentClassEnvironments"
                :key="`INDEPENDENT:${item.environmentName}`"
                :label="`${item.environmentName}（${item.userCount} 个用户）`"
                :value="`INDEPENDENT:${item.environmentName}`" /></el-option-group></el-select></el-form-item
        ><el-form-item label="代码工具"
          ><el-select v-model="classForm.editorTool" style="width: 100%"
            ><el-option label="VS Code" value="VSCODE" /><el-option
              label="Jupyter Notebook"
              value="JUPYTER" /></el-select></el-form-item
        ><el-form-item label="课堂签到"><el-checkbox v-model="classForm.attendance">开启本轮签到</el-checkbox></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="classDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="classStarting"
          @click="startSelectedClass"
          >确认上课</el-button
        ></span
      ></el-dialog
    >
    <section v-if="attendance && attendance.active" class="monitor-section">
      <header class="section-heading"><div><h2>当前签到</h2><p>已签到 {{ attendance.checked || 0 }} / {{ attendance.total || 0 }}</p></div><el-button icon="el-icon-download" @click="exportAttendance">导出签到表</el-button></header>
      <div class="module-table-wrap"><el-table :data="attendance.rows || []" size="small"><el-table-column prop="userName" label="用户名"/><el-table-column v-if="attendanceHasName" prop="name" label="姓名"/><el-table-column label="状态"><template slot-scope="s"><el-tag :type="s.row.checkedIn?'success':'info'">{{s.row.checkedIn?'已签到':'未签到'}}</el-tag></template></el-table-column></el-table></div>
    </section>
  </section>
</template>

<script>
import Vue from "vue";
import { getDashboardOverview } from "@/api/DashboardOverview";
import { getPlatformMode, changePlatformMode } from "@/api/PlatformMode";
import { listAdminCourses } from "@/api/Courses";
import {
  listAdminTrainingEnvironments,
  startClassTrainingCourse,
  startClassTrainingIndependent,
  getClassTrainingStatus,
  stopAdminTrainingEnvironment,
  stopClassTrainingEnvironment,
} from "@/api/TrainingEnvironments";
import { getAttendanceStatus, startAttendance, endAttendance } from "@/api/Attendance";
import * as XLSX from "xlsx";
import { saveAs } from "file-saver";
import { getUserInfo } from "@/utils/auth";

const MetricBar = Vue.component("dashboard-metric-bar", {
  props: { value: { type: [Number, String], default: null } },
  computed: {
    percent() {
      return this.value == null
        ? null
        : Math.max(0, Math.min(100, Number(this.value)));
    },
  },
  template:
    '<div class="metric-cell"><span>{{ percent == null ? "--" : percent.toFixed(0) + "%" }}</span><i><b v-if="percent != null" :style="{ width: percent + "%" }" /></i></div>',
});

export default {
  components: { MetricBar },
  data: () => ({
    loading: false,
    refreshing: false,
    switchingMode: false,
    snapshot: null,
    platformMode:
      getUserInfo().platformMode === "COMPETITION"
        ? "COMPETITION"
        : "TRAINING",
    courses: [],
    classEnvironments: [],
    classDialog: false,
    classStarting: false,
    classStopping: false,
    classForm: { target: "", editorTool: "VSCODE", attendance: false },
    attendance: null,
    classStatus: null,
    lastClassWaitingCount: null,
    classReadyNotified: false,
    refreshTimer: null,
  }),
  computed: {
    agents() {
      return (this.snapshot && this.snapshot.agents) || {};
    },
    environments() {
      return (this.snapshot && this.snapshot.environments) || {};
    },
    totalAgents() {
      return Number(this.agents.enabled || 0);
    },
    onlineAgents() {
      return Number(this.agents.online || 0);
    },
    totalUsers() {
      return Number((this.snapshot && this.snapshot.totalUsers) || 0);
    },
    onlineUsers() {
      return Number((this.snapshot && this.snapshot.onlineUsers) || 0);
    },
    agentResources() {
      return (this.snapshot && this.snapshot.agentResources) || [];
    },
    snapshotTime() {
      return this.formatTime(this.snapshot && this.snapshot.snapshotAt);
    },
    courseCount() {
      return this.courses.length;
    },
    resourceCount() {
      return this.courses.reduce(
        (sum, item) => sum + (item.resources || []).length,
        0
      );
    },
    environmentCount() {
      return (
        Number(this.environments.running || 0) +
        Number(this.environments.transitional || 0)
      );
    },
    categories() {
      const resources = this.courses.reduce(
        (all, item) => all.concat(item.resources || []),
        []
      );
      const count = (type) =>
        resources.filter((item) => item.resourceType === type).length;
      return [
        {
          key: "ebook",
          label: "电子书",
          value: count("EBOOK"),
          note: "在线预览",
          icon: "el-icon-document",
          tone: "coral",
          route: "/management/resources",
        },
        {
          key: "video",
          label: "视频课件",
          value: count("VIDEO"),
          note: "在线播放",
          icon: "el-icon-video-play",
          tone: "blue",
          route: "/management/resources",
        },
        {
          key: "ppt",
          label: "PPT 课件",
          value: count("PPT"),
          note: "在线查看",
          icon: "el-icon-data-board",
          tone: "green",
          route: "/management/resources",
        },
        {
          key: "archive",
          label: "课程数据",
          value: count("ARCHIVE"),
          note: "发送到实训",
          icon: "el-icon-folder",
          tone: "purple",
          route: "/management/resources",
        },
      ];
    },
    quickActions() {
      return [
        {
          label: "课程平台",
          route: "/management/demo/courses",
          icon: "el-icon-reading",
        },
        {
          label: "资源中心",
          route: "/management/demo/resources",
          icon: "el-icon-folder-opened",
        },
        {
          label: "实训环境",
          route: "/management/demo/training",
          icon: "el-icon-monitor",
        },
        {
          label: "模型验证",
          route: "/management/demo/validation",
          icon: "el-icon-cpu",
        },
      ];
    },
    independentClassEnvironments() {
      const groups = {};
      this.classEnvironments
        .filter((item) => item.environmentType === "COURSE" && !item.courseId)
        .forEach((item) => {
          const name = item.environmentName || "默认实训环境";
          if (!groups[name])
            groups[name] = { environmentName: name, userCount: 0 };
          groups[name].userCount += 1;
        });
      return Object.values(groups);
    },
    attendanceHasName() { return Boolean(this.attendance && (this.attendance.rows || []).some(x => x.name)); },
  },
  mounted() {
    this.refreshAll().then(() => {
      if (this.platformMode === "TRAINING") this.checkDefaultEnvironment();
    });
    this.refreshTimer = setInterval(this.refreshAll, 5000);
  },
  beforeDestroy() {
    clearInterval(this.refreshTimer);
  },
  methods: {
    course(value) {
      return value && value.course ? value.course : value;
    },
    async checkDefaultEnvironment() {
      try {
        const result = await listAdminTrainingEnvironments();
        const exists = (result.data || []).some(
          (item) => item.environmentType === "COURSE" && !item.courseId
        );
        if (!exists) {
          await this.$confirm(
            "平台尚未创建默认实训环境。默认环境用于未上课时普通用户自由打开图像标注或代码编辑工具。",
            "需要创建默认实训环境",
            {
              confirmButtonText: "前往创建",
              cancelButtonText: "稍后处理",
              type: "warning",
            }
          );
          this.$router.push({
            path: "/management/training",
            query: { createDefault: "1" },
          });
        }
      } catch (error) {
        if (error !== "cancel" && error !== "close") throw error;
      }
    },
    async refreshAll() {
      if (this.refreshing) return;
      this.refreshing = true;
      this.loading = !this.snapshot;
      try {
        const mode = await getPlatformMode();
        this.syncPlatformMode(mode.data || {});
        const [overview, courses, classStatus, attendance] = await Promise.all([
          getDashboardOverview(),
          listAdminCourses(),
          this.platformMode === "TRAINING"
            ? getClassTrainingStatus()
            : Promise.resolve({ data: null }),
          getAttendanceStatus(),
        ]);
        this.snapshot = overview.data || {};
        this.courses = courses.data || [];
        this.updateClassStatus(classStatus.data || null);
        this.attendance = attendance.data || null;
      } finally {
        this.refreshing = false;
        this.loading = false;
      }
    },
    syncPlatformMode(value) {
      const mode = value.mode === "COMPETITION" ? "COMPETITION" : "TRAINING";
      this.platformMode = mode;
      this.$store.commit("Match/SET_USER_INFO", {
        ...getUserInfo(),
        platformMode: mode,
        modeGeneration: Number(value.generation || 0),
      });
    },
    async togglePlatformMode() {
      const target =
        this.platformMode === "COMPETITION" ? "TRAINING" : "COMPETITION";
      await this.$confirm(
        `确认${
          target === "COMPETITION" ? "进入" : "退出"
        }比赛模式？普通用户现有登录会失效。`,
        "切换平台模式",
        { type: "warning" }
      );
      this.switchingMode = true;
      try {
        const result = await changePlatformMode(target);
        this.syncPlatformMode(result.data || {});
        this.$message.success("平台模式已切换");
        await this.refreshAll();
      } finally {
        this.switchingMode = false;
      }
    },
    formatRate(bytes) {
      if (bytes == null) return "--";
      const value = Number(bytes);
      if (value >= 1073741824) return `${(value / 1073741824).toFixed(1)} GB/s`;
      if (value >= 1048576) return `${(value / 1048576).toFixed(1)} MB/s`;
      if (value >= 1024) return `${(value / 1024).toFixed(1)} KB/s`;
      return `${Math.round(value)} B/s`;
    },
    formatTime(value) {
      if (!value) return "--";
      const date = new Date(value);
      return Number.isNaN(date.getTime())
        ? "--"
        : date.toLocaleString("zh-CN", { hour12: false });
    },
    environmentLabel(item) {
      return `${item.environmentName || "实训环境"} · 用户 ${item.userId} · ${
        item.actualState || "未知状态"
      }`;
    },
    async openClassDialog() {
      const result = await listAdminTrainingEnvironments();
      this.classEnvironments = (result.data || []).filter(
        (item) => item.userId != null
      );
      this.classForm = { target: "", editorTool: "VSCODE", attendance: false };
      this.classDialog = true;
    },
    async startSelectedClass() {
      if (!this.classForm.target)
        return this.$message.warning("请选择课程或独立实训环境");
      this.classStarting = true;
      try {
        const separator = this.classForm.target.indexOf(":");
        const type = this.classForm.target.slice(0, separator);
        const value = this.classForm.target.slice(separator + 1);
        await this.submitClassStart(type, value, false);
        if (this.classForm.attendance) await startAttendance();
        this.classDialog = false;
        this.classReadyNotified = false;
        this.lastClassWaitingCount = null;
        this.$message.success("已提交上课任务，普通用户已锁定到指定环境");
      } catch (error) {
        const data =
          error &&
          error.response &&
          error.response.data &&
          error.response.data.data;
        if (!data || data.reasonCode !== "CLASS_SERVERS_OFFLINE") throw error;
        const lines = (data.offlineServers || []).map(
          (item) =>
            `${item.displayName || item.agentId}${
              item.primaryIp ? `（${item.primaryIp}）` : ""
            }：${(item.userNames || []).join("、")}`
        );
        await this.$confirm(
          `以下服务器不在线：\n${lines.join(
            "\n"
          )}\n\n忽略后在线用户立即上课，离线服务器上线后将自动启动当前课堂环境。`,
          "部分服务器离线",
          {
            type: "warning",
            confirmButtonText: "忽略并继续",
            cancelButtonText: "取消",
          }
        );
        const separator = this.classForm.target.indexOf(":");
        const type = this.classForm.target.slice(0, separator);
        const value = this.classForm.target.slice(separator + 1);
        await this.submitClassStart(type, value, true);
        if (this.classForm.attendance) await startAttendance();
        this.classDialog = false;
        this.classReadyNotified = false;
        this.lastClassWaitingCount = null;
        this.$message.warning(
          "已忽略离线服务器，相关命令将在服务器上线后自动执行"
        );
      } finally {
        this.classStarting = false;
      }
    },
    submitClassStart(type, value, ignoreOffline) {
      return type === "COURSE"
        ? startClassTrainingCourse(
            value,
            this.classForm.editorTool,
            ignoreOffline
          )
        : startClassTrainingIndependent(
            value,
            this.classForm.editorTool,
            ignoreOffline
          );
    },
    updateClassStatus(status) {
      if (!status || !status.active) {
        this.classStatus = status;
        this.lastClassWaitingCount = null;
        this.classReadyNotified = false;
        return;
      }
      const waiting = Number(status.waitingCount || 0);
      if (
        this.lastClassWaitingCount != null &&
        waiting < this.lastClassWaitingCount &&
        waiting > 0
      )
        this.$notify({
          title: "课堂环境已补充启动",
          message: `当前仍有 ${waiting} 个用户环境等待服务器上线`,
          type: "success",
        });
      if (status.allReady && !this.classReadyNotified) {
        this.$notify({
          title: "课堂环境已全部就绪",
          message: `${status.runningCount} 个用户环境均可使用`,
          type: "success",
        });
        this.classReadyNotified = true;
      }
      this.lastClassWaitingCount = waiting;
      this.classStatus = status;
    },
    async stopAllClasses() {
      await this.$confirm("确认下课并停止全部运行中的实训环境？", "一键下课", {
        type: "warning",
      });
      this.classStopping = true;
      try {
        await stopClassTrainingEnvironment();
        await endAttendance();
        const result = await listAdminTrainingEnvironments();
        const environments = (result.data || []).filter(
          (item) => item.actualState === "RUNNING"
        );
        if (!environments.length)
          return this.$message.info("已结束课堂，当前没有运行中的实训环境");
        await Promise.all(
          environments.map((item) =>
            stopAdminTrainingEnvironment(item.environmentId)
          )
        );
        this.$message.success(
          `课堂已结束，已提交 ${environments.length} 个环境的停止任务`
        );
      } finally {
        this.classStopping = false;
      }
    },
    exportAttendance() {
      const rows=(this.attendance.rows||[]).map(x=>{const row={用户名:x.userName};if(this.attendanceHasName)row.姓名=x.name||'';row.签到状态=x.checkedIn?'已签到':'未签到';return row});
      const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,XLSX.utils.json_to_sheet(rows),'签到表');const data=XLSX.write(book,{bookType:'xlsx',type:'array'});saveAs(new Blob([data]),`签到表-${new Date().toISOString().slice(0,10)}.xlsx`);
    },
  },
};
</script>

<style scoped>
.management-home {
  width: 100%;
  color: var(--ui-text);
}
.page-heading,
.heading-actions,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.page-heading {
  min-height: 42px;
  margin-bottom: 12px;
  align-items: center;
  padding: 0 4px;
  justify-content: flex-end;
}
.section-heading h2 {
  margin: 0;
}
.section-heading p {
  margin: 5px 0 0;
  color: var(--ui-muted);
  font-size: 12px;
}
.heading-actions {
  align-items: center;
}
.status-strip {
  display: grid;
  grid-template-columns: 1.5fr repeat(3, 1fr);
  gap: 1px;
  margin-bottom: 18px;
  overflow: hidden;
  background: var(--ui-border);
  border: 1px solid var(--ui-border);
  border-radius: 8px;
}
.status-strip > div {
  min-height: 108px;
  padding: 18px;
  background: #fff;
}
.status-strip small,
.status-strip span {
  display: block;
  color: var(--ui-muted);
  font-size: 12px;
}
.status-strip strong {
  display: block;
  margin: 8px 0 6px;
  font-size: 26px;
}
.status-strip .mode-status {
  color: #fff;
  background: #6078ed;
}
.status-strip .mode-status.competition {
  background: #e85d61;
}
.status-strip .mode-status small,
.status-strip .mode-status span {
  color: rgba(255, 255, 255, 0.82);
}
.online-number {
  color: #28a773;
}
.monitor-section,
.business-layout > section,
.business-layout > aside {
  margin-bottom: 18px;
  padding: 18px;
  background: #fff;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
}
.section-heading {
  align-items: center;
  margin-bottom: 14px;
}
.section-heading h2 {
  font-size: 18px;
}
.section-heading > span {
  color: var(--ui-muted);
  font-size: 12px;
}
.server-table-wrap {
  overflow: hidden;
  border: 1px solid var(--ui-border);
}
.server-name {
  display: flex;
  align-items: center;
  gap: 9px;
}
.server-name strong,
.server-name small {
  display: block;
}
.server-name small {
  margin-top: 3px;
  color: var(--ui-muted);
  font-size: 11px;
}
.state-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  background: #aeb8c2;
  border-radius: 50%;
}
.state-dot.online {
  background: #27ad72;
  box-shadow: 0 0 0 3px #dcf5e9;
}
.business-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 18px;
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.category-grid button {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
  padding: 14px;
  text-align: left;
  background: #f8f9fc;
  border: 1px solid #e9edf3;
  border-radius: 7px;
  cursor: pointer;
}
.category-grid button > span {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  color: #fff;
  border-radius: 50%;
}
.category-grid .coral {
  background: #ff7279;
}
.category-grid .blue {
  background: #4bb7ee;
}
.category-grid .green {
  background: #39c9aa;
}
.category-grid .purple {
  background: #d78bee;
}
.category-grid strong,
.category-grid small,
.category-grid em {
  display: block;
}
.category-grid strong {
  font-size: 19px;
}
.category-grid small {
  font-size: 12px;
}
.category-grid em {
  margin-top: 2px;
  color: var(--ui-muted);
  font-size: 10px;
  font-style: normal;
}
.quick-actions button {
  display: grid;
  grid-template-columns: 26px 1fr auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 0;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  cursor: pointer;
}
.quick-actions button:last-child {
  border-bottom: 0;
}
.quick-actions i {
  color: var(--ui-primary);
  font-size: 17px;
}
.quick-actions b {
  color: var(--ui-muted);
}
::v-deep .metric-cell {
  font-size: 11px;
}
::v-deep .metric-cell > span {
  display: block;
  margin-bottom: 4px;
}
::v-deep .metric-cell > i {
  display: block;
  width: 100%;
  height: 4px;
  overflow: hidden;
  background: #e7ebf1;
  border-radius: 3px;
}
::v-deep .metric-cell > i > b {
  display: block;
  height: 100%;
  background: #5c87ea;
  border-radius: 3px;
}
@media (max-width: 1100px) {
  .status-strip {
    grid-template-columns: repeat(2, 1fr);
  }
  .business-layout {
    grid-template-columns: 1fr;
  }
  .category-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 700px) {
  .page-heading,
  .heading-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .heading-actions .el-button {
    margin-left: 0;
  }
  .status-strip,
  .category-grid {
    grid-template-columns: 1fr;
  }
}
</style>
