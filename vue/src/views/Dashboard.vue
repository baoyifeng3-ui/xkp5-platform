<template>
  <div class="dashboard">
    <div v-if="showDashboard">
      <el-row class="d-top">
        <el-col :span="18">
          <div class="grid-content bg-purple">
            <div class="d-block1">
              <div class="d-b-title1">
                分析模型：<span>{{ detectList.analyticalModel }}</span>
              </div>
              <div class="d-b-title2">
                矿物名称：<span>{{
                  detectList.mineralType == "MAGNETITE" ? "磁铁矿" : "萤石"
                }}</span>
              </div>
            </div>
            <div class="d-block2">
              <div class="d-b2-top">
                <div class="d-b2-top-title1">
                  当前样本：
                  <el-tooltip
                    class="item"
                    effect="dark"
                    :content="detectList.sampleCode"
                    placement="top-start"
                  >
                    <span>{{ detectList.sampleCode }}</span>
                  </el-tooltip>
                </div>
                <div class="d-b2-top-title2">
                  样本序号：<span> 第 {{ index }}/{{ total }} 件 </span>
                </div>
                <div class="d-b2-top-icon">
                  <div class="icon-items" @click="toFirst">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 448 512"
                      width="24"
                      height="24"
                      style="
                        border-color: rgba(0, 0, 0, 0);
                        border-width: bpx;
                        border-style: undefined;
                      "
                      filter="none"
                    >
                      <path
                        d="M64 468V44c0-6.6 5.4-12 12-12h48c6.6 0 12 5.4 12 12v176.4l195.5-181C352.1 22.3 384 36.6 384 64v384c0 27.4-31.9 41.7-52.5 24.6L136 292.7V468c0 6.6-5.4 12-12 12H76c-6.6 0-12-5.4-12-12z"
                      ></path>
                    </svg>
                  </div>
                  <div class="icon-items" @click="toPrev">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 320 512"
                      width="24"
                      height="24"
                      style="
                        border-color: rgba(0, 0, 0, 0);
                        border-width: bpx;
                        border-style: undefined;
                      "
                      filter="none"
                    >
                      <path
                        d="M34.52 239.03L228.87 44.69c9.37-9.37 24.57-9.37 33.94 0l22.67 22.67c9.36 9.36 9.37 24.52.04 33.9L131.49 256l154.02 154.75c9.34 9.38 9.32 24.54-.04 33.9l-22.67 22.67c-9.37 9.37-24.57 9.37-33.94 0L34.52 272.97c-9.37-9.37-9.37-24.57 0-33.94z"
                      ></path>
                    </svg>
                  </div>
                  <div class="icon-items" @click="toNext">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 320 512"
                      width="24"
                      height="24"
                      style="
                        border-color: rgba(0, 0, 0, 0);
                        border-width: bpx;
                        border-style: undefined;
                      "
                      filter="none"
                    >
                      <path
                        d="M285.476 272.971L91.132 467.314c-9.373 9.373-24.569 9.373-33.941 0l-22.667-22.667c-9.357-9.357-9.375-24.522-.04-33.901L188.505 256 34.484 101.255c-9.335-9.379-9.317-24.544.04-33.901l22.667-22.667c9.373-9.373 24.569-9.373 33.941 0L285.475 239.03c9.373 9.372 9.373 24.568.001 33.941z"
                      ></path>
                    </svg>
                  </div>
                  <div class="icon-items" @click="toLast">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 448 512"
                      width="24"
                      height="24"
                      style="
                        border-color: rgba(0, 0, 0, 0);
                        border-width: bpx;
                        border-style: undefined;
                      "
                      filter="none"
                    >
                      <path
                        d="M384 44v424c0 6.6-5.4 12-12 12h-48c-6.6 0-12-5.4-12-12V291.6l-195.5 181C95.9 489.7 64 475.4 64 448V64c0-27.4 31.9-41.7 52.5-24.6L312 219.3V44c0-6.6 5.4-12 12-12h48c6.6 0 12 5.4 12 12z"
                      ></path>
                    </svg>
                  </div>
                </div>
              </div>
              <div class="d-b2-center">
                <div class="d-b2-c-items">
                  <div class="items-title">含量百分数</div>
                  <div class="items-num" style="color: #228edc">
                    {{ Number(maxC).toFixed(2) }}%
                  </div>
                </div>
                <div class="d-b2-c-items">
                  <div class="items-title">矿物密度</div>
                  <div class="items-num">
                    {{
                      Number(detectList.diggingsWell.mineralDensity).toFixed(2)
                    }}g/cm³
                  </div>
                </div>
                <div class="d-b2-c-items">
                  <div class="items-title">质量百分数</div>
                  <div class="items-num" style="color: #dc2222">
                    {{ Number(maxM).toFixed(2) }}%
                  </div>
                </div>
              </div>
              <div class="d-b2-bot">
                <div class="d-b2-bot-2">
                  <img
                    :src="formatTif(imgTif)"
                    @click="onloadTif(imgTif)"
                    alt=""
                    style="cursor: pointer"
                  />
                </div>
                <div class="d-b2-bot-3">
                  <img
                    :src="imgPng"
                    alt=""
                    @click="onloadPng(imgPng)"
                    style="cursor: pointer"
                  />
                </div>
              </div>
              <span class="d-b2-more" @click="openDialog">详细&gt;&gt;</span>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="grid-content bg-purple-light">
            <div class="d-block3">
              <div class="d-b3-title">样本详细信息</div>
              <div class="d-b3-list1">
                <ul>
                  <li>
                    <span>采集区域：</span
                    ><span>{{ detectList.diggingsWell.diggings }}</span>
                  </li>
                  <li>
                    <span>钻井号： </span
                    ><span>{{ detectList.diggingsWell.diggingsWellId }}</span>
                  </li>
                  <li>
                    <span>钻井经度：</span>
                    <span>{{ detectList.diggingsWell.longitude }}</span>
                  </li>
                  <li>
                    <span>钻井纬度：</span>
                    <span>{{ detectList.diggingsWell.dimensionality }}</span>
                  </li>
                  <li>
                    <span>{{
                      detectList.diggingsWell.depthOrHeight == "HEIGHT"
                        ? "样本水平高度："
                        : "样本水平深度："
                    }}</span>
                    <span>
                      <span
                        v-if="detectList.diggingsWell.depthOrHeight == 'DEPTH'"
                      >
                        -
                      </span>
                      {{
                        Number(Math.abs(detectList.depthOrHeightValue)).toFixed(
                          2
                        )
                      }}m
                    </span>
                  </li>
                  <li>
                    <span>采集时间：</span>
                    <span>
                      {{ formatDate(detectList.createTime) }}
                    </span>
                  </li>
                  <li>
                    <span>制样时间：</span>
                    <span>
                      {{ formatDate(detectList.updateTime) }}
                    </span>
                  </li>
                </ul>
              </div>
              <div class="d-b3-list2">
                <ul>
                  <li>
                    <span>样本编号：</span>
                    <span class="ybbh">{{ detectList.sampleCode }}</span>
                  </li>
                  <li>
                    <span>入库时间：</span>
                    <span>
                      {{ formatDate(detectList.diggingsWell.samplingTime) }}
                    </span>
                  </li>
                  <li>
                    <span>入库账号：</span>
                    <span>{{ detectList.user.username }}</span>
                  </li>
                  <li>
                    <span>样本数据量：</span>
                    <span>{{
                      detectList.sampleInspectionRecordAll[
                        detectList.sampleInspectionRecordAll.length - 1
                      ].sampleImgInspectionRecordList.length
                    }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-row class="d-bot">
        <el-col :span="24">
          <div class="grid-content bg-purple">
            <div class="d-block4">
              <span class="d-b4-title">相近样本</span>
              <div class="d-b4-list">
                <div
                  class="d-b4-items"
                  v-for="(item, index) in similar"
                  :key="index"
                >
                  <div class="items-img">
                    <img src="../assets/image/3.png" alt="" />
                  </div>
                  <span class="items-label">{{ item.sampleCode }}</span>
                  <span class="items-name">{{ item.user.username }}</span>
                  <span class="items-time">{{
                    formatDate(item.createTime)
                  }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    <span v-else style="font-weight: bold"
      >无目标数据，请先
      <span class="addList" @click="$router.push({ path: '/Sample' })">
        选择要查看的样本</span
      >。
    </span>

    <el-dialog :visible.sync="dialogVisible" width="40%">
      <div class="d-dia">
        <div class="d-dia-top">
          <div class="d-dia-block1">
            <span>含量分析值</span>
            <span> {{ Number(maxObj.contentPercentAvg).toFixed(2) }}%</span>
            <span>{{ formatDate(detail.createTime) }}</span>
          </div>
          <div class="d-dia-block2">
            <LineEcharts :lineArr="lineArr" :maxSList="maxSList" />
          </div>
        </div>
        <div class="d-dia-jug">
          <div class="d-dia-block3">
            <div class="d-b2-top-title1">
              当前样本： <span>{{ detectList.sampleCode }}</span>
            </div>
            <div class="d-b2-top-title2">
              样本序号：<span>
                第 {{ sIndex + 1 }}/{{ sampleInspectionRecordAll.length }} 件
              </span>
            </div>
            <div class="d-b2-top-icon">
              <div class="icon-items" @click="toFirstS">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 448 512"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M64 468V44c0-6.6 5.4-12 12-12h48c6.6 0 12 5.4 12 12v176.4l195.5-181C352.1 22.3 384 36.6 384 64v384c0 27.4-31.9 41.7-52.5 24.6L136 292.7V468c0 6.6-5.4 12-12 12H76c-6.6 0-12-5.4-12-12z"
                  ></path>
                </svg>
              </div>
              <div class="icon-items" @click="toPrevS">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 320 512"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M34.52 239.03L228.87 44.69c9.37-9.37 24.57-9.37 33.94 0l22.67 22.67c9.36 9.36 9.37 24.52.04 33.9L131.49 256l154.02 154.75c9.34 9.38 9.32 24.54-.04 33.9l-22.67 22.67c-9.37 9.37-24.57 9.37-33.94 0L34.52 272.97c-9.37-9.37-9.37-24.57 0-33.94z"
                  ></path>
                </svg>
              </div>
              <div class="icon-items" @click="toNextS">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 320 512"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M285.476 272.971L91.132 467.314c-9.373 9.373-24.569 9.373-33.941 0l-22.667-22.667c-9.357-9.357-9.375-24.522-.04-33.901L188.505 256 34.484 101.255c-9.335-9.379-9.317-24.544.04-33.901l22.667-22.667c9.373-9.373 24.569-9.373 33.941 0L285.475 239.03c9.373 9.372 9.373 24.568.001 33.941z"
                  ></path>
                </svg>
              </div>
              <div class="icon-items" @click="toLastS">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 448 512"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M384 44v424c0 6.6-5.4 12-12 12h-48c-6.6 0-12-5.4-12-12V291.6l-195.5 181C95.9 489.7 64 475.4 64 448V64c0-27.4 31.9-41.7 52.5-24.6L312 219.3V44c0-6.6 5.4-12 12-12h48c6.6 0 12 5.4 12 12z"
                  ></path>
                </svg>
              </div>
            </div>
          </div>
        </div>
        <div class="d-dia-mid">
          <div class="d-dia-block4">
            <div class="dd-b4-left">
              <span
                >当前图片序号：第 <span>{{ sIndex + 1 }}</span> 张</span
              >
              <div>
                <div>当前数据分析值</div>
                <div>{{ Number(maxObj.massPercentAvg).toFixed(2) }}%</div>
              </div>
            </div>
            <div class="dd-b4-center">
              <img :src="formatTif(maxIndex.imgOld)" alt="" />
            </div>
            <div class="dd-b4-right">
              <img :src="maxIndex.imgNew" alt="" />
            </div>
          </div>
        </div>
        <div class="d-dia-bot">
          <div class="d-dia-block5">
            <span>分析历史记录</span>
            <div class="d5Table">
              <el-table
                :header-cell-style="headClass"
                :data="sTable"
                style="width: 100%"
                max-height="100px"
              >
                <el-table-column label="开始时间">
                  <template slot-scope="scope">
                    <span class="sampleCodeSpan">{{
                      formatDate(scope.row.startTime)
                    }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="massPercentAvg" label="质量百分比">
                  <template slot-scope="scope">
                    <span class="sampleCodeSpan">{{
                      Number(scope.row.massPercentAvg).toFixed(2)
                    }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="endTime" label="完成时间">
                  <template slot-scope="scope">
                    <span class="sampleCodeSpan">
                      {{ formatDate(scope.row.endTime) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="contentPercentAvg" label="含量百分比">
                  <template slot-scope="scope">
                    <span class="sampleCodeSpan">{{
                      Number(scope.row.contentPercentAvg).toFixed(2)
                    }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { mapState } from "vuex";
import mixins from "@/mixins";
import LineEcharts from "@/components/LineEcharts";
export default {
  data() {
    return {
      dialogVisible: false,
      sampleNo: 0,
      maxC: 0,
      maxM: 0,
      imgArr: [],
      index: 0,
      detailIndex: 0,
      sampleInspectionRecordAll: [],
      sampleInspectionRecordObj: {},
      sIndex: 0,
      sImgArr: [],
      sampleImgInspectionRecordList: [],
      imgSrc: [],
      imgPng: "",
      imgTif: "",
      sTable: [],
      maxObj: {},
      lineArr: [],
      maxSList: [],
      maxIndex: {},
      showDashboard: false,
    };
  },
  mixins: [mixins],
  computed: {
    ...mapState("Sample", [
      "detectList",
      "similar",
      "total",
      "tableData",
      "detail",
    ]),
  },
  watch: {
    detectList: {
      handler(nV, oV) {
        if (nV) {
          this.getAvg(nV.sampleInspectionRecordAll);
          let sampleImgInspectionRecordList =
            this.detectList.sampleInspectionRecordAll[0]
              .sampleImgInspectionRecordList;
          this.imgArr = sampleImgInspectionRecordList.map((v) => {
            return v.imgOld;
          });
          this.lineArr = sampleImgInspectionRecordList;
          this.setImg(nV.sampleInspectionRecordAll);
        }
      },
      immediate: true,
    },
    sIndex(nV) {
      this.maxIndex = this.maxSList[nV];
    },
  },
  methods: {
    // 下载tif
    onloadTif(tif) {
      let a = document.createElement("a");
      a.href = tif;
      a.click();
    },
    // 查看png
    onloadPng(png) {
      let a = document.createElement("a");
      a.href = png;
      a.target = "_blank";
      a.click();
    },
    // tif转png
    formatTif(tif) {
      if (tif) {
        let arr = tif.split(".");
        arr.splice(arr.length - 1, 1, "jpg");
        return arr.join(".");
      }
    },
    // 设置img
    setImg(img) {
      this.imgSrc = img[img.length - 1];
      this.imgPng = this.imgSrc.imgNewAvg;
      this.imgTif = this.imgSrc.imgOldAvg;
    },
    // 表头样式
    headClass() {
      return "background:#e8e8e8;text-align:center;height:20px!important;";
    },
    // 查看详情
    async openDialog() {
      let data = {
        sampleNo: this.sampleNo,
      };
      await this.$store.dispatch("Sample/browserDetail", data);
      this.dialogVisible = true;
      this.sampleInspectionRecordAll =
        this.detectList.sampleInspectionRecordAll[
          this.detectList.sampleInspectionRecordAll.length - 1
        ].sampleImgInspectionRecordList;
      this.sTable = this.detail.sampleInspectionRecordAll;
      this.maxObj = this.sTable[this.sTable.length - 1];
      this.maxSList = this.maxObj.sampleImgInspectionRecordList;
      this.maxIndex = this.maxSList[this.sIndex];
      this.setRecord();
    },
    setRecord() {
      this.sampleImgInspectionRecordList = [];
      this.sImgArr = this.sampleInspectionRecordAll.map((v) => {
        return v.imgOld;
      });
    },
    // 第一页
    toFirst() {
      if (this.index == 1) {
        this.$message({
          message: "已经是第一项",
          type: "error",
        });
        return;
      }
      this.sampleNo = this.tableData[0].sampleNo;
      this.getDetectList();
      this.index = 1;
    },
    // 最后一页
    toLast() {
      if (this.index == this.tableData.length) {
        this.$message({
          message: "已经是最后一项",
          type: "error",
        });
        return;
      }
      this.sampleNo = this.tableData[this.tableData.length - 1].sampleNo;
      this.getDetectList();
      this.index = this.tableData.length;
    },
    // 上一页
    toPrev() {
      if (this.index == 1) {
        this.$message({
          message: "已经是第一项",
          type: "error",
        });
        return;
      }
      this.sampleNo = this.tableData[this.index - 2].sampleNo;
      this.getDetectList();
      this.index -= 1;
    },
    // 下一页
    toNext() {
      if (this.index == this.tableData.length) {
        this.$message({
          message: "已经是最后一项",
          type: "error",
        });
        return;
      }
      this.sampleNo = this.tableData[this.index].sampleNo;
      this.getDetectList();
      this.index += 1;
    },
    // 第一页S
    toFirstS() {
      if (this.sIndex == 0) {
        this.$message({
          message: "已经是第一项",
          type: "error",
        });
        return;
      }
      this.sIndex = 0;
      this.setRecord();
    },
    // 最后一页S
    toLastS() {
      if (this.sIndex == this.sampleInspectionRecordAll.length - 1) {
        this.$message({
          message: "已经是最后一项",
          type: "error",
        });
        return;
      }
      this.sIndex = this.sampleInspectionRecordAll.length - 1;
      this.setRecord();
    },
    // 上一页S
    toPrevS() {
      if (this.sIndex == 0) {
        this.$message({
          message: "已经是第一项",
          type: "error",
        });
        return;
      }
      this.sIndex -= 1;
      this.setRecord();
    },
    // 下一页S
    toNextS() {
      if (this.sIndex == this.sampleInspectionRecordAll.length - 1) {
        this.$message({
          message: "已经是最后一项",
          type: "error",
        });
        return;
      }
      this.sIndex += 1;
      this.setRecord();
    },
    // 时间格式化
    formatDate(date) {
      return this.handleDate(date);
    },
    // 渲染页面
    getDetectList() {
      let data = {
        sampleNo: this.sampleNo,
      };
      this.$store.dispatch("Sample/getAllDetectListBySampleNo", data);
    },
    // 相似样本
    getSimilar() {
      let data = {
        sampleNo: this.sampleNo,
        pageSize: 4,
      };
      this.$store.dispatch("Sample/getSimilarSampleBySampleNo", data);
    },
    // 含量和质量平均值
    getAvg(all) {
      this.maxC = all[all.length - 1].contentPercentAvg;
      this.maxM = all[all.length - 1].massPercentAvg;
    },
    // 全部列表
    getTableList() {
      let data = {
        search: "",
        pageNum: 1,
        pageSize: 9999,
        fieldName: "",
        isAsc: true,
      };
      this.$store.dispatch("Sample/getListByMutiType", data);
    },
  },
  components: {
    LineEcharts,
  },
  mounted() {
    if (this.$route.params.sampleNo) {
      this.index = this.$route.params.index;
      this.sampleNo = this.$route.params.sampleNo;
      if (this.sampleNo != 0) {
        this.getDetectList();
        this.getSimilar();
      }
    }
    if (this.sampleNo == 0 || this.index == 0) {
      this.showDashboard = false;
    } else {
      this.showDashboard = true;
    }
  },
  created() {
    this.getTableList();
  },
};
</script>

<style>
.ybbh {
  width: 180px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  word-break: break-all;
}

.dCanvasBox canvas {
  width: 304px !important;
  height: 154px !important;
}

.dCanvasBox4 canvas {
  width: 200px !important;
  height: 150px !important;
}

.dCanvasBox4 {
  overflow: hidden;
}

.d-top {
  width: 100%;
  height: 470px;
  margin-top: -5px;
}

.d-block1 {
  width: 980px;
  height: 50px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  position: relative;
  font-size: 16px;
  font-weight: bold;
  color: #000;
  line-height: 50px;
}

.d-block1 div span,
.d-b2-top-title1 span,
.d-b2-top-title2 span {
  color: #228edc;
}

.d-b-title1 {
  width: 300px;
  height: 50px;
  float: left;
  margin-left: 50px;
}

.d-b-title2 {
  width: 300px;
  height: 50px;
  float: left;
  margin-left: 70px;
}

.d-block2 {
  width: 980px;
  height: 400px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  margin-top: 10px;
  position: relative;
}

.d-block3 {
  width: 320px;
  height: 464px;
  margin-right: 22px;
  float: right;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
}

.d-bot {
  width: 100%;
  height: 145px;
  margin-top: 10px;
}

.d-block4 {
  width: 1320px;
  height: 135px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  position: relative;
}

.d-b2-top {
  width: 100%;
  height: 30px;
  margin-top: 20px;
}

.d-b2-top-title1 {
  width: 250px;
  height: 30px;
  line-height: 30px;
  font-size: 16px;
  color: #000;
  margin-left: 50px;
  float: left;
  font-weight: bold;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  word-break: break-all;
}

.d-b2-top-title2 {
  width: 250px;
  height: 30px;
  line-height: 30px;
  font-size: 16px;
  color: #000;
  margin-left: 100px;
  float: left;
  font-weight: bold;
}

.d-b2-top-icon {
  width: 186px;
  height: 24px;
  float: right;
  margin-right: 50px;
  margin-top: 3px;
}

.icon-items {
  width: 24px;
  height: 24px;
  float: left;
  cursor: pointer;
  margin-right: 30px;
}

.icon-items:nth-child(4) {
  margin-right: 0;
}

.d-b2-center {
  width: 100%;
  height: 120px;
  margin-top: 20px;
  position: relative;
}

.d-b2-c-items {
  height: 120px;
  width: 250px;
  float: left;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  text-align: center;
}

.d-b2-c-items:nth-child(1) {
  margin-left: 50px;
}

.d-b2-c-items:nth-child(3) {
  float: right;
  margin-right: 50px;
}

.d-b2-c-items:nth-child(2) {
  position: absolute;
  left: 50%;
  margin-left: -125px;
}

.items-title {
  font-size: 16px;
  font-weight: bold;
  color: #000;
  margin-top: 20px;
}

.items-num {
  font-size: 30px;
  font-weight: bold;
  margin-top: 20px;
}

.d-b2-bot {
  width: 100%;
  height: 150px;
  margin-top: 20px;
}

.d-b2-bot-2 {
  width: 300px;
  height: 150px;
  float: left;
  margin-left: 150px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
}

.d-b2-bot-3 {
  width: 300px;
  height: 150px;
  float: right;
  margin-right: 150px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
}

.d-b2-bot-2 img,
.d-b2-bot-3 img {
  width: 100%;
  height: 100%;
}

.d-b2-more {
  position: absolute;
  bottom: 5px;
  right: 5px;
  font-size: 12px;
  color: #94979d;
  cursor: pointer;
}

.d-b3-title {
  width: 80%;
  height: 30px;
  margin: 20px auto 0;
  border-bottom: 1px solid #bbbbbb;
  text-align: center;
  font-size: 14px;
  font-weight: bold;
}

.d-b3-list1 {
  width: 80%;
  height: 220px;
  margin: 20px auto 0;
  border-bottom: 1px solid #bbbbbb;
}

.d-b3-list1 ul,
.d-b3-list2 ul {
  width: 100%;
  padding-inline-start: 0;
}

.d-b3-list1 li,
.d-b3-list2 li {
  height: 20px;
  margin-bottom: 10px;
  font-size: 14px;
  list-style: none;
}

.d-b3-list1 li span:nth-child(1),
.d-b3-list2 li span:nth-child(1) {
  float: left;
  color: #000;
  font-weight: bold;
}

.d-b3-list1 li span:nth-child(2),
.d-b3-list2 li span:nth-child(2) {
  float: right;
  color: #3d9bdf;
}

.d-b3-list2 {
  width: 80%;
  height: 120px;
  margin: 20px auto 0;
}

.d-b4-title {
  position: absolute;
  left: 50px;
  top: 10px;
  font-size: 14px;
  font-weight: bold;
}

.d-b4-list {
  width: 1200px;
  height: 70px;
  position: absolute;
  left: 50px;
  top: 40px;
}

.d-b4-items {
  width: 200px;
  height: 70px;
  box-shadow: 0px 2px 5px 0px rgba(187, 187, 187, 62);
  border: 3px solid rgba(34, 142, 220, 67);
  float: left;
  margin-right: 30px;
  position: relative;
}

.items-img {
  position: absolute;
  width: 40px;
  height: 40px;
  left: 5px;
  top: 8px;
}

.items-img img {
  width: 40px;
  height: 40px;
}

.items-label {
  position: absolute;
  color: #228edc;
  left: 50px;
  top: 10px;
  font-size: 14px;
  font-weight: bold;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  word-break: break-all;
  display: inline-block;
  width: 150px;
}

.items-name {
  position: absolute;
  font-size: 9px;
  color: #94979d;
  left: 50px;
  top: 30px;
}

.items-time {
  font-size: 9px;
  position: absolute;
  color: #94979d;
  left: 5px;
  bottom: 8px;
}

.dashboard .el-dialog {
  height: 530px;
}

.d-dia {
  width: 570px;
  height: 450px;
}

.d-dia-top {
  width: 570px;
  height: 100px;
}

.d-dia-block1 {
  width: 130px;
  height: 100px;
  float: left;
  position: relative;
}

[class^="d-dia-block"] {
  box-shadow: 0px 1px 4px 0px rgba(0, 0, 0, 0.24);
  border: 1px solid #bbb;
  box-sizing: border-box;
}

.d-dia-block2 {
  width: 420px;
  height: 100px;
  float: right;
}

.d-dia-block2 img {
  width: 420px;
  height: 100px;
}

.d-dia-jug {
  width: 570px;
  height: 30px;
  margin-top: 10px;
}

.d-dia-block3 {
  width: 570px;
  height: 30px;
}

.d-dia-block3 .d-b2-top-title1 {
  font-size: 12px;
  margin-left: 10px;
  width: 180px;
}

.d-dia-block3 .d-b2-top-title2 {
  font-size: 12px;
  margin-left: 10px;
  width: 180px;
}

.d-dia-block3 .d-b2-top-icon {
  margin-right: 0px;
  width: 170px;
}

.d-dia-block3 .d-b2-top-icon .icon-items {
  margin-right: 15px;
}

.d-dia-mid {
  width: 570px;
  height: 150px;
  margin-top: 10px;
}

.d-dia-block4 {
  width: 570px;
  height: 150px;
}

.d-dia-bot {
  width: 570px;
  height: 120px;
  margin-top: 10px;
}

.d-dia-block5 {
  width: 570px;
  height: 120px;
  position: relative;
}

.d-dia-block1 span:nth-child(1) {
  font-size: 12px;
  font-weight: bold;
  position: absolute;
  left: 10px;
  top: 10px;
}

.d-dia-block1 span:nth-child(2) {
  font-size: 14px;
  font-weight: bold;
  position: absolute;
  left: 10px;
  top: 35px;
  color: #228edc;
}

.d-dia-block1 span:nth-child(3) {
  font-size: 9px;
  position: absolute;
  left: 10px;
  top: 60px;
  color: #228edc;
}

.dd-b4-left {
  width: 150px;
  height: 150px;
  float: left;
  position: relative;
}

.dd-b4-left > span {
  position: absolute;
  top: 5px;
  left: 10px;
  font-size: 12px;
  font-weight: bold;
  color: #000;
}

.dd-b4-left > span span {
  color: #228edc;
}

.dd-b4-left > div {
  width: 140px;
  height: 110px;
  border: 1px solid #bbb;
  box-sizing: border-box;
  position: absolute;
  bottom: 10px;
  left: 10px;
}

.dd-b4-left > div div:nth-child(1) {
  width: 100%;
  text-align: center;
  height: 12px;
  line-height: 12px;
  font-weight: bold;
  color: #000;
  font-size: 12px;
  position: absolute;
  top: 20px;
}

.dd-b4-left > div div:nth-child(2) {
  width: 100%;
  text-align: center;
  height: 16px;
  line-height: 16px;
  font-weight: bold;
  color: #228edc;
  font-size: 16px;
  position: absolute;
  top: 60px;
}

.dd-b4-center {
  width: 200px;
  height: 150px;
  margin-left: 10px;
  float: left;
  box-sizing: border-box;
}

.dd-b4-right {
  width: 200px;
  height: 150px;
  float: right;
  box-sizing: border-box;
}

.dd-b4-center img,
.dd-b4-right img {
  width: 200px;
  height: 150px;
}

.d-dia-block5 span {
  font-size: 12px;
  font-weight: bold;
  color: #000;
  position: absolute;
  left: 10px;
  top: 5px;
}

.d5table {
  width: 545px;
  height: 200px;
  position: absolute;
  bottom: 5px;
  left: 10px;
}

.addList {
  color: #228edc;
  cursor: pointer;
}

.sampleCodeSpan {
  display: inline-block;
  width: 133px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  word-break: break-all;
}
</style>