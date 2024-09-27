/* eslint-disable @typescript-eslint/no-explicit-any */
import Vue, {inject} from "vue";
import VueRouter from "vue-router";
import GoogleAnalytics from "@/services/GoogleAnalytics";
import App from "@/App.vue";

Vue.use(VueRouter);

const routes = [
  {
    path: "/",
    name: "home",
    component: () => import("@/views/Workspaces.vue")
  },
  {
    path: "/workspaces/:workspaceId/components/:componentId",
    name: "edit-component",
    component: () => import("@/views/EditComponentView.vue"),
    props: true
  },
  {
    path: "/users",
    name: "users",
    component: () => import("@/views/Users.vue")
  },
  {
    path: "/about",
    name: "about",
    component: () => import("@/views/About.vue")
  },
  {
    path: "/licensing",
    name: "licensing",
    component: () => import("@/views/Licensing.vue")
  },
  {
    path: "/profile",
    name: "profile",
    component: () => import("@/views/UserProfile.vue")
  },
  {
    path: "/data-stores",
    name: "data-stores",
    component: () => import("@/views/DataStores.vue")
  },
  {
    path: "/pql-interpreter",
    name: "pql-interpreter",
    component: () => import("@/views/PQL.vue")
  },
  {
    path: "/pql-docs",
    name: "pql-docs",
    component: () => import("@/views/PQLDocs.vue")
  },
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/Login.vue"),
    beforeEnter: (_to: any, _from: any, next: any) => {
      Vue.prototype.$sessionStorage.sessionExists ? next("/") : next();
    },
    meta: { allowUnauthenticated: true }
  },
  {
    path: "/register",
    name: "register",
    component: () => import("@/views/Registration.vue"),
    beforeEnter: (_to: any, _from: any, next: any) => {
      Vue.prototype.$sessionStorage.sessionExists ? next("/") : next();
    },
    meta: { allowUnauthenticated: true }
  },
  {
    path: "/reset-password",
    name: "reset-password-request",
    component: () => import("@/views/ResetPasswordRequest.vue"),
    beforeEnter: (_to: any, _from: any, next: any) => {
      Vue.prototype.$sessionStorage.sessionExists ? next("/") : next();
    },
    meta: { allowUnauthenticated: true }
  },
  {
    path: "/reset-password/:token",
    name: "reset-password",
    component: () => import("@/views/ResetPassword.vue"),
    beforeEnter: (_to: any, _from: any, next: any) => {
      Vue.prototype.$sessionStorage.sessionExists ? next("/") : next();
    },
    meta: { allowUnauthenticated: true }
  }
];

const router = new VueRouter({
  routes
});

router.beforeEach((to, _from, next) => {
  GoogleAnalytics.instance?.page_view(to.path);
  Vue.prototype.$sessionStorage.sessionExists || to.matched.some((record) => record.meta.allowUnauthenticated) ? next() : next({name: "login"});
});

export default router;
