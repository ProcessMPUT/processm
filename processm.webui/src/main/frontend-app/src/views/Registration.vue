<template>
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex xs12 sm8 md4>
        <v-card class="elevation-12">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>{{
              $t("registration-form.title")
            }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-form v-model="isValidForm" ref="registrationForm">
              <v-text-field
                :label="$t('registration-form.organization-name')"
                v-model="organizationName"
                prepend-icon="business"
                type="text"
                :rules="[
                  (v) =>
                    !!v || $t('registration-form.validation.organization-empty')
                ]"
              ></v-text-field>

              <v-text-field
                :label="$t('registration-form.admin-email')"
                v-model="userEmail"
                prepend-icon="person"
                type="text"
                :rules="[
                  (v) =>
                    /.+@.+\..+/.test(v) ||
                    $t('registration-form.validation.email-format')
                ]"
              ></v-text-field>
              <v-layout>
                <v-spacer></v-spacer>
                <v-btn color="primary" @click.stop="register">{{
                  $t("registration-form.register")
                }}</v-btn>
              </v-layout>
            </v-form>
          </v-card-text>
        </v-card>
        <v-snackbar
          color="error"
          v-model="errorMessage"
          :timeout="messageTimeout"
        >
          {{ $t("registration-form.error-box.failed") }}
          <v-btn dark text @click="errorMessage = false">
            {{ $t("common.close") }}
          </v-btn>
        </v-snackbar>
        <v-snackbar
          color="success"
          v-model="registrationResultMessage"
          :timeout="messageTimeout"
        >
          {{ $t("registration-form.success-box.registered") }}
          <v-btn dark text @click="registrationResultMessage = false">
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
export default class Registration extends Vue {
  @Inject() accountService!: AccountService;
  readonly messageTimeout = 3000;
  isValidForm = false;
  errorMessage = false;
  registrationResultMessage = false;
  userEmail = "";
  organizationName = "";

  async register() {
    if (!this.isValidForm) {
      return (this.$refs.loginForm as Vue & {
        validate: () => boolean;
      }).validate();
    }

    try {
      await this.accountService.registerNewAccount(
        this.userEmail,
        this.organizationName
      );
      this.registrationResultMessage = true;
      setTimeout(
        () => this.$router.push({ name: "login" }),
        this.messageTimeout
      );
    } catch (error) {
      console.error(error);
      this.errorMessage = true;
    }
  }
}
</script>
