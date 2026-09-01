<template>
  <section class="module-page user-resource-center">
    <header class="page-heading">
      <div>
        <h1>资源中心</h1>
        <p>公共资源可下载或发送到实训环境，交换和作业空间支持上传。</p>
      </div>
      <el-button icon="el-icon-refresh" :loading="loading" @click="load"
        >刷新</el-button
      >
    </header>
    <el-tabs v-model="space" @tab-click="changeSpace"
      ><el-tab-pane label="公共资源库" name="public" /><el-tab-pane
        label="交换空间"
        name="exchange" /><el-tab-pane
        v-if="!adminDemo"
        label="作业空间"
        name="homework"
    /></el-tabs>
    <ResourceBrowser
      :directories="entries.directories"
      :files="entries.files"
      :breadcrumbs="breadcrumbs"
      :loading="loading"
      :can-upload="adminDemo || space !== 'public'"
      :can-create-directory="adminDemo || space !== 'public'"
      :can-delete="canDelete"
      :allow-deliver="!adminDemo && space === 'public'"
      @navigate="navigate"
      @open-directory="openDirectory"
      @create-directory="createDirectory"
      @upload="uploadVisible = true"
      @download="download"
      @deliver="deliver"
      @remove="remove"
    />
    <ResourceUploadDialog
      :visible.sync="uploadVisible"
      :admin="adminDemo"
      :space="space"
      :directory-id="currentDirectoryId"
      @uploaded="load"
    />
  </section>
</template>
<script>
import ResourceBrowser from "@/components/resources/ResourceBrowser.vue";
import ResourceUploadDialog from "@/components/resources/ResourceUploadDialog.vue";
import {
  listResourceEntries,
  createResourceDirectory,
  getResourceDownload,
  deleteResourceFile,
  deleteResourceDirectory,
  deliverPublicResource,
} from "@/api/ResourceSpaces";
import { listUserTrainingEnvironments } from "@/api/TrainingEnvironments";
export default {
  props: { adminDemo: { type: Boolean, default: false } },
  components: { ResourceBrowser, ResourceUploadDialog },
  data: () => ({
    space: "public",
    loading: false,
    entries: { directories: [], files: [] },
    breadcrumbs: [{ name: "根目录", id: null }],
    uploadVisible: false,
  }),
  computed: {
    currentDirectoryId() {
      return this.breadcrumbs[this.breadcrumbs.length - 1].id;
    },
    currentUserId() {
      return Number(sessionStorage.getItem("userId"));
    },
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const result = await listResourceEntries(
          this.adminDemo,
          this.space,
          this.currentDirectoryId
        );
        this.entries = result.data || { directories: [], files: [] };
      } finally {
        this.loading = false;
      }
    },
    changeSpace() {
      this.breadcrumbs = [{ name: "根目录", id: null }];
      this.load();
    },
    navigate(item, index) {
      this.breadcrumbs = this.breadcrumbs.slice(0, index + 1);
      this.load();
    },
    openDirectory(item) {
      this.breadcrumbs.push({ name: item.name, id: item.directoryId });
      this.load();
    },
    canDelete(item) {
      if (this.adminDemo) return true;
      if (this.space === "public") return false;
      if (this.space === "homework") return true;
      return Number(item.uploadedBy || item.createdBy) === this.currentUserId;
    },
    async createDirectory() {
      const result = await this.$prompt("输入新目录名称", "新建目录", {
        inputPattern: /^(?!\.{1,2}$)[^\\/]+$/,
        inputErrorMessage: "目录名称无效",
      });
      await createResourceDirectory(this.adminDemo, this.space, {
        parentId: this.currentDirectoryId,
        name: result.value,
      });
      this.$message.success("目录已创建");
      await this.load();
    },
    async download(file) {
      const result = await getResourceDownload(
        this.adminDemo,
        this.space,
        file.fileId
      );
      window.open(result.data.downloadUrl, "_blank", "noopener");
    },
    async deliver(file) {
      const result = await listUserTrainingEnvironments();
      const environments = result.data || [];
      if (!environments.length)
        return this.$message.warning("当前没有可用的实训环境");
      const selection = await this.$prompt(
        "输入需要接收文件的实训环境 ID：\n" +
          environments
            .map(
              (item) =>
                `${item.environmentId}（课程 ${
                  item.courseLabel || item.courseId
                }）`
            )
            .join("\n"),
        "发送到实训环境",
        {
          inputValue: environments[0].environmentId,
          inputValidator: (value) =>
            environments.some((item) => item.environmentId === value) ||
            "实训环境 ID 无效",
        }
      );
      await deliverPublicResource(file.fileId, selection.value);
      this.$message.success("资源下发任务已提交");
    },
    async remove(item) {
      await this.$confirm(
        `确认删除“${item.name || item.fileName}”？`,
        "删除确认",
        { type: "warning" }
      );
      if (item.kind === "directory")
        await deleteResourceDirectory(
          this.adminDemo,
          this.space,
          item.directoryId
        );
      else await deleteResourceFile(this.adminDemo, this.space, item.fileId);
      this.$message.success("已删除");
      await this.load();
    },
  },
};
</script>
<style scoped>
.user-resource-center {
  width: 100%;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.page-heading h1 {
  margin: 0;
  font-size: 28px;
}
.page-heading p {
  margin: 6px 0 0;
  color: var(--ui-muted);
  font-size: 13px;
}
@media (max-width: 620px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
