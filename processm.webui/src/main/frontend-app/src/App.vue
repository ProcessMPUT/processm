<template>
  <v-app>
    <top-bar v-if="$sessionStorage.sessionExists" />

    <app-navigation v-if="$sessionStorage.sessionExists" />

    <v-main>
      <router-view />
    </v-main>

    <v-snackbar :color="snack.color" v-model="snack.visible" :timeout="snack.timeout">
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

  /**
   * @param locale Expected to follow RFC5646, because that's what web browsers seems to do
   * @return true if the locale was available and thus set; false otherwise
   */
  private setLocale(locale: string | undefined): boolean {
    console.debug("Trying to set locale to", locale);
    console.debug("Available", this.$i18n.availableLocales);
    if (locale == undefined) return false;
    // Use the exact match if possible
    if (this.$i18n.availableLocales.includes(locale)) {
      this.$i18n.locale = locale;
      console.debug("Successfully set locale to", locale);
      return true;
    }
    // If locale contains more information than the country code, strip it and search for the language without the country code etc.
    if (locale.includes("-")) {
      locale = locale.split("-")[0];
      if (this.$i18n.availableLocales.includes(locale)) {
        this.$i18n.locale = locale;
        console.debug("Successfully set locale to", locale);
        return true;
      }
    }
    // Search for locale with the same language, but a different country code
    const prefix = locale + "-";
    const candidate = this.$i18n.availableLocales.find((candidate) => {
      return candidate.startsWith(prefix);
    });
    if (candidate !== undefined) {
      this.$i18n.locale = candidate;
      console.debug("Successfully set locale to", candidate);
      return true;
    }
    return false;
  }

  /**
   * If available, sets the locale according to the preferences of the current user.
   * Otherwise, sets the locale to the first available language in the order of preference reported by the web browser
   */
  resetLocale() {
    if (this.setLocale(this.$sessionStorage.userInfo?.locale)) return;
    for (const language of navigator.languages) {
      if (this.setLocale(language)) return;
    }
  }

  async mounted() {
    this.resetLocale();
  }

  // Defining the empty _data fixes the TypeError: Cannot convert undefined or null to object in processState() (in Vue)
  // when rendering some view the second time.
  _data = {};

  private message(color: string, text: string, timeout = 10000) {
    if (text == "") return;
    this.snack.color = color;
    this.snack.message = text;
    this.snack.timeout = timeout;
    this.snack.visible = true;
  }

  success(text: string, timeout = 10000) {
    this.message("success", text, timeout);
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