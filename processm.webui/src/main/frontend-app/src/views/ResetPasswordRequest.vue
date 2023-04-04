<template>
  <v-container fill-height fluid>
    <v-layout align-center justify-center>
      <v-flex md4 sm8 xs12>
        <v-card class="elevation-12" min-width="470">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>{{ $t("reset-password-form.request.title") }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-form ref="resetForm" v-model="isValidForm" @submit.prevent="reset">
              <v-text-field
                v-model="userEmail"
                :label="$t('common.email')"
                :rules="[(v) => /.+@.+\..+/.test(v) || $t('login-form.validation.email-format')]"
                prepend-icon="person"
                type="text"
              ></v-text-field>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn color="secondary" text @click="cancel">
              {{ $t("common.cancel") }}
            </v-btn>
            <v-btn :disabled="!isValidForm" color="primary" form="resetForm" type="submit"  @click.stop="reset">
              {{ $t("reset-password-form.request.reset") }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import { Component, Inject } from "vue-property-decorator";
import Vue from "vue";
import App from "@/App.vue";
import AccountService from "@/services/AccountService";

@Component
export default class ResetPasswordRequest extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  isValidForm = false;
  userEmail = "";

  cancel() {
    this.$router.push({ name: "login" });
  }

  async reset() {
    try {
      await this.accountService.requestPasswordReset(this.userEmail);
      this.app.info(this.$t("reset-password-form.request.requested").toString());
      this.$emit("success");
      this.$router.push({ name: "login" });
    } catch (e) {
      this.app.error(e);
    }
  }
}
</script>

<style scoped></style>
