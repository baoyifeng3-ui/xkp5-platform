<template>
  <div class="practical-upload">
    <el-upload
      ref="upload"
      action="#"
      :auto-upload="false"
      accept="image/*"
      list-type="picture-card"
      :on-preview="handlePictureCardPreview"
      :on-remove="handleRemove"
      :on-change="
        (file, fileList) => {
          uploadSuccess(file, fileList);
        }
      "
      :file-list="imageList"
      :disabled="disabled"
    >
      <i class="el-icon-plus"></i>
    </el-upload>
    <el-dialog :visible.sync="dialogVisible" append-to-body class="checkPic">
      <img width="100%" :src="dialogImageUrl" alt="" />
    </el-dialog>

    <el-button
      v-if="!previewMode"
      type="primary"
      class="save-images-button"
      :loading="loading"
      :disabled="loading || disabled"
      @click="upfile"
    >保存图片</el-button>
  </div>
</template>

<script>
import { saveSubjectAnswerApi } from "@/api/Match";
export default {
  data() {
    return {
      actionVal: "",
      actionList: [],
      textarea: "",
      isShowTips: false,
      isShowCard: true,
      isShowIframe: false,
      questionList: [
        {
          question: [],
        },
      ],
      dialogImageUrl: "",
      imageList: [],
      fileList: [],
      dialogVisible: false,
      activeName: "4",
      loading: false,
      dirty: false
    };
  },
  methods: {
    closeAll() {
      this.isShowTips = false;
      this.isShowCard = false;
    },
    handleCard() {
      this.isShowCard = !this.isShowCard;
      this.isShowTips = false;
    },
    handleTips() {
      this.isShowTips = !this.isShowTips;
      this.isShowCard = false;
    },
    doBack() {
      if (this.isShowIframe) {
        this.isShowIframe = false;
      } else {
        this.$router.push({ path: "/Question" });
      }
    },
    async upfile() {
      if (this.previewMode || this.loading || this.disabled) return;
      this.loading = true;
      const val = this.subject;
      let obj = {
        subjectId: val.subject.subjectId,
        answerText: ""
      };
      var formData = new FormData();
      this.fileList.map((v) => {
        if (!v.raw) {
          formData.append("retainedImages", v.url);
        } else {
          formData.append("files", v.raw);
        }
      });
      for (let item in obj) {
        formData.append(item, obj[item]);
      }
      try {
        const result = await saveSubjectAnswerApi(formData);
        if (result.code === 200) {
          const savedImages = String(result.data || "")
            .split(",")
            .filter(Boolean)
            .map((url, index) => ({
              name: `图片${index + 1}`,
              url,
              status: "success",
              uid: `saved-${index}-${url}`
            }));
          this.fileList = savedImages;
          this.imageList = savedImages.slice();
          this.dirty = false;
          this.$message.success("图片已保存");
        }
      } catch (error) {
        // The shared interceptor displays the upload error.
      } finally {
        this.loading = false;
      }
    },
    //删除已上传图片
    handleRemove(file, fileList) {
      if (this.previewMode) return;
      //file是当前删除的图片，fileList是已上传图片列表
      this.imageList = fileList;
      this.fileList = fileList;
      this.dirty = true;
    },
    //图片预览
    handlePictureCardPreview(file) {
      this.dialogImageUrl = file.url;
      this.dialogVisible = true;
    },
    // 上传成功时的钩子
    uploadSuccess(file, fileList) {
      if (this.previewMode) return;
      if (!file.raw) return;
      var fileType = file.raw.type;
      var isJpg = false;
      if (
        fileType == "image/jpeg" ||
        fileType == "image/png" ||
        fileType == "image/jpg"
      ) {
        isJpg = true;
      }
      if (!isJpg) {
        this.$message({
          message: "上传的图标只能是jpg、png格式!",
          type: "error",
        });
        const validFiles = fileList.filter(item => item !== file);
        this.fileList = validFiles;
        this.imageList = validFiles.slice();
        return;
      }
      this.fileList = fileList.slice();
      this.imageList = fileList.slice();
      this.dirty = true;
    },
    hasUnsavedChanges() {
      return this.dirty;
    }
  },
  mounted() {
    if (this.previewMode) return;
    const val = this.subject;
    if (val?.answerSheet?.answerImg) {
      let imgsArr = val.answerSheet.answerImg.split(",");
      imgsArr.map((v) => {
        if (v) {
          var Handsome = {
            name: `图片${this.fileList.length + 1}`,
            status: "success",
            uid: `saved-${this.fileList.length}-${v}`
          };
          Handsome.url = /^(data:image|https?:\/\/|\/files\/)/.test(v)
            ? v
            : "data:image/png;base64," + v;
          this.fileList.push(Handsome);
          this.imageList.push(Handsome);
        }
      });
    }
  },
  props: {
    subject: { type: Object, required: true },
    disabled: { type: Boolean, default: false },
    previewMode: { type: Boolean, default: false }
  },
};
</script>

<style scoped>
@import url(../assets/style/question.css);

.practical-upload { position: relative; padding-bottom: 4px; }
.save-images-button { position: static; margin-top: 12px; }
</style>
