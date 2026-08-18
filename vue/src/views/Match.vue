<template>
    <div class="m-box">
        <div class="m-tips">
            <div class="m-title"></div>
            <div class="tips1">
                <div class="tips-page">▶</div>
                <div class="tips2line">
                    实操题答题过程中选手请先将截图保存在桌面"实操题答案保存"目录中,在上传到平台中，避免出现上传错误截图的情况发生。
                </div>
            </div>
            <div class="tips2">
                <div class="tips-page">▶</div>
                <div class="tips3line">
                    为避免同步赛卷时误操作导致答案丢失，请选手在进行简答题作答之前先在"实操题答案保存"下的"简答题草稿"文件中作答，在复制到简答题答题框中保留原始答题资料。
                    <!-- 交卷开放时间为<span>13:30-14:10</span>参赛队伍务必控制时间，否则后果自负。 -->
                </div>
            </div>
            <div class="tips3">
                <div class="tips-page">▶</div>
                <div class="tips3line">
                    两名选手需分工合作，答题完成后由队长点击同步试卷，同步完成后队长需核对试卷答案无误后进行提交，并将试卷保存在桌面"实操题答案保存"下的"试卷提交"目录中。
                    <!-- 赛题分为A、B卷，请根据现场抽取结果选择正确的赛卷。<span>赛卷选择错误，计为零分</span>。参赛队伍需根据赛卷要求进行答题，务必审题清楚，以免出现差错。 -->
                </div>
            </div>
            <div class="tips4">
                <div class="tips-page">▶</div>
                <div class="tips5line">
                    请参赛队伍<span>保持诚信比赛的原则</span>，不利用各种方式在比赛期间向场外进行赛题有关的求助/答案或是影响其他队伍。如有发现以上行为，将取消该支队伍的比赛成绩。
                </div>
            </div>
            <div class="tips-btn">
                <el-button type="primary" :disabled="!canClk" @click="doMatch">阅读完毕 <span v-if="!canClk">({{ countTime
                }})</span>
                </el-button>
            </div>
        </div>
    </div>
</template>

<script>
export default {
    data () {
        return {
            canClk: false,
            countTime: location.hash == "#/Match?lizongyao=dashuaige" ? 1 : 30,
            timer: null,
        };
    },
    methods: {
        runTime () {
            this.timer = setInterval(() => {
                this.countTime--;
                if (this.countTime <= 0) {
                    this.stopTime();
                }
            }, 1000);
        },
        stopTime () {
            clearInterval(this.timer);
            this.timer = null;
            this.canClk = true;
            return;
        },
        doMatch () {
            this.$confirm("确定选择B卷", "提示", {
                confirmButtonText: "确定",
                cancelButtonText: "取消",
                type: "warning",
            })
                .then(() => {
                    this.$store.commit("Match/SET_PLAN", "B");
                    this.$router.push({
                        name: "Question",
                        params: { matchType: this.matchType },
                    });
                })
                .catch(() => { });
        },
        getMatchType () {
            this.matchType = "B";
        },
    },
    mounted () {
        this.runTime();
        this.getMatchType();
    },
};
</script>

<style scoped>
@import url(../assets/style/match.css);
</style>