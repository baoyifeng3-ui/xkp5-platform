<template>
  <section class="module-page course-platform-page">
    <template v-if="!selectedCourse">
      <header class="course-heading">
        <div>
          <h1>课程学习</h1>
          <p>选择课程，在线学习 PPT、视频和电子书。</p>
        </div>
        <el-button
          icon="el-icon-monitor"
          @click="
            $router.push(
              adminDemo ? '/management/demo/training' : '/training-environment'
            )
          "
          >实训环境</el-button
        >
      </header>
      <section class="course-filters">
        <div class="filter-line">
          <strong>课程类别</strong
          ><button :class="{ active: !type }" @click="type = ''">
            全部 <small>{{ courses.length }}</small></button
          ><button
            v-for="item in typeOptions"
            :key="item.value"
            :class="{ active: type === item.value }"
            @click="type = item.value"
          >
            {{ item.value }} <small>{{ item.count }}</small>
          </button>
        </div>
        <div class="filter-line">
          <strong>课程标签</strong
          ><button :class="{ active: !tag }" @click="tag = ''">全部</button
          ><button
            v-for="item in tagOptions"
            :key="item.value"
            :class="{ active: tag === item.value }"
            @click="tag = item.value"
          >
            {{ item.value }} <small>{{ item.count }}</small>
          </button>
        </div>
        <div class="filter-search">
          <el-input
            v-model="keyword"
            clearable
            prefix-icon="el-icon-search"
            placeholder="搜索课程名称"
          /><el-select v-model="status" placeholder="学习状态"
            ><el-option label="全部课程" value="all" /><el-option
              label="学习中"
              value="learning" /><el-option
              label="已完成"
              value="done" /></el-select
          ><span>共 {{ filtered.length }} 门课程</span
          ><el-button icon="el-icon-refresh-left" @click="resetFilters"
            >重置</el-button
          >
        </div>
      </section>
      <div v-if="filtered.length" class="course-grid">
        <article
          v-for="item in filtered"
          :key="item.course.courseId"
          class="course-card"
        >
          <div class="course-cover">
            <img
              :src="coverUrls[item.course.courseId] || defaultCourseCover"
              :alt="item.course.name"
            /><el-tag
              size="mini"
              :type="courseDone(item) ? 'success' : 'info'"
              >{{ courseDone(item) ? "已完成" : "学习中" }}</el-tag
            >
          </div>
          <div class="course-body">
            <h2>{{ item.course.name }}</h2>
            <p>
              {{ item.course.courseType }} · {{ item.resources.length }} 项内容
            </p>
            <div class="card-tags">
              <el-tag
                v-for="label in courseLabels(item)"
                :key="label"
                size="mini"
                effect="plain"
                >{{ label }}</el-tag
              >
            </div>
            <small>{{ item.course.description || "暂无课程简介" }}</small>
            <div class="progress">
              <el-progress
                :percentage="courseProgress(item)"
                :show-text="false"
                :stroke-width="6"
              /><span>{{ courseProgress(item) }}%</span>
            </div>
            <el-button type="primary" @click="startLearning(item)"
              >开始学习</el-button
            >
          </div>
        </article>
      </div>
      <el-alert
        v-if="loadError"
        type="error"
        :closable="false"
        :title="loadError"
        show-icon
      />
      <el-empty v-else-if="!filtered.length" description="暂无已发布课程" />
    </template>

    <template v-else>
      <header class="course-heading detail-heading">
        <div>
          <el-button
            type="text"
            icon="el-icon-back"
            @click="selectedCourse = null"
            >返回课程列表</el-button
          >
          <h1>{{ selectedCourse.course.name }}</h1>
          <p>
            {{ selectedCourse.course.courseType }} ·
            {{ selectedCourse.course.description || "暂无课程简介" }}
          </p>
        </div>
      </header>
      <CourseLearningWorkspace
        v-if="workspaceVisible"
        :visible="workspaceVisible"
        :training-visible="trainingPaneVisible"
        :environments="courseEnvironments"
        :environment-id.sync="workspaceEnvironmentId"
        :embedded-url="workspaceEmbeddedUrl"
        :embedded-title="workspaceEmbeddedTitle"
        :embedded-key="workspaceEmbeddedKey"
        :busy="workspaceBusy"
        :start-progress="workspaceStartProgress"
        @tool="openWorkspaceTool"
        @refresh="workspaceEmbeddedKey += 1"
        @report="reportVisible = true"
        @close="closeWorkspace"
      >
        <div v-if="activeResource" class="workspace-preview">
          <header>
            <div>
              <strong>{{ activeResource.name }}</strong>
              <small>{{ resourceLabel(activeResource.resourceType) }}</small>
            </div>
            <div class="workspace-resource-actions">
              <el-button size="mini" icon="el-icon-monitor" @click="openTraining">进入实训</el-button>
              <el-button v-if="!adminDemo" size="mini" type="primary" @click="finishResource">完成本资源学习</el-button>
            </div>
          </header>
          <video
            v-if="activeResource.resourceType === 'VIDEO' && previewUrl"
            :src="previewUrl"
            controls
          />
          <iframe
            v-else-if="previewUrl"
            :src="previewUrl"
            title="课程资料预览"
          />
          <div v-else class="workspace-loading">
            <i class="el-icon-loading" /> 正在加载课程资料...
          </div>
          <div class="preview-footer">
            <el-progress :percentage="progress" />
          </div>
        </div>
        <div v-else class="workspace-materials">
          <h2>{{ selectedCourse.course.name }}</h2>
          <p>{{ selectedCourse.course.description || "请选择课程资料" }}</p>
          <button
            v-for="resource in selectedCourse.resources"
            :key="resource.resourceId"
            type="button"
            @click="openResource(resource)"
          >
            <i :class="resourceIcon(resource.resourceType)" />
            <span>{{ resource.name }}</span>
            <small>{{ resourceLabel(resource.resourceType) }}</small>
          </button>
        </div>
        <CourseReportEditor
          slot="overlay"
          floating
          :visible.sync="reportVisible"
          :course-id="selectedCourse && selectedCourse.course.courseId"
          :user-name="reportUserName"
        />
      </CourseLearningWorkspace>

      <template v-else>
      <el-tabs v-model="resourceTab" class="resource-tabs">
        <el-tab-pane label="课程简介" name="INTRODUCTION" /><el-tab-pane
          label="课程大纲"
          name="OUTLINE"
        /><el-tab-pane label="课程目录" name="DIRECTORY" /><el-tab-pane
          label="PPT"
          name="PPT"
        /><el-tab-pane label="视频" name="VIDEO" /><el-tab-pane
          label="数据资源"
          name="ARCHIVE"
        />
      </el-tabs>
      <div
        v-if="resourceTab === 'INTRODUCTION' || resourceTab === 'OUTLINE'"
        class="course-rich-content"
        v-html="
          resourceTab === 'INTRODUCTION'
            ? selectedCourse.course.introductionHtml || '<p>暂无课程简介</p>'
            : selectedCourse.course.outlineHtml || '<p>暂无课程大纲</p>'
        "
      ></div>
      <div v-else class="resource-list">
        <section
          v-if="resourceTab === 'DIRECTORY'"
          v-for="chapter in chapters"
          :key="chapter.chapterId"
          class="chapter-section"
        >
          <header>
            <strong>{{ chapter.chapterName }}</strong
            ><el-button
              v-if="chapter.practiceTool"
              type="text"
              icon="el-icon-monitor"
              @click="openPractice(chapter.practiceTool)"
              >打开实训环境</el-button
            >
          </header>
          <article
            v-for="resource in chapterResources(chapter.chapterId)"
            :key="resource.resourceId"
            class="resource-row chapter-resource"
          >
            <span class="resource-icon"
              ><i :class="resourceIcon(resource.resourceType)"
            /></span>
            <div>
              <strong>{{ resource.name }}</strong
              ><small
                >{{ resourceLabel(resource.resourceType) }} ·
                {{ formatBytes(resource.contentLength) }}</small
              >
            </div>
            <div class="resource-progress">
              <el-progress
                :percentage="progressFor(resource)"
                :show-text="false"
                :stroke-width="5"
              /><span>{{ progressFor(resource) }}%</span>
            </div>
            <el-button
              type="primary"
              plain
              icon="el-icon-view"
              @click="openResource(resource)"
              >在线浏览</el-button
            >
          </article>
        </section>
        <article
          v-for="resource in directResources"
          :key="resource.resourceId"
          class="resource-row"
        >
          <span class="resource-icon"
            ><i :class="resourceIcon(resource.resourceType)"
          /></span>
          <div>
            <strong>{{ resource.name }}</strong
            ><small
              >{{ resourceLabel(resource.resourceType) }} ·
              {{ formatBytes(resource.contentLength) }}</small
            >
          </div>
          <div class="resource-progress">
            <el-progress
              :percentage="progressFor(resource)"
              :show-text="false"
              :stroke-width="5"
            /><span>{{ progressFor(resource) }}%</span>
          </div>
          <el-button
            v-if="resource.resourceType === 'ARCHIVE'"
            type="primary"
            plain
            icon="el-icon-upload2"
            @click="openDelivery(resource)"
            >发送到实训环境</el-button
          >
          <el-button
            v-else
            type="primary"
            plain
            icon="el-icon-view"
            @click="openResource(resource)"
            >在线浏览</el-button
          >
          <el-button
            v-if="resource.resourceType === 'VIDEO' && resource.practiceTool"
            type="text"
            icon="el-icon-monitor"
            @click="openPractice(resource.practiceTool)"
            >打开实训环境</el-button
          >
        </article>
        <el-empty
          v-if="!visibleResources.length"
          description="该分类暂无内容"
        />
      </div>
      </template>
    </template>

    <el-dialog
      title="发送数据资源到实训环境"
      :visible.sync="deliveryDialog"
      width="520px"
    >
      <el-form label-width="90px"
        ><el-form-item label="数据资源">{{
          deliveryResource && deliveryResource.name
        }}</el-form-item
        ><el-form-item label="实训环境"
          ><el-select
            v-model="selectedEnvironmentId"
            style="width: 100%"
            placeholder="选择自己的实训环境"
            ><el-option
              v-for="item in courseEnvironments"
              :key="item.environmentId"
              :value="item.environmentId"
              :label="environmentLabel(item)" /></el-select></el-form-item
      ></el-form>
      <span slot="footer"
        ><el-button @click="deliveryDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="delivering"
          :disabled="!selectedEnvironmentId"
          @click="deliverResource"
          >确认发送</el-button
        ></span
      >
    </el-dialog>
    <CourseReportEditor
      v-if="!workspaceVisible"
      :visible.sync="reportVisible"
      :course-id="selectedCourse && selectedCourse.course.courseId"
      :user-name="reportUserName"
    />
  </section>
</template>

<script>
import { getClassPolicy } from '@/utils/auth';
import {
  listUserCourses,
  listAdminCourses,
  listCourseProgress,
  recordCourseProgress,
  getCourseResourcePreviewUrl,
  getCourseCoverUrl,
  getAdminCourseResourcePreviewUrl,
  getAdminCourseCoverUrl,
  deliverCourseResource,
} from "@/api/Courses";
import {
  listUserTrainingEnvironments,
  listAdminTrainingEnvironments,
  startUserTrainingEnvironment,
  startAdminTrainingEnvironment,
} from "@/api/TrainingEnvironments";
import { listUserCourseChapters } from "@/api/Courses";
import { listCourseChapters } from "@/api/Courses";
import CourseReportEditor from "@/components/course/CourseReportEditor.vue";
import CourseLearningWorkspace from "@/components/course/CourseLearningWorkspace.vue";
import { getUserName } from "@/utils/auth";
import defaultCourseCover from "@/assets/image/bg.png";

export default {
  components: { CourseReportEditor, CourseLearningWorkspace },
  props: { adminDemo: { type: Boolean, default: false } },
  data: () => ({
    courses: [],
    progressRecords: {},
    coverUrls: {},
    environments: [],
    chapters: [],
    keyword: "",
    type: "",
    tag: "",
    status: "all",
    selectedCourse: null,
    resourceTab: "INTRODUCTION",
    preview: false,
    activeResource: null,
    previewUrl: "",
    progress: 0,
    deliveryDialog: false,
    deliveryResource: null,
    selectedEnvironmentId: "",
    delivering: false,
    reportVisible: false,
    workspaceVisible: false,
    trainingPaneVisible: false,
    workspaceEnvironmentId: "",
    workspaceEmbeddedUrl: "",
    workspaceEmbeddedTitle: "",
    workspaceEmbeddedKey: 0,
    workspaceBusy: false,
    workspaceStartProgress: 0,
    environmentTimer: null, destroyed: false, refreshingEnvironment: false,
    defaultCourseCover,
    loadError: "",
  }),
  watch: {
    workspaceEnvironmentId() {
      this.workspaceEmbeddedUrl = "";
      this.workspaceEmbeddedTitle = "";
    },
  },
  computed: {
    reportUserName() {
      return getUserName() || (this.adminDemo ? "管理员" : "用户");
    },
    typeOptions() {
      return this.countOptions(
        this.courses.map((item) => item.course.courseType)
      );
    },
    tagOptions() {
      return this.countOptions(
        this.courses.reduce(
          (all, item) => all.concat(this.courseLabels(item)),
          []
        )
      );
    },
    filtered() {
      return this.courses.filter((item) => {
        const done = this.courseDone(item);
        return (
          (!this.keyword ||
            item.course.name
              .toLowerCase()
              .includes(this.keyword.toLowerCase())) &&
          (!this.type || item.course.courseType === this.type) &&
          (!this.tag || this.courseLabels(item).includes(this.tag)) &&
          (this.status === "all" || (this.status === "done" ? done : !done))
        );
      });
    },
    visibleResources() {
      const resources = this.selectedCourse
        ? this.selectedCourse.resources || []
        : [];
      const type = {
        DIRECTORY: "EBOOK",
        PPT: "PPT",
        VIDEO: "VIDEO",
        ARCHIVE: "ARCHIVE",
      }[this.resourceTab];
      return type
        ? resources.filter((item) => item.resourceType === type)
        : [];
    },
    directResources() {
      return this.resourceTab === "DIRECTORY"
        ? this.visibleResources.filter((item) => !item.chapterId)
        : this.visibleResources;
    },
    courseEnvironments() {
      if (!this.selectedCourse) return this.environments;
      const courseId = this.selectedCourse.course.courseId;
      const matched = this.environments.filter(
        (item) => String(item.courseId) === String(courseId)
      );
      return matched;
    },
  },
  async created() {
    try {
      const [courses, progress, environments] = await Promise.all([
        this.adminDemo ? listAdminCourses() : listUserCourses(),
        this.adminDemo ? Promise.resolve({ data: [] }) : listCourseProgress(),
        this.adminDemo
          ? listAdminTrainingEnvironments()
          : listUserTrainingEnvironments(),
      ]);
      this.courses = Array.isArray(courses.data) ? courses.data : [];
      (progress.data || []).forEach((item) =>
        this.$set(
          this.progressRecords,
          item.resourceId,
          Number(item.percent || 0)
        )
      );
      this.environments = Array.isArray(environments.data)
        ? environments.data
        : [];
      this.refreshChapters();
      await this.loadCovers();
      const requestedCourseId = this.$route.query && this.$route.query.courseId;
      const requestedCourse = requestedCourseId && this.courses.find(
        (item) => String(item.course.courseId) === String(requestedCourseId)
      );
        if (requestedCourse) { this.startLearning(requestedCourse); if (getClassPolicy().active) { await this.openTraining(); await this.openWorkspaceTool(getClassPolicy().editorTool || 'VSCODE'); } }
    } catch (error) {
      this.loadError =
        (error &&
          error.response &&
          error.response.data &&
          error.response.data.msg) ||
        "课程信息加载失败，请刷新重试";
    }
  },
  mounted() { this.environmentTimer=setInterval(() => this.syncWorkspaceEnvironment().catch(() => {}),2000); },
  beforeDestroy() { this.destroyed=true; clearInterval(this.environmentTimer); },
  methods: {
    async syncWorkspaceEnvironment() {
      if(this.refreshingEnvironment || this.destroyed || this.workspaceBusy) return;
      this.refreshingEnvironment=true;
      try {
        await this.reloadEnvironments();
        const row=this.courseEnvironments.find(item=>item.environmentId===this.workspaceEnvironmentId);
        if(row && (row.actualState!=='RUNNING' || row.modeSwitching)) this.workspaceEmbeddedUrl='';
      } finally {this.refreshingEnvironment=false;}
    },
    async refreshChapters() {
      if (!this.selectedCourse) {
        this.chapters = [];
        return;
      }
      try {
        const result = await (this.adminDemo
          ? listCourseChapters
          : listUserCourseChapters)(this.selectedCourse.course.courseId);
        this.chapters = result.data || [];
      } catch (error) {
        this.chapters = [];
      }
    },
    chapterResources(chapterId) {
      return this.visibleResources.filter(
        (item) => item.chapterId === chapterId
      );
    },
    async openPractice(tool) {
      await this.openTraining();
      if (this.trainingPaneVisible)
        await this.openWorkspaceTool(tool === "ANNOTATION" ? tool : "VSCODE");
    },
    countOptions(values) {
      const counts = new Map();
      values
        .filter(Boolean)
        .forEach((value) => counts.set(value, (counts.get(value) || 0) + 1));
      return Array.from(counts.entries())
        .map(([value, count]) => ({ value, count }))
        .sort(
          (a, b) => b.count - a.count || a.value.localeCompare(b.value, "zh-CN")
        );
    },
    courseLabels(item) {
      const labels = {
        PPT: "PPT",
        VIDEO: "视频",
        EBOOK: "课程目录",
        ARCHIVE: "数据资源",
      };
      return [
        ...new Set(
          (item.resources || [])
            .map((resource) => labels[resource.resourceType])
            .filter(Boolean)
        ),
      ];
    },
    resetFilters() {
      this.keyword = "";
      this.type = "";
      this.tag = "";
      this.status = "all";
    },
    async loadCovers() {
      const pairs = await Promise.all(
        this.courses.map(async (item) => {
          try {
            const result = await (this.adminDemo
              ? getAdminCourseCoverUrl(item.course.courseId)
              : getCourseCoverUrl(item.course.courseId));
            return [item.course.courseId, result.data && result.data.coverUrl];
          } catch (error) {
            return [item.course.courseId, ""];
          }
        })
      );
      pairs.forEach((pair) => {
        if (pair[1]) this.$set(this.coverUrls, pair[0], pair[1]);
      });
    },
    startLearning(item) {
      this.selectedCourse = item;
      this.resourceTab = "INTRODUCTION";
      this.closeWorkspace();
      this.refreshChapters();
    },
    async openTraining() {
      this.workspaceVisible = true;
      const environment =
        this.courseEnvironments.find(
          (item) => item.actualState === "RUNNING"
        ) || this.courseEnvironments[0];
      if (!environment) {
        this.trainingPaneVisible = false;
        return this.$message.warning("当前课程未分配实训环境");
      }
      this.trainingPaneVisible = true;
      this.workspaceEnvironmentId = environment.environmentId;
      this.workspaceEmbeddedUrl = "";
      this.workspaceEmbeddedTitle = "";
    },
    closeWorkspace() {
      this.workspaceVisible = false;
      this.trainingPaneVisible = false;
      this.workspaceEmbeddedUrl = "";
      this.workspaceEmbeddedTitle = "";
      this.workspaceBusy = false;
      this.activeResource = null;
      this.previewUrl = "";
    },
    async reloadEnvironments() {
      const result = await (this.adminDemo
        ? listAdminTrainingEnvironments()
        : listUserTrainingEnvironments(false));
      this.environments = Array.isArray(result.data) ? result.data : [];
    },
    async waitForWorkspaceEnvironment(environmentId) {
      const deadline=Date.now()+15*60*1000;
      while(!this.destroyed && Date.now()<deadline) {
        await this.reloadEnvironments();
        const environment = this.courseEnvironments.find((item) => item.environmentId === environmentId);
        if (environment && !environment.modeSwitching && ["RUNNING", "ERROR", "DEGRADED", "STOPPED"].includes(environment.actualState) && !['PENDING','RUNNING','WAITING_DEPENDENCY'].includes(environment.operationState)) return environment;
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
      return null;
    },
    async openWorkspaceTool(tool) {
      if(this.workspaceBusy) return;
      let environment = this.courseEnvironments.find(
        (item) => item.environmentId === this.workspaceEnvironmentId
      );
      if (!environment) return this.$message.warning("请选择实训环境");
      if(environment.modeSwitching) return this.$message.info("平台正在切换环境，请等待准备完成");
      this.workspaceBusy = true;
      this.workspaceStartProgress = 5;
      try {
        if (environment.actualState !== 'RUNNING') {
          this.workspaceEmbeddedUrl = "";
          this.workspaceEmbeddedTitle = "";
          if (!['STARTING', 'CREATING', 'WAITING_DEPENDENCY', 'STOPPING', 'RESTORING'].includes(environment.actualState)) {
          await (this.adminDemo
            ? startAdminTrainingEnvironment(environment.environmentId)
            : startUserTrainingEnvironment(environment.environmentId));
          }
          this.$message.info("实训环境正在启动");
          environment = await this.waitForWorkspaceEnvironment(environment.environmentId);
          if (!environment || environment.actualState !== 'RUNNING') return this.$message.error((environment && environment.resultMessage) || "环境尚未就绪，请查看后台准备状态");
        }
        const url =
          tool === "ANNOTATION"
            ? environment.annotationUrl
            : tool === "JUPYTER"
            ? environment.jupyterUrl
            : environment.editorUrl;
        if (!url) return this.$message.warning("实训工具尚未就绪");
        this.workspaceEmbeddedUrl = tool === "VSCODE" && url.startsWith("http://")
          ? `https://${url.slice(7)}`
          : url;
        this.workspaceEmbeddedTitle =
          tool === "ANNOTATION"
            ? "图像标注"
            : tool === "JUPYTER"
            ? "Jupyter Notebook"
            : "VS Code";
        this.workspaceEmbeddedKey += 1;
        this.workspaceStartProgress = 100;
      } finally {
        this.workspaceBusy = false;
        this.workspaceStartProgress = 0;
      }
    },
    progressFor(resource) {
      return Number(this.progressRecords[resource.resourceId] || 0);
    },
    courseProgress(item) {
      if (!item.resources.length) return 0;
      return Math.round(
        item.resources.reduce(
          (sum, resource) => sum + this.progressFor(resource),
          0
        ) / item.resources.length
      );
    },
    courseDone(item) {
      return item.resources.length > 0 && this.courseProgress(item) >= 100;
    },
    async openResource(resource) {
      this.activeResource = resource;
      this.progress = this.progressFor(resource);
      this.previewUrl = "";
      this.workspaceVisible = true;
      try {
        const result = await (this.adminDemo
          ? getAdminCourseResourcePreviewUrl(resource.resourceId)
          : getCourseResourcePreviewUrl(resource.resourceId));
        this.previewUrl = result.data && result.data.previewUrl;
      } catch (error) {
        this.$message.error("在线浏览地址获取失败");
      }
    },
    async finishResource() {
      if (this.adminDemo) return this.$message.success("管理员演示浏览完成");
      await recordCourseProgress({
        resourceId: this.activeResource.resourceId,
        progressKind:
          this.activeResource.resourceType === "VIDEO"
            ? "TIME"
            : this.activeResource.resourceType === "PPT"
            ? "SLIDE"
            : "PAGE",
        progressValue: 100,
        completed: true,
      });
      this.$set(this.progressRecords, this.activeResource.resourceId, 100);
      this.progress = 100;
      this.$message.success("学习进度已记录");
    },
    openDelivery(resource) {
      if (!this.environments.length)
        return this.$message.warning("当前账号尚未分配实训环境");
      this.deliveryResource = resource;
      this.selectedEnvironmentId =
        this.courseEnvironments.length === 1
          ? this.courseEnvironments[0].environmentId
          : "";
      this.deliveryDialog = true;
    },
    async deliverResource() {
      this.delivering = true;
      try {
        await deliverCourseResource(
          this.deliveryResource.resourceId,
          this.selectedEnvironmentId
        );
        await recordCourseProgress({
          resourceId: this.deliveryResource.resourceId,
          progressKind: "DELIVERY",
          progressValue: 100,
          completed: true,
        });
        this.$set(this.progressRecords, this.deliveryResource.resourceId, 100);
        this.deliveryDialog = false;
        this.$message.success("数据资源已发送到实训环境");
      } finally {
        this.delivering = false;
      }
    },
    environmentLabel(item) {
      return `${item.environmentName || "实训环境"} · ${
        item.actualState || "未知状态"
      }`;
    },
    resourceLabel(type) {
      return (
        { PPT: "PPT", VIDEO: "视频", EBOOK: "课程目录", ARCHIVE: "数据资源" }[
          type
        ] || type
      );
    },
    resourceIcon(type) {
      return (
        {
          PPT: "el-icon-data-board",
          VIDEO: "el-icon-video-play",
          EBOOK: "el-icon-document",
          ARCHIVE: "el-icon-folder-opened",
        }[type] || "el-icon-document"
      );
    },
    formatBytes(bytes) {
      const value = Number(bytes || 0);
      if (!value) return "--";
      if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
      if (value < 1024 * 1024 * 1024)
        return `${(value / 1024 / 1024).toFixed(1)} MiB`;
      return `${(value / 1024 / 1024 / 1024).toFixed(2)} GiB`;
    },
  },
};
</script>

<style scoped>
.course-platform-page {
  width: 100%;
}
.course-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}
.course-heading h1 {
  margin: 0;
  font-size: 28px;
}
.course-heading p {
  margin: 6px 0 0;
  color: var(--ui-muted);
  font-size: 13px;
}
.course-rich-content {
  min-height: 360px;
  padding: 24px;
  background: #fff;
  border: 1px solid var(--ui-border);
  line-height: 1.8;
}
.course-rich-content::v-deep img { max-width: 100%; }
.course-rich-content::v-deep table { width: 100%; border-collapse: collapse; }
.course-rich-content::v-deep td,
.course-rich-content::v-deep th { padding: 8px; border: 1px solid var(--ui-border); }
.course-filters {
  margin-bottom: 18px;
  background: #fff;
  border: 1px solid var(--ui-border);
}
.filter-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  min-height: 48px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--ui-border);
}
.filter-line > strong {
  flex: 0 0 74px;
  font-size: 13px;
}
.filter-line button {
  flex: 0 0 auto;
  padding: 6px 9px;
  color: #596a79;
  background: transparent;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
}
.filter-line button.active {
  color: #fff;
  background: var(--ui-primary);
}
.filter-line button small {
  margin-left: 3px;
  opacity: 0.75;
}
.filter-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
}
.filter-search .el-input {
  width: min(360px, 100%);
}
.filter-search .el-select {
  width: 150px;
}
.filter-search > span {
  margin-left: auto;
  color: var(--ui-muted);
  font-size: 12px;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  min-height: 24px;
  margin-top: 8px;
}
.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}
.course-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
}
.course-cover {
  position: relative;
  display: grid;
  place-items: center;
  height: 150px;
  overflow: hidden;
  color: var(--ui-primary);
  font-size: 44px;
  background: #eef2f7;
}
.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.course-cover .el-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}
.course-body {
  padding: 16px;
}
.course-body h2 {
  margin: 0;
  font-size: 17px;
}
.course-body p {
  margin: 7px 0;
  color: var(--ui-primary-strong);
  font-size: 12px;
}
.course-body > small {
  display: block;
  min-height: 38px;
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.6;
}
.course-body > .el-button {
  width: 100%;
  margin-top: 14px;
}
.progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}
.progress .el-progress {
  flex: 1;
}
.progress span {
  font-size: 12px;
  color: var(--ui-muted);
}
.detail-heading h1 {
  margin-top: 6px;
  font-size: 24px;
}
.chapter-section > header { font-size: 15px; }
.resource-tabs {
  margin-bottom: 8px;
}
.resource-list {
  background: #fff;
  border: 1px solid var(--ui-border);
}
.resource-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 180px 150px;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--ui-border);
}
.resource-row:last-child {
  border-bottom: 0;
}
.resource-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--ui-primary);
  background: #eef3fb;
  border-radius: 6px;
}
.resource-row strong,
.resource-row small {
  display: block;
}
.resource-row strong { font-size: 14px; }
.resource-row small {
  margin-top: 5px;
  color: var(--ui-muted);
  font-size: 11px;
}
.resource-progress {
  display: flex;
  align-items: center;
  gap: 8px;
}
.resource-progress .el-progress {
  flex: 1;
}
.resource-progress span {
  font-size: 11px;
  color: var(--ui-muted);
}
.workspace-preview {
  display: flex;
  height: 100%;
  min-height: calc(100vh - 270px);
  padding: 10px;
  box-sizing: border-box;
  flex-direction: column;
}
.workspace-preview > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.workspace-preview > header strong,
.workspace-preview > header small {
  display: block;
}
.workspace-preview > header small {
  margin-top: 3px;
  color: var(--ui-muted);
}
.workspace-resource-actions { display: flex; align-items: center; gap: 8px; }
.workspace-resource-actions .el-button + .el-button { margin-left: 0; }
.workspace-preview iframe,
.workspace-preview video {
  display: block;
  width: 100%;
  min-height: 0;
  flex: 1;
  border: 0;
  background: #fff;
}
.workspace-preview video {
  object-fit: contain;
  background: #101820;
}
.workspace-loading {
  display: grid;
  min-height: 360px;
  place-items: center;
  align-content: center;
  color: var(--ui-muted);
}
.workspace-materials {
  padding: 18px;
}
.workspace-materials h2 {
  margin: 0;
  font-size: 20px;
}
.workspace-materials > p {
  color: var(--ui-muted);
}
.workspace-materials > button {
  display: grid;
  width: 100%;
  min-height: 48px;
  padding: 8px 10px;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  color: var(--ui-text);
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--ui-border);
  cursor: pointer;
}
.workspace-materials > button:hover {
  background: #f5f8fc;
}
.workspace-materials > button small {
  color: var(--ui-muted);
}
.preview-frame {
  display: block;
  width: 100%;
  height: 470px;
  border: 1px solid var(--ui-border);
}
.preview-video {
  display: block;
  width: 100%;
  max-height: 470px;
}
.preview-footer {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
}
.preview-footer .el-progress {
  flex: 1;
}
@media (max-width: 950px) {
  .resource-row {
    grid-template-columns: 44px 1fr 130px;
  }
  .resource-progress {
    display: none;
  }
}
@media (max-width: 650px) {
  .course-heading,
  .filter-search {
    align-items: stretch;
    flex-direction: column;
  }
  .filter-search .el-input,
  .filter-search .el-select {
    width: 100%;
  }
  .filter-search > span {
    margin-left: 0;
  }
  .course-grid {
    grid-template-columns: 1fr;
  }
  .resource-row {
    grid-template-columns: 40px 1fr;
  }
  .resource-row > .el-button {
    grid-column: 1/-1;
  }
  .preview-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
