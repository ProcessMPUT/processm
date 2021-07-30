<template>
  <v-app>
    <top-bar v-if="$sessionStorage.sessionExists" />

    <app-navigation v-if="$sessionStorage.sessionExists" />

    <v-main>
      <router-view />
    </v-main>

    <v-snackbar
      :color="snack.color"
      v-model="snack.visible"
      :timeout="snack.timeout"
    >
      {{ snack.message }}
      <template v-slot:action="{ attrs }">
        <v-btn text v-bind="attrs" @click="snack.visible = false">
          {{ $t("common.close") }}
        </v-btn>
      </template>
    </v-snackbar>
  </v-app>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import AppNavigation from "@/components/AppNavigation.vue";
import TopBar from "@/components/TopBar.vue";
import { Provide } from "vue-property-decorator";

@Component({
  components: { AppNavigation, TopBar }
})
export default class App extends Vue {
  @Provide("app") app = this;
  snack = {
    color: "info",
    message: "",
    visible: false,
    timeout: 10000
  };

  private message(color: string, text: string, timeout = 10000) {
    if (text == "") return;
    this.snack.color = color;
    this.snack.message = text;
    this.snack.timeout = timeout;
    this.snack.visible = true;
  }

  info(text: string, timeout = 10000) {
    this.message("info", text, timeout);
  }

  warn(text: string, timeout = 10000) {
    this.message("warning", text, timeout);
  }

  error(text: string, timeout = 10000) {
    this.message("error", text, timeout);
  }
}
</script>
