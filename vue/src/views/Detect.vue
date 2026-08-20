<template>
  <div class="detect-page">
    <header class="detect-heading">
      <div><span>成果验证</span><h1>模型检测与评分</h1></div>
      <el-tag size="small" type="info">{{ activePaper ? activePaper + ' 卷' : '未选择赛卷' }}</el-tag>
    </header>
    <div v-if="!paperStatusReady" class="no-plan">
      <i class="el-icon-loading" />{{ paperSyncFailed ? '当前赛卷同步失败，请刷新重试' : '正在同步当前赛卷' }}
    </div>
    <div v-if="previewOnly" class="preview-notice"><i class="el-icon-view" />比赛预览为只读模式，检测与评分操作已停用。</div>
    <div v-if="paperStatusReady && activePaper === 'B'" :class="['block', { 'is-preview': previewOnly }]">
      <DetectB />
    </div>
    <div v-else-if="paperStatusReady && activePaper === 'A'" :class="['block', { 'is-preview': previewOnly }]">
      <DetectA />
    </div>
    <div v-else-if="paperStatusReady" class="no-plan">
      <i class="el-icon-warning-outline" />{{ activePaper ? `${activePaper} 卷尚未配置检测样例` : '请先选择赛卷' }}
    </div>
  </div>
</template>

<script>
import { mapState } from "vuex";
import DetectB from "@/views/DetectB.vue";
import DetectA from "@/views/DetectA.vue";
export default {
  components: {
    DetectB,
    DetectA,
  },
  data () {
    return {
      paperStatusReady: false,
      paperSyncFailed: false,
    };
  },
  computed: {
    ...mapState("Match", ["activePaper"]),
    previewOnly () {
      return this.$route.query.preview === '1';
    },
  },
  async created () {
    try {
      const result = await this.$store.dispatch("Match/syncActivePaper");
      this.paperStatusReady = Boolean(result && result.synced);
      this.paperSyncFailed = !this.paperStatusReady;
    } catch (error) {
      this.paperSyncFailed = true;
    }
  },
};
</script>

<style scoped>
@import url(../assets/style/detect.css);
.preview-notice { margin-bottom: 14px; padding: 12px 16px; color: #536875; background: #edf4f1; border: 1px solid #cfe1da; }
.preview-notice i { margin-right: 8px; color: #2b7b60; }
.block.is-preview { pointer-events: none; opacity: .72; }
</style>
