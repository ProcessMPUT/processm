<template>
  <v-form v-model="isValidForm" ref="registrationForm">
    <v-text-field
      :label="$t('registration-form.admin-email')"
      v-model="userEmail"
      prepend-icon="person"
      type="text"
      name="user-email"
      :rules="[(v) => /^([a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,6})*$/.test(v) || $t('registration-form.validation.email-format')]"
    ></v-text-field>

    <v-text-field
      :label="$t('registration-form.password')"
      v-model="userPassword"
      prepend-icon="password"
      type="password"
      name="user-password"
      :rules="[(v) => /(?=(.*?[0-9]))(?=.*?[A-Z])(?=.*?[a-z])^.{8,}?$/.test(v) || $t('registration-form.validation.password-format')]"
    ></v-text-field>

    <v-checkbox v-model="newOrganization" name="new-organization" :label="$t('registration-form.new-organization')"></v-checkbox>
    <v-text-field
      v-show="newOrganization"
      v-model="organizationName"
      :label="$t('registration-form.organization-name')"
      :rules="[(v) => !newOrganization || !!v || $t('registration-form.validation.organization-empty')]"
      prepend-icon="business"
      type="text"
      name="organization-name"
    ></v-text-field>
    <v-layout>
      <v-spacer></v-spacer>
      <v-btn color="secondary" text @click="cancel">
        {{ $t("common.cancel") }}
      </v-btn>
      <v-btn name="btn-register" color="primary" @click.stop="register">{{ $t("registration-form.register") }}</v-btn>
    </v-layout>
  </v-form>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Inject } from "vue-property-decorator";
import AccountService from "@/services/AccountService";
import App from "@/App.vue";

@Component
export default class RegistrationForm extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;

  isValidForm = false;
  userEmail = "";
  userPassword = "";
  organizationName = "";
  newOrganization = false;

  async register() {
    if (!this.isValidForm) {
      return (this.$refs.registrationForm as Vue & {
        validate: () => boolean;
      }).validate();
    }

    try {
      await this.accountService.registerNewAccount(this.userEmail, this.userPassword, this.newOrganization, this.organizationName);
      this.app.info(this.$t("registration-form.success-box.registered").toString());
      this.$emit("success");
    } catch (error) {
      console.error(error);
      this.app.error(error);
    }
  }

  cancel() {
    this.$emit("cancel");
  }
}
</script>

<style scoped></style>
