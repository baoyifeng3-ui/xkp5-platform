<template>
    <div :class="['login-box', { 'has-background': hasLoginBackground }]" :style="loginStyle">
        <header class="login-brand">
            <img v-if="platformLogoUrl" class="login-brand-logo" :src="platformLogoUrl" alt="平台 Logo">
            <span v-else class="login-brand-mark" aria-hidden="true">X</span>
            <div><strong>{{ loginBrandName }}</strong><small>{{ loginEnglishSubtitle }}</small></div>
        </header>
        <main class="login-shell">
            <el-card class="login-card" shadow="never">
                <div class="login-context">
                    <h1>{{ loginTitle }}</h1>
                    <p>{{ loginDescription }}</p>
                    <div :class="['login-status', `is-${serviceStatus}`]" role="status" aria-live="polite"><i aria-hidden="true" />{{ serviceStatusText }}</div>
                </div>
                <div class="login-card-heading"><span>账号登录</span><small>请使用已分配的平台账号</small></div>
                <el-form :model="ruleForm" :rules="rules" ref="ruleForm" label-position="top" class="login-form">
                    <el-form-item label="账号" prop="UserName">
                        <el-input v-model="ruleForm.UserName" prefix-icon="el-icon-user" autocomplete="username" placeholder="请输入账号" />
                    </el-form-item>
                    <el-form-item label="密码" prop="Password">
                        <el-input type="password" v-model="ruleForm.Password" prefix-icon="el-icon-lock" autocomplete="current-password" placeholder="请输入密码" show-password @keyup.native.enter="submitForm('ruleForm')" />
                    </el-form-item>
                    <div class="button-list">
                        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitForm('ruleForm')">登录</el-button>
                        <el-button :disabled="submitting" @click="resetForm('ruleForm')">重置</el-button>
                    </div>
                </el-form>
            </el-card>
        </main>
        <footer class="login-c">&copy; {{ loginCopyright }}</footer>
    </div>
</template>

<script>
import { competitionApi } from '@/api/Match'
const roleNavigation = require('@/navigation/roleNavigation')

export default {
    data () {
        return {
            ruleForm: { UserName: '', Password: '' },
            submitting: false,
            serviceStatus: 'checking',
            rules: {
                UserName: [
                    { required: true, message: "请输入账号", trigger: "blur" },
                ],
                Password: [{ required: true, message: "请填写密码", trigger: "blur" }],
            },
        };
    },
    computed: {
        platformLogoUrl () { return this.$store.state.Match.platformLogoUrl },
        loginEnglishSubtitle () { return this.$store.state.Match.loginEnglishSubtitle },
        loginBrandName () { return this.$store.state.Match.loginBrandName },
        loginTitle () { return this.$store.state.Match.loginTitle },
        loginDescription () { return this.$store.state.Match.loginDescription },
        loginCopyright () { return this.$store.state.Match.loginCopyright },
        serviceStatusText () { return ({ checking: '正在检查平台服务', ready: '平台服务就绪', unavailable: '平台服务暂不可用，可稍后重试' })[this.serviceStatus] },
        hasLoginBackground () { return Boolean(this.$store.state.Match.loginBackgroundUrl) },
        loginStyle () {
            const url = this.$store.state.Match.loginBackgroundUrl
            return url ? { '--login-background-image': `url("${url.replace(/"/g, '\\"')}")` } : {}
        }
    },
    methods: {
        submitForm (formName) {
            this.$refs[formName].validate(async (valid) => {
                if (valid) {
                    this.submitting = true;
                    try {
                        const res = await this.$store.dispatch("Match/userLogin", this.ruleForm);
                        if (!res) return;
                        const state = this.$store.state.Match;
                        if (state.mustChangePassword) {
                            this.$router.push({ path: "/change-password" }).catch(() => { });
                        } else {
                            this.$router.push(roleNavigation.landingRoute(res.role, res.platformMode)).catch(() => { });
                        }
                    } catch (error) {
                        // The shared request interceptor displays the login error.
                    } finally {
                        this.submitting = false;
                    }
                }
            });
        },
        resetForm (formName) {
            this.$refs[formName].resetFields();
        },
    },
    async mounted () {
        try {
            const result = await competitionApi()
            if (result.code === 200) { this.$store.commit('Match/SET_PLATFORM_SETTINGS', result.data); this.serviceStatus = 'ready' }
            else this.serviceStatus = 'unavailable'
        } catch (error) {
            this.serviceStatus = 'unavailable'
        }
    },
};
</script>

<style scoped>
@import url(../assets/style/login.css);
</style>
