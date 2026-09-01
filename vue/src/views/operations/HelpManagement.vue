<template>
  <section class="module-page">
    <header class="module-heading">
      <div>
        <h1>在线帮助管理</h1>
        <p>维护普通用户和管理员可见的帮助内容。</p>
      </div>
      <div>
        <el-button @click="openUpload">上传文档</el-button
        ><el-button type="primary" @click="create">新建富文本</el-button>
      </div>
    </header>
    <el-table :data="documents"
      ><el-table-column prop="title" label="标题" /><el-table-column
        label="受众"
        ><template slot-scope="s">{{
          s.row.audience === "ADMIN" ? "管理员" : "普通用户"
        }}</template></el-table-column
      ><el-table-column prop="contentType" label="类型" /><el-table-column
        label="状态"
        ><template slot-scope="s"
          ><el-tag :type="s.row.published ? 'success' : 'info'">{{
            s.row.published ? "已发布" : "未发布"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="操作"
        ><template slot-scope="s"
          ><el-button v-if="s.row.contentType === 'HTML'" type="text" @click="edit(s.row)">编辑</el-button
          ><el-button type="text" @click="togglePublished(s.row)">{{ s.row.published ? "停用" : "发布" }}</el-button></template
        ></el-table-column
      ></el-table
    ><el-dialog title="编辑帮助文档" :visible.sync="dialog" width="860px"
      ><el-form label-width="70px"
        ><el-form-item label="标题"
          ><el-input v-model.trim="form.title" /></el-form-item
        ><el-form-item label="受众"
          ><el-radio-group v-model="form.audience"
            ><el-radio label="USER">普通用户</el-radio
            ><el-radio label="ADMIN">管理员</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item label="发布"
          ><el-switch v-model="form.published" /></el-form-item></el-form
      ><RichHelpEditor v-model="form.htmlContent" /><span slot="footer"
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></span
      ></el-dialog
    ><el-dialog
      title="上传 PDF 或 Word"
      :visible.sync="uploadDialog"
      width="520px"
      ><el-form label-width="70px"
        ><el-form-item label="标题"
          ><el-input v-model.trim="upload.title" /></el-form-item
        ><el-form-item label="受众"
          ><el-radio-group v-model="upload.audience"
            ><el-radio label="USER">普通用户</el-radio
            ><el-radio label="ADMIN">管理员</el-radio></el-radio-group
          ></el-form-item
        ><el-form-item label="文件"
          ><input
            type="file"
            accept=".pdf,.doc,.docx"
            @change="
              upload.file = $event.target.files[0]
            " /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="uploadDialog = false">取消</el-button
        ><el-button type="primary" @click="sendUpload">上传</el-button></span
      ></el-dialog
    >
  </section>
</template>
<script>
import RichHelpEditor from "@/components/help/RichHelpEditor.vue";
import {
  listAllHelpDocuments,
  saveHelpDocument,
  uploadHelpDocument,
  setHelpPublished,
} from "@/api/OnlineHelp";
const empty = () => ({
  documentId: "",
  title: "",
  audience: "USER",
  contentType: "HTML",
  htmlContent: "",
  published: false,
});
export default {
  components: { RichHelpEditor },
  data: () => ({
    documents: [],
    dialog: false,
    form: empty(),
    uploadDialog: false,
    upload: { title: "", audience: "USER", file: null },
  }),
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.documents = (await listAllHelpDocuments()).data || [];
    },
    create() {
      this.form = empty();
      this.dialog = true;
    },
    edit(row) {
      if (row.contentType !== "HTML")
        return this.$message.info("上传文档无需编辑正文");
      this.form = { ...row };
      this.dialog = true;
    },
    async save() {
      await saveHelpDocument(this.form);
      this.dialog = false;
      await this.load();
    },
    openUpload() {
      this.upload = { title: "", audience: "USER", file: null };
      this.uploadDialog = true;
    },
    async togglePublished(row) {
      await setHelpPublished(row.documentId, !row.published);
      await this.load();
    },
    async sendUpload() {
      if (!this.upload.title || !this.upload.file)
        return this.$message.warning("请填写标题并选择文件");
      const data = new FormData();
      data.append("file", this.upload.file);
      await uploadHelpDocument(data, {
        title: this.upload.title,
        audience: this.upload.audience,
      });
      this.uploadDialog = false;
      await this.load();
    },
  },
};
</script>
