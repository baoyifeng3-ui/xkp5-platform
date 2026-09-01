<template>
  <section class="grading-page">
    <div class="grading-summary" aria-label="判分状态概览">
      <button type="button" :class="{ active: status === '' }" @click="setStatus('')"><small>已提交</small><strong>{{ summary.submitted || 0 }}</strong></button>
      <button type="button" :class="{ active: status === 'PENDING_GRADING' }" @click="setStatus('PENDING_GRADING')"><small>待判分</small><strong>{{ summary.pending || 0 }}</strong></button>
      <button type="button" :class="{ active: status === 'GRADED' }" @click="setStatus('GRADED')"><small>已完成</small><strong>{{ summary.graded || 0 }}</strong></button>
      <button type="button" :class="{ active: status === 'RETURNED' }" @click="setStatus('RETURNED')"><small>已退回</small><strong>{{ summary.returned || 0 }}</strong></button>
    </div>

    <div class="grading-filterbar">
      <el-select v-model="status" size="small" clearable placeholder="全部状态" @change="loadSubmissions">
        <el-option label="待判分" value="PENDING_GRADING" />
        <el-option label="已完成" value="GRADED" />
        <el-option label="已退回" value="RETURNED" />
      </el-select>
      <el-input v-model="keyword" size="small" clearable prefix-icon="el-icon-search" placeholder="搜索用户名" @keyup.enter.native="loadSubmissions" @clear="loadSubmissions" />
      <el-button size="small" icon="el-icon-search" @click="loadSubmissions">查询</el-button>
      <span class="grading-result-count">共 {{ submissions.length }} 份</span>
      <div class="grading-heading-actions">
        <el-tooltip :content="exportTip" placement="bottom"><span><el-button type="primary" size="small" icon="el-icon-download" class="grading-primary-action" :disabled="!exportAllowed" :loading="exporting" @click="createExport">导出全部试卷</el-button></span></el-tooltip>
        <el-button size="small" icon="el-icon-time" class="grading-secondary-action" :disabled="!paper" @click="openExportHistory">导出历史</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="submissions" class="grading-table" empty-text="当前筛选条件下暂无试卷">
      <el-table-column label="用户" min-width="150"><template slot-scope="scope"><div class="grading-user"><span>{{ userInitial(scope.row.userName) }}</span><strong>{{ scope.row.userName }}</strong></div></template></el-table-column>
      <el-table-column label="试卷" width="70"><template slot-scope="scope">{{ scope.row.paperType }} 卷</template></el-table-column>
      <el-table-column label="修订" width="70"><template slot-scope="scope">第 {{ scope.row.revision }} 次</template></el-table-column>
      <el-table-column label="客观题" width="90"><template slot-scope="scope">{{ scope.row.objectiveScore || 0 }} 分</template></el-table-column>
      <el-table-column label="实操题" width="90"><template slot-scope="scope">{{ scope.row.practicalScore == null ? '--' : `${scope.row.practicalScore} 分` }}</template></el-table-column>
      <el-table-column label="总分" width="90"><template slot-scope="scope"><strong class="grading-total-score">{{ scope.row.totalScore == null ? '--' : `${scope.row.totalScore} 分` }}</strong></template></el-table-column>
      <el-table-column label="提交时间" min-width="155"><template slot-scope="scope">{{ formatTime(scope.row.submittedAt) }}</template></el-table-column>
      <el-table-column label="状态" width="100"><template slot-scope="scope"><el-tag size="small" :type="statusType(scope.row.status)" class="grading-status-tag">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="102" fixed="right"><template slot-scope="scope"><el-button type="text" size="mini" class="grading-row-action" :icon="scope.row.status === 'PENDING_GRADING' ? 'el-icon-edit-outline' : 'el-icon-view'" @click="openDetail(scope.row)">{{ scope.row.status === 'PENDING_GRADING' ? '判分' : '查看' }}</el-button></template></el-table-column>
    </el-table>

    <el-drawer :visible.sync="detailVisible" :size="drawerSize" append-to-body custom-class="grading-drawer" @closed="detail = null">
      <template slot="title"><span class="drawer-title">{{ detail ? `${detail.userName} · ${detail.paperType} 卷` : '试卷详情' }}</span></template>
      <div v-loading="detailLoading" class="grading-detail">
        <template v-if="detail">
          <div class="detail-meta">
            <span><small>状态</small><el-tag size="small" :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag></span>
            <span><small>客观题</small><strong>{{ detail.objectiveScore || 0 }} 分</strong></span>
            <span><small>实操题</small><strong>{{ detail.practicalScore == null ? '--' : `${detail.practicalScore} 分` }}</strong></span>
            <span><small>总分</small><strong>{{ detail.totalScore == null ? '--' : `${detail.totalScore} 分` }}</strong></span>
          </div>
          <el-alert v-if="detail.returnReason" :title="`退回原因：${detail.returnReason}`" type="warning" :closable="false" show-icon />
          <article v-for="(answer, index) in detail.answers" :key="answer.submissionAnswerId" class="grading-answer">
            <header><span>{{ index + 1 }}</span><div><small>模块 {{ answer.modular }} · {{ typeLabel(answer.subjectType) }}</small><h3>{{ answer.subjectName }}</h3></div><b>{{ answer.maxScore || 0 }} 分</b></header>
            <template v-if="answer.gradingMethod === 'AUTO'">
              <div class="answer-comparison"><div><small>用户答案</small><p>{{ answerText(answer) }}</p></div><div><small>标准答案</small><p>{{ correctAnswerText(answer) }}</p></div><strong :class="{ correct: answer.awardedScore === answer.maxScore }">{{ answer.awardedScore || 0 }} / {{ answer.maxScore || 0 }}</strong></div>
            </template>
            <template v-else>
              <p v-if="answer.answering" class="answer-note">{{ answer.answering }}</p>
              <div v-if="imageList(answer.answerImg).length" class="answer-images"><el-image v-for="(image, imageIndex) in imageList(answer.answerImg)" :key="imageIndex" :src="image" :preview-src-list="imageList(answer.answerImg)" fit="cover" /></div>
              <p v-else class="answer-empty"><i class="el-icon-picture-outline" /> 用户未提交截图</p>
              <div class="manual-score"><span>实操题得分</span><el-input-number v-model="answer.awardedScore" :disabled="detail.status !== 'PENDING_GRADING'" :min="0" :max="answer.maxScore || 0" controls-position="right" /><b>/ {{ answer.maxScore || 0 }} 分</b></div>
            </template>
          </article>
          <footer v-if="detail.status === 'PENDING_GRADING'" class="detail-actions">
            <el-button type="danger" plain icon="el-icon-back" @click="returnDialog = true">退回重做</el-button>
            <div><el-button :loading="saving" icon="el-icon-check" @click="saveScores">保存进度</el-button><el-button type="primary" :loading="completing" icon="el-icon-circle-check" @click="completeGrading">完成判分</el-button></div>
          </footer>
        </template>
      </div>
    </el-drawer>

    <el-dialog :visible.sync="exportHistoryVisible" width="min(1120px, calc(100vw - 24px))" top="8vh" append-to-body class="grading-history-dialog">
      <template slot="title">
        <div class="export-history-title">
          <div><h2>导出历史</h2><p>密钥仅对管理员可见</p></div>
          <el-button icon="el-icon-refresh" size="mini" circle title="刷新导出历史" :loading="exportsLoading" @click="loadExports" />
        </div>
      </template>
      <div class="export-history-table-wrap">
        <el-table v-loading="exportsLoading" :data="exports" size="mini" class="export-history-table" empty-text="暂无导出记录">
          <el-table-column label="导出时间" min-width="155"><template slot-scope="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column>
          <el-table-column label="试卷" width="70"><template slot-scope="scope">{{ scope.row.paperType }} 卷</template></el-table-column>
          <el-table-column prop="pdfCount" label="PDF" width="70" />
          <el-table-column prop="createdByName" label="操作人" min-width="110" />
          <el-table-column label="随机密钥" min-width="300">
            <template slot-scope="scope">
              <code>{{ revealedKeys[scope.row.exportBatchId] ? scope.row.exportKey : maskKey(scope.row.exportKey) }}</code>
              <el-button type="text" size="mini" icon="el-icon-view" @click="toggleKey(scope.row.exportBatchId)">{{ revealedKeys[scope.row.exportBatchId] ? '隐藏' : '查看' }}</el-button>
              <el-button type="text" size="mini" icon="el-icon-document-copy" @click="copyKey(scope.row.exportKey)">复制</el-button>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right"><template slot-scope="scope"><el-button type="text" size="mini" icon="el-icon-download" @click="downloadExport(scope.row)">下载</el-button></template></el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog title="退回重做" :visible.sync="returnDialog" width="min(440px, calc(100vw - 24px))" append-to-body :close-on-click-modal="false" class="grading-return-dialog">
      <el-input v-model="returnReason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="请填写退回原因" />
      <span slot="footer"><el-button @click="returnDialog = false">取消</el-button><el-button type="danger" :loading="returning" @click="returnSubmission">确认退回</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import {
  gradingSubmissionsApi,
  gradingSubmissionApi,
  saveGradingScoresApi,
  completeGradingApi,
  returnSubmissionApi,
  gradingExportsApi,
  createGradingExportApi,
  downloadGradingExportApi
} from '@/api/Match'

export default {
  name: 'AdminGrading',
  props: {
    activePaper: { type: String, default: '' },
    competitionFinished: { type: Boolean, default: false }
  },
  data () {
    return {
      paper: this.activePaper || '',
      status: '',
      keyword: '',
      summary: {},
      submissions: [],
      exports: [],
      exportReady: false,
      exportCount: 0,
      loading: false,
      exporting: false,
      exportHistoryVisible: false,
      exportsLoading: false,
      detailVisible: false,
      detailLoading: false,
      detail: null,
      saving: false,
      completing: false,
      returnDialog: false,
      returnReason: '',
      returning: false,
      revealedKeys: {},
      drawerSize: window.innerWidth <= 760 ? '100%' : '76%'
    }
  },
  computed: {
    exportAllowed () {
      return this.competitionFinished && this.exportReady
    },
    exportTip () {
      if (!this.competitionFinished) return '比赛结束后才可以导出全部试卷'
      if (!this.exportCount) return '当前试卷暂无已提交用户'
      if (!this.exportReady) return '仍有已提交试卷未完成判分'
      return `导出 ${this.exportCount} 份已完成试卷`
    }
  },
  watch: {
    activePaper (value) {
      this.paper = value || ''
      this.reload()
    }
  },
  mounted () {
    if (this.paper) this.reload()
  },
  methods: {
    async reload () {
      if (!this.paper) return
      await this.loadSubmissions()
    },
    async loadSubmissions () {
      if (!this.paper) return
      this.loading = true
      try {
        const result = await gradingSubmissionsApi({ paperType: this.paper, status: this.status || undefined, keyword: this.keyword.trim() || undefined })
        if (result.code === 200) {
          this.summary = result.data.summary || {}
          this.submissions = result.data.items || []
          this.exportReady = Boolean(result.data.exportReady)
          this.exportCount = Number(result.data.exportCount || 0)
        }
      } finally {
        this.loading = false
      }
    },
    async loadExports () {
      if (!this.paper) return
      this.exportsLoading = true
      try {
        const result = await gradingExportsApi(this.paper)
        if (result.code === 200) this.exports = result.data || []
      } finally {
        this.exportsLoading = false
      }
    },
    async openExportHistory () {
      this.exportHistoryVisible = true
      await this.loadExports()
    },
    setStatus (status) {
      this.status = status
      this.loadSubmissions()
    },
    async openDetail (row) {
      this.detailVisible = true
      this.detailLoading = true
      try {
        const result = await gradingSubmissionApi(row.submissionId)
        if (result.code === 200) this.detail = result.data
      } finally {
        this.detailLoading = false
      }
    },
    scorePayload () {
      return { scores: (this.detail.answers || []).filter(item => item.gradingMethod === 'MANUAL').map(item => ({ submissionAnswerId: item.submissionAnswerId, score: item.awardedScore })) }
    },
    async saveScores () {
      this.saving = true
      try {
        const result = await saveGradingScoresApi(this.detail.submissionId, this.scorePayload())
        if (result.code === 200) {
          this.detail = result.data
          this.$message.success('判分进度已保存')
          await this.loadSubmissions()
        }
      } finally {
        this.saving = false
      }
    },
    async completeGrading () {
      const scores = this.scorePayload().scores
      if (scores.some(item => item.score === null || item.score === undefined)) {
        this.$message.error('请完成所有实操题判分')
        return
      }
      this.completing = true
      try {
        await saveGradingScoresApi(this.detail.submissionId, { scores })
        const result = await completeGradingApi(this.detail.submissionId)
        if (result.code === 200) {
          this.detail = result.data
          this.$message.success('判分已完成')
          await this.loadSubmissions()
        }
      } finally {
        this.completing = false
      }
    },
    async returnSubmission () {
      if (!this.returnReason.trim()) {
        this.$message.error('请填写退回原因')
        return
      }
      this.returning = true
      try {
        const result = await returnSubmissionApi(this.detail.submissionId, this.returnReason.trim())
        if (result.code === 200) {
          this.detail = result.data
          this.returnDialog = false
          this.returnReason = ''
          this.$message.success('试卷已退回')
          await this.loadSubmissions()
        }
      } finally {
        this.returning = false
      }
    },
    async createExport () {
      this.exporting = true
      try {
        const response = await createGradingExportApi(this.paper)
        this.saveBlob(response)
        const key = response.headers['x-export-key']
        this.$alert(`本次导出密钥：${key}`, '导出成功', { confirmButtonText: '知道了' })
      } finally {
        this.exporting = false
      }
    },
    async downloadExport (batch) {
      const response = await downloadGradingExportApi(batch.exportBatchId)
      this.saveBlob(response)
      this.$message.success('导出包已下载')
    },
    saveBlob (response) {
      const disposition = response.headers['content-disposition'] || ''
      const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
      const name = match ? decodeURIComponent(match[1]) : '试卷导出.zip'
      const url = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      link.download = name
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    },
    toggleKey (id) {
      this.$set(this.revealedKeys, id, !this.revealedKeys[id])
    },
    maskKey (key) {
      return key ? `${key.slice(0, 2)}••••••••••••${key.slice(-2)}` : '--'
    },
    async copyKey (key) {
      if (navigator.clipboard) await navigator.clipboard.writeText(key)
      else {
        const input = document.createElement('input')
        input.value = key
        document.body.appendChild(input)
        input.select()
        document.execCommand('copy')
        input.remove()
      }
      this.$message.success('密钥已复制')
    },
    statusLabel (status) {
      return { PENDING_GRADING: '待判分', GRADED: '已完成', RETURNED: '已退回' }[status] || status
    },
    statusType (status) {
      return { PENDING_GRADING: 'warning', GRADED: 'success', RETURNED: 'info' }[status] || ''
    },
    typeLabel (type) {
      return { single_choice: '单选题', multiple_choice: '多选题', true_false: '判断题', fill_blank: '填空题', practical: '实操题' }[type] || type
    },
    answerText (answer) {
      return this.formatStoredAnswer(answer.answerText, answer.subjectType)
    },
    correctAnswerText (answer) {
      return this.formatStoredAnswer(answer.correctAnswer, answer.subjectType)
    },
    formatStoredAnswer (value, type) {
      if (type === 'multiple_choice') {
        try { return JSON.parse(value || '[]').join('、') } catch (error) { return value || '--' }
      }
      return value || '--'
    },
    imageList (value) {
      return String(value || '').split(',').map(item => item.trim()).filter(Boolean)
    },
    userInitial (name) {
      return String(name || '?').trim().slice(0, 1).toUpperCase()
    },
    formatTime (value) {
      if (!value) return '--'
      return String(value).replace('T', ' ').slice(0, 19)
    }
  }
}
</script>

<style scoped>
.grading-page {
  --grading-ink: #1d3348;
  --grading-muted: #718090;
  --grading-line: #dfe6eb;
  --grading-blue: #3f759d;
  --grading-green: #2f7d61;
  color: var(--grading-ink);
}
.grading-heading { display: flex; align-items: center; justify-content: flex-end; gap: 20px; margin-bottom: 12px; }
.grading-heading h1 { margin: 0 0 6px; color: #172d44; font-size: 25px; font-weight: 700; line-height: 1.25; letter-spacing: 0; }
.grading-heading p, .export-history-title p { margin: 0; color: var(--grading-muted); font-size: 13px; }
.grading-heading-actions { display: flex; align-items: center; gap: 10px; }
.grading-heading-actions .el-button { min-height: 34px; margin-left: 0; border-radius: 5px; }
.grading-secondary-action { color: #41596f; border-color: #d4dee5; background: #fff; }
.grading-secondary-action:hover, .grading-secondary-action:focus { color: var(--grading-blue); border-color: #9bb5c8; background: #f5f9fb; }

.grading-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 16px; overflow: hidden; border: 1px solid var(--grading-line); border-radius: 7px; background: #fff; }
.grading-summary button { display: flex; align-items: center; justify-content: space-between; min-height: 82px; padding: 15px 22px; color: #6e7d8c; font-family: inherit; border: 0; border-right: 1px solid #e6ebef; background: #fff; cursor: pointer; transition: color .18s ease, background-color .18s ease, box-shadow .18s ease; }
.grading-summary button:last-child { border-right: 0; }
.grading-summary button:hover { color: #29455d; background: #f8fafb; }
.grading-summary button:focus-visible { position: relative; z-index: 1; outline: 2px solid #88aabd; outline-offset: -2px; }
.grading-summary button.active { color: var(--grading-green); box-shadow: inset 0 -3px var(--grading-green); background: #f5faf8; }
.grading-summary small { font-size: 12px; }
.grading-summary strong { color: #172d44; font-size: 25px; font-weight: 700; line-height: 1; }

.grading-filterbar { display: flex; align-items: center; gap: 9px; padding: 11px 12px; border: 1px solid var(--grading-line); border-bottom: 0; border-radius: 7px 7px 0 0; background: #f7f9fa; }
.grading-filterbar .el-select { width: 132px; }
.grading-filterbar .el-input { width: 240px; }
.grading-filterbar::v-deep .el-input__inner { height: 34px; color: #344a5f; border-color: #d6dfe5; border-radius: 5px; background: #fff; }
.grading-filterbar::v-deep .el-input__inner:focus { border-color: #7fa2ba; }
.grading-filterbar .el-button { min-height: 34px; margin-left: 0; color: #3f5c73; border-color: #d6dfe5; border-radius: 5px; background: #fff; }
.grading-filterbar .el-button:hover { color: #fff; border-color: var(--grading-blue); background: var(--grading-blue); }
.grading-result-count { margin-left: auto; color: #82909d; font-size: 12px; white-space: nowrap; }

.grading-table, .export-history-table { width: 100%; background: #fff !important; }
.grading-table { overflow: hidden; border: 1px solid var(--grading-line); border-radius: 0 0 7px 7px; }
.grading-table::v-deep::before,
.export-history-table::v-deep::before,
.grading-table::v-deep .el-table__fixed::before,
.grading-table::v-deep .el-table__fixed-right::before,
.export-history-table::v-deep .el-table__fixed::before,
.export-history-table::v-deep .el-table__fixed-right::before { display: none; }
.grading-table::v-deep .el-table__header-wrapper,
.grading-table::v-deep .el-table__header-wrapper div,
.grading-table::v-deep .el-table__header-wrapper th,
.export-history-table::v-deep .el-table__header-wrapper,
.export-history-table::v-deep .el-table__header-wrapper div,
.export-history-table::v-deep .el-table__header-wrapper th { background: #f6f8fa !important; }
.grading-table::v-deep .el-table__header-wrapper,
.export-history-table::v-deep .el-table__header-wrapper { border-bottom: 1px solid #e2e8ed !important; }
.grading-table::v-deep th.el-table__cell,
.export-history-table::v-deep th.el-table__cell { height: 46px; padding: 0; color: #657587 !important; font-size: 12px; font-weight: 600 !important; border-bottom-color: #e2e8ed !important; }
.grading-table::v-deep th.el-table__cell .cell,
.export-history-table::v-deep th.el-table__cell .cell { color: #657587 !important; font-family: inherit !important; font-size: 12px !important; font-weight: 600 !important; }
.grading-table::v-deep .el-table__body-wrapper,
.export-history-table::v-deep .el-table__body-wrapper { height: auto !important; margin-top: 0 !important; background: #fff !important; }
.grading-table::v-deep .el-table__body,
.grading-table::v-deep .el-table__row,
.grading-table::v-deep td.el-table__cell,
.export-history-table::v-deep .el-table__body,
.export-history-table::v-deep .el-table__row,
.export-history-table::v-deep td.el-table__cell { color: #3a4e61 !important; background: #fff !important; border-color: #edf0f2 !important; }
.grading-table::v-deep td.el-table__cell { height: 64px; padding: 0; }
.export-history-table::v-deep td.el-table__cell { height: 56px; padding: 0; }
.grading-table::v-deep td.el-table__cell .cell,
.export-history-table::v-deep td.el-table__cell .cell { color: #3a4e61 !important; font-family: inherit !important; font-size: 13px !important; font-weight: 400 !important; }
.grading-table::v-deep .el-table__row td:first-child,
.export-history-table::v-deep .el-table__row td:first-child { color: inherit !important; font-weight: 400 !important; }
.grading-table::v-deep .el-table__row:hover > td.el-table__cell,
.export-history-table::v-deep .el-table__row:hover > td.el-table__cell { background: #f7fafb !important; }
.grading-table::v-deep .el-table__empty-block,
.export-history-table::v-deep .el-table__empty-block { min-height: 220px; background: #fff !important; }
.grading-table::v-deep .el-table__empty-text,
.export-history-table::v-deep .el-table__empty-text { color: #97a3ae; font-family: inherit; font-size: 13px; }
.grading-table::v-deep .el-table__fixed,
.grading-table::v-deep .el-table__fixed-right,
.export-history-table::v-deep .el-table__fixed,
.export-history-table::v-deep .el-table__fixed-right { background: #fff !important; box-shadow: -6px 0 16px rgba(31, 52, 70, .06); }
.grading-table::v-deep .el-table__fixed-header-wrapper th,
.grading-table::v-deep .el-table__fixed-right .el-table__fixed-header-wrapper th,
.export-history-table::v-deep .el-table__fixed-header-wrapper th,
.export-history-table::v-deep .el-table__fixed-right .el-table__fixed-header-wrapper th { background: #f6f8fa !important; }

.grading-user { display: flex; align-items: center; gap: 10px; min-width: 0; }
.grading-user > span { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 34px; width: 34px; height: 34px; color: #315f82; font-size: 13px; font-weight: 700; border: 1px solid #d0e0eb; border-radius: 6px; background: #e2edf5; }
.grading-user strong { overflow: hidden; color: #263d52; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.grading-total-score { color: #21445e; font-size: 14px; font-weight: 700; }
.grading-status-tag { min-width: 62px; text-align: center; border-radius: 4px; }
.grading-row-action { color: var(--grading-blue); font-weight: 600; }
.grading-row-action:hover { color: #285d83; }

.export-history-title { display: flex; align-items: center; justify-content: space-between; padding-right: 34px; }
.export-history-title h2 { margin: 0 0 4px; color: #1d3348; font-size: 18px; font-weight: 700; letter-spacing: 0; }
.export-history-title .el-button { flex: 0 0 auto; color: #49677f; border-color: #d6e0e6; background: #fff; }
.export-history-table-wrap { overflow-x: auto; border: 1px solid var(--grading-line); border-radius: 6px; }
.export-history-table { min-width: 900px; }
.export-history-table code { display: inline-block; min-width: 144px; padding: 4px 7px; color: #31485c; font-family: Menlo, Monaco, Consolas, monospace; font-size: 12px; letter-spacing: 0; border: 1px solid #e2e8ed; border-radius: 4px; background: #f3f6f8; }
.export-history-table .el-button { margin-left: 8px; color: var(--grading-blue); }

.drawer-title { color: #172d44; font-size: 18px; font-weight: 700; }
.grading-detail { min-height: calc(100vh - 80px); padding: 0 28px 30px; background: #f5f7f9; }
.detail-meta { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 16px; overflow: hidden; border: 1px solid var(--grading-line); border-radius: 7px; background: #fff; }
.detail-meta > span { display: flex; align-items: center; justify-content: space-between; min-height: 68px; padding: 11px 16px; border-right: 1px solid #e6ebef; box-sizing: border-box; }
.detail-meta > span:last-child { border-right: 0; }
.detail-meta small { color: #7b8996; font-size: 12px; }
.detail-meta strong { color: #1d3348; font-size: 15px; }
.grading-answer { margin-bottom: 12px; padding: 20px; border: 1px solid #e0e6eb; border-radius: 7px; background: #fff; }
.grading-answer > header { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; gap: 12px; align-items: start; }
.grading-answer > header > span { display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px; color: #fff; font-size: 13px; font-weight: 700; background: var(--grading-green); border-radius: 5px; }
.grading-answer h3 { margin: 4px 0 0; color: #1f3549; font-size: 15px; line-height: 1.6; letter-spacing: 0; }
.grading-answer header small { color: #81909d; }
.grading-answer header b { color: #53697d; font-size: 13px; }
.answer-comparison { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 92px; gap: 10px; align-items: stretch; margin: 14px 0 0 46px; }
.answer-comparison > div { padding: 12px 14px; border: 1px solid #e0e6ea; border-radius: 5px; background: #f8fafb; }
.answer-comparison small { color: #7b8996; }
.answer-comparison p { margin: 6px 0 0; color: #2d4255; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }
.answer-comparison > strong { display: flex; align-items: center; justify-content: center; color: #976326; border: 1px solid #ead9c1; border-radius: 5px; background: #fff7ea; }
.answer-comparison > strong.correct { color: #2c7258; border-color: #c8dfd5; background: #eef7f3; }
.answer-note { margin: 13px 0 0 46px; color: #617486; font-size: 13px; line-height: 1.7; }
.answer-images { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 14px 0 0 46px; }
.answer-images .el-image { width: 100%; aspect-ratio: 4 / 3; overflow: hidden; border: 1px solid #dce4e9; border-radius: 5px; background: #f2f4f5; }
.answer-empty { margin: 14px 0 0 46px; padding: 14px; color: #896632; border: 1px dashed #e4d2b7; border-radius: 5px; background: #fffaf2; }
.manual-score { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin: 15px 0 0 46px; padding-top: 14px; border-top: 1px solid #edf0f2; }
.manual-score span { color: #657687; font-size: 13px; }
.manual-score b { color: #40566b; font-size: 13px; }
.detail-actions { position: sticky; z-index: 2; bottom: 0; display: flex; justify-content: space-between; gap: 12px; margin: 20px -28px -30px; padding: 14px 28px; border-top: 1px solid #dfe6eb; background: rgba(255, 255, 255, .97); }

@media (max-width: 760px) {
  .grading-heading { flex-direction: column; }
  .grading-heading-actions { flex-wrap: wrap; width: 100%; }
  .grading-heading-actions > span, .grading-heading-actions > .el-button { flex: 1 1 140px; }
  .grading-heading-actions > span .el-button { width: 100%; }
  .grading-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .grading-summary button { min-height: 72px; padding: 13px 16px; }
  .grading-summary button:nth-child(2) { border-right: 0; }
  .grading-summary button:nth-child(-n+2) { border-bottom: 1px solid #e6ebef; }
  .grading-filterbar { flex-wrap: wrap; }
  .grading-filterbar .el-select { flex: 0 1 128px; width: 128px; }
  .grading-filterbar .el-input { flex: 1 1 160px; width: auto; }
  .grading-result-count { flex: 1 0 100%; margin-left: 0; }
  .grading-table::v-deep .el-table__body-wrapper { overflow-x: auto !important; }
  .grading-detail { padding: 0 12px 24px; }
  .detail-meta { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .detail-meta > span:nth-child(2) { border-right: 0; }
  .detail-meta > span:nth-child(-n+2) { border-bottom: 1px solid #e6ebef; }
  .grading-answer { padding: 16px 14px; }
  .grading-answer > header { grid-template-columns: 32px minmax(0, 1fr); }
  .grading-answer > header > b { grid-column: 2; }
  .answer-comparison { grid-template-columns: minmax(0, 1fr); margin-left: 0; }
  .answer-images { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-left: 0; }
  .answer-note, .answer-empty, .manual-score { margin-left: 0; }
  .manual-score { align-items: flex-end; flex-wrap: wrap; }
  .detail-actions { flex-direction: column-reverse; margin: 18px -12px -24px; padding: 12px; }
  .detail-actions > .el-button, .detail-actions > div { width: 100%; margin-left: 0; }
  .detail-actions > div { display: flex; gap: 8px; }
  .detail-actions > div .el-button { flex: 1; margin-left: 0; }
}
</style>

<style>
.grading-history-dialog .el-dialog,
.grading-return-dialog .el-dialog { overflow: hidden; border-radius: 7px; box-shadow: 0 20px 54px rgba(23, 45, 68, .18); }
.grading-history-dialog .el-dialog__header,
.grading-return-dialog .el-dialog__header { padding: 20px 24px 16px; border-bottom: 1px solid #e5eaee; }
.grading-history-dialog .el-dialog__body { padding: 18px 24px 24px; }
.grading-return-dialog .el-dialog__title { color: #1d3348; font-size: 18px; font-weight: 700; }
.grading-return-dialog .el-dialog__body { padding: 20px 24px; }
.grading-return-dialog .el-dialog__footer { padding: 14px 24px 20px; border-top: 1px solid #edf0f2; }
.grading-drawer { background: #f5f7f9; }
.grading-drawer .el-drawer__header { min-height: 68px; margin-bottom: 0; padding: 0 28px; color: #172d44; border-bottom: 1px solid #dfe6eb; background: #fff; box-sizing: border-box; }
.grading-drawer .el-drawer__body { overflow: auto; background: #f5f7f9; }
@media (max-width: 760px) {
  .grading-history-dialog .el-dialog__header { padding: 18px 18px 14px; }
  .grading-history-dialog .el-dialog__body { padding: 14px 14px 18px; }
  .grading-return-dialog .el-dialog__header { padding: 18px 18px 14px; }
  .grading-return-dialog .el-dialog__body { padding: 18px; }
  .grading-return-dialog .el-dialog__footer { padding: 12px 18px 18px; }
  .grading-drawer .el-drawer__header { min-height: 62px; padding: 0 16px; }
}
</style>
