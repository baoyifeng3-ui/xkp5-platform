<template>
  <div id="app">
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