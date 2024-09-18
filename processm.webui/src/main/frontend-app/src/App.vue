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
    <v-footer :app="true">
      <v-btn type="button" data-cc="show-preferencesModal" x-small color="secondary">{{ this.$t("cookies.openDialog") }}</v-btn>
    </v-footer>
  </v-app>
</template>

<script lang="ts">
import Vue, { reactive } from "vue";
import Component from "vue-class-component";
import AppNavigation from "@/components/AppNavigation.vue";
import TopBar from "@/components/TopBar.vue";
import { Inject, Provide } from "vue-property-decorator";
import NotificationService, { ComponentUpdatedEvent } from "@/services/NotificationService";
import { NotificationsObserver } from "@/utils/NotificationsObserver";
import { Config } from "@/openapi";
import ConfigService from "@/services/ConfigService";
import "vanilla-cookieconsent";
// Beware. IntelliJ claims the next line can be simplified to `import CookieConsent from "vanilla-cookieconsent"`. It cannot.
import * as CookieConsent from "vanilla-cookieconsent";
import { Translation } from "vanilla-cookieconsent";
import "vanilla-cookieconsent/dist/cookieconsent.css";

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

  @Inject() configService!: ConfigService;
  @Inject() notificationService!: NotificationService;
  notifications: NotificationsObserver | undefined = undefined;

  lastEvent = reactive({ lastEvent: null } as { lastEvent: ComponentUpdatedEvent | null });

  config: Config = reactive({
    brand: "",
    version: "",
    loginMessage: "",
    demoMode: false
  });

  /**
   * @param locale Expected to follow RFC5646, because that's what web browsers seems to do
   * @return true if the locale was available and thus set; false otherwise
   */
  setLocale(locale: string | undefined): boolean {
    console.debug("Trying to set locale to", locale);
    console.debug("Available", this.$i18n.availableLocales);
    if (locale == undefined) return false;
    // Use the exact match if possible
    if (this.$i18n.availableLocales.includes(locale)) {
      this.$i18n.locale = locale;
      this.updateCookieConsentDialog();
      console.debug("Successfully set locale to", locale);
      return true;
    }
    // If locale contains more information than the country code, strip it and search for the language without the country code etc.
    if (locale.includes("-")) {
      locale = locale.split("-")[0];
      if (this.$i18n.availableLocales.includes(locale)) {
        this.$i18n.locale = locale;
        this.updateCookieConsentDialog();
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
      this.updateCookieConsentDialog();
      console.debug("Successfully set locale to", candidate);
      return true;
    }
    return false;
  }

  private updateCookieConsentDialog() {
    CookieConsent.setLanguage(this.$i18n.locale);
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
    const translations: Record<string, () => Translation> = {};
    // Every function to retrieve locale is the same, but it is called with a different value of this.$i18n.locale set, and thus retrieves different locale. Ugly, but seems to work.
    for (const locale of this.$i18n.availableLocales) {
      translations[locale] = () => {
        return (this.$t("cookies") as unknown) as Translation;
      };
    }
    await CookieConsent.run({
      guiOptions: {
        consentModal: {
          layout: "box",
          position: "bottom left",
          equalWeightButtons: true,
          flipButtons: false
        },
        preferencesModal: {
          layout: "box",
          position: "right",
          equalWeightButtons: true,
          flipButtons: false
        }
      },
      categories: {
        necessary: {
          readOnly: true
        }
        // Uncomment the last line in #318 and complete the necessary translation using the following template:
        // {
        //   "title": "Analytics Cookies",
        //   "description": "This is to be filled in #318",
        //   "linkedCategory": "analytics"
        // },
        //analytics: {}
      },
      language: {
        default: this.$i18n.locale,
        autoDetect: "browser",
        translations: translations
      }
    });
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

  async created() {
    Object.assign(this.config, await this.configService.getConfig());

    if (this.notifications === undefined)
      this.notifications = this.notificationService.subscribe(async (event) => {
        this.lastEvent.lastEvent = event;
        if (event.changeType != "Model") return;
        const goToModel = await this.$confirm(
          this.$t("notifications.new-model-available.text", {
            workspaceName: event.workspaceName,
            componentName: event.componentName
          }).toString(),
          {
            title: this.$t("notifications.new-model-available.title").toString(),
            buttonTrueText: this.$t("common.yes").toString(),
            buttonFalseText: this.$t("common.no").toString()
          }
        );
        if (!goToModel) return;
        await this.$router.push({
          name: "edit-component",
          params: { workspaceId: event.workspaceId, componentId: event.componentId }
        });
      });
    await this.notifications?.start();
  }

  beforeDestroy() {
    this.notifications?.close();
  }
}
</script>