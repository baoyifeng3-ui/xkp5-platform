<template>
  <el-dialog
    :title="space === 'course' ? '上传课程资源' : '上传文件'"
    :visible="visible"
    width="540px"
    :close-on-click-modal="!uploading"
    @close="close"
  >
    <el-form label-width="90px">
      <el-form-item label="文件"
        ><input
          ref="picker"
          type="file"
          multiple
          class="hidden-picker"
          @change="choose"
        /><el-button
          icon="el-icon-folder-opened"
          :disabled="uploading"
          @click="$refs.picker.click()"
          >选择多个文件</el-button
        ><span class="file-name">{{
          files.length ? `已选择 ${files.length} 个文件` : "尚未选择文件"
        }}</span></el-form-item
      >
      <el-form-item v-if="space === 'course'" label="资源类型"
        ><el-select v-model="resourceType" style="width: 100%"
          ><el-option label="电子书" value="EBOOK" /><el-option
            label="视频"
            value="VIDEO" /><el-option label="PPT" value="PPT" /><el-option
            label="压缩包/课程数据"
            value="ARCHIVE" /></el-select
      ></el-form-item>
      <el-form-item v-if="space === 'course'" label="关联课程"
        ><el-select
          v-model="courseIds"
          multiple
          filterable
          style="width: 100%"
          placeholder="至少选择一门课程"
          ><el-option
            v-for="item in courses"
            :key="item.course.courseId"
            :label="item.course.name"
            :value="item.course.courseId" /></el-select
      ></el-form-item>
      <el-form-item v-if="queue.length" label="上传队列"
        ><div v-for="item in queue" :key="item.id" class="upload-row">
          <div>
            <strong>{{ item.file.name }}</strong
            ><small
              >{{ formatSize(item.file.size) }} · {{ item.message }}</small
            >
          </div>
          <el-progress :percentage="item.progress" :status="item.status" /></div
      ></el-form-item>
    </el-form>
    <span slot="footer"
      ><el-button :disabled="uploading" @click="close">取消</el-button
      ><el-button type="primary" :loading="uploading" @click="submit"
        >开始上传</el-button
      ></span
    >
  </el-dialog>
</template>
<script>
import { uploadResourceFile } from "@/api/ResourceSpaces";
export default {
  props: {
    visible: Boolean,
    admin: Boolean,
    space: { type: String, required: true },
    directoryId: { type: String, default: null },
    courses: { type: Array, default: () => [] },
    initialCourseId: { type: String, default: "" },
    initialResourceType: { type: String, default: "EBOOK" },
  },
  data: () => ({
    files: [],
    queue: [],
    resourceType: "EBOOK",
    courseIds: [],
    uploading: false,
  }),
  watch: {
    visible(value) {
      if (value) {
        this.files = [];
        this.queue = [];
        this.courseIds = this.initialCourseId ? [this.initialCourseId] : [];
        this.resourceType = this.initialResourceType || "EBOOK";
      }
    },
  },
  methods: {
    choose(event) {
      this.files = Array.from(event.target.files || []);
      this.queue = this.files.map((file, index) => ({
        id: `${file.name}:${file.size}:${index}`,
        file,
        progress: 0,
        status: undefined,
        message: "等待上传",
      }));
    },
    async submit() {
      if (!this.files.length)
        return this.$message.warning("请选择需要上传的文件");
      if (this.space === "course" && !this.courseIds.length)
        return this.$message.warning("课程资源必须至少关联一门课程");
      this.uploading = true;
      let success = 0;
      for (const item of this.queue) {
        const data = new FormData();
        data.append("file", item.file);
        if (this.directoryId) data.append("directoryId", this.directoryId);
        data.append("resourceType", this.resourceType);
        this.courseIds.forEach((id) => data.append("courseIds", id));
        item.message = "正在上传";
        try {
          const result = await uploadResourceFile(
            this.admin,
            this.space,
            data,
            (event) => {
              if (event.total)
                item.progress = Math.min(
                  99,
                  Math.round((event.loaded * 100) / event.total)
                );
            }
          );
          item.progress = 100;
          item.status = "success";
          item.message = "上传成功";
          success++;
          this.$emit("uploaded", result.data || result);
        } catch (error) {
          item.status = "exception";
          item.message = "上传失败";
        }
      }
      this.uploading = false;
      this.$message[success === this.queue.length ? "success" : "warning"](
        `成功上传 ${success}/${this.queue.length} 个文件`
      );
    },
    close() {
      if (!this.uploading) this.$emit("update:visible", false);
    },
    formatSize(bytes) {
      if (bytes < 1024) return `${bytes} B`;
      if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
      if (bytes < 1073741824) return `${(bytes / 1048576).toFixed(1)} MB`;
      return `${(bytes / 1073741824).toFixed(1)} GB`;
    },
  },
};
</script>
<style scoped>
.hidden-picker {
  display: none;
}
.file-name {
  margin-left: 10px;
  color: var(--ui-muted);
  font-size: 12px;
}
.el-form-item small {
  display: block;
  color: var(--ui-muted);
  font-size: 11px;
}
.upload-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190px;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--ui-border);
}
.upload-row strong,
.upload-row small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
