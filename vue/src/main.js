import Vue from 'vue'
import AccountTemplateDialog from '@/components/accounts/AccountTemplateDialog.vue'
import AccountImportDialog from '@/components/accounts/AccountImportDialog.vue'
import App from './App.vue'
import router from './router'
import store from './store'

// element
import ElementUI from 'element-ui';
import 'element-ui/lib/theme-chalk/index.css';
ElementUI.Dialog.props.closeOnClickModal.default = false;
Vue.use(ElementUI);

// cookie
import VueCookie from 'vue-cookies'
Vue.use(VueCookie)

// axios
import axios from 'axios'
Vue.prototype.$axios = axios

Vue.config.productionTip = false
Vue.component('AccountTemplateDialog', AccountTemplateDialog)
Vue.component('AccountImportDialog', AccountImportDialog)

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')

