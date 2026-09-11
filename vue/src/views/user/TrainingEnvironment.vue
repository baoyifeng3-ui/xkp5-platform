<template>
  <section class="module-page module-composed-page">
    <header class="module-heading">
      <div>
        <h1>实训环境</h1>
        <p>课程环境会在进入时自动启动，启动后选择工具或发送课程资源。</p>
      </div>
      <el-button
        icon="el-icon-cpu"
        @click="$router.push(adminDemo ? '/management/demo/validation' : '/training-validation')"
        >模型验证</el-button
      >
    </header>
    <div class="environment-control">
      <label>{{ adminDemo ? "演示环境" : "实训环境" }}</label>
      <el-select
        v-model="adminDemoEnvironmentId"
        filterable
        placeholder="选择实训环境"
        :disabled="environmentStarting || classLocked"
      >
        <el-option
          v-for="item in environments"
          :key="item.environmentId"
          :value="item.environmentId"
          :label="environmentLabel(item)"
        /> </el-select
      ><span v-if="currentEnvironment"
        >状态：<el-tag
          size="mini"
          :type="tagType(currentEnvironment.actualState)"
          >{{ stateText(currentEnvironment.actualState) }}</el-tag
        ></span
      ><span
        >课程：{{
          currentCourse ? currentCourse.course.name : "独立实训"
        }}</span
      >
      <div class="tool-switch">
        <el-button
          v-if="currentEnvironment && currentEnvironment.annotationUrl"
          size="mini"
          :disabled="environmentStarting || openingTool"
          @click="openTool('ANNOTATION')"
          >图像标注</el-button
        ><el-button
          v-if="
            currentEnvironment &&
            currentEnvironment.editorUrl &&
            (!currentEnvironment.editorTool ||
              currentEnvironment.editorTool === 'VSCODE')
          "
          size="mini"
          :disabled="environmentStarting || openingTool"
          @click="openTool('VSCODE')"
          >VS Code</el-button
        ><el-button
          v-if="
            currentEnvironment &&
            currentEnvironment.jupyterUrl &&
            (!currentEnvironment.editorTool ||
              currentEnvironment.editorTool === 'JUPYTER')
          "
          size="mini"
          :disabled="environmentStarting || openingTool"
          @click="openTool('JUPYTER')"
          >Jupyter</el-button
        >
      </div>
    </div>
    <section v-if="environmentStarting" class="standalone-start-progress">
      <i class="el-icon-loading" />
      <strong>实训环境正在启动</strong>
      <span>{{ preparationText }}</span>
      <span>已等待 {{ elapsedSeconds }} 秒，请勿重复启动</span>
    </section>
    <ParticipantPreviewNotice v-if="previewOnly" />
    <el-alert v-if="currentEnvironment && ['ERROR','DEGRADED'].includes(currentEnvironment.actualState)" type="error" :closable="false" show-icon :title="currentEnvironment.resultMessage || '环境准备失败，请重试或联系老师'" />
    <el-empty
      v-if="!loading && !currentEnvironment"
      description="暂无已分配环境"
    />
    <section v-if="embeddedUrl" ref="workspace" class="embedded-workspace">
      <header>
        <strong>{{ embeddedTitle }}</strong>
        <div>
          <el-button size="mini" icon="el-icon-refresh" @click="refreshEmbedded"
            >刷新</el-button
          >
          <el-button
            size="mini"
            icon="el-icon-full-screen"
            @click="toggleFullscreen"
            >{{ fullscreen ? "退出全屏" : "全屏" }}</el-button
          >
          <el-button
            size="mini"
            icon="el-icon-data-analysis"
            @click="
              $router.push(
                adminDemo
                  ? '/management/demo/validation'
                  : '/training-validation'
              )
            "
            >模型验证</el-button
          >
          <el-button
            size="mini"
            icon="el-icon-edit"
            :disabled="!selectedEnvironmentCourseId"
            @click="reportVisible = true"
            >实训报告</el-button
          >
          <el-button
            size="mini"
            icon="el-icon-upload2"
            @click="$refs.dataset.open(currentEnvironment)"
            >数据集上传</el-button
          >
          <el-button
            size="mini"
            icon="el-icon-setting"
            @click="$refs.modelDeployment.open(currentEnvironment)"
            >模型部署</el-button
          >
          <el-button size="mini" icon="el-icon-download" @click="downloadCodeServerRootCa">下载平台根证书</el-button>
        </div>
      </header>
      <el-alert v-if="embeddedTitle === 'VS Code'" type="info" :closable="false" title="首次使用代码环境需信任当前平台根证书；若出现空白或证书错误，请下载根证书交由管理员安装后重启浏览器。" />
      <div class="workspace-split">
        <aside v-if="currentCourse && !fullscreen">
          <strong>{{ currentCourse.course.name }}</strong
          ><button
            v-for="r in currentCourse.resources"
            :key="r.resourceId"
            @click="send(r, currentEnvironment)"
          >
            <i class="el-icon-document" />{{ r.name }}
          </button>
        </aside>
        <EnvironmentToolFrame
          :key="embeddedKey"
          :src="embeddedUrl"
          :title="embeddedTitle"
          allow="clipboard-read; clipboard-write"
        />
      </div>
    </section>
    <CourseReportEditor
      :visible.sync="reportVisible"
      :course-id="selectedEnvironmentCourseId"
      :user-name="getUserName()"
    />
    <DatasetUploadDialog ref="dataset" />
    <ModelDeploymentDialog ref="modelDeployment" />
  </section>
</template>
<script>
import EnvironmentToolFrame from '@/components/training/EnvironmentToolFrame.vue';
import { getClassPolicy } from "@/utils/auth";
import {
  adminParticipantPreviewTrainingEnvironmentsApi,
  listUserTrainingEnvironments,
  startUserTrainingEnvironment,
  listAdminTrainingEnvironments,
  startAdminTrainingEnvironment,
} from "@/api/TrainingEnvironments";
import {
  listUserCourses,
  listAdminCourses,
  deliverCourseResource,
  deliverCourseResourceForUser,
} from "@/api/Courses";
import ParticipantPreviewNotice from "@/components/ParticipantPreviewNotice.vue";
import { getRole, getUserInfo, getUserName } from "@/utils/auth";
import CourseReportEditor from "@/components/course/CourseReportEditor.vue";
import DatasetUploadDialog from "@/components/training/DatasetUploadDialog.vue";
import ModelDeploymentDialog from "@/components/training/ModelDeploymentDialog.vue";
import { downloadCodeServerRootCa as downloadCodeServerRootCaFile } from "@/api/TrainingModels";
const { isPreviewRoute } = require("@/services/participantPreview");
export default {
  props: { adminDemo: { type: Boolean, default: false } },
  components: { EnvironmentToolFrame, ParticipantPreviewNotice, CourseReportEditor, DatasetUploadDialog, ModelDeploymentDialog },
  data: () => ({
    loading: false,
    environments: [],
    courses: [],
    started: false,
    sendingId: "",
    adminDemoEnvironmentId: "",
    embeddedUrl: "",
    embeddedTitle: "",
    embeddedKey: 0,
    reportVisible: false,
    fullscreen: false,
    startProgress: 0,
    waitingSince: 0, nowTick: Date.now(), pollTimer: null, refreshingEnvironment: false, destroyed: false, openingTool: false,
  }),
  computed: {
    classLocked() { const policy=getClassPolicy(); return Boolean(policy.active || policy.modeSwitching); },
    elapsedSeconds() { return this.waitingSince ? Math.floor((this.nowTick-this.waitingSince)/1000) : 0; },
    preparationText() { const state=this.currentEnvironment && this.currentEnvironment.actualState; return this.currentEnvironment && this.currentEnvironment.modeSwitching ? "平台模式切换中，等待原环境停止" : ({WAITING_DEPENDENCY:"正在停止原环境",STARTING:"正在启动容器并检查工具服务",CREATING:"正在创建容器",STOPPING:"正在停止环境",RESTORING:"正在重建环境"}[state] || "正在准备环境"); },
    previewOnly() {
      return !this.adminDemo && isPreviewRoute(this.$route, getRole());
    },
    displayedEnvironments() {
      if (!this.adminDemo || !this.adminDemoEnvironmentId)
        return this.environments;
      return this.environments.filter(
        (item) => item.environmentId === this.adminDemoEnvironmentId
      );
    },
    currentEnvironment() {
      return (
        this.environments.find(
          (item) => item.environmentId === this.adminDemoEnvironmentId
        ) ||
        this.environments[0] ||
        null
      );
    },
    environmentStarting() {
      return Boolean(
        this.currentEnvironment && (this.currentEnvironment.modeSwitching ||
          ["STARTING", "CREATING", "WAITING_DEPENDENCY", "STOPPING", "RESTORING"].includes(
            this.currentEnvironment.actualState
          ))
      );
    },
    currentCourse() {
      if (!this.currentEnvironment || !this.currentEnvironment.courseId)
        return null;
      return (
        this.courses.find(
          (item) =>
            String(item.course.courseId) ===
            String(this.currentEnvironment.courseId)
        ) || null
      );
    },
    selectedEnvironmentCourseId() {
      const selected = this.currentEnvironment;
      return selected && selected.courseId ? String(selected.courseId) : "";
    },
  },
  created() {
    this.loadCourses();
    this.load();
    this.pollTimer = setInterval(() => { this.nowTick=Date.now(); this.refreshEnvironmentState().catch(() => {}); }, 2000);
  },
  watch: {
    adminDemoEnvironmentId() {
      this.embeddedUrl = "";
      this.embeddedTitle = "";
      this.reportVisible = false;
      this.startProgress = 0;
    },
  },
  mounted() {
    document.addEventListener("fullscreenchange", this.fullscreenChanged);
  },
  beforeDestroy() {
    this.destroyed = true; clearInterval(this.pollTimer);
    document.removeEventListener("fullscreenchange", this.fullscreenChanged);
  },
  methods: {
    async refreshEnvironmentState() {
      if(this.previewOnly || this.refreshingEnvironment || this.loading || this.destroyed) return;
      this.refreshingEnvironment=true;
      try {
        const response=await (this.adminDemo ? listAdminTrainingEnvironments() : listUserTrainingEnvironments(false));
        const courseId=this.$route.query && this.$route.query.courseId;
        this.environments=(response.data||[]).filter(row => (!this.adminDemo || String(row.userId)===String(getUserInfo().userId)) && (!courseId ? row.environmentType !== 'COMPETITION' && !row.courseId : String(row.courseId)===String(courseId)));
        if(this.currentEnvironment && (this.currentEnvironment.actualState !== 'RUNNING' || this.currentEnvironment.modeSwitching)) this.embeddedUrl='';
        if(this.environmentStarting && !this.waitingSince) this.waitingSince=Date.now();
        if(!this.environmentStarting) this.waitingSince=0;
        if(this.classLocked && this.currentEnvironment && this.currentEnvironment.actualState==='RUNNING' && !this.embeddedUrl && !this.openingTool) await this.openTool(this.currentEnvironment.editorUrl ? (this.currentEnvironment.editorTool||'VSCODE') : 'ANNOTATION');
      } finally {this.refreshingEnvironment=false;}
    },
    async downloadCodeServerRootCa() {
      const response = await downloadCodeServerRootCaFile();
      const url = URL.createObjectURL(new Blob([response.data], { type: "application/x-pem-file" }));
      const link = document.createElement("a");
      link.href = url; link.download = "rootCA.pem"; link.click();
      URL.revokeObjectURL(url);
    },
    async loadCourses() {
      if (this.previewOnly) return;
      const r = await (this.adminDemo ? listAdminCourses() : listUserCourses());
      this.courses = r.data || [];
    },
    resourcesFor(courseId) {
      const item = this.courses.find(
        (c) => String(c.course.courseId) === String(courseId)
      );
      return item ? item.resources || [] : [];
    },
    async send(resource, environment) {
      this.sendingId = resource.resourceId;
      try {
        if (this.adminDemo)
          await deliverCourseResourceForUser(
            resource.resourceId,
            environment.environmentId,
            environment.userId
          );
        else
          await deliverCourseResource(
            resource.resourceId,
            environment.environmentId
          );
        this.$message.success("资源下发任务已提交");
      } finally {
        this.sendingId = "";
      }
    },
    async load() {
      this.loading = true;
      try {
        const call = this.previewOnly
          ? adminParticipantPreviewTrainingEnvironmentsApi
          : this.adminDemo
          ? listAdminTrainingEnvironments
          : listUserTrainingEnvironments;
        const courseId = this.$route.query && this.$route.query.courseId;
        const result = await (call === listUserTrainingEnvironments
          ? call(!courseId)
          : call());
        const all = result.data || [];
        const currentUserId = getUserInfo().userId;
        const scoped = this.adminDemo
          ? all.filter(
              (item) =>
                String(item.userId) === String(currentUserId) &&
                item.environmentType !== "COMPETITION"
            )
          : all;
        this.environments = courseId
          ? scoped.filter((item) => String(item.courseId) === String(courseId))
          : scoped;
        if (!this.adminDemoEnvironmentId && this.environments.length) {
          this.adminDemoEnvironmentId = this.environments[0].environmentId;
        }
        if (!this.previewOnly && courseId && !this.started) {
          const target = this.environments.find(
            (item) => !["RUNNING", "STARTING", "CREATING", "WAITING_DEPENDENCY"].includes(item.actualState)
          );
          if (target && !target.modeSwitching && !this.classLocked) {
            await (this.adminDemo
              ? startAdminTrainingEnvironment(target.environmentId)
              : startUserTrainingEnvironment(target.environmentId));
            this.started = true;
            this.$message.success("已提交当前课程实训环境启动任务");
            const refreshed = await (call === listUserTrainingEnvironments
              ? call(false)
              : call());
            const refreshedRows = refreshed.data || [];
            this.environments = refreshedRows
              .filter(
                (item) =>
                  (!this.adminDemo ||
                    (String(item.userId) === String(currentUserId) &&
                      item.environmentType !== "COMPETITION")) &&
                  String(item.courseId) === String(courseId)
            );
          }
        }
        if (this.environmentStarting) {
          this.startProgress = Math.max(this.startProgress, 5);
          await this.waitForEnvironment(this.currentEnvironment.environmentId);
        }
      } finally {
        this.loading = false;
      }
    },
    async waitForEnvironment(environmentId) {
      this.waitingSince = this.waitingSince || Date.now();
      const deadline = Date.now() + 15 * 60 * 1000;
      while (!this.destroyed && Date.now() < deadline) {
        const result = await (this.adminDemo
          ? listAdminTrainingEnvironments()
          : listUserTrainingEnvironments(false));
        const rows = result.data || [];
        const environment = rows.find((item) => item.environmentId === environmentId);
        if (environment) {
          const index = this.environments.findIndex((item) => item.environmentId === environmentId);
          if (index >= 0) this.$set(this.environments, index, environment);
          if (!environment.modeSwitching && ["RUNNING", "ERROR", "DEGRADED", "STOPPED"].includes(environment.actualState) && !["PENDING","RUNNING","WAITING_DEPENDENCY"].includes(environment.operationState)) {
            this.startProgress = environment.actualState === "RUNNING" ? 100 : 0;
            return environment;
          }
        }
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
      return null;
    },
    async openTool(tool) {
      if (this.previewOnly || this.openingTool) return;
      this.openingTool = true;
      try {
      let environment = this.currentEnvironment;
      if (!environment) return;
      if (environment.modeSwitching) return this.$message.info("平台正在切换，请等待准备完成");
      if (environment.actualState !== "RUNNING") {
        this.startProgress = 5;
        if (!["STARTING", "CREATING", "WAITING_DEPENDENCY"].includes(environment.actualState)) {
        await (this.adminDemo
          ? startAdminTrainingEnvironment(environment.environmentId)
          : startUserTrainingEnvironment(environment.environmentId));
        }
        this.$message.info("环境正在启动，请稍候");
        environment = await this.waitForEnvironment(environment.environmentId);
        if (!environment || environment.actualState !== "RUNNING")
          return this.$message.error((environment && environment.resultMessage) || "环境尚未就绪，请查看当前阶段；后台任务不会因页面等待结束而取消");
      }
      if (!this.currentEnvironment || this.currentEnvironment.environmentId !== environment.environmentId) return;
      const url =
        tool === "ANNOTATION"
          ? environment.annotationUrl
          : tool === "JUPYTER"
          ? environment.jupyterUrl
          : environment.editorUrl;
      this.openUrl(url, tool);
      } finally { this.openingTool=false; }
    },
    openUrl(url, tool) {
      if (!url) return this.$message.warning("实训工具尚未就绪");
      if (tool === "VSCODE" && url.startsWith("http://")) url = `https://${url.slice(7)}`;
      this.embeddedUrl = url;
      this.embeddedTitle =
        tool === "JUPYTER"
          ? "Jupyter Notebook"
          : tool === "ANNOTATION"
          ? "图像标注"
          : "VS Code";
      this.embeddedKey += 1;
    },
    refreshEmbedded() {
      this.embeddedKey += 1;
    },
    async toggleFullscreen() {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await this.$refs.workspace.requestFullscreen();
    },
    fullscreenChanged() {
      this.fullscreen = document.fullscreenElement === this.$refs.workspace;
    },
    environmentLabel(item) {
      const owner = item.userName || item.name || (this.adminDemo ? getUserName() : "") || item.userId;
      return `${owner} · ${
        item.environmentName || "实训环境"
      } · 槽位 ${item.slotNumber || "--"}`;
    },
    getUserName,
    stateText(s) {
      return (
        {
          RUNNING: "运行中",
          STOPPED: "已停止",
          STARTING: "启动中",
          STOPPING: "停止中",
          DEGRADED: "部分异常",
          ERROR: "异常",
        }[s] ||
        s ||
        "未知"
      );
    },
    tagType(s) {
      return s === "RUNNING"
        ? "success"
        : s === "ERROR" || s === "DEGRADED"
        ? "danger"
        : "info";
    },
  },
};
</script>
<style scoped>
.module-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.muted {
  color: var(--ui-muted);
  font-size: 12px;
}
.environment-control {
  display: grid;
  grid-template-columns: auto minmax(260px, 520px) auto auto 1fr;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid var(--ui-border);
}
.environment-control .el-select {
  width: 100%;
}
.tool-switch {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}
.standalone-start-progress { display: grid; width: min(520px, calc(100% - 32px)); margin: 80px auto 0; gap: 12px; justify-items: center; color: var(--ui-muted); }
.standalone-start-progress i { color: var(--ui-primary); font-size: 24px; }
.standalone-start-progress strong { color: var(--ui-text); font-size: 16px; }
.standalone-start-progress .el-progress { width: 100%; }
.tool-switch .el-button + .el-button {
  margin-left: 0;
}
.embedded-workspace {
  display: flex;
  min-height: calc(100vh - 250px);
  margin-top: 10px;
  flex-direction: column;
  background: #fff;
}
.embedded-workspace > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.workspace-split {
  display: flex;
  min-height: 0;
  flex: 1;
}
.workspace-split aside {
  width: 32%;
  min-width: 220px;
  max-width: 65%;
  padding: 12px;
  overflow: auto;
  resize: horizontal;
  border: 1px solid var(--ui-border);
  border-right: 0;
}
.workspace-split aside strong,
.workspace-split aside button {
  display: block;
  width: 100%;
  text-align: left;
}
.workspace-split aside button {
  padding: 10px 4px;
  color: var(--ui-text);
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--ui-border);
  cursor: pointer;
}
.workspace-split aside i {
  margin-right: 8px;
}
.embedded-workspace iframe {
  display: block;
  min-width: 0;
  min-height: calc(100vh - 305px);
  flex: 1;
  border: 1px solid var(--ui-border);
  background: #fff;
}
.embedded-workspace:fullscreen {
  width: 100vw;
  height: 100vh;
  padding: 10px;
  box-sizing: border-box;
  background: #fff;
}
.embedded-workspace:fullscreen > header {
  flex: 0 0 42px;
  margin: 0 0 8px;
}
.embedded-workspace:fullscreen iframe {
  min-height: 0;
}
@media (max-width: 650px) {
  .module-heading {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
  .environment-control {
    grid-template-columns: 1fr;
  }
  .tool-switch,
  .embedded-workspace > header {
    align-items: stretch;
    flex-wrap: wrap;
  }
  .workspace-split {
    flex-direction: column;
  }
  .workspace-split aside {
    width: auto;
    max-width: none;
    resize: vertical;
    border-right: 1px solid var(--ui-border);
  }
}
</style>
