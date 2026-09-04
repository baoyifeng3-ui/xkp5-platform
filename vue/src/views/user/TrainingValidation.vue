<template>
  <section class="module-page module-composed-page">
    <header class="module-heading">
      <div>
        <h1>模型验证</h1>
        <p>调用当前实训环境中的 T100 服务验证模型结果。</p>
      </div>
      <el-button
        icon="el-icon-monitor"
        @click="$router.push('/training-environment')"
        >返回实训环境</el-button
      >
    </header>
    <el-select
      v-if="adminDemo"
      v-model="selectedEnvironmentId"
      style="width: 100%; margin-bottom: 14px"
      placeholder="选择用于演示的实训环境"
      ><el-option
        v-for="item in environments"
        :key="item.environmentId"
        :value="item.environmentId"
        :label="`${item.environmentName || item.environmentId} · 用户 ${
          item.userId
        }`"
    /></el-select>
    <el-tabs v-model="mode"
      ><el-tab-pane label="上传图片" name="image"
        ><el-upload
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          accept="image/*"
          :on-change="selectImage"
          ><el-button icon="el-icon-upload2">选择图片</el-button></el-upload
        >
        <p v-if="fileName">{{ fileName }}</p></el-tab-pane
      ><el-tab-pane label="图片 URL" name="url"
        ><el-input
          v-model="imageUrl"
          placeholder="https://example.com/image.jpg"
          clearable /></el-tab-pane></el-tabs
    ><el-button
      type="primary"
      icon="el-icon-cpu"
      :loading="loading"
      :disabled="!canSubmit"
      @click="validate"
      >开始验证</el-button
    >
    <div v-if="result" class="validation-result">
      <h2>验证结果</h2>
      <img
        v-if="result.data && result.data.image"
        class="result-image"
        :src="'data:image/jpeg;base64,' + result.data.image"
        alt="模型验证结果"
      />
      <p v-if="result.data">
        <strong>文件：</strong>{{ result.data.name || "未命名" }}
        <strong>标注框：</strong>{{ (result.data.bboxes || []).length }}
      </p>
      <el-collapse
        ><el-collapse-item title="查看原始返回">
          <pre>{{ result }}</pre>
        </el-collapse-item></el-collapse
      >
    </div>
  </section>
</template>
<script>
import request from "@/utils/request";
import {
  listAdminTrainingEnvironments,
  listUserTrainingEnvironments,
  startUserTrainingEnvironment,
} from "@/api/TrainingEnvironments";
export default {
  props: { adminDemo: { type: Boolean, default: false } },
  data: () => ({
    mode: "image",
    imageUrl: "",
    fileName: "",
    imageBase64: "",
    loading: false,
    result: null,
    environments: [],
    selectedEnvironmentId: "",
  }),
  computed: {
    canSubmit() {
      return this.mode === "image"
        ? !!this.imageBase64
        : /^https?:\/\//.test(this.imageUrl);
    },
  },
  async created() {
    if (this.adminDemo) {
      const result = await listAdminTrainingEnvironments();
      this.environments = (result.data || []).filter((item) => item.t100Url);
      if (this.environments.length)
        this.selectedEnvironmentId = this.environments[0].environmentId;
    }
  },
  methods: {
    selectImage(file) {
      this.fileName = file.name;
      const reader = new FileReader();
      reader.onload = (e) => {
        this.imageBase64 = String(e.target.result).split(",")[1] || "";
      };
      reader.readAsDataURL(file.raw);
    },
    async validate() {
      this.loading = true;
      this.result = null;
      try {
        let environment;
        if (this.adminDemo) {
          environment = this.environments.find(
            (item) => item.environmentId === this.selectedEnvironmentId
          );
        } else {
          let rows = (await listUserTrainingEnvironments(false)).data || [];
          environment = rows.find((item) => item.actualState === "RUNNING" && item.t100Url) || rows.find((item) => item.t100Url);
          if (environment && environment.actualState !== "RUNNING") {
            await startUserTrainingEnvironment(environment.environmentId);
            for (let attempt = 0; attempt < 30 && environment.actualState !== "RUNNING"; attempt += 1) {
              await new Promise((resolve) => setTimeout(resolve, 1000));
              rows = (await listUserTrainingEnvironments(false)).data || [];
              environment = rows.find((item) => item.environmentId === environment.environmentId) || environment;
            }
          }
        }
        const t100Url = environment && environment.actualState === "RUNNING" && environment.t100Url;
        if (!t100Url) throw new Error("当前用户未分配 T100 服务");
        let image = this.imageBase64;
        if (this.mode === "url") {
          const converted = await request.post("/sample/httpToBase64", {
            url: this.imageUrl,
          });
          image =
            converted.data && (converted.data.base64 || converted.data.image);
        }
        if (!image) throw new Error("图片内容为空");
        const response = await this.$axios.post(
          `http://${t100Url}/api/v2/t100`,
          { image, name: "training/validation.jpg", paperType: /^[A-Z]$/.test(this.$store.state.Match.activePaper) ? this.$store.state.Match.activePaper : "A" }
        );
        if (!response.data || Number(response.data.code) !== 1)
          throw new Error(
            (response.data && response.data.message) || "T100 返回失败"
          );
        this.result = response.data;
        this.$message.success("模型验证完成");
      } catch (error) {
        this.$message.error((error && error.message) || "模型验证失败");
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
<style scoped>
.validation-result {
  margin-top: 24px;
  padding: 16px;
  border: 1px solid var(--ui-border);
}
.result-image {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin-bottom: 14px;
}
.validation-result pre {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
