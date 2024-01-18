<template>
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex xs12 sm8 md4>
        <v-card class="elevation-12" min-width="470">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>{{ $t("login-form.title") }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-alert type="info" text v-if="config.loginMessage !== ''">
              {{ config.loginMessage }}
            </v-alert>
            <v-form id="loginForm" ref="loginForm" v-model="isValidForm" @submit.prevent="authenticate">
              <v-text-field
                :label="$t('common.email')"
                v-model="username"
                name="username"
                prepend-icon="person"
                type="text"
                :rules="[(v) => /.+@.+\..+/.test(v) || $t('login-form.validation.email-format')]"
              ></v-text-field>

              <v-text-field
                id="password"
                :label="$t('login-form.password')"
                v-model="password"
                name="password"
                prepend-icon="lock"
                type="password"
                :rules="[(v) => !!v || $t('login-form.validation.password-empty')]"
                @keypress.enter="authenticate"
              ></v-text-field>
              <v-layout>
                <v-btn v-if="!config.demoMode" name="btn-register" color="primary" text to="register">
                  {{ $t("login-form.register-account") }}
                </v-btn>
                <v-btn v-if="!config.demoMode" color="primary" text to="reset-password">
                  {{ $t("login-form.reset-password") }}
                </v-btn>
                <v-spacer></v-spacer>
                <v-btn color="primary" name="btn-login" form="loginForm" type="submit">
                  {{ $t("login-form.login") }}
                </v-btn>
              </v-layout>
            </v-form>
          </v-card-text>
        </v-card>
        <v-snackbar v-model="errorMessage" :timeout="errorTimeout" color="error">
          {{ $t("login-form.error-box.failed") }}
          <v-btn dark text @click="errorMessage = false">{{ $t("common.close") }}</v-btn>
        </v-snackbar>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import AccountService from "@/services/AccountService";
import ConfigService from "@/services/ConfigService";
import { Config, Organization } from "@/openapi";

@Component
export default class Login extends Vue {
  @Inject() accountService!: AccountService;
  @Inject() configService!: ConfigService;
  readonly errorTimeout = 3000;
  isValidForm = false;
  errorMessage = false;
  username = "";
  password = "";
  config: Config = {
    loginMessage: "",
    demoMode: false
  };

  async mounted() {
    const c = await this.configService.getConfig();
    Object.assign(this.config, c);
  }

  async authenticate() {
    if (!this.isValidForm) {
      return (this.$refs.loginForm as Vue & {
        validate: () => boolean;
      }).validate();
    }

    try {
      await this.accountService.signIn(this.username, this.password);
      const { language } = await this.accountService.getAccountDetails();
      this.setLanguage(language);
      const organizations = await this.accountService.getUserOrganizations();
      this.setCurrentOrganization(organizations);
      this.$router.push({ name: "home" });
    } catch (error) {
      console.error(error);
      this.errorMessage = true;
    }
  }

  setLanguage(language: string) {
    if (this.$i18n.availableLocales.includes(language)) {
      this.$i18n.locale = language;
    }
  }

  setCurrentOrganization(userOrganizations: Organization[]) {
    if (userOrganizations.length > 1) {
      //TODO: the current user is associated with more than one organization,
      //display a modal which allows the user to choose the organization context
      this.$sessionStorage.currentOrganizationIndex = 0; // FIXME: temporary: always choose the first organization
      console.error(
        `Not implemented: the current user is associated with ${userOrganizations.length} organizations; the first one was automatically selected.`
      );
    } else if (userOrganizations.length == 1) {
      this.$sessionStorage.currentOrganizationIndex = 0;
    } else {
      //TODO: user is not assigned to any organization
      //display the error on the global snackbar
      console.error("Not implemented: the user is not associated with any organization.");
    }
  }
}
</script>
