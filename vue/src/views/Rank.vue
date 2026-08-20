<template>
  <div>
    <div class="r-table">
      <!-- {{ tableData }} -->
      <el-table
        :data="tableData"
        style="width: 100%"
        :header-cell-style="headClass"
        :cell-style="rowClass"
        max-height="580px"
      >
        <el-table-column type="index" label="排名" width="300">
        </el-table-column>
        <el-table-column prop="user.userName" label="队伍编号" width="300">
        </el-table-column>
        <el-table-column prop="score" label="分数" width="300">
        </el-table-column>
        <el-table-column prop="createTime" label="最优成绩提交时间" width="300">
          <template slot-scope="scope">
            <span>{{ formatDate(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script>
import { mapState } from "vuex";
import mixins from "@/mixins";
export default {
  data() {
    return {};
  },
  mixins: [mixins],
  computed: {
    ...mapState("Match", ["tableData"]),
  },
  methods: {
    formatDate(date) {
      if (date) {
        return this.handleDate(date);
      }
    },
    headClass() {
      return "text-align:center;color:#868180";
    },
    rowClass() {
      return "text-align:center";
    },
    setTop() {
      this.$store.dispatch("Match/scoreTop");
    },
  },
  mounted() {
    this.setTop();
  },
};
</script>

<style scoped>
@import url(../assets/style/rank.css);
</style>