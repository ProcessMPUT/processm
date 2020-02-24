<template>
  <v-app>
    <app-navigation v-if="!$route.meta.allowUnauthenticated" />

    <v-content>
      <router-view />
    </v-content>
  </v-app>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import AppNavigation from "@/components/AppNavigation.vue";

@Component({
  components: { AppNavigation }
})
export default class App extends Vue {}
</script>
