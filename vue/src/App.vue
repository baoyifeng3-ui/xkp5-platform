<template>
  <div id="app" class="platform-app">
    <Index />
  </div>
</template>

<script>
import Index from "@/views/index";
export default {
  components: {
    Index,
  },
};
</script>

<style>
@import url(./assets/style/platform-theme.css);

html,
body,
#app,
.platform-app {
  min-height: 100%;
}

body {
  margin: 0;
  color: var(--ui-text);
  background: var(--ui-workspace);
  font-family: "Microsoft YaHei", "PingFang SC", Arial, sans-serif;
}

::-webkit-scrollbar {
  width: 5px !important;
  height: 3px !important;
}

::-webkit-scrollbar-thumb {
  border-radius: 10px;
  width: 1px;
  background: #666;
}

::-webkit-scrollbar-track {
  box-shadow: inset 0 0 5px rgba(0, 0, 0, 0.2);
  border-radius: 10px;
  background: #fff;
}
</style>
