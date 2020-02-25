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
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn color="primary" @click.stop="authenticate">{{
              $t("login-form.login")
            }}</v-btn>
          </v-card-actions>
        </v-card>
        <v-snackbar
          color="error"
          v-model="errorMessage"
          :timeout="errorTimeout"
        >
          {{ $t("login-form.error-box.failed") }}
          <v-btn dark text @click="errorMessage = false">
            {{ $t("common.close") }}
          </v-btn>
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
      const { userData, token } = await this.accountService.signIn(
        this.username,
        this.password
      );
      this.$sessionStorage.setSessionToken(token);
      this.$sessionStorage.setUserInfo(userData);
      this.$router.push("/");
    } catch (error) {
      this.errorMessage = true;
    }
  }
}
</script>
