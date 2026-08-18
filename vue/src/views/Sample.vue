<template>
  <div>
    <div class="s-body">
      <header class="s-header">
        <div class="s-header-add">
          <el-button type="primary" @click="$router.push({ path: 'Form' })"
            >新建样本</el-button
          >
          <el-button type="primary" @click="dialogVisible = true"
            >新建采集位置</el-button
          >
        </div>
        <div class="s-header-search">
          <div>
            <el-input
              style="width: 100% !important"
              placeholder="请输入关键字"
              v-model="input"
              class="input-with-select"
              clearable
              @clear="onClear"
            >
              <el-button
                slot="append"
                icon="el-icon-search"
                @click="doSearch"
              ></el-button>
            </el-input>
          </div>
        </div>
        <!-- <div class="s-header-filter">
          <el-select v-model="select">
            <el-option label="选项1" value="1"></el-option>
          </el-select>
          <el-button>筛选</el-button>
        </div> -->
        <div class="s-header-total">
          在 <span>{{ total }}</span> 条记录中找到 <span>{{ total }}</span> 个
        </div>
      </header>
      <div class="s-table">
        <el-table
          :data="tableData"
          border
          style="width: 100%"
          :header-cell-style="headClass"
          :cell-style="rowClass"
          max-height="450"
          @sort-change="onSortChange"
        >
          <el-table-column fixed label="操作">
            <template slot-scope="scope">
              <!-- {{ scope.$index + 1 + ( current * 5 - 5 ) }} -->
              <el-button
                @click="doBrowser(scope.row, scope.$index)"
                type="text"
                size="small"
                >查看</el-button
              >
              <el-button type="text" size="small" @click="editList(scope.row, scope.row.depthOrHeightValue)"
                >编辑</el-button
              >
              <el-popconfirm
                style="margin-left: 10px"
                title="确定删除？"
                @confirm="yesDelete(scope.row.sampleNo)"
              >
                <el-button type="text" size="small" slot="reference"
                  >删除</el-button
                >
              </el-popconfirm>
            </template>
          </el-table-column>
          <el-table-column prop="sampleCode" label="版本编号">
            <template slot-scope="scope">
              <el-tooltip
                class="item"
                effect="dark"
                :content="scope.row.sampleCode"
                placement="top-start"
              >
                <span class="sampleCodeSpan">{{ scope.row.sampleCode }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="analyticalModel" label="分析模型">
          </el-table-column>
          <el-table-column prop="mineralName" label="矿物名称">
          </el-table-column>
          <el-table-column
            label="矿物密度"
            prop="diggingsWell.mineralDensity"
            sortable
          >
            <template slot-scope="scope">
              <span>{{
                scope.row.diggingsWell.mineralDensity.toFixed(2) + "g/cm³"
              }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="user.username" label="创建人">
          </el-table-column>
          <el-table-column
            label="创建时间"
            sortable
            prop="diggingsWell.createDate"
          >
            <template slot-scope="scope">
              <span>{{ formatDate(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="count" label="数据量"> </el-table-column>
        </el-table>
      </div>
      <footer class="s-footer">
        <div class="s-footer-icon">
          <el-button-group>
            <!-- <el-tooltip
              class="item"
              effect="dark"
              content="回收站"
              placement="top-start"
            >
              <el-button icon="el-icon-delete"></el-button>
            </el-tooltip> -->
            <el-tooltip
              class="item"
              effect="dark"
              content="导出Excel表"
              placement="top-start"
            >
              <el-button @click="onExport" icon="el-icon-share"></el-button>
            </el-tooltip>
          </el-button-group>
        </div>
        <div class="s-header-page">
          <el-button :disabled="current == 1" class="toPrev" @click="toPrev"
            >上一页</el-button
          >
          <div class="s-header-page-span">
            <span>{{ current }}</span
            >/{{ pages }}
          </div>
          <el-button :disabled="current == pages" class="toNext" @click="toNext"
            >下一页</el-button
          >
        </div>
      </footer>

      <el-dialog
        :before-close="handleClose"
        :visible.sync="dialogVisible"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        width="50%"
      >
        <div
          class="dialog-form"
          v-loading="loading"
          element-loading-text="拼命加载中"
          element-loading-spinner="el-icon-loading"
          element-loading-background="rgba(0, 0, 0, 0.8)"
        >
          <div class="dia-top">
            <div class="d-t-left">
              <img src="@/assets/icon/dialog1.png" alt="" />
              <span>建立矿区</span>
            </div>
            <div class="d-t-right">
              <img src="@/assets/icon/dialog1.png" alt="" />
              <span>建立采集位置</span>
            </div>
          </div>
          <div class="dia-lform">
            <el-form :model="form">
              <el-form-item label="矿区名称">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M6.667 16.667c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895zM25.333 20.439c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895zM4 23.333v-13.333c0-3.313 5.373-6 12-6s12 2.687 12 6v13.333c0 3.313-5.373 6-12 6s-12-2.687-12-6zM16 13.333c2.831 0 5.448-0.553 7.293-1.476 1.425-0.713 2.040-1.44 2.040-1.857s-0.615-1.144-2.040-1.857c-1.845-0.923-4.463-1.476-7.293-1.476s-5.448 0.553-7.293 1.476c-1.425 0.713-2.040 1.44-2.040 1.857s0.615 1.144 2.040 1.857c1.845 0.923 4.463 1.476 7.293 1.476z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-input
                  v-model="form.diggings"
                  clearable
                  placeholder="请输入矿区名称"
                ></el-input>
              </el-form-item>
              <el-form-item label="矿物密度">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M14.528 6.080v10.208c0 0.64 0.512 1.184 1.152 1.184h10.24c0.64 0 1.152 0.512 1.088 1.152-0.576 6.368-5.952 11.392-12.48 11.392-6.912 0-12.512-5.632-12.512-12.544s5.568-12.512 12.448-12.544h0.032s0.032 0.512 0.032 1.152zM29.984 14.336c0.003 0.030 0.005 0.065 0.005 0.1 0 0.599-0.485 1.084-1.084 1.084-0.003 0-0.006 0-0.009-0h-11.2c-0.64 0-1.152-0.512-1.152-1.152 0-3.264-0.032-12.352 0-12.352 7.072 0 12.864 5.44 13.44 12.32z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-input
                  v-model="form.mineralDensity"
                  clearable
                  placeholder="请输入矿物密度"
                  type="number"
                ></el-input>
              </el-form-item>
              <el-form-item label="深度/标高">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="26"
                  height="26"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M2.016 6.528c0.096 0 0.16-0.096 0.256-0.128 1.248-0.8 2.528-1.6 3.776-2.432 0.16-0.096 0.256-0.096 0.352 0.096 0.032 0.096 0.096 0.16 0.16 0.256 0.16 0.192 0.32 0.416-0.064 0.576-0.128 0.032-0.032 0.128 0 0.224 0.224 0.352 0.448 0.704 0.704 1.056 0.096 0.128 0.096 0.256 0.064 0.416-0.128 0.544-0.224 1.12-0.384 1.664-0.032 0.128 0 0.224 0.064 0.352 0.8 1.344 1.632 2.656 2.432 4 0.096 0.192 0.16 0.192 0.32 0.096 0.576-0.416 1.184-0.8 1.792-1.184 0.096-0.064 0.192-0.128 0.288 0 0.736 1.184 1.44 2.4 1.856 3.744 0.192 0.544 0.128 1.088-0.032 1.664-0.216 0.728-0.455 1.344-0.738 1.934l0.034-0.078c-0.064 0.16-0.16 0.192-0.288 0.192-0.125 0.005-0.272 0.008-0.419 0.008-0.724 0-1.43-0.073-2.113-0.212l0.068 0.012c-0.512-0.096-0.896-0.384-1.248-0.704-0.992-0.928-1.728-2.016-2.464-3.104-0.096-0.16 0-0.224 0.096-0.288l1.824-1.152c0.192-0.096 0.16-0.192 0.064-0.352-0.8-1.312-1.568-2.624-2.368-3.936-0.096-0.16-0.192-0.224-0.384-0.288-0.512-0.096-1.056-0.224-1.568-0.32-0.16-0.032-0.288-0.096-0.352-0.256-0.224-0.352-0.448-0.672-0.672-1.024-0.096-0.16-0.16-0.224-0.32-0.096-0.16 0.16-0.256 0.096-0.352-0.064-0.128-0.192-0.224-0.416-0.384-0.576v-0.096zM6.528 6.592c0.032-0.096 0-0.128-0.032-0.192-0.224-0.32-0.416-0.64-0.608-0.928-0.064-0.128-0.128-0.128-0.256-0.064-0.64 0.416-1.28 0.832-1.952 1.248-0.096 0.096-0.128 0.16-0.032 0.256 0.192 0.288 0.384 0.608 0.576 0.896 0.034 0.093 0.121 0.159 0.224 0.16h0l1.536 0.352c0.096 0 0.16 0.032 0.192-0.096 0.128-0.544 0.224-1.088 0.352-1.632zM30.016 27.52c-0.256 0-0.448 0.096-0.672 0.192-0.352 0.128-0.672-0.032-0.96-0.224-0.064-0.032-0.096-0.096-0.064-0.16l0.192-0.768c0.032-0.064 0.064-0.096 0.128-0.096 0.288-0.096 0.608-0.32 0.864-0.256 0.224 0.064 0.192 0.48 0.32 0.736 0.064 0.16 0.064 0.32 0.192 0.48v0.096zM26.144 28.096h-20.192c-0.32 0-0.32 0-0.288-0.32 0.064-0.352 0.128-0.704 0.16-1.088 0.032-0.16 0.128-0.224 0.224-0.288 0.608-0.32 1.216-0.672 1.856-0.992 0.16-0.096 0.224-0.16 0.192-0.384-0.128-0.704-0.192-1.408-0.288-2.112-0.032-0.224 0-0.32 0.256-0.32 0.8-0.064 1.632-0.16 2.464-0.224 0.192 0 0.256-0.064 0.288-0.256 0-0.32 0.064-0.64 0.096-0.96 0-0.128 0.032-0.192 0.192-0.192 1.024-0.096 2.016-0.192 3.040-0.256 0.224-0.032 0.256-0.096 0.288-0.288 0.032-0.704 0.096-1.408 0.16-2.112 0.032-0.256 0.256-0.416 0.384-0.608 0.128-0.256 0.32-0.32 0.608-0.224 0.192 0.064 0.448 0.128 0.672 0.16 0.288 0 0.352 0.16 0.384 0.384 0 0.256 0.064 0.512 0.064 0.736 0 0.192 0.096 0.256 0.288 0.256l1.696-0.128c0.224-0.032 0.288 0.032 0.32 0.256 0 0.672 0.064 1.344 0.096 1.984 0 0.192 0.064 0.288 0.224 0.352 0.704 0.256 1.376 0.544 2.080 0.8 0.16 0.064 0.224 0.128 0.224 0.32l0.096 1.536c0.032 0.224 0.16 0.288 0.32 0.256 0.256 0 0.544-0.064 0.8-0.096 0.32-0.032 0.352 0.032 0.384 0.32 0 0.352 0.16 0.576 0.448 0.768 0.256 0.16 0.544 0.256 0.832 0.32 0.096 0.032 0.224 0.032 0.224 0.16 0.128 0.896 0.736 1.44 1.28 2.080 0.048 0.047 0.090 0.1 0.126 0.156l0.002 0.004zM27.008 21.696c0.672 0 1.12 0.32 1.472 0.832 0.32 0.512 0.544 1.088 0.736 1.632 0.064 0.192 0.032 0.384-0.096 0.576-0.2 0.355-0.526 0.619-0.917 0.733l-0.011 0.003-1.344 0.48c-0.064 0-0.128 0.064-0.224-0.032-0.352-0.416-0.768-0.768-1.184-1.12-0.256-0.224-0.352-0.448-0.288-0.768 0.128-0.768 0.48-1.44 0.992-2.016 0.224-0.288 0.576-0.256 0.864-0.32zM26.016 18.368c-0.032 0.288-0.064 0.512-0.128 0.704-0.058 0.228-0.226 0.407-0.443 0.479l-0.005 0.001c-0.16 0.064-0.288 0.128-0.448 0.192-0.64 0.288-0.928 0.096-1.216-0.48-0.256-0.544-0.256-1.152-0.064-1.728 0.128-0.416 0.32-0.48 0.736-0.352 0.32 0.096 0.672 0.256 1.056 0.224 0.16-0.032 0.256 0.096 0.32 0.224 0.128 0.256 0.16 0.512 0.192 0.736zM21.28 15.584c0.192 0 0.384 0.064 0.544 0.192 0.224 0.16 0.352 0.352 0.288 0.64-0.096 0.32-0.32 0.576-0.608 0.608-0.224 0.032-0.448 0.064-0.704 0.096-0.16 0.032-0.288 0-0.256-0.224 0-0.192 0-0.384-0.032-0.544-0.032-0.512 0.224-0.768 0.768-0.768zM28.48 20.672c-0.287-0.034-0.547-0.125-0.777-0.261l0.009 0.005c-0.096-0.064-0.064-0.16-0.064-0.224l0.192-0.672c0.032-0.064 0.032-0.128 0.096-0.128l0.864-0.288c0.096-0.032 0.128 0.032 0.16 0.096 0.096 0.352 0.224 0.704 0.352 1.056 0 0.064 0.032 0.128-0.064 0.16-0.256 0.096-0.544 0.192-0.768 0.256zM24 22.4c-0.128-0.032-0.192-0.064-0.288-0.096-0.544-0.128-0.576-0.192-0.416-0.704 0.032-0.192 0.032-0.416 0.192-0.512 0.256-0.128 0.576-0.192 0.864-0.288 0.096-0.032 0.096 0.064 0.128 0.128l0.352 0.992c0 0.096 0.064 0.16-0.032 0.192-0.288 0.096-0.576 0.192-0.8 0.288zM20.864 18.72c0.032-0.256 0.192-0.416 0.384-0.576 0.032-0.032 0.064-0.032 0.128 0 0.288 0.096 0.608 0.192 0.928 0.32 0.16 0.064 0.224 0.288 0.224 0.48s-0.128 0.288-0.288 0.352l-0.256 0.096c-0.576 0.192-0.704 0.16-1.056-0.384-0.064-0.096-0.064-0.192-0.064-0.288z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-radio v-model="form.depthOrHeight" label="DEPTH"
                  >深度</el-radio
                >
                <el-radio v-model="form.depthOrHeight" label="HEIGHT"
                  >标高</el-radio
                >
              </el-form-item>
              <el-form-item
                :label="
                  form.depthOrHeight == 'HEIGHT' ? '矿区标高' : '矿区深度'
                "
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="26"
                  height="26"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M2.016 6.528c0.096 0 0.16-0.096 0.256-0.128 1.248-0.8 2.528-1.6 3.776-2.432 0.16-0.096 0.256-0.096 0.352 0.096 0.032 0.096 0.096 0.16 0.16 0.256 0.16 0.192 0.32 0.416-0.064 0.576-0.128 0.032-0.032 0.128 0 0.224 0.224 0.352 0.448 0.704 0.704 1.056 0.096 0.128 0.096 0.256 0.064 0.416-0.128 0.544-0.224 1.12-0.384 1.664-0.032 0.128 0 0.224 0.064 0.352 0.8 1.344 1.632 2.656 2.432 4 0.096 0.192 0.16 0.192 0.32 0.096 0.576-0.416 1.184-0.8 1.792-1.184 0.096-0.064 0.192-0.128 0.288 0 0.736 1.184 1.44 2.4 1.856 3.744 0.192 0.544 0.128 1.088-0.032 1.664-0.216 0.728-0.455 1.344-0.738 1.934l0.034-0.078c-0.064 0.16-0.16 0.192-0.288 0.192-0.125 0.005-0.272 0.008-0.419 0.008-0.724 0-1.43-0.073-2.113-0.212l0.068 0.012c-0.512-0.096-0.896-0.384-1.248-0.704-0.992-0.928-1.728-2.016-2.464-3.104-0.096-0.16 0-0.224 0.096-0.288l1.824-1.152c0.192-0.096 0.16-0.192 0.064-0.352-0.8-1.312-1.568-2.624-2.368-3.936-0.096-0.16-0.192-0.224-0.384-0.288-0.512-0.096-1.056-0.224-1.568-0.32-0.16-0.032-0.288-0.096-0.352-0.256-0.224-0.352-0.448-0.672-0.672-1.024-0.096-0.16-0.16-0.224-0.32-0.096-0.16 0.16-0.256 0.096-0.352-0.064-0.128-0.192-0.224-0.416-0.384-0.576v-0.096zM6.528 6.592c0.032-0.096 0-0.128-0.032-0.192-0.224-0.32-0.416-0.64-0.608-0.928-0.064-0.128-0.128-0.128-0.256-0.064-0.64 0.416-1.28 0.832-1.952 1.248-0.096 0.096-0.128 0.16-0.032 0.256 0.192 0.288 0.384 0.608 0.576 0.896 0.034 0.093 0.121 0.159 0.224 0.16h0l1.536 0.352c0.096 0 0.16 0.032 0.192-0.096 0.128-0.544 0.224-1.088 0.352-1.632zM30.016 27.52c-0.256 0-0.448 0.096-0.672 0.192-0.352 0.128-0.672-0.032-0.96-0.224-0.064-0.032-0.096-0.096-0.064-0.16l0.192-0.768c0.032-0.064 0.064-0.096 0.128-0.096 0.288-0.096 0.608-0.32 0.864-0.256 0.224 0.064 0.192 0.48 0.32 0.736 0.064 0.16 0.064 0.32 0.192 0.48v0.096zM26.144 28.096h-20.192c-0.32 0-0.32 0-0.288-0.32 0.064-0.352 0.128-0.704 0.16-1.088 0.032-0.16 0.128-0.224 0.224-0.288 0.608-0.32 1.216-0.672 1.856-0.992 0.16-0.096 0.224-0.16 0.192-0.384-0.128-0.704-0.192-1.408-0.288-2.112-0.032-0.224 0-0.32 0.256-0.32 0.8-0.064 1.632-0.16 2.464-0.224 0.192 0 0.256-0.064 0.288-0.256 0-0.32 0.064-0.64 0.096-0.96 0-0.128 0.032-0.192 0.192-0.192 1.024-0.096 2.016-0.192 3.040-0.256 0.224-0.032 0.256-0.096 0.288-0.288 0.032-0.704 0.096-1.408 0.16-2.112 0.032-0.256 0.256-0.416 0.384-0.608 0.128-0.256 0.32-0.32 0.608-0.224 0.192 0.064 0.448 0.128 0.672 0.16 0.288 0 0.352 0.16 0.384 0.384 0 0.256 0.064 0.512 0.064 0.736 0 0.192 0.096 0.256 0.288 0.256l1.696-0.128c0.224-0.032 0.288 0.032 0.32 0.256 0 0.672 0.064 1.344 0.096 1.984 0 0.192 0.064 0.288 0.224 0.352 0.704 0.256 1.376 0.544 2.080 0.8 0.16 0.064 0.224 0.128 0.224 0.32l0.096 1.536c0.032 0.224 0.16 0.288 0.32 0.256 0.256 0 0.544-0.064 0.8-0.096 0.32-0.032 0.352 0.032 0.384 0.32 0 0.352 0.16 0.576 0.448 0.768 0.256 0.16 0.544 0.256 0.832 0.32 0.096 0.032 0.224 0.032 0.224 0.16 0.128 0.896 0.736 1.44 1.28 2.080 0.048 0.047 0.090 0.1 0.126 0.156l0.002 0.004zM27.008 21.696c0.672 0 1.12 0.32 1.472 0.832 0.32 0.512 0.544 1.088 0.736 1.632 0.064 0.192 0.032 0.384-0.096 0.576-0.2 0.355-0.526 0.619-0.917 0.733l-0.011 0.003-1.344 0.48c-0.064 0-0.128 0.064-0.224-0.032-0.352-0.416-0.768-0.768-1.184-1.12-0.256-0.224-0.352-0.448-0.288-0.768 0.128-0.768 0.48-1.44 0.992-2.016 0.224-0.288 0.576-0.256 0.864-0.32zM26.016 18.368c-0.032 0.288-0.064 0.512-0.128 0.704-0.058 0.228-0.226 0.407-0.443 0.479l-0.005 0.001c-0.16 0.064-0.288 0.128-0.448 0.192-0.64 0.288-0.928 0.096-1.216-0.48-0.256-0.544-0.256-1.152-0.064-1.728 0.128-0.416 0.32-0.48 0.736-0.352 0.32 0.096 0.672 0.256 1.056 0.224 0.16-0.032 0.256 0.096 0.32 0.224 0.128 0.256 0.16 0.512 0.192 0.736zM21.28 15.584c0.192 0 0.384 0.064 0.544 0.192 0.224 0.16 0.352 0.352 0.288 0.64-0.096 0.32-0.32 0.576-0.608 0.608-0.224 0.032-0.448 0.064-0.704 0.096-0.16 0.032-0.288 0-0.256-0.224 0-0.192 0-0.384-0.032-0.544-0.032-0.512 0.224-0.768 0.768-0.768zM28.48 20.672c-0.287-0.034-0.547-0.125-0.777-0.261l0.009 0.005c-0.096-0.064-0.064-0.16-0.064-0.224l0.192-0.672c0.032-0.064 0.032-0.128 0.096-0.128l0.864-0.288c0.096-0.032 0.128 0.032 0.16 0.096 0.096 0.352 0.224 0.704 0.352 1.056 0 0.064 0.032 0.128-0.064 0.16-0.256 0.096-0.544 0.192-0.768 0.256zM24 22.4c-0.128-0.032-0.192-0.064-0.288-0.096-0.544-0.128-0.576-0.192-0.416-0.704 0.032-0.192 0.032-0.416 0.192-0.512 0.256-0.128 0.576-0.192 0.864-0.288 0.096-0.032 0.096 0.064 0.128 0.128l0.352 0.992c0 0.096 0.064 0.16-0.032 0.192-0.288 0.096-0.576 0.192-0.8 0.288zM20.864 18.72c0.032-0.256 0.192-0.416 0.384-0.576 0.032-0.032 0.064-0.032 0.128 0 0.288 0.096 0.608 0.192 0.928 0.32 0.16 0.064 0.224 0.288 0.224 0.48s-0.128 0.288-0.288 0.352l-0.256 0.096c-0.576 0.192-0.704 0.16-1.056-0.384-0.064-0.096-0.064-0.192-0.064-0.288z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-input
                  v-model="form.datumline"
                  clearable
                  :placeholder="
                    form.depthOrHeight == 'HEIGHT'
                      ? '请输入矿区标高'
                      : '请输入矿区深度'
                  "
                  :disabled="form.depthOrHeight == 'DEPTH'"
                  type="number"
                ></el-input>
              </el-form-item>
            </el-form>
          </div>
          <div class="dia-swap">
            <img src="@/assets/icon/swap.png" alt="" />
          </div>
          <div class="dia-rform">
            <el-form :model="form">
              <el-form-item label="采集位置">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M6.667 16.667c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895zM25.333 20.439c-2.2 1.36-5.564 2.228-9.333 2.228s-7.133-0.869-9.333-2.228v2.895c0 0.417 0.615 1.144 2.040 1.857 1.845 0.923 4.463 1.476 7.293 1.476s5.448-0.553 7.293-1.476c1.425-0.713 2.040-1.44 2.040-1.857v-2.895zM4 23.333v-13.333c0-3.313 5.373-6 12-6s12 2.687 12 6v13.333c0 3.313-5.373 6-12 6s-12-2.687-12-6zM16 13.333c2.831 0 5.448-0.553 7.293-1.476 1.425-0.713 2.040-1.44 2.040-1.857s-0.615-1.144-2.040-1.857c-1.845-0.923-4.463-1.476-7.293-1.476s-5.448 0.553-7.293 1.476c-1.425 0.713-2.040 1.44-2.040 1.857s0.615 1.144 2.040 1.857c1.845 0.923 4.463 1.476 7.293 1.476z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-input
                  v-model="form.samplingPlace"
                  clearable
                  placeholder="请输入采集位置"
                ></el-input>
              </el-form-item>
              <el-form-item class="dimensionality" label="采集地经度">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M1 12C1 5.92487 5.92487 1 12 1C18.0751 1 23 5.92487 23 12C23 18.0751 18.0751 23 12 23C5.92487 23 1 18.0751 1 12ZM12 4C7.58172 4 4 7.58172 4 12H8.71429C11.0812 12 13 13.9188 13 16.2857C13 16.6802 12.6802 17 12.2857 17C12.2857 17 10.5 17 10 17C9.5 17 9.08907 17.5 9.08907 18.5C9.08907 19.5 10 20 10.5 20C11 20 11.3094 20 12 20C16.0015 20 19.3169 17.0621 19.9067 13.2256C19.2107 13.716 18.3852 14 17.5 14C15.6547 14 14.0688 12.7659 13.3744 11H10C9.44771 11 8.99034 10.5486 9.08907 10.0052C9.60145 7.18519 11.5 6 13.8669 6.0491C14.0571 5.76054 14.2744 5.49548 14.5147 5.25857C14.7976 4.97973 14.7477 4.47377 14.3682 4.35634C13.6199 4.12473 12.8245 4 12 4Z"
                    fill="rgba(134, 117, 117, 1)"
                    stroke="none"
                  ></path>
                </svg>
                <el-input
                  v-model="form.longitude1"
                  placeholder="度"
                  type="number"
                  oninput="value=value.replace(/[^\d]/g,'')"
                ></el-input>
                <el-input
                  v-model="form.longitude2"
                  placeholder="分"
                  type="number"
                  oninput="value=value.replace(/[^\d]/g,'')"
                ></el-input>
                <el-input
                  v-model="form.longitude3"
                  placeholder="秒"
                  type="number"
                  oninput="value=value.replace(/[^\d]/g,'')"
                ></el-input>
              </el-form-item>
              <el-form-item class="dimensionality" label="采集地纬度">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    fill-rule="evenodd"
                    clip-rule="evenodd"
                    d="M1 12C1 5.92487 5.92487 1 12 1C18.0751 1 23 5.92487 23 12C23 18.0751 18.0751 23 12 23C5.92487 23 1 18.0751 1 12ZM12 4C7.58172 4 4 7.58172 4 12H8.71429C11.0812 12 13 13.9188 13 16.2857C13 16.6802 12.6802 17 12.2857 17C12.2857 17 10.5 17 10 17C9.5 17 9.08907 17.5 9.08907 18.5C9.08907 19.5 10 20 10.5 20C11 20 11.3094 20 12 20C16.0015 20 19.3169 17.0621 19.9067 13.2256C19.2107 13.716 18.3852 14 17.5 14C15.6547 14 14.0688 12.7659 13.3744 11H10C9.44771 11 8.99034 10.5486 9.08907 10.0052C9.60145 7.18519 11.5 6 13.8669 6.0491C14.0571 5.76054 14.2744 5.49548 14.5147 5.25857C14.7976 4.97973 14.7477 4.47377 14.3682 4.35634C13.6199 4.12473 12.8245 4 12 4Z"
                    fill="rgba(134, 117, 117, 1)"
                    stroke="none"
                  ></path>
                </svg>
                <el-input
                  v-model="form.dimensionality1"
                  placeholder="度"
                  type="number"
                  oninput="value=value.replace(/[^\d]/g,'')"
                ></el-input>
                <el-input
                  v-model="form.dimensionality2"
                  placeholder="分"
                  type="number"
                  oninput="value=value.replace(/[^\d]/g,'')"
                ></el-input>
                <el-input
                  v-model="form.dimensionality3"
                  placeholder="秒"
                  type="number"
                  oninput="value=value.replace(/[^\d]/g,'')"
                ></el-input>
              </el-form-item>
              <el-form-item label="采集时间">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M8 2h2v2h-2v-2zM20 2h2v2h-2v-2zM26 4h-4v2h-2v-2h-10v2h-2v-2h-6v24h26v-24h-2zM26 26h-22v-14h22v14z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                  <path
                    d="M14 14h2v2h-2v-2zM10 14h2v2h-2v-2zM18 14h2v2h-2v-2zM22 14h2v2h-2v-2zM8 18h2v2h-2v-2zM12 18h2v2h-2v-2zM16 18h2v2h-2v-2zM20 18h2v2h-2v-2zM6 22h2v2h-2v-2zM10 22h2v2h-2v-2zM14 22h2v2h-2v-2zM18 22h2v2h-2v-2z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-date-picker
                  type="datetime"
                  :picker-options="pickerOptions0"
                  placeholder="请选择采集时间"
                  v-model="form.samplingTime"
                  style="width: 100%"
                ></el-date-picker>
              </el-form-item>
              <el-form-item label="制样时间">
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 32 32"
                  width="24"
                  height="24"
                  style="
                    border-color: rgba(0, 0, 0, 0);
                    border-width: bpx;
                    border-style: undefined;
                  "
                  filter="none"
                >
                  <path
                    d="M8 2h2v2h-2v-2zM20 2h2v2h-2v-2zM26 4h-4v2h-2v-2h-10v2h-2v-2h-6v24h26v-24h-2zM26 26h-22v-14h22v14z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                  <path
                    d="M14 14h2v2h-2v-2zM10 14h2v2h-2v-2zM18 14h2v2h-2v-2zM22 14h2v2h-2v-2zM8 18h2v2h-2v-2zM12 18h2v2h-2v-2zM16 18h2v2h-2v-2zM20 18h2v2h-2v-2zM6 22h2v2h-2v-2zM10 22h2v2h-2v-2zM14 22h2v2h-2v-2zM18 22h2v2h-2v-2z"
                    fill="rgba(134, 117, 117, 1)"
                  ></path>
                </svg>
                <el-date-picker
                  type="datetime"
                  :picker-options="pickerOptions0"
                  placeholder="请选择制样时间"
                  v-model="form.samplePreparationTime"
                  style="width: 100%"
                ></el-date-picker>
              </el-form-item>
            </el-form>
          </div>
        </div>
        <span slot="footer" class="dialog-footer">
          <el-button type="primary" :disabled="!canSubmit" @click="onSubmit" class="form-submit"
            >提交</el-button
          >
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import mixins from "@/mixins";
import { mapState } from "vuex";
export default {
  data() {
    return {
      input: "",
      select: "",
      dialogVisible: false,
      form: {
        depthOrHeight: "HEIGHT",
      },
      pageNum: 1,
      fieldName: "",
      isAsc: false,
      loading: false,
      pickerOptions0: {
        disabledDate(time) {
          return time.getTime() > Date.now() - 8.64e6;
        },
      },
    };
  },
  computed: {
    ...mapState("Sample", ["tableData", "total", "current", "pages"]),
  },
  watch: {
    form: {
      handler(nV, oV) {
        if (
          nV.diggings &&
          nV.mineralDensity &&
          nV.datumline &&
          nV.samplingPlace &&
          nV.longitude1 &&
          nV.longitude2 &&
          nV.longitude3 &&
          nV.dimensionality1 &&
          nV.dimensionality2 &&
          nV.dimensionality3
        ) {
          this.canSubmit = true;
        } else {
          this.canSubmit = false;
        }
      },
      immediate: true,
    },
  },
  methods: {
    // 导出excel表
    onExport() {
      let data = {
        search: this.input,
      };
      this.$store.dispatch("Sample/exportExcel", data).then((res) => {
        let url = window.URL.createObjectURL(res);
        let a = document.createElement("a");
        a.href = url;
        a.click();
        window.URL.revokeObjectURL(url);
      });
    },
    // 排序
    onSortChange(column) {
      this.fieldName =
        column.prop == "diggingsWell.mineralDensity"
          ? "mineralDensity"
          : "createTime";
      this.isAsc = column.order == "ascending";
      this.getTableList();
    },
    // 确认删除
    yesDelete(id) {
      this.deleteList(id);
    },
    // 删除
    async deleteList(id) {
      let data = {
        sampleNo: id,
      };
      await this.$store.dispatch("Sample/deleteSample", data);
      this.$message({
        message: "删除成功",
        type: "success",
      });
      this.getTableList();
    },
    // 上一页
    toPrev() {
      this.pageNum -= 1;
      this.getTableList();
    },
    // 下一页
    toNext() {
      this.pageNum += 1;
      this.getTableList();
    },
    // 关闭form遮罩层
    handleClose() {
      this.dialogVisible = false;
      this.form = {
        depthOrHeight: "HEIGHT",
      };
    },
    // form提交
    async onSubmit() {
      this.loading = true;
      let form = {
        ...this.form,
        samplingTime: this.formatDate(this.form.samplingTime),
        samplePreparationTime: this.formatDate(this.form.samplePreparationTime),
        well: this.form.samplingPlace,
        longitude:
          this.form.longitude1 +
          "," +
          this.form.longitude2 +
          "," +
          this.form.longitude3,
        dimensionality:
          this.form.dimensionality1 +
          "," +
          this.form.dimensionality2 +
          "," +
          this.form.dimensionality3,
      };
      await this.$store.dispatch("Sample/insertDiggWell", form);
      this.loading = false;
      this.$message({
        message: "新增采集位置成功",
        type: "success",
      });
      this.dialogVisible = false;
    },
    // 跳转仪表盘查看详情
    doBrowser(row, index, h) {
      this.$router.push({
        name: "Dashboard",
        params: {
          sampleNo: row.sampleNo,
          index: index + 1 + 5 * (this.current - 1),
        },
      });
    },
    // 修改
    editList(row, h) {
      this.$router.push({ name: "Form", params: { row, h } });
    },
    // 表头样式
    headClass() {
      return "background:#e8e8e8;text-align:center";
    },
    // 表格样式
    rowClass() {
      return "text-align:center";
    },
    // 时间格式化
    formatDate(date) {
      return this.handleDate(date);
    },
    // 搜索
    doSearch() {
      this.pageNum = 1;
      this.getTableList();
    },
    // 清空搜索
    onClear() {
      this.getTableList();
    },
    // 渲染列表
    getTableList() {
      let search = "";
      if (this.input.trim()) {
        search = this.input.trim();
      }
      let data = {
        search,
        pageNum: this.pageNum,
        pageSize: 5,
        fieldName: this.fieldName,
        isAsc: this.isAsc,
      };
      this.$store.dispatch("Sample/getListByMutiType", data);
    },
  },
  mixins: [mixins],
  mounted() {
    this.getTableList();
  },
};
</script>

<style>
.s-body {
  width: 1320px;
  height: 610px;
  border: 2px solid #bbbbbb;
  box-shadow: #d6d6d6 0 0 4px;
  position: relative;
}

.s-header {
  width: 100%;
  height: 40px;
  margin-top: 20px;
}

.s-header-add {
  margin-left: 20px;
  float: left;
}

.s-header-add .el-button {
  width: 150px;
}

.s-header-search {
  float: left;
  margin-left: 100px;
}

.s-header-search .el-input {
  width: 100% !important;
}

.el-select .el-input {
  width: 130px;
}
.input-with-select .el-input-group__prepend {
  background-color: #fff;
}

.s-header-total {
  height: 40px;
  line-height: 40px;
  font-size: 14px;
  color: #000;
  margin-right: 30px;
  float: right;
}

.s-header-total span {
  color: #228edc;
}

.s-header-page {
  float: right;
  width: 300px;
  height: 40px;
  margin-right: 20px;
}

.s-header-page-span {
  float: left;
  width: 100px;
  height: 40px;
  text-align: center;
  line-height: 40px;
}

.s-header-page-span span {
  color: #228edc;
}

.toPrev {
  width: 100px;
  float: left;
}

.toNext {
  width: 100px;
  float: right;
}

.s-table {
  width: 1280px;
  height: auto;
  margin: 30px 20px 0;
}

.s-footer {
  width: 100%;
  height: 40px;
  position: absolute;
  bottom: 20px;
}

.s-footer-icon {
  width: 300px;
  height: 40px;
  float: left;
  margin-left: 20px;
}

.s-header-filter {
  width: 210px;
  margin-left: 100px;
  float: left;
}

.s-header-filter .el-select {
  margin-left: 0 !important;
}

.s-header-filter .el-select .el-input {
  width: 130px !important;
  margin-left: 0 !important;
}

.dialog-form {
  width: 720px;
  height: 400px;
  background-color: #eee0e0;
  position: relative;
}

.s-body .el-dialog__body,
.s-body .el-dialog__header,
.s-body .el-dialog__footer {
  background-color: #eee0e0;
}

.dia-top {
  width: 700px;
  height: 30px;
  margin: 0 auto;
  border-bottom: 1px solid #bbbbbb;
}

.d-t-left {
  width: 200px;
  height: 30px;
  float: left;
  margin-left: 50px;
}

.d-t-right {
  width: 200px;
  height: 30px;
  float: right;
  margin-right: 50px;
}

.d-t-left img,
.d-t-right img {
  width: 30px;
  height: 30px;
  float: left;
}

.d-t-left span,
.d-t-right span {
  font-size: 20px;
  line-height: 30px;
  color: #101010;
  float: left;
  margin-left: 10px;
}

.dia-lform {
  width: 300px;
  height: 350px;
  position: absolute;
  left: 0;
  top: 50px;
}

.dia-rform {
  width: 300px;
  height: 350px;
  position: absolute;
  right: 0;
  top: 50px;
}

.s-body .el-input {
  width: 60% !important;
}

.s-body .el-form-item {
  margin-bottom: 10px !important;
  position: relative;
  margin-bottom: 15px !important;
  border-bottom: 1px solid #bbbbbb;
  padding-bottom: 15px;
}

.s-body .el-form-item svg {
  position: absolute !important;
  left: -10px !important;
  top: 8px !important;
}

.el-form-item__label {
  font-size: 14px;
  font-weight: bold;
  color: #796e58;
  width: 105px;
  text-align: left !important;
  text-indent: 20px;
}

.dia-swap {
  width: 50px;
  height: 50px;
  position: absolute;
  left: 0;
  bottom: 0;
  right: 0;
  top: 0;
  margin: auto;
}

.dia-swap img {
  width: 50px;
  height: 50px;
}

.s-body .form-submit {
  width: 170px;
  height: 30px;
  border-radius: 30px;
  line-height: 5px !important;
  /* background-color: #514e4e !important; */
  /* border-color: #514e4e !important; */
  position: absolute;
  left: 50%;
  margin-left: -85px;
  bottom: 15px;
}

.sampleCodeSpan {
  display: inline-block;
  width: 133px;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
  word-break: break-all;
}

.dialog-form .el-loading-mask {
  top: -60px !important;
  right: -29px !important;
  bottom: -60px !important;
  left: -20px !important;
}

.dimensionality .el-input {
  width: 20% !important;
}

.dimensionality .el-input--suffix.el-input__inner {
  padding-right: 0 !important;
}

.s-table .el-button--text {
  margin-bottom: 6px !important;
}
</style>