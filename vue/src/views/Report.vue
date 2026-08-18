<template>
  <div>
    <div class="r-box">
      <div class="r-tab">
        <div class="r-tab-top">
          <div class="park" @click="handlePark">
            <img
              v-show="parkActive"
              src="@/assets/icon/iconPark-on.png"
              alt=""
            />
            <img v-show="!parkActive" src="@/assets/icon/iconPark.png" alt="" />
          </div>
        </div>
        <div class="r-tab-bot">
          <div class="line" @click="handleLine">
            <img
              v-show="lineActive"
              src="@/assets/icon/antOutline-on.png"
              alt=""
            />
            <img
              v-show="!lineActive"
              src="@/assets/icon/antOutline.png"
              alt=""
            />
          </div>
        </div>
      </div>
      <div class="r-view">
        <div v-show="parkActive" class="r-view-park">
          <div class="park-logo">
            <img src="@/assets/icon/iconPark.png" alt="" />
          </div>
          <span class="park-title">地图</span>
          <div class="park-select">
            <div class="park-sel1">
              <span>矿场名称</span>
              <el-select v-model="value" placeholder="请选择">
                <el-option
                  v-for="item in options"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </div>
            <div class="park-sel2">
              <span>钻井</span>
              <el-select v-model="value" placeholder="请选择">
                <el-option
                  v-for="item in options"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                >
                </el-option>
              </el-select>
            </div>
          </div>
          <div class="park-echarts">
            <Echarts />
          </div>
        </div>
        <div v-show="lineActive" class="r-view-line">
          <div class="park-logo">
            <img src="@/assets/icon/antOutline.png" alt="" />
          </div>
          <span class="park-title">表格</span>
          <div class="park-select">
            <div class="line-input">
              <el-input
                placeholder="请输入关键字"
                prefix-icon="el-icon-search"
                v-model="input"
              >
              </el-input>
            </div>
            <div class="line-total">
              在 <span>{{ total }}</span> 条记录中找到
              <span>{{ total }}</span> 个
            </div>
          </div>
          <div class="line-table">
            <el-table
              :data="tableData"
              :header-cell-style="headClass"
              :cell-style="rowClass"
              border
              style="width: 100%"
              max-height="250"
            >
              <el-table-column fixed label="操作">
                <template>
                  <el-button
                    type="text"
                    size="small"
                    @click="$router.push({ path: 'Analysis' })"
                    >查看</el-button
                  >
                  <el-button type="text" size="small">删除</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="tableUpdateTime" label="查看更新时间">
                <template slot-scope="scope">
                  <span>{{ formatDate(scope.row.tableUpdateTime) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="diggings" label="矿产名称">
              </el-table-column>
              <el-table-column prop="tableSampleCount" label="所含岩心数">
              </el-table-column>
              <el-table-column prop="well" label="样本量"> </el-table-column>
            </el-table>
          </div>
          <div class="s-footer">
            <div class="s-footer-icon">
              <el-button-group>
                <el-button icon="el-icon-delete"></el-button>
                <el-button icon="el-icon-edit"></el-button>
                <el-button icon="el-icon-share" @click="onExport"></el-button>
              </el-button-group>
            </div>
            <div class="s-header-page">
              <el-button :disabled="current == 1" class="toPrev" @click="toPrev"
                >上一页</el-button
              >
              <div class="s-header-page-span">
                <span>{{ current }}</span
                >/{{ pages }}
              </div>
              <el-button
                :disabled="current == pages"
                class="toNext"
                @click="toNext"
                >下一页</el-button
              >
            </div>
          </div>
        </div>
      </div>
      <el-button
        type="primary"
        round
        class="r-submit"
        @click="$router.push({ path: 'Analysis' })"
        >生成报表</el-button
      >
    </div>
  </div>
</template>

<script>
import Echarts from "@/components/Echarts";
import { mapState } from "vuex";
import mixins from "@/mixins";
export default {
  data() {
    return {
      parkActive: true,
      lineActive: false,
      options: [
        {
          value: "选项1",
          label: "黄金糕",
        },
        {
          value: "选项2",
          label: "双皮奶",
        },
        {
          value: "选项3",
          label: "蚵仔煎",
        },
        {
          value: "选项4",
          label: "龙须面",
        },
        {
          value: "选项5",
          label: "北京烤鸭",
        },
      ],
      value: "",
      input: "",
      pageNum: 1,
    };
  },
  mixins: [mixins],
  computed: {
    ...mapState("DiggingsWell", ["tableData", "total", "current", "pages"]),
  },
  methods: {
    onExport() {
      this.$store.dispatch("DiggingsWell/exportTable")
    },
    // 时间格式化
    formatDate(date) {
      return this.handleDate(date);
    },
    // tab切换
    handlePark() {
      if (!this.parkActive) {
        this.parkActive = !this.parkActive;
        this.lineActive = !this.lineActive;
      }
    },
    handleLine() {
      if (!this.lineActive) {
        this.lineActive = !this.lineActive;
        this.parkActive = !this.parkActive;
      }
    },
    // 表格样式
    headClass() {
      return "background:#e8e8e8;text-align:center";
    },
    rowClass() {
      return "text-align:center";
    },
    // 渲染
    getTableList() {
      let data = {
        pageNum: this.pageNum,
        pageSize: 3,
      };
      this.$store.dispatch("DiggingsWell/getdiggingsTable", data);
    },
    // 上一页
    toPrev() {
      this.pageNum -= 1;
      this.getTableList();
    },
    // 下一页
    toNext() {
      this.pageNum += 1;
      this.getTableList();
    },
  },
  components: {
    Echarts,
  },
  mounted() {
    this.getTableList();
  },
};
</script>

<style>
.r-box {
  width: 1320px;
  height: 610px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  position: relative;
}

.r-tab {
  width: 80px;
  height: 200px;
  position: absolute;
  left: 5px;
  top: 155px;
  border: 2px solid #bbbbbb;
  border-right: 0;
  box-shadow: #d6d6d6 0 0 4px;
}

.r-tab-top {
  width: 70px;
  height: 89px;
  margin-left: 5px;
  margin-top: 10px;
  border-bottom: 1px solid #bbbbbb;
  position: relative;
}

.r-tab-bot {
  width: 70px;
  height: 90px;
  margin-left: 5px;
  position: relative;
}

.park {
  width: 66px;
  height: 50px;
  border: 2px solid #bbbbbb;
  position: absolute;
  top: 0;
  cursor: pointer;
}

.park img {
  width: 50px;
  height: 50px;
  position: absolute;
  left: 50%;
  top: 50%;
  margin-left: -25px;
  margin-top: -25px;
}

.line {
  width: 66px;
  height: 50px;
  border: 2px solid #bbbbbb;
  position: absolute;
  bottom: 0;
  cursor: pointer;
}

.line img {
  width: 50px;
  height: 50px;
  position: absolute;
  left: 50%;
  top: 50%;
  margin-left: -25px;
  margin-top: -25px;
}

.r-view {
  width: 1200px;
  height: 500px;
  position: absolute;
  border: 2px solid #bbbbbb;
  left: 87px;
  top: 40px;
}

.r-submit {
  position: absolute;
  bottom: 10px;
  width: 200px;
  left: 50%;
  margin-left: -100px !important;
}

.r-view-park,
.r-view-line {
  width: 1160px;
  height: 460px;
  margin: 20px;
  position: relative;
}

.park-logo {
  width: 60px;
  height: 60px;
  position: absolute;
  left: 0;
  top: 0;
}

.park-logo img {
  width: 60px;
  height: 60px;
}

.park-title {
  font-size: 24px;
  font-weight: bold;
  color: #228edc;
  position: absolute;
  left: 65px;
  top: 18px;
}

.park-select {
  width: 100%;
  height: 40px;
  position: absolute;
  top: 80px;
  left: 0;
}

.park-sel1 {
  width: 340px;
  height: 40px;
  float: left;
  font-size: 20px;
  line-height: 40px;
}

.park-sel2 {
  width: 300px;
  height: 40px;
  float: right;
  font-size: 20px;
  line-height: 40px;
}

.r-box .el-select {
  margin-left: 30px;
}

.park-echarts {
  width: 100%;
  height: 330px;
  position: absolute;
  bottom: 0;
}

.line-input {
  width: 300px;
  height: 40px;
  float: left;
}

.line-total {
  width: 300px;
  height: 40px;
  font-size: 14px;
  float: right;
  text-align: right;
}

.line-total span {
  color: #228edc;
}

.line-table {
  width: 100%;
  height: 280px;
  position: absolute;
  bottom: 50px;
}

.s-footer-icon {
  width: 300px;
  height: 40px;
  float: left;
  margin-left: 20px;
}

.s-footer {
  width: 100%;
  height: 40px;
  position: absolute;
  bottom: 20px;
}

.s-header-page {
  float: right;
  width: 300px;
  height: 40px;
  margin-right: 20px;
}

.s-header-page-span {
  float: left;
  width: 100px;
  height: 40px;
  text-align: center;
  line-height: 40px;
}

.s-header-page-span span {
  color: #228edc;
}

.toPrev {
  width: 100px;
  float: left;
}

.toNext {
  width: 100px;
  float: right;
}
</style>