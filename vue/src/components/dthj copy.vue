<template>
  <div class="q-box">
    <!-- 左侧考题列表 -->
    <div class="q-iframe">
      <iframe :src="iframeSrc" frameborder="0"></iframe>
    </div>
    <!-- 右侧按钮组 -->
    <div class="btn-list">
      <div class="q1" @click="doBack">
        <el-tooltip class="item" effect="dark" content="返回" placement="left">
          <img src="../assets/image/q1.png" alt="" />
        </el-tooltip>
      </div>
      <div class="q2" @click="handleCard">
        <el-tooltip
          class="item"
          effect="dark"
          content="答题卡"
          placement="left"
        >
          <img src="../assets/image/q2.png" alt=""
        /></el-tooltip>
      </div>
      <div class="q2" @click="handleTips">
        <el-tooltip class="item" effect="dark" content="提示" placement="left">
          <img src="../assets/image/q3.png" alt=""
        /></el-tooltip>
      </div>
    </div>
    <!-- 抽屉 提示 -->
    <div class="q-drawer-tips" v-show="isShowTips">
      <div class="dra-title">比赛帮助</div>
      <div class="dra-close" @click="closeAll">x</div>
      <el-collapse v-model="activeName" accordion>
        <el-collapse-item title="Linux命令行代码" name="1">
          <ul>
            <li>
              <span class="li-left">1. wget+源文件</span>
              <span class="li-right">下载指令</span>
            </li>
            <li>
              <span class="li-left">2. tar -xzvf+压缩包</span>
              <span class="li-right">解压指令</span>
            </li>
            <li>
              <span class="li-left">3. rm+源文件</span>
              <span class="li-right">移除指令</span>
            </li>
            <li>
              <span class="li-left">4. cp+源文件+目标路径</span>
              <span class="li-right">拷贝指令</span>
            </li>
            <li>
              <span class="li-left">5. cd+目标路径</span>
              <span class="li-right">进入目标路径指令</span>
            </li>
          </ul>
        </el-collapse-item>
        <el-collapse-item title="比赛参考代码" name="2">
          <ul>
            <li>
              <span class="li-left">1. cd /home/student/contest</span>
              <span class="li-right">进入/home/student/contest目录下</span>
            </li>
            <li>
              <span class="li-left">2. ls -l /home/student/contest</span>
              <span class="li-right"
                >查看/home/student/contest目录下的所有文件列表</span
              >
            </li>
            <li>
              <span class="li-left">3. conda activate contest</span>
              <span class="li-right"
                >激活环境
                <span style="color: red"
                  >（含本次比赛所需用到的所有依赖库、检测库）</span
                ></span
              >
            </li>
            <li>
              <span class="li-left">4. conda deactivate</span>
              <span class="li-right">退出环境</span>
            </li>
            <li>
              <span class="li-left">5. python denoise.py</span>
              <span class="li-right">执行“中值滤波处理”脚本</span>
            </li>
            <li>
              <span class="li-left">6. python bright.py</span>
              <span class="li-right">执行“亮度增强”脚本</span>
            </li>
            <li>
              <span class="li-left"
                >7. python train.py --logtostderr --train_dir checkpoint
                --pipeline_config_path model.config</span
              >
              <span class="li-right">执行“模型训练”脚本</span>
            </li>
            <li>
              <span class="li-left"
                >8. tensorboard --host 0.0.0.0 --port 8889 --logdir
                checkpoint/</span
              >
              <span class="li-right">查看“可视化训练结果”</span>
            </li>
            <li style="height: 40px">
              <span class="li-left"
                >9. python eval.py --logtostderr --checkpoint_dir checkpoint
                --eval_dir evaluation --pipeline_config_path model.config</span
              >
              <span class="li-right">执行“模型评估”脚本</span>
            </li>
            <li>
              <span class="li-left"
                >10. tensorboard --host 0.0.0.0 --port 8889 --logdir
                evaluation/</span
              >
              <span class="li-right">查看“模型精度评估结果”</span>
            </li>
            <li style="height: 60px">
              <span class="li-left"
                >11. python export_fz.py --input_type image_tensor
                --pipeline_config_path model.config --trained_checkpoint_prefix
                checkpoint/model.ckpt-xx --output_directory frozen_models</span
              >
              <span class="li-right"
                >执行“导出冻结模型”脚本
                <span class="red">
                  （xx为model.config文件中num_steps的值）
                </span>
              </span>
            </li>
            <li>
              <span class="li-left">12. python detect.py</span>
              <span class="li-right">执行“模型检测”脚本</span>
            </li>
            <li style="height: 40px">
              <span class="li-left"
                >13. python export_pb.py --pipeline_config_path model.config
                --trained_checkpoint_prefix checkpoint/model.ckpt-xx
                --output_directory tflite_models</span
              >
              <span class="li-right"
                >执行“模型转换to_pb”脚本
                <span class="red">
                  （xx为model.config文件中num_steps的值）
                </span>
              </span>
            </li>
            <li>
              <span class="li-left">14. python pb_to_tflite.py</span>
              <span class="li-right">执行“模型转换pb_to_tflite”脚本</span>
            </li>
          </ul>
        </el-collapse-item>
        <el-collapse-item title="脚本文件地址" name="3">
          <ul>
            <li>
              <span class="li-left">1.“中值滤波处理”脚本</span>
              <span class="li-right"
                >/dataset/A/denoise.py</span
              >
            </li>
            <li>
              <span class="li-left">2.“亮度增强”脚本</span>
              <span class="li-right"
                >/dataset/A/bright.py</span
              >
            </li>
            <li>
              <span class="li-left">3.“模型训练”脚本</span>
              <span class="li-right"
                >/dataset/A/train.py</span
              >
            </li>
            <li>
              <span class="li-left">4.“模型评估”脚本</span>
              <span class="li-right"
                >/dataset/A/eval.py</span
              >
            </li>
            <li>
              <span class="li-left">5.“导出冻结模型”脚本</span>
              <span class="li-right"
                >/dataset/A/export_fz.py</span
              >
            </li>
            <li>
              <span class="li-left">6.“模型检测”脚本</span>
              <span class="li-right"
                >/dataset/A/detect.py</span
              >
            </li>
            <li>
              <span class="li-left">7.“模型转换to_pb”脚本</span>
              <span class="li-right"
                >/dataset/A/export_pb.py</span
              >
            </li>
            <li>
              <span class="li-left">8.“模型转换pb_to_tflite”脚本</span>
              <span class="li-right"
                >/dataset/A/pb_to_tflite.py</span
              >
            </li>
            <li>
              <span class="li-left">9.“推理”脚本</span>
              <span class="li-right"
                >/dataset/A/object_detector.py</span
              >
            </li>
          </ul>
        </el-collapse-item>
        <el-collapse-item title="数据资源地址" name="4">
          <ul>
            <li>
              <span class="li-left">1.数据集地址</span>
              <span class="li-right">/dataset/A/u1.ta</span>
            </li>
            <li>
              <span class="li-left">2.预训练模型地址</span>
              <span class="li-right"
                >/dataset/A/pretrain_model.tar.gz</span
              >
            </li>
            <li>
              <span class="li-left">3.训练数据集地址</span>
              <span class="li-right"
                >/dataset/A/train.tfrecord</span
              >
            </li>
            <li>
              <span class="li-left">4.标签文件地址</span>
              <span class="li-right"
                >/dataset/A/label_map.pbtxt</span
              >
            </li>
            <li>
              <span class="li-left">5.验证数据集地址</span>
              <span class="li-right"
                >/dataset/A/val.tfrecord</span
              >
            </li>
            <li>
              <span class="li-left">6.模型配置文件地址</span>
              <span class="li-right"
                >/dataset/A/model.config</span
              >
            </li>
            <li>
              <span class="li-left">7.测试图片地址</span>
              <span class="li-right"
                >/dataset/A/test.jpg</span
              >
            </li>
            <li>
              <span class="li-left">8.需“中值滤波”处理图像地址</span>
              <span class="li-right"
                >/dataset/A/noise.jpg</span
              >
            </li>
            <li>
              <span class="li-left">9.需“亮度增强”处理图像地址</span>
              <span class="li-right"
                >/dataset/A/dark.jpg</span
              >
            </li>
          </ul>
        </el-collapse-item>
      </el-collapse>
    </div>
    <!-- 抽屉 答题卡 -->
    <div class="q-drawer-card" v-show="isShowCard">
      <div class="dra-title">答题框</div>
      <div class="dra-close" @click="closeAll">x</div>
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
      >
        <i class="el-icon-plus"></i>
      </el-upload>
      <el-dialog :visible.sync="dialogVisible" append-to-body class="checkPic">
        <img width="100%" :src="dialogImageUrl" alt="" />
      </el-dialog>

      <el-button @click="upfile" type="primary" class="dra-button"
        >保存</el-button
      >
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      actionVal: "",
      actionList: [],
      textarea: "",
      isShowTips: false,
      isShowCard: true,
      isShowIframe: false,
      iframeSrc: "",
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
    ///获取时间 年月日时分秒
    getDate() {
      let date = new Date();
      let y = date.getFullYear();
      let MM = date.getMonth() + 1;
      var dd = date.getDate();
      var h = date.getHours(); //小时
      var m = date.getMinutes(); //分
      var s = date.getSeconds(); //秒
      MM = MM < 10 ? "0" + MM : MM;
      dd = dd < 10 ? "0" + dd : dd;
      h = h < 10 ? "0" + h : h;
      m = m < 10 ? "0" + m : m;
      s = s < 10 ? "0" + s : s;
      return `${y}-${MM}-${dd} ${h}:${m}:${s}`;
    },
    async upfile() {
      const id = sessionStorage.getItem("userId");
      const val = this.$route.params.subject;
      const idx = this.$route.params.index;
      let obj = {
        userId: id,
        subjectId: val.subject.subjectId,
        subjectNumber: idx,
        subjectType: "2",
        answerText: "",
        creationTime: this.getDate(),
      };
      var formData = new FormData();
      this.fileList.map((v) => {
        if (!v.raw) {
          let imgfiles = this.base64ToFile(v.url, "upimgname.png");
          formData.append("files", imgfiles);
        } else {
          formData.append("files", v.raw);
        }
      });
      for (let item in obj) {
        formData.append(item, obj[item]);
      }
      let config = {
        headers: { "Content-Type": "multipart/form-data" },
      };
      this.$axios
        .post(
          "/api/testPaper/addAnswer",
          formData,
          config
        )
        .then((res) => {
          if (res.data.code == 200) {
            this.$message.success("提交成功");
            this.$router.push({ path: "/Question" });
          }
        });
    },
    base64ToFile(urlData, fileName) {
      let arr = urlData.split(",");
      let mime = arr[0].match(/:(.*?);/)[1];
      let bytes = atob(arr[1]); // 解码base64
      let n = bytes.length;
      let ia = new Uint8Array(n);
      while (n--) {
        ia[n] = bytes.charCodeAt(n);
      }
      return new File([ia], fileName, { type: mime });
    },
    //删除已上传图片
    handleRemove(file, fileList) {
      //file是当前删除的图片，fileList是已上传图片列表
      this.imageList = fileList;
      this.fileList = fileList;
    },
    //图片预览
    handlePictureCardPreview(file) {
      this.dialogImageUrl = file.url;
      this.dialogVisible = true;
    },
    // 上传成功时的钩子
    uploadSuccess(file, fileList) {
      let that = this;
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
        return false;
      }
      this.fileList = fileList;
      this.imageList = fileList;
    },
  },
  mounted() {
    const val = this.$route.params.subject;
    this.iframeSrc = val.subject.environment;
    if (val?.answerSheet?.answerImg) {
      let imgsArr = val.answerSheet.answerImg.split(",");
      imgsArr.map((v) => {
        if (v) {
          var Handsome = {};
          Handsome.url = "data:image/png;base64," + v;
          this.fileList.push(Handsome);
          this.imageList.push(Handsome);
        }
      });
    }
  },
};
</script>

<style scoped>
@import url(../assets/style/question.css);
</style>
