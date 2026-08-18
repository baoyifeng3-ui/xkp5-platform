<template>
    <div>
        <el-carousel v-if="!showDetect" trigger="click" height="500px" :autoplay="false">
            <el-carousel-item v-for="item in 10" :key="item">
                <img :src="require(`../assets/data_set_B/${item}.jpg`)" alt="" />
            </el-carousel-item>
        </el-carousel>
        <el-carousel v-else trigger="click" height="500px" :autoplay="false">
            <el-carousel-item v-for="(item, index) in imgSrc" :key="index">
                <img :src="item" alt="" />
            </el-carousel-item>
        </el-carousel>
        <div class="d-testing">
            <el-button type="primary" :disabled="!t100Url" @click="doCheckUtils" v-loading.fullscreen.lock="fullscreenLoading"
                element-loading-text="拼命加载中" element-loading-spinner="el-icon-loading"
                element-loading-background="rgba(0, 0, 0, 0.8)">检测</el-button>
        </div>
    </div>
</template>

<script>
import { base64ListB } from "@/utils/Base64";
import { mapState } from "vuex";
const { requireMatchingPaper } = require("@/utils/t100Paper");

const PAPER_TYPE = "B";
const PAPER_PATH = "b";

export default {
    data () {
        return {
            data: {},
            imgSrc: [],
            prefix: "",
            showDetect: false,
            anotation: {},
            fullscreenLoading: false,
        };
    },
    computed: {
        ...mapState("Match", ["url", "activePaper"]),
        t100Url () {
            return this.url && this.url.t100Url;
        },
    },
    methods: {
        handleBase64 (event) {
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
                console.log(that.prefix);
            };
        },
        resetDetection () {
            this.imgSrc = [];
            this.anotation = {};
            this.showDetect = false;
        },
        requireCurrentPaper () {
            if (this.activePaper !== PAPER_TYPE) {
                throw new Error(`当前赛卷已切换，${PAPER_TYPE} 卷检测已取消`);
            }
        },
        showInferenceError (error) {
            this.resetDetection();
            this.$message.error((error && error.message) || "推理服务请求失败");
        },
        async doTesting () {
            this.fullscreenLoading = true;
            this.resetDetection();
            try {
                const responses = await Promise.all(base64ListB.map((v, i) => {
                    return this.$axios.post("http://" + this.t100Url + "/api/v2/t100", {
                        ...v,
                        paperType: PAPER_TYPE,
                        name: PAPER_PATH + "/dataset/test/" + (i + 1) + ".jpg",
                    });
                }));
                const anotation = {};
                const imgSrc = responses.map((response) => {
                    const result = requireMatchingPaper(response.data, PAPER_TYPE);
                    if (!result.image || !result.name) {
                        throw new Error("推理服务返回数据不完整");
                    }
                    anotation[result.name] = result.bboxes;
                    return "data:image/jpeg;base64," + result.image;
                });

                this.requireCurrentPaper();
                const scoreResult = await this.$store.dispatch("Match/getScore", {
                    anotation,
                    paperType: PAPER_TYPE,
                });
                if (!scoreResult || Number(scoreResult.code) !== 200) {
                    throw new Error((scoreResult && scoreResult.msg) || "评分失败");
                }

                this.imgSrc = imgSrc;
                this.anotation = anotation;
                this.showDetect = true;
                this.$message.success("检测完成");
            } catch (error) {
                this.showInferenceError(error);
            } finally {
                this.fullscreenLoading = false;
            }
        },
        tryUrl () {
            return this.$store.dispatch("Match/trainUrl");
        },
        async doCheckUtils () {
            try {
                const paperStatus = await this.$store.dispatch("Match/syncActivePaper");
                if (!paperStatus || !paperStatus.synced) {
                    throw new Error("当前赛卷同步失败，请刷新重试");
                }
                this.requireCurrentPaper();
                if (!this.t100Url) {
                    await this.tryUrl();
                }
                if (!this.t100Url) {
                    throw new Error("未分配推理服务");
                }
                const response = await this.$axios.get(
                    "http://" + this.t100Url + "/api/v2/checkUtils_x86",
                    { params: { paperType: PAPER_TYPE } }
                );
                requireMatchingPaper(response.data, PAPER_TYPE);
            } catch (error) {
                this.showInferenceError(error);
                return;
            }
            await this.doTesting();
        },
    },
    mounted () {
        this.tryUrl();
    },
};
</script>

<style scoped></style>
