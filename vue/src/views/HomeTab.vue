<template>
  <div class="hometab">
    <ul
      class="d-b4-list hb-list"
      :style="{ width: isRightShow ? '1000px' : '1200px' }"
      v-if="tableData.length"
    >
      <li
        class="d-b4-items"
        v-for="(item, index) in tableData"
        :key="index"
        @click="doBrowser(item, index)"
      >
        <div class="items-img">
          <img src="../assets/image/3.png" alt="" />
        </div>
        <span class="items-label">{{ item.sampleCode }}</span>
        <span class="items-name">{{ item.user.username }}</span>
        <span class="items-time">{{ formatDate(item.createTime) }}</span>
      </li>
    </ul>
    <ul
      v-else
      class="d-b4-list hb-list"
      :style="{ width: isRightShow ? '1000px' : '1200px' }"
    >
      <span style="font-weight: bold"
        >没有找到，请先
        <span class="addList" @click="$router.push({ path: '/Sample' })"
          >创建样本</span
        >
        。</span
      >
    </ul>
  </div>
</template>

<script>
import mixins from "@/mixins";
import { mapState } from "vuex";
export default {
  props: {
    isRightShow: {
      type: Boolean,
      default: false,
    },
    mineralType: {
      type: String,
      default: "MAGNETITE",
    },
  },
  data() {
    return {};
  },
  computed: {
    ...mapState("Sample", ["tableData"]),
  },
  methods: {
    formatDate(date) {
      return this.handleDate(date);
    },
    doBrowser(detail, index) {
      this.$router.push({
        name: "Dashboard",
        params: {
          sampleNo: detail.sampleNo,
          index: index + 1,
        },
      });
    },
  },
  mixins: [mixins],
  created() {},
};
</script>

<style scoped>
.hometab {
  width: 100%;
  height: 350px;
}

.d-b4-list {
  height: 70px;
  position: absolute;
  left: 50px;
  top: 40px;
}

ul {
  padding: 0;
}

li {
  list-style: none;
  margin-bottom: 10px;
}

.d-b4-items {
  width: 200px;
  height: 70px;
  box-shadow: 0px 2px 5px 0px rgba(187, 187, 187, 62);
  border: 3px solid rgba(34, 142, 220, 67);
  float: left;
  margin-right: 30px;
  position: relative;
  cursor: pointer;
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

.hb-list {
  left: 0;
  top: 0;
}

.addList {
  color: #228edc;
  cursor: pointer;
}
</style>