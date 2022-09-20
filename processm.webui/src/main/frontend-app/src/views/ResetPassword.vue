<template>
  <v-container fill-height fluid>
    <v-layout align-center justify-center>
      <v-flex md4 sm8 xs12>
        <v-card class="elevation-12">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>{{ $t("reset-password.title") }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-form ref="resetForm" v-model="isValidForm" @submit.prevent="reset">
              <v-text-field
                v-model="username"
                :label="$t('login-form.email')"
                :rules="[(v) => /.+@.+\..+/.test(v) || $t('login-form.validation.email-format')]"
                prepend-icon="person"
                type="text"
              ></v-text-field>
            </v-form>
          </v-card-text>
        </v-card>
        <v-card-actions>
          <v-btn :disabled="!isValidForm" color="primary" form="resetForm" type="submit">
            {{ $t("login-form.login") }}
          </v-btn>
        </v-card-actions>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import { Component, Inject } from "vue-property-decorator";
import Vue from "vue";
import App from "@/App.vue";

@Component
export default class ResetPassword extends Vue {
  @Inject() app!: App;
  isValidForm = false;

  async reset() {
    try {
      // TODO
      throw new Error("Not implemented");
    } catch (e) {
      this.app.error(e);
    }
  }
}
</script>

<style scoped></style>
