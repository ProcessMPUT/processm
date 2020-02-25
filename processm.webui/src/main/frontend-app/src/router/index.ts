/* eslint-disable @typescript-eslint/no-explicit-any */
import Vue from "vue";
import VueRouter from "vue-router";

Vue.use(VueRouter);

const routes = [
  {
    path: "/",
    component: () => import("@/views/Workspaces.vue")
  },
  {
    path: "/about",
    component: () => import("@/views/About.vue")
  },
  {
    path: "/login",
    component: () => import("@/views/Login.vue"),
    beforeEnter: (_to: any, _from: any, next: any) => {
      Vue.prototype.$sessionStorage.sessionExists() ? next("/") : next();
    },
    meta: { allowUnauthenticated: true }
  }
];

const router = new VueRouter({
  routes
});

router.beforeEach((to, _from, next) => {
  Vue.prototype.$sessionStorage.sessionExists() ||
  to.matched.some(record => record.meta.allowUnauthenticated)
    ? next()
    : next("/login");
});

export default router;
