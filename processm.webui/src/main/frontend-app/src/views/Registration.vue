<template>
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex xs12 sm8 md4>
        <v-card class="elevation-12" min-width="470">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>
              {{ $t("registration-form.title") }}
            </v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <registration-form @cancel="goToLogin" @success="goToLogin"></registration-form>
          </v-card-text>
        </v-card>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import { Component } from "vue-property-decorator";
import RegistrationForm from "@/components/RegistrationForm.vue";

@Component({
  components: { RegistrationForm }
})
export default class Registration extends Vue {
  goToLogin() {
    this.$router.push({ name: "login" });
  }
}
</script>
