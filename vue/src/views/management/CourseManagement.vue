<template>
  <section class="module-page course-admin-page">
    <header class="page-heading">
      <div>
        <h1>课程管理</h1>
        <p>创建课程、关联课程资源并控制发布状态。</p>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="openCourse()"
        >新建课程</el-button
      >
    </header>
    <section class="course-summary">
      <div>
        <small>课程目录</small><strong>{{ courses.length }}</strong
        ><span>已创建课程</span>
      </div>
      <div>
        <small>资源关联</small><strong>{{ resourceCount }}</strong
        ><span>电子书、视频、PPT 和课程数据</span>
      </div>
      <div>
        <small>已发布</small><strong>{{ publishedCount }}</strong
        ><span>普通用户可见课程</span>
      </div>
    </section>
    <div v-if="courses.length" class="course-grid">
      <article
        v-for="item in courses"
        :key="item.course.courseId"
        class="course-card"
      >
        <div class="course-cover">
          <img
            v-if="item.course.coverResourceId"
            :src="coverUrl(item.course.coverResourceId)"
            alt="课程封面"
          /><i v-else class="el-icon-reading" />
        </div>
        <header>
          <div>
            <h2>{{ item.course.name }}</h2>
            <span>{{ item.course.courseType }}</span>
          </div>
          <el-tag
            size="small"
            :type="item.course.enabled ? 'success' : 'info'"
            >{{ item.course.enabled ? "已发布" : "草稿" }}</el-tag
          >
        </header>
        <p>{{ item.course.description || "暂无课程简介" }}</p>
        <div class="resource-list">
          <div v-for="resource in item.resources" :key="resource.resourceId">
            <span
              ><i :class="resourceIcon(resource.resourceType)" />{{
                resource.name
              }}</span
            ><el-button
              v-if="resource.resourceType === 'VIDEO'"
              type="text"
              @click="openResourceBinding(item, resource)"
              >绑定实训</el-button
            ><el-button
              type="text"
              class="unlink"
              @click="unlink(item.course, resource)"
              >移除</el-button
            >
          </div>
          <em v-if="!item.resources.length">暂无课程资源</em>
        </div>
        <footer>
          <el-button size="small" @click="openCourse(item.course)"
            >编辑课程</el-button
          ><el-button
            size="small"
            type="primary"
            plain
            @click="openContent(item)"
            >编辑课程内容</el-button
          ><el-button size="small" plain @click="openChapter(item)"
            >新建章节</el-button
          ><el-button
            size="small"
            :type="item.course.enabled ? 'warning' : 'success'"
            @click="toggle(item.course)"
            >{{ item.course.enabled ? "停用" : "启用" }}</el-button
          >
          <el-button
            size="small"
            type="danger"
            plain
            @click="removeCourse(item.course)"
            >删除</el-button
          >
        </footer>
      </article>
    </div>
    <div v-else class="empty">
      <i class="el-icon-reading" /><strong>暂无课程</strong
      ><span>点击右上角新建课程。</span>
    </div>

    <el-dialog
      :title="editing ? '编辑课程' : '新建课程'"
      :visible.sync="courseDialog"
      width="500px"
      ><el-form :model="form" label-width="90px"
        ><el-form-item label="课程封面"
          ><input
            ref="coverPicker"
            type="file"
            accept="image/*"
            class="hidden-picker"
            @change="uploadCover"
          /><el-button
            icon="el-icon-picture"
            :loading="uploadingCover"
            @click="$refs.coverPicker.click()"
            >上传封面图片</el-button
          ></el-form-item
        ><el-form-item label="课程名称"
          ><el-input v-model.trim="form.name" /></el-form-item
        ><el-form-item label="课程类别"
          ><el-input v-model.trim="form.courseType" /></el-form-item
        ><el-form-item label="课程简介"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3" /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="courseDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveCourse"
          >保存</el-button
        ></span
      ></el-dialog
    >

    <input ref="replacePicker" type="file" class="hidden-picker" @change="replaceResource" />
    <el-dialog title="编辑课程内容" :visible.sync="contentEditorDialog" width="920px" top="5vh">
      <el-tabs v-model="editorTab">
        <el-tab-pane label="课程简介" name="INTRODUCTION">
          <RichHelpEditor v-model="contentForm.introductionHtml" :image-uploader="uploadCourseContentImage" />
        </el-tab-pane>
        <el-tab-pane label="课程大纲" name="OUTLINE">
          <RichHelpEditor v-model="contentForm.outlineHtml" :image-uploader="uploadCourseContentImage" />
        </el-tab-pane>
        <el-tab-pane label="课程目录" name="DIRECTORY" />
        <el-tab-pane label="PPT" name="PPT" />
        <el-tab-pane label="视频" name="VIDEO" />
        <el-tab-pane label="数据资源" name="ARCHIVE" />
      </el-tabs>
      <template v-if="editorTab === 'DIRECTORY'">
        <div class="editor-actions"><el-button type="primary" icon="el-icon-plus" @click="openEditorChapter()">新建章节</el-button></div>
        <el-table :data="editorChapters" size="small">
          <el-table-column prop="chapterName" label="章节名称" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="实训工具" width="110"><template slot-scope="scope">{{ scope.row.practiceTool === 'ANNOTATION' ? '图像标注' : scope.row.practiceTool === 'EDITOR' ? '代码编辑' : '未绑定' }}</template></el-table-column>
          <el-table-column label="操作" width="150"><template slot-scope="scope"><el-button type="text" @click="openEditorChapter(scope.row)">编辑</el-button><el-button type="text" class="unlink" @click="removeChapter(scope.row)">删除</el-button></template></el-table-column>
        </el-table>
      </template>
      <template v-if="!['INTRODUCTION','OUTLINE'].includes(editorTab)">
        <div class="editor-actions"><el-button icon="el-icon-upload2" @click="addEditorResource">上传内容</el-button></div>
        <el-table :data="editorResources" size="small">
          <el-table-column prop="name" label="内容名称" min-width="220" />
          <el-table-column prop="resourceType" label="类型" width="100" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="操作" width="220"><template slot-scope="scope"><el-button type="text" @click="openMetadata(scope.row)">编辑</el-button><el-button type="text" @click="chooseReplacement(scope.row)">替换</el-button><el-button type="text" class="unlink" @click="removeEditorResource(scope.row)">删除</el-button></template></el-table-column>
        </el-table>
      </template>
      <span slot="footer"><el-button @click="contentEditorDialog=false">关闭</el-button><el-button v-if="['INTRODUCTION','OUTLINE'].includes(editorTab)" type="primary" :loading="saving" @click="saveRichContent">保存</el-button></span>
    </el-dialog>
    <el-dialog :title="editingChapterId ? '编辑章节' : '新建章节'" :visible.sync="editorChapterDialog" width="520px">
      <el-form label-width="90px"><el-form-item label="章节名称"><el-input v-model.trim="chapterForm.chapterName" /></el-form-item><el-form-item label="排序"><el-input-number v-model="chapterForm.sortOrder" :min="0" /></el-form-item><el-form-item label="实训工具"><el-select v-model="chapterForm.practiceTool" clearable><el-option label="图像标注" value="ANNOTATION" /><el-option label="代码编辑" value="EDITOR" /></el-select></el-form-item></el-form>
      <span slot="footer"><el-button @click="editorChapterDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEditorChapter">保存</el-button></span>
    </el-dialog>
    <el-dialog title="编辑课程内容信息" :visible.sync="metadataDialog" width="520px">
      <el-form label-width="90px"><el-form-item label="名称"><el-input v-model.trim="metadataForm.name" /></el-form-item><el-form-item label="类型"><el-select v-model="metadataForm.resourceType"><el-option label="课程目录" value="EBOOK" /><el-option label="PPT" value="PPT" /><el-option label="视频" value="VIDEO" /><el-option label="数据资源" value="ARCHIVE" /></el-select></el-form-item><el-form-item label="所属章节"><el-select v-model="metadataForm.chapterId" clearable><el-option v-for="chapter in editorChapters" :key="chapter.chapterId" :label="chapter.chapterName" :value="chapter.chapterId" /></el-select></el-form-item><el-form-item label="实训工具"><el-select v-model="metadataForm.practiceTool" clearable><el-option label="图像标注" value="ANNOTATION" /><el-option label="代码编辑" value="EDITOR" /></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="metadataForm.sortOrder" :min="0" /></el-form-item></el-form>
      <span slot="footer"><el-button @click="metadataDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveMetadata">保存</el-button></span>
    </el-dialog>

    <el-dialog title="添加课程内容" :visible.sync="contentDialog" width="600px"
      ><el-tabs v-model="contentTab"
        ><el-tab-pane label="上传新文件" name="upload"
          ><div class="content-prompt">
            <i class="el-icon-upload2" /><strong
              >上传文件并自动关联当前课程</strong
            ><span>支持电子书、视频、PPT、压缩包或课程数据。</span
            ><el-button type="primary" @click="uploadVisible = true"
              >选择文件并上传</el-button
            >
          </div></el-tab-pane
        ><el-tab-pane label="从课程资源库选择" name="library"
          ><el-select
            v-model="selectedFiles"
            multiple
            filterable
            style="width: 100%"
            placeholder="选择一个或多个资源"
            ><el-option
              v-for="file in availableFiles"
              :key="file.fileId"
              :label="file.fileName"
              :value="file.fileId" /></el-select
          ><el-select
            v-model="selectedType"
            style="width: 100%; margin-top: 12px"
            ><el-option label="电子书" value="EBOOK" /><el-option
              label="视频"
              value="VIDEO" /><el-option label="PPT" value="PPT" /><el-option
              label="压缩包/课程数据"
              value="ARCHIVE" /></el-select></el-tab-pane></el-tabs
      ><span slot="footer"
        ><el-button @click="contentDialog = false">关闭</el-button
        ><el-button
          v-if="contentTab === 'library'"
          type="primary"
          :loading="saving"
          @click="linkSelected"
          >确认关联</el-button
        ></span
      ></el-dialog
    >
    <el-dialog title="新建课程章节" :visible.sync="chapterDialog" width="520px">
      <el-form label-width="100px"
        ><el-form-item label="章节名称"
          ><el-input
            v-model.trim="chapterForm.chapterName"
            placeholder="请输入章节名称" /></el-form-item
        ><el-form-item label="绑定实训"
          ><el-select
            v-model="chapterForm.practiceTool"
            clearable
            style="width: 100%"
            placeholder="可不绑定"
            ><el-option label="图像标注" value="ANNOTATION" /><el-option
              label="代码编辑"
              value="EDITOR" /></el-select></el-form-item
      ></el-form>
      <span slot="footer"
        ><el-button @click="chapterDialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveChapter"
          >保存并上传章节内容</el-button
        ></span
      >
    </el-dialog>
    <el-dialog
      title="绑定视频实训环境"
      :visible.sync="bindingDialog"
      width="500px"
    >
      <el-form label-width="100px"
        ><el-form-item label="课程内容">{{
          bindingResource && bindingResource.name
        }}</el-form-item
        ><el-form-item label="实训工具"
          ><el-select
            v-model="bindingTool"
            clearable
            style="width: 100%"
            placeholder="不绑定"
            ><el-option label="图像标注" value="ANNOTATION" /><el-option
              label="代码编辑"
              value="EDITOR" /></el-select></el-form-item
      ></el-form>
      <span slot="footer"
        ><el-button @click="bindingDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          @click="saveResourceBinding"
          >保存</el-button
        ></span
      >
    </el-dialog>
    <ResourceUploadDialog
      :visible.sync="uploadVisible"
      :admin="true"
      space="course"
      :courses="courses"
      :initial-course-id="selectedCourseId"
      :initial-resource-type="uploadResourceType"
      @uploaded="uploadedContent"
    />
  </section>
</template>
<script>
import ResourceUploadDialog from "@/components/resources/ResourceUploadDialog.vue";
import RichHelpEditor from "@/components/help/RichHelpEditor.vue";
import {
  listAdminCourses,
  createCourse,
  updateCourse,
  deleteCourse,
  createCourseChapter,
  updateCourseChapter,
  deleteCourseChapter,
  listCourseChapters,
  bindCourseResource,
  updateCourseResourceMetadata,
  uploadCourseContentImage,
  setCourseEnabled,
  uploadCourseResource,
} from "@/api/Courses";
import {
  listCourseLibraryFiles,
  linkCourseResource,
  unlinkCourseResource,
  uploadResourceFile,
  deleteResourceFile,
} from "@/api/ResourceSpaces";
export default {
  components: { ResourceUploadDialog, RichHelpEditor },
  data: () => ({
    loading: false,
    saving: false,
    uploadingCover: false,
    courses: [],
    libraryFiles: [],
    courseDialog: false,
    contentDialog: false,
    contentEditorDialog: false,
    editorChapterDialog: false,
    metadataDialog: false,
    uploadVisible: false,
    chapterDialog: false,
    bindingDialog: false,
    editing: false,
    selectedCourse: null,
    contentTab: "upload",
    editorTab: "INTRODUCTION",
    editorChapters: [],
    contentForm: { introductionHtml: "", outlineHtml: "" },
    editingChapterId: "",
    metadataResource: null,
    metadataForm: { name: "", resourceType: "EBOOK", chapterId: "", practiceTool: "", sortOrder: 0 },
    replaceTarget: null,
    selectedFiles: [],
    selectedType: "EBOOK",
    uploadResourceType: "EBOOK",
    pendingChapter: null,
    chapterForm: { chapterName: "", practiceTool: "", sortOrder: 0 },
    bindingResource: null,
    bindingCourseId: "",
    bindingTool: "",
    form: { name: "", courseType: "", description: "", coverResourceId: "" },
  }),
  computed: {
    resourceCount() {
      return this.courses.reduce(
        (sum, item) => sum + (item.resources || []).length,
        0
      );
    },
    publishedCount() {
      return this.courses.filter((item) => item.course.enabled).length;
    },
    selectedCourseId() {
      return (this.selectedCourse && this.selectedCourse.course.courseId) || "";
    },
    availableFiles() {
      const linked = new Set(
        ((this.selectedCourse && this.selectedCourse.resources) || []).map(
          (item) => item.resourceId
        )
      );
      return this.libraryFiles.filter((file) => !linked.has(file.fileId));
    },
    editorResources() {
      const type = this.editorTab === "DIRECTORY" ? "EBOOK" : this.editorTab;
      return ((this.selectedCourse && this.selectedCourse.resources) || []).filter(item => item.resourceType === type);
    },
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [courses, files] = await Promise.all([
          listAdminCourses(),
          listCourseLibraryFiles(),
        ]);
        this.courses = courses.data || [];
        this.libraryFiles = files.data || [];
      } finally {
        this.loading = false;
      }
    },
    openCourse(course) {
      this.editing = !!course;
      this.form = course
        ? Object.assign({}, course)
        : { name: "", courseType: "", description: "", coverResourceId: "" };
      this.courseDialog = true;
    },
    async saveCourse() {
      if (!this.form.name || !this.form.courseType)
        return this.$message.warning("请填写课程名称和类别");
      this.saving = true;
      try {
        await (this.editing
          ? updateCourse(this.form.courseId, this.form)
          : createCourse(this.form));
        this.courseDialog = false;
        this.$message.success("课程已保存");
        await this.load();
      } finally {
        this.saving = false;
      }
    },
    async toggle(course) {
      await setCourseEnabled(course.courseId, !course.enabled);
      await this.load();
    },
    async removeCourse(course) {
      await this.$confirm(
        `确认删除课程“${course.name}”？课程资源库中的原文件会保留。`,
        "删除课程",
        { type: "warning" }
      );
      await deleteCourse(course.courseId);
      this.$message.success("课程已删除");
      await this.load();
    },
    async openContent(item) {
      this.selectedCourse = item;
      this.contentForm = {
        introductionHtml: item.course.introductionHtml || "",
        outlineHtml: item.course.outlineHtml || "",
      };
      this.editorTab = "INTRODUCTION";
      const result = await listCourseChapters(item.course.courseId);
      this.editorChapters = result.data || [];
      this.contentEditorDialog = true;
    },
    uploadCourseContentImage(data) {
      return uploadCourseContentImage(data);
    },
    async refreshEditor() {
      const id = this.selectedCourseId;
      await this.load();
      this.selectedCourse = this.courses.find(item => item.course.courseId === id) || null;
      if (this.selectedCourse) {
        const result = await listCourseChapters(id);
        this.editorChapters = result.data || [];
      }
    },
    async saveRichContent() {
      this.saving = true;
      try {
        await updateCourse(this.selectedCourseId, Object.assign({}, this.selectedCourse.course, this.contentForm));
        this.$message.success("课程内容已保存");
        await this.refreshEditor();
      } finally { this.saving = false; }
    },
    openEditorChapter(chapter) {
      this.editingChapterId = chapter ? chapter.chapterId : "";
      this.chapterForm = chapter
        ? { chapterName: chapter.chapterName, practiceTool: chapter.practiceTool || "", sortOrder: Number(chapter.sortOrder || 0) }
        : { chapterName: "", practiceTool: "", sortOrder: this.editorChapters.length * 10 };
      this.editorChapterDialog = true;
    },
    async saveEditorChapter() {
      if (!this.chapterForm.chapterName) return this.$message.warning("请输入章节名称");
      this.saving = true;
      try {
        if (this.editingChapterId) await updateCourseChapter(this.selectedCourseId, this.editingChapterId, this.chapterForm);
        else await createCourseChapter(this.selectedCourseId, this.chapterForm);
        this.editorChapterDialog = false;
        await this.refreshEditor();
      } finally { this.saving = false; }
    },
    async removeChapter(chapter) {
      const resources = this.chapterResourcesForEditing(chapter.chapterId);
      await this.$confirm(
        resources.length
          ? `章节“${chapter.chapterName}”包含 ${resources.length} 项内容。删除后这些内容将从本课程解除关联，课程资源库原文件仍保留。`
          : `确认删除章节“${chapter.chapterName}”？`,
        "删除章节",
        { type: "warning" }
      );
      for (const resource of resources)
        await unlinkCourseResource(resource.resourceId, this.selectedCourseId);
      await deleteCourseChapter(this.selectedCourseId, chapter.chapterId);
      await this.refreshEditor();
    },
    chapterResourcesForEditing(chapterId) {
      return ((this.selectedCourse && this.selectedCourse.resources) || []).filter(
        resource => resource.chapterId === chapterId
      );
    },
    addEditorResource() {
      this.uploadResourceType = this.editorTab === "DIRECTORY" ? "EBOOK" : this.editorTab;
      this.uploadVisible = true;
    },
    openMetadata(resource) {
      this.metadataResource = resource;
      this.metadataForm = {
        name: resource.name,
        resourceType: resource.resourceType,
        chapterId: resource.chapterId || "",
        practiceTool: resource.practiceTool || "",
        sortOrder: Number(resource.sortOrder || 0),
      };
      this.metadataDialog = true;
    },
    async saveMetadata() {
      if (!this.metadataForm.name) return this.$message.warning("请输入内容名称");
      this.saving = true;
      try {
        await updateCourseResourceMetadata(this.selectedCourseId, this.metadataResource.resourceId, this.metadataForm);
        this.metadataDialog = false;
        await this.refreshEditor();
      } finally { this.saving = false; }
    },
    chooseReplacement(resource) {
      this.replaceTarget = resource;
      this.$refs.replacePicker.value = "";
      this.$refs.replacePicker.click();
    },
    async replaceResource(event) {
      const file = event.target.files && event.target.files[0];
      if (!file || !this.replaceTarget) return;
      this.saving = true;
      const old = this.replaceTarget;
      try {
        const data = new FormData();
        data.append("file", file); data.append("resourceType", old.resourceType); data.append("courseIds", this.selectedCourseId);
        const result = await uploadResourceFile(true, "course", data);
        const added = result.data || result;
        await updateCourseResourceMetadata(this.selectedCourseId, added.fileId, {
          name: old.name, resourceType: old.resourceType, chapterId: old.chapterId || null,
          practiceTool: old.practiceTool || null, sortOrder: old.sortOrder || 0,
        });
        await unlinkCourseResource(old.resourceId, this.selectedCourseId);
        try { await deleteResourceFile(true, "course", old.resourceId); }
        catch (error) { this.$message.warning("旧文件仍被其他课程使用，已仅解除当前课程关联"); }
        this.$message.success("课程内容已替换");
        await this.refreshEditor();
      } finally { this.saving = false; this.replaceTarget = null; }
    },
    async removeEditorResource(resource) {
      await this.$confirm(`确认删除课程内容“${resource.name}”？`, "删除内容", { type: "warning" });
      await unlinkCourseResource(resource.resourceId, this.selectedCourseId);
      try { await deleteResourceFile(true, "course", resource.resourceId); }
      catch (error) { this.$message.warning("文件仍被其他课程使用，已仅解除当前课程关联"); }
      await this.refreshEditor();
    },
    openChapter(item) {
      this.selectedCourse = item;
      this.chapterForm = { chapterName: "", practiceTool: "" };
      this.chapterDialog = true;
    },
    async saveChapter() {
      if (!this.chapterForm.chapterName)
        return this.$message.warning("请输入章节名称");
      this.saving = true;
      try {
        const result = await createCourseChapter(
          this.selectedCourseId,
          this.chapterForm
        );
        this.pendingChapter = result.data || result;
        this.uploadResourceType = "EBOOK";
        this.chapterDialog = false;
        this.uploadVisible = true;
      } finally {
        this.saving = false;
      }
    },
    openResourceBinding(item, resource) {
      this.bindingCourseId = item.course.courseId;
      this.bindingResource = resource;
      this.bindingTool = resource.practiceTool || "";
      this.bindingDialog = true;
    },
    async saveResourceBinding() {
      this.saving = true;
      try {
        await bindCourseResource(
          this.bindingCourseId,
          this.bindingResource.resourceId,
          {
            chapterId: this.bindingResource.chapterId || null,
            practiceTool: this.bindingTool || null,
          }
        );
        this.bindingDialog = false;
        this.$message.success("实训工具绑定已保存");
        await this.load();
      } finally {
        this.saving = false;
      }
    },
    async linkSelected() {
      if (!this.selectedFiles.length)
        return this.$message.warning("请选择课程资源");
      this.saving = true;
      try {
        for (const fileId of this.selectedFiles)
          await linkCourseResource(
            fileId,
            this.selectedCourseId,
            this.selectedType
          );
        this.$message.success("课程资源已关联");
        this.contentDialog = false;
        await this.load();
      } finally {
        this.saving = false;
      }
    },
    async unlink(course, resource) {
      await this.$confirm(
        `从“${course.name}”移除资源“${resource.name}”？文件仍保留在课程资源库。`,
        "移除关联",
        { type: "warning" }
      );
      await unlinkCourseResource(resource.resourceId, course.courseId);
      await this.load();
    },
    async uploadedContent(file) {
      if (this.pendingChapter && file && file.fileId) {
        await bindCourseResource(this.selectedCourseId, file.fileId, {
          chapterId: this.pendingChapter.chapterId,
          practiceTool: this.pendingChapter.practiceTool || null,
        });
        this.pendingChapter = null;
        this.$message.success("章节内容已添加");
      }
      this.contentDialog = false;
      if (this.contentEditorDialog) await this.refreshEditor();
      else await this.load();
    },
    async uploadCover(event) {
      const file = event.target.files && event.target.files[0];
      if (!file) return;
      this.uploadingCover = true;
      try {
        const result = await uploadCourseResource(file);
        this.form.coverResourceId = result.data.storageKey;
        this.$message.success("课程封面上传完成");
      } finally {
        this.uploadingCover = false;
      }
    },
    coverUrl(key) {
      if (!key) return "";
      return key.startsWith("http") || key.startsWith("/files/")
        ? key
        : `/files/${key}`;
    },
    resourceIcon(type) {
      return (
        {
          EBOOK: "el-icon-document",
          VIDEO: "el-icon-video-play",
          PPT: "el-icon-data-board",
          ARCHIVE: "el-icon-folder",
        }[type] || "el-icon-document"
      );
    },
  },
};
</script>
<style scoped>
.course-admin-page {
  width: 100%;
}
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
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
.course-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin-bottom: 18px;
  background: var(--ui-border);
  border: 1px solid var(--ui-border);
}
.course-summary > div {
  padding: 16px 18px;
  background: #fff;
}
.course-summary small,
.course-summary span {
  display: block;
  color: var(--ui-muted);
  font-size: 12px;
}
.course-summary strong {
  display: block;
  margin: 6px 0;
  font-size: 26px;
}
.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}
.course-card {
  min-width: 0;
  padding: 0 16px 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
}
.course-cover {
  display: grid;
  place-items: center;
  height: 120px;
  margin: 0 -16px 14px;
  overflow: hidden;
  background: #edf1f8;
  color: var(--ui-primary);
  font-size: 38px;
}
.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.course-card > header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.course-card h2 {
  margin: 0;
  font-size: 16px;
}
.course-card header span {
  color: var(--ui-muted);
  font-size: 11px;
}
.course-card > p {
  min-height: 40px;
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.6;
}
.resource-list {
  display: grid;
  gap: 5px;
  min-height: 58px;
}
.resource-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.resource-list span {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.resource-list i {
  margin-right: 6px;
  color: var(--ui-primary);
}
.resource-list em {
  color: var(--ui-muted);
  font-size: 12px;
  font-style: normal;
}
.unlink {
  padding: 0;
  color: #d05d5d;
}
.course-card footer {
  display: flex;
  gap: 6px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--ui-border);
}
.course-card footer .el-button {
  margin-left: 0;
}
.empty,
.content-prompt {
  display: grid;
  place-items: center;
  gap: 8px;
  min-height: 260px;
  color: var(--ui-muted);
  background: #fff;
  border: 1px solid var(--ui-border);
  border-radius: 8px;
}
.content-prompt {
  min-height: 220px;
  border: 0;
}
.content-prompt i {
  font-size: 40px;
  color: var(--ui-primary);
}
.content-prompt strong {
  color: var(--ui-text);
}
.hidden-picker {
  display: none;
}
.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin: 10px 0;
}
@media (max-width: 650px) {
  .page-heading {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
  .course-summary,
  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>
