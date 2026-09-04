<template>
  <section
    v-if="visible"
    ref="workspace"
    class="course-learning-workspace"
    :class="{ 'is-fullscreen': fullscreen }"
  >
    <header class="workspace-toolbar">
      <div class="environment-picker" v-if="trainingVisible">
        <span>实训环境</span>
        <el-select
          :value="environmentId"
          size="small"
          filterable
          placeholder="选择实训环境"
          @input="$emit('update:environmentId', $event)"
        >
          <el-option
            v-for="item in environments"
            :key="item.environmentId"
            :label="environmentLabel(item)"
            :value="item.environmentId"
          />
        </el-select>
        <el-tag v-if="currentEnvironment" size="mini" :type="stateType">
          {{ stateText(currentEnvironment.actualState) }}
        </el-tag>
      </div>
      <div class="workspace-actions">
        <el-button
          v-if="trainingVisible"
          size="mini"
          :disabled="busy || !toolEnabled.ANNOTATION"
          @click="$emit('tool', 'ANNOTATION')"
          >图像标注</el-button
        >
        <el-button
          v-if="trainingVisible"
          size="mini"
          :disabled="busy || !toolEnabled.VSCODE"
          @click="$emit('tool', 'VSCODE')"
          >VS Code</el-button
        >
        <el-button
          v-if="trainingVisible"
          size="mini"
          :disabled="busy || !toolEnabled.JUPYTER"
          @click="$emit('tool', 'JUPYTER')"
          >Jupyter</el-button
        >
        <el-button
          v-if="trainingVisible"
          size="mini"
          icon="el-icon-refresh"
          :disabled="!embeddedUrl"
          @click="$emit('refresh')"
          >刷新</el-button
        >
        <el-button size="mini" icon="el-icon-edit" @click="$emit('report')"
          >实训报告</el-button
        >
        <el-button
          size="mini"
          icon="el-icon-full-screen"
          @click="toggleFullscreen"
          >{{ fullscreen ? "退出全屏" : "全屏" }}</el-button
        >
        <el-button size="mini" icon="el-icon-close" @click="$emit('close')"
          >关闭</el-button
        >
      </div>
    </header>

    <div
      ref="body"
      class="workspace-body"
      :class="{ 'with-training': trainingVisible }"
      :style="{ '--split-ratio': splitRatio + '%' }"
    >
      <div class="course-pane"><slot /></div>
      <div
        v-if="trainingVisible"
        class="workspace-divider"
        role="separator"
        aria-label="调整课程资料和实训环境宽度"
        @pointerdown="beginResize"
      />
      <div v-if="trainingVisible" class="training-pane">
        <div v-if="busy" class="workspace-status">
          <div class="workspace-progress-panel">
            <i class="el-icon-loading" />
            <strong>{{ startStage }}</strong>
            <el-progress :percentage="startProgress" :stroke-width="10" class="workspace-progress" />
            <span>启动进度 {{ startProgress }}%</span>
          </div>
        </div>
        <iframe
          v-else-if="embeddedUrl"
          :key="embeddedKey"
          :src="embeddedUrl"
          :title="embeddedTitle || '实训环境'"
          allow="clipboard-read; clipboard-write"
        />
        <el-empty v-else description="请选择上方实训工具" />
      </div>
    </div>
    <slot name="overlay" />
  </section>
</template>

<script>
export default {
  props: {
    visible: Boolean,
    trainingVisible: Boolean,
    environments: { type: Array, default: () => [] },
    environmentId: { type: String, default: "" },
    embeddedUrl: { type: String, default: "" },
    embeddedTitle: { type: String, default: "" },
    embeddedKey: { type: Number, default: 0 },
    busy: Boolean,
    startProgress: { type: Number, default: 0 },
  },
  data: () => ({ splitRatio: 34, resizing: false, fullscreen: false }),
  computed: {
    currentEnvironment() {
      return (
        this.environments.find(
          (item) => item.environmentId === this.environmentId
        ) || null
      );
    },
    toolEnabled() {
      const item = this.currentEnvironment || {};
      return {
        ANNOTATION: Boolean(item.annotationUrl),
        VSCODE: Boolean(item.editorUrl) && (!item.editorTool || item.editorTool === "VSCODE"),
        JUPYTER: Boolean(item.jupyterUrl) && (!item.editorTool || item.editorTool === "JUPYTER"),
      };
    },
    stateType() {
      const state = this.currentEnvironment && this.currentEnvironment.actualState;
      return state === "RUNNING"
        ? "success"
        : state === "ERROR" || state === "DEGRADED"
        ? "danger"
        : "info";
    },
    startStage() {
      if (this.startProgress < 15) return "正在提交启动任务"
      if (this.startProgress < 60) return "正在创建实训容器"
      if (this.startProgress < 95) return "正在等待环境依赖就绪"
      return "正在完成环境启动"
    },
  },
  mounted() {
    document.addEventListener("fullscreenchange", this.fullscreenChanged);
  },
  beforeDestroy() {
    document.removeEventListener("fullscreenchange", this.fullscreenChanged);
    this.endResize();
  },
  methods: {
    environmentLabel(item) {
      return `${item.environmentName || "实训环境"} · ${this.stateText(
        item.actualState
      )}`;
    },
    stateText(state) {
      return (
        {
          RUNNING: "运行中",
          STARTING: "启动中",
          STOPPED: "已停止",
          STOPPING: "停止中",
          DEGRADED: "部分异常",
          ERROR: "异常",
        }[state] || state || "未知状态"
      );
    },
    beginResize(event) {
      event.preventDefault();
      this.resizing = true;
      if (event.currentTarget.setPointerCapture) {
        event.currentTarget.setPointerCapture(event.pointerId);
      }
      document.addEventListener("pointermove", this.resize);
      document.addEventListener("pointerup", this.endResize);
    },
    resize(event) {
      if (!this.resizing || !this.$refs.body) return;
      const box = this.$refs.body.getBoundingClientRect();
      const vertical = window.innerWidth <= 760;
      const raw = vertical
        ? ((event.clientY - box.top) / box.height) * 100
        : ((event.clientX - box.left) / box.width) * 100;
      const min = vertical ? 25 : 22;
      const max = vertical ? 70 : 72;
      this.splitRatio = Math.max(min, Math.min(max, Math.round(raw)));
    },
    endResize() {
      this.resizing = false;
      document.removeEventListener("pointermove", this.resize);
      document.removeEventListener("pointerup", this.endResize);
    },
    async toggleFullscreen() {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await this.$refs.workspace.requestFullscreen();
    },
    fullscreenChanged() {
      this.fullscreen = document.fullscreenElement === this.$refs.workspace;
    },
  },
};
</script>

<style scoped>
.course-learning-workspace {
  display: flex;
  min-height: calc(100vh - 205px);
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--ui-border);
}
.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 10px;
  border-bottom: 1px solid var(--ui-border);
}
.environment-picker,
.workspace-actions {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.environment-picker > span {
  font-size: 12px;
  color: var(--ui-muted);
}
.environment-picker .el-select {
  width: min(340px, 36vw);
}
.workspace-actions .el-button + .el-button {
  margin-left: 0;
}
.workspace-body {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(0, 1fr);
}
.workspace-body.with-training {
  grid-template-columns: minmax(220px, var(--split-ratio)) 8px minmax(320px, 1fr);
}
.course-pane,
.training-pane {
  min-width: 0;
  min-height: 0;
  overflow: auto;
}
.workspace-divider {
  background: var(--ui-border);
  cursor: col-resize;
  touch-action: none;
}
.workspace-divider:hover {
  background: var(--ui-primary);
}
.training-pane iframe {
  display: block;
  width: 100%;
  height: 100%;
  min-height: calc(100vh - 260px);
  border: 0;
  background: #fff;
}
.workspace-status {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  min-height: 360px;
  color: var(--ui-muted);
}
.workspace-progress { width: min(420px, 80%); }
.workspace-progress-panel { display: grid; width: min(460px, 82%); gap: 12px; justify-items: center; }
.workspace-progress-panel strong { color: var(--ui-text); font-size: 16px; }
.workspace-progress-panel .workspace-progress { width: 100%; }
.course-learning-workspace:fullscreen,
.course-learning-workspace.is-fullscreen {
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  border: 0;
}
.course-learning-workspace:fullscreen .training-pane iframe,
.course-learning-workspace.is-fullscreen .training-pane iframe {
  min-height: 0;
}
@media (max-width: 760px) {
  .workspace-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .environment-picker .el-select {
    width: 100%;
  }
  .workspace-body.with-training {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(180px, var(--split-ratio)) 8px minmax(260px, 1fr);
  }
  .workspace-divider {
    cursor: row-resize;
  }
}
</style>
