import Vue from "vue";
import App from "@/App.vue";
import router from "@/router";
import store from "@/store";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import vuetify from "@/plugins/vuetify";
import "material-design-icons-iconfont/dist/material-design-icons.css";
import "typeface-roboto";
import "flag-icon-css/css/flag-icon.min.css";
import i18n from "@/i18n";
import SessionStorage from "@/plugins/SessionStorage";
import WorkspaceService from "@/services/WorkspaceService";
import AccountService from "@/services/AccountService";
import OrganizationService from "@/services/OrganizationService";
import DataStoreService from "@/services/DataStoreService";
import LogsService from "./services/LogsService";
import ConfigService from "@/services/ConfigService";
import VuetifyConfirm from "vuetify-confirm";
import ACLService from "@/services/ACLService";
import GroupService from "@/services/GroupService";
import NotificationService from "@/services/NotificationService";

Vue.config.productionTip = false;
Vue.use(SessionStorage, { persist: true });
Vue.use(VuetifyConfirm, { vuetify });

new Vue({
  router,
  store,
  vuetify,
  i18n,
  provide: () => ({
    configService: new ConfigService(),
    workspaceService: new WorkspaceService(),
    dataStoreService: new DataStoreService(),
    accountService: new AccountService(),
    groupService: new GroupService(),
    organizationService: new OrganizationService(),
    logsService: new LogsService(),
    aclService: new ACLService(),
    notificationService: new NotificationService()
  }),
  render: (h) => h(App)
}).$mount("#app");
