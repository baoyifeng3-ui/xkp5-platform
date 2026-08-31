<template>
  <div class="admin-page">
    <section v-if="mustChangePassword" class="password-gate">
      <span class="password-gate-icon"><i class="el-icon-lock" /></span>
      <h1>请先修改管理员密码</h1>
      <p>首次进入管理端需要完成密码更新。</p>
      <el-button type="primary" icon="el-icon-lock" @click="passwordDialog = true">修改密码</el-button>
    </section>

    <div v-else class="admin-workspace">
      <el-tabs v-model="activeTab" tab-position="left" class="admin-tabs">
        <el-tab-pane name="timer">
          <span slot="label" class="admin-tab-label" title="比赛控制">
            <i class="el-icon-odometer" />
            <span>比赛控制</span>
          </span>
          <section class="workspace-page control-page">
            <div class="summary-band competition-summary" aria-label="比赛状态概览">
              <div class="summary-item summary-paper">
                <span class="summary-icon"><i class="el-icon-document" /></span>
                <div><small>当前赛卷</small><strong>{{ activePaper ? `${activePaper} 卷` : '未选择' }}</strong></div>
              </div>
              <div class="summary-item">
                <span class="summary-icon summary-icon-success"><i class="el-icon-video-play" /></span>
                <div><small>计时状态</small><strong>{{ countDownStatusLabel }}</strong></div>
              </div>
              <div class="summary-item">
                <span class="summary-icon"><i class="el-icon-time" /></span>
                <div><small>剩余时间</small><strong>{{ formatRemaining(countDown.remainingSeconds) }}</strong></div>
              </div>
              <div class="summary-item">
                <span class="summary-icon"><i class="el-icon-alarm-clock" /></span>
                <div><small>距离比赛开始</small><strong>{{ preStartRemainingLabel }}</strong></div>
              </div>
            </div>

            <section class="admin-panel schedule-panel">
              <header class="schedule-heading">
                <div class="schedule-title"><span class="schedule-icon"><i class="el-icon-date" /></span><div><strong>赛前开放设置</strong><small>设置登录开放、正式开始与预计结束时间</small></div></div>
                <span class="schedule-state">{{ countDownStatusLabel }}</span>
              </header>
              <div class="schedule-fields">
                <div class="schedule-field schedule-start-field"><span class="field-label">正式开始时间</span><el-date-picker v-model="scheduleForm.startTime" type="datetime" value-format="timestamp" placeholder="选择正式开始时间" /></div>
                <div class="schedule-field"><span class="field-label">提前开放登录</span><div class="schedule-number"><el-input-number v-model="scheduleForm.preLoginMinutes" :min="0" :max="10080" controls-position="right" /><span>分钟</span></div></div>
                <div class="schedule-field"><span class="field-label">比赛时长</span><div class="schedule-number"><el-input-number v-model="scheduleForm.durationMinutes" :min="1" :max="10080" controls-position="right" /><span>分钟</span></div></div>
              </div>
              <div class="schedule-timeline">
                <div class="schedule-milestone"><small>开放登录</small><strong>{{ scheduleLoginOpenLabel }}</strong></div>
                <span class="schedule-line"><i /></span>
                <div class="schedule-milestone"><small>正式开始</small><strong>{{ scheduleStartLabel }}</strong></div>
                <span class="schedule-line"><i /></span>
                <div class="schedule-milestone"><small>预计结束</small><strong>{{ scheduleEndLabel }}</strong></div>
                <el-button type="primary" icon="el-icon-check" :loading="scheduleSaving" @click="saveSchedule">保存排期</el-button>
              </div>
            </section>

            <div class="control-grid">
              <section class="admin-panel timer-panel">
                <header class="panel-heading">
                  <div><span class="panel-icon"><i class="el-icon-timer" /></span><strong>比赛计时</strong></div>
                  <span class="panel-state">{{ countDownStatusLabel }}</span>
                </header>
                <div class="panel-body timer-actions">
                  <div class="command-group">
                    <span class="field-label">快速控制</span>
                    <div class="command-row">
                      <el-button type="primary" icon="el-icon-video-play" :loading="timerUpdating" @click="timerAction('START', 240)">开始 4 小时</el-button>
                      <el-button icon="el-icon-plus" @click="timerAction('EXTEND', 15)">15 分钟</el-button>
                      <el-button icon="el-icon-plus" @click="timerAction('EXTEND', 30)">30 分钟</el-button>
                      <el-button icon="el-icon-plus" @click="timerAction('EXTEND', 60)">60 分钟</el-button>
                      <el-button v-if="countDown.status === 'RUNNING'" icon="el-icon-video-pause" @click="timerAction('PAUSE')">暂停</el-button>
                      <el-button v-if="countDown.status === 'PAUSED'" icon="el-icon-video-play" @click="timerAction('RESUME')">恢复</el-button>
                    </div>
                  </div>
                  <div class="command-group timer-custom-row">
                    <div>
                      <span class="field-label">自定义延时</span>
                      <div class="inline-control">
                        <el-input-number v-model="customMinutes" :min="1" :max="10080" controls-position="right" />
                        <el-button @click="timerAction('EXTEND', customMinutes)">应用分钟数</el-button>
                      </div>
                    </div>
                    <div>
                      <span class="field-label">指定结束时间</span>
                      <div class="inline-control">
                        <el-date-picker v-model="customEndTime" type="datetime" value-format="timestamp" placeholder="选择结束时间" />
                        <el-button @click="timerAction('SET_END')">设置</el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </section>

              <section class="admin-panel paper-panel">
                <header class="panel-heading">
                  <div><span class="panel-icon"><i class="el-icon-document" /></span><strong>赛卷资源</strong></div>
                  <div class="paper-heading-actions">
                    <el-button size="small" icon="el-icon-plus" @click="openPaperDialog">新增试卷</el-button>
                    <el-radio-group v-model="paperDraft" size="small" class="paper-selector">
                      <el-radio-button v-for="paper in availablePapers" :key="paper" :label="paper" :disabled="!paperSelectable(paper)">{{ paper }} 卷</el-radio-button>
                    </el-radio-group>
                  </div>
                </header>
                <div class="panel-body paper-resources">
                  <div v-for="paper in availablePapers" :key="paper" :class="['paper-resource-row', { 'is-active': activePaper === paper }]">
                    <span class="paper-letter">{{ paper }}</span>
                    <div><strong>{{ paper }} 卷</strong><small>{{ paperResourceLabel(paper) }}</small></div>
                    <span :class="['resource-state', { 'is-ready': paperReady(paper) }]">
                      <i />{{ paperReady(paper) ? '资源完整' : '资源缺失' }}
                    </span>
                  </div>
                  <el-button type="primary" icon="el-icon-check" :disabled="!paperDraft" @click="selectPaper">
                    启用所选赛卷
                  </el-button>
                </div>
              </section>
            </div>

            <section class="danger-zone">
              <div><strong>危险操作</strong><span>这些操作会立即影响所有参赛账号。</span></div>
              <div>
                <el-button type="danger" icon="el-icon-switch-button" :disabled="competitionFinished" :loading="timerUpdating" @click="timerAction('END')">{{ competitionFinished ? '比赛已结束' : '结束比赛' }}</el-button>
              </div>
            </section>
          </section>
        </el-tab-pane>

        <el-tab-pane name="rules">
          <span slot="label" class="admin-tab-label" title="赛规赛程编辑"><i class="el-icon-edit-outline" /><span>赛规赛程编辑</span></span>
          <section class="workspace-page content-editor-page">
            <el-tabs v-model="rulesTab" class="rules-tabs">
              <el-tab-pane label="赛规赛程" name="rules">
            <div class="content-editor-toolbar">
              <el-radio-group v-model="competitionContentKey" size="small">
                <el-radio-button v-for="item in competitionContentTabs" :key="item.key" :label="item.key">{{ item.label }}</el-radio-button>
              </el-radio-group>
              <el-button type="primary" icon="el-icon-check" :loading="competitionContentSaving" @click="saveCompetitionContent">保存内容</el-button>
            </div>
            <div class="content-editor-grid">
              <section class="admin-panel content-editor-input">
                <header class="panel-heading"><div><span class="panel-icon"><i class="el-icon-edit" /></span><strong>编辑内容</strong></div></header>
                <div class="panel-body">
                  <el-form label-position="top">
                    <el-form-item label="标题">
                      <el-input v-model="activeCompetitionSection.title" maxlength="100" show-word-limit />
                    </el-form-item>
                    <el-form-item label="正文">
                      <el-input v-model="activeCompetitionSection.body" type="textarea" :rows="20" maxlength="100000" placeholder="输入赛规赛程正文；换行会在预览中保留" />
                    </el-form-item>
                  </el-form>
                </div>
              </section>
              <section class="admin-panel content-editor-preview">
                <header class="panel-heading"><div><span class="panel-icon"><i class="el-icon-view" /></span><strong>实时预览</strong></div></header>
                <div class="competition-preview-body">
                  <h2>{{ activeCompetitionSection.title || activeCompetitionTabLabel }}</h2>
                  <div class="competition-preview-copy">{{ activeCompetitionSection.body || '尚未填写内容' }}</div>
                </div>
              </section>
            </div>
              </el-tab-pane>
              <el-tab-pane label="比赛公告" name="announcement">
            <section class="admin-panel announcement-field-panel">
              <div class="section-heading-row"><div><h2>比赛公告</h2><p>独立参赛选手清单，不关联平台账号</p></div><el-button type="primary" icon="el-icon-plus" @click="editAnnouncementEntry()">新增条目</el-button></div>
              <el-table :data="announcementEntries" empty-text="暂无公告条目" class="admin-table announcement-entry-table">
                <el-table-column v-for="field in announcementFields.filter(item => item.enabled)" :key="field.fieldKey" :label="field.fieldName" min-width="130"><template slot-scope="scope">{{ announcementValue(scope.row, field.fieldKey) }}</template></el-table-column>
                <el-table-column label="操作" width="130" align="right"><template slot-scope="scope"><el-button type="text" size="mini" @click="editAnnouncementEntry(scope.row)">编辑</el-button><el-button type="text" size="mini" class="is-danger" @click="removeAnnouncementEntry(scope.row)">删除</el-button></template></el-table-column>
              </el-table>
            </section>
              </el-tab-pane>
              <el-tab-pane label="注意事项" name="notice">
            <section v-loading="noticeLoading" class="admin-panel notice-editor-panel">
              <div class="section-heading-row"><div><h2>注意事项</h2><p>编辑首页只读展示的注意事项正文</p></div><el-button type="primary" icon="el-icon-check" :loading="noticeSaving" :disabled="noticeLoading" @click="saveNotice">保存内容</el-button></div>
              <el-input v-model="noticeContent" type="textarea" :rows="5" resize="vertical" placeholder="请输入首页注意事项" aria-label="首页注意事项正文" />
            </section>
              </el-tab-pane>
              <el-tab-pane label="公告字段" name="fields">
            <section class="admin-panel announcement-field-panel">
              <div class="section-heading-row"><div><h2>比赛公告字段</h2><p>控制首页比赛公告表格的字段名称与显示状态</p></div><el-button type="primary" plain icon="el-icon-plus" @click="openAnnouncementFieldDialog()">新增字段</el-button></div>
              <el-table :data="announcementFields" empty-text="暂无公告字段" class="admin-table announcement-field-table">
                <el-table-column prop="fieldName" label="字段名称" min-width="220" />
                <el-table-column label="状态" width="170"><template slot-scope="scope"><div class="account-status"><el-switch :value="scope.row.enabled" active-color="#76c7aa" inactive-color="#c6cdd2" :aria-label="`${scope.row.fieldName}状态`" @change="toggleAnnouncementField(scope.row, $event)" /><span :class="['account-status-label', { 'is-enabled': scope.row.enabled }]">{{ scope.row.enabled ? '已启用' : '已关闭' }}</span></div></template></el-table-column>
                <el-table-column label="操作" width="100" align="right"><template slot-scope="scope"><el-button class="table-action" icon="el-icon-edit-outline" circle aria-label="编辑公告字段" @click="openAnnouncementFieldDialog(scope.row)" /></template></el-table-column>
              </el-table>
            </section>
              </el-tab-pane>
            </el-tabs>
          </section>
        </el-tab-pane>

        <el-tab-pane name="subjects">
          <span slot="label" class="admin-tab-label" title="试卷题目">
            <i class="el-icon-document" />
            <span>试卷题目</span>
          </span>
          <section class="workspace-page subject-panel">
            <header class="workspace-heading">
              <div class="subject-heading-actions">
                <div class="subject-clear-actions">
                  <el-button type="danger" plain icon="el-icon-delete" :loading="subjectAnswersClearing" :disabled="subjectsClearing" @click="clearSubjectAnswers">清空作答记录</el-button>
                  <el-button type="danger" plain icon="el-icon-delete" :loading="subjectsClearing" :disabled="subjectAnswersClearing" @click="clearAllSubjects">清空全部题目</el-button>
                </div>
                <div class="subject-main-actions">
                  <el-button icon="el-icon-question" @click="openCompetitionHelp">比赛帮助</el-button>
                  <el-button type="primary" icon="el-icon-plus" :disabled="subjectAnswersClearing || subjectsClearing" @click="openSubjectCreateDialog">新增题目</el-button>
                </div>
              </div>
            </header>
            <div class="admin-filterbar subject-toolbar">
              <el-radio-group v-model="subjectPaper" size="small" class="paper-selector">
                <el-radio-button v-for="paper in availablePapers" :key="paper" :label="paper">{{ paper }} 卷</el-radio-button>
              </el-radio-group>
              <el-select v-model="subjectTypeFilter" size="small" clearable placeholder="全部题型">
                <el-option v-for="type in subjectTypes" :key="type.value" :label="type.label" :value="type.value" />
              </el-select>
              <el-input v-model="subjectKeyword" size="small" clearable prefix-icon="el-icon-search" placeholder="搜索题干" class="subject-search" @keyup.enter.native="loadSubjects" @clear="loadSubjects" />
              <el-button size="small" icon="el-icon-search" @click="loadSubjects">查询</el-button>
              <span class="filter-result">共 {{ subjects.length }} 题</span>
            </div>
            <div v-loading="subjectLoading" class="subject-switcher">
              <section v-for="module in subjectModules" :key="module.key" class="subject-module-row">
                <header class="subject-module-heading"><strong>模块 {{ module.key }}</strong><span>{{ module.title }}</span></header>
                <div class="subject-table-scroll">
                  <table class="subject-list-table">
                    <colgroup><col class="subject-col-order"><col><col class="subject-col-type"><col class="subject-col-actions"></colgroup>
                    <thead><tr><th scope="col">序号</th><th scope="col">题目</th><th scope="col">题目类型</th><th scope="col">操作</th></tr></thead>
                    <tbody>
                      <tr v-for="subject in module.subjects" :key="subject.subjectId" :class="{ 'is-active': editingSubject.subjectId === subject.subjectId }">
                        <td class="subject-row-order">{{ subject.sortOrder || 1 }}</td>
                        <td class="subject-row-copy">{{ subject.subjectName }}</td>
                        <td class="subject-row-type">{{ subjectTypeLabel(subject.subjectType) }}</td>
                        <td class="subject-row-actions">
                          <el-button type="text" size="mini" @click="openSubjectEditor(subject)">编辑</el-button>
                          <el-button type="text" size="mini" class="is-danger" @click="deleteSubject(subject)">删除</el-button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
              <span v-if="!subjects.length" class="subject-switcher-empty">当前试卷暂无题目，请新增题目</span>
            </div>
          </section>
        </el-tab-pane>

        <el-tab-pane name="grading">
          <span slot="label" class="admin-tab-label" title="试卷评分"><i class="el-icon-finished" /><span>试卷评分</span></span>
          <admin-grading ref="adminGrading" :active-paper="activePaper" :competition-finished="competitionFinished" />
        </el-tab-pane>

        <el-tab-pane name="users">
          <span slot="label" class="admin-tab-label" title="比赛账号"><i class="el-icon-user" /><span>比赛账号</span></span>
          <section class="workspace-page account-panel">
            <header class="workspace-heading"><div><h1>比赛账号</h1><p>{{ users.length }} 个账号</p></div><div class="account-heading-actions"><el-button type="danger" plain icon="el-icon-delete" :loading="userClearing" :disabled="participantUserCount === 0 || userClearing" @click="clearUsers">清空用户</el-button><el-button type="primary" icon="el-icon-plus" :disabled="userClearing" @click="openUserBatchDialog">新增账号</el-button></div></header>
            <div class="summary-band" aria-label="账号状态概览">
              <div class="summary-item"><span class="summary-icon"><i class="el-icon-user" /></span><div><small>全部账号</small><strong>{{ users.length }}</strong></div></div>
              <div class="summary-item"><span class="summary-icon summary-icon-success"><i class="el-icon-check" /></span><div><small>已启用</small><strong>{{ enabledUserCount }}</strong></div></div>
              <div class="summary-item"><span class="summary-icon summary-icon-muted"><i class="el-icon-minus" /></span><div><small>已停用</small><strong>{{ disabledUserCount }}</strong></div></div>
            </div>
            <div class="admin-filterbar account-toolbar">
              <el-input v-model="userKeyword" clearable prefix-icon="el-icon-search" placeholder="搜索账号、学校、选手或老师" aria-label="搜索账号、学校、参赛选手或带队老师" class="account-search" />
              <el-select v-model="userStatusFilter" aria-label="筛选账号状态" class="account-filter"><el-option label="全部状态" value="all" /><el-option label="已启用" value="enabled" /><el-option label="已停用" value="disabled" /></el-select>
              <span class="filter-result">当前显示 {{ filteredUsers.length }} 个</span>
            </div>
            <el-table v-loading="userLoading" :data="filteredUsers" :empty-text="userEmptyText" row-key="userId" class="admin-table account-table">
              <el-table-column label="账号" min-width="220">
                <template slot-scope="scope"><div class="account-identity"><span :class="['account-avatar', { 'is-admin': isAdminUser(scope.row) }]">{{ accountInitial(scope.row.userName) }}</span><div class="account-identity-copy"><div class="account-name-row"><strong>{{ scope.row.userName }}</strong><span v-if="isAdminUser(scope.row)" class="account-admin-badge"><i class="el-icon-lock" /> 系统管理员</span><span v-else class="account-role">参赛账号</span></div><span class="account-id">用户 ID {{ scope.row.userId }}</span></div></div></template>
              </el-table-column>
              <el-table-column label="密码" min-width="130"><template slot-scope="scope"><code class="account-password">{{ scope.row.password || '--' }}</code></template></el-table-column>
              <el-table-column prop="schoolName" label="学校名称" min-width="180"><template slot-scope="scope"><span class="account-field">{{ scope.row.schoolName || '--' }}</span></template></el-table-column>
              <el-table-column prop="contestantName" label="参赛选手" min-width="130"><template slot-scope="scope"><span class="account-field">{{ scope.row.contestantName || '--' }}</span></template></el-table-column>
              <el-table-column prop="teacherName" label="带队老师" min-width="130"><template slot-scope="scope"><span class="account-field">{{ scope.row.teacherName || '--' }}</span></template></el-table-column>
              <el-table-column v-for="field in customAnnouncementFields" :key="field.fieldKey" :prop="`customFields.${field.fieldKey}`" :label="field.fieldName" min-width="130"><template slot-scope="scope"><span class="account-field">{{ scope.row.customFields && scope.row.customFields[field.fieldKey] ? scope.row.customFields[field.fieldKey] : '--' }}</span></template></el-table-column>
              <el-table-column label="状态" width="160">
                <template slot-scope="scope"><div class="account-status"><el-switch :value="scope.row.enabled" :disabled="isProtectedAdmin(scope.row) || userTogglingId !== null" active-color="#2f9d68" inactive-color="#a7b0ba" :aria-label="`${scope.row.userName}账号状态`" @change="toggleUser(scope.row, $event)" /><span :class="['account-status-label', { 'is-enabled': scope.row.enabled }]">{{ userTogglingId === scope.row.userId ? '更新中' : (scope.row.enabled ? '已启用' : '已停用') }}</span></div></template>
              </el-table-column>
              <el-table-column label="操作" width="100" align="right">
                <template slot-scope="scope"><el-tooltip v-if="isProtectedAdmin(scope.row)" content="请通过顶部“修改密码”维护" placement="top"><span class="account-protected"><i class="el-icon-lock" /> 已保护</span></el-tooltip><el-tooltip v-else content="编辑账号" placement="top"><el-button class="table-action" icon="el-icon-edit-outline" circle aria-label="编辑账号" @click="openUserDialog(scope.row)" /></el-tooltip></template>
              </el-table-column>
            </el-table>
          </section>
        </el-tab-pane>

        <el-tab-pane v-if="false" name="training">
          <span slot="label" class="admin-tab-label" title="比赛设备"><i class="el-icon-monitor" /><span>比赛设备</span></span>
          <section class="workspace-page training-panel">
            <header class="workspace-heading"><div><h1>比赛设备</h1><p>查看比赛服务器、节点与端口运行状态</p></div><el-button v-if="canManageCompetitionDevices" type="primary" icon="el-icon-plus" @click="newServer">新增服务器</el-button></header>
            <div class="summary-band" aria-label="训练环境概览">
              <div class="summary-item"><span class="summary-icon"><i class="el-icon-monitor" /></span><div><small>训练服务器</small><strong>{{ servers.length }}</strong></div></div>
              <div class="summary-item"><span class="summary-icon"><i class="el-icon-connection" /></span><div><small>训练节点</small><strong>{{ trainingNodeCount }}</strong></div></div>
              <div class="summary-item"><span class="summary-icon summary-icon-success"><i class="el-icon-user" /></span><div><small>已分配端口</small><strong>{{ occupiedPortCount }} / {{ totalPortCount }}</strong></div></div>
            </div>
            <div v-if="canManageCompetitionDevices" class="assignment-bar">
              <div class="assignment-heading"><span class="panel-icon"><i class="el-icon-connection" /></span><div><strong>分配端口</strong><small>每个账号只能分配三个端口</small></div></div>
              <div class="assignment-controls">
                <el-select v-model="assignment.userId" placeholder="选择账号" filterable><el-option v-for="user in participantUsers" :key="user.userId" :label="user.userName" :value="user.userId" /></el-select>
                <el-select v-model="assignment.trainingNodeId" placeholder="选择端口组" filterable><el-option v-for="node in allNodes" :key="node.trainingNodeId" :label="nodeLabel(node)" :value="node.trainingNodeId" /></el-select>
                <el-button type="primary" :loading="assignmentSaving" :disabled="!assignmentReady" @click="assign">分配端口</el-button>
              </div>
            </div>
            <div v-if="servers.length" class="server-list">
              <section v-for="server in servers" :key="server.trainingServerId" class="server-panel">
                <header class="server-heading">
                  <button type="button" class="server-toggle" :aria-expanded="String(isServerExpanded(server))" @click="toggleServer(server)"><i :class="isServerExpanded(server) ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" /><span :class="['server-status-dot', serverRuntimeClass(server)]" /><strong>{{ server.serverName }}</strong><span class="server-state">{{ serverRuntimeLabel(server) }}</span><span class="server-node-count">{{ (server.nodes || []).length }} 个节点</span></button>
                  <div v-if="canManageCompetitionDevices" class="server-heading-actions"><el-tooltip content="编辑服务器" placement="top"><el-button class="table-action" icon="el-icon-edit-outline" circle aria-label="编辑服务器" @click="editServer(server)" /></el-tooltip><el-tooltip content="删除服务器" placement="top"><el-button class="table-action is-danger" icon="el-icon-delete" circle :loading="serverDeletingId === server.trainingServerId" :disabled="serverDeletingId !== null" aria-label="删除服务器" @click="deleteServer(server)" /></el-tooltip></div>
                </header>
                <el-table v-if="isServerExpanded(server)" :data="server.nodes || []" empty-text="暂无训练节点" class="admin-table server-table">
                  <el-table-column label="节点" width="96"><template slot-scope="scope"><strong>节点 {{ scope.row.nodeNo }}</strong></template></el-table-column>
                  <el-table-column prop="host" label="节点 IP" min-width="150"><template slot-scope="scope"><span class="node-host">{{ scope.row.host || '未配置' }}</span></template></el-table-column>
                  <el-table-column label="账号" min-width="130"><template slot-scope="scope"><span class="node-account">{{ nodeAccountLabel(scope.row) }}</span></template></el-table-column>
                  <el-table-column v-for="port in trainingPortTypes" :key="port.slotNo" :label="port.label" min-width="168">
                    <template slot-scope="scope">
                      <div :class="['port-state', { 'is-occupied': portAssignment(scope.row, port.slotNo).userId }]">
                        <div class="port-copy"><strong>{{ serverPort(server, scope.row, port.field) || '未配置' }}</strong><span>{{ portAssignment(scope.row, port.slotNo).userName || '空闲' }}</span></div>
                        <el-tooltip v-if="canManageCompetitionDevices && portAssignment(scope.row, port.slotNo).userId" content="取消分配" placement="top"><el-button type="text" icon="el-icon-close" :loading="unassigningUserId === portAssignment(scope.row, port.slotNo).userId" aria-label="取消分配" @click="unassign(portAssignment(scope.row, port.slotNo).userId)" /></el-tooltip>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </div>
            <div v-else class="admin-empty"><i class="el-icon-monitor" /><strong>暂无训练服务器</strong></div>
          </section>
        </el-tab-pane>

        <el-tab-pane v-if="false" name="settings">
          <span slot="label" class="admin-tab-label" title="平台设置"><i class="el-icon-setting" /><span>平台设置</span></span>
          <section class="workspace-page settings-panel">
            <header class="workspace-heading"><div><h1>平台设置</h1><p>平台基础信息</p></div></header>
            <section class="admin-panel settings-card">
              <el-form ref="platformForm" :model="platformForm" :rules="platformRules" label-position="top" class="settings-form">
                <el-form-item label="平台名称" prop="platformName">
                  <el-input
                    v-model="platformForm.platformName"
                    maxlength="30"
                    show-word-limit
                    placeholder="请输入平台名称"
                    @input="platformEditing = true"
                  />
                </el-form-item>
                <el-form-item label="主题色">
                  <el-color-picker v-model="platformForm.themeColor" @change="platformEditing = true" />
                </el-form-item>
                <el-form-item label="登录页顶部名称"><el-input v-model="platformForm.loginBrandName" maxlength="60" show-word-limit @input="platformEditing = true" /></el-form-item>
                <el-form-item label="登录页主标题"><el-input v-model="platformForm.loginTitle" maxlength="60" show-word-limit @input="platformEditing = true" /></el-form-item>
                <el-form-item label="登录页说明文字"><el-input v-model="platformForm.loginDescription" type="textarea" :rows="3" maxlength="300" show-word-limit @input="platformEditing = true" /></el-form-item>
                <el-form-item label="登录页版权文字"><el-input v-model="platformForm.loginCopyright" maxlength="100" show-word-limit @input="platformEditing = true" /></el-form-item>
                <el-form-item label="登录页面背景图片">
                  <el-upload action="#" accept="image/jpeg,image/png,image/webp" :auto-upload="false" :show-file-list="false" :on-change="selectLoginBackground" :disabled="backgroundUploading">
                    <el-button icon="el-icon-picture-outline" :loading="backgroundUploading">选择本地图片</el-button>
                  </el-upload>
                  <div v-if="platformForm.loginBackgroundUrl" class="background-preview"><img :src="platformForm.loginBackgroundUrl" alt="登录背景预览" /></div>
                  <el-button v-if="platformForm.loginBackgroundUrl" type="text" icon="el-icon-delete" @click="removeLoginBackground">移除背景图片</el-button>
                </el-form-item>
                <div class="settings-actions">
                  <el-button icon="el-icon-refresh-left" :disabled="platformSaving || platformForm.themeColor === defaultThemeColor" @click="restoreDefaultTheme">恢复默认主题色</el-button>
                  <el-button type="primary" icon="el-icon-check" :loading="platformSaving" @click="savePlatformSettings">保存设置</el-button>
                </div>
              </el-form>
            </section>
          </section>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer title="比赛帮助" :visible.sync="competitionHelpDrawer" :size="competitionHelpDrawerSize" class="competition-help-drawer">
      <div class="help-editor-grid">
        <section class="help-editor-column"><h3>编辑内容</h3><el-input v-model="competitionHelpDraft" type="textarea" :rows="22" maxlength="100000" placeholder="输入比赛帮助内容，换行会在用户端保留" /><el-button type="primary" icon="el-icon-check" :loading="competitionHelpSaving" @click="saveCompetitionHelp">保存帮助</el-button></section>
        <section class="help-preview-column"><h3>预览</h3><div class="help-preview-copy">{{ competitionHelpDraft || '尚未填写比赛帮助' }}</div></section>
      </div>
    </el-drawer>

    <el-dialog title="新增试卷" :visible.sync="paperDialog" width="min(420px, calc(100vw - 24px))" :close-on-click-modal="false" class="admin-dialog paper-dialog" @closed="resetPaperForm">
      <el-form ref="paperForm" :model="paperForm" :rules="paperRules" label-position="top" class="paper-create-form" @submit.native.prevent>
        <el-form-item label="卷名" prop="paperName">
          <el-input v-model="paperForm.paperName" maxlength="1" placeholder="例如：C" prefix-icon="el-icon-document" @input="normalizePaperName" @keyup.enter.native="createPaper" />
        </el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="paperDialog = false">取消</el-button><el-button type="primary" icon="el-icon-plus" :loading="paperCreating" @click="createPaper">创建试卷</el-button></span>
    </el-dialog>

    <el-dialog
      title="编辑题目"
      :visible.sync="subjectEditDialog"
      width="min(1180px, calc(100vw - 48px))"
      top="3vh"
      :close-on-click-modal="false"
      class="admin-dialog subject-edit-dialog"
      @closed="resetSubjectEditForm"
    >
      <div v-if="editingSubject.subjectId" class="subject-edit-dialog-grid">
        <section class="subject-edit-column">
          <h3 class="subject-edit-heading"><span><i class="el-icon-edit" />编辑题目</span></h3>
          <el-form ref="subjectForm" :model="editingSubject" :rules="subjectRules" label-position="top" class="subject-editor-form subject-edit-form" @submit.native.prevent>
            <div class="subject-form-grid">
              <el-form-item label="试卷" prop="testPaperType"><el-radio-group v-model="editingSubject.testPaperType" size="small" class="paper-selector"><el-radio-button v-for="paper in availablePapers" :key="paper" :label="paper">{{ paper }} 卷</el-radio-button></el-radio-group></el-form-item>
              <el-form-item label="题型" prop="subjectType"><el-select v-model="editingSubject.subjectType" placeholder="选择题型" @change="onSubjectTypeChange"><el-option v-for="type in subjectTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></el-form-item>
              <el-form-item label="所属模块" prop="modular"><el-select v-model="editingSubject.modular" placeholder="选择所属模块" @change="onSubjectModuleChange($event, editingSubject)"><el-option v-for="module in subjectModuleOptions" :key="module.value" :label="module.label" :value="module.value" /></el-select></el-form-item>
              <el-form-item label="题内顺序" prop="sortOrder"><el-input-number v-model="editingSubject.sortOrder" :min="0" :max="9999" controls-position="right" /></el-form-item>
              <el-form-item label="分值" prop="score"><el-input-number v-model="editingSubject.score" :min="0" :max="1000" controls-position="right" /></el-form-item>
            </div>
            <el-form-item label="题目标识"><el-input v-model="editingSubject.subjectIdentification" placeholder="选填" /></el-form-item>
            <el-form-item label="题干" prop="subjectName"><el-input v-model="editingSubject.subjectName" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
            <el-form-item label="作答说明"><el-input v-model="editingSubject.answering" type="textarea" :rows="2" placeholder="选填，可使用句号分隔步骤" /></el-form-item>
            <el-form-item label="截图要求"><el-input v-model="editingSubject.screenshotRequirement" type="textarea" :rows="2" maxlength="2000" placeholder="选填，例如：展示文件列表和运行结果" /></el-form-item>
            <el-form-item v-if="isChoiceType(editingSubject.subjectType)" label="选项" prop="options"><div v-for="(option, index) in editingSubject.options" :key="index" class="subject-option-row"><el-input v-model="editingSubject.options[index]" :placeholder="`选项 ${index + 1}`" /><el-button icon="el-icon-delete" circle title="删除选项" @click="removeSubjectOption(index)" /></div><el-button size="small" icon="el-icon-plus" @click="addSubjectOption">添加选项</el-button></el-form-item>
            <el-form-item v-if="editingSubject.subjectType === 'true_false'" label="固定选项"><el-tag>正确</el-tag><el-tag type="info">错误</el-tag></el-form-item>
            <el-form-item v-if="isObjectiveType(editingSubject.subjectType)" label="标准答案" required>
              <el-radio-group v-if="editingSubject.subjectType === 'single_choice'" v-model="editingSubject.correctAnswer"><el-radio v-for="(option, index) in editingSubject.options" :key="`correct-${index}`" :label="option" :disabled="!option.trim()">{{ option || '未填写选项' }}</el-radio></el-radio-group>
              <el-checkbox-group v-else-if="editingSubject.subjectType === 'multiple_choice'" v-model="editingSubject.correctAnswer"><el-checkbox v-for="(option, index) in editingSubject.options" :key="`correct-${index}`" :label="option" :disabled="!option.trim()">{{ option || '未填写选项' }}</el-checkbox></el-checkbox-group>
              <el-radio-group v-else-if="editingSubject.subjectType === 'true_false'" v-model="editingSubject.correctAnswer"><el-radio label="正确">正确</el-radio><el-radio label="错误">错误</el-radio></el-radio-group>
              <el-input v-else v-model="editingSubject.correctAnswer" maxlength="5000" placeholder="输入填空题标准答案" />
            </el-form-item>
            <el-form-item label="是否使用答题环境"><el-radio-group v-model="editingSubject.useEnvironment" @change="onUseEnvironmentChange"><el-radio :label="true">是</el-radio><el-radio :label="false">否</el-radio></el-radio-group></el-form-item>
            <el-form-item v-if="editingSubject.useEnvironment" label="选择环境" prop="point"><el-select v-model="editingSubject.point" placeholder="选择答题环境"><el-option label="代码环境" value="code" /><el-option label="标注环境" value="cvat" /><el-option label="推理环境" value="t100" /></el-select></el-form-item>
          </el-form>
        </section>
        <section class="subject-edit-column subject-edit-preview">
          <h3 class="subject-edit-heading"><span><i class="el-icon-view" />实时预览</span><el-tag size="small" type="info">{{ editingSubject.testPaperType || subjectPaper }} 卷</el-tag></h3>
          <div class="subject-preview-paper"><div class="subject-preview-module">模块 {{ editingSubject.modular || '-' }} <strong>{{ editingSubject.modularName || '未命名模块' }}</strong></div><article class="subject-preview-card"><div class="subject-preview-meta"><b>{{ editingSubject.sortOrder || 1 }}</b><el-tag size="mini" :type="subjectTypeTag(editingSubject.subjectType)">{{ subjectTypeLabel(editingSubject.subjectType) }}</el-tag><span>{{ Number(editingSubject.score || 0) }} 分</span></div><h3>{{ editingSubject.subjectName || '请输入题干' }}</h3><ul v-if="editingSubject.options && editingSubject.options.length" class="subject-preview-options"><li v-for="(option, index) in editingSubject.options" :key="index">{{ option || `选项 ${index + 1}` }}</li></ul><div v-if="isObjectiveType(editingSubject.subjectType)" class="subject-preview-note"><strong>标准答案</strong><span>{{ standardAnswerText(editingSubject) || '尚未配置' }}</span></div><div v-if="editingSubject.answering" class="subject-preview-note"><strong>作答说明</strong><span>{{ editingSubject.answering }}</span></div><div v-if="editingSubject.screenshotRequirement" class="subject-preview-note"><strong>截图要求</strong><span>{{ editingSubject.screenshotRequirement }}</span></div><div v-if="editingSubject.useEnvironment && editingSubject.point" class="subject-preview-environment"><i class="el-icon-monitor" /> 使用环境：{{ environmentLabel(editingSubject.point) }}</div></article></div>
        </section>
      </div>
      <span slot="footer"><el-button :disabled="subjectSaving" @click="subjectEditDialog = false">取消</el-button><el-button type="primary" icon="el-icon-check" :loading="subjectSaving" @click="saveSubject">保存题目</el-button></span>
    </el-dialog>

    <el-dialog
      title="新增题目"
      :visible.sync="subjectDialog"
      width="min(820px, calc(100vw - 24px))"
      top="4vh"
      :close-on-click-modal="false"
      class="admin-dialog subject-create-dialog"
      @closed="resetSubjectCreateForm"
    >
      <el-form ref="subjectCreateForm" :model="creatingSubject" :rules="subjectRules" label-position="top" class="subject-editor-form subject-create-form" @submit.native.prevent>
        <div class="subject-form-grid">
          <el-form-item label="试卷" prop="testPaperType"><el-radio-group v-model="creatingSubject.testPaperType" size="small" class="paper-selector"><el-radio-button v-for="paper in availablePapers" :key="paper" :label="paper">{{ paper }} 卷</el-radio-button></el-radio-group></el-form-item>
          <el-form-item label="题型" prop="subjectType"><el-select v-model="creatingSubject.subjectType" placeholder="选择题型" @change="onSubjectTypeChange($event, creatingSubject)"><el-option v-for="type in subjectTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></el-form-item>
          <el-form-item label="所属模块" prop="modular"><el-select v-model="creatingSubject.modular" placeholder="选择所属模块" @change="onSubjectModuleChange($event, creatingSubject)"><el-option v-for="module in subjectModuleOptions" :key="module.value" :label="module.label" :value="module.value" /></el-select></el-form-item>
          <el-form-item label="题内顺序" prop="sortOrder"><el-input-number v-model="creatingSubject.sortOrder" :min="0" :max="9999" controls-position="right" /></el-form-item>
          <el-form-item label="分值" prop="score"><el-input-number v-model="creatingSubject.score" :min="0" :max="1000" controls-position="right" /></el-form-item>
        </div>
        <el-form-item label="题目标识"><el-input v-model="creatingSubject.subjectIdentification" placeholder="选填" /></el-form-item>
        <el-form-item label="题干" prop="subjectName"><el-input v-model="creatingSubject.subjectName" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item>
        <el-form-item label="作答说明"><el-input v-model="creatingSubject.answering" type="textarea" :rows="2" placeholder="选填，可使用句号分隔步骤" /></el-form-item>
        <el-form-item label="截图要求"><el-input v-model="creatingSubject.screenshotRequirement" type="textarea" :rows="2" maxlength="2000" placeholder="选填，例如：展示文件列表和运行结果" /></el-form-item>
        <el-form-item v-if="isChoiceType(creatingSubject.subjectType)" label="选项" prop="options"><div v-for="(option, index) in creatingSubject.options" :key="index" class="subject-option-row"><el-input v-model="creatingSubject.options[index]" :placeholder="`选项 ${index + 1}`" /><el-button icon="el-icon-delete" circle title="删除选项" @click="removeSubjectOption(index, creatingSubject)" /></div><el-button size="small" icon="el-icon-plus" @click="addSubjectOption(creatingSubject)">添加选项</el-button></el-form-item>
        <el-form-item v-if="creatingSubject.subjectType === 'true_false'" label="固定选项"><el-tag>正确</el-tag><el-tag type="info">错误</el-tag></el-form-item>
        <el-form-item v-if="isObjectiveType(creatingSubject.subjectType)" label="标准答案" required>
          <el-radio-group v-if="creatingSubject.subjectType === 'single_choice'" v-model="creatingSubject.correctAnswer"><el-radio v-for="(option, index) in creatingSubject.options" :key="`correct-${index}`" :label="option" :disabled="!option.trim()">{{ option || '未填写选项' }}</el-radio></el-radio-group>
          <el-checkbox-group v-else-if="creatingSubject.subjectType === 'multiple_choice'" v-model="creatingSubject.correctAnswer"><el-checkbox v-for="(option, index) in creatingSubject.options" :key="`correct-${index}`" :label="option" :disabled="!option.trim()">{{ option || '未填写选项' }}</el-checkbox></el-checkbox-group>
          <el-radio-group v-else-if="creatingSubject.subjectType === 'true_false'" v-model="creatingSubject.correctAnswer"><el-radio label="正确">正确</el-radio><el-radio label="错误">错误</el-radio></el-radio-group>
          <el-input v-else v-model="creatingSubject.correctAnswer" maxlength="5000" placeholder="输入填空题标准答案" />
        </el-form-item>
        <el-form-item label="是否使用答题环境"><el-radio-group v-model="creatingSubject.useEnvironment" @change="onUseEnvironmentChange($event, creatingSubject)"><el-radio :label="true">是</el-radio><el-radio :label="false">否</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="creatingSubject.useEnvironment" label="选择环境" prop="point"><el-select v-model="creatingSubject.point" placeholder="选择答题环境"><el-option label="代码环境" value="code" /><el-option label="标注环境" value="cvat" /><el-option label="推理环境" value="t100" /></el-select></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="subjectDialog = false">取消</el-button><el-button type="primary" icon="el-icon-plus" :loading="subjectCreating" @click="createSubject">创建题目</el-button></span>
    </el-dialog>

    <el-dialog
      title="新增比赛账号"
      :visible.sync="userBatchDialog"
      width="min(420px, calc(100vw - 24px))"
      :close-on-click-modal="false"
      class="admin-dialog account-dialog account-batch-dialog"
    >
      <el-form ref="userBatchForm" :model="userBatchForm" :rules="userBatchRules" label-position="top" class="account-form" @submit.native.prevent>
        <el-form-item label="新增数量" prop="count">
          <el-input-number v-model="userBatchForm.count" :min="1" :max="500" :step="1" :precision="0" controls-position="right" @keyup.enter.native="createUsersBatch" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="userBatchDialog = false">取消</el-button>
        <el-button type="primary" :loading="userBatchSaving" @click="createUsersBatch">创建账号</el-button>
      </span>
    </el-dialog>

    <el-dialog
      :title="editingUser && editingUser.userId ? '编辑比赛账号' : '新增比赛账号'"
      :visible.sync="userDialog"
      width="min(480px, calc(100vw - 24px))"
      :close-on-click-modal="false"
      class="admin-dialog account-dialog"
    >
      <el-form ref="userForm" :model="editingUser" :rules="userRules" label-position="top" class="account-form">
        <el-form-item label="账号名称" prop="userName">
          <el-input v-model="editingUser.userName" maxlength="50" placeholder="请输入比赛账号" />
        </el-form-item>
        <el-form-item :label="editingUser.userId ? '重置密码' : '初始密码'" prop="password">
          <el-input
            v-model="editingUser.password"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editingUser.userId ? '留空则保持原密码' : '至少 4 个字符'"
          />
        </el-form-item>
        <div v-if="!editingUser.admin" class="account-profile-grid">
          <el-form-item label="学校名称"><el-input v-model="editingUser.schoolName" maxlength="100" placeholder="请输入学校名称" /></el-form-item>
          <el-form-item label="参赛选手"><el-input v-model="editingUser.contestantName" maxlength="100" placeholder="请输入参赛选手" /></el-form-item>
          <el-form-item label="带队老师"><el-input v-model="editingUser.teacherName" maxlength="100" placeholder="请输入带队老师" /></el-form-item>
          <el-form-item v-for="field in customAnnouncementFields" :key="field.fieldKey" :label="field.fieldName"><el-input v-model="editingUser.customFields[field.fieldKey]" maxlength="255" :placeholder="`请输入${field.fieldName}`" /></el-form-item>
        </div>
        <el-form-item label="账号状态">
          <div class="account-form-status">
            <el-switch v-model="editingUser.enabled" active-color="#2f9d68" inactive-color="#a7b0ba" />
            <span>{{ editingUser.enabled ? '启用' : '停用' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="管理员权限">
          <div class="account-form-status">
            <el-switch v-model="editingUser.admin" :disabled="isProtectedAdmin(editingUser)" active-color="#2f9d68" inactive-color="#a7b0ba" />
            <span>{{ editingUser.admin ? '可访问管理端' : '仅参赛功能' }}</span>
          </div>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="saveUser">保存账号</el-button>
      </span>
    </el-dialog>

    <el-dialog :title="announcementEntry.entryId ? '编辑公告条目' : '新增公告条目'" :visible.sync="announcementEntryDialog" width="min(460px, calc(100vw - 24px))" :close-on-click-modal="false" class="admin-dialog">
      <el-form label-position="top">
        <el-form-item v-for="field in announcementFields.filter(item => item.enabled && item.fieldKey !== 'sequence')" :key="field.fieldKey" :label="field.fieldName"><el-input v-model="announcementEntry.values[field.fieldKey]" maxlength="255" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="announcementEntryDialog = false">取消</el-button><el-button type="primary" :loading="announcementEntrySaving" @click="saveAnnouncementEntry">保存条目</el-button></span>
    </el-dialog>

    <el-dialog :title="editingAnnouncementField.fieldId ? '编辑公告字段' : '新增公告字段'" :visible.sync="announcementFieldDialog" width="min(420px, calc(100vw - 24px))" :close-on-click-modal="false" class="admin-dialog">
      <el-form ref="announcementFieldForm" :model="editingAnnouncementField" :rules="announcementFieldRules" label-position="top">
        <el-form-item label="字段名称" prop="fieldName"><el-input v-model="editingAnnouncementField.fieldName" maxlength="30" show-word-limit placeholder="例如：赛位号" /></el-form-item>
        <el-form-item v-if="editingAnnouncementField.fieldId" label="状态"><div class="account-form-status"><el-switch v-model="editingAnnouncementField.enabled" active-color="#76c7aa" inactive-color="#c6cdd2" /><span>{{ editingAnnouncementField.enabled ? '已启用' : '已关闭' }}</span></div></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="announcementFieldDialog = false">取消</el-button><el-button type="primary" :loading="announcementFieldSaving" @click="saveAnnouncementField">保存字段</el-button></span>
    </el-dialog>

    <el-dialog
      :title="editingServer && editingServer.trainingServerId ? '编辑训练服务器' : '新增训练服务器'"
      :visible.sync="serverDialog"
      width="min(720px, calc(100vw - 24px))"
      top="5vh"
      :close-on-click-modal="false"
      class="admin-dialog server-dialog"
    >
      <el-form v-if="editingServer" ref="serverForm" :model="editingServer" :rules="serverRules" label-position="top" class="server-form">
        <div class="server-form-grid">
          <el-form-item label="服务器名称" prop="serverName">
            <el-input v-model="editingServer.serverName" maxlength="50" placeholder="例如：训练服务器 1" />
          </el-form-item>
          <el-form-item label="运行状态">
            <div class="account-form-status">
              <el-switch v-model="editingServer.enabled" active-color="#2f9d68" inactive-color="#a7b0ba" />
              <span>{{ editingServer.enabled ? '启用' : '停用' }}</span>
            </div>
          </el-form-item>
        </div>
        <div class="server-port-grid">
          <el-form-item label="代码端口"><el-input-number v-model="editingServer.vscodeBasePort" :min="1" :max="65535" controls-position="right" /></el-form-item>
          <el-form-item label="标注端口"><el-input-number v-model="editingServer.cvatBasePort" :min="1" :max="65535" controls-position="right" /></el-form-item>
          <el-form-item label="推理端口"><el-input-number v-model="editingServer.t100BasePort" :min="1" :max="65535" controls-position="right" /></el-form-item>
        </div>
        <div class="node-form-heading"><strong>节点地址</strong><span>每台服务器固定配置 4 个训练节点</span></div>
        <div class="node-form-grid">
          <el-form-item v-for="node in editingServer.nodes" :key="node.nodeNo" :label="`节点 ${node.nodeNo}`">
            <el-input v-model="node.host" placeholder="例如：192.168.1.10" prefix-icon="el-icon-monitor" />
          </el-form-item>
        </div>
      </el-form>
      <span slot="footer">
        <el-button @click="serverDialog = false">取消</el-button>
        <el-button type="primary" :loading="serverSaving" @click="saveServer">保存服务器</el-button>
      </span>
    </el-dialog>

    <el-dialog title="修改管理员密码" :visible.sync="passwordDialog" width="min(440px, calc(100vw - 24px))" :close-on-click-modal="false" class="admin-dialog password-dialog">
      <el-form ref="passwordForm" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="当前密码" prop="currentPassword"><el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="passwordDialog = false">取消</el-button><el-button type="primary" @click="changePassword">保存</el-button></span>
    </el-dialog>
  </div>
</template>

<script>
import {
  adminCountdownApi,
  adminUsersApi,
  announcementFieldsApi,
  createAnnouncementFieldApi,
  updateAnnouncementFieldApi,
  competitionAnnouncementsApi,
  createCompetitionAnnouncementApi,
  updateCompetitionAnnouncementApi,
  deleteCompetitionAnnouncementApi,
  adminNoticeApi,
  updateAdminNoticeApi,
  createAdminUserApi,
  createAdminUsersBatchApi,
  clearAdminUsersApi,
  updateAdminUserApi,
  trainingServersApi,
  trainingServerHeartbeatApi,
  createTrainingServerApi,
  updateTrainingServerApi,
  deleteTrainingServerApi,
  assignTrainingApi,
  unassignTrainingApi,
  adminSubjectsApi,
  createAdminSubjectApi,
  updateAdminSubjectApi,
  deleteAdminSubjectApi,
  clearAdminSubjectAnswersApi,
  clearAdminSubjectsApi,
  competitionApi,
  selectCompetitionApi,
  createPaperApi,
  updatePlatformSettingsApi,
  uploadLoginBackgroundApi,
  updateCompetitionContentApi,
  updateCompetitionHelpApi,
  changePasswordApi,
  getClearTime,
  gradingExportStatusApi
} from '@/api/Match'
import { setUserInfo, hasRole } from '@/utils/auth'
import AdminGrading from '@/components/AdminGrading'
import {
  competitionContentTabs,
  createDefaultCompetitionContent,
  createDefaultCompetitionHelpItems,
  formatCompetitionHelpContent
} from '@/utils/competitionDefaults'

const DEFAULT_THEME_COLOR = '#386bdc'

export default {
  components: { AdminGrading },
  data () {
    return {
      activeTab: 'timer',
      rulesTab: 'rules',
      platformForm: { platformName: '', themeColor: DEFAULT_THEME_COLOR, loginBackgroundUrl: '', loginBrandName: '', loginTitle: '', loginDescription: '', loginCopyright: '' },
      defaultThemeColor: DEFAULT_THEME_COLOR,
      platformSaving: false,
      platformEditing: false,
      backgroundUploading: false,
      platformRules: {
        platformName: [
          {
            validator: (rule, value, callback) => {
              const name = String(value || '').trim()
              if (!name) callback(new Error('请输入平台名称'))
              else if (name.length > 30) callback(new Error('平台名称不能超过 30 个字符'))
              else callback()
            },
            trigger: 'blur'
          }
        ]
      },
      activePaper: '',
      paperDraft: '',
      availablePapers: [],
      paperResources: {},
      paperDialog: false,
      paperCreating: false,
      paperForm: { paperName: '' },
      paperRules: {
        paperName: [
          {
            validator: (rule, value, callback) => {
              if (!/^[A-Za-z]$/.test(String(value || '').trim())) callback(new Error('卷名只能是单个英文字母'))
              else callback()
            },
            trigger: 'blur'
          }
        ]
      },
      competitionContentKey: 'matchContent',
      competitionContentSaving: false,
      competitionContentTabs,
      competitionContent: createDefaultCompetitionContent(),
      competitionContentInitialized: false,
      competitionHelpDrawer: false,
      competitionHelpDrawerSize: window.innerWidth <= 900 ? '94%' : '72%',
      competitionHelpDraft: '',
      competitionHelpSaving: false,
      subjectPaper: 'A',
      subjectTypeFilter: '',
      subjectKeyword: '',
      subjects: [],
      subjectLoading: false,
      subjectSaving: false,
      subjectCreating: false,
      subjectAnswersClearing: false,
      subjectsClearing: false,
      subjectEditDialog: false,
      subjectDialog: false,
      editingSubject: {},
      creatingSubject: {},
      subjectTypes: [
        { value: 'single_choice', label: '单选题' },
        { value: 'multiple_choice', label: '多选题' },
        { value: 'true_false', label: '判断题' },
        { value: 'fill_blank', label: '填空题' },
        { value: 'practical', label: '实操题' }
      ],
      subjectModuleOptions: [
        { value: 'A', name: '工程环境准备', label: '模块 A 工程环境准备' },
        { value: 'B', name: '数据标注与增强', label: '模块 B 数据标注与增强' },
        { value: 'C', name: '模型搭建与训练', label: '模块 C 模型搭建与训练' },
        { value: 'D', name: '算法模型优化', label: '模块 D 算法模型优化' },
        { value: 'E', name: '算法模型应用部署', label: '模块 E 算法模型应用部署' }
      ],
      subjectRules: {
        testPaperType: [{ required: true, message: '请选择试卷', trigger: 'change' }],
        subjectType: [{ required: true, message: '请选择题型', trigger: 'change' }],
        modular: [{ required: true, message: '请选择所属模块', trigger: 'change' }],
        subjectName: [{ required: true, message: '请输入题干', trigger: 'blur' }]
      },
      countDown: {},
      countDownTimer: null,
      clockTimer: null,
      heartbeatTimer: null,
      heartbeatLoading: false,
      timerUpdating: false,
      clockNow: Date.now(),
      serverOffset: 0,
      scheduleForm: { startTime: null, preLoginMinutes: 30, durationMinutes: 240 },
      scheduleInitialized: false,
      scheduleSaving: false,
      customMinutes: 30,
      customEndTime: null,
      users: [],
      noticeContent: '',
      noticeLoading: false,
      noticeSaving: false,
      announcementFields: [],
      announcementEntries: [],
      announcementEntryDialog: false,
      announcementEntrySaving: false,
      announcementEntry: { entryId: null, values: {} },
      announcementFieldDialog: false,
      announcementFieldSaving: false,
      editingAnnouncementField: { fieldName: '', enabled: true },
      announcementFieldRules: {
        fieldName: [{ required: true, message: '请输入字段名称', trigger: 'blur' }]
      },
      userKeyword: '',
      userStatusFilter: 'all',
      userLoading: false,
      userSaving: false,
      userBatchSaving: false,
      userClearing: false,
      userTogglingId: null,
      userBatchDialog: false,
      userBatchForm: { count: 20 },
      userBatchRules: {
        count: [
          { required: true, message: '请输入新增数量', trigger: 'change' },
          {
            validator: (rule, value, callback) => {
              if (!Number.isInteger(value) || value < 1 || value > 500) callback(new Error('新增数量必须在 1 到 500 之间'))
              else callback()
            },
            trigger: 'change'
          }
        ]
      },
      userDialog: false,
      editingUser: { userName: '', password: '', enabled: true, admin: false, schoolName: '', contestantName: '', teacherName: '', customFields: {} },
      userRules: {
        userName: [
          {
            validator: (rule, value, callback) => {
              if (!value || !value.trim()) callback(new Error('请输入账号名称'))
              else callback()
            },
            trigger: 'blur'
          }
        ],
        password: [
          {
            validator: (rule, value, callback) => {
              const password = value || ''
              if (!this.editingUser.userId && !password) callback(new Error('请输入初始密码'))
              else if (password && password.length < 4) callback(new Error('密码至少需要 4 个字符'))
              else callback()
            },
            trigger: 'blur'
          }
        ]
      },
      servers: [],
      expandedServerIds: {},
      editingServer: null,
      serverDialog: false,
      serverSaving: false,
      serverDeletingId: null,
      serverRules: {
        serverName: [
          {
            validator: (rule, value, callback) => {
              if (!value || !value.trim()) callback(new Error('请输入服务器名称'))
              else callback()
            },
            trigger: 'blur'
          }
        ]
      },
      trainingPortTypes: [
        { slotNo: 1, label: '代码端口', field: 'vscodeBasePort' },
        { slotNo: 2, label: '标注端口', field: 'cvatBasePort' },
        { slotNo: 3, label: '推理端口', field: 't100BasePort' }
      ],
      assignment: { userId: null, trainingNodeId: null },
      assignmentSaving: false,
      unassigningUserId: null,
      passwordDialog: false,
      passwordForm: { currentPassword: '', newPassword: '' },
      passwordRules: {
        currentPassword: [
          { required: true, message: '请输入当前密码', trigger: 'blur' }
        ],
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          {
            validator: (rule, value, callback) => {
              if (!value || value.trim().length < 6) {
                callback(new Error('新密码至少需要 6 个字符'))
              } else if (value === this.passwordForm.currentPassword) {
                callback(new Error('新密码不能与当前密码相同'))
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
        ]
      }
    }
  },
  computed: {
    canManageCompetitionDevices () {
      return hasRole('SUPER_ADMIN')
    },
    mustChangePassword () {
      return this.$store.state.Match.mustChangePassword
    },
    platformName () {
      return this.$store.state.Match.platformName || '数据杯管理台'
    },
    countDownStatusLabel () {
      return {
        RUNNING: '进行中',
        SCHEDULED: '赛前开放',
        WAITING_LOGIN: '等待开放登录',
        PAUSED: '已暂停',
        FINISHED: '已结束',
        ENDED: '已结束',
        NOT_STARTED: '未开始'
      }[this.countDown.status] || '未设置'
    },
    competitionFinished () {
      return ['FINISHED', 'ENDED'].includes(this.countDown.status)
    },
    preStartRemainingLabel () {
      if (!this.countDown.scheduledStartTime || !['WAITING_LOGIN', 'SCHEDULED'].includes(this.countDown.status)) return '--:--:--'
      const remaining = Math.max(0, Math.ceil((Number(this.countDown.scheduledStartTime) - (this.clockNow + this.serverOffset)) / 1000))
      return this.formatRemaining(remaining)
    },
    scheduleLoginOpenLabel () {
      const start = Number(this.scheduleForm.startTime || 0)
      if (!start) return '--:--:--'
      return this.formatScheduleTime(start - Number(this.scheduleForm.preLoginMinutes || 0) * 60000)
    },
    scheduleStartLabel () {
      const start = Number(this.scheduleForm.startTime || 0)
      return start ? this.formatScheduleTime(start) : '--:--:--'
    },
    scheduleEndLabel () {
      const start = Number(this.scheduleForm.startTime || 0)
      if (!start) return '--:--:--'
      return this.formatScheduleTime(start + Number(this.scheduleForm.durationMinutes || 0) * 60000)
    },
    participantUsers () {
      return this.users.filter(user => !this.isAdminUser(user) && user.enabled)
    },
    customAnnouncementFields () {
      return this.announcementFields.filter(field => field.fieldType === 'CUSTOM')
    },
    participantUserCount () {
      return this.users.filter(user => String(user.userName || '').toLowerCase() !== 'admin').length
    },
    enabledUserCount () {
      return this.users.filter(user => Boolean(user.enabled)).length
    },
    disabledUserCount () {
      return this.users.length - this.enabledUserCount
    },
    filteredUsers () {
      const keyword = this.userKeyword.trim().toLowerCase()
      return this.users.filter(user => {
        const searchable = [user.userName, user.userId, user.schoolName, user.contestantName, user.teacherName]
          .concat(Object.values(user.customFields || {}))
          .map(value => String(value || '').toLowerCase())
        const matchesKeyword = !keyword || searchable.some(value => value.includes(keyword))
        const matchesStatus = this.userStatusFilter === 'all' ||
          (this.userStatusFilter === 'enabled' && Boolean(user.enabled)) ||
          (this.userStatusFilter === 'disabled' && !user.enabled)
        return matchesKeyword && matchesStatus
      })
    },
    userEmptyText () {
      return this.users.length ? '没有符合条件的账号' : '暂无比赛账号'
    },
    allNodes () {
      return this.servers.reduce((nodes, server) => nodes.concat(server.nodes || []), [])
    },
    trainingNodeCount () {
      return this.allNodes.length
    },
    totalPortCount () {
      return this.trainingNodeCount * this.trainingPortTypes.length
    },
    occupiedPortCount () {
      return this.allNodes.reduce((count, node) => {
        return count + (node.slots || []).filter(slot => slot && slot.userId).length
      }, 0)
    },
    assignmentReady () {
      return Boolean(this.assignment.userId && this.assignment.trainingNodeId)
    },
    subjectModules () {
      const modules = []
      const byKey = {}
      this.subjects.forEach(subject => {
        const key = subject.modular || '-'
        if (!byKey[key]) {
          byKey[key] = { key, title: subject.modularName || '未命名模块', subjects: [] }
          modules.push(byKey[key])
        }
        byKey[key].subjects.push(subject)
      })
      return modules
    },
    activeCompetitionSection () {
      return this.competitionContent[this.competitionContentKey]
    },
    activeCompetitionTabLabel () {
      const tab = this.competitionContentTabs.find(item => item.key === this.competitionContentKey)
      return tab ? tab.label : '赛规赛程'
    }
  },
  watch: {
    '$route.query.tab' (value) {
      const tab = this.normalizeAdminTab(value)
      if (tab !== this.activeTab) this.activeTab = tab
    },
    activeTab (value) {
      if (value === 'subjects') this.loadSubjects()
      if (value === 'grading' && this.$refs.adminGrading) this.$refs.adminGrading.reload()
      if (value === 'training') this.startTrainingHeartbeat()
      else this.stopTrainingHeartbeat()
      if (value === 'settings' && !this.platformEditing) {
        this.syncPlatformForm()
      }
    },
    subjectPaper () {
      if (this.activeTab === 'subjects') this.loadSubjects()
    },
    subjectTypeFilter () {
      if (this.activeTab === 'subjects') this.loadSubjects()
    }
  },
  created () {
    this.activeTab = this.normalizeAdminTab(this.$route.query.tab)
  },
  async mounted () {
    await this.loadPublicState()
    this.subjectPaper = this.activePaper || this.availablePapers[0] || ''
    if (!this.mustChangePassword) {
      this.countDownTimer = setInterval(this.loadPublicState, 10000)
      this.clockTimer = setInterval(() => { this.clockNow = Date.now() }, 1000)
      await this.loadAdminState()
    } else {
      this.$nextTick(() => { this.passwordDialog = true })
    }
  },
  beforeDestroy () {
    if (this.countDownTimer) clearInterval(this.countDownTimer)
    if (this.clockTimer) clearInterval(this.clockTimer)
    this.stopTrainingHeartbeat()
  },
  methods: {
    normalizeAdminTab (value) {
      return ['timer', 'rules', 'subjects', 'grading', 'users'].includes(String(value || ''))
        ? String(value)
        : 'timer'
    },
    async loadPublicState () {
      const [competition, countdown] = await Promise.all([competitionApi(), getClearTime()])
      if (competition.code === 200) {
        this.activePaper = competition.data.activePaper
        this.paperDraft = this.activePaper
        this.availablePapers = Array.isArray(competition.data.availablePapers)
          ? competition.data.availablePapers.map(paper => String(paper || '').trim().toUpperCase()).filter(paper => /^[A-Z]$/.test(paper))
          : []
        this.paperResources = competition.data.paperResources || {}
        if (!this.availablePapers.includes(this.subjectPaper)) {
          this.subjectPaper = this.activePaper || this.availablePapers[0] || ''
        }
        const savedContent = competition.data.competitionContent || {}
        if (!this.competitionHelpDrawer) {
          const savedHelp = String(competition.data.competitionHelpContent || '').trim()
          this.competitionHelpDraft = savedHelp || this.defaultCompetitionHelpContent()
        }
        if (!this.competitionContentInitialized) {
          this.competitionContentTabs.forEach(item => {
            const saved = savedContent[item.key]
            if (saved && String(saved.body || '').trim()) {
              this.$set(this.competitionContent, item.key, {
                title: String(saved.title || item.label),
                body: String(saved.body || '')
              })
            }
          })
          this.competitionContentInitialized = true
        }
        this.$store.commit('Match/SET_ACTIVE_PAPER', this.activePaper)
        this.$store.commit('Match/SET_PLATFORM_SETTINGS', competition.data)
        if (!this.platformEditing) {
          this.syncPlatformForm()
        }
      }
      if (countdown.code === 200) {
        this.countDown = countdown.data
        this.serverOffset = Number(countdown.data.serverTime || Date.now()) - Date.now()
        this.clockNow = Date.now()
        if (!this.scheduleInitialized) {
          this.scheduleForm = {
            startTime: countdown.data.scheduledStartTime || null,
            preLoginMinutes: Number(countdown.data.preLoginMinutes || 30),
            durationMinutes: Number(countdown.data.durationMinutes || 240)
          }
          this.scheduleInitialized = true
        }
      }
    },
    async loadAdminState () {
      await Promise.all([this.loadNotice(), this.loadAnnouncementFields(), this.loadAnnouncements(), this.loadUsers(), this.loadServers()])
    },
    async loadNotice () {
      this.noticeLoading = true
      try {
        const result = await adminNoticeApi()
        if (result.code === 200) this.noticeContent = String((result.data && result.data.noticeContent) || '')
      } catch (error) {
        // The shared interceptor reports the request failure; keep the current draft visible.
      } finally {
        this.noticeLoading = false
      }
    },
    async saveNotice () {
      this.noticeSaving = true
      try {
        const result = await updateAdminNoticeApi({ noticeContent: this.noticeContent })
        if (result.code === 200) {
          this.noticeContent = String((result.data && result.data.noticeContent) || '')
          this.$message.success('注意事项已保存')
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.noticeSaving = false
      }
    },
    async loadAnnouncementFields () {
      const result = await announcementFieldsApi()
      if (result.code === 200) this.announcementFields = result.data || []
    },
    async loadAnnouncements () {
      const result = await competitionAnnouncementsApi()
      if (result.code === 200) this.announcementEntries = result.data || []
    },
    editAnnouncementEntry (row) {
      const values = {}
      this.announcementFields.filter(field => field.enabled && field.fieldKey !== 'sequence')
        .forEach(field => { values[field.fieldKey] = row && row[field.fieldKey] ? row[field.fieldKey] : '' })
      this.announcementEntry = { entryId: row ? row.entryId : null, values }
      this.announcementEntryDialog = true
    },
    async saveAnnouncementEntry () {
      this.announcementEntrySaving = true
      try {
        const result = this.announcementEntry.entryId
          ? await updateCompetitionAnnouncementApi(this.announcementEntry.entryId, this.announcementEntry.values)
          : await createCompetitionAnnouncementApi(this.announcementEntry.values)
        if (result.code === 200) {
          this.announcementEntryDialog = false
          await this.loadAnnouncements()
          this.$message.success('公告条目已保存')
        }
      } finally { this.announcementEntrySaving = false }
    },
    async removeAnnouncementEntry (row) {
      try { await this.$confirm('确定删除该公告条目吗？', '删除公告条目', { type: 'warning' }) } catch (error) { return }
      const result = await deleteCompetitionAnnouncementApi(row.entryId)
      if (result.code === 200) {
        await this.loadAnnouncements()
        this.$message.success('公告条目已删除')
      }
    },
    async loadUsers () {
      this.userLoading = true
      try {
        const result = await adminUsersApi()
        if (result.code === 200) this.users = result.data || []
      } catch (error) {
        // The shared interceptor reports the request failure; keep the current list visible.
      } finally {
        this.userLoading = false
      }
    },
    async loadServers () {
      const result = await trainingServersApi()
      if (result.code === 200) this.servers = result.data || []
    },
    startTrainingHeartbeat () {
      if (this.heartbeatTimer || this.mustChangePassword) return
      this.refreshTrainingHeartbeat()
      this.heartbeatTimer = setInterval(this.refreshTrainingHeartbeat, 5000)
    },
    stopTrainingHeartbeat () {
      if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    },
    async refreshTrainingHeartbeat () {
      if (this.heartbeatLoading || this.activeTab !== 'training') return
      this.heartbeatLoading = true
      try {
        const result = await trainingServerHeartbeatApi()
        if (result.code === 200) {
          const heartbeats = result.data || []
          heartbeats.forEach(heartbeat => {
            const server = this.servers.find(item => item.trainingServerId === heartbeat.trainingServerId)
            if (!server) return
            this.$set(server, 'heartbeatStatus', heartbeat.heartbeatStatus)
            this.$set(server, 'lastHeartbeatAt', heartbeat.lastHeartbeatAt)
          })
        }
      } catch (error) {
        // Keep the last heartbeat visible until the next probe.
      } finally {
        this.heartbeatLoading = false
      }
    },
    serverRuntimeLabel (server) {
      if (!server.enabled || server.heartbeatStatus === 'DISABLED') return '已停用'
      if (server.heartbeatStatus === 'ONLINE') return '运行中'
      if (server.heartbeatStatus === 'OFFLINE') return '离线'
      return '检测中'
    },
    serverRuntimeClass (server) {
      if (!server.enabled || server.heartbeatStatus === 'DISABLED') return 'is-disabled'
      if (server.heartbeatStatus === 'OFFLINE') return 'is-offline'
      if (server.heartbeatStatus === 'ONLINE') return 'is-online'
      return 'is-checking'
    },
    isServerExpanded (server) {
      return Boolean(this.expandedServerIds[server.trainingServerId])
    },
    toggleServer (server) {
      const id = server.trainingServerId
      this.$set(this.expandedServerIds, id, !this.isServerExpanded(server))
    },
    formatRemaining (seconds) {
      let value = Math.max(0, Number(seconds || 0))
      const hours = Math.floor(value / 3600)
      value %= 3600
      const minutes = Math.floor(value / 60)
      const secs = value % 60
      return [hours, minutes, secs].map(item => String(item).padStart(2, '0')).join(':')
    },
    formatScheduleTime (timestamp) {
      const date = new Date(Number(timestamp))
      if (Number.isNaN(date.getTime())) return '--:--:--'
      return [date.getHours(), date.getMinutes(), date.getSeconds()].map(item => String(item).padStart(2, '0')).join(':')
    },
    paperReady (paper) {
      return Boolean(this.paperResources[paper] && this.paperResources[paper].ready)
    },
    paperSelectable (paper) {
      return Number((this.paperResources[paper] && this.paperResources[paper].subjectCount) || 0) > 0
    },
    paperResourceLabel (paper) {
      const resource = this.paperResources[paper]
      if (!resource) return '检查中'
      if (resource.ready) return `${resource.subjectCount} 道题 / ${resource.datasetFileCount} 个文件`
      const missing = []
      if (!resource.subjectCount) missing.push('暂无题目')
      if (!resource.datasetFileCount) missing.push('数据文件缺失')
      if (!resource.annotationsPresent) missing.push('评分标注缺失')
      else if (!resource.annotationsValid) missing.push('评分标注内容无效')
      return missing.join('、') || '资源不可用'
    },
    announcementValue (row, key) {
      const value = row[key]
      return value === null || value === undefined || String(value).trim() === '' ? '--' : value
    },
    openPaperDialog () {
      this.paperForm.paperName = ''
      this.paperDialog = true
      this.$nextTick(() => {
        if (this.$refs.paperForm) this.$refs.paperForm.clearValidate()
      })
    },
    resetPaperForm () {
      this.paperForm.paperName = ''
      if (this.$refs.paperForm) this.$refs.paperForm.clearValidate()
    },
    normalizePaperName (value) {
      this.paperForm.paperName = String(value || '').toUpperCase()
    },
    async createPaper () {
      const valid = await new Promise(resolve => this.$refs.paperForm.validate(resolve))
      if (!valid) return
      this.paperCreating = true
      try {
        const result = await createPaperApi(this.paperForm.paperName)
        if (result.code === 200) {
          const paper = String(result.data.paperType || this.paperForm.paperName).toUpperCase()
          this.paperDialog = false
          await this.loadPublicState()
          this.subjectPaper = paper
          this.$message.success(`已创建 ${paper} 卷`)
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.paperCreating = false
      }
    },
    async loadSubjects () {
      this.subjectLoading = true
      try {
        const result = await adminSubjectsApi({
          paperType: this.subjectPaper,
          subjectType: this.subjectTypeFilter || undefined,
          keyword: this.subjectKeyword.trim() || undefined
        })
        if (result.code === 200) {
          const selectedId = this.editingSubject && this.editingSubject.subjectId
          this.subjects = result.data || []
          const selected = selectedId ? this.subjects.find(subject => subject.subjectId === selectedId) : null
          if (selected) this.openSubjectDialog(selected)
          else this.editingSubject = {}
        }
      } catch (error) {
        // The shared interceptor displays the backend error; keep the current list intact.
      } finally {
        this.subjectLoading = false
      }
    },
    emptySubject () {
      return {
        testPaperType: this.subjectPaper,
        subjectType: 'single_choice',
        subjectName: '',
        score: 0,
        answering: '',
        screenshotRequirement: '',
        modular: '',
        modularName: '',
        point: '',
        subjectIdentification: '',
        sortOrder: 0,
        useEnvironment: false,
        options: ['', ''],
        correctAnswer: ''
      }
    },
    openSubjectDialog (subject) {
      if (!subject) return
      this.editingSubject = Object.assign({}, subject, { options: (subject.options || []).slice(), useEnvironment: Boolean(subject.point) })
      if (subject.subjectType === 'multiple_choice') {
        try { this.editingSubject.correctAnswer = JSON.parse(subject.correctAnswer || '[]') } catch (error) { this.editingSubject.correctAnswer = [] }
      } else {
        this.editingSubject.correctAnswer = subject.correctAnswer || ''
      }
      const module = this.subjectModuleOptions.find(item => item.value === String(subject.modular || '').trim().toUpperCase())
      this.editingSubject.modular = module ? module.value : ''
      this.editingSubject.modularName = module ? module.name : ''
      if (this.editingSubject.score === undefined || this.editingSubject.score === null) this.$set(this.editingSubject, 'score', 0)
      if (this.editingSubject.screenshotRequirement === undefined || this.editingSubject.screenshotRequirement === null) this.$set(this.editingSubject, 'screenshotRequirement', '')
      if (this.isChoiceType(this.editingSubject.subjectType) && this.editingSubject.options.length < 2) {
        this.editingSubject.options = ['', '']
      }
      this.$nextTick(() => {
        if (this.$refs.subjectForm) this.$refs.subjectForm.clearValidate()
      })
    },
    openSubjectEditor (subject) {
      this.openSubjectDialog(subject)
      this.subjectEditDialog = true
      this.$nextTick(() => {
        if (this.$refs.subjectForm) this.$refs.subjectForm.clearValidate()
      })
    },
    resetSubjectEditForm () {
      this.editingSubject = {}
      if (this.$refs.subjectForm) this.$refs.subjectForm.clearValidate()
    },
    openSubjectCreateDialog () {
      this.creatingSubject = this.emptySubject()
      this.subjectDialog = true
      this.$nextTick(() => {
        if (this.$refs.subjectCreateForm) this.$refs.subjectCreateForm.clearValidate()
      })
    },
    resetSubjectCreateForm () {
      this.creatingSubject = {}
      if (this.$refs.subjectCreateForm) this.$refs.subjectCreateForm.clearValidate()
    },
    isChoiceType (type) {
      return type === 'single_choice' || type === 'multiple_choice'
    },
    isObjectiveType (type) {
      return type && type !== 'practical'
    },
    onSubjectTypeChange (type, subject = this.editingSubject) {
      if (this.isChoiceType(type)) {
        if (!subject.options || subject.options.length < 2) {
          this.$set(subject, 'options', ['', ''])
        }
      } else {
        this.$set(subject, 'options', [])
      }
      this.$set(subject, 'correctAnswer', type === 'multiple_choice' ? [] : '')
    },
    onSubjectModuleChange (value, subject = this.editingSubject) {
      const module = this.subjectModuleOptions.find(item => item.value === value)
      this.$set(subject, 'modularName', module ? module.name : '')
    },
    onUseEnvironmentChange (enabled, subject = this.editingSubject) {
      subject.point = enabled ? (subject.point || 'code') : ''
    },
    addSubjectOption (subject = this.editingSubject) {
      subject.options.push('')
    },
    removeSubjectOption (index, subject = this.editingSubject) {
      subject.options.splice(index, 1)
    },
    subjectTypeLabel (value) {
      const type = this.subjectTypes.find(item => item.value === value)
      return type ? type.label : value
    },
    subjectTypeTag (value) {
      if (value === 'practical') return 'danger'
      if (value === 'multiple_choice') return 'warning'
      if (value === 'true_false') return 'success'
      if (value === 'fill_blank') return 'info'
      return ''
    },
    environmentLabel (value) {
      return { code: '代码环境', cvat: '标注环境', t100: '推理环境' }[value] || value
    },
    validateSubjectSpecifics (subject) {
      if (this.isChoiceType(subject.subjectType)) {
        const options = (subject.options || []).map(value => value.trim()).filter(Boolean)
        if (new Set(options).size < 2) {
          this.$message.error('单选题和多选题至少需要两个不同选项')
          return false
        }
      }
      const correctAnswer = subject.correctAnswer
      if (this.isObjectiveType(subject.subjectType)
          && (Array.isArray(correctAnswer) ? correctAnswer.length === 0 : !String(correctAnswer || '').trim())) {
        this.$message.error('请配置标准答案')
        return false
      }
      if (subject.useEnvironment && !subject.point) {
        this.$message.error('请选择实操环境')
        return false
      }
      return true
    },
    subjectPayload (subject) {
      const payload = Object.assign({}, subject)
      const module = this.subjectModuleOptions.find(item => item.value === payload.modular)
      payload.modularName = module ? module.name : ''
      delete payload.subjectId
      delete payload.subjectCreationTime
      delete payload.subjectOptions
      delete payload.useEnvironment
      payload.correctAnswer = payload.subjectType === 'multiple_choice'
        ? JSON.stringify(payload.correctAnswer || [])
        : (payload.correctAnswer || '')
      return payload
    },
    standardAnswerText (subject) {
      return Array.isArray(subject.correctAnswer) ? subject.correctAnswer.join('、') : String(subject.correctAnswer || '')
    },
    async createSubject () {
      const valid = await new Promise(resolve => this.$refs.subjectCreateForm.validate(resolve))
      if (!valid || !this.validateSubjectSpecifics(this.creatingSubject)) return
      this.subjectCreating = true
      try {
        const result = await createAdminSubjectApi(this.subjectPayload(this.creatingSubject))
        if (result.code === 200) {
          this.editingSubject = {}
          this.subjectPaper = result.data.testPaperType
          this.subjectDialog = false
          await Promise.all([this.loadSubjects(), this.loadPublicState()])
          this.$message.success('题目已创建')
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.subjectCreating = false
      }
    },
    async saveSubject () {
      const valid = await new Promise(resolve => this.$refs.subjectForm.validate(resolve))
      if (!valid || !this.validateSubjectSpecifics(this.editingSubject)) return
      this.subjectSaving = true
      try {
        const result = await updateAdminSubjectApi(this.editingSubject.subjectId, this.subjectPayload(this.editingSubject))
        if (result.code === 200) {
          this.subjectEditDialog = false
          this.editingSubject = {}
          this.subjectPaper = result.data.testPaperType
          await Promise.all([this.loadSubjects(), this.loadPublicState()])
          this.$message.success('题目已保存')
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.subjectSaving = false
      }
    },
    async deleteSubject (subject) {
      try {
        await this.$confirm(`确定删除“${subject.subjectName}”吗？`, '删除题目', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        })
        const result = await deleteAdminSubjectApi(subject.subjectId)
        if (result.code === 200) {
          await Promise.all([this.loadSubjects(), this.loadPublicState()])
          this.$message.success('题目已删除')
        }
      } catch (error) {
        // Confirmation cancellation and request errors need no additional message here.
      }
    },
    async clearSubjectAnswers () {
      try {
        await this.$confirm('将删除所有试卷的全部作答记录，题目会保留。删除后无法恢复，确定继续吗？', '清空作答记录', {
          confirmButtonText: '确认清空',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return
      }
      this.subjectAnswersClearing = true
      try {
        const result = await clearAdminSubjectAnswersApi()
        if (result.code === 200) {
          await Promise.all([this.loadSubjects(), this.loadPublicState()])
          this.$message.success(`已清空 ${Number(result.data || 0)} 条作答记录`)
        }
      } catch (error) {
        // The shared interceptor reports validation and transport errors.
      } finally {
        this.subjectAnswersClearing = false
      }
    },
    async clearAllSubjects () {
      try {
        await this.$confirm('将删除所有试卷的全部题目和作答记录，删除后无法恢复。确定继续吗？', '清空全部题目', {
          confirmButtonText: '清空全部',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return
      }
      this.subjectsClearing = true
      try {
        const result = await clearAdminSubjectsApi()
        if (result.code === 200) {
          const counts = result.data || {}
          this.editingSubject = {}
          await Promise.all([this.loadSubjects(), this.loadPublicState()])
          this.$message.success(`已清空 ${Number(counts.subjectCount || 0)} 道题目和 ${Number(counts.answerCount || 0)} 条作答记录`)
        }
      } catch (error) {
        // The shared interceptor reports validation and transport errors.
      } finally {
        this.subjectsClearing = false
      }
    },
    async timerAction (action, minutes) {
      try {
        if (action === 'START') {
          const exportStatus = this.activePaper ? await gradingExportStatusApi(this.activePaper) : null
          const status = exportStatus && exportStatus.code === 200 ? exportStatus.data : {}
          const count = Number(status.submissionCount || 0)
          let message = '当前没有已提交试卷。确定开始新的 4 小时比赛吗？'
          let type = 'warning'
          if (count && status.exported) {
            message = `已确认 ${count} 份试卷全部导出。确定开始新的 4 小时比赛吗？`
            type = 'success'
          } else if (count) {
            message = `当前 ${count} 份试卷尚未全部导出，开始后将进入新一轮比赛。建议先到“试卷判分”导出全部试卷。确定仍要开始吗？`
          }
          await this.$confirm(message, '开始 4 小时', {
            confirmButtonText: '确定开始',
            cancelButtonText: '取消',
            type
          })
        }
        if (action === 'END') {
          await this.$confirm('结束后所有参赛账号将无法继续作答，确定结束比赛吗？', '结束比赛', {
            confirmButtonText: '确认结束',
            cancelButtonText: '取消',
            type: 'warning'
          })
        }
        if (action === 'SET_END' && !this.customEndTime) {
          this.$message.warning('请先选择结束时间')
          return
        }
        const data = { action }
        if (minutes) data.minutes = minutes
        if (action === 'SET_END') data.endTime = this.customEndTime
        this.timerUpdating = true
        const result = await adminCountdownApi(data)
        if (result.code === 200) {
          this.countDown = result.data
          this.$message.success('计时已更新')
        }
      } catch (error) {
        // Confirmation cancellation and request errors need no additional message here.
      } finally {
        this.timerUpdating = false
      }
    },
    async saveSchedule () {
      if (!this.scheduleForm.startTime) {
        this.$message.warning('请先选择正式开始时间')
        return
      }
      this.scheduleSaving = true
      try {
        const result = await adminCountdownApi({
          action: 'SCHEDULE',
          startTime: this.scheduleForm.startTime,
          preLoginMinutes: this.scheduleForm.preLoginMinutes,
          durationMinutes: this.scheduleForm.durationMinutes
        })
        if (result.code === 200) {
          this.countDown = result.data
          this.$message.success('比赛排期已保存')
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.scheduleSaving = false
      }
    },
    async selectPaper () {
      const result = await selectCompetitionApi(this.paperDraft)
      if (result.code === 200) {
        this.activePaper = this.paperDraft
        this.$store.commit('Match/SET_ACTIVE_PAPER', this.activePaper)
        this.$message.success(`已启用 ${this.activePaper} 卷`)
      }
    },
    async savePlatformSettings () {
      const valid = await new Promise(resolve => this.$refs.platformForm.validate(resolve))
      if (!valid) return
      this.platformSaving = true
      try {
        const result = await updatePlatformSettingsApi({
          platformName: String(this.platformForm.platformName || '').trim(),
          themeColor: String(this.platformForm.themeColor || DEFAULT_THEME_COLOR).trim(),
          loginBackgroundUrl: String(this.platformForm.loginBackgroundUrl || '').trim(),
          loginBrandName: String(this.platformForm.loginBrandName || '').trim(),
          loginTitle: String(this.platformForm.loginTitle || '').trim(),
          loginDescription: String(this.platformForm.loginDescription || '').trim(),
          loginCopyright: String(this.platformForm.loginCopyright || '').trim()
        })
        if (result.code === 200) {
          this.$store.commit('Match/SET_PLATFORM_SETTINGS', result.data)
          this.platformForm = Object.assign({}, this.platformForm, result.data)
          this.platformEditing = false
          this.$message.success('平台设置已更新')
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.platformSaving = false
      }
    },
    syncPlatformForm () {
      const state = this.$store.state.Match
      this.platformForm = {
        platformName: this.platformName,
        themeColor: state.themeColor,
        loginBackgroundUrl: state.loginBackgroundUrl,
        loginBrandName: state.loginBrandName,
        loginTitle: state.loginTitle,
        loginDescription: state.loginDescription,
        loginCopyright: state.loginCopyright
      }
    },
    async selectLoginBackground (uploadFile) {
      const file = uploadFile && uploadFile.raw
      if (!file) return
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
        this.$message.error('背景图片仅支持 JPEG、PNG 或 WebP')
        return
      }
      if (file.size > 10 * 1024 * 1024) {
        this.$message.error('背景图片不能超过 10 MB')
        return
      }
      this.backgroundUploading = true
      try {
        const result = await uploadLoginBackgroundApi(file)
        if (result.code === 200) {
          this.platformForm.loginBackgroundUrl = result.data.loginBackgroundUrl
          this.platformEditing = true
          this.$message.success('图片已上传，请保存设置')
        }
      } finally {
        this.backgroundUploading = false
      }
    },
    removeLoginBackground () {
      this.platformForm.loginBackgroundUrl = ''
      this.platformEditing = true
    },
    restoreDefaultTheme () {
      this.platformForm.themeColor = DEFAULT_THEME_COLOR
      this.platformEditing = true
      this.$message.info('已恢复默认主题色，点击“保存设置”后生效')
    },
    defaultCompetitionHelpContent () {
      const items = createDefaultCompetitionHelpItems(this.activePaper || this.subjectPaper, location.host)
      return formatCompetitionHelpContent(items)
    },
    async saveCompetitionContent () {
      this.competitionContentSaving = true
      try {
        const result = await updateCompetitionContentApi({ sections: this.competitionContent })
        if (result.code === 200) this.$message.success('赛规赛程已更新')
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.competitionContentSaving = false
      }
    },
    openCompetitionHelp () {
      this.competitionHelpDrawer = true
    },
    async saveCompetitionHelp () {
      this.competitionHelpSaving = true
      try {
        const result = await updateCompetitionHelpApi({ content: this.competitionHelpDraft })
        if (result.code === 200) {
          this.competitionHelpDraft = String(result.data.competitionHelpContent || '')
          this.$message.success('比赛帮助已更新')
        }
      } catch (error) {
        // The shared interceptor displays validation and transport errors.
      } finally {
        this.competitionHelpSaving = false
      }
    },
    openUserDialog (user) {
      if (user && this.isProtectedAdmin(user)) {
        this.$message.info('管理员账号请通过“修改管理员密码”维护')
        return
      }
      this.editingUser = user
        ? {
            userId: user.userId,
            userName: user.userName,
            password: '',
            enabled: user.enabled,
            admin: Boolean(user.isAdmin),
            schoolName: user.schoolName === '--' ? '' : (user.schoolName || ''),
            contestantName: user.contestantName === '--' ? '' : (user.contestantName || ''),
            teacherName: user.teacherName === '--' ? '' : (user.teacherName || ''),
            customFields: Object.assign({}, user.customFields || {})
          }
        : { userName: '', password: '', enabled: true, admin: false, schoolName: '', contestantName: '', teacherName: '', customFields: {} }
      this.customAnnouncementFields.forEach(field => {
        if (this.editingUser.customFields[field.fieldKey] == null) {
          this.$set(this.editingUser.customFields, field.fieldKey, '')
        }
      })
      this.userDialog = true
      this.$nextTick(() => {
        if (this.$refs.userForm) this.$refs.userForm.clearValidate()
      })
    },
    openAnnouncementFieldDialog (field) {
      this.editingAnnouncementField = field
        ? { fieldId: field.fieldId, fieldName: field.fieldName, enabled: Boolean(field.enabled) }
        : { fieldName: '', enabled: true }
      this.announcementFieldDialog = true
      this.$nextTick(() => {
        if (this.$refs.announcementFieldForm) this.$refs.announcementFieldForm.clearValidate()
      })
    },
    async saveAnnouncementField () {
      const valid = await new Promise(resolve => this.$refs.announcementFieldForm.validate(resolve))
      if (!valid) return
      this.announcementFieldSaving = true
      try {
        const data = { fieldName: this.editingAnnouncementField.fieldName.trim(), enabled: this.editingAnnouncementField.enabled }
        const result = this.editingAnnouncementField.fieldId
          ? await updateAnnouncementFieldApi(this.editingAnnouncementField.fieldId, data)
          : await createAnnouncementFieldApi(data)
        if (result.code === 200) {
          this.announcementFieldDialog = false
          await this.loadAnnouncementFields()
          this.$message.success('公告字段已保存')
        }
      } finally {
        this.announcementFieldSaving = false
      }
    },
    async toggleAnnouncementField (field, enabled) {
      try {
        const result = await updateAnnouncementFieldApi(field.fieldId, { enabled })
        if (result.code === 200) await this.loadAnnouncementFields()
      } catch (error) {
        // Server state remains authoritative; failed switches revert on refresh.
      }
    },
    openUserBatchDialog () {
      this.userBatchForm = { count: 20 }
      this.userBatchDialog = true
      this.$nextTick(() => {
        if (this.$refs.userBatchForm) this.$refs.userBatchForm.clearValidate()
      })
    },
    async createUsersBatch () {
      const valid = await new Promise(resolve => this.$refs.userBatchForm.validate(resolve))
      if (!valid) return
      this.userBatchSaving = true
      try {
        const result = await createAdminUsersBatchApi({ count: this.userBatchForm.count })
        if (result.code === 200) {
          const createdCount = Array.isArray(result.data) ? result.data.length : this.userBatchForm.count
          this.userBatchDialog = false
          await this.loadUsers()
          this.$message.success(`已创建 ${createdCount} 个账号`)
        } else {
          this.$message.error(result.msg || '账号创建失败')
        }
      } catch (error) {
        // The shared interceptor reports validation and transport errors.
      } finally {
        this.userBatchSaving = false
      }
    },
    async clearUsers () {
      try {
        await this.$confirm('清空后仅保留 admin 用户，非 admin 用户的作答、成绩、队伍关联和训练环境分配将一并删除。确定继续吗？', '清空用户', {
          confirmButtonText: '清空用户',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch (error) {
        return
      }
      this.userClearing = true
      try {
        const result = await clearAdminUsersApi()
        if (result.code === 200) {
          await Promise.all([this.loadUsers(), this.loadServers()])
          this.$message.success(`已清空 ${Number(result.data || 0)} 个用户`)
        } else {
          this.$message.error(result.msg || '用户清空失败')
        }
      } catch (error) {
        // The shared interceptor reports validation and transport errors.
      } finally {
        this.userClearing = false
      }
    },
    isAdminUser (user) {
      return Boolean(user && user.isAdmin) || String(user && user.userName || '').toLowerCase() === 'admin'
    },
    isProtectedAdmin (user) {
      return String(user && user.userName || '').toLowerCase() === 'admin'
    },
    accountInitial (userName) {
      const value = String(userName || '?').trim()
      return (value.charAt(0) || '?').toUpperCase()
    },
    async saveUser () {
      const valid = await new Promise(resolve => this.$refs.userForm.validate(resolve))
      if (!valid) return
      this.userSaving = true
      try {
        const request = {
          userName: this.editingUser.userName.trim(),
          password: this.editingUser.password,
          enabled: this.editingUser.enabled,
          admin: this.editingUser.admin,
          schoolName: this.editingUser.schoolName,
          contestantName: this.editingUser.contestantName,
          teacherName: this.editingUser.teacherName,
          customFields: this.editingUser.customFields
        }
        const result = this.editingUser.userId
          ? await updateAdminUserApi(this.editingUser.userId, request)
          : await createAdminUserApi(request)
        if (result.code === 200) {
          this.userDialog = false
          await this.loadUsers()
          this.$message.success(this.editingUser.userId ? '账号已更新' : '账号已创建')
        } else {
          this.$message.error(result.msg || '账号保存失败')
        }
      } catch (error) {
        // The shared interceptor reports validation and transport errors.
      } finally {
        this.userSaving = false
      }
    },
    async toggleUser (user, enabled) {
      if (this.isProtectedAdmin(user) || this.userTogglingId !== null) return
      this.userTogglingId = user.userId
      try {
        const result = await updateAdminUserApi(user.userId, { userName: user.userName, enabled })
        if (result.code === 200) {
          await this.loadUsers()
          this.$message.success(enabled ? '账号已启用' : '账号已停用')
        } else {
          this.$message.error(result.msg || '账号状态更新失败')
        }
      } catch (error) {
        // The switch is controlled by server state, so a failed request changes nothing locally.
      } finally {
        this.userTogglingId = null
      }
    },
    newServer () {
      this.editingServer = {
        serverName: '',
        vscodeBasePort: 9090,
        cvatBasePort: 8080,
        t100BasePort: 5000,
        enabled: true,
        nodes: [1, 2, 3, 4].map(nodeNo => ({ nodeNo, host: '', enabled: true }))
      }
      this.serverDialog = true
      this.$nextTick(() => {
        if (this.$refs.serverForm) this.$refs.serverForm.clearValidate()
      })
    },
    editServer (server) {
      this.editingServer = JSON.parse(JSON.stringify(server))
      const nodes = this.editingServer.nodes || []
      this.editingServer.nodes = [1, 2, 3, 4].map(nodeNo => {
        return nodes.find(node => node.nodeNo === nodeNo) || { nodeNo, host: '', enabled: true }
      })
      this.serverDialog = true
      this.$nextTick(() => {
        if (this.$refs.serverForm) this.$refs.serverForm.clearValidate()
      })
    },
    async saveServer () {
      const valid = await new Promise(resolve => this.$refs.serverForm.validate(resolve))
      if (!valid) return
      this.serverSaving = true
      try {
        const result = this.editingServer.trainingServerId
          ? await updateTrainingServerApi(this.editingServer)
          : await createTrainingServerApi(this.editingServer)
        if (result.code === 200) {
          this.serverDialog = false
          await this.loadServers()
          this.$message.success(this.editingServer.trainingServerId ? '服务器已更新' : '服务器已创建')
        }
      } catch (error) {
        // The shared interceptor reports validation and transport errors.
      } finally {
        this.serverSaving = false
      }
    },
    async deleteServer (server) {
      try {
        await this.$confirm(`确定删除“${server.serverName}”吗？删除后服务器及其节点配置将永久移除。`, '删除服务器', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        })
        this.serverDeletingId = server.trainingServerId
        const result = await deleteTrainingServerApi(server.trainingServerId)
        if (result.code === 200) {
          await this.loadServers()
          this.$message.success('服务器已删除')
        }
      } catch (error) {
        // Confirmation cancellation and request errors need no additional message here.
      } finally {
        this.serverDeletingId = null
      }
    },
    nodeLabel (node) {
      const server = this.servers.find(item => (item.nodes || []).some(value => value.trainingNodeId === node.trainingNodeId))
      if (!server) return `节点 ${node.nodeNo} / ${node.host}`
      return `${server.serverName} / 节点 ${node.nodeNo} / 代码 ${this.serverPort(server, node, 'vscodeBasePort')} / 标注 ${this.serverPort(server, node, 'cvatBasePort')} / 推理 ${this.serverPort(server, node, 't100BasePort')}`
    },
    nodeAccountLabel (node) {
      const names = Array.from(new Set((node.slots || []).map(slot => slot && slot.userName).filter(Boolean)))
      return names.length ? names.join('、') : '未分配'
    },
    serverPort (server, node, field) {
      const basePort = Number(server[field] || 0)
      return basePort && node && node.nodeNo ? basePort + Number(node.nodeNo) : 0
    },
    portAssignment (node, slotNo) {
      return (node.slots || []).find(slot => slot && slot.slotNo === slotNo) || {}
    },
    async assign () {
      if (!this.assignmentReady) return
      this.assignmentSaving = true
      try {
        const result = await assignTrainingApi(this.assignment)
        if (result.code === 200) {
          this.assignment = { userId: null, trainingNodeId: null }
          await this.loadServers()
          this.$message.success('三端口已分配')
        }
      } finally {
        this.assignmentSaving = false
      }
    },
    async unassign (userId) {
      try {
        await this.$confirm('确定取消该账号的训练环境分配吗？', '取消分配', {
          confirmButtonText: '确认取消',
          cancelButtonText: '保留分配',
          type: 'warning'
        })
        this.unassigningUserId = userId
        const result = await unassignTrainingApi(userId)
        if (result.code === 200) {
          await this.loadServers()
          this.$message.success('分配已取消')
        }
      } catch (error) {
        // Confirmation cancellation and request errors need no additional message here.
      } finally {
        this.unassigningUserId = null
      }
    },
    async changePassword () {
      const valid = await new Promise(resolve => this.$refs.passwordForm.validate(resolve))
      if (!valid) return
      const result = await changePasswordApi(this.passwordForm)
      if (result.code === 200) {
        const userInfo = Object.assign({}, this.$store.state.Match.userInfo, { mustChangePassword: false })
        setUserInfo(userInfo)
        this.$store.commit('Match/SET_USER_INFO', userInfo)
        this.passwordForm = { currentPassword: '', newPassword: '' }
        this.passwordDialog = false
        this.$message.success('管理员密码已修改')
        this.$router.push({ path: '/Publicity' }).catch(() => {})
      } else {
        this.$message.error(result.msg || '密码修改失败')
      }
    }
  }
}
</script>

<style scoped>
.admin-page { padding: 20px; }
.admin-header { margin-bottom: 20px; }
.admin-header-row, .control-row, .toolbar, .assign-row, .server-title { display: flex; align-items: center; gap: 12px; }
.admin-header-row, .toolbar, .server-title { justify-content: space-between; }
.admin-tabs {
  display: block;
  min-height: calc(100vh - 250px);
  padding: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dbe4ed;
  border-radius: 6px;
  box-shadow: 0 10px 28px rgba(13, 41, 68, 0.08);
}
.admin-tab-label { display: flex; align-items: center; gap: 12px; }
.admin-tab-label i { width: 20px; font-size: 18px; text-align: center; }
.admin-tabs::v-deep .el-tabs__header.is-left {
  width: 172px;
  flex: 0 0 172px;
  float: none;
  margin: 0;
  padding-top: 14px;
  background: #172d44;
}
.admin-tabs::v-deep .el-tabs__nav-wrap.is-left::after { display: none; }
.admin-tabs::v-deep .el-tabs__nav-scroll { height: 100%; }
.admin-tabs::v-deep .el-tabs__item.is-left {
  height: 48px;
  padding: 0 20px;
  color: #c8d5e2 !important;
  font-size: 14px !important;
  line-height: 48px;
  text-align: left;
  transition: color 160ms ease, background-color 160ms ease;
}
.admin-tabs::v-deep .el-tabs__item.is-left:hover {
  color: #fff !important;
  background: rgba(108, 166, 211, 0.12);
}
.admin-tabs::v-deep .el-tabs__item.is-left.is-active {
  color: #f4c542 !important;
  background: rgba(108, 166, 211, 0.16);
}
.admin-tabs::v-deep .el-tabs__active-bar.is-left {
  right: auto;
  left: 0;
  width: 3px;
  height: 48px !important;
  background: #f4c542 !important;
}
.admin-tabs::v-deep .el-tabs__content {
  height: auto !important;
  min-width: 0;
  flex: 1;
  padding: 20px;
  overflow: visible;
}
.admin-tabs::v-deep .el-tab-pane { margin-top: 0; }
.timer-secondary { margin-top: 18px; }
.server-form { max-width: 760px; padding: 10px 0 20px; }
.server-table { margin-top: 20px; }
.assign-row { margin-top: 20px; }
.subject-panel { padding: 4px 0; }
.subject-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 8px 0 16px; }
.subject-search { width: 220px; }
.subject-count { color: #606266; font-size: 13px; }
.subject-add { margin-left: auto; }
.subject-table small { color: #909399; }
.subject-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; }
.subject-form-grid .el-select, .subject-form-grid .el-input-number { width: 100%; }
.subject-option-row { display: grid; grid-template-columns: minmax(0, 1fr) 40px; gap: 8px; margin-bottom: 8px; }
.node-account { color: #34495e; font-weight: 600; }
.subject-heading-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.subject-heading-actions > div { display: flex; gap: 8px; }
.subject-heading-actions .el-button { margin-left: 0; }
.subject-switcher { display: flex; flex-direction: column; min-height: 48px; margin-bottom: 14px; overflow: hidden; background: #fff; border: 1px solid var(--line); border-radius: 7px; box-sizing: border-box; }
.subject-module-row + .subject-module-row { border-top: 1px solid var(--line); }
.subject-module-heading { display: flex; align-items: baseline; gap: 10px; min-width: 0; padding: 9px 14px; background: #f7f9fa; }
.subject-module-heading strong { flex: 0 0 auto; color: var(--navy-900); font-size: 13px; }
.subject-module-heading span { overflow: hidden; color: var(--muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.subject-table-scroll { overflow-x: auto; }
.subject-list-table { width: 100%; table-layout: fixed; border-collapse: collapse; color: #5c6d7c; font-size: 12px; }
.subject-list-table th, .subject-list-table td { padding: 9px 12px; text-align: left; border-top: 1px solid #edf0f2; box-sizing: border-box; }
.subject-list-table th + th, .subject-list-table td + td { border-left: 1px solid #f0f2f4; }
.subject-list-table th { color: #738393; font-weight: 600; background: #fbfcfd; }
.subject-list-table tbody tr { transition: background-color 150ms ease, box-shadow 150ms ease; }
.subject-list-table tbody tr:hover, .subject-list-table tbody tr.is-active { background: #eef6fc; box-shadow: inset 3px 0 #3c9bf2; }
.subject-col-order { width: 68px; }
.subject-col-type { width: 104px; }
.subject-col-actions { width: 112px; }
.subject-row-order { color: #84919d; font-weight: 600; text-align: center !important; }
.subject-list-table th:first-child { text-align: center; }
.subject-row-copy { min-width: 0; line-height: 1.55; overflow-wrap: anywhere; white-space: pre-wrap; }
.subject-row-type { color: #718292; white-space: nowrap; }
.subject-row-actions { text-align: center !important; white-space: nowrap; }
.subject-row-actions .el-button { padding: 3px 2px; font-size: 12px; }
.subject-row-actions .el-button + .el-button { margin-left: 10px; }
.subject-row-actions .el-button.is-danger { color: #d94848; }
.subject-row-actions .el-button.is-danger:hover { color: #b92f2f; }
.subject-list-table tbody tr.is-active .subject-row-order, .subject-list-table tbody tr.is-active .subject-row-type { color: #1d6d9f; }
.subject-switcher-empty { align-self: center; color: var(--muted); font-size: 13px; line-height: 28px; }
.help-editor-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; height: calc(100vh - 95px); padding: 0 22px 22px; box-sizing: border-box; }
.help-editor-column, .help-preview-column { min-width: 0; }
.help-editor-column h3, .help-preview-column h3 { margin: 0 0 12px; color: var(--navy-900); font-size: 15px; }
.help-editor-column::v-deep .el-textarea__inner { line-height: 1.7; resize: vertical; }
.help-editor-column .el-button { margin-top: 12px; }
.help-preview-column { padding: 16px; background: #f7f9fa; border: 1px solid #dce4e9; border-radius: 6px; box-sizing: border-box; }
.help-preview-copy { color: #435568; font-size: 14px; line-height: 1.85; white-space: pre-wrap; word-break: break-word; }
.content-editor-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.content-editor-toolbar .el-radio-group { min-width: 0; overflow-x: auto; white-space: nowrap; }
.content-editor-toolbar > .el-button { flex: 0 0 auto; }
.content-editor-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, 1fr); gap: 18px; align-items: start; }
.content-editor-input .panel-body { padding-bottom: 4px; }
.content-editor-input::v-deep .el-textarea__inner { resize: vertical; line-height: 1.7; }
.competition-preview-body { min-height: 540px; padding: 30px; color: #34495e; background: #fff; box-sizing: border-box; }
.competition-preview-body h2 { margin: 0 0 24px; padding-bottom: 16px; color: #173d5b; font-size: 23px; border-bottom: 1px solid #dbe2e8; }
.competition-preview-copy { line-height: 1.9; white-space: pre-wrap; word-break: break-word; }
.subject-editor-form { min-width: 0; }
.subject-edit-dialog::v-deep .el-dialog { display: flex; flex-direction: column; max-height: 94vh; border-radius: 7px; }
.subject-edit-dialog::v-deep .el-dialog__body { min-height: 0; padding-top: 16px; overflow-y: auto; }
.subject-edit-dialog-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(380px, .92fr); gap: 24px; align-items: start; }
.subject-edit-column { min-width: 0; }
.subject-edit-preview { position: sticky; top: 0; padding-left: 24px; border-left: 1px solid #e3e9ee; }
.subject-edit-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 0 0 16px; color: #173d5b; font-size: 15px; }
.subject-edit-heading > i, .subject-edit-heading > span > i { margin-right: 7px; color: #2f8fe4; }
.subject-edit-form::v-deep .el-form-item { margin-bottom: 18px; }
.subject-edit-form::v-deep .el-select, .subject-edit-form::v-deep .el-input-number { width: 100%; }
.subject-preview-paper { padding: 22px; background: #f7f9fa; }
.subject-preview-module { margin-bottom: 14px; color: #667789; font-size: 12px; }
.subject-preview-module strong { margin-left: 8px; color: #173d5b; font-size: 16px; }
.subject-preview-card { min-height: 310px; padding: 20px; background: #fff; border: 1px solid #d6dfe6; border-left: 3px solid #2f8fe4; border-radius: 5px; box-sizing: border-box; }
.subject-preview-meta { display: flex; align-items: center; gap: 10px; color: #738190; font-size: 12px; }
.subject-preview-meta b { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; color: #fff; background: #173d5b; border-radius: 4px; }
.subject-preview-meta span:last-child { margin-left: auto; }
.subject-preview-card h3 { margin: 16px 0; color: #2d3439; font-size: 16px; line-height: 1.75; }
.subject-preview-options { margin: 0 0 16px; padding-left: 22px; color: #4f6071; line-height: 1.9; }
.subject-preview-note { display: flex; flex-direction: column; gap: 6px; margin-top: 14px; padding: 12px 14px; color: #586878; font-size: 12px; line-height: 1.7; background: #f0f3f5; border-radius: 4px; white-space: pre-wrap; }
.subject-preview-note strong { color: #34495e; }
.subject-preview-environment { margin-top: 18px; color: #2f8fe4; font-size: 13px; }
.subject-create-dialog::v-deep .el-dialog { display: flex; flex-direction: column; max-height: 92vh; border-radius: 7px; }
.subject-create-dialog::v-deep .el-dialog__body { min-height: 0; overflow-y: auto; }
.subject-create-form::v-deep .el-form-item { margin-bottom: 18px; }
.subject-create-form::v-deep .el-select, .subject-create-form::v-deep .el-input-number { width: 100%; }

.account-panel {
  min-width: 0;
  padding: 6px 2px 10px;
  color: #253548;
}
.account-heading-actions { display: flex; align-items: center; gap: 8px; }
.account-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 0 20px;
}
.account-heading > div { display: flex; align-items: baseline; gap: 10px; min-width: 0; }
.account-heading h2 {
  margin: 0;
  color: #172d44;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
}
.account-heading-meta { color: #8a97a5; font-size: 13px; white-space: nowrap; }
.account-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 18px;
  overflow: hidden;
  background: #f6f8fa;
  border: 1px solid #e7ebef;
  border-radius: 7px;
}
.account-metric {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 18px 20px;
}
.account-metric + .account-metric { border-left: 1px solid #e1e7ec; }
.account-metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  color: #3d6f9b;
  font-size: 17px;
  background: #e8f0f6;
  border-radius: 7px;
}
.account-metric-enabled .account-metric-icon { color: #287c56; background: #e4f2ea; }
.account-metric-disabled .account-metric-icon { color: #707b86; background: #e9edf0; }
.account-metric > div { display: flex; flex-direction: column; min-width: 0; }
.account-metric strong {
  color: #172d44;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.account-metric span:last-child { margin-top: 6px; color: #718090; font-size: 12px; white-space: nowrap; }
.account-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 0 14px;
}
.account-search { width: 260px; }
.account-filter { width: 136px; }
.account-result { margin-left: auto; color: #8a97a5; font-size: 12px; white-space: nowrap; }
.account-table {
  width: 100%;
  min-height: 270px;
  background: #fff !important;
  border-top: 1px solid #e6ebf0;
}
.account-table::v-deep::before { display: none; }
.account-table::v-deep .el-table__header-wrapper,
.account-table::v-deep .el-table__header-wrapper div,
.account-table::v-deep .el-table__header-wrapper th { background: #fafbfc !important; }
.account-table::v-deep .el-table__header-wrapper { border-bottom-color: #e8edf1 !important; }
.account-table::v-deep .el-table__body-wrapper {
  height: auto !important;
  margin-top: 0 !important;
  background: #fff !important;
}
.account-table::v-deep th.el-table__cell {
  height: 46px;
  padding: 0;
  color: #667587;
  font-size: 12px;
  font-weight: 600;
  background: #fafbfc;
  border-bottom-color: #e8edf1;
}
.account-table::v-deep td.el-table__cell {
  height: 68px;
  padding: 0;
  border-bottom-color: #edf0f3;
}
.account-table::v-deep .el-table__row td:first-child { color: inherit !important; font-weight: normal !important; }
.account-table::v-deep .el-table__row:hover > td.el-table__cell { background: #f8fafb; }
.account-identity { display: flex; align-items: center; gap: 12px; min-width: 0; }
.account-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  color: #315e81;
  font-size: 15px;
  font-weight: 700;
  background: #dce9f2;
  border: 1px solid #d0e0eb;
  border-radius: 7px;
}
.account-avatar.is-admin { color: #755a12; background: #f8edc7; border-color: #efdfa4; }
.account-identity-copy { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.account-name-row { display: flex; align-items: center; gap: 8px; min-width: 0; }
.account-name-row strong {
  overflow: hidden;
  color: #24384d;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-role, .account-admin-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  flex: 0 0 auto;
  color: #718090;
  font-size: 11px;
}
.account-admin-badge { color: #8b6a13; }
.account-id { color: #98a2ad; font-size: 11px; }
.account-status { display: flex; align-items: center; gap: 10px; }
.account-status-label { color: #7d8791; font-size: 13px; }
.account-status-label.is-enabled { color: #287c56; }
.account-password {
  display: inline-block;
  padding: 4px 7px;
  color: #31475c;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 13px;
  letter-spacing: 0;
  background: #f3f6f8;
  border: 1px solid #e2e8ed;
  border-radius: 4px;
  white-space: nowrap;
}
.account-field { display: block; overflow: hidden; color: #435568; text-overflow: ellipsis; white-space: nowrap; }
.account-action {
  width: 34px;
  height: 34px;
  padding: 0;
  color: #3d6f9b;
  border-color: #dce4eb;
  background: #fff;
}
.account-action:hover, .account-action:focus { color: #fff; background: #3d6f9b; border-color: #3d6f9b; }
.account-protected { color: #9a7b29; font-size: 12px; white-space: nowrap; cursor: help; }
.account-form::v-deep .el-form-item { margin-bottom: 20px; }
.account-form::v-deep .el-form-item__label {
  padding: 0 0 7px;
  color: #405267;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}
.account-form-status {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 12px;
  color: #536477;
  background: #f7f9fa;
  border: 1px solid #e5e9ed;
  border-radius: 6px;
}
.account-dialog::v-deep .el-dialog { border-radius: 7px; }
.account-dialog::v-deep .el-dialog__header { padding: 22px 24px 15px; }
.account-dialog::v-deep .el-dialog__title { color: #172d44; font-size: 18px; font-weight: 700; }
.account-dialog::v-deep .el-dialog__body { padding: 10px 24px 4px; }
.account-dialog::v-deep .el-dialog__footer { padding: 14px 24px 22px; border-top: 1px solid #edf0f2; }
.account-batch-dialog::v-deep .el-input-number { width: 100%; }

@media (max-width: 760px) {
  .admin-page { padding: 10px; }
  .admin-tabs { min-height: calc(100vh - 220px); }
  .admin-tabs::v-deep .el-tabs__header.is-left { width: 60px; flex-basis: 60px; }
  .admin-tabs::v-deep .el-tabs__item.is-left { padding: 0 18px; }
  .admin-tabs::v-deep .el-tabs__content { padding: 14px 10px; }
  .admin-tab-label > span { display: none; }
  .admin-header-row, .control-row, .assign-row { align-items: stretch; flex-wrap: wrap; }
  .subject-search { width: 100%; }
  .subject-add { margin-left: 0; }
  .subject-form-grid { grid-template-columns: minmax(0, 1fr); }
  .account-heading { align-items: flex-start; }
  .account-panel .workspace-heading { align-items: stretch; flex-direction: column; }
  .account-panel .workspace-heading h1 { white-space: nowrap; }
  .account-heading-actions { align-items: stretch; flex-wrap: nowrap; width: 100%; }
  .account-heading-actions .el-button { flex: 1 1 0; margin-left: 0; }
  .account-heading > div { flex-direction: column; gap: 2px; }
  .account-summary { grid-template-columns: repeat(3, minmax(112px, 1fr)); overflow-x: auto; }
  .account-metric { gap: 8px; padding: 14px 12px; }
  .account-metric-icon { flex-basis: 32px; width: 32px; height: 32px; }
  .account-metric strong { font-size: 20px; }
  .account-toolbar { flex-wrap: wrap; }
  .account-search { flex: 1 1 220px; width: auto; }
  .account-filter { flex: 0 0 126px; width: 126px; }
  .account-result { flex: 1 0 100%; margin-left: 0; }
  .account-id { display: none; }
  .account-table::v-deep .el-table__body-wrapper { overflow-x: auto; }
  .account-dialog::v-deep .el-dialog__header { padding: 20px 18px 14px; }
  .account-dialog::v-deep .el-dialog__body { padding: 8px 18px 2px; }
  .account-dialog::v-deep .el-dialog__footer { padding: 14px 18px 18px; }
  .subject-create-dialog::v-deep .el-dialog { max-height: 94vh; margin-top: 3vh !important; }
}

/* Full admin console shell */
.admin-page {
  --navy-900: #172d44;
  --navy-800: #1d3852;
  --ink: #22364a;
  --muted: #6f7e8d;
  --line: #dfe6eb;
  --surface: #ffffff;
  --canvas: #edf1f4;
  --gold: var(--platform-theme-color, #efc44b);
  --blue: #3f759d;
  --green: #2f8c61;
  position: relative;
  min-height: 100%;
  padding: 0;
  color: var(--ink);
  background: var(--canvas);
}
.admin-workspace { position: relative; min-height: calc(100vh - 64px); }
.admin-page .el-button--primary { background: var(--platform-theme-color, #3f759d); border-color: var(--platform-theme-color, #3f759d); }
.admin-utility {
  position: absolute;
  z-index: 3;
  top: 0;
  right: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  height: 64px;
  padding: 0 28px;
  background: #fff;
  border-bottom: 1px solid var(--line);
  box-sizing: border-box;
}
.admin-breadcrumb { color: #546678; font-size: 13px; }
.admin-utility-actions { display: flex; align-items: center; gap: 18px; }
.service-state { display: inline-flex; align-items: center; gap: 7px; color: #8a96a1; font-size: 12px; white-space: nowrap; }
.service-state i, .resource-state i, .server-status-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  background: #a4adb5;
  border-radius: 50%;
}
.service-state.is-online { color: var(--green); }
.service-state.is-online i, .resource-state.is-ready i, .server-status-dot { background: var(--green); box-shadow: 0 0 0 3px rgba(47, 140, 97, 0.1); }
.admin-identity { display: inline-flex; align-items: center; gap: 8px; color: #4e6071; font-size: 13px; }
.admin-identity b {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #fff;
  font-size: 12px;
  background: #6d7e8d;
  border-radius: 50%;
}
.admin-tabs {
  display: flex;
  min-height: calc(100vh - 64px);
  padding: 0;
  overflow: visible;
  background: var(--canvas);
  border: 0;
  border-radius: 0;
  box-shadow: none;
}
.admin-tabs::v-deep > .el-tabs__header { display: none; }
.admin-tabs::v-deep .el-tabs__content {
  height: auto !important;
  width: 100%;
  min-width: 0;
  padding: 0;
  overflow: visible !important;
  background: var(--canvas);
  box-sizing: border-box;
}
.admin-tabs::v-deep .el-tab-pane { margin-top: 0; }
.admin-tab-label { display: flex; align-items: center; gap: 12px; }
.admin-tab-label i { width: 20px; font-size: 17px; text-align: center; }
.workspace-page { width: 100%; min-width: 0; }
.workspace-heading {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  margin-bottom: 12px;
}
.workspace-heading h1 { margin: 0; color: var(--navy-900); font-size: 25px; font-weight: 700; line-height: 1.25; letter-spacing: 0; }
.workspace-heading p { margin: 6px 0 0; color: var(--muted); font-size: 13px; }
.icon-command, .table-action {
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--blue);
  background: #fff;
  border-color: #d5dee5;
}
.icon-command:hover, .table-action:hover { color: #fff; background: var(--blue); border-color: var(--blue); }
.table-action.is-danger { color: #b14c4c; }
.table-action.is-danger:hover { color: #fff; background: #b14c4c; border-color: #b14c4c; }
.summary-band {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 20px;
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 7px;
}
.competition-summary { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.schedule-panel { margin-bottom: 18px; border-left: 4px solid var(--platform-theme-color, #162d45); }
.schedule-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; min-height: 66px; padding: 12px 18px; border-bottom: 1px solid var(--line); box-sizing: border-box; }
.schedule-title { display: flex; align-items: center; gap: 11px; min-width: 0; }
.schedule-title > div { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.schedule-title strong { color: var(--navy-900); font-size: 14px; }
.schedule-title small { color: var(--muted); font-size: 11px; }
.schedule-icon { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 36px; width: 36px; height: 36px; color: #715817; font-size: 16px; background: #fff8df; border: 1px solid #ead37e; border-radius: 6px; }
.schedule-state { flex: 0 0 auto; padding: 5px 9px; color: #715817; font-size: 11px; background: #fff8df; border: 1px solid #ead37e; border-radius: 4px; }
.schedule-fields { display: grid; grid-template-columns: minmax(250px, 1.25fr) minmax(190px, .8fr) minmax(190px, .8fr); gap: 16px; padding: 17px 18px; }
.schedule-field { min-width: 0; }
.schedule-field .el-date-editor, .schedule-field .el-input-number { width: 100%; }
.schedule-number { display: grid; grid-template-columns: minmax(0, 1fr) 42px; align-items: center; overflow: hidden; background: #f8fafb; border: 1px solid #dcdfe6; border-radius: 4px; }
.schedule-number::v-deep .el-input__inner { border: 0; }
.schedule-number > span { color: var(--muted); font-size: 11px; text-align: center; }
.schedule-timeline { display: grid; grid-template-columns:auto minmax(24px, 1fr) auto minmax(24px, 1fr) auto minmax(120px, auto); align-items: center; gap: 12px; padding: 13px 18px; background: #f7f9fa; border-top: 1px solid var(--line); }
.schedule-milestone { display: flex; min-width: 72px; flex-direction: column; gap: 4px; }
.schedule-milestone small { color: var(--muted); font-size: 10px; }
.schedule-milestone strong { color: var(--navy-900); font-family: Menlo, Monaco, Consolas, monospace; font-size: 13px; letter-spacing: 0; }
.schedule-line { position: relative; width: 100%; height: 1px; background: #b6c3cc; }
.schedule-line i { position: absolute; right: 0; top: -3px; width: 0; height: 0; border-top: 3px solid transparent; border-bottom: 3px solid transparent; border-left: 5px solid #9faeba; }
.schedule-timeline .el-button { margin-left: 4px; }
.summary-item { display: flex; align-items: center; gap: 13px; min-width: 0; min-height: 86px; padding: 16px 20px; box-sizing: border-box; }
.summary-item + .summary-item { border-left: 1px solid var(--line); }
.summary-icon, .panel-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  color: #356d95;
  background: #e5eef5;
  border-radius: 7px;
}
.summary-icon-success { color: #287953; background: #e4f1e9; }
.summary-icon-muted { color: #6d7882; background: #ebeff2; }
.summary-item > div { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.summary-item small { color: var(--muted); font-size: 12px; }
.summary-item strong { overflow: hidden; color: var(--navy-900); font-size: 22px; line-height: 1.1; text-overflow: ellipsis; white-space: nowrap; }
.control-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(310px, 0.75fr); gap: 18px; }
.admin-panel, .assignment-bar, .server-panel {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 7px;
}
.notice-editor-panel,
.announcement-field-panel { margin-bottom: 18px; padding: 18px 20px 20px; }
.rules-tabs::v-deep > .el-tabs__header .el-tabs__item { color: #43566a; font-weight: 600; }
.rules-tabs::v-deep > .el-tabs__header .el-tabs__item:hover,
.rules-tabs::v-deep > .el-tabs__header .el-tabs__item.is-active { color: #2674b8; }
.announcement-entry-table + .el-button,
.announcement-field-panel .section-heading-row .el-button { color: #fff; }
.announcement-field-panel .section-heading-row .el-button.is-disabled { color: #52677a; background: #dce8f2; border-color: #c7d8e5; }
.notice-editor-panel .el-textarea { display: block; }
.notice-editor-panel ::v-deep .el-textarea__inner { min-height: 118px; line-height: 1.7; }
.section-heading-row { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 14px; }
.section-heading-row h2 { margin: 0; color: var(--navy-900); font-size: 16px; font-weight: 600; }
.section-heading-row p { margin: 5px 0 0; color: var(--muted); font-size: 12px; }
.account-profile-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 18px; }
@media (max-width: 760px) {
  .section-heading-row { align-items: flex-start; flex-direction: column; }
  .account-profile-grid { grid-template-columns: 1fr; }
}
.panel-heading, .server-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 62px;
  padding: 0 20px;
  border-bottom: 1px solid #e7ecef;
  box-sizing: border-box;
}
.server-heading > .server-heading-actions { display: flex; align-items: center; gap: 4px; }
.panel-heading > div, .server-heading > div { display: flex; align-items: center; gap: 10px; min-width: 0; }
.panel-heading .panel-icon { width: 32px; height: 32px; flex-basis: 32px; }
.panel-heading strong, .server-heading strong { color: var(--navy-900); font-size: 15px; }
.server-toggle { display: flex; align-items: center; min-width: 0; gap: 10px; padding: 8px 0; color: inherit; font: inherit; text-align: left; background: transparent; border: 0; cursor: pointer; }
.server-toggle:focus-visible { outline: 2px solid #3c86b8; outline-offset: 3px; border-radius: 4px; }
.server-toggle > i { width: 14px; flex: 0 0 14px; color: #668097; font-size: 13px; text-align: center; }
.server-toggle strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.server-state { flex: 0 0 auto; color: var(--green); font-size: 11px; }
.server-node-count { flex: 0 0 auto; color: #87939e; font-size: 11px; }
.panel-state { color: var(--green); font-size: 12px; }
.panel-body { padding: 20px; }
.timer-actions { display: flex; flex-direction: column; gap: 22px; }
.command-group { min-width: 0; }
.field-label { display: block; margin-bottom: 9px; color: #546679; font-size: 12px; font-weight: 600; }
.command-row { display: flex; flex-wrap: wrap; gap: 8px; }
.command-row .el-button + .el-button { margin-left: 0; }
.timer-custom-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; padding-top: 18px; border-top: 1px solid #e8edef; }
.inline-control { display: flex; gap: 8px; }
.inline-control .el-input-number, .inline-control .el-date-editor { width: 100%; min-width: 0; }
.paper-heading-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 8px; min-width: 0; }
.paper-selector { display: inline-flex; flex-wrap: wrap; }
.paper-create-form .el-input { text-transform: uppercase; }
.paper-resources { display: flex; flex-direction: column; gap: 11px; }
.paper-resource-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 11px; padding: 11px; background: #f7f9fa; border: 1px solid #e6ebee; border-radius: 6px; }
.paper-resource-row.is-active { background: #fffaf0; border-color: #e8d393; }
.paper-letter { display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px; color: #315f83; font-weight: 700; background: #e3edf4; border-radius: 6px; }
.paper-resource-row.is-active .paper-letter { color: #745b17; background: #f5e8bc; }
.paper-resource-row > div { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.paper-resource-row strong { color: var(--ink); font-size: 13px; }
.paper-resource-row small { overflow: hidden; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.resource-state { display: inline-flex; align-items: center; gap: 6px; color: #8a959f; font-size: 11px; white-space: nowrap; }
.resource-state.is-ready { color: var(--green); }
.danger-zone { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 18px; padding: 17px 20px; background: #fffafa; border: 1px solid #ead5d5; border-radius: 7px; }
.danger-zone > div:first-child { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.danger-zone strong { color: #934444; font-size: 13px; }
.danger-zone span { color: #8c7777; font-size: 12px; }
.danger-zone > div:last-child { display: flex; flex-shrink: 0; gap: 8px; }
.admin-filterbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 7px;
}
.subject-toolbar { padding: 12px; }
.subject-search { width: 240px; }
.account-search { width: 270px; }
.account-filter { width: 140px; }
.filter-result { margin-left: auto; color: #7d8995; font-size: 12px; white-space: nowrap; }
.admin-table {
  width: 100%;
  min-height: 180px;
  overflow: hidden;
  background: #fff !important;
  border: 1px solid var(--line);
  border-radius: 7px;
}
.admin-table::v-deep::before, .admin-table::v-deep .el-table__fixed::before, .admin-table::v-deep .el-table__fixed-right::before { display: none; }
.admin-table::v-deep .el-table__header-wrapper,
.admin-table::v-deep .el-table__header-wrapper div,
.admin-table::v-deep .el-table__header-wrapper th { background: #f7f9fa !important; }
.admin-table::v-deep .el-table__body-wrapper { height: auto !important; margin-top: 0 !important; background: #fff !important; }
.admin-table::v-deep .el-table__body, .admin-table::v-deep .el-table__row, .admin-table::v-deep td.el-table__cell { background: #fff !important; }
.admin-table::v-deep th.el-table__cell { height: 44px; padding: 0; color: #617183; font-size: 12px; font-weight: 600; border-bottom-color: #e5eaee; }
.admin-table::v-deep td.el-table__cell { height: 60px; padding: 0; color: #35485b; border-bottom-color: #edf0f2; }
.admin-table::v-deep .el-table__row td:first-child { color: inherit !important; font-weight: normal !important; }
.admin-table::v-deep .el-table__row:hover > td.el-table__cell { background: #f8fafb !important; }
.subject-table small { color: #8a96a1; font-size: 11px; }
.account-identity { display: flex; align-items: center; gap: 12px; min-width: 0; }
.account-avatar { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 38px; width: 38px; height: 38px; color: #315e81; font-size: 15px; font-weight: 700; background: #dce9f2; border: 1px solid #d0e0eb; border-radius: 7px; }
.account-avatar.is-admin { color: #755a12; background: #f8edc7; border-color: #efdfa4; }
.account-identity-copy { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.account-name-row { display: flex; align-items: center; gap: 8px; min-width: 0; }
.account-name-row strong { overflow: hidden; color: #24384d; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.account-role, .account-admin-badge { color: #718090; font-size: 11px; white-space: nowrap; }
.account-admin-badge { color: #8b6a13; }
.account-id { color: #98a2ad; font-size: 11px; }
.account-status { display: flex; align-items: center; gap: 10px; }
.account-status-label { color: #7d8791; font-size: 13px; }
.account-status-label.is-enabled { color: var(--green); }
.account-protected { color: #9a7b29; font-size: 12px; white-space: nowrap; cursor: help; }
.assignment-bar { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 18px; padding: 16px 18px; overflow: visible; }
.assignment-heading { display: flex; align-items: center; gap: 11px; flex: 0 0 auto; }
.assignment-heading .panel-icon { width: 34px; height: 34px; flex-basis: 34px; }
.assignment-heading > div { display: flex; flex-direction: column; gap: 3px; }
.assignment-heading strong { color: var(--navy-900); font-size: 13px; }
.assignment-heading small { color: var(--muted); font-size: 11px; }
.assignment-controls { display: flex; justify-content: flex-end; gap: 8px; min-width: 0; }
.assignment-controls .el-select { width: 170px; }
.server-list { display: flex; flex-direction: column; gap: 16px; }
.server-panel { overflow: hidden; }
.server-status-dot.is-disabled { background: #a4adb5; box-shadow: none; }
.server-status-dot.is-disabled + strong + .server-state { color: #8a959f; }
.server-status-dot.is-offline { background: #d5574f; box-shadow: 0 0 0 3px rgba(213, 87, 79, 0.12); }
.server-status-dot.is-offline + strong + .server-state { color: #b4433d; }
.server-status-dot.is-checking { background: #d9a441; box-shadow: 0 0 0 3px rgba(217, 164, 65, 0.12); }
.server-status-dot.is-checking + strong + .server-state { color: #9b711f; }
.server-table { margin-top: 0; border: 0; border-radius: 0; }
.node-host { color: #4f6477; font-family: Menlo, Monaco, Consolas, monospace; font-size: 12px; }
.port-state { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-height: 40px; padding: 6px 9px; color: #87929c; background: #f3f5f6; border: 1px solid #e5e9ec; border-radius: 5px; box-sizing: border-box; }
.port-state.is-occupied { color: #276c4d; background: #e9f4ed; border-color: #d5e8dc; }
.port-copy { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.port-copy strong { color: #34495e; font-family: Menlo, Monaco, Consolas, monospace; font-size: 12px; }
.port-copy span { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.port-state.is-occupied .port-copy strong { color: #276c4d; }
.port-state .el-button { padding: 3px; color: #56806c; }
.admin-empty { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10px; min-height: 260px; color: #86929d; background: #fff; border: 1px dashed #cfd8df; border-radius: 7px; }
.admin-empty i { font-size: 30px; }
.admin-empty strong { color: #617184; font-size: 13px; }
.settings-card { max-width: 680px; padding: 24px; box-sizing: border-box; }
.settings-form { max-width: 520px; }
.background-preview { width: 100%; max-width: 360px; height: 120px; margin-top: 10px; overflow: hidden; border: 1px solid #e3e8eb; border-radius: 6px; background: #f3f5f6; }
.background-preview img { width: 100%; height: 100%; object-fit: cover; }
.settings-form::v-deep .el-form-item { margin-bottom: 22px; }
.settings-form::v-deep .el-form-item__label { color: #405267; font-size: 13px; font-weight: 600; }
.settings-actions { display: flex; justify-content: flex-end; gap: 10px; }
.settings-actions .el-button + .el-button { margin-left: 0; }
.password-gate { display: flex; align-items: center; justify-content: center; min-height: calc(100vh - 54px); flex-direction: column; padding: 24px; text-align: center; box-sizing: border-box; }
.password-gate-icon { display: inline-flex; align-items: center; justify-content: center; width: 54px; height: 54px; color: #8b6a13; font-size: 22px; background: #f5e9bd; border-radius: 7px; }
.password-gate h1 { margin: 18px 0 7px; color: var(--navy-900); font-size: 22px; letter-spacing: 0; }
.password-gate p { margin: 0 0 20px; color: var(--muted); font-size: 13px; }
.subject-form-grid, .server-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 18px; }
.server-form { max-width: none; padding: 0; }
.server-port-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.server-port-grid .el-input-number { width: 100%; }
.node-form-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin: 3px 0 14px; padding-top: 16px; border-top: 1px solid #e8edef; }
.node-form-heading strong { color: var(--navy-900); font-size: 14px; }
.node-form-heading span { color: var(--muted); font-size: 11px; }
.node-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 14px; }
.subject-option-row { display: grid; grid-template-columns: minmax(0, 1fr) 40px; gap: 8px; margin-bottom: 8px; }
.admin-dialog::v-deep .el-dialog { overflow: hidden; border-radius: 7px; box-shadow: 0 18px 48px rgba(22, 42, 60, 0.2); }
.admin-dialog::v-deep .el-dialog__header { padding: 21px 24px 15px; border-bottom: 1px solid #edf0f2; }
.admin-dialog::v-deep .el-dialog__title { color: var(--navy-900); font-size: 18px; font-weight: 700; }
.admin-dialog::v-deep .el-dialog__body { padding: 18px 24px 6px; }
.admin-dialog::v-deep .el-dialog__footer { padding: 14px 24px 20px; border-top: 1px solid #edf0f2; }
.admin-dialog::v-deep .el-form-item { margin-bottom: 19px; }
.admin-dialog::v-deep .el-form-item__label { padding: 0 0 7px; color: #405267; font-size: 13px; font-weight: 600; line-height: 20px; }
.account-form-status { display: flex; align-items: center; gap: 10px; min-height: 40px; padding: 0 12px; color: #536477; background: #f7f9fa; border: 1px solid #e5e9ed; border-radius: 6px; box-sizing: border-box; }

@media (max-width: 980px) {
  .control-grid { grid-template-columns: minmax(0, 1fr); }
  .content-editor-grid { grid-template-columns: minmax(0, 1fr); }
  .help-editor-grid { grid-template-columns: minmax(0, 1fr); height: auto; }
  .schedule-fields { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .schedule-start-field { grid-column: 1 / -1; }
  .schedule-timeline { grid-template-columns: auto minmax(18px, 1fr) auto minmax(18px, 1fr) auto; }
  .schedule-timeline .el-button { grid-column: 1 / -1; justify-self: end; }
  .assignment-bar { align-items: stretch; flex-direction: column; }
  .assignment-controls { justify-content: flex-start; flex-wrap: wrap; }
  .assignment-controls .el-select { flex: 1 1 150px; width: auto; }
}

@media (max-width: 760px) {
  .admin-workspace { min-height: calc(100vh - 64px); }
  .admin-utility { left: 0; height: 64px; padding: 0 12px; }
  .admin-breadcrumb, .service-state { display: none; }
  .admin-utility-actions { width: 100%; justify-content: flex-end; gap: 10px; }
  .admin-identity { margin-right: auto; }
  .admin-tabs { min-height: calc(100vh - 64px); }
  .admin-tabs::v-deep .el-tabs__content { padding: 0; }
  .workspace-heading { align-items: center; margin-bottom: 16px; }
  .workspace-heading h1 { font-size: 22px; }
  .workspace-heading p { font-size: 12px; }
  .subject-panel .workspace-heading { align-items: flex-start; flex-direction: column; }
  .subject-heading-actions { width: 100%; }
  .subject-heading-actions > div { width: 100%; }
  .subject-heading-actions .el-button { flex: 1 1 0; min-width: 0; }
  .summary-band { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .summary-item:nth-child(3) { border-left: 0; border-top: 1px solid var(--line); }
  .summary-item:nth-child(4) { border-top: 1px solid var(--line); }
  .schedule-fields { grid-template-columns: minmax(0, 1fr); }
  .schedule-start-field { grid-column: auto; }
  .schedule-timeline { grid-template-columns: minmax(0, 1fr); align-items: stretch; }
  .schedule-line { display: none; }
  .schedule-milestone { min-width: 0; padding: 8px 0; border-bottom: 1px solid #e4eaee; }
  .schedule-timeline .el-button { grid-column: auto; width: 100%; margin: 4px 0 0; }
  .settings-actions { align-items: stretch; flex-direction: column-reverse; }
  .settings-actions .el-button { width: 100%; margin: 0; }
  .summary-item { min-height: 76px; gap: 8px; padding: 12px 10px; }
  .summary-icon { width: 31px; height: 31px; flex-basis: 31px; }
  .summary-item strong { font-size: 17px; }
  .summary-item small { font-size: 10px; }
  .timer-custom-row { grid-template-columns: minmax(0, 1fr); }
  .paper-panel .panel-heading { align-items: flex-start; flex-direction: column; height: auto; padding-top: 14px; padding-bottom: 14px; }
  .paper-heading-actions { width: 100%; justify-content: flex-start; }
  .paper-resource-row { grid-template-columns: 34px minmax(0, 1fr); }
  .resource-state { grid-column: 2; }
  .danger-zone { align-items: stretch; flex-direction: column; }
  .danger-zone > div:last-child { flex-wrap: wrap; }
  .subject-search, .account-search { flex: 1 1 190px; width: auto; }
  .subject-list-table th, .subject-list-table td { padding: 8px 7px; }
  .subject-col-order { width: 48px; }
  .subject-col-type { width: 72px; }
  .subject-col-actions { width: 92px; }
  .subject-row-actions .el-button + .el-button { margin-left: 4px; }
  .account-filter { width: 126px; }
  .filter-result { flex: 1 0 100%; margin-left: 0; }
  .admin-table::v-deep .el-table__body-wrapper { overflow-x: auto; }
  .account-id { display: none; }
  .assignment-controls .el-select { flex: 1 1 calc(50% - 8px); }
  .subject-form-grid, .server-form-grid, .node-form-grid { grid-template-columns: minmax(0, 1fr); }
  .server-port-grid { grid-template-columns: minmax(0, 1fr); gap: 0; }
  .node-form-heading { align-items: flex-start; flex-direction: column; }
  .admin-dialog::v-deep .el-dialog__header { padding: 19px 18px 14px; }
  .admin-dialog::v-deep .el-dialog__body { padding: 16px 18px 4px; }
  .admin-dialog::v-deep .el-dialog__footer { padding: 13px 18px 18px; }
}
</style>
