<template>
  <div>
    <el-carousel
      v-if="!showDetect"
      trigger="click"
      height="500px"
      :autoplay="false"
    >
      <el-carousel-item v-for="item in 10" :key="item">
        <img :src="require(`../assets/data_set_A/${item}.jpg`)" alt="" />
      </el-carousel-item>
    </el-carousel>
    <el-carousel v-else trigger="click" height="500px" :autoplay="false">
      <el-carousel-item v-for="(item, index) in imgSrc" :key="index">
        <img :src="item" alt="" />
      </el-carousel-item>
    </el-carousel>
    <div class="d-testing">
      <el-button
        type="primary"
        @click="doCheckUtils"
        :disabled="!url"
        v-loading.fullscreen.lock="fullscreenLoading"
        element-loading-text="拼命加载中"
        element-loading-spinner="el-icon-loading"
        element-loading-background="rgba(0, 0, 0, 0.8)"
        >检测</el-button
      >
    </div>
  </div>
</template>

<script>
import { base64ListA } from "@/utils/Base64";
import { mapState } from "vuex";
export default {
  data() {
    return {
      data: {},
      imgSrc: [],
      prefix: "",
      showDetect: false,
      scoreData: [],
      anotation: {},
      fullscreenLoading: false,
    };
  },
  computed: {
    ...mapState("Match", ["url"]),
  },
  watch: {
    imgSrc(newValue, oldValue) {
      if (newValue.length == 10) {
        this.showDetect = true;
      }
    },
    scoreData(newValue, oldValue) {
      if (newValue.length == 10) {
        newValue.map((v) => {
          Object.assign(this.anotation, v);
        });
        this.$store
          .dispatch("Match/getScore", {
            anotation: this.anotation,
            paperType: "a",
          })
          .then((res) => {
            if (res.code == 200) {
              this.$message({
                message: "检测完成",
                type: "success",
              });
              this.fullscreenLoading = false;
            }
          });
      }
    },
  },
  methods: {
    handleBase64(event) {
      let etf = event.target.files;
      let reader = new FileReader();
      let that = this;
      reader.readAsDataURL(etf[0]);
      reader.onload = function (e) {
        let ecr = e.currentTarget.result;
        that.prefix = ecr.split(",")[0];
        let base64 = ecr.split(",")[1];
        that.data = {
          image: base64,
        };
        console.log(that.data);
      };
    },
    doTesting() {
      this.fullscreenLoading = true;
      this.imgSrc = [];
      this.scoreData = [];
      let that = this;
      base64ListA.forEach((v, i) => {
        this.$axios
          .post("http://" + this.url.t100Url + "/api/v2/t100", {
            ...v,
            name: "a/dataset/test/" + (i + 1) + ".jpg",
          })
          .then((res) => {
            that.imgSrc.push("data:image/jpeg;base64," + res.data.data.image);
            that.scoreData.push({
              [res.data.data.name]: res.data.data.bboxes,
            });
          });
      });
    },
    tryUrl() {
      this.$message({
        message: "网络异常，请重试",
        type: "error",
      });
      this.$store.dispatch("Match/trainUrl");
      if (this.url) {
        return;
      }
      this.tryUrl();
    },
    doCheckUtils() {
      if (!this.url) {
        this.tryUrl();
      }
      this.$axios
        .get("http://" + this.url.t100Url + "/api/v2/checkUtils_x86")
        .then((res) => {
          if (res.data.code == 1002) {
            this.$alert(res.data.message, "", {
              confirmButtonText: "确定",
            });
          } else {
            this.doTesting();
          }
        });
    },
  },
  mounted() {
    this.$store.dispatch("Match/trainUrl");
  },
};
</script>

<style scoped>
</style>
