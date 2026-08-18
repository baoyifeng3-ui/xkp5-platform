<template>
  <div class="p-box">
    <div class="p-left">
      <div class="p-top">
        <section class="p-top-section p-announcement">
          <span class="p-t-title">比赛公告</span>
          <div class="p-table1">
            <el-table :data="tableData1" empty-text="暂无比赛账号" :cell-style="rowClass" :header-cell-style="headClass"
                      style="width: 100%">
              <el-table-column v-for="field in announcementFields" :key="field.fieldKey" :prop="field.fieldKey"
                               :label="field.fieldName" :width="field.fieldKey === 'sequence' ? 70 : undefined">
                <template slot-scope="scope">{{ displayAnnouncementValue(scope.row[field.fieldKey]) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="p-top-section p-notice">
          <span class="p-t-title">注意事项</span>
          <div class="p-table2">
            <div class="p2-body">
              <div class="name2">
                <div v-for="(v, i) in NoticeData" :key="i" class="notice-content">
                  {{ v.noticeContent }}
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
      <div class="p-bottom">
        <span class="p-t-title">比赛进度</span>
        <el-table :data="tableData3" empty-text="暂无比赛进度" style="width: 100%" :cell-style="rowClass"
                  :header-cell-style="headClass">
          <el-table-column prop="name" label="参赛队编号"></el-table-column>
          <el-table-column prop="progress" label="答题进度">
            <template slot-scope="scope">
              <div>
                <el-progress :percentage="scope.row.bfb" :color="customColors"></el-progress>
              </div>
            </template>
          </el-table-column>
          <el-table-column width="100">
            <template slot-scope="scope">
              <div>{{ scope.row.dt }} / {{ scope.row.all }}</div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <div class="p-right">
      <div class="p-table4">
        <span class="p-t-title">比赛排名</span>
        <el-table :data="tableData4" :cell-style="rowClassTwo" :header-cell-style="headClassTwo"
                  style="width: 100%">
          <el-table-column type="index" label="排名" width="80">
          </el-table-column>
          <el-table-column prop="" label="参赛队编号">
            <template slot-scope="scope">
              <div>
                {{ scope.row.user ? scope.row.user.userName : "" }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="score" label="分值"></el-table-column>
          <el-table-column label="提交时间">
            <template slot-scope="scope">
              <div>
                {{ scope.row.createTime ? formatDate(scope.row.createTime) : "" }}
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script>
import {getNoticelist, scoreTopApi, getTeamInformation} from "@/api/Match";
import mixins from "@/mixins";
export default {
  data() {
    return {
      // 题目长度
      questionLength: 40,
      // 进度条颜色
      customColors: [
        {color: "#f56c6c", percentage: 20},
        {color: "#e6a23c", percentage: 40},
        {color: "#5cb87a", percentage: 60},
        {color: "#1989fa", percentage: 80},
        {color: "#6f7ad3", percentage: 100},
      ],
      tableData4: [],
      tableData3: [],
      tableData1: [],
      announcementFields: [],
      timer1: null,
      timer2: null,
      timer3: null,
      timer4: null,
      timer5: null,
      NoticeData: [],
    };
  },
  mixins: [mixins],
  mounted() {
    this.getCompetitionRanking();
    this.getRankData();
    this.timer5 = setInterval(() => {
      this.getRankData();
    }, 10000);
    sessionStorage.removeItem("route_sjactive");
    this.setscroll();
  },
  methods: {
    // 时间格式化
    formatDate(date) {
      return this.handleDate(date);
    },
    // 表格样式
    rowClass() {
      return "text-align:left !important";
    },
    // 表头样式
    headClass() {
      return "text-align:left !important";
    },
    rowClassTwo() {
      return "text-align:center !important";
    },
    headClassTwo() {
      return "text-align:center !important";
    },
    async getCompetitionRanking() {
      this.tableData4 = [];
      this.NoticeData = [];
      const rs = await scoreTopApi();
      if (rs.code == 200) {
        // this.tableData4 = rs.data;
        this.tableData4 = rs.data.filter((v, i) => {
          if (v.score != null) {
            return v;
          }
        });
      }
      const rs2 = await getNoticelist();
      if (rs2.code == 200) this.NoticeData = rs2.data;
    },
    async getRankData() {
      const rs2 = await getTeamInformation();
      if (rs2.code == 200) {
        const data = rs2.data || {};
        this.announcementFields = Array.isArray(data.fields) ? data.fields : [];
        this.tableData1 = Array.isArray(data.rows) ? data.rows : [];
        this.tableData3 = (Array.isArray(data.progress) ? data.progress : []).map((v) => ({
          name: v.userName || "",
          all: Number(v.all || 0),
          bfb: Number(v.percentage || 0),
          dt: Number(v.present || 0),
        }));
        this.tableData3.sort((a, b) => {
          return b.dt - a.dt;
        });
      }
    },
    displayAnnouncementValue(value) {
      return value === null || value === undefined || String(value).trim() === '' ? '--' : value;
    },
    setscrollCreated(dataname, classnameone, classnametwo, time = 500) {
      this[dataname] = setInterval(() => {
        const box = document.querySelector(`.${classnameone}`);
        const boxheight = window.getComputedStyle(
            document.querySelector(`.${classnameone}`)
        ).height;
        const boxheightval = boxheight.substring(0, boxheight.length - 2) * 1;
        const tableheight = window.getComputedStyle(
            document.querySelector(`.${classnametwo}`)
        ).height;
        const tableheightval =
            tableheight.substring(0, tableheight.length - 2) * 1;
        let boxscrohei = box.scrollTop + boxheightval;
        if (tableheightval <= boxscrohei + 1) {
          box.scrollTop = 0;
        } else {
          box.scrollTop += 10;
        }
      }, time);
    },
    setscroll() {
      this.setscrollCreated(
          "timer1",
          "p-table1 .el-table__body-wrapper",
          "p-table1 .el-table__body"
      );
      this.setscrollCreated(
          "timer2",
          "p-bottom .el-table__body-wrapper",
          "p-bottom .el-table__body"
      );
      this.setscrollCreated(
          "timer3",
          "p-table4 .el-table__body-wrapper",
          "p-table4 .el-table__body"
      );
      this.setscrollCreated("timer4", "p2-body", "name2");
    },
  },
  beforeDestroy() {
    clearInterval(this.timer1);
    clearInterval(this.timer2);
    clearInterval(this.timer3);
    clearInterval(this.timer4);
    clearInterval(this.timer5);
    this.timer1 = null;
    this.timer2 = null;
    this.timer3 = null;
    this.timer4 = null;
    this.timer5 = null;
  },
};
</script>

<style scoped>
@import url(../assets/style/publicity.css);
.notice-content { white-space: pre-wrap; }
</style>
