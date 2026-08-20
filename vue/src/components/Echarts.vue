<template>
  <div class="chart-wrap">
    <div ref="myEcharts" class="chart"></div>
  </div>
</template>

<script>
import * as echarts from "echarts";

export default {
  data() {
    return { chart: null };
  },
  methods: {
    setEcharts() {
      this.chart = echarts.init(this.$refs.myEcharts);
      this.chart.setOption({
        tooltip: { trigger: "axis" },
        grid: { left: 36, right: 20, top: 24, bottom: 30 },
        xAxis: { type: "category", data: ["A", "B", "C", "D", "E"] },
        yAxis: { type: "value", min: 0, max: 100 },
        series: [{ type: "bar", data: [82, 68, 74, 91, 77], itemStyle: { color: "#3d7eff" } }]
      });
    },
    resize() {
      if (this.chart) this.chart.resize();
    }
  },
  mounted() {
    this.setEcharts();
    window.addEventListener("resize", this.resize);
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.resize);
    if (this.chart) this.chart.dispose();
  }
};
</script>

<style scoped>
.chart-wrap, .chart { width: 100%; height: 300px; }
</style>
