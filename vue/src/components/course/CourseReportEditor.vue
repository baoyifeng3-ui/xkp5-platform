<template>
  <component
    :is="floating ? 'div' : 'el-drawer'"
    v-show="!floating || visible"
    title="实训报告"
    :visible="visible"
    :size="floating ? null : '420px'"
    :class="{ 'report-floating': floating, minimized }"
    :style="floating ? floatingStyle : null"
    @close="$emit('update:visible', false)"
  >
    <header v-if="floating" class="report-floating-header" @mousedown="beginDrag">
      <strong>实训报告</strong>
      <div>
        <el-button type="text" size="mini" @click.stop="minimized = !minimized">
          {{ minimized ? "展开" : "最小化" }}
        </el-button>
        <el-button type="text" size="mini" @click.stop="$emit('update:visible', false)">关闭</el-button>
      </div>
    </header>
    <div v-show="!minimized" class="report-content">
    <div class="report-format-tools">
      <el-button size="mini" @click="format('bold')"><b>B</b></el-button>
      <el-button size="mini" @click="format('italic')"><i>I</i></el-button>
      <el-button size="mini" @click="format('formatBlock', 'h2')">标题</el-button>
      <el-button size="mini" @click="format('insertUnorderedList')">列表</el-button>
      <el-button size="mini" icon="el-icon-picture" @click="$refs.imagePicker.click()">图片</el-button>
      <input ref="imagePicker" type="file" accept="image/*" hidden @change="insertImage" />
    </div>
    <div class="report-tools">
      <el-button size="mini" icon="el-icon-download" @click="exportDoc"
        >导出文档</el-button
      ><el-button
        size="mini"
        icon="el-icon-upload2"
        @click="uploadSpace('EXCHANGE')"
        >交换空间</el-button
      ><el-button
        size="mini"
        icon="el-icon-folder"
        @click="uploadSpace('HOMEWORK')"
        >作业空间</el-button
      >
    </div>
    <div
      ref="editor"
      class="report-editor"
      contenteditable="true"
      @input="scheduleSave"
      @paste="pasteImage"
    ></div>
    <small class="report-status">{{
      saving ? "正在保存..." : "已自动保存"
    }}</small>
    </div>
  </component>
</template>
<script>
import { getCourseReport, saveCourseReport } from "@/api/Courses";
import { uploadResourceFile } from "@/api/ResourceSpaces";
export default {
  props: {
    floating: Boolean,
    visible: Boolean,
    courseId: { type: String, default: "" },
    userName: { type: String, default: "用户" },
  },
  data: () => ({
    saving: false,
    timer: null,
    loadedFor: "",
    left: 80,
    top: 90,
    minimized: false,
    dragging: false,
    dragOffsetX: 0,
    dragOffsetY: 0,
  }),
  computed: {
    floatingStyle() {
      return { left: `${this.left}px`, top: `${this.top}px` };
    },
  },
  watch: {
    visible(v) {
      if (v) this.load();
    },
    courseId() {
      if (this.visible) this.load();
    },
  },
  beforeDestroy() {
    clearTimeout(this.timer);
    this.endDrag();
  },
  methods: {
    format(command, value = null) {
      this.$refs.editor.focus();
      document.execCommand(command, false, value);
      this.scheduleSave();
    },
    insertImage(event) {
      const file = event.target.files && event.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (result) => {
        this.$refs.editor.focus();
        document.execCommand("insertImage", false, result.target.result);
        this.scheduleSave();
      };
      reader.readAsDataURL(file);
      event.target.value = "";
    },
    beginDrag(event) {
      if (!this.floating || event.button !== 0) return;
      this.dragging = true;
      this.dragOffsetX = event.clientX - this.left;
      this.dragOffsetY = event.clientY - this.top;
      document.addEventListener("mousemove", this.drag);
      document.addEventListener("mouseup", this.endDrag);
    },
    drag(event) {
      if (!this.dragging) return;
      this.left = Math.max(0, Math.min(window.innerWidth - 180, event.clientX - this.dragOffsetX));
      this.top = Math.max(0, Math.min(window.innerHeight - 48, event.clientY - this.dragOffsetY));
    },
    endDrag() {
      this.dragging = false;
      document.removeEventListener("mousemove", this.drag);
      document.removeEventListener("mouseup", this.endDrag);
    },
    async load() {
      if (!this.courseId || this.loadedFor === this.courseId) return;
      const r = await getCourseReport(this.courseId);
      this.$refs.editor.innerHTML = (r.data && r.data.content) || "";
      this.loadedFor = this.courseId;
    },
    scheduleSave() {
      clearTimeout(this.timer);
      this.timer = setTimeout(this.save, 700);
    },
    async save() {
      if (!this.courseId || !this.$refs.editor) return;
      this.saving = true;
      try {
        await saveCourseReport(this.courseId, this.$refs.editor.innerHTML);
      } finally {
        this.saving = false;
      }
    },
    pasteImage(e) {
      const item = [...((e.clipboardData && e.clipboardData.items) || [])].find(
        (x) => x.type.indexOf("image") === 0
      );
      if (!item) return;
      const file = item.getAsFile();
      const reader = new FileReader();
      reader.onload = (event) => {
        document.execCommand("insertImage", false, event.target.result);
        this.scheduleSave();
      };
      reader.readAsDataURL(file);
    },
    exportDoc() {
      const content = `<html><meta charset="utf-8"><body>${this.$refs.editor.innerHTML}</body></html>`;
      const blob = new Blob([content], { type: "application/msword" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `${this.userName}_${Date.now()}.doc`;
      a.click();
      URL.revokeObjectURL(a.href);
    },
    async uploadSpace(space) {
      await this.save();
      const file = new File(
        [
          `<html><meta charset="utf-8"><body>${this.$refs.editor.innerHTML}</body></html>`,
        ],
        `${this.userName}_${Date.now()}.doc`,
        { type: "application/msword" }
      );
      const data = new FormData();
      data.append("file", file);
      await uploadResourceFile(false, space, data);
      this.$message.success(
        `报告已上传到${space === "EXCHANGE" ? "交换空间" : "作业空间"}`
      );
    },
  },
};
</script>
<style scoped>
.report-floating {
  position: fixed;
  z-index: 3000;
  display: flex;
  width: 460px;
  height: 620px;
  min-width: 320px;
  min-height: 220px;
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
  overflow: hidden;
  flex-direction: column;
  resize: both;
  background: #fff;
  border: 1px solid var(--ui-border);
  box-shadow: 0 12px 32px rgba(14, 35, 56, 0.2);
}
.report-floating.minimized {
  width: 300px;
  height: 44px;
  min-height: 44px;
  resize: none;
}
.report-floating-header {
  display: flex;
  min-height: 42px;
  padding: 0 12px;
  align-items: center;
  justify-content: space-between;
  cursor: move;
  border-bottom: 1px solid var(--ui-border);
  user-select: none;
}
.report-content {
  display: flex;
  min-height: 0;
  padding: 12px;
  flex: 1;
  flex-direction: column;
}
.report-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.report-format-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 7px;
  padding-bottom: 7px;
  border-bottom: 1px solid var(--ui-border);
}
.report-format-tools .el-button + .el-button {
  margin-left: 0;
}
.report-editor {
  min-height: 520px;
  padding: 12px;
  border: 1px solid var(--ui-border);
  outline: none;
  overflow: auto;
  resize: both;
}
.report-floating .report-editor {
  min-height: 160px;
  flex: 1;
  resize: none;
}
.report-status {
  display: block;
  margin-top: 8px;
  color: var(--ui-muted);
}
</style>
