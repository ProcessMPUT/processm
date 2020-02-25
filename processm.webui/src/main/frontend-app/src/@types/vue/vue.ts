import Vue from "vue";
import SessionStorage from "@/plugins/SessionStorage";

declare module "vue/types/vue" {
  interface Vue {
    $sessionStorage: typeof SessionStorage;
  }
}
