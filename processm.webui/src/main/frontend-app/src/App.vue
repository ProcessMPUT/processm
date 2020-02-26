<template>
  <v-app>
    <top-bar v-if="$sessionStorage.sessionExists" />

    <app-navigation v-if="$sessionStorage.sessionExists" />

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
export default class App extends Vue {}
</script>
