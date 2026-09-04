<template>
  <el-dialog title="部署模型到 T100" :visible.sync="visible" width="640px">
    <p>模型文件部署到 <code>/usr/local/zy-T100/utils_x86/models/A</code>，配置文件部署到 <code>/usr/local/zy-T100/utils_x86</code>。</p>
    <el-form label-width="90px">
      <el-form-item label="模型文件">
        <el-select v-model="modelPath" filterable placeholder="请选择模型文件">
          <el-option v-for="file in files" :key="file" :label="file" :value="file" />
        </el-select>
      </el-form-item>
      <el-form-item label="配置文件">
        <el-select v-model="configPath" filterable placeholder="请选择配置文件">
          <el-option v-for="file in files" :key="file" :label="file" :value="file" />
        </el-select>
      </el-form-item>
      <el-form-item label="覆盖文件"><el-checkbox v-model="overwrite">目标同名文件存在时覆盖</el-checkbox></el-form-item>
    </el-form>
    <el-empty v-if="!loading && !files.length" description="/home/student 暂无可部署文件" />
    <span slot="footer">
      <el-button @click="visible=false">取消</el-button>
      <el-button type="primary" :loading="loading || deploying" :disabled="!modelPath || !configPath" @click="deploy">确认部署</el-button>
    </span>
  </el-dialog>
</template>
<script>
import { listTrainingModelFiles, deployTrainingModel, trainingModelCommand } from '@/api/TrainingModels'
const done = state => ['SUCCEEDED', 'FAILED'].includes(state)
export default {
  data: () => ({ visible: false, environment: null, files: [], modelPath: '', configPath: '', overwrite: false, loading: false, deploying: false }),
  methods: {
    async open (environment) {
      this.environment = environment
      this.files = []
      this.modelPath = ''
      this.configPath = ''
      this.overwrite = false
      this.visible = true
      this.loading = true
      try {
        const task = await listTrainingModelFiles(environment.environmentId)
        const result = await this.wait(task.data.commandId)
        this.files = (result.details && result.details.files) || []
      } catch (error) { this.$message.error(error.message || '读取模型文件失败') } finally { this.loading = false }
    },
    async deploy () {
      this.deploying = true
      try {
        const task = await deployTrainingModel(this.environment.environmentId, {
          modelPath: this.modelPath, configPath: this.configPath, overwrite: this.overwrite
        })
        await this.wait(task.data.commandId)
        this.$message.success('模型和配置文件已部署到 T100')
        this.visible = false
      } catch (error) { this.$message.error(error.message || '模型部署失败') } finally { this.deploying = false }
    },
    async wait (commandId) {
      for (let i = 0; i < 60; i++) {
        const response = await trainingModelCommand(commandId)
        if (done(response.data.state)) {
          if (response.data.state === 'FAILED') throw new Error(response.data.resultMessage || '模型部署失败')
          return response.data
        }
        await new Promise(resolve => setTimeout(resolve, 500))
      }
      throw new Error('模型部署任务超时')
    }
  }
}
</script>
<style scoped>.el-select{width:100%}code{color:#285d8f}</style>
