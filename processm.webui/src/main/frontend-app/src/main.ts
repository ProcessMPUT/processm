import Vue from "vue";
import App from "./App.vue";
import router from "./router";
import store from "./store";
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import Vuetify from "vuetify";
import vuetify from "./plugins/vuetify";
import "material-design-icons-iconfont/dist/material-design-icons.css";
import "typeface-roboto";
import i18n from "./i18n";
import WorkspaceService from "./services/WorkspaceService";

Vue.config.productionTip = false;

new Vue({
  router,
  store,
  vuetify,
  i18n,
  provide: () => ({
    workspaceService: new WorkspaceService()
  }),
  render: h => h(App)
}).$mount("#app");
