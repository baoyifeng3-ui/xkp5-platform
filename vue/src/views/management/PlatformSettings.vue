<template>
  <section class="module-page platform-settings-page">
    <header class="module-heading">
      <div><h1>平台设置</h1><p>维护全平台品牌信息、主题色与登录页展示。</p></div>
    </header>

    <div v-if="loadError" class="settings-error" role="alert">
      <span>平台设置加载失败，保存操作已禁用。</span>
      <el-button size="small" icon="el-icon-refresh" @click="loadPlatformSettings">重新加载</el-button>
    </div>

    <el-form v-loading="loading" :disabled="!initialized || loading" ref="platformForm" :model="platformForm" :rules="platformRules" label-position="top" class="settings-form">
      <el-form-item label="平台名称" prop="platformName">
        <el-input v-model="platformForm.platformName" maxlength="30" show-word-limit placeholder="请输入平台名称" @input="platformEditing = true" />
      </el-form-item>
      <el-form-item label="主题色"><el-color-picker v-model="platformForm.themeColor" @change="platformEditing = true" /></el-form-item>
      <el-form-item label="平台 Logo">
        <el-upload action="#" accept="image/jpeg,image/png,image/webp,image/svg+xml" :auto-upload="false" :show-file-list="false" :on-change="selectPlatformLogo" :disabled="logoUploading || !initialized || loading">
          <el-button icon="el-icon-picture" :loading="logoUploading">选择 Logo</el-button>
        </el-upload>
        <div v-if="platformForm.platformLogoUrl" class="logo-preview"><img :src="platformForm.platformLogoUrl" alt="平台 Logo 预览" @error="assetLoadError('Logo')" /></div>
        <el-button v-if="platformForm.platformLogoUrl" type="text" icon="el-icon-delete" @click="removePlatformLogo">移除 Logo</el-button>
      </el-form-item>
      <el-form-item label="登录页顶部名称"><el-input v-model="platformForm.loginBrandName" maxlength="60" show-word-limit @input="platformEditing = true" /></el-form-item>
      <el-form-item label="登录页英文副标题"><el-input v-model="platformForm.loginEnglishSubtitle" maxlength="100" show-word-limit @input="platformEditing = true" /></el-form-item>
      <el-form-item label="登录页主标题"><el-input v-model="platformForm.loginTitle" maxlength="60" show-word-limit @input="platformEditing = true" /></el-form-item>
      <el-form-item label="登录页说明文字"><el-input v-model="platformForm.loginDescription" type="textarea" :rows="3" maxlength="300" show-word-limit @input="platformEditing = true" /></el-form-item>
      <el-form-item label="登录页版权文字"><el-input v-model="platformForm.loginCopyright" maxlength="100" show-word-limit @input="platformEditing = true" /></el-form-item>
      <el-form-item label="登录页面背景图片">
        <el-upload action="#" accept="image/jpeg,image/png,image/webp" :auto-upload="false" :show-file-list="false" :on-change="selectLoginBackground" :disabled="backgroundUploading || !initialized || loading">
          <el-button icon="el-icon-picture-outline" :loading="backgroundUploading">选择本地图片</el-button>
        </el-upload>
        <div v-if="platformForm.loginBackgroundUrl" class="background-preview"><img :src="platformForm.loginBackgroundUrl" alt="登录背景预览" @error="assetLoadError('背景图片')" /></div>
        <el-button v-if="platformForm.loginBackgroundUrl" type="text" icon="el-icon-delete" @click="removeLoginBackground">移除背景图片</el-button>
      </el-form-item>
      <div class="settings-actions">
        <el-button icon="el-icon-refresh-left" :disabled="platformSaving || platformForm.themeColor === defaultThemeColor" @click="restoreDefaultTheme">恢复默认主题色</el-button>
        <el-button type="primary" icon="el-icon-check" :loading="platformSaving" :disabled="platformSaving || !initialized || loading" @click="savePlatformSettings">保存设置</el-button>
      </div>
    </el-form>
  </section>
</template>

<script>
import { competitionApi, updatePlatformSettingsApi, uploadLoginBackgroundApi, uploadPlatformLogoApi } from '@/api/Match'

const DEFAULT_THEME_COLOR = '#6f7ff7'

export default {
  name: 'PlatformSettings',
  data () {
    return {
      platformForm: { platformName: '', themeColor: DEFAULT_THEME_COLOR, loginBackgroundUrl: '', platformLogoUrl: '', loginEnglishSubtitle: '', loginBrandName: '', loginTitle: '', loginDescription: '', loginCopyright: '' },
      defaultThemeColor: DEFAULT_THEME_COLOR,
      loading: true,
      loadError: false,
      initialized: false,
      platformSaving: false,
      platformEditing: false,
      backgroundUploading: false,
      logoUploading: false,
      platformRules: {
        platformName: [{
          validator: (rule, value, callback) => {
            const name = String(value || '').trim()
            if (!name) callback(new Error('请输入平台名称'))
            else if (name.length > 30) callback(new Error('平台名称不能超过 30 个字符'))
            else callback()
          },
          trigger: 'blur'
        }]
      }
    }
  },
  computed: {
    platformName () { return this.$store.state.Match.platformName || '数据杯管理台' }
  },
  created () { this.loadPlatformSettings() },
  methods: {
    async loadPlatformSettings () {
      this.loading = true
      this.loadError = false
      this.initialized = false
      try {
        const result = await competitionApi()
        if (result.code !== 200 || !result.data) throw new Error('Platform settings response is unavailable')
        this.$store.commit('Match/SET_PLATFORM_SETTINGS', result.data)
        this.syncPlatformForm()
        this.platformEditing = false
        this.initialized = true
      } catch (error) {
        this.loadError = true
      } finally {
        this.loading = false
      }
    },
    syncPlatformForm () {
      const state = this.$store.state.Match
      this.platformForm = {
        platformName: this.platformName,
        themeColor: state.themeColor,
        loginBackgroundUrl: state.loginBackgroundUrl,
        platformLogoUrl: state.platformLogoUrl,
        loginEnglishSubtitle: state.loginEnglishSubtitle,
        loginBrandName: state.loginBrandName,
        loginTitle: state.loginTitle,
        loginDescription: state.loginDescription,
        loginCopyright: state.loginCopyright
      }
    },
    async savePlatformSettings () {
      if (!this.initialized) return
      const valid = await new Promise(resolve => this.$refs.platformForm.validate(resolve))
      if (!valid) return
      this.platformSaving = true
      try {
        const result = await updatePlatformSettingsApi({
          platformName: String(this.platformForm.platformName || '').trim(),
          themeColor: String(this.platformForm.themeColor || DEFAULT_THEME_COLOR).trim(),
          loginBackgroundUrl: String(this.platformForm.loginBackgroundUrl || '').trim(),
          platformLogoUrl: String(this.platformForm.platformLogoUrl || '').trim(),
          loginEnglishSubtitle: String(this.platformForm.loginEnglishSubtitle || '').trim(),
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
    removeLoginBackground () { this.platformForm.loginBackgroundUrl = ''; this.platformEditing = true },
    async selectPlatformLogo (uploadFile) {
      const file = uploadFile && uploadFile.raw
      if (!file) return
      if (!['image/jpeg', 'image/png', 'image/webp', 'image/svg+xml'].includes(file.type) || file.size > 2 * 1024 * 1024) return this.$message.error('Logo 仅支持 JPEG、PNG、WebP 或 SVG，且不能超过 2 MB')
      this.logoUploading = true
      try {
        const result = await uploadPlatformLogoApi(file)
        if (result.code === 200) { this.platformForm.platformLogoUrl = result.data.platformLogoUrl; this.platformEditing = true; this.$message.success('Logo 已上传，请保存设置') }
      } finally { this.logoUploading = false }
    },
    removePlatformLogo () { this.platformForm.platformLogoUrl = ''; this.platformEditing = true },
    assetLoadError (name) { this.$message.error(`${name}加载失败，请重新上传`) },
    restoreDefaultTheme () {
      this.platformForm.themeColor = DEFAULT_THEME_COLOR
      this.platformEditing = true
      this.$message.info('已恢复默认主题色，点击“保存设置”后生效')
    }
  }
}
</script>

<style scoped>
.settings-form { max-width: 840px; padding-top: 18px; border-top: 1px solid #dce4e9; }
.settings-error { display: flex; align-items: center; justify-content: space-between; gap: 16px; max-width: 840px; margin-bottom: 14px; padding: 10px 12px; color: #934444; background: #fff7f7; border: 1px solid #ead5d5; border-radius: 5px; box-sizing: border-box; }
.settings-form::v-deep .el-form-item { margin-bottom: 22px; }
.settings-form::v-deep .el-form-item__label { color: #405267; font-size: 13px; font-weight: 600; }
.background-preview { width: 100%; max-width: 360px; height: 120px; margin-top: 10px; overflow: hidden; background: #f3f5f6; border: 1px solid #e3e8eb; border-radius: 6px; }
.background-preview img { width: 100%; height: 100%; object-fit: cover; }
.logo-preview { display: grid; place-items: center; width: 120px; height: 80px; margin-top: 10px; background: #f3f5f6; border: 1px solid #e3e8eb; border-radius: 6px; }
.logo-preview img { max-width: 100px; max-height: 64px; object-fit: contain; }
.settings-actions { display: flex; justify-content: flex-end; gap: 10px; }
.settings-actions .el-button + .el-button { margin-left: 0; }
@media (max-width: 600px) { .settings-actions { align-items: stretch; flex-direction: column-reverse; } .settings-actions .el-button { width: 100%; margin: 0; } }
</style>
