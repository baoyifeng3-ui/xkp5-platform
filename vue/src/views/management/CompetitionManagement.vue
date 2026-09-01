<template>
  <section class="module-page module-composed-page competition-management-page">
    <header class="module-heading">
      <div><h1>竞赛管理</h1><p>配置比赛控制、赛程赛规、试卷与评分。</p></div>
    </header>

    <nav class="competition-links" aria-label="竞赛管理功能">
      <router-link v-for="item in competitionItems" :key="item.key" :to="item.route" class="competition-link">
        <span class="competition-link-icon"><i :class="iconFor(item.key)" /></span>
        <span><strong>{{ item.label }}</strong><small>{{ descriptionFor(item.key) }}</small></span>
        <i class="el-icon-arrow-right" aria-hidden="true" />
      </router-link>
    </nav>
  </section>
</template>

<script>
const { competitionItems } = require('@/navigation/roleNavigation')

const itemDetails = {
  mode: ['el-icon-set-up', '切换平台模式并绑定比赛环境'],
  preview: ['el-icon-view', '预览参赛端页面与流程'],
  control: ['el-icon-odometer', '管理比赛排期、倒计时与赛卷'],
  rules: ['el-icon-document', '维护赛程、赛规与公告内容'],
  subjects: ['el-icon-edit-outline', '管理试卷模块和题目'],
  grading: ['el-icon-finished', '查看提交并完成评分'],
  accounts: ['el-icon-user', '管理参赛账号与登录信息']
}

export default {
  name: 'CompetitionManagement',
  data: () => ({ competitionItems }),
  methods: {
    iconFor (key) { return itemDetails[key][0] },
    descriptionFor (key) { return itemDetails[key][1] }
  }
}
</script>

<style scoped>
.competition-links { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid #dce4e9; }
.competition-link { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 13px; min-height: 78px; padding: 12px 16px; color: var(--ui-text); text-decoration: none; border-right: 1px solid var(--ui-border); border-bottom: 1px solid var(--ui-border); box-sizing: border-box; }
.competition-link:nth-child(even) { border-right: 0; }
.competition-link:hover, .competition-link:focus { color: var(--ui-primary-strong); background: #f5f6fb; }
.competition-link-icon { display: inline-flex; align-items: center; justify-content: center; width: 36px; height: 36px; color: var(--ui-primary-strong); background: #eef0ff; border-radius: 6px; }
.competition-link > span:nth-child(2) { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.competition-link strong { font-size: 14px; }
.competition-link small { overflow: hidden; color: #75848f; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.competition-link > i { color: #8795a0; }
@media (max-width: 680px) { .competition-links { grid-template-columns: 1fr; } .competition-link { border-right: 0; } }
</style>
