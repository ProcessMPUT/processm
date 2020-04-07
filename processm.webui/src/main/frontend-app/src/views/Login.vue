<template>
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex xs12 sm8 md4>
        <v-card class="elevation-12">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>{{ $t("login-form.title") }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-form v-model="isValidForm" ref="loginForm">
              <v-text-field
                :label="$t('login-form.email')"
                v-model="username"
                prepend-icon="person"
                type="text"
                :rules="[
                  v =>
                    /.+@.+\..+/.test(v) ||
                    $t('login-form.validation.email-format')
                ]"
              ></v-text-field>

              <v-text-field
                id="password"
                :label="$t('login-form.password')"
                v-model="password"
                prepend-icon="lock"
                type="password"
                :rules="[
                  v => !!v || $t('login-form.validation.password-empty')
                ]"
                @keypress.enter="authenticate"
              ></v-text-field>
              <v-layout justify-space-between>
                <v-btn color="primary" text small to="register">
                  {{ $t('login-form.register-account') }}
                </v-btn>
                <v-btn color="primary" @click.stop="authenticate">
                  {{ $t("login-form.login") }}
                </v-btn>
              </v-layout>
            </v-form>
          </v-card-text>
        </v-card>
        <v-snackbar
          color="error"
          v-model="errorMessage"
          :timeout="errorTimeout"
        >
          {{ $t("login-form.error-box.failed") }}
          <v-btn dark text @click="errorMessage = false">{{
            $t("common.close")
          }}</v-btn>
        </v-snackbar>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import AccountService from "@/services/AccountService";

@Component
export default class Login extends Vue {
  @Inject() accountService!: AccountService;
  readonly errorTimeout = 3000;
  isValidForm = false;
  errorMessage = false;
  username = "";
  password = "";

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
      this.$router.push({ name: "home" });
    } catch (error) {
      this.errorMessage = true;
    }
  }

  setLanguage(language: string) {
    if (this.$i18n.availableLocales.includes(language)) {
      this.$i18n.locale = language;
    }
  }
}
</script>
