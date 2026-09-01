<template>
  <div class="rich-help">
    <div class="toolbar">
      <select @change="block($event.target.value)">
        <option value="p">正文</option>
        <option value="h1">标题一</option>
        <option value="h2">标题二</option></select
      ><button @click="cmd('bold')"><b>B</b></button
      ><button @click="cmd('italic')"><i>I</i></button
      ><input
        type="color"
        title="文字颜色"
        @input="cmd('foreColor', $event.target.value)"
      /><button @click="cmd('insertUnorderedList')">列表</button
      ><button @click="cmd('formatBlock', 'blockquote')">引用</button
      ><button @click="link">链接</button><button @click="table">表格</button
      ><button @click="$refs.image.click()">图片</button
      ><input ref="image" type="file" accept="image/*" hidden @change="image" />
    </div>
    <div
      ref="editor"
      class="editor"
      contenteditable="true"
      @input="$emit('input', $refs.editor.innerHTML)"
    ></div>
  </div>
</template>
<script>
import { uploadHelpImage } from "@/api/OnlineHelp";
export default {
  props: {
    value: { type: String, default: "" },
    imageUploader: { type: Function, default: uploadHelpImage },
  },
  watch: {
    value: {
      immediate: true,
      handler(v) {
        this.$nextTick(() => {
          if (this.$refs.editor && this.$refs.editor.innerHTML !== v)
            this.$refs.editor.innerHTML = v || "";
        });
      },
    },
  },
  methods: {
    focus() {
      this.$refs.editor.focus();
    },
    cmd(name, value = null) {
      this.focus();
      document.execCommand(name, false, value);
    },
    block(value) {
      this.cmd("formatBlock", value);
    },
    link() {
      const url = window.prompt("输入链接地址");
      if (url) this.cmd("createLink", url);
    },
    table() {
      this.cmd(
        "insertHTML",
        '<table border="1"><tbody><tr><td>内容</td><td>内容</td></tr></tbody></table>'
      );
    },
    async image(e) {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      const data = new FormData();
      data.append("file", file);
      const r = await this.imageUploader(data);
      this.focus();
      this.cmd("insertImage", r.data.url);
      e.target.value = "";
    },
  },
};
</script>
<style scoped>
.rich-help {
  border: 1px solid var(--ui-border);
}
.toolbar {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 8px;
  border-bottom: 1px solid var(--ui-border);
  background: #f7f9fc;
}
.toolbar button,
.toolbar select,
.toolbar input {
  height: 30px;
  border: 1px solid var(--ui-border);
  background: #fff;
}
.editor {
  min-height: 420px;
  padding: 18px;
  outline: none;
  overflow: auto;
}
.editor::v-deep img {
  max-width: 100%;
}
.editor::v-deep table {
  border-collapse: collapse;
}
.editor::v-deep td {
  padding: 8px;
}
</style>
