<template>
  <section class="module-page help-page">
    <header class="module-heading">
      <div>
        <h1>在线帮助</h1>
        <p>查看平台操作说明和常见问题。</p>
      </div>
    </header>
    <div class="help-layout">
      <nav>
        <button
          v-for="item in documents"
          :key="item.documentId"
          :class="{
            active: selected && selected.documentId === item.documentId,
          }"
          @click="selected = item"
        >
          {{ item.title }}
        </button>
      </nav>
      <main v-if="selected">
        <article
          v-if="selected.contentType === 'HTML'"
          v-html="selected.htmlContent"
        />
        <iframe
          v-else-if="selected.contentType === 'PDF'"
          :src="fileUrl(selected)"
          title="帮助文档"
        />
        <div v-else class="download">
          <i class="el-icon-document" /><strong>{{ selected.fileName }}</strong
          ><a :href="fileUrl(selected)" download>下载 Word 文档</a>
        </div>
      </main>
      <el-empty v-else description="暂无已发布帮助文档" />
    </div>
  </section>
</template>
<script>
import { listHelpDocuments } from "@/api/OnlineHelp";
export default {
  data: () => ({ documents: [], selected: null }),
  async created() {
    const r = await listHelpDocuments();
    this.documents = r.data || [];
    this.selected = this.documents[0] || null;
  },
  methods: {
    fileUrl(item) {
      return item.storageKey ? `/files/${item.storageKey}` : "";
    },
  },
};
</script>
<style scoped>
.help-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: calc(100vh - 190px);
  border: 1px solid var(--ui-border);
}
.help-layout nav {
  padding: 8px;
  border-right: 1px solid var(--ui-border);
}
.help-layout nav button {
  display: block;
  width: 100%;
  padding: 12px;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--ui-border);
  cursor: pointer;
}
.help-layout nav button.active {
  color: var(--ui-primary);
  background: #eef3ff;
}
.help-layout main {
  min-width: 0;
  padding: 24px;
  overflow: auto;
}
.help-layout iframe {
  width: 100%;
  height: calc(100vh - 250px);
  border: 0;
}
.download {
  display: grid;
  place-items: center;
  gap: 16px;
  min-height: 360px;
}
.download i {
  font-size: 54px;
}
@media (max-width: 700px) {
  .help-layout {
    grid-template-columns: 1fr;
  }
  .help-layout nav {
    border-right: 0;
    border-bottom: 1px solid var(--ui-border);
  }
}
</style>
