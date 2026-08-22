<template>
  <div class="q-box module-page">
    <main v-loading="loading" class="question-content">
      <header class="paper-heading module-heading">
        <div>
          <span class="paper-kicker">当前赛卷</span>
          <h1>{{ plan }} 卷答题区</h1>
        </div>
        <el-tag type="info">{{ totalSubjects }} 道题</el-tag>
      </header>

      <ParticipantPreviewNotice v-if="previewOnly" />
      <el-alert v-if="!previewOnly && submissionState.status === 'RETURNED'" :title="`试卷已退回：${submissionState.returnReason || '请修改后重新提交'}`" type="warning" :closable="false" show-icon class="submission-alert" />
      <el-alert v-else-if="!previewOnly && submissionLocked" :title="submissionState.status === 'GRADED' ? `判分已完成，总分 ${submissionState.totalScore || 0} 分` : '试卷已提交，等待管理员判分'" :type="submissionState.status === 'GRADED' ? 'success' : 'info'" :closable="false" show-icon class="submission-alert" />

      <el-alert v-if="errorText" :title="errorText" type="warning" :closable="false" show-icon />

      <div v-else-if="!loading && !questionList.length" class="question-empty">
        <i class="el-icon-document" />
        <p>当前试卷暂无题目</p>
      </div>

      <section v-for="module in questionList" :key="module.key" class="question-module">
        <div class="module-heading">
          <span>模块 {{ module.key }}</span>
          <h2>{{ module.title }}</h2>
        </div>

        <article v-for="item in module.subjects" :id="previewOnly ? undefined : `subject-${item.subject.subjectId}`" :key="item.previewKey || item.subject.subjectId" class="question-item">
          <div class="question-meta">
            <span class="question-number">{{ item.number }}</span>
            <el-tag size="small" :type="subjectTypeTag(item.subject.subjectType)">
              {{ subjectTypeLabel(item.subject.subjectType) }}
            </el-tag>
            <span v-if="!previewOnly && Number(item.subject.score || 0)" class="question-score">{{ item.subject.score }} 分</span>
          </div>
          <h3>{{ item.subject.subjectName }}</h3>

          <ul v-if="!previewOnly && answeringParts(item.subject.answering).length" class="answering-notes">
            <li v-for="(note, noteIndex) in answeringParts(item.subject.answering)" :key="noteIndex">{{ note }}</li>
          </ul>

          <div v-if="item.subject.screenshotRequirement" class="screenshot-requirement">
            <strong>截图要求</strong><span>{{ item.subject.screenshotRequirement }}</span>
          </div>

          <div v-if="item.subject.point" class="question-environment">
            <el-button type="text" icon="el-icon-monitor" :disabled="previewOnly" @click="onAnswerEnvironment(item)">
              {{ environmentButtonLabel(item.subject.point) }}
            </el-button>
          </div>

          <div v-if="item.subject.subjectType === 'single_choice'" class="answer-control">
            <el-radio-group v-model="item.value" :disabled="submissionLocked || previewOnly">
              <el-radio v-for="option in item.subject.options" :key="option" :label="option">{{ option }}</el-radio>
            </el-radio-group>
          </div>

          <div v-else-if="item.subject.subjectType === 'multiple_choice'" class="answer-control">
            <el-checkbox-group v-model="item.value" :disabled="submissionLocked || previewOnly">
              <el-checkbox v-for="option in item.subject.options" :key="option" :label="option">{{ option }}</el-checkbox>
            </el-checkbox-group>
          </div>

          <div v-else-if="item.subject.subjectType === 'true_false'" class="answer-control">
            <el-radio-group v-model="item.value" :disabled="submissionLocked || previewOnly">
              <el-radio label="正确">正确</el-radio>
              <el-radio label="错误">错误</el-radio>
            </el-radio-group>
          </div>

          <el-input
            v-else-if="item.subject.subjectType === 'fill_blank'"
            v-model="item.value"
            class="answer-control"
            type="textarea"
            :rows="3"
            maxlength="5000"
            show-word-limit
            placeholder="请输入答案"
            :disabled="submissionLocked || previewOnly"
          />

          <div v-else-if="item.subject.subjectType === 'practical'" class="practical-control">
            <dthj ref="practicalAnswers" ref-in-for :subject="item" :disabled="submissionLocked || previewOnly" :preview-mode="previewOnly" />
          </div>

          <div v-if="item.subject.subjectType !== 'practical' && !submissionLocked && !previewOnly" class="question-actions">
            <el-button type="primary" size="small" icon="el-icon-check" :loading="item.saving" @click="saveAnswer(item)">
              保存答案
            </el-button>
          </div>
        </article>
      </section>

      <div v-if="totalSubjects && !previewOnly" class="submit-zone">
        <el-button v-if="!submissionLocked" type="primary" icon="el-icon-upload2" :loading="submitting" @click="submitPaper">提交试卷</el-button>
        <span v-else class="submitted-state"><i :class="submissionState.status === 'GRADED' ? 'el-icon-circle-check' : 'el-icon-time'" />{{ submissionState.status === 'GRADED' ? '判分已完成' : '已提交，等待判分' }}</span>
      </div>
    </main>

    <aside class="btn-list">
      <button class="help-button" title="比赛帮助" @click="handleTips">
        <i class="el-icon-question" />
      </button>
    </aside>

    <el-drawer title="比赛帮助" :size="drawerSize" :visible.sync="isShowTips" class="q-drawer-tips">
      <div v-if="competitionHelpContent" class="user-help-copy">{{ competitionHelpContent }}</div>
      <el-collapse v-else v-model="activeName" accordion>
        <el-collapse-item title="数据资源地址" name="4">
          <ul>
            <li v-for="(item, index) in collapseList4" :key="index" title="双击复制" @dblclick="copyCode(item.left)">
              <span class="li-left">{{ `${index + 1}. ${item.left}` }}</span>
              <span class="li-right">{{ item.right }}</span>
            </li>
          </ul>
        </el-collapse-item>
      </el-collapse>
    </el-drawer>
  </div>
</template>

<script>
import { getPlan, getRole } from '@/utils/auth'
import { adminParticipantPreviewSubjectsApi, getSubject, saveSubjectAnswerApi, competitionApi, paperSubmissionStatusApi, submitPaperApi } from '@/api/Match'
import { createDefaultCompetitionHelpItems } from '@/utils/competitionDefaults'
import dthj from '@/components/dthj'
import ParticipantPreviewNotice from '@/components/ParticipantPreviewNotice.vue'
const { isPreviewRoute } = require('@/services/participantPreview')

export default {
  components: { dthj, ParticipantPreviewNotice },
  data () {
    return {
      loading: false,
      errorText: '',
      isShowTips: false,
      activeName: '4',
      drawerSize: window.innerWidth <= 760 ? '90%' : '50%',
      plan: getPlan(),
      questionList: [],
      submissionState: { status: 'DRAFT', locked: false },
      submitting: false,
      competitionHelpContent: '',
      collapseList4: []
    }
  },
  computed: {
    totalSubjects () {
      return this.questionList.reduce((total, module) => total + module.subjects.length, 0)
    },
    submissionLocked () {
      return Boolean(this.submissionState.locked)
    },
    previewOnly () {
      return isPreviewRoute(this.$route, getRole())
    },
    unansweredCount () {
      return this.allItems().filter(item => {
        if (item.subject.subjectType === 'practical') {
          return !(item.answerSheet && String(item.answerSheet.answerImg || '').trim())
        }
        return Array.isArray(item.value) ? item.value.length === 0 : !String(item.value || '').trim()
      }).length
    }
  },
  mounted () {
    if (!this.plan) {
      this.$router.push({ path: '/Publicity' })
      return
    }
    this.renderDrawer()
    this.loadCompetitionHelp()
    if (this.previewOnly) this.loadSubjects()
    else Promise.all([this.loadSubjects(), this.loadSubmissionStatus()])
  },
  methods: {
    async loadCompetitionHelp () {
      try {
        const result = await competitionApi()
        if (result.code === 200) this.competitionHelpContent = String(result.data.competitionHelpContent || '')
      } catch (error) {
        // Keep the built-in resource help available when settings cannot be loaded.
      }
    },
    async loadSubjects () {
      this.loading = true
      this.errorText = ''
      try {
        const result = this.previewOnly
          ? await adminParticipantPreviewSubjectsApi({ testPaperType: this.plan })
          : await getSubject({ testPaperType: this.plan })
        if (result.code !== 200) {
          this.questionList = []
          this.errorText = result.msg || '试卷读取失败'
          return
        }
        const records = result.data || []
        this.questionList = this.groupSubjects(this.previewOnly ? records.map(this.sanitizePreviewRecord) : records)
      } catch (error) {
        this.errorText = '试卷读取失败，请稍后重试'
      } finally {
        this.loading = false
      }
    },
    async loadSubmissionStatus () {
      try {
        const result = await paperSubmissionStatusApi(this.plan)
        if (result.code === 200) this.submissionState = result.data || { status: 'DRAFT', locked: false }
      } catch (error) {
        // The answer list remains available if status refresh fails.
      }
    },
    groupSubjects (records) {
      const modules = []
      const moduleIndex = {}
      records.forEach((record, index) => {
        const subject = record.subject
        const key = subject.modular || '未分组'
        if (moduleIndex[key] === undefined) {
          moduleIndex[key] = modules.length
          modules.push({ key, title: subject.modularName || `模块 ${key}`, subjects: [] })
        }
        modules[moduleIndex[key]].subjects.push(this.prepareRecord(record, index + 1))
      })
      return modules
    },
    sanitizePreviewRecord (record, index) {
      const source = (record && record.subject) || {}
      const subject = {
        modular: source.modular,
        modularName: source.modularName,
        subjectType: source.subjectType,
        subjectName: source.subjectName,
        options: Array.isArray(source.options) ? source.options.slice() : [],
        screenshotRequirement: source.screenshotRequirement,
        point: source.point
      }
      return { previewKey: `preview-subject-${index + 1}`, subject, answerSheet: null }
    },
    prepareRecord (record, number) {
      const answerText = !this.previewOnly && record.answerSheet ? record.answerSheet.answerText : ''
      let value = answerText || ''
      if (record.subject.subjectType === 'multiple_choice') {
        try {
          const parsed = JSON.parse(answerText || '[]')
          value = Array.isArray(parsed) ? parsed : []
        } catch (error) {
          value = []
        }
      }
      return Object.assign({}, record, { number, value, savedValue: this.cloneValue(value), saving: false })
    },
    answeringParts (answering) {
      return (answering || '').split(/[。\n]/).map(value => value.trim()).filter(Boolean)
    },
    subjectTypeLabel (type) {
      return {
        single_choice: '单选题',
        multiple_choice: '多选题',
        true_false: '判断题',
        fill_blank: '填空题',
        practical: '实操题'
      }[type] || type
    },
    subjectTypeTag (type) {
      if (type === 'practical') return 'danger'
      if (type === 'multiple_choice') return 'warning'
      if (type === 'true_false') return 'success'
      if (type === 'fill_blank') return 'info'
      return ''
    },
    environmentButtonLabel (point) {
      return {
        code: '进入代码环境',
        cvat: '进入标注环境',
        t100: '进入推理环境'
      }[point] || '进入答题环境'
    },
    async saveAnswer (item) {
      if (this.previewOnly) return
      const formData = new FormData()
      formData.append('subjectId', item.subject.subjectId)
      formData.append('answerText', item.subject.subjectType === 'multiple_choice' ? JSON.stringify(item.value) : (item.value || ''))
      item.saving = true
      try {
        const result = await saveSubjectAnswerApi(formData)
        if (result.code === 200) {
          item.savedValue = this.cloneValue(item.value)
          this.$message.success('答案已保存')
        }
      } catch (error) {
        // The shared interceptor displays the save error.
      } finally {
        item.saving = false
      }
    },
    onAnswerEnvironment (item) {
      if (this.previewOnly) return
      const urlByPoint = {
        code: item.subject.vscodeUrl,
        cvat: item.subject.cvatUrl,
        t100: item.subject.t100Url
      }
      const target = urlByPoint[item.subject.point]
      if (!target) {
        this.$message.error('当前题目没有可用的答题环境')
        return
      }
      window.open(/^https?:\/\//.test(target) ? target : `http://${target}`)
    },
    handleTips () {
      this.isShowTips = !this.isShowTips
    },
    async submitPaper () {
      if (this.previewOnly) return
      const dirty = this.allItems().find(item => item.subject.subjectType !== 'practical' && this.valueKey(item.value) !== this.valueKey(item.savedValue))
      const dirtyPractical = (this.$refs.practicalAnswers || []).find(component => component.hasUnsavedChanges && component.hasUnsavedChanges())
      if (dirty || dirtyPractical) {
        this.$message.error('仍有未保存的答案或图片，请先保存后再提交')
        if (dirty) this.scrollToSubject(dirty.subject.subjectId)
        return
      }
      try {
        const message = this.unansweredCount > 0
          ? `还有 ${this.unansweredCount} 道题未完成，仍可提交。提交后试卷将锁定，只有管理员退回后才能修改。`
          : '提交后试卷将锁定，只有管理员退回后才能修改。确定提交吗？'
        await this.$confirm(message, '提交试卷', {
          confirmButtonText: '确认提交',
          cancelButtonText: '继续检查',
          type: 'warning'
        })
      } catch (error) {
        return
      }
      this.submitting = true
      try {
        const result = await submitPaperApi(this.plan)
        if (result.code === 200) {
          this.submissionState = Object.assign({}, result.data, { locked: true, submitted: true })
          this.$message.success('试卷已提交')
        }
      } catch (error) {
        const data = error && error.response && error.response.data && error.response.data.data
        const subjectIds = data && Array.isArray(data.subjectIds) ? data.subjectIds : []
        if (subjectIds.length) this.scrollToSubject(subjectIds[0])
      } finally {
        this.submitting = false
      }
    },
    allItems () {
      return this.questionList.reduce((items, module) => items.concat(module.subjects), [])
    },
    cloneValue (value) {
      return Array.isArray(value) ? value.slice() : value
    },
    valueKey (value) {
      return JSON.stringify(Array.isArray(value) ? value.slice().sort() : (value || ''))
    },
    scrollToSubject (subjectId) {
      this.$nextTick(() => {
        const target = document.getElementById(`subject-${subjectId}`)
        if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
    },
    copyCode (code) {
      const copyContent = document.createElement('input')
      copyContent.value = code
      document.body.appendChild(copyContent)
      copyContent.select()
      document.execCommand('Copy')
      copyContent.remove()
      this.$message.success('复制成功')
    },
    renderDrawer () {
      this.collapseList4 = createDefaultCompetitionHelpItems(this.plan, location.host)
    }
  }
}
</script>

<style scoped>
@import url(../assets/style/question.css);

.question-content {
  width: calc(100% - 60px);
  min-height: 100%;
  box-sizing: border-box;
  padding: 24px clamp(18px, 4vw, 56px) 50px;
}

.paper-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid #dcdfe6;
}

.paper-heading h1 { margin: 4px 0 0; text-align: left; font-size: 24px; letter-spacing: 0; }
.paper-kicker { color: #909399; font-size: 12px; }
.question-module { margin-top: 30px; }
.module-heading { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.module-heading span { color: #909399; font-size: 12px; }
.module-heading h2 { margin: 0; font-size: 18px; letter-spacing: 0; }

.question-item {
  margin-bottom: 14px;
  padding: 18px 20px;
  border: 1px solid #dcdfe6;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  background: #fff;
}

.question-meta { display: flex; align-items: center; gap: 10px; }
.question-number { color: #303133; font-weight: 700; }
.question-score { margin-left: auto; color: #7b8792; font-size: 12px; }
.question-item h3 { margin: 12px 0; color: #303133; font-size: 16px; line-height: 1.7; letter-spacing: 0; }
.answering-notes { margin: 0 0 16px; padding-left: 20px; color: #606266; line-height: 1.8; }
.screenshot-requirement { display: flex; gap: 10px; margin: 0 0 16px; padding: 11px 14px; color: #606266; font-size: 13px; line-height: 1.7; background: #f2f4f5; border-radius: 4px; }
.screenshot-requirement strong { flex: 0 0 auto; color: #303133; }
.answer-control { margin: 12px 0; }
.answer-control .el-radio, .answer-control .el-checkbox { display: block; margin: 10px 0; white-space: normal; }
.question-actions { display: flex; justify-content: flex-end; margin-top: 14px; }
.practical-control { max-width: 760px; }
.question-environment { margin: 12px 0; }
.question-empty { padding: 80px 20px; color: #909399; text-align: center; }
.question-empty i { font-size: 42px; }
.question-empty p { margin-top: 12px; }
.submission-alert { margin-top: 16px; }
.submit-zone { display: flex; justify-content: flex-end; margin: 24px 0 0; }
.submit-zone .el-button { min-width: 132px; }
.submitted-state { display: inline-flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 14px; color: #2f6f5e; font-size: 13px; font-weight: 600; border: 1px solid #cde0da; background: #f0f7f5; border-radius: 4px; }
.btn-list { position: fixed; z-index: 1200; right: 18px; bottom: 22px; left: auto; width: 54px; height: 54px; }
.help-button { display: inline-flex; align-items: center; justify-content: center; width: 54px; height: 54px; margin: 0; color: #fff; font-size: 23px; border: 0; border-radius: 7px; background: var(--platform-theme-color, #162d45); box-shadow: 0 9px 24px rgba(22, 42, 60, .22); cursor: pointer; }
.help-button:hover { filter: brightness(.94); }
.user-help-copy { padding: 8px 24px 24px; color: #435568; font-size: 14px; line-height: 1.85; white-space: pre-wrap; word-break: break-word; }

@media (max-width: 760px) {
  .question-content { width: 100%; padding: 16px 12px 80px; }
  .btn-list { right: 12px; bottom: 12px; width: 50px; height: 50px; }
  .help-button { width: 56px; height: 56px; margin: 0; }
  .paper-heading h1 { font-size: 20px; }
  .question-item { padding: 15px 14px; }
}
</style>
