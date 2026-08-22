<template>
  <div class="participant-preview-notice" role="status">
    <i class="el-icon-view" />
    <span>当前为只读预览，操作不会提交。</span>
  </div>
</template>

<script>
export default {
  name: 'ParticipantPreviewNotice'
}
</script>

<style scoped>
.participant-preview-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  padding: 10px 12px;
  color: #536875;
  font-size: 13px;
  background: #edf4f1;
  border: 1px solid #cfe1da;
  border-radius: 6px;
}
.participant-preview-notice i { color: #2b7b60; }
</style>
