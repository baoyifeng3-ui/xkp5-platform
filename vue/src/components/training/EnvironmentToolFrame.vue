<template>
  <div class="tool-frame">
    <div v-if="unconfirmed" class="tool-frame-message" role="status">
      <strong>工具页面尚未完成加载</strong>
      <p>容器状态与浏览器访问是两项检查。请检查网络；代码页面还需要信任当前平台根证书。</p>
      <el-button size="small" icon="el-icon-refresh" @click="retry">重试加载</el-button>
      <el-button size="small" icon="el-icon-top-right" @click="openStandalone">独立打开</el-button>
    </div>
    <iframe :key="revision" :src="src" :title="title" allow="clipboard-read; clipboard-write" @load="loaded" @error="unconfirmed=true" />
  </div>
</template>
<script>
export default {
  props: { src: {type:String,required:true}, title: {type:String,default:'实训工具'} },
  data: () => ({revision:0,unconfirmed:false,timer:null}),
  watch: {src() {this.retry();}},
  mounted() {this.arm();},
  beforeDestroy() {clearTimeout(this.timer);},
  methods: {
    arm() {clearTimeout(this.timer);this.unconfirmed=false;this.timer=setTimeout(()=>{this.unconfirmed=true;},20000);},
    loaded() {clearTimeout(this.timer);this.unconfirmed=false;},
    retry() {this.revision+=1;this.arm();},
    openStandalone() {const url=new URL(this.src,window.location.origin);if(['https:','http:'].includes(url.protocol))window.open(url.href,'_blank','noopener,noreferrer');}
  }
}
</script>
<style scoped>
.tool-frame{position:relative;width:100%;height:100%;min-height:480px}.tool-frame iframe{display:block;width:100%;height:100%;min-height:480px;border:0}.tool-frame-message{position:absolute;z-index:2;top:20px;left:20px;right:20px;padding:18px;background:#fff;border:1px solid #d7dce3;border-radius:4px;color:#303642}.tool-frame-message p{font-size:13px;line-height:1.6;margin:8px 0 14px}
</style>
