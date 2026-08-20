<template>
  <div>
    <div class="a-box">
      <div class="a-tab">
        <div class="a-tab-top">
          <div class="xiantiao" @click="handleActive(0)">
            <img v-if="active == 0" src="@/assets/icon/线条-on.png" alt="" />
            <img v-else src="@/assets/icon/线条.png" alt="" />
          </div>
        </div>
        <div class="a-tab-mid">
          <div class="juxing" @click="handleActive(1)">
            <img v-if="active == 1" src="@/assets/icon/矩形-on.png" alt="" />
            <img v-else src="@/assets/icon/矩形.png" alt="" />
          </div>
        </div>
        <div class="a-tab-bot">
          <div class="fangti" @click="handleActive(2)">
            <img v-if="active == 2" src="@/assets/icon/方体-on.png" alt="" />
            <img v-else src="@/assets/icon/方体.png" alt="" />
          </div>
        </div>
      </div>
      <div class="a-view">
        <div class="a-view-xiantiao" v-show="active == 0">
          <div class="a-view-header">
            <div class="a-back" @click="$router.go(-1)">
              <img src="@/assets/icon/back.png" alt="" />
            </div>
            <div class="a-select">
              <el-select placeholder="请选择" v-model="value">
                <el-option label="岩心分析" value="1"></el-option>
              </el-select>
            </div>
            <div class="a-close" @click="$router.push({ path: 'Report' })">
              <img src="@/assets/icon/close.png" alt="" />
            </div>
            <div class="a-title-list">
              <div class="list1">矿场名称：<span>白云鄂博D05矿</span></div>
              <div class="list2">矿物密度：<span>68.5%</span></div>
              <div class="list3">
                矿物名称：
                <el-select v-model="value">
                  <el-option label="磁铁矿" value="磁铁矿"></el-option>
                </el-select>
              </div>
            </div>
          </div>
          <div class="a-view-left">
            <div class="left-title">
              <span style="float: left">钻井号：</span>
              <el-select style="float: right" v-model="value">
                <el-option label="J001" value="J001"></el-option>
                <el-option label="J002" value="J002"></el-option>
                <el-option label="J003" value="J003"></el-option>
                <el-option label="J004" value="J004"></el-option>
              </el-select>
            </div>
            <div class="left-canvas"></div>
          </div>
          <div class="a-view-right">
            <div class="left-title">
              <span style="float: left">图表样式：</span>
              <el-select style="float: right" v-model="value">
                <el-option label="折线图" value="折线图"></el-option>
                <el-option label="柱状图" value="柱状图"></el-option>
                <el-option label="饼状图" value="饼状图"></el-option>
              </el-select>
            </div>
            <div class="right-canvas">
              <LineEcharts />
            </div>
            <div class="right-table">
              <el-table
                :data="tableData"
                :header-cell-style="headClass"
                :cell-style="rowClass"
                border
                style="width: 100%"
                max-height="140"
              >
                <el-table-column prop="date" label="含量百分数">
                </el-table-column>
                <el-table-column prop="name" label="质量百分数">
                </el-table-column>
                <el-table-column prop="province" label="梯度值">
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
        <div class="a-view-juxing" v-show="active == 1">
          <div class="a-view-header">
            <div class="a-back" @click="$router.go(-1)">
              <img src="@/assets/icon/back.png" alt="" />
            </div>
            <div class="a-select">
              <el-select placeholder="请选择" v-model="value">
                <el-option label="中段分析" value="中段分析"></el-option>
                <el-option label="横剖面分析" value="横剖面分析"></el-option>
                <el-option label="纵剖面分析" value="纵剖面分析"></el-option>
              </el-select>
            </div>
            <div class="a-close" @click="$router.push({ path: 'Report' })">
              <img src="@/assets/icon/close.png" alt="" />
            </div>
            <div class="a-title-list">
              <div class="list1">矿场名称：<span>白云鄂博D05矿</span></div>
              <div class="list2">矿物密度：<span>68.5%</span></div>
              <div class="list3">
                矿物名称：
                <el-select v-model="value">
                  <el-option label="磁铁矿" value="磁铁矿"></el-option>
                  <el-option label="萤石" value="萤石"></el-option>
                </el-select>
              </div>
            </div>
          </div>
          <div class="a-view-left">
            <div class="left-title">
              <span style="float: left">钻井号：</span>
              <el-select v-model="value" style="float: right">
                <el-option label="全选" value="全选"></el-option>
                <el-option label="J001" value="J001"></el-option>
                <el-option label="J002" value="J002"></el-option>
                <el-option label="J003" value="J003"></el-option>
                <el-option label="J004" value="J004"></el-option>
                <el-option label="J005" value="J005"></el-option>
                <el-option label="J006" value="J006"></el-option>
                <el-option label="J007" value="J007"></el-option>
                <el-option label="J008" value="J008"></el-option>
              </el-select>
            </div>
            <div class="left-canvas"></div>
          </div>
          <div class="a-view-right">
            <div class="left-title">
              <span style="float: left">图表样式：</span>
              <el-select style="float: right" v-model="value">
                <el-option label="折线图" value="折线图"></el-option>
                <el-option label="柱状图" value="柱状图"></el-option>
                <el-option label="饼状图" value="饼状图"></el-option>
              </el-select>
            </div>
            <div class="right-canvas"></div>
            <div class="right-table">
              <el-table
                :data="tableData"
                :header-cell-style="headClass"
                :cell-style="rowClass"
                border
                style="width: 100%"
                max-height="140"
              >
                <el-table-column prop="date" label="含量百分数">
                </el-table-column>
                <el-table-column prop="name" label="质量百分数">
                </el-table-column>
                <el-table-column prop="province" label="梯度值">
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
        <div class="a-view-fangti" v-show="active == 2">
          <div class="a-view-header">
            <div class="a-back" @click="$router.go(-1)">
              <img src="@/assets/icon/back.png" alt="" />
            </div>
            <div class="a-select">
              <el-select placeholder="请选择" v-model="value">
                <el-option
                  label="三维立体分析"
                  value="三维立体分析"
                ></el-option>
              </el-select>
            </div>
            <div class="a-close" @click="$router.push({ path: 'Report' })">
              <img src="@/assets/icon/close.png" alt="" />
            </div>
            <div class="a-title-list">
              <div class="list1">矿场名称：<span>白云鄂博D05矿</span></div>
              <div class="list2">矿物密度：<span>68.5%</span></div>
              <div class="list3">
                矿物名称：
                <el-select v-model="value">
                  <el-option label="磁铁矿" value="磁铁矿"></el-option>
                  <el-option label="萤石" value="萤石"></el-option>
                </el-select>
              </div>
            </div>
          </div>
          <div class="a-view-center">
            <div class="left-title">
              <span style="float: left">目标钻井：</span>
              <el-select v-model="value" style="float: right">
                <el-option label="全选" value="全选"></el-option>
                <el-option label="J001" value="J001"></el-option>
                <el-option label="J002" value="J002"></el-option>
                <el-option label="J003" value="J003"></el-option>
                <el-option label="J004" value="J004"></el-option>
              </el-select>
            </div>
            <div class="left-canvas"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import LineEcharts from "@/components/LineEcharts"
export default {
  data() {
    return {
      active: 0,
      value: "",
      tableData: [
        {
          date: "2016-05-02",
          name: "王小虎",
          province: "上海",
          city: "普陀区",
          address: "上海市普陀区金沙江路 1518 弄",
          zip: 200333,
        },
      ],
    };
  },
  methods: {
    handleActive(num) {
      if (this.active != num) {
        this.active = num;
      }
    },
    // 表头样式
    headClass() {
      return "background:#e8e8e8;text-align:center";
    },
    // 表格样式
    rowClass() {
      return "text-align:center";
    },
  },
  components: {
    LineEcharts
  }
};
</script>

<style>
.a-box {
  width: 1330px;
  height: 610px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  position: relative;
}

.a-tab {
  width: 80px;
  height: 290px;
  position: absolute;
  left: 5px;
  top: 155px;
  border: 2px solid #bbbbbb;
  border-right: 0;
  box-shadow: #d6d6d6 0 0 4px;
}

.a-tab-top {
  width: 70px;
  height: 89px;
  margin-left: 5px;
  margin-top: 10px;
  border-bottom: 1px solid #bbbbbb;
  position: relative;
}

.a-tab-mid {
  width: 70px;
  height: 89px;
  margin-left: 5px;
  border-bottom: 1px solid #bbbbbb;
  position: relative;
}

.a-tab-bot {
  width: 70px;
  height: 90px;
  margin-left: 5px;
  position: relative;
}

.xiantiao {
  width: 66px;
  height: 70px;
  border: 2px solid #bbbbbb;
  position: absolute;
  top: 0;
  cursor: pointer;
}

.juxing,
.fangti {
  width: 66px;
  height: 70px;
  border: 2px solid #bbbbbb;
  position: absolute;
  margin-top: 10px;
  cursor: pointer;
}

.xiantiao img {
  width: 2px;
  height: 40px;
  position: absolute;
  left: 50%;
  top: 50%;
  margin-left: -1px;
  margin-top: -20px;
}

.juxing img,
.fangti img {
  width: 40px;
  height: 40px;
  position: absolute;
  left: 50%;
  top: 50%;
  margin-left: -20px;
  margin-top: -20px;
}

.a-view {
  width: 1200px;
  height: 500px;
  position: absolute;
  border: 2px solid #bbbbbb;
  left: 87px;
  top: 40px;
}

.a-view-xiantiao,
.a-view-juxing,
.a-view-fangti {
  width: 1160px;
  height: 460px;
  margin: 20px;
  position: relative;
}

.a-back {
  width: 24px;
  height: 24px;
  position: absolute;
  left: 0;
  top: 0;
  cursor: pointer;
}

img {
  width: 100%;
  height: 100%;
}

.a-close {
  width: 30px;
  height: 30px;
  position: absolute;
  right: 0;
  top: 0;
  cursor: pointer;
}

.a-view-header {
  width: 100%;
  height: 100px;
  position: absolute;
  left: 0;
  top: 0;
}

.a-select {
  width: 200px;
  height: 30px;
  position: absolute;
  left: 50%;
  top: 0;
  margin-left: -100px;
}

.a-select .el-select {
  margin-left: 0 !important;
}

.a-select .el-input__inner {
  height: 30px !important;
  line-height: 30px !important;
}

.a-select .el-input__icon {
  height: 120% !important;
}

.a-title-list {
  width: 100%;
  height: 65px;
  position: absolute;
  bottom: 0;
  left: 0;
}

.list1 {
  width: 380px;
  height: 65px;
  float: left;
  box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(187, 187, 187, 100);
  box-sizing: border-box;
  font-size: 16px;
  text-align: center;
  font-weight: bold;
  color: #000;
  line-height: 65px;
}

.list2 {
  width: 350px;
  height: 65px;
  float: left;
  box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(187, 187, 187, 100);
  box-sizing: border-box;
  margin-left: 20px;
  font-size: 16px;
  text-align: center;
  font-weight: bold;
  color: #000;
  line-height: 65px;
}

.list1 span,
.list2 span {
  color: #228edc;
}

.list3 {
  width: 390px;
  height: 65px;
  float: right;
  box-shadow: 0px 2px 6px 0px rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(187, 187, 187, 100);
  box-sizing: border-box;
  line-height: 65px;
  color: #000;
  font-size: 16px;
  text-align: center;
  font-weight: bold;
  color: #000;
}

.a-view-left {
  width: 40%;
  height: 350px;
  position: absolute;
  left: 0;
  bottom: 0;
}

.a-view-right {
  width: 55%;
  height: 350px;
  position: absolute;
  right: 0;
  bottom: 0;
}

.left-title {
  width: 100%;
  height: 40px;
  line-height: 40px;
}

.left-canvas {
  width: 100%;
  height: 300px;
  position: absolute;
  bottom: 0;
  background-color: aqua;
}

.right-canvas {
  width: 100%;
  height: 250px;
  position: absolute;
  top: 50px;
  right: 0;
}

.right-table {
  width: 100%;
  height: 100px;
  position: absolute;
  right: 0;
  bottom: 0;
}

.a-view-center {
  width: 50%;
  height: 350px;
  position: absolute;
  left: 25%;
  bottom: 0;
}
</style>