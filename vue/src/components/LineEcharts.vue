<template>
  <div>
    <div style="width: 420px; height: 100px" ref="lineEcharts"></div>
  </div>
</template>

<script>
import * as echarts from "echarts";
export default {
  props: {
    lineArr: {
      type: Array,
    },
    maxSList: {
      type: Array,
    },
  },
  watch: {
    lineArr: {
      handler(nV, oV) {},
      immediate: true,
      deep: true,
    },
    maxSList: {
      handler(nV, oV) {},
      immediate: true,
      deep: true,
    },
  },
  methods: {
    getEcharts() {
      var chartDom = this.$refs.lineEcharts;
      var myChart = echarts.init(chartDom);
      var option;

      let sList = this.lineArr;
      let sData = sList.map((v) => {
        return v.contentPercent;
      });
      let sSeries = sList.map((v) => {
        return {
          name: v.contentPercent,
          type: "line",
          stack: "Total",
          data: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100],
        };
      });
      console.log(sSeries);
      let sLength = sList.map((v, i) => {
        return "图片" + (i + 1);
      });

      option = {
        title: {
          text: "质量百分数(%)",
        },
        tooltip: {
          trigger: "axis",
        },
        legend: {
          data: sData,
        },
        grid: {
          left: "3%",
          right: "4%",
          bottom: "3%",
          containLabel: true,
        },
        toolbox: {
          feature: {
            saveAsImage: {},
          },
        },
        xAxis: {
          type: "category",
          boundaryGap: false,
          data: sLength,
        },
        yAxis: {
          type: "value",
        },
        series: sSeries,
      };

      option && myChart.setOption(option);
    },
  },
  mounted() {
    this.getEcharts();
  },
};
</script>

<style scoped>
</style>