<template>
  <v-dialog v-model="value" max-width="400" @click:outside="cancel" @keydown.esc="cancel">
    <v-card>
      <v-card-title class="headline">
        {{ $t("password-change-dialog.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form v-model="isFormValid" ref="passwordChangeForm">
            <v-row>
              <v-col>
                <v-text-field
                  id="currentPassword"
                  :label="$t('password-change-dialog.current-password')"
                  v-model="currentPassword"
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
                  id="newPassword"
                  :label="$t('password-change-dialog.new-password')"
                  v-model="newPassword"
                  type="password"
                  :rules="[
                    (v) =>
                      !!v ||
                      $t('password-change-dialog.validation.password-empty'),
                    (v) =>
                      v != currentPassword ||
                      $t(
                        'password-change-dialog.validation.new-password-is-the-same'
                      )
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
              </v-col>
            </v-row>
          </v-form>
        </v-container>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn color="primary" text @click.stop="cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn
          color="primary"
          text
          :disabled="!isFormValid"
          @click.stop="submitPassword"
        >
          {{ $t("common.submit") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";

@Component
export default class PasswordChangeDialog extends Vue {
  @Prop({ default: false })
  readonly value!: boolean;

  currentPassword = "";
  newPassword = "";
  passwordConfirmation = "";
  isFormValid = true;

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  submitPassword() {
    if (
      !(this.$refs.passwordChangeForm as Vue & {
        validate: () => boolean;
      }).validate()
    ) {
      return;
    }

    this.$emit("submitted", this.currentPassword, this.newPassword);
    this.resetForm();
  }

  private resetForm() {
    (this.$refs.passwordChangeForm as HTMLFormElement).reset();
  }
}
</script>
