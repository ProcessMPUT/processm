import Vue from "vue";
import App from "@/App.vue";
import router from "@/router";
import store from "@/store";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import Vuetify from "vuetify";
import vuetify from "@/plugins/vuetify";
import "material-design-icons-iconfont/dist/material-design-icons.css";
import "typeface-roboto";
import "flag-icon-css/css/flag-icon.min.css";
import i18n from "@/i18n";
import SessionStorage from "@/plugins/SessionStorage";
import WorkspaceService from "@/services/WorkspaceService";
import AccountService from "@/services/AccountService";
import OrganizationService from "@/services/OrganizationService";
import DataSourceService from "@/services/DataSourceService";

Vue.config.productionTip = false;
Vue.use(SessionStorage, { persist: true });

new Vue({
  router,
  store,
  vuetify,
  i18n,
  provide: () => ({
    workspaceService: new WorkspaceService(),
    dataSourceService: new DataSourceService(),
    accountService: new AccountService(),
    organizationService: new OrganizationService()
  }),
  render: (h) => h(App)
}).$mount("#app");
