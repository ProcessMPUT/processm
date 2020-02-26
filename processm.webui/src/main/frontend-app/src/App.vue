<template>
  <v-app>
    <top-bar v-if="!isPublicView" />

    <app-navigation v-if="!isPublicView" />

    <v-content>
      <router-view />
    </v-content>
  </v-app>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import AppNavigation from "@/components/AppNavigation.vue";
import TopBar from "@/components/TopBar.vue";

@Component({
  components: { AppNavigation, TopBar }
})
export default class App extends Vue {
  get isPublicView(): boolean {
    return this.$route.meta.allowUnauthenticated;
  }
}
</script>
