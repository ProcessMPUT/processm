<template>
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex xs12 sm8 md4>
        <v-card class="elevation-12" min-width="470">
          <v-toolbar color="primary" dark flat>
            <v-toolbar-title>
              {{ $t("reset-password-form.title") }}
            </v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-form v-model="isFormValid">
              <v-text-field
                  id="newPassword"
                  :label="$t('password-change-dialog.new-password')"
                  v-model="newPassword"
                  type="password"
                  :rules="[
                    (v) =>
                      !!v ||
                      $t('password-change-dialog.validation.password-empty')
                  ]"
                  validate-on-blur
                  required
              ></v-text-field>
              <v-text-field
                  id="passwordConfirmation"
                  :label="$t('password-change-dialog.password-confirmation')"
                  v-model="passwordConfirmation"
                  type="password"
                  :rules="[
                    (v) =>
                      !!v ||
                      $t('password-change-dialog.validation.password-empty'),
                    (v) =>
                      v == newPassword ||
                      $t(
                        'password-change-dialog.validation.password-confirmation-not-the-same'
                      )
                  ]"
                  required
              ></v-text-field>

              <v-layout>
                <v-spacer></v-spacer>
                <v-btn color="secondary" text @click="cancel">
                  {{ $t("common.cancel") }}
                </v-btn>
                <v-btn color="primary" @click.stop="submit">{{ $t("common.submit") }}</v-btn>
              </v-layout>
            </v-form>
          </v-card-text>
        </v-card>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import {Component, Inject} from "vue-property-decorator";
import PasswordChangeDialog from "@/components/PasswordChangeDialog.vue";
import App from "@/App.vue";
import AccountService from "@/services/AccountService";

@Component({
  components: {PasswordChangeDialog}
})
export default class ResetPassword extends Vue {
  @Inject() app!: App;
  @Inject() accountService!: AccountService;
  newPassword = "";
  passwordConfirmation = "";
  isFormValid = false;

  async submit() {
    try {
      const token = this.$route.params.token;
      await this.accountService.resetPassword(token, this.newPassword);
      this.app.info(this.$t("reset-password-form.success").toString());
      await this.$router.push({name: "login"});
    } catch (e) {
      this.app.error(this.$t("reset-password-form.invalid-token").toString());
    }
  }

  cancel() {
    this.$router.push({name: "login"});
  }
}
</script>
